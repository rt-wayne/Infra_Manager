package com.mpx.infra_manager_java.dao.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：草稿寫入對真實測試 Oracle 的整合測試（S6 回合二 b-1，只由 mvnw verify 執行；連線資訊 API 位址為空、
//           或測試帳號 T0001／各群組啟用選項不存在時略過）。用 ITW 開頭的假單號，不經取號、不碰計數列。
//           驗證：主檔＋6 張子表在同一交易寫入後讀得回來，CLOB 存 20000 個中文字（約 60 KB）不撞 ORA-01461／ORA-24816；
//           交易內丟例外時主檔與子表都不留；deleteChildren 只刪子表。測試結束刪除本測試建的單
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import com.mpx.infra_manager_java.model.changerequest.AppRow;
import com.mpx.infra_manager_java.model.changerequest.FormOptionRow;
import com.mpx.infra_manager_java.model.changerequest.OptionRow;
import com.mpx.infra_manager_java.util.TaiwanTime;
import com.mpx.infra_manager_java.util.TextLength;

@SpringBootTest
class AppWriteDaoIT {

	private static final String USER = "T0001";

	@Autowired
	private AppWriteDao appWriteDao;

	@Autowired
	private AppDao appDao;

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
		String longText = "字".repeat(TextLength.LIMIT_LONG);
		return new AppDraft("S6 整合測試草稿", "P3", "資訊處", "1234", "it@example.com", true, true, "REMOTE",
				"VPN", "廠商甲", "聯絡人", "02-1234", 2, "主旨", "影響".repeat(1000), longText, "風險", null, "其他",
				Timestamp.valueOf(LocalDateTime.of(2099, 1, 2, 9, 0)),
				Timestamp.valueOf(LocalDateTime.of(2099, 1, 2, 18, 0)), new BigDecimal("8.25"), "不適用",
				List.of(first("CATG_ITEM")), List.of(new AppDraft.CategoryOther(first("CATG"), "其他類別")),
				List.of(first("REASON")), List.of(first("SCOPE")),
				List.of(new AppDraft.Equipment("SW-01", "A001", "C9300", "SN1", "核心", "10.0.0.1"),
						new AppDraft.Equipment("SW-02", null, null, null, null, null)),
				List.of("關機", "更換", "開機"));
	}

	private void write(AppDraft d) {
		appWriteDao.insertApp(appId, "full", USER, TaiwanTime.startOf(TaiwanTime.today()), d);
		appWriteDao.insertChildren(appId, USER, d);
	}

	@Test
	void 主檔與子表寫入後讀得回來_長文CLOB不出錯() {
		new TransactionTemplate(transactionManager).executeWithoutResult(s -> write(draft()));

		Optional<AppRow> row = appDao.findById(appId);
		assertThat(row).isPresent();
		AppRow a = row.get();
		assertThat(a.getAppTitle()).isEqualTo("S6 整合測試草稿");
		assertThat(a.getAppStatusCode()).isEqualTo("DRAFT");
		assertThat(a.getSourceCode()).isEqualTo("ONLINE");
		assertThat(a.getRowVerNo()).isZero();
		assertThat(a.getWorkDetail()).hasSize(TextLength.LIMIT_LONG);
		assertThat(a.getImpactDesc()).hasSize(2000);
		assertThat(a.getRollBackPlan()).isNull();
		assertThat(a.getEstHourQty()).isEqualByComparingTo("8.25");

		List<OptionRow> opts = appDao.findOptions(appId);
		assertThat(opts).extracting(OptionRow::getGroupCode).containsExactlyInAnyOrder("CATG", "CATG_ITEM", "REASON",
				"SCOPE");
		assertThat(opts).filteredOn(o -> "CATG".equals(o.getGroupCode())).extracting(OptionRow::getOtherText)
				.containsExactly("其他類別");
		assertThat(appDao.findEquipments(appId)).hasSize(2);
		assertThat(appDao.findPlanSteps(appId)).extracting(p -> p.getStepText()).containsExactly("關機", "更換", "開機");

		appWriteDao.deleteChildren(appId);
		assertThat(appDao.findOptions(appId)).isEmpty();
		assertThat(appDao.findEquipments(appId)).isEmpty();
		assertThat(appDao.findPlanSteps(appId)).isEmpty();
		assertThat(appDao.findById(appId)).isPresent();
	}

	@Test
	void 交易內丟例外時主檔與子表都不留() {
		assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
			write(draft());
			throw new IllegalStateException("故意回滾");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(appDao.findById(appId)).isEmpty();
		assertThat(appDao.findEquipments(appId)).isEmpty();
		assertThat(appDao.findPlanSteps(appId)).isEmpty();
	}
}
