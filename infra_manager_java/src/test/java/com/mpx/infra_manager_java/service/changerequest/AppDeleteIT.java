package com.mpx.infra_manager_java.service.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：S9 回合二「刪除＋歷次簽核」對真實測試 Oracle 的整合測試（只由 mvnw verify 執行；連線資訊 API 為空、
//           測試帳號 T0001 或 full 流程不存在時略過）。用 S9DT 開頭的假單號，比照 AppFlowResubmitIT。
//           刪除：admin 刪審核中的單 → 主檔 STATUS 0、DELETE_* 四欄有值（ADMIN）、簽核實例 CANCELLED、無未結關卡、
//           事件 DELETE 帶原因、第一關候選人待辦數減一、檢視 API 404、再刪 404。
//           權限：申請人刪草稿成功（APPLICANT_PRE_REVIEW）；申請人刪已退件 403 且不寫入；confirmId 不符 400。
//           歷次簽核：送審 → 撤回 → 再送審 → 退件 → 補件後，檢視頁 approvalHistory 依序是 v1 RECALLED、v1 REJECTED，
//           目前實例是 v2 PENDING（第 101 項 ⑤ 撤回那一輪也看得到）。
//           併發：同 rowVerNo 的刪除與簽核同時出發，恰一成功，另一 409 或 404，結果與勝方一致。
//           測試結束刪除本測試建的所有列（含 IM_APP_VER 與六張子表）
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;

import com.mpx.common.db.DbClient;
import com.mpx.infra_manager_java.config.DbSchema;
import com.mpx.infra_manager_java.dao.changerequest.AppDao;
import com.mpx.infra_manager_java.dao.changerequest.AppWriteDao;
import com.mpx.infra_manager_java.dao.changerequest.ApprovalDao;
import com.mpx.infra_manager_java.model.DualRow;
import com.mpx.infra_manager_java.model.auth.AuthUser;
import com.mpx.infra_manager_java.model.changerequest.AppDetail;
import com.mpx.infra_manager_java.model.changerequest.AppDraft;
import com.mpx.infra_manager_java.model.changerequest.AppDraftRequest;
import com.mpx.infra_manager_java.model.changerequest.ApprRow;
import com.mpx.infra_manager_java.model.changerequest.ApprStepRow;
import com.mpx.infra_manager_java.model.changerequest.CandRow;
import com.mpx.infra_manager_java.model.changerequest.DecisionRequest;
import com.mpx.infra_manager_java.model.changerequest.DeleteRequest;
import com.mpx.infra_manager_java.model.changerequest.FlowActionRequest;
import com.mpx.infra_manager_java.model.changerequest.ResubmitRequest;
import com.mpx.infra_manager_java.util.TaiwanTime;
import com.mpx.infra_manager_java.web.ApiBadRequestException;
import com.mpx.infra_manager_java.web.ApiConflictException;
import com.mpx.infra_manager_java.web.ApiNotFoundException;

@SpringBootTest
class AppDeleteIT {

	private static final String APPLICANT_ID = "T0001";
	private static final AuthUser APPLICANT = new AuthUser(APPLICANT_ID, "wayne", "整合測試申請人", List.of("infra"),
			false);
	private static final AuthUser ADMIN = new AuthUser("A0001", "admin", "管理員", List.of("admin"), false);

	@Autowired
	private AppDeleteService appDeleteService;

	@Autowired
	private AppFlowService appFlowService;

	@Autowired
	private AppQueryService appQueryService;

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

	@BeforeEach
	void setUp() {
		assumeTrue(apiUrl != null && !apiUrl.isBlank(), "host.properties 未設定連線資訊 API，略過整合測試");
		assumeTrue(exists("SELECT COUNT(*) AS OK FROM " + schema.table("IM_USER") + " WHERE USER_ID = :u",
				Map.of("u", APPLICANT_ID)), "測試帳號不存在，略過");
		assumeTrue(exists("SELECT COUNT(*) AS OK FROM " + schema.table("IM_FLOW_STEP")
				+ " WHERE FLOW_ID = 'full' AND STATUS = 1 AND IS_NOTIFY_ONLY = 0", Map.of()), "full 流程沒有啟用關卡，略過");
		appId = "S9DT" + (100_000_000L + ThreadLocalRandom.current().nextLong(900_000_000L));
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
		dbClient.update(itflowDb, "DELETE FROM " + schema.table("IM_APP_VER") + " WHERE APP_ID = :id", p);
		appWriteDao.deleteChildren(appId);
		dbClient.update(itflowDb, "DELETE FROM " + schema.table("IM_APP") + " WHERE APP_ID = :id", p);
	}

	private boolean exists(String sql, Map<String, Object> params) {
		List<DualRow> rows = dbClient.query(itflowDb, sql, params, DualRow.class);
		return !rows.isEmpty() && rows.get(0).getOk() != null && rows.get(0).getOk() > 0;
	}

	private long countSql(String sql) {
		return dbClient.query(itflowDb, sql, Map.of("id", appId), DualRow.class).get(0).getOk();
	}

	private static AuthUser user(String userId) {
		return new AuthUser(userId, userId.toLowerCase(), userId, List.of(), false);
	}

	/** 建齊全草稿（rowVerNo 0） */
	private void createDraft() {
		AppDraft d = new AppDraft("S9 整合測試刪除", "P3", "資訊處", "1234", "it@example.com", true, false, "ONSITE", null,
				null, null, null, null, "主旨", null, null, null, null, null, null, null, null, null, List.of(), List.of(),
				List.of(), List.of(), List.of(), List.of());
		appWriteDao.insertApp(appId, "full", APPLICANT_ID, TaiwanTime.startOf(TaiwanTime.today()), d);
	}

	/** 目前版次第一關的第一位候選人 */
	private String firstCandidate(int verNo) {
		ApprRow appr = approvalDao.findCurrent(appId, verNo).orElseThrow();
		ApprStepRow current = DecisionPolicy.currentStep(approvalDao.findSteps(appr.getApprId()));
		List<String> cands = approvalDao.findCandidates(appr.getApprId()).stream()
				.filter(c -> current.getApprStepId().equals(c.getApprStepId())).map(CandRow::getUserId).toList();
		assertThat(cands).isNotEmpty();
		return cands.get(0);
	}

	private static AppDraftRequest fullForm() {
		return new AppDraftRequest("S9 整合測試刪除（已補）", "P3", new AppDraftRequest.Applicant("資訊處", "1234", "it@example.com"),
				true, false, "ONSITE", null, null, "新主旨", null, null, null, null, null, null, null, null, null, List.of(),
				List.of(), null, null, null);
	}

	@Test
	void admin刪審核中_主檔軟刪_簽核取消_事件DELETE_待辦減一_檢視404() {
		createDraft();
		long ver = appFlowService.submit(appId, new FlowActionRequest(0L, null), APPLICANT);
		String cand = firstCandidate(1);
		long todoBefore = appDao.countMine(cand);
		long apprId = approvalDao.findCurrent(appId, 1).orElseThrow().getApprId();

		appDeleteService.delete(appId, new DeleteRequest(ver, appId, "  廠商取消施工  "), ADMIN);

		assertThat(exists("SELECT COUNT(*) AS OK FROM " + schema.table("IM_APP") + " WHERE APP_ID = :id AND STATUS = 0"
				+ " AND DELETE_DATE IS NOT NULL AND DELETE_USER_ID = :u AND DELETE_REASON = :r AND DELETE_MODE_CODE = 'ADMIN'"
				+ " AND ROW_VER_NO = :v", Map.of("id", appId, "u", ADMIN.userId(), "r", "廠商取消施工", "v", ver + 1)))
				.as("主檔軟刪四欄").isTrue();
		assertThat(exists("SELECT COUNT(*) AS OK FROM " + schema.table("IM_APPR")
				+ " WHERE APPR_ID = :a AND APPR_STATUS_CODE = 'CANCELLED' AND CLOSE_DATE IS NOT NULL", Map.of("a", apprId)))
				.as("簽核實例 CANCELLED").isTrue();
		assertThat(countSql("SELECT COUNT(*) AS OK FROM " + schema.table("IM_APPR_STEP") + " S JOIN " + schema.table("IM_APPR")
				+ " A ON A.APPR_ID = S.APPR_ID WHERE A.DOC_ID = :id AND S.STEP_STATUS_CODE IN ('PENDING', 'WAITING')"))
				.as("沒有未結關卡").isZero();
		assertThat(exists("SELECT COUNT(*) AS OK FROM " + schema.table("IM_APP_EVENT") + " WHERE APP_ID = :id"
				+ " AND EVENT_CODE = 'DELETE' AND APP_VER_NO = 1 AND USER_ID = :u AND DBMS_LOB.COMPARE(MEMO, TO_CLOB(:r)) = 0",
				Map.of("id", appId, "u", ADMIN.userId(), "r", "廠商取消施工"))).as("事件 DELETE").isTrue();
		assertThat(appDao.countMine(cand)).as("待辦數").isEqualTo(todoBefore - 1);
		assertThatThrownBy(() -> appQueryService.detail(appId, ADMIN)).isInstanceOf(ApiNotFoundException.class);
		assertThatThrownBy(() -> appDeleteService.delete(appId, new DeleteRequest(ver + 1, appId, "再刪"), ADMIN))
				.isInstanceOf(ApiNotFoundException.class);
	}

	@Test
	void 申請人刪草稿成功_刪已退件403_確認編號不符400() {
		createDraft();
		assertThatThrownBy(() -> appDeleteService.delete(appId, new DeleteRequest(0L, "S9DT000000000", "x"), APPLICANT))
				.isInstanceOf(ApiBadRequestException.class).hasMessage(AppDeleteService.MSG_CONFIRM_MISMATCH);

		long ver = appFlowService.submit(appId, new FlowActionRequest(0L, null), APPLICANT);
		ver = appFlowService.decide(appId, new DecisionRequest(ver, "REJECT", "資料不全"), user(firstCandidate(1)));
		long rejectedVer = ver;
		assertThatThrownBy(() -> appDeleteService.delete(appId, new DeleteRequest(rejectedVer, appId, "不要了"), APPLICANT))
				.isInstanceOf(AccessDeniedException.class);
		assertThat(exists("SELECT COUNT(*) AS OK FROM " + schema.table("IM_APP")
				+ " WHERE APP_ID = :id AND STATUS = 1 AND ROW_VER_NO = :v AND DELETE_DATE IS NULL",
				Map.of("id", appId, "v", rejectedVer))).as("403 時取鎖要 rollback").isTrue();

		// 另建一張草稿給申請人刪
		cleanUp();
		createDraft();
		appDeleteService.delete(appId, new DeleteRequest(0L, appId, "建錯單"), APPLICANT);
		assertThat(exists("SELECT COUNT(*) AS OK FROM " + schema.table("IM_APP") + " WHERE APP_ID = :id AND STATUS = 0"
				+ " AND DELETE_MODE_CODE = 'APPLICANT_PRE_REVIEW' AND DELETE_USER_ID = :u",
				Map.of("id", appId, "u", APPLICANT_ID))).isTrue();
	}

	@Test
	void 歷次簽核_撤回那一輪與退件那一輪都在approvalHistory() {
		createDraft();
		long ver = appFlowService.submit(appId, new FlowActionRequest(0L, null), APPLICANT);
		ver = appFlowService.recall(appId, new FlowActionRequest(ver, "先收回"), APPLICANT);

		// 撤回後（草稿、沒有目前實例）就看得到被撤回那一輪
		AppDetail draft = appQueryService.detail(appId, APPLICANT);
		assertThat(draft.approval().apprId()).isNull();
		assertThat(draft.approvalHistory()).extracting(AppDetail.PastApproval::statusCode).containsExactly("RECALLED");

		ver = appFlowService.submit(appId, new FlowActionRequest(ver, null), APPLICANT);
		ver = appFlowService.decide(appId, new DecisionRequest(ver, "REJECT", "資料不全"), user(firstCandidate(1)));
		appFlowService.resubmit(appId, new ResubmitRequest(ver, "已補", fullForm()), APPLICANT);

		AppDetail d = appQueryService.detail(appId, APPLICANT);
		assertThat(d.verNo()).isEqualTo(2);
		assertThat(d.approval().statusCode()).isEqualTo("PENDING");
		List<AppDetail.PastApproval> history = d.approvalHistory();
		assertThat(history).extracting(AppDetail.PastApproval::statusCode).containsExactly("RECALLED", "REJECTED");
		assertThat(history).extracting(AppDetail.PastApproval::verNo).containsExactly(1, 1);
		assertThat(history).extracting(AppDetail.PastApproval::apprId).doesNotContain(d.approval().apprId());
		assertThat(history.get(0).steps()).isNotEmpty().allSatisfy(s -> assertThat(s.statusCode()).isEqualTo("CANCELLED"));
		assertThat(history.get(1).steps()).filteredOn(s -> "REJECTED".equals(s.statusCode())).hasSize(1).first()
				.satisfies(s -> assertThat(s.memo()).isEqualTo("資料不全"));
		assertThat(history.get(1).closedAt()).isNotNull();
	}

	@Test
	@Timeout(value = 60, unit = TimeUnit.SECONDS)
	void 刪除與簽核同rowVerNo同時出發_恰一成功_結果與勝方一致() throws Exception {
		createDraft();
		long ver = appFlowService.submit(appId, new FlowActionRequest(0L, null), APPLICANT);
		String cand = firstCandidate(1);
		long apprId = approvalDao.findCurrent(appId, 1).orElseThrow().getApprId();

		CountDownLatch go = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			Future<Object> del = pool.submit(() -> {
				go.await();
				try {
					appDeleteService.delete(appId, new DeleteRequest(ver, appId, "併發刪除"), ADMIN);
					return "OK";
				} catch (RuntimeException e) {
					return e;
				}
			});
			Future<Object> dec = pool.submit(() -> {
				go.await();
				try {
					return appFlowService.decide(appId, new DecisionRequest(ver, "APPROVE", null), user(cand));
				} catch (RuntimeException e) {
					return e;
				}
			});
			go.countDown();
			Object delResult = del.get(30, TimeUnit.SECONDS);
			Object decResult = dec.get(30, TimeUnit.SECONDS);

			boolean deleteWon = "OK".equals(delResult);
			boolean decideWon = decResult instanceof Long;
			assertThat(deleteWon ^ decideWon).as("恰一成功：刪除=%s 簽核=%s", delResult, decResult).isTrue();
			Object loser = deleteWon ? decResult : delResult;
			assertThat(loser).as("另一方 409 或 404：%s", loser)
					.isInstanceOfAny(ApiConflictException.class, ApiNotFoundException.class);

			if (deleteWon) {
				assertThat(exists("SELECT COUNT(*) AS OK FROM " + schema.table("IM_APP") + " WHERE APP_ID = :id AND STATUS = 0",
						Map.of("id", appId))).isTrue();
				assertThat(exists("SELECT COUNT(*) AS OK FROM " + schema.table("IM_APPR")
						+ " WHERE APPR_ID = :a AND APPR_STATUS_CODE = 'CANCELLED'", Map.of("a", apprId))).isTrue();
				assertThat(countSql("SELECT COUNT(*) AS OK FROM " + schema.table("IM_APPR_STEP") + " S JOIN "
						+ schema.table("IM_APPR") + " A ON A.APPR_ID = S.APPR_ID WHERE A.DOC_ID = :id"
						+ " AND S.STEP_STATUS_CODE = 'APPROVED'")).as("刪除勝出時不能有同意紀錄").isZero();
			} else {
				assertThat(appDao.findById(appId)).as("簽核勝出時單仍在").isPresent();
				assertThat(countSql("SELECT COUNT(*) AS OK FROM " + schema.table("IM_APPR_STEP") + " S JOIN "
						+ schema.table("IM_APPR") + " A ON A.APPR_ID = S.APPR_ID WHERE A.DOC_ID = :id"
						+ " AND S.STEP_STATUS_CODE = 'APPROVED'")).isEqualTo(1);
				assertThat(countSql("SELECT COUNT(*) AS OK FROM " + schema.table("IM_APP_EVENT")
						+ " WHERE APP_ID = :id AND EVENT_CODE = 'DELETE'")).isZero();
			}
		} finally {
			pool.shutdownNow();
		}
	}
}
