package com.mpx.im_acct_insp.service;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：到期排程 J01／J02 與手動觸發（spec v2.0 §4.11、§6.7）
//           排程：@Scheduled cron 取 im.acct.insp.job.j01.cron／j02.cron（時區 Asia/Taipei），
//           im.acct.insp.job.enabled＝false 時排程觸發直接略過（手動 API 仍可用）；
//           手動：POST /jobs/{code}/run 需 SYS_JOB_RUN，code 限 J01／J02（其他 404）；baseDate 僅 allow-base-date＝true 時可帶（否則 400 E17）；
//           不重入：同一 JOB_CODE 同時只跑一個（本 JVM 內鎖），執行中再觸發 409 E16；
//           每次寫 IM_ACCT_JOB_RUN（RUNNING → SUCCESS／PARTIAL／FAIL、AFFECTED_CNT、RUN_BY、BASE_DATE），PARTIAL／FAIL 寄 JOB_FAIL；
//           J01：①轉逾期（每筆帳號一個交易：帶條件 UPDATE 主表 → 影響 1 筆才寫歷史 EXPIRE／SYS；單筆失敗記錄後繼續＝PARTIAL）
//                ②轉逾期信、③到期前 7 天信（以通知紀錄「此參照鍵尚無任何紀錄」判斷，漏跑會補寄、不重寄；寄信在交易外）；
//           J02：週鍵＝基準日所在週的週一；該週已有 WEEKLY_SUMMARY 紀錄則不寄（SUCCESS、0 筆），否則寄一封列出所有 EXPIRED 帳號
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 變更單 #1（L5）：J02 未寫入任何通知紀錄（無收件人）時記 WARN，結果照常 SUCCESS（spec §4.13 補註）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-11
// 變更說明: BACKLOG 第 7 項：開頭與收尾出錯也寄 JOB_FAIL——
//           開跑前先把同一排程停在 RUNNING 的舊紀錄補記為 FAIL，該 runId 尚無 JOB_FAIL 通知紀錄才寄（先查寄過沒再改狀態，失敗時留在 RUNNING 下次重試；補記失敗只記 log、照常往下跑；
//           前提：同時只有一個後端在跑，鎖只在本 JVM，所以拿到鎖時的 RUNNING 都是上次沒正常結束的）；
//           取號或寫 RUNNING 失敗 → 不跑、記 log、寄 JOB_FAIL（沒取到號時 runId 為空）、例外照拋（手動 API 回 500）；
//           寫結束紀錄失敗 → 照常回結果，寄 JOB_FAIL 並在錯誤摘要註明「結束紀錄寫入失敗」（排程成功時結果碼仍為 SUCCESS）
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S7 回合 1：新增 J03 緊急存取逾期通知（預設每 15 分鐘，im.acct.insp.job.j03.cron）——
//           基準改為時間點（S7-①）：run 收 LocalDateTime，排程傳現在時刻（到秒），BASE_DATE 寫時間點；J01／J02 取其日期、BASE_DATE 寫 00:00 不變；
//           手動 baseDate：J03 收 yyyy-MM-dd HH:mm:ss（只給日期 → 400 BAD_DATE_TIME，沿用 InspectionService.parseInspDate，S7-②），J01／J02 照舊只收日期；
//           J03：查計算狀態 OVERDUE 且尚無 EMG_OVERDUE 紀錄的單（S7-③），逐張寄信（寄前以同一基準時間點重讀，已處理跳過）；
//           AFFECTED_CNT＝寫入通知紀錄 >0 的單數（S7-⑧）；寫入 0 筆的單記「{單號}:未寫入通知紀錄」→ PARTIAL（S7-⑩）；查詢失敗整體 FAIL；
//           分派改 switch、未知代碼丟 IllegalArgumentException（陷阱 A）；不重入鎖表改由 AcctConst.JOB_CODES 自動產生、
//           run 對鎖表沒有的代碼明確丟例外（陷阱 B）
// ============================================================

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.mpx.im_acct_insp.common.AcctConst;
import com.mpx.im_acct_insp.common.AcctMsg;
import com.mpx.im_acct_insp.common.ApiException;
import com.mpx.im_acct_insp.common.Identity;
import com.mpx.im_acct_insp.common.Texts;
import com.mpx.im_acct_insp.dao.AcctDao;
import com.mpx.im_acct_insp.dao.AcctHistDao;
import com.mpx.im_acct_insp.dao.EmgDao;
import com.mpx.im_acct_insp.dao.JobRunDao;
import com.mpx.im_acct_insp.dao.NotifyLogDao;
import com.mpx.im_acct_insp.model.Acct;
import com.mpx.im_acct_insp.model.AcctHist;
import com.mpx.im_acct_insp.model.Emg;
import com.mpx.im_acct_insp.model.JobRunResult;

@Service
public class JobService {

	private static final Logger log = LoggerFactory.getLogger(JobService.class);

	static final String ZONE = "Asia/Taipei";
	static final int ERR_MSG_MAX = 1000;
	static final String STALE_MSG = "上次執行未正常結束（下次開跑時補記）";
	static final String START_WRITE_FAIL = "開始紀錄寫入失敗，排程未執行";
	static final String FINISH_WRITE_FAIL = "結束紀錄寫入失敗";
	/**
	 * J03：該單未寫入任何通知紀錄（無收件人、紀錄全部寫入失敗、或寄前重讀發現已處理／不存在）時記在錯誤摘要的文字（S7-⑩）。
	 * 注意：SMTP 寄送失敗會寫 FAIL 紀錄、算已寄，不會進這裡
	 */
	static final String J03_NOT_WRITTEN = "未寫入通知紀錄";
	private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
	private static final DateTimeFormatter SHOW = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	/** 單次執行結果 */
	record Outcome(String resultCode, int affected, String errMsg) {
	}

	private final AcctDao acctDao;
	private final AcctHistDao acctHistDao;
	private final JobRunDao jobRunDao;
	private final NotifyLogDao notifyLogDao;
	private final EmgDao emgDao;
	private final NotificationService notificationService;
	private final PermissionService perm;
	private final TransactionTemplate tx;
	private final Clock clock;
	private final boolean enabled;
	private final boolean allowBaseDate;
	/**
	 * 不重入鎖：每個排程代碼一把，直接由 AcctConst.JOB_CODES 產生，新增排程代碼時不必再來這裡加（陷阱 B）；
	 * run 對不在表裡的代碼仍丟 IllegalArgumentException 當防線
	 */
	private final Map<String, AtomicBoolean> running = AcctConst.JOB_CODES.stream()
			.collect(Collectors.toUnmodifiableMap(c -> c, c -> new AtomicBoolean()));

	public JobService(AcctDao acctDao, AcctHistDao acctHistDao, JobRunDao jobRunDao, NotifyLogDao notifyLogDao, EmgDao emgDao,
			NotificationService notificationService, PermissionService perm, TransactionTemplate tx, Clock clock,
			@Value("${im.acct.insp.job.enabled:true}") boolean enabled,
			@Value("${im.acct.insp.job.allow-base-date:false}") boolean allowBaseDate) {
		this.acctDao = acctDao;
		this.acctHistDao = acctHistDao;
		this.jobRunDao = jobRunDao;
		this.notifyLogDao = notifyLogDao;
		this.emgDao = emgDao;
		this.notificationService = notificationService;
		this.perm = perm;
		this.tx = tx;
		this.clock = clock;
		this.enabled = enabled;
		this.allowBaseDate = allowBaseDate;
	}

	// ------------------------------------------------------------
	// 觸發
	// ------------------------------------------------------------

	@Scheduled(cron = "${im.acct.insp.job.j01.cron:0 10 0 * * *}", zone = ZONE)
	public void scheduledJ01() {
		scheduled(AcctConst.JOB_J01);
	}

	@Scheduled(cron = "${im.acct.insp.job.j02.cron:0 0 8 * * *}", zone = ZONE)
	public void scheduledJ02() {
		scheduled(AcctConst.JOB_J02);
	}

	@Scheduled(cron = "${im.acct.insp.job.j03.cron:0 */15 * * * *}", zone = ZONE)
	public void scheduledJ03() {
		scheduled(AcctConst.JOB_J03);
	}

	/** 排程入口：基準＝現在時刻（到秒，Oracle DATE 精度）；J01／J02 只取其日期 */
	void scheduled(String code) {
		if (!enabled) {
			return;
		}
		try {
			JobRunResult r = run(code, LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS), null);
			log.info("排程完成 job={} runId={} result={} affected={}", code, r.getRunId(), r.getResultCode(), r.getAffectedCnt());
		} catch (ApiException e) {
			log.warn("排程略過 job={} 原因={}", code, e.getMessage());
		} catch (RuntimeException e) {
			log.error("排程無法執行 job={} 例外={}", code, e.getClass().getSimpleName(), e);
		}
	}

	/** POST /jobs/{code}/run */
	public JobRunResult runManual(Identity id, String code, String baseDate) {
		perm.require(id, AcctConst.PERM_SYS_JOB_RUN);
		if (code == null || !AcctConst.JOB_CODES.contains(code)) {
			throw AcctMsg.notFound();
		}
		LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
		LocalDateTime base = now;
		String bd = Texts.trimToNull(baseDate);
		if (bd != null) {
			if (!allowBaseDate) {
				throw ApiException.badRequest(AcctMsg.E17);
			}
			base = parseBase(code, bd, now);
		}
		log.info("手動觸發排程 job={} runBy={} baseDate={}", code, id.getRealUserId(), base);
		return run(code, base, id.getRealUserId());
	}

	/**
	 * 手動觸發的基準（S7-②）：J03 是時間點，收 yyyy-MM-dd HH:mm:ss（T 可換空白、可省秒；只給日期 → 400 BAD_DATE_TIME）；
	 * J01／J02 是日期，只收 yyyy-MM-dd（格式錯 → 400 BAD_DATE），回當天 00:00
	 */
	static LocalDateTime parseBase(String code, String bd, LocalDateTime now) {
		if (AcctConst.JOB_J03.equals(code)) {
			return InspectionService.parseInspDate(bd, now);
		}
		try {
			return LocalDate.parse(bd, DATE_FMT).atStartOfDay();
		} catch (DateTimeParseException e) {
			throw ApiException.badRequest(AcctMsg.BAD_DATE);
		}
	}

	/**
	 * 執行一次（排程與手動共用）；同一 JOB_CODE 執行中 → 409 E16。
	 * base：J03 為基準時間點（到秒），J01／J02 只取其日期（BASE_DATE 寫當天 00:00）
	 */
	JobRunResult run(String code, LocalDateTime base, String runBy) {
		AtomicBoolean flag = running.get(code);
		if (flag == null) {
			// 鎖表由 JOB_CODES 產生，這裡只擋 JOB_CODES 以外的代碼；明確丟例外而不是 NPE，也不會落到任何排程去跑（陷阱 B 防線）
			throw new IllegalArgumentException("未知的排程代碼 " + code);
		}
		if (!flag.compareAndSet(false, true)) {
			throw ApiException.conflict(AcctMsg.E16);
		}
		try {
			String createBy = runBy == null ? AcctConst.SYSTEM_USER : runBy;
			recoverStale(code, createBy);
			// 目前只有 J03 以時間點為基準；日後新增時間點型排程（例如 J04）要把它加進這個判斷，否則會被截成 00:00
			boolean dateBased = !AcctConst.JOB_J03.equals(code);
			LocalDateTime baseToWrite = dateBased ? base.toLocalDate().atStartOfDay() : base;
			Long runId = null;
			try {
				runId = jobRunDao.nextId();
				jobRunDao.insertRunning(runId, code, runBy, baseToWrite, LocalDateTime.now(clock), createBy);
			} catch (RuntimeException e) {
				// 不帶堆疊：例外照拋，由排程入口或全域錯誤處理印完整堆疊
				log.error("排程開始紀錄寫入失敗、未執行 job={} runId={} 例外={}", code, runId, e.getClass().getSimpleName());
				notificationService.jobFail(code, runId, AcctConst.JR_FAIL,
						START_WRITE_FAIL + "（" + e.getClass().getSimpleName() + "）");
				throw e;
			}
			Outcome o;
			try {
				o = switch (code) {
				case AcctConst.JOB_J01 -> j01(base.toLocalDate());
				case AcctConst.JOB_J02 -> j02(base.toLocalDate());
				case AcctConst.JOB_J03 -> j03(base);
				default -> throw new IllegalArgumentException("未知的排程代碼 " + code);
				};
			} catch (RuntimeException e) {
				log.error("排程失敗 job={} runId={} 例外={}", code, runId, e.getClass().getSimpleName(), e);
				o = new Outcome(AcctConst.JR_FAIL, 0, e.getClass().getSimpleName());
			}
			String err = Texts.truncate(o.errMsg(), ERR_MSG_MAX);
			boolean finished = true;
			try {
				jobRunDao.finish(runId, o.resultCode(), o.affected(), err, LocalDateTime.now(clock), createBy);
			} catch (RuntimeException e) {
				finished = false;
				log.error("排程結束紀錄寫入失敗 job={} runId={} result={} affected={} 例外={}", code, runId, o.resultCode(),
						o.affected(), e.getClass().getSimpleName(), e);
				String note = FINISH_WRITE_FAIL + "（" + e.getClass().getSimpleName() + "）";
				err = Texts.truncate(err == null ? note : note + "; " + err, ERR_MSG_MAX);
			}
			if (!finished || !AcctConst.JR_SUCCESS.equals(o.resultCode())) {
				notificationService.jobFail(code, runId, o.resultCode(), err);
			}
			JobRunResult r = new JobRunResult();
			r.setRunId(runId);
			r.setResultCode(o.resultCode());
			r.setAffectedCnt(o.affected());
			return r;
		} finally {
			flag.set(false);
		}
	}

	/**
	 * 把同一排程停在 RUNNING 的舊紀錄補記為 FAIL；該 runId 尚未寄過 JOB_FAIL 才寄（呼叫端已持有本排程的鎖）。
	 * 先查寄過沒、再改狀態：查詢或改狀態失敗時該筆仍是 RUNNING，下次開跑會重試，不會變成「已改 FAIL 卻沒寄信」
	 */
	private void recoverStale(String code, String updateBy) {
		List<Long> ids;
		try {
			ids = jobRunDao.findRunningIds(code);
		} catch (RuntimeException e) {
			log.error("查詢未結束的排程紀錄失敗 job={} 例外={}", code, e.getClass().getSimpleName(), e);
			return;
		}
		for (Long staleId : ids) {
			try {
				boolean notified = notifyLogDao.countByEventRef(AcctConst.EV_JOB_FAIL, AcctConst.REF_JOB,
						String.valueOf(staleId)) > 0;
				if (jobRunDao.markStaleFail(staleId, STALE_MSG, LocalDateTime.now(clock), updateBy) != 1) {
					continue;
				}
				log.warn("補記未正常結束的排程 job={} runId={}", code, staleId);
				if (!notified) {
					notificationService.jobFail(code, staleId, AcctConst.JR_FAIL, STALE_MSG);
				}
			} catch (RuntimeException e) {
				log.error("補記未正常結束的排程失敗 job={} runId={} 例外={}", code, staleId, e.getClass().getSimpleName(), e);
			}
		}
	}

	// ------------------------------------------------------------
	// J01 到期處理
	// ------------------------------------------------------------

	Outcome j01(LocalDate today) {
		int affected = 0;
		List<String> errors = new ArrayList<>();

		// ① 轉逾期：每筆帳號一個交易
		for (Acct a : acctDao.findToExpire(today)) {
			try {
				Boolean changed = tx.execute(s -> expireOne(a, today));
				if (Boolean.TRUE.equals(changed)) {
					affected++;
				}
			} catch (RuntimeException e) {
				log.error("J01 轉逾期失敗 acctId={} 例外={}", a.getAcctId(), e.getClass().getSimpleName());
				errors.add(a.getAcctId() + ":" + e.getClass().getSimpleName());
			}
		}
		// ② 轉逾期信（交易外；每帳號每參照鍵一次）
		for (Acct a : acctDao.findExpiredUnnotified(AcctConst.EV_EXPIRED)) {
			if (notificationService.expiryEvent(AcctConst.EV_EXPIRED, a, today) > 0) {
				affected++;
			}
		}
		// ③ 到期前 7 天信
		for (Acct a : acctDao.findExpiry7dUnnotified(AcctConst.EV_EXPIRY_7D, today,
				today.plusDays(AcctConst.EXPIRY_NOTICE_DAYS))) {
			if (notificationService.expiryEvent(AcctConst.EV_EXPIRY_7D, a, today) > 0) {
				affected++;
			}
		}
		return errors.isEmpty() ? new Outcome(AcctConst.JR_SUCCESS, affected, null)
				: new Outcome(AcctConst.JR_PARTIAL, affected, String.join("; ", errors));
	}

	/** 單筆轉逾期（交易內）：帶條件 UPDATE 影響 1 筆才寫歷史 EXPIRE */
	private boolean expireOne(Acct a, LocalDate today) {
		LocalDateTime now = LocalDateTime.now(clock);
		if (acctDao.expire(a.getAcctId(), today, now, AcctConst.SYSTEM_USER) != 1) {
			return false;
		}
		AcctHist h = new AcctHist();
		h.setAcctId(a.getAcctId());
		h.setSourceCode(AcctConst.HIST_SRC_SYS);
		h.setActionCode(AcctConst.ACT_EXPIRE);
		h.setActByName(AcctConst.SYSTEM_ACTOR_NAME);
		h.setActDate(now);
		h.setResultDescr("到期日 " + (a.getExpiryDate() == null ? "" : a.getExpiryDate().toLocalDate().format(SHOW))
				+ " 已過，轉為已逾期");
		h.setIsSelfAction(0);
		h.setCreateBy(AcctConst.SYSTEM_USER);
		acctHistDao.insert(h);
		return true;
	}

	// ------------------------------------------------------------
	// J02 每週彙總
	// ------------------------------------------------------------

	Outcome j02(LocalDate base) {
		String key = weekKey(base);
		if (notifyLogDao.countByEventRef(AcctConst.EV_WEEKLY_SUMMARY, AcctConst.REF_WEEK, key) > 0) {
			return new Outcome(AcctConst.JR_SUCCESS, 0, null);
		}
		List<Acct> expired = acctDao.findExpiredAll();
		int written = notificationService.weeklySummary(key, expired, base);
		if (written == 0) {
			log.warn("J02 無任何收件人（未寫入通知紀錄），週鍵={}；結果照常 SUCCESS，下次執行會再嘗試", key);
		}
		return new Outcome(AcctConst.JR_SUCCESS, expired.size(), null);
	}

	/** 週鍵＝基準日所在週的週一（yyyymmdd） */
	static String weekKey(LocalDate base) {
		return base.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).format(DateTimeFormatter.BASIC_ISO_DATE);
	}

	// ------------------------------------------------------------
	// J03 緊急存取逾期通知（BACKLOG 第 46 項 S7）
	// ------------------------------------------------------------

	/**
	 * 以 base 為基準時間點，找計算狀態 OVERDUE 且尚未寄過 EMG_OVERDUE 的單，逐張寄給 EMG_DECIDE 持有人（每案一次）。
	 * AFFECTED_CNT＝有寫入通知紀錄的單數（SMTP 寄送失敗會寫 FAIL 紀錄，算已寄、不重寄，S7-④）；
	 * 寫入 0 筆（無收件人、紀錄全部寫入失敗、或寄前重讀發現已處理／不存在）記錯誤摘要 → PARTIAL，下次掃描會再嘗試；
	 * 單張例外記錄後繼續；查詢本身失敗由 run 的外層 catch 記 FAIL
	 */
	Outcome j03(LocalDateTime base) {
		int affected = 0;
		List<String> errors = new ArrayList<>();
		for (Emg e : emgDao.findOverdueUnnotified(AcctConst.EV_EMG_OVERDUE, base)) {
			try {
				if (notificationService.emgOverdue(e.getEmgId(), base) > 0) {
					affected++;
				} else {
					log.warn("J03 逾期通知未寫入任何紀錄 emgId={}，下次掃描再嘗試", e.getEmgId());
					errors.add(e.getEmgId() + ":" + J03_NOT_WRITTEN);
				}
			} catch (RuntimeException ex) {
				log.error("J03 逾期通知失敗 emgId={} 例外={}", e.getEmgId(), ex.getClass().getSimpleName());
				errors.add(e.getEmgId() + ":" + ex.getClass().getSimpleName());
			}
		}
		return errors.isEmpty() ? new Outcome(AcctConst.JR_SUCCESS, affected, null)
				: new Outcome(AcctConst.JR_PARTIAL, affected, String.join("; ", errors));
	}
}
