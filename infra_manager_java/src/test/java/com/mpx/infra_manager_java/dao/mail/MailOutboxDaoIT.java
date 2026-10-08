package com.mpx.infra_manager_java.dao.mail;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-08
// 變更說明: 新增：IM_MAIL_OUTBOX DAO 對真實測試 Oracle 的整合測試（S8 R1；只由 mvnw verify 執行；
//           連線資訊 API 為空時略過）。鎖定：
//           insert 後 CLOB 欄位往返一致、狀態 PENDING、TRY_CNT 0；findDueIds 看得到新信；
//           交易內 lockPending 拿得到、另一條連線同時 lockPending 拿不到（SKIP LOCKED）；
//           markFailedTry 累到 maxTry 變 FAILED 且不再是候選；退避中（TRY_CNT 1、剛更新）不是候選；
//           markSent 後 SENT、SEND_DATE／SMTP_MSG_ID 有值、不再是候選、再 markSent 0 列。
//           測試結束硬刪本測試建的列（只限 CREATE_BY='S8IT' 且主鍵在本測試記下的範圍）
//           2026-10-08 S8 R3（Claude Opus 5.5）：加「業務交易 rollback 時 outbox 也沒有資料」（enqueue 在外層交易內、
//           之後業務拋例外 → 本標記的列數不變；萬一殘留也記進清除名單）
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.mpx.common.db.DbClient;
import com.mpx.infra_manager_java.config.DbSchema;
import com.mpx.infra_manager_java.model.mail.MailIdRow;
import com.mpx.infra_manager_java.model.mail.MailMessage;
import com.mpx.infra_manager_java.model.mail.MailOutboxRow;
import com.mpx.infra_manager_java.service.mail.MailOutboxService;

@SpringBootTest
class MailOutboxDaoIT {

	private static final String MARKER = "S8IT";
	private static final String TO_JSON = "[\"s8it-a@example.invalid\",\"s8it-b@example.invalid\"]";
	private static final String META_JSON = "{\"event\":\"step-pending\",\"appId\":\"S8IT-0001\"}";
	private static final String HTML = "<p>整合測試</p>" + "x".repeat(5000);

	@Autowired
	private MailOutboxDao dao;

	@Autowired
	private MailOutboxService outboxService;

	@Autowired
	private DbClient dbClient;

	@Autowired
	private DbSchema schema;

	@Autowired
	private PlatformTransactionManager txManager;

	@Value("${db.connect.api.domain.path:}")
	private String apiUrl;

	@Value("${db.connect.itflow}")
	private String itflowDb;

	private final List<Long> created = new ArrayList<>();
	private TransactionTemplate tx;

	@BeforeEach
	void setUp() {
		assumeTrue(apiUrl != null && !apiUrl.isBlank(), "host.properties 未設定連線資訊 API，略過整合測試");
		tx = new TransactionTemplate(txManager);
	}

	@AfterEach
	void cleanUp() {
		for (Long id : created) {
			dbClient.update(itflowDb, "DELETE FROM " + schema.table("IM_MAIL_OUTBOX")
					+ " WHERE MAIL_OUTBOX_ID = :id AND CREATE_BY = :by", Map.of("id", id, "by", MARKER));
		}
	}

	/** insert 沒有回主鍵（DbClient 沒有 generated key），用 CREATE_BY 標記找出本測試新寫的那一列 */
	private long insertOne(String subject) {
		List<Long> before = idsByMarker();
		dao.insert(TO_JSON, "[\"s8it-c@example.invalid\"]", null, subject, HTML, META_JSON, MARKER);
		List<Long> after = idsByMarker();
		after.removeAll(before);
		assertThat(after).hasSize(1);
		created.add(after.get(0));
		return after.get(0);
	}

	private List<Long> idsByMarker() {
		return new ArrayList<>(dbClient.query(itflowDb, "SELECT MAIL_OUTBOX_ID FROM " + schema.table("IM_MAIL_OUTBOX")
				+ " WHERE CREATE_BY = :by ORDER BY MAIL_OUTBOX_ID", Map.of("by", MARKER), MailIdRow.class).stream()
				.map(MailIdRow::getMailOutboxId).toList());
	}

	private List<Long> due() {
		return dao.findDueIds(1000, 5, 1);
	}

	@Test
	void 寫入後欄位往返一致且為候選() {
		long id = insertOne("S8IT 主旨");

		MailOutboxRow row = dao.findById(id);
		assertThat(row).isNotNull();
		assertThat(row.getToJson()).isEqualTo(TO_JSON);
		assertThat(row.getCcJson()).isEqualTo("[\"s8it-c@example.invalid\"]");
		assertThat(row.getBccJson()).isNull();
		assertThat(row.getMailSubj()).isEqualTo("S8IT 主旨");
		assertThat(row.getHtmlBody()).isEqualTo(HTML);
		assertThat(row.getMetaJson()).isEqualTo(META_JSON);
		assertThat(row.getMailStatusCode()).isEqualTo(MailOutboxDao.STATUS_PENDING);
		assertThat(row.getTryCnt()).isZero();
		assertThat(row.getCreateBy()).isEqualTo(MARKER);
		assertThat(due()).contains(id);
	}

	@Test
	void 交易內鎖住後另一連線拿不到() throws Exception {
		long id = insertOne("S8IT 鎖");
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		ExecutorService pool = Executors.newSingleThreadExecutor();
		try {
			Future<MailOutboxRow> holder = pool.submit(() -> tx.execute(status -> {
				MailOutboxRow r = dao.lockPending(id);
				locked.countDown();
				try {
					release.await(20, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				return r;
			}));
			assertThat(locked.await(20, TimeUnit.SECONDS)).isTrue();

			MailOutboxRow second = tx.execute(status -> dao.lockPending(id));
			assertThat(second).as("SKIP LOCKED 應讓第二條連線拿不到").isNull();

			release.countDown();
			assertThat(holder.get(20, TimeUnit.SECONDS)).isNotNull();
		} finally {
			release.countDown();
			pool.shutdownNow();
		}

		// tx.execute 的回傳直接塞進 assertThat 會讓 Java 推論撞上 Predicate 多載，所以先接成區域變數
		MailOutboxRow again = tx.execute(status -> dao.lockPending(id));
		assertThat(again).as("鎖釋放後可再鎖").isNotNull();
	}

	@Test
	void 失敗累計到上限變FAILED且退避中不是候選() {
		long id = insertOne("S8IT 失敗");

		assertThat(dao.markFailedTry(id, "MailSendException: 測試 1", 3)).isEqualTo(1);
		MailOutboxRow r1 = dao.findById(id);
		assertThat(r1.getTryCnt()).isEqualTo(1);
		assertThat(r1.getMailStatusCode()).isEqualTo(MailOutboxDao.STATUS_PENDING);
		assertThat(r1.getErrorText()).isEqualTo("MailSendException: 測試 1");
		assertThat(r1.getUpdateBy()).isEqualTo(MailOutboxDao.SYSTEM_USER);
		assertThat(due()).as("剛失敗、退避 1 分鐘內不是候選").doesNotContain(id);
		assertThat(dao.findDueIds(1000, 5, 0)).as("退避 0 分鐘時立刻是候選").contains(id);

		assertThat(dao.markFailedTry(id, "測試 2", 3)).isEqualTo(1);
		assertThat(dao.markFailedTry(id, "測試 3", 3)).isEqualTo(1);
		MailOutboxRow r3 = dao.findById(id);
		assertThat(r3.getTryCnt()).isEqualTo(3);
		assertThat(r3.getMailStatusCode()).isEqualTo(MailOutboxDao.STATUS_FAILED);
		assertThat(dao.findDueIds(1000, 5, 0)).doesNotContain(id);
		MailOutboxRow lockedFailed = tx.execute(status -> dao.lockPending(id));
		assertThat(lockedFailed).as("FAILED 不可再鎖為 PENDING").isNull();
		assertThat(dao.markFailedTry(id, "測試 4", 3)).as("非 PENDING 不再更新").isZero();
	}

	@Test
	void 標成已寄出後不再是候選() {
		long id = insertOne("S8IT 寄出");

		assertThat(dao.markSent(id, "<s8it@example.invalid>", null)).isEqualTo(1);
		MailOutboxRow row = dao.findById(id);
		assertThat(row.getMailStatusCode()).isEqualTo(MailOutboxDao.STATUS_SENT);
		assertThat(row.getTryCnt()).isEqualTo(1);
		assertThat(row.getSmtpMsgId()).isEqualTo("<s8it@example.invalid>");
		assertThat(row.getSendDate()).isNotNull();
		assertThat(row.getErrorText()).isNull();
		assertThat(due()).doesNotContain(id);
		assertThat(dao.markSent(id, "<again>", null)).isZero();
	}

	@Test
	void 業務交易rollback時outbox也沒有資料() {
		List<Long> before = idsByMarker();
		MailMessage message = MailMessage.of(List.of("s8it-a@example.invalid"), "S8IT rollback", "<p>x</p>",
				Map.of("event", "step-pending", "appId", "S8IT-0001"), MARKER);

		assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
			assertThat(outboxService.enqueue(message)).isTrue();
			throw new IllegalStateException("S8IT 模擬業務失敗");
		})).isInstanceOf(IllegalStateException.class).hasMessage("S8IT 模擬業務失敗");

		List<Long> after = idsByMarker();
		// 萬一沒有 rollback，也要記下來讓 cleanUp 刪掉，不在測試 DB 留殘列
		after.stream().filter(id -> !before.contains(id)).forEach(created::add);
		assertThat(after).as("enqueue 跟業務同一交易，業務 rollback 信也不留").isEqualTo(before);
	}
}
