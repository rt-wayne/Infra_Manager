package com.mpx.infra_manager_java.service.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-07
// 變更說明: 新增：S9 回合一「補件」對真實測試 Oracle 的整合測試（只由 mvnw verify 執行；連線資訊 API 為空、
//           測試帳號 T0001 或 full 流程不存在時略過）。用 S9IT 開頭的假單號，比照 AppFlowDecisionIT。
//           完成條件：v1 送審 → 第一關退件 → 申請人改標題補件後，主檔 IN_REVIEW、CURR_VER_NO 2、rowVerNo 加一；
//           v1 的簽核實例仍是 REJECTED 且查得到退件意見；v2 有新的 PENDING 實例；IM_APP_VER 恰一列（APP_VER_NO 1、
//           CLOSE REJECTED、VER_REASON 是退件意見、FORM_JSON 含舊標題）；子表依新表單重建；有 RESUBMIT 事件。
//           另驗：必填缺漏 400 整筆不留殘；非申請人與 admin 403、非 REJECTED 409；兩個補件同 rowVerNo 同時出發恰一成功。
//           測試結束刪除本測試建的所有列（含 IM_APP_VER 與六張子表）
//           2026-10-08 S8a 階段末 review（Claude Opus 5.5）：清除時一併刪 S9IT 單號寫進 IM_MAIL_OUTBOX 的信
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
import com.mpx.infra_manager_java.dao.changerequest.AppVerDao;
import com.mpx.infra_manager_java.dao.changerequest.AppWriteDao;
import com.mpx.infra_manager_java.dao.changerequest.ApprovalDao;
import com.mpx.infra_manager_java.model.DualRow;
import com.mpx.infra_manager_java.model.auth.AuthUser;
import com.mpx.infra_manager_java.model.changerequest.AppDraft;
import com.mpx.infra_manager_java.model.changerequest.AppDraftRequest;
import com.mpx.infra_manager_java.model.changerequest.AppRow;
import com.mpx.infra_manager_java.model.changerequest.AppVerRow;
import com.mpx.infra_manager_java.model.changerequest.AppVersionSnapshot;
import com.mpx.infra_manager_java.model.changerequest.ApprRow;
import com.mpx.infra_manager_java.model.changerequest.ApprStepRow;
import com.mpx.infra_manager_java.model.changerequest.CandRow;
import com.mpx.infra_manager_java.model.changerequest.DecisionRequest;
import com.mpx.infra_manager_java.model.changerequest.EquipRow;
import com.mpx.infra_manager_java.model.changerequest.EventRow;
import com.mpx.infra_manager_java.model.changerequest.FlowActionRequest;
import com.mpx.infra_manager_java.model.changerequest.PlanStepRow;
import com.mpx.infra_manager_java.model.changerequest.ResubmitRequest;
import com.mpx.infra_manager_java.util.TaiwanTime;
import com.mpx.infra_manager_java.web.ApiBadRequestException;
import com.mpx.infra_manager_java.web.ApiConflictException;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
class AppFlowResubmitIT {

	private static final String APPLICANT_ID = "T0001";
	private static final AuthUser APPLICANT = new AuthUser(APPLICANT_ID, "wayne", "整合測試申請人", List.of("infra"),
			false);
	private static final AuthUser OTHER = new AuthUser("T0002", "other", "別人", List.of("infra"), false);
	private static final AuthUser ADMIN = new AuthUser("A0001", "admin", "管理員", List.of("admin"), false);
	private static final String OLD_TITLE = "S9 整合測試補件（退件前）";
	private static final String NEW_TITLE = "S9 整合測試補件（已補）";

	@Autowired
	private AppFlowService appFlowService;

	@Autowired
	private AppWriteDao appWriteDao;

	@Autowired
	private AppDao appDao;

	@Autowired
	private ApprovalDao approvalDao;

	@Autowired
	private AppVerDao appVerDao;

	@Autowired
	private DbClient dbClient;

	@Autowired
	private DbSchema schema;

	@Autowired
	private ObjectMapper objectMapper;

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
		appId = "S9IT" + (100_000_000L + ThreadLocalRandom.current().nextLong(900_000_000L));
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
		// 流程事件寫進 outbox 的信（S8）；用本測試的單號前綴刪，連先前跑留下的也一併清掉
		dbClient.update(itflowDb, "DELETE FROM " + schema.table("IM_MAIL_OUTBOX")
				+ " WHERE JSON_VALUE(META_JSON, '$.appId') LIKE :prefix", Map.of("prefix", "S9IT%"));
	}

	private boolean exists(String sql, Map<String, Object> params) {
		List<DualRow> rows = dbClient.query(itflowDb, sql, params, DualRow.class);
		return !rows.isEmpty() && rows.get(0).getOk() != null && rows.get(0).getOk() > 0;
	}

	private long count(String table) {
		List<DualRow> rows = dbClient.query(itflowDb,
				"SELECT COUNT(*) AS OK FROM " + schema.table(table) + " WHERE APP_ID = :id", Map.of("id", appId),
				DualRow.class);
		return rows.get(0).getOk();
	}

	private static AuthUser user(String userId) {
		return new AuthUser(userId, userId.toLowerCase(), userId, List.of(), false);
	}

	/** 建齊全草稿並送審，回送審後的 rowVerNo（1） */
	private long createAndSubmit() {
		AppDraft d = new AppDraft(OLD_TITLE, "P3", "資訊處", "1234", "it@example.com", true, false, "ONSITE", null, null,
				null, null, null, "舊主旨", null, null, null, null, null, null, null, null, null, List.of(), List.of(),
				List.of(), List.of(), List.of(), List.of());
		appWriteDao.insertApp(appId, "full", APPLICANT_ID, TaiwanTime.startOf(TaiwanTime.today()), d);
		return appFlowService.submit(appId, new FlowActionRequest(0L, null), APPLICANT);
	}

	/** 送審後由第一關第一位候選人退件（意見「資料不全」），回退件後的 rowVerNo */
	private long createSubmitAndReject() {
		long ver = createAndSubmit();
		ApprRow appr = approvalDao.findCurrent(appId, 1).orElseThrow();
		ApprStepRow current = DecisionPolicy.currentStep(approvalDao.findSteps(appr.getApprId()));
		List<String> cands = approvalDao.findCandidates(appr.getApprId()).stream()
				.filter(c -> current.getApprStepId().equals(c.getApprStepId())).map(CandRow::getUserId).toList();
		assertThat(cands).isNotEmpty();
		return appFlowService.decide(appId, new DecisionRequest(ver, "REJECT", "資料不全"), user(cands.get(0)));
	}

	/** 補件用的完整表單：改標題、改主旨、一台設備、兩個步驟 */
	private static AppDraftRequest fullForm(String tel) {
		return new AppDraftRequest(NEW_TITLE, "P3", new AppDraftRequest.Applicant("資訊處", tel, "it@example.com"), true,
				false, "ONSITE", null, null, "新主旨", null, null, null, null, null, null, null, null, null,
				List.of(new AppDraftRequest.Equipment("核心交換器", "A-1", null, null, null, null)), List.of("關機", "換板"),
				null, null, null);
	}

	@Test
	void 完成條件_退件後補件_主檔進v2_v1簽核紀錄與快照查得到() {
		long ver = createSubmitAndReject();
		AppRow before = appDao.findById(appId).orElseThrow();
		assertThat(before.getAppStatusCode()).isEqualTo("REJECTED");
		assertThat(before.getCurrVerNo()).isEqualTo(1);
		long v1ApprId = approvalDao.findCurrent(appId, 1).orElseThrow().getApprId();

		long newVer = appFlowService.resubmit(appId, new ResubmitRequest(ver, "已補齊設備清單", fullForm("1234")), APPLICANT);
		assertThat(newVer).isEqualTo(ver + 1);

		// 主檔：v2、IN_REVIEW、新內容
		AppRow app = appDao.findById(appId).orElseThrow();
		assertThat(app.getRowVerNo()).isEqualTo(ver + 1);
		assertThat(app.getAppStatusCode()).isEqualTo("IN_REVIEW");
		assertThat(app.getCurrVerNo()).isEqualTo(2);
		assertThat(app.getAppTitle()).isEqualTo(NEW_TITLE);
		assertThat(app.getWorkSubj()).isEqualTo("新主旨");
		assertThat(app.getResubMemo()).isEqualTo("已補齊設備清單");

		// 子表依新表單重建
		assertThat(appDao.findEquipments(appId)).extracting(EquipRow::getEquipName).containsExactly("核心交換器");
		assertThat(appDao.findPlanSteps(appId)).extracting(PlanStepRow::getStepText).containsExactly("關機", "換板");

		// v1 簽核紀錄仍在且是 REJECTED；v2 有新的 PENDING 實例
		ApprRow v1 = approvalDao.findCurrent(appId, 1).orElseThrow();
		assertThat(v1.getApprId()).isEqualTo(v1ApprId);
		assertThat(v1.getApprStatusCode()).isEqualTo("REJECTED");
		assertThat(approvalDao.findSteps(v1ApprId)).filteredOn(s -> "REJECTED".equals(s.getStepStatusCode())).hasSize(1)
				.first().satisfies(s -> assertThat(s.getMemo()).isEqualTo("資料不全"));
		ApprRow v2 = approvalDao.findCurrent(appId, 2).orElseThrow();
		assertThat(v2.getApprId()).isNotEqualTo(v1ApprId);
		assertThat(v2.getApprStatusCode()).isEqualTo("PENDING");
		assertThat(approvalDao.findSteps(v2.getApprId())).filteredOn(s -> "PENDING".equals(s.getStepStatusCode()))
				.isNotEmpty();

		// 版次表：恰一列 v1 快照
		List<AppVerRow> versions = appVerDao.findByApp(appId);
		assertThat(versions).hasSize(1);
		AppVerRow snap = versions.get(0);
		assertThat(snap.getAppVerNo()).isEqualTo(1);
		assertThat(snap.getCloseStatusCode()).isEqualTo("REJECTED");
		assertThat(snap.getVerReason()).isEqualTo("資料不全");
		assertThat(snap.getCreateBy()).isEqualTo(APPLICANT_ID);
		assertThat(snap.getSnapDate()).isNotNull();
		AppVersionSnapshot form = objectMapper.readValue(snap.getFormJson(), AppVersionSnapshot.class);
		assertThat(form.snapshotSchema()).isEqualTo(AppVersionSnapshot.SCHEMA);
		assertThat(form.verNo()).isEqualTo(1);
		assertThat(form.title()).isEqualTo(OLD_TITLE);
		assertThat(form.workSubject()).isEqualTo("舊主旨");
		assertThat(form.applicant().userId()).isEqualTo(APPLICANT_ID);
		assertThat(form.equipments()).isEmpty();
		// Oracle 端也認得這是 JSON（IS JSON 約束）
		assertThat(exists("SELECT COUNT(*) AS OK FROM " + schema.table("IM_APP_VER")
				+ " WHERE APP_ID = :id AND JSON_VALUE(FORM_JSON, '$.title') = :t", Map.of("id", appId, "t", OLD_TITLE)))
				.isTrue();

		// 事件：v2 有 RESUBMIT 且帶補件說明
		List<EventRow> events = appDao.findEvents(appId);
		assertThat(events).filteredOn(e -> "RESUBMIT".equals(e.getEventCode())).hasSize(1).first().satisfies(e -> {
			assertThat(e.getAppVerNo()).isEqualTo(2);
			assertThat(e.getMemo()).isEqualTo("已補齊設備清單");
			assertThat(e.getUserId()).isEqualTo(APPLICANT_ID);
		});

		// 補完已是 IN_REVIEW，再補 409
		assertThatThrownBy(() -> appFlowService.resubmit(appId, new ResubmitRequest(newVer, null, fullForm("1234")), APPLICANT))
				.isInstanceOf(ApiConflictException.class).hasMessage(AppFlowService.MSG_RESUBMIT_NOT_REJECTED);
	}

	@Test
	void 必填缺漏400整筆不留殘_非申請人與admin都403() {
		long ver = createSubmitAndReject();

		assertThatThrownBy(() -> appFlowService.resubmit(appId, new ResubmitRequest(ver, null, fullForm("  ")), APPLICANT))
				.isInstanceOf(ApiBadRequestException.class).hasMessageContaining("聯絡電話");
		AppRow app = appDao.findById(appId).orElseThrow();
		assertThat(app.getAppStatusCode()).as("400 時狀態轉換要 rollback").isEqualTo("REJECTED");
		assertThat(app.getRowVerNo()).isEqualTo(ver);
		assertThat(app.getCurrVerNo()).isEqualTo(1);
		assertThat(app.getAppTitle()).as("主檔覆寫要 rollback").isEqualTo(OLD_TITLE);
		assertThat(appVerDao.findByApp(appId)).as("快照要 rollback").isEmpty();
		assertThat(approvalDao.findCurrent(appId, 2)).isEmpty();
		assertThat(count("IM_APP_EQUIP")).as("子表重建要 rollback").isZero();

		assertThatThrownBy(() -> appFlowService.resubmit(appId, new ResubmitRequest(ver, null, fullForm("1234")), OTHER))
				.isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> appFlowService.resubmit(appId, new ResubmitRequest(ver, null, fullForm("1234")), ADMIN))
				.isInstanceOf(AccessDeniedException.class);
		assertThat(appDao.findById(appId).orElseThrow().getRowVerNo()).isEqualTo(ver);
		assertThat(appVerDao.findByApp(appId)).isEmpty();
	}

	@Test
	@Timeout(value = 60, unit = TimeUnit.SECONDS)
	void 兩個補件同rowVerNo同時出發_恰一成功另一409_版次表只一列() throws Exception {
		long ver = createSubmitAndReject();

		CountDownLatch go = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			List<Future<Object>> results = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				results.add(pool.submit(() -> {
					go.await();
					try {
						return appFlowService.resubmit(appId, new ResubmitRequest(ver, "併發補件", fullForm("1234")), APPLICANT);
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

			assertThat(outcomes.stream().filter(o -> o instanceof Long).count()).as("恰一成功：%s", outcomes).isEqualTo(1);
			assertThat(outcomes.stream().filter(o -> o instanceof ApiConflictException).count()).as("另一 409：%s", outcomes)
					.isEqualTo(1);

			AppRow app = appDao.findById(appId).orElseThrow();
			assertThat(app.getRowVerNo()).isEqualTo(ver + 1);
			assertThat(app.getCurrVerNo()).isEqualTo(2);
			assertThat(app.getAppStatusCode()).isEqualTo("IN_REVIEW");
			assertThat(appVerDao.findByApp(appId)).hasSize(1);
			assertThat(approvalDao.findCurrent(appId, 2)).isPresent();
			assertThat(appDao.findEvents(appId)).filteredOn(e -> "RESUBMIT".equals(e.getEventCode())).hasSize(1);
		} finally {
			pool.shutdownNow();
		}
	}
}
