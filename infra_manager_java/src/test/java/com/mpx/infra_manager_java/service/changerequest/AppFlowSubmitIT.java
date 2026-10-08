package com.mpx.infra_manager_java.service.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-07
// 變更說明: 新增：送審／撤回對真實測試 Oracle 的整合測試（S7 R1，只由 mvnw verify 執行；連線資訊 API 為空、
//           測試帳號 T0001 或 full 流程不存在時略過）。用 ITF 開頭的假單號，不經取號。
//           驗證：送審後 IM_APPR PENDING、關卡數＝流程啟用關卡數且恰一關 PENDING、每個待簽關卡至少一位候選人且都不是申請人、
//           事件 SUBMIT、ROW_VER_NO +1；撤回後 DRAFT、關卡全 CANCELLED、實例 RECALLED、事件 RECALL 帶原因；撤回後可再送審
//           （第二個實例）；必填缺漏時 400 且狀態仍 DRAFT、沒有殘留 IM_APPR。測試結束刪除本測試建的所有列
//           2026-10-08 S8a 階段末（Claude Opus 5.5）：清除時一併刪 ITF 單號寫進 IM_MAIL_OUTBOX 的信
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import com.mpx.common.db.DbClient;
import com.mpx.infra_manager_java.config.DbSchema;
import com.mpx.infra_manager_java.dao.changerequest.AppDao;
import com.mpx.infra_manager_java.dao.changerequest.AppWriteDao;
import com.mpx.infra_manager_java.dao.changerequest.ApprovalDao;
import com.mpx.infra_manager_java.model.DualRow;
import com.mpx.infra_manager_java.model.auth.AuthUser;
import com.mpx.infra_manager_java.model.changerequest.AppDraft;
import com.mpx.infra_manager_java.model.changerequest.AppRow;
import com.mpx.infra_manager_java.model.changerequest.ApprRow;
import com.mpx.infra_manager_java.model.changerequest.ApprStepRow;
import com.mpx.infra_manager_java.model.changerequest.CandRow;
import com.mpx.infra_manager_java.model.changerequest.CountRow;
import com.mpx.infra_manager_java.model.changerequest.EventRow;
import com.mpx.infra_manager_java.model.changerequest.FlowActionRequest;
import com.mpx.infra_manager_java.util.TaiwanTime;
import com.mpx.infra_manager_java.web.ApiBadRequestException;

@SpringBootTest
class AppFlowSubmitIT {

	private static final String USER_ID = "T0001";
	private static final AuthUser USER = new AuthUser(USER_ID, "wayne", "整合測試申請人", List.of("infra"), false);

	@Autowired
	private AppFlowService appFlowService;

	@Autowired
	private AppWriteDao appWriteDao;

	@Autowired
	private AppDao appDao;

	@Autowired
	private ApprovalDao approvalDao;

	@Autowired
	private DbClient dbClient;

	@Autowired
	private DbSchema schema;

	@Value("${db.connect.api.domain.path:}")
	private String apiUrl;

	@Value("${db.connect.itflow}")
	private String itflowDb;

	private String appId;
	private long flowStepCount;

	@BeforeEach
	void setUp() {
		assumeTrue(apiUrl != null && !apiUrl.isBlank(), "host.properties 未設定連線資訊 API，略過整合測試");
		assumeTrue(exists("SELECT COUNT(*) AS OK FROM " + schema.table("IM_USER") + " WHERE USER_ID = :u",
				Map.of("u", USER_ID)), "測試帳號不存在，略過");
		flowStepCount = count("SELECT COUNT(*) AS CNT FROM " + schema.table("IM_FLOW_STEP")
				+ " WHERE FLOW_ID = 'full' AND STATUS = 1 AND IS_NOTIFY_ONLY = 0", Map.of());
		assumeTrue(flowStepCount > 0, "full 流程沒有啟用關卡，略過");
		appId = "ITF" + (1_000_000_000L + ThreadLocalRandom.current().nextLong(9_000_000_000L));
	}

	@AfterEach
	void cleanUp() {
		if (appId == null) {
			return;
		}
		String appr = schema.table("IM_APPR");
		String step = schema.table("IM_APPR_STEP");
		Map<String, Object> p = Map.of("id", appId);
		dbClient.update(itflowDb, "DELETE FROM " + schema.table("IM_APPR_CAND_MAP") + " WHERE APPR_STEP_ID IN"
				+ " (SELECT S.APPR_STEP_ID FROM " + step + " S JOIN " + appr + " A ON A.APPR_ID = S.APPR_ID"
				+ " WHERE A.DOC_TYPE = 'CR' AND A.DOC_ID = :id)", p);
		dbClient.update(itflowDb, "DELETE FROM " + step + " WHERE APPR_ID IN (SELECT APPR_ID FROM " + appr
				+ " WHERE DOC_TYPE = 'CR' AND DOC_ID = :id)", p);
		dbClient.update(itflowDb, "DELETE FROM " + appr + " WHERE DOC_TYPE = 'CR' AND DOC_ID = :id", p);
		dbClient.update(itflowDb, "DELETE FROM " + schema.table("IM_APP_EVENT") + " WHERE APP_ID = :id", p);
		dbClient.update(itflowDb, "DELETE FROM " + schema.table("IM_APP") + " WHERE APP_ID = :id", p);
		// 流程事件寫進 outbox 的信（S8）；用本測試的單號前綴刪，連先前跑留下的也一併清掉
		dbClient.update(itflowDb, "DELETE FROM " + schema.table("IM_MAIL_OUTBOX")
				+ " WHERE JSON_VALUE(META_JSON, '$.appId') LIKE :prefix", Map.of("prefix", "ITF%"));
	}

	private boolean exists(String sql, Map<String, Object> params) {
		List<DualRow> rows = dbClient.query(itflowDb, sql, params, DualRow.class);
		return !rows.isEmpty() && rows.get(0).getOk() != null && rows.get(0).getOk() > 0;
	}

	private long count(String sql, Map<String, Object> params) {
		List<CountRow> rows = dbClient.query(itflowDb, sql, params, CountRow.class);
		return rows.isEmpty() || rows.get(0).getCnt() == null ? 0 : rows.get(0).getCnt();
	}

	/** 齊全可送審的草稿；tel 為 null 時故意缺聯絡電話 */
	private AppDraft draft(String tel) {
		return new AppDraft("S7 整合測試送審", "P3", "資訊處", tel, "it@example.com", true, false, "ONSITE", null, null, null,
				null, null, "主旨", null, null, null, null, null, null, null, null, null, List.of(), List.of(),
				List.of(), List.of(), List.of(), List.of());
	}

	private void createDraft(String tel) {
		appWriteDao.insertApp(appId, "full", USER_ID, TaiwanTime.startOf(TaiwanTime.today()), draft(tel));
	}

	private List<ApprRow> allApprs() {
		return dbClient.query(itflowDb, "SELECT APPR_ID, DOC_VER_NO, FLOW_ID, APPR_STATUS_CODE, START_DATE, CLOSE_DATE"
				+ " FROM " + schema.table("IM_APPR") + " WHERE DOC_TYPE = 'CR' AND DOC_ID = :id ORDER BY APPR_ID",
				Map.of("id", appId), ApprRow.class);
	}

	@Test
	void 送審後建立簽核實例_關卡與候選人展開且排除申請人_撤回後可再送審() {
		createDraft("1234");

		long ver = appFlowService.submit(appId, new FlowActionRequest(0L, null), USER);
		assertThat(ver).isEqualTo(1L);

		AppRow app = appDao.findById(appId).orElseThrow();
		assertThat(app.getAppStatusCode()).isEqualTo("IN_REVIEW");
		assertThat(app.getRowVerNo()).isEqualTo(1L);
		ApprRow appr = approvalDao.findCurrent(appId, app.getCurrVerNo()).orElseThrow();
		assertThat(appr.getApprStatusCode()).isEqualTo("PENDING");

		List<ApprStepRow> steps = approvalDao.findSteps(appr.getApprId());
		assertThat(steps).hasSize((int) count("SELECT COUNT(*) AS CNT FROM " + schema.table("IM_FLOW_STEP")
				+ " WHERE FLOW_ID = 'full' AND STATUS = 1", Map.of()));
		assertThat(steps).filteredOn(s -> "PENDING".equals(s.getStepStatusCode())).hasSize(1);
		assertThat(steps).filteredOn(s -> "PENDING".equals(s.getStepStatusCode())).first()
				.extracting(ApprStepRow::getSeqNo).isEqualTo(steps.stream().filter(s -> !"SKIPPED".equals(s.getStepStatusCode()))
						.mapToInt(ApprStepRow::getSeqNo).min().orElseThrow());
		assertThat(steps).filteredOn(s -> "WAITING".equals(s.getStepStatusCode())).hasSize((int) flowStepCount - 1);

		List<CandRow> cands = approvalDao.findCandidates(appr.getApprId());
		assertThat(cands).extracting(CandRow::getUserId).doesNotContain(USER_ID);
		for (ApprStepRow s : steps) {
			if ("SKIPPED".equals(s.getStepStatusCode())) {
				continue;
			}
			assertThat(cands).filteredOn(c -> c.getApprStepId().equals(s.getApprStepId()))
					.as("第 %d 關要有候選人", s.getSeqNo()).isNotEmpty();
		}

		List<EventRow> events = appDao.findEvents(appId);
		assertThat(events).extracting(EventRow::getEventCode).containsExactly("SUBMIT");

		long ver2 = appFlowService.recall(appId, new FlowActionRequest(1L, "先補資料"), USER);
		assertThat(ver2).isEqualTo(2L);
		app = appDao.findById(appId).orElseThrow();
		assertThat(app.getAppStatusCode()).isEqualTo("DRAFT");
		assertThat(approvalDao.findCurrent(appId, app.getCurrVerNo())).isEmpty();
		List<ApprRow> apprs = allApprs();
		assertThat(apprs).hasSize(1);
		assertThat(apprs.get(0).getApprStatusCode()).isEqualTo("RECALLED");
		assertThat(apprs.get(0).getCloseDate()).isNotNull();
		assertThat(approvalDao.findSteps(apprs.get(0).getApprId()))
				.filteredOn(s -> !"SKIPPED".equals(s.getStepStatusCode()))
				.extracting(ApprStepRow::getStepStatusCode).containsOnly("CANCELLED");
		events = appDao.findEvents(appId);
		assertThat(events).extracting(EventRow::getEventCode).containsExactlyInAnyOrder("SUBMIT", "RECALL");
		assertThat(events).filteredOn(e -> "RECALL".equals(e.getEventCode())).extracting(EventRow::getMemo)
				.containsExactly("先補資料");

		long ver3 = appFlowService.submit(appId, new FlowActionRequest(2L, null), USER);
		assertThat(ver3).isEqualTo(3L);
		assertThat(allApprs()).hasSize(2);
		assertThat(approvalDao.findCurrent(appId, 1).orElseThrow().getApprStatusCode()).isEqualTo("PENDING");
	}

	@Test
	void 必填缺漏時400_狀態仍是草稿_沒有殘留簽核實例() {
		createDraft(null);

		assertThatThrownBy(() -> appFlowService.submit(appId, new FlowActionRequest(0L, null), USER))
				.isInstanceOf(ApiBadRequestException.class).hasMessageContaining("聯絡電話");

		AppRow app = appDao.findById(appId).orElseThrow();
		assertThat(app.getAppStatusCode()).isEqualTo("DRAFT");
		assertThat(app.getRowVerNo()).isZero();
		assertThat(allApprs()).isEmpty();
		assertThat(appDao.findEvents(appId)).isEmpty();
	}
}
