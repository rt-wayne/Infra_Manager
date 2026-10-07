package com.mpx.infra_manager_java.service.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-07
// 變更說明: 新增：S7 的兩個完成條件對真實測試 Oracle 的整合測試（只由 mvnw verify 執行；連線資訊 API 為空、
//           測試帳號 T0001 或 full 流程不存在時略過）。用 S7IT 開頭的假單號。
//           完成條件一：full 流程每一關都由該關第一位候選人同意，走完後主檔 APPROVED、實例 APPROVED、所有待簽關卡 APPROVED
//           且簽核人正確；每關簽前 countMine 對候選人為 1、簽後為 0。
//           完成條件二：先建一位擁有第一關角色的臨時帳號（種子只有一位 idc_admin），兩位候選人用同一個 rowVerNo 以
//           CountDownLatch 同時出發，恰一人成功、另一人 ApiConflictException，
//           關卡 USER_ID 是成功者、主檔版本只加一。測試類不加 @Transactional（兩個執行緒要各自 commit 才看得到鎖）。
//           另驗退件：第一關退件後主檔 REJECTED、剩餘關卡 SKIPPED、實例 REJECTED；非候選人簽 403。
//           測試結束刪除本測試建的所有列
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
import com.mpx.infra_manager_java.model.changerequest.AppDraft;
import com.mpx.infra_manager_java.model.changerequest.AppRow;
import com.mpx.infra_manager_java.model.changerequest.ApprRow;
import com.mpx.infra_manager_java.model.changerequest.ApprStepRow;
import com.mpx.infra_manager_java.model.changerequest.CandRow;
import com.mpx.infra_manager_java.model.changerequest.DecisionRequest;
import com.mpx.infra_manager_java.model.changerequest.FlowActionRequest;
import com.mpx.infra_manager_java.util.TaiwanTime;
import com.mpx.infra_manager_java.web.ApiConflictException;

@SpringBootTest
class AppFlowDecisionIT {

	private static final String APPLICANT_ID = "T0001";
	private static final AuthUser APPLICANT = new AuthUser(APPLICANT_ID, "wayne", "整合測試申請人", List.of("infra"),
			false);

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

	/** 併發測試用的臨時第二位候選人（第一關角色），只在該測試建立，結束刪除 */
	private String extraUserId;

	@BeforeEach
	void setUp() {
		assumeTrue(apiUrl != null && !apiUrl.isBlank(), "host.properties 未設定連線資訊 API，略過整合測試");
		assumeTrue(exists("SELECT COUNT(*) AS OK FROM " + schema.table("IM_USER") + " WHERE USER_ID = :u",
				Map.of("u", APPLICANT_ID)), "測試帳號不存在，略過");
		assumeTrue(exists("SELECT COUNT(*) AS OK FROM " + schema.table("IM_FLOW_STEP")
				+ " WHERE FLOW_ID = 'full' AND STATUS = 1 AND IS_NOTIFY_ONLY = 0", Map.of()), "full 流程沒有啟用關卡，略過");
		appId = "S7IT" + (100_000_000L + ThreadLocalRandom.current().nextLong(900_000_000L));
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
		if (extraUserId != null) {
			Map<String, Object> u = Map.of("u", extraUserId);
			dbClient.update(itflowDb, "DELETE FROM " + schema.table("IM_USER_ROLE_MAP") + " WHERE USER_ID = :u", u);
			dbClient.update(itflowDb, "DELETE FROM " + schema.table("IM_USER") + " WHERE USER_ID = :u", u);
		}
	}

	/** 建一位擁有 full 流程第一關角色的臨時帳號（不可登入：無密碼雜湊），讓第一關至少有兩位候選人 */
	private void createExtraFirstStepCandidate() {
		extraUserId = "S7ITU" + ThreadLocalRandom.current().nextInt(100_000_000, 1_000_000_000);
		String flowStep = schema.table("IM_FLOW_STEP");
		dbClient.update(itflowDb, "INSERT INTO " + schema.table("IM_USER")
				+ " (USER_ID, LOGIN_ID, USER_NAME, STATUS, CREATE_BY) VALUES (:u, :login, 'S7 併發測試候選人', 1, 'S7IT')",
				Map.of("u", extraUserId, "login", extraUserId.toLowerCase()));
		int mapped = dbClient.update(itflowDb, "INSERT INTO " + schema.table("IM_USER_ROLE_MAP")
				+ " (USER_ID, ROLE_ID, STATUS, CREATE_BY) SELECT :u, FS.ROLE_ID, 1, 'S7IT' FROM " + flowStep + " FS"
				+ " WHERE FS.FLOW_ID = 'full' AND FS.STATUS = 1 AND FS.APPR_TYPE = 'ROLE' AND FS.SEQ_NO ="
				+ " (SELECT MIN(F2.SEQ_NO) FROM " + flowStep + " F2 WHERE F2.FLOW_ID = 'full' AND F2.STATUS = 1"
				+ " AND F2.IS_NOTIFY_ONLY = 0)", Map.of("u", extraUserId));
		assumeTrue(mapped == 1, "full 流程第一關不是角色關卡，無法補候選人，略過併發測試");
	}

	private boolean exists(String sql, Map<String, Object> params) {
		List<DualRow> rows = dbClient.query(itflowDb, sql, params, DualRow.class);
		return !rows.isEmpty() && rows.get(0).getOk() != null && rows.get(0).getOk() > 0;
	}

	private static AuthUser user(String userId) {
		return new AuthUser(userId, userId.toLowerCase(), userId, List.of(), false);
	}

	/** 建齊全草稿並送審，回送審後的 rowVerNo（1） */
	private long createAndSubmit() {
		AppDraft d = new AppDraft("S7 整合測試簽核", "P3", "資訊處", "1234", "it@example.com", true, false, "ONSITE", null,
				null, null, null, null, "主旨", null, null, null, null, null, null, null, null, null, List.of(),
				List.of(), List.of(), List.of(), List.of(), List.of());
		appWriteDao.insertApp(appId, "full", APPLICANT_ID, TaiwanTime.startOf(TaiwanTime.today()), d);
		return appFlowService.submit(appId, new FlowActionRequest(0L, null), APPLICANT);
	}

	private ApprRow currentAppr() {
		AppRow app = appDao.findById(appId).orElseThrow();
		return approvalDao.findCurrent(appId, app.getCurrVerNo()).orElseThrow();
	}

	private ApprStepRow pendingStep(long apprId) {
		return DecisionPolicy.currentStep(approvalDao.findSteps(apprId));
	}

	private List<String> candidatesOf(long apprId, long apprStepId) {
		return approvalDao.findCandidates(apprId).stream().filter(c -> apprStepId == c.getApprStepId())
				.map(CandRow::getUserId).toList();
	}

	@Test
	void 完成條件一_full流程每關依序同意後結案APPROVED_待辦數隨關卡增減() {
		long ver = createAndSubmit();
		long apprId = currentAppr().getApprId();
		List<ApprStepRow> steps = approvalDao.findSteps(apprId);
		List<ApprStepRow> open = steps.stream().filter(s -> !"SKIPPED".equals(s.getStepStatusCode())).toList();
		assertThat(open).isNotEmpty();
		List<String> signers = new ArrayList<>();

		for (int i = 0; i < open.size(); i++) {
			ApprStepRow current = pendingStep(apprId);
			assertThat(current).as("第 %d 次簽核時要有 PENDING 關卡", i + 1).isNotNull();
			assertThat(current.getSeqNo()).isEqualTo(open.get(i).getSeqNo());
			List<String> cands = candidatesOf(apprId, current.getApprStepId());
			assertThat(cands).isNotEmpty();
			String signer = cands.get(0);
			long before = appDao.countMine(signer);
			assertThat(before).as("%s 簽前待辦應包含本單", signer).isGreaterThanOrEqualTo(1);

			ver = appFlowService.decide(appId, new DecisionRequest(ver, "APPROVE", i == 0 ? null : "第 " + (i + 1) + " 關同意"),
					user(signer));
			signers.add(signer);

			// full 流程 governance 出現兩次：若簽核人同時也是下一關候選人，待辦數不變；否則減一
			ApprStepRow next = pendingStep(apprId);
			boolean alsoNext = next != null && candidatesOf(apprId, next.getApprStepId()).contains(signer);
			assertThat(appDao.countMine(signer)).isEqualTo(alsoNext ? before : before - 1);
			AppRow app = appDao.findById(appId).orElseThrow();
			assertThat(app.getRowVerNo()).isEqualTo(ver);
			if (i < open.size() - 1) {
				assertThat(app.getAppStatusCode()).isEqualTo("IN_REVIEW");
			}
		}

		AppRow app = appDao.findById(appId).orElseThrow();
		assertThat(app.getAppStatusCode()).isEqualTo("APPROVED");
		ApprRow appr = approvalDao.findCurrent(appId, app.getCurrVerNo()).orElseThrow();
		assertThat(appr.getApprStatusCode()).isEqualTo("APPROVED");
		assertThat(appr.getCloseDate()).isNotNull();
		List<ApprStepRow> after = approvalDao.findSteps(apprId).stream()
				.filter(s -> !"SKIPPED".equals(s.getStepStatusCode())).toList();
		assertThat(after).extracting(ApprStepRow::getStepStatusCode).containsOnly("APPROVED");
		assertThat(after).extracting(ApprStepRow::getUserId).containsExactlyElementsOf(signers);
		assertThat(after.get(0).getMemo()).isEqualTo("同意");
		assertThat(after).allSatisfy(s -> assertThat(s.getDecideDate()).isNotNull());
		assertThat(pendingStep(apprId)).isNull();
	}

	@Test
	@Timeout(value = 60, unit = TimeUnit.SECONDS)
	void 完成條件二_兩位候選人同時簽同一關_恰一人成功另一人409() throws Exception {
		createExtraFirstStepCandidate();
		long ver = createAndSubmit();
		long apprId = currentAppr().getApprId();
		ApprStepRow current = pendingStep(apprId);
		List<String> cands = candidatesOf(apprId, current.getApprStepId());
		assertThat(cands).as("第一關候選人要含臨時帳號").contains(extraUserId).hasSizeGreaterThanOrEqualTo(2);
		String a = cands.get(0);
		String b = cands.get(1);

		CountDownLatch go = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			final long sameVer = ver;
			List<Future<Object>> results = new ArrayList<>();
			for (String signer : List.of(a, b)) {
				results.add(pool.submit(() -> {
					go.await();
					try {
						return appFlowService.decide(appId, new DecisionRequest(sameVer, "APPROVE", signer + " 同意"),
								user(signer));
					} catch (RuntimeException e) {
						return e;
					}
				}));
			}
			go.countDown();
			List<Object> outcomes = new ArrayList<>();
			for (Future<Object> f : results) {
				outcomes.add(f.get(30, TimeUnit.SECONDS));
			}

			long ok = outcomes.stream().filter(o -> o instanceof Long).count();
			long conflict = outcomes.stream().filter(o -> o instanceof ApiConflictException).count();
			assertThat(ok).as("恰一人成功：%s", outcomes).isEqualTo(1);
			assertThat(conflict).as("另一人 409：%s", outcomes).isEqualTo(1);
			int winnerIdx = outcomes.get(0) instanceof Long ? 0 : 1;
			String winner = winnerIdx == 0 ? a : b;

			AppRow app = appDao.findById(appId).orElseThrow();
			assertThat(app.getRowVerNo()).isEqualTo(ver + 1);
			assertThat(app.getAppStatusCode()).isEqualTo("IN_REVIEW");
			ApprStepRow decided = approvalDao.findSteps(apprId).stream()
					.filter(s -> s.getApprStepId().equals(current.getApprStepId())).findFirst().orElseThrow();
			assertThat(decided.getStepStatusCode()).isEqualTo("APPROVED");
			assertThat(decided.getUserId()).isEqualTo(winner);
			assertThat(decided.getMemo()).isEqualTo(winner + " 同意");
			assertThat(approvalDao.findSteps(apprId)).filteredOn(s -> "APPROVED".equals(s.getStepStatusCode())).hasSize(1);
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	void 退件後主檔REJECTED_剩餘關卡SKIPPED_非候選人簽403() {
		long ver = createAndSubmit();
		long apprId = currentAppr().getApprId();
		ApprStepRow current = pendingStep(apprId);
		List<String> cands = candidatesOf(apprId, current.getApprStepId());

		assertThatThrownBy(() -> appFlowService.decide(appId, new DecisionRequest(ver, "APPROVE", null), APPLICANT))
				.isInstanceOf(AccessDeniedException.class);
		assertThat(appDao.findById(appId).orElseThrow().getRowVerNo()).as("403 時版本加一要一起 rollback").isEqualTo(ver);

		long before = appDao.countMine(cands.get(0));
		long ver2 = appFlowService.decide(appId, new DecisionRequest(ver, "REJECT", "資料不全"), user(cands.get(0)));
		assertThat(ver2).isEqualTo(ver + 1);
		assertThat(appDao.countMine(cands.get(0))).as("退件後本單離開待辦").isEqualTo(before - 1);

		AppRow app = appDao.findById(appId).orElseThrow();
		assertThat(app.getAppStatusCode()).isEqualTo("REJECTED");
		ApprRow appr = approvalDao.findCurrent(appId, app.getCurrVerNo()).orElseThrow();
		assertThat(appr.getApprStatusCode()).isEqualTo("REJECTED");
		List<ApprStepRow> steps = approvalDao.findSteps(apprId);
		assertThat(steps).filteredOn(s -> s.getApprStepId().equals(current.getApprStepId())).first()
				.satisfies(s -> {
					assertThat(s.getStepStatusCode()).isEqualTo("REJECTED");
					assertThat(s.getMemo()).isEqualTo("資料不全");
				});
		assertThat(steps).filteredOn(s -> !s.getApprStepId().equals(current.getApprStepId()))
				.extracting(ApprStepRow::getStepStatusCode).containsOnly("SKIPPED");

		assertThatThrownBy(() -> appFlowService.decide(appId, new DecisionRequest(ver2, "APPROVE", null), user(cands.get(0))))
				.isInstanceOf(ApiConflictException.class).hasMessage(AppFlowService.MSG_DECIDE_NOT_IN_REVIEW);
	}
}
