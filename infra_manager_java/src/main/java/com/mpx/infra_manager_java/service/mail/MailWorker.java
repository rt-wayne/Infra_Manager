package com.mpx.infra_manager_java.service.mail;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-08
// 變更說明: 新增：背景寄信排程（S8 R1）。只有 im.mail.enabled=true（MailSchedulingConfig 啟用 @EnableScheduling）才會跑。
//           每輪：不鎖查最多 batch-size 個到期候選 → 每封信各開一個交易：FOR UPDATE SKIP LOCKED 鎖到才寄，
//           寄完在同交易標 SENT 或 TRY_CNT+1（達 max-try 標 FAILED）後 commit。
//           鎖沒拿到（被另一實例或 admin 重寄佔住）就跳過。語意是「至少寄一次」：寄出後、commit 前程式掛掉會重寄
//           （schema 沒有 SENDING 中間狀態，不改 DDL 的已知取捨）。
//           DB 連不上或 SMTP 未設定時每輪都會碰到，只在第一次失敗與恢復時各記一次 log（避免每 30 秒洗版）；
//           單封信的成敗各記一行，含 outbox ID、第幾次、例外類別，不含收件人地址。
//           ERROR_TEXT 存例外類別＋訊息（SMTP 回應）截 1000 字
// ============================================================

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.mpx.infra_manager_java.config.MailProperties;
import com.mpx.infra_manager_java.dao.mail.MailOutboxDao;
import com.mpx.infra_manager_java.model.mail.MailOutboxRow;
import com.mpx.infra_manager_java.service.mail.MailDispatcher.SendResult;

@Component
public class MailWorker {

	/** ERROR_TEXT VARCHAR2(1000 CHAR) */
	static final int ERROR_MAX = 1000;

	private static final Logger log = LoggerFactory.getLogger(MailWorker.class);

	private final MailOutboxDao outboxDao;
	private final MailDispatcher dispatcher;
	private final MailProperties properties;
	private final TransactionTemplate tx;

	/** 目前是否處於「DB 無法存取」狀態；只在狀態翻轉時記 log */
	private final AtomicBoolean dbUnavailable = new AtomicBoolean(false);
	/** 目前是否處於「SMTP 未設定」狀態；同上 */
	private final AtomicBoolean smtpUnconfigured = new AtomicBoolean(false);

	public MailWorker(MailOutboxDao outboxDao, MailDispatcher dispatcher, MailProperties properties,
			PlatformTransactionManager transactionManager) {
		this.outboxDao = outboxDao;
		this.dispatcher = dispatcher;
		this.properties = properties;
		this.tx = new TransactionTemplate(transactionManager);
	}

	/** 排程進入點；間隔與首次延遲讀 properties，預設 30 秒／15 秒 */
	@Scheduled(fixedDelayString = "${im.mail.poll-delay-ms:30000}", initialDelayString = "${im.mail.initial-delay-ms:15000}")
	public void poll() {
		pollOnce();
	}

	/**
	 * 跑一輪；回「成功寄出的封數」。供排程與測試呼叫。
	 * 任何例外都在這裡吞掉並記 log，不讓排程執行緒死掉
	 */
	public int pollOnce() {
		if (!dispatcher.isConfigured()) {
			if (smtpUnconfigured.compareAndSet(false, true)) {
				log.warn("信件 worker：SMTP 未設定（spring.mail.host／im.mail.from），暫不寄信，信只累積在 outbox");
			}
			return 0;
		}
		if (smtpUnconfigured.compareAndSet(true, false)) {
			log.info("信件 worker：SMTP 設定已就緒，恢復寄信");
		}

		List<Long> ids;
		try {
			ids = outboxDao.findDueIds(properties.getBatchSize(), properties.getMaxTry(),
					properties.getRetryBackoffMinutes());
			markDbRecovered();
		} catch (DataAccessException e) {
			markDbUnavailable(e);
			return 0;
		}

		int sent = 0;
		for (Long id : ids) {
			try {
				Boolean ok = tx.execute(status -> processOne(id));
				if (Boolean.TRUE.equals(ok)) {
					sent++;
				}
				markDbRecovered();
			} catch (DataAccessException e) {
				markDbUnavailable(e);
				return sent;
			} catch (RuntimeException e) {
				// 不該走到這裡（processOne 已把寄送例外轉成 markFailedTry）；記下來但不中斷其他信
				log.warn("信件 worker：outbox {} 處理時發生未預期錯誤 {}", id, e.getClass().getSimpleName());
			}
		}
		return sent;
	}

	/** 在交易內處理一封：鎖不到回 null；寄成功回 true；寄失敗（已記 TRY_CNT）回 false */
	private Boolean processOne(long id) {
		MailOutboxRow row = outboxDao.lockPending(id);
		if (row == null) {
			return null;
		}
		int tryNo = (row.getTryCnt() == null ? 0 : row.getTryCnt()) + 1;
		try {
			SendResult result = dispatcher.send(row);
			outboxDao.markSent(id, result.messageId(), result.note());
			if (result.note() == null) {
				log.info("信件 worker：outbox {} 已寄出（第 {} 次）", id, tryNo);
			} else {
				log.warn("信件 worker：outbox {} 已寄出但部分收件人未寄達（第 {} 次）", id, tryNo);
			}
			return true;
		} catch (DataAccessException e) {
			// markSent 失敗屬 DB 問題，往外拋讓交易 rollback、本輪停止
			throw e;
		} catch (RuntimeException e) {
			boolean reachedLimit = tryNo >= properties.getMaxTry();
			outboxDao.markFailedTry(id, errorText(e), properties.getMaxTry());
			log.warn("信件 worker：outbox {} 寄送失敗（第 {} 次，{}）：{}", id, tryNo,
					reachedLimit ? "已達上限、標 FAILED" : "稍後重試", e.getClass().getSimpleName());
			return false;
		}
	}

	/** 例外類別＋訊息（含 SMTP 回應），有 cause 時再附一層；截到欄位長度 */
	static String errorText(Throwable e) {
		StringBuilder sb = new StringBuilder(e.getClass().getSimpleName());
		if (e.getMessage() != null && !e.getMessage().isBlank()) {
			sb.append(": ").append(e.getMessage().trim());
		}
		Throwable cause = e.getCause();
		if (cause != null && cause != e) {
			sb.append(" <- ").append(cause.getClass().getSimpleName());
			if (cause.getMessage() != null && !cause.getMessage().isBlank()) {
				sb.append(": ").append(cause.getMessage().trim());
			}
		}
		String text = sb.toString().replaceAll("[\\r\\n]+", " ");
		return text.length() <= ERROR_MAX ? text : text.substring(0, ERROR_MAX);
	}

	private void markDbUnavailable(DataAccessException e) {
		if (dbUnavailable.compareAndSet(false, true)) {
			log.warn("信件 worker：無法存取 DB（{}），暫停到恢復為止", e.getClass().getSimpleName());
		}
	}

	private void markDbRecovered() {
		if (dbUnavailable.compareAndSet(true, false)) {
			log.info("信件 worker：DB 存取已恢復");
		}
	}
}
