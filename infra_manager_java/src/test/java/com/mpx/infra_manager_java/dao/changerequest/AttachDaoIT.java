package com.mpx.infra_manager_java.dao.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：附件上傳 SQL 對真實測試 Oracle 的整合測試（S6 回合三，只由 mvnw verify 執行；連線資訊 API 位址為空、
//           或測試帳號 T0001／各群組啟用選項不存在時略過）。用 ITW 開頭的假單號，不經取號、不碰計數列。
//           驗證：findAppState／lockApp（交易內 FOR UPDATE）讀得到狀態、申請人、版本，不存在回 null；
//           insertAppFile 寫入後以 FILE_PATH 查回 identity 產生的 ATTACH_ID，findByApp 看得到；countAppFiles 只算有效的 APP 附件。
//           測試結束刪除本測試建的附件列、子表與主檔。
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

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
import com.mpx.infra_manager_java.model.DualRow;
import com.mpx.infra_manager_java.model.changerequest.AppDraft;
import com.mpx.infra_manager_java.model.changerequest.AppLockRow;
import com.mpx.infra_manager_java.model.changerequest.AttachRow;
import com.mpx.infra_manager_java.model.changerequest.FormOptionRow;
import com.mpx.infra_manager_java.util.TaiwanTime;

@SpringBootTest
class AttachDaoIT {

	private static final String USER = "T0001";

	@Autowired
	private AttachDao attachDao;

	@Autowired
	private AppWriteDao appWriteDao;

	@Autowired
	private FormOptionDao formOptionDao;

	@Autowired
	private DbClient dbClient;

	@Autowired
	private DbSchema schema;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Value("${db.connect.api.domain.path:}")
	private String apiUrl;

	@Value("${db.connect.itflow}")
	private String itflowDb;

	private String appId;
	private List<FormOptionRow> options;

	@BeforeEach
	void setUp() {
		assumeTrue(apiUrl != null && !apiUrl.isBlank(), "host.properties 未設定連線資訊 API，略過整合測試");
		List<DualRow> users = dbClient.query(itflowDb, "SELECT COUNT(*) AS OK FROM " + schema.table("IM_USER")
				+ " WHERE USER_ID = :u", Map.of("u", USER), DualRow.class);
		assumeTrue(!users.isEmpty() && users.get(0).getOk() != null && users.get(0).getOk() > 0, "測試帳號不存在，略過");
		options = formOptionDao.findActive();
		for (String g : List.of("CATG", "CATG_ITEM", "REASON", "SCOPE")) {
			assumeTrue(first(g) != null, "群組 " + g + " 沒有啟用選項，略過");
		}
		appId = "ITW" + (1_000_000_000L + ThreadLocalRandom.current().nextLong(9_000_000_000L));
	}

	@AfterEach
	void cleanUp() {
		if (appId != null) {
			dbClient.update(itflowDb, "DELETE FROM " + schema.table("IM_ATTACH")
					+ " WHERE OWNER_TYPE = 'APP' AND OWNER_ID = :id", Map.of("id", appId));
			appWriteDao.deleteChildren(appId);
			dbClient.update(itflowDb, "DELETE FROM " + schema.table("IM_APP") + " WHERE APP_ID = :id",
					Map.of("id", appId));
		}
	}

	private Long first(String group) {
		return options.stream().filter(o -> group.equals(o.getGroupCode())).map(FormOptionRow::getFormOptionId)
				.findFirst().orElse(null);
	}

	private AppDraft draft() {
		return new AppDraft("S6 附件整合測試草稿", "P3", "資訊處", "1234", "it@example.com", true, false, "ONSITE", null,
				null, null, null, null, "主旨", "影響", "細節", "風險", null, null,
				Timestamp.valueOf(LocalDateTime.of(2099, 1, 2, 9, 0)),
				Timestamp.valueOf(LocalDateTime.of(2099, 1, 2, 18, 0)), new BigDecimal("1"), "不適用",
				List.of(first("CATG_ITEM")), List.of(), List.of(first("REASON")), List.of(first("SCOPE")), List.of(),
				List.of());
	}

	@Test
	void 主檔狀態查得到_交易內可鎖_不存在回null() {
		new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
			appWriteDao.insertApp(appId, "full", USER, TaiwanTime.startOf(TaiwanTime.today()), draft());
			appWriteDao.insertChildren(appId, USER, draft());
		});

		AppLockRow state = attachDao.findAppState(appId);
		assertThat(state.getAppStatusCode()).isEqualTo("DRAFT");
		assertThat(state.getApplyUserId()).isEqualTo(USER);
		assertThat(state.getRowVerNo()).isZero();

		AppLockRow locked = new TransactionTemplate(transactionManager).execute(s -> attachDao.lockApp(appId));
		assertThat(locked.getAppStatusCode()).isEqualTo("DRAFT");
		assertThat(attachDao.findAppState("ITW_NONE")).isNull();
		AppLockRow none = new TransactionTemplate(transactionManager).execute(s -> attachDao.lockApp("ITW_NONE"));
		assertThat(none).isNull();
	}

	@Test
	void 寫入附件後查回ATTACH_ID_計數只算有效APP附件() {
		new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
			appWriteDao.insertApp(appId, "full", USER, TaiwanTime.startOf(TaiwanTime.today()), draft());
			appWriteDao.insertChildren(appId, USER, draft());
		});
		assertThat(attachDao.countAppFiles(appId)).isZero();

		String path1 = appId + "/it-" + System.nanoTime() + ".pdf";
		AttachRow a = new TransactionTemplate(transactionManager).execute(s -> attachDao.insertAppFile(appId,
				"報價單.pdf", path1.substring(path1.indexOf('/') + 1), path1, 123L, "application/pdf",
				"a".repeat(64), USER));
		assertThat(a.getAttachId()).isNotNull();
		assertThat(a.getOwnerType()).isEqualTo("APP");
		assertThat(a.getOwnerId()).isEqualTo(appId);
		assertThat(a.getOrigFileName()).isEqualTo("報價單.pdf");
		assertThat(a.getFilePath()).isEqualTo(path1);
		assertThat(a.getFileByteQty()).isEqualTo(123L);
		assertThat(a.getCreateDate()).isNotNull();

		String path2 = appId + "/it-" + System.nanoTime() + ".txt";
		AttachRow b = attachDao.insertAppFile(appId, "說明.txt", path2.substring(path2.indexOf('/') + 1), path2, 5L,
				"text/plain", "b".repeat(64), USER);
		assertThat(b.getAttachId()).isNotEqualTo(a.getAttachId());
		assertThat(attachDao.countAppFiles(appId)).isEqualTo(2);
		assertThat(attachDao.findByApp(appId)).extracting(AttachRow::getAttachId).containsExactly(a.getAttachId(),
				b.getAttachId());

		dbClient.update(itflowDb, "UPDATE " + schema.table("IM_ATTACH") + " SET STATUS = 0 WHERE ATTACH_ID = :id",
				Map.of("id", b.getAttachId()));
		assertThat(attachDao.countAppFiles(appId)).isEqualTo(1);
	}
}
