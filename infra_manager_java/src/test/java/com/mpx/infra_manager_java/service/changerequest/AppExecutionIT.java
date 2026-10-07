package com.mpx.infra_manager_java.service.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：S10 回合一「填寫執行紀錄」對真實測試 Oracle 的整合測試（只由 mvnw verify 執行；連線資訊 API 為空、
//           測試帳號 T0001 不存在或未啟用、CHECK_LIST／EXEC_RESULT 選項沒有啟用列時略過）。用 S10E 開頭的假單號，
//           草稿建好後直接把主檔改成 APPROVED（簽核流程由 AppFlowDecisionIT 涵蓋），只驗執行這一段。
//           暫存 → 送治理審查：第一次儲存展開與啟用 CHECK_LIST 選項同數的檢核列、勾完成沒填時間補現在時間、未完成帶時間存 null、
//           主檔 IN_EXECUTION、執行結果列結案人與結案時間仍空；送審缺實際結束時間 400 且不加版本；
//           送審後 PENDING_REVIEW、結案人是送出者、結案時間有值、檢核列不重複展開；之後再存 409（修舊系統重開結案單）。
//           權限：非 idc_admin 非申請人 403 且不取鎖；idc_admin 可以填；版本過期 409。
//           併發：同 rowVerNo 兩人同時暫存，恰一成功、另一 409。測試結束刪除本測試建的所有列。
//           2026-10-07 S10 R2：加完成條件全程（PENDING_REVIEW → 治理通過 EXECUTED、事件 GOV_PASS、之後審查／執行／退回都 409）、
//           治理退回 → REJECTED → 補件後 IM_APP_VER v1 為 GOV_RETURNED 且 v2 檢核表與執行結果空白、
//           執行端退回 → REJECTED 不清 v1 執行資料 → 補件後 v1 為 EXEC_REJECTED（補件兩條要 full 流程有啟用關卡，否則略過）；
//           清理加簽核實例三表與 IM_APP_VER
//           2026-10-07 S10 R3：第一條加檢視 API 檢核項帶原始 userId／executorDesc 的斷言
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
import com.mpx.infra_manager_java.dao.changerequest.AppVerDao;
import com.mpx.infra_manager_java.dao.changerequest.AppWriteDao;
import com.mpx.infra_manager_java.model.DualRow;
import com.mpx.infra_manager_java.model.auth.AuthUser;
import com.mpx.infra_manager_java.model.changerequest.AppDetail;
import com.mpx.infra_manager_java.model.changerequest.AppDraft;
import com.mpx.infra_manager_java.model.changerequest.AppDraftRequest;
import com.mpx.infra_manager_java.model.changerequest.AppVerRow;
import com.mpx.infra_manager_java.model.changerequest.ExecRejectRequest;
import com.mpx.infra_manager_java.model.changerequest.ExecutionRequest;
import com.mpx.infra_manager_java.model.changerequest.GovernanceReviewRequest;
import com.mpx.infra_manager_java.model.changerequest.ResubmitRequest;
import com.mpx.infra_manager_java.util.TaiwanTime;
import com.mpx.infra_manager_java.web.ApiBadRequestException;
import com.mpx.infra_manager_java.web.ApiConflictException;

@SpringBootTest
class AppExecutionIT {

	private static final String APPLICANT_ID = "T0001";
	private static final AuthUser APPLICANT = new AuthUser(APPLICANT_ID, "wayne", "整合測試申請人", List.of("infra"),
			false);
	private static final AuthUser IDC = new AuthUser("S10IDC", "s10idc", "機房管理員", List.of("idc_admin"), false);
	private static final AuthUser OTHER = new AuthUser("S10OTH", "s10oth", "路人", List.of("infra"), false);

	@Autowired
	private AppExecutionService appExecutionService;

	@Autowired
	private AppWriteDao appWriteDao;

	@Autowired
	private AppFlowService appFlowService;

	@Autowired
	private AppQueryService appQueryService;

	@Autowired
	private AppVerDao appVerDao;

	@Autowired
	private DbClient dbClient;

	@Autowired
	private DbSchema schema;

	@Value("${db.connect.api.domain.path:}")
	private String apiUrl;

	@Value("${db.connect.itflow}")
	private String itflowDb;

	private String appId;
	private long checkListCount;

	@BeforeEach
	void setUp() {
		assumeTrue(apiUrl != null && !apiUrl.isBlank(), "host.properties 未設定連線資訊 API，略過整合測試");
		assumeTrue(exists("SELECT COUNT(*) AS OK FROM " + schema.table("IM_USER") + " WHERE USER_ID = :u AND STATUS = 1",
				Map.of("u", APPLICANT_ID)), "測試帳號不存在或未啟用，略過");
		assumeTrue(exists("SELECT COUNT(*) AS OK FROM " + schema.table("IM_FORM_OPTION")
				+ " WHERE GROUP_CODE = 'EXEC_RESULT' AND OPTION_CODE = 'DONE' AND STATUS = 1", Map.of()),
				"EXEC_RESULT 沒有啟用的 DONE，略過");
		checkListCount = count("SELECT COUNT(*) AS OK FROM " + schema.table("IM_FORM_OPTION")
				+ " WHERE GROUP_CODE = 'CHECK_LIST' AND STATUS = 1", Map.of());
		assumeTrue(checkListCount >= 2, "CHECK_LIST 啟用選項不足兩項，略過");
		appId = "S10E" + (100_000_000L + ThreadLocalRandom.current().nextLong(900_000_000L));
	}

	@AfterEach
	void cleanUp() {
		if (appId == null) {
			return;
		}
		Map<String, Object> p = Map.of("id", appId);
		dbClient.update(itflowDb, "DELETE FROM " + schema.table("IM_APP_CHECK_LIST") + " WHERE APP_ID = :id", p);
		dbClient.update(itflowDb, "DELETE FROM " + schema.table("IM_APP_EXEC") + " WHERE APP_ID = :id", p);
		dbClient.update(itflowDb, "DELETE FROM " + schema.table("IM_APP_EVENT") + " WHERE APP_ID = :id", p);
		// 補件測試會建 v2 簽核實例與 v1 快照
		String appr = schema.table("IM_APPR");
		String step = schema.table("IM_APPR_STEP");
		dbClient.update(itflowDb, "DELETE FROM " + schema.table("IM_APPR_CAND_MAP") + " WHERE APPR_STEP_ID IN"
				+ " (SELECT S.APPR_STEP_ID FROM " + step + " S JOIN " + appr + " A ON A.APPR_ID = S.APPR_ID"
				+ " WHERE A.DOC_TYPE = 'CR' AND A.DOC_ID = :id)", p);
		dbClient.update(itflowDb, "DELETE FROM " + step + " WHERE APPR_ID IN (SELECT APPR_ID FROM " + appr
				+ " WHERE DOC_TYPE = 'CR' AND DOC_ID = :id)", p);
		dbClient.update(itflowDb, "DELETE FROM " + appr + " WHERE DOC_TYPE = 'CR' AND DOC_ID = :id", p);
		dbClient.update(itflowDb, "DELETE FROM " + schema.table("IM_APP_VER") + " WHERE APP_ID = :id", p);
		appWriteDao.deleteChildren(appId);
		dbClient.update(itflowDb, "DELETE FROM " + schema.table("IM_APP") + " WHERE APP_ID = :id", p);
	}

	private boolean exists(String sql, Map<String, Object> params) {
		return count(sql, params) > 0;
	}

	private long count(String sql, Map<String, Object> params) {
		List<DualRow> rows = dbClient.query(itflowDb, sql, params, DualRow.class);
		return rows.isEmpty() || rows.get(0).getOk() == null ? 0 : rows.get(0).getOk();
	}

	/** 建齊全草稿並直接改成 APPROVED；ROW_VER_NO 維持 0 */
	private void createApproved() {
		AppDraft d = new AppDraft("S10 整合測試執行", "P3", "資訊處", "1234", "it@example.com", true, false, "ONSITE", null,
				null, null, null, null, "主旨", null, null, null, null, null, null, null, null, null, List.of(), List.of(),
				List.of(), List.of(), List.of(), List.of());
		appWriteDao.insertApp(appId, "full", APPLICANT_ID, TaiwanTime.startOf(TaiwanTime.today()), d);
		appWriteDao.updateStatus(appId, "APPROVED", APPLICANT_ID);
	}

	private static ExecutionRequest draft(long rowVerNo, List<ExecutionRequest.CheckItem> items) {
		return new ExecutionRequest(rowVerNo, items, "2026-10-07 09:00", null, null, false, null, false, null, "暫存");
	}

	private static ExecutionRequest submit(long rowVerNo, String end) {
		return new ExecutionRequest(rowVerNo, List.of(), "2026-10-07 09:00", end, "DONE", false, null, false, null,
				"完成");
	}

	private boolean appIs(String status, long rowVerNo) {
		return exists("SELECT COUNT(*) AS OK FROM " + schema.table("IM_APP")
				+ " WHERE APP_ID = :id AND APP_STATUS_CODE = :s AND ROW_VER_NO = :v",
				Map.of("id", appId, "s", status, "v", rowVerNo));
	}

	@Test
	void 暫存後送治理審查_檢核表展開一次_結案人只在送審寫入_送審後再存409() {
		createApproved();
		AppExecutionService.SaveResult saved = appExecutionService.save(appId,
				draft(0L, List.of(new ExecutionRequest.CheckItem(1, true, null, APPLICANT_ID, null),
						new ExecutionRequest.CheckItem(2, false, "2026-10-07 10:00", null, "門市EDP、廠商"))),
				APPLICANT);
		assertThat(saved.rowVerNo()).isEqualTo(1L);
		assertThat(saved.statusCode()).isEqualTo("IN_EXECUTION");
		assertThat(appIs("IN_EXECUTION", 1L)).as("主檔執行中").isTrue();

		String list = schema.table("IM_APP_CHECK_LIST");
		assertThat(count("SELECT COUNT(*) AS OK FROM " + list + " WHERE APP_ID = :id AND APP_VER_NO = 1", Map.of("id", appId)))
				.as("展開列數＝啟用 CHECK_LIST 選項數").isEqualTo(checkListCount);
		assertThat(exists("SELECT COUNT(*) AS OK FROM " + list + " WHERE APP_ID = :id AND SEQ_NO = 1 AND IS_DONE = 1"
				+ " AND DONE_DATE IS NOT NULL AND USER_ID = :u AND EXEC_USER_DESC IS NULL",
				Map.of("id", appId, "u", APPLICANT_ID))).as("勾完成沒填時間補現在時間").isTrue();
		assertThat(exists("SELECT COUNT(*) AS OK FROM " + list + " WHERE APP_ID = :id AND SEQ_NO = 2 AND IS_DONE = 0"
				+ " AND DONE_DATE IS NULL AND USER_ID IS NULL AND EXEC_USER_DESC = :d",
				Map.of("id", appId, "d", "門市EDP、廠商"))).as("未完成帶時間存 null").isTrue();
		List<AppDetail.CheckItem> shown = appQueryService.detail(appId, APPLICANT).checklist();
		assertThat(shown.get(0).userId()).as("檢視 API 帶原始工號（R3 執行頁重存用）").isEqualTo(APPLICANT_ID);
		assertThat(shown.get(0).executorDesc()).isNull();
		assertThat(shown.get(0).doneAt()).isNotNull();
		assertThat(shown.get(1).userId()).isNull();
		assertThat(shown.get(1).executorDesc()).isEqualTo("門市EDP、廠商");
		assertThat(shown.get(1).executor()).isEqualTo("門市EDP、廠商");
		String exec = schema.table("IM_APP_EXEC");
		assertThat(exists("SELECT COUNT(*) AS OK FROM " + exec + " WHERE APP_ID = :id AND APP_VER_NO = 1"
				+ " AND RESULT_CODE IS NULL AND USER_ID IS NULL AND CLOSE_DATE IS NULL AND ACTUAL_START_DATE IS NOT NULL",
				Map.of("id", appId))).as("暫存不寫結案人").isTrue();

		assertThatThrownBy(() -> appExecutionService.save(appId, submit(1L, null), APPLICANT))
				.isInstanceOf(ApiBadRequestException.class)
				.hasMessage(ExecutionValidator.MSG_SUBMIT_PREFIX + "實際結束時間");
		assertThat(appIs("IN_EXECUTION", 1L)).as("必填缺漏不加版本").isTrue();

		saved = appExecutionService.save(appId, submit(1L, "2026-10-07 11:30"), APPLICANT);
		assertThat(saved.rowVerNo()).isEqualTo(2L);
		assertThat(saved.statusCode()).isEqualTo("PENDING_REVIEW");
		assertThat(appIs("PENDING_REVIEW", 2L)).isTrue();
		assertThat(exists("SELECT COUNT(*) AS OK FROM " + exec + " WHERE APP_ID = :id AND APP_VER_NO = 1"
				+ " AND RESULT_CODE = 'DONE' AND USER_ID = :u AND CLOSE_DATE IS NOT NULL",
				Map.of("id", appId, "u", APPLICANT_ID))).as("送審寫結案人與結案時間").isTrue();
		assertThat(count("SELECT COUNT(*) AS OK FROM " + exec + " WHERE APP_ID = :id", Map.of("id", appId))).isEqualTo(1);
		assertThat(count("SELECT COUNT(*) AS OK FROM " + list + " WHERE APP_ID = :id", Map.of("id", appId)))
				.as("不重複展開").isEqualTo(checkListCount);
		assertThat(count("SELECT COUNT(*) AS OK FROM " + schema.table("IM_APP_EVENT") + " WHERE APP_ID = :id",
				Map.of("id", appId))).as("執行儲存不寫事件").isZero();

		assertThatThrownBy(() -> appExecutionService.save(appId, draft(2L, List.of()), APPLICANT))
				.isInstanceOf(ApiConflictException.class).hasMessage(AppExecutionService.MSG_EXEC_NOT_EXECUTABLE);
		assertThat(appIs("PENDING_REVIEW", 2L)).as("409 時取鎖要 rollback").isTrue();
	}

	@Test
	void 非機房管理員非申請人403不取鎖_機房管理員可填_版本過期409() {
		createApproved();
		assertThatThrownBy(() -> appExecutionService.save(appId, draft(0L, List.of()), OTHER))
				.isInstanceOf(AccessDeniedException.class);
		assertThat(appIs("APPROVED", 0L)).as("403 不加版本").isTrue();

		AppExecutionService.SaveResult saved = appExecutionService.save(appId, draft(0L, List.of()), IDC);
		assertThat(saved.rowVerNo()).isEqualTo(1L);
		assertThat(appIs("IN_EXECUTION", 1L)).isTrue();

		assertThatThrownBy(() -> appExecutionService.save(appId, draft(0L, List.of()), APPLICANT))
				.isInstanceOf(ApiConflictException.class).hasMessage(AppFlowService.MSG_STALE);
		assertThatThrownBy(() -> appExecutionService.save(appId,
				draft(1L, List.of(new ExecutionRequest.CheckItem(999, true, null, null, null))), APPLICANT))
				.isInstanceOf(ApiBadRequestException.class).hasMessage(ExecutionValidator.MSG_BAD_SEQ);
		assertThat(appIs("IN_EXECUTION", 1L)).as("未知序號 400 時 rollback").isTrue();
	}

	@Test
	@Timeout(value = 60, unit = TimeUnit.SECONDS)
	void 同rowVerNo兩人同時暫存_恰一成功另一409() throws Exception {
		createApproved();
		CountDownLatch go = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			Future<Object> a = pool.submit(() -> {
				go.await();
				try {
					return appExecutionService.save(appId, draft(0L, List.of()), APPLICANT);
				} catch (RuntimeException e) {
					return e;
				}
			});
			Future<Object> b = pool.submit(() -> {
				go.await();
				try {
					return appExecutionService.save(appId, draft(0L, List.of()), IDC);
				} catch (RuntimeException e) {
					return e;
				}
			});
			go.countDown();
			Object ra = a.get(30, TimeUnit.SECONDS);
			Object rb = b.get(30, TimeUnit.SECONDS);
			boolean aWon = ra instanceof AppExecutionService.SaveResult;
			boolean bWon = rb instanceof AppExecutionService.SaveResult;
			assertThat(aWon ^ bWon).as("恰一成功：a=%s b=%s", ra, rb).isTrue();
			assertThat(aWon ? rb : ra).isInstanceOf(ApiConflictException.class);
			assertThat(appIs("IN_EXECUTION", 1L)).isTrue();
			assertThat(count("SELECT COUNT(*) AS OK FROM " + schema.table("IM_APP_CHECK_LIST") + " WHERE APP_ID = :id",
					Map.of("id", appId))).as("檢核表只展開一次").isEqualTo(checkListCount);
		} finally {
			pool.shutdownNow();
		}
	}

	// ---- S10 R2：執行端退回與治理審查 ----

	private static final AuthUser GOV = new AuthUser("S10GOV", "s10gov", "資訊治理", List.of("governance"), false);

	/** 暫存一次再送治理審查，回送審後的 rowVerNo（2） */
	private long createPendingReview() {
		createApproved();
		long ver = appExecutionService.save(appId,
				draft(0L, List.of(new ExecutionRequest.CheckItem(1, true, null, APPLICANT_ID, null))), APPLICANT)
				.rowVerNo();
		return appExecutionService.save(appId, submit(ver, "2026-10-07 11:30"), APPLICANT).rowVerNo();
	}

	/** 補件用的完整表單（同 AppFlowResubmitIT） */
	private static AppDraftRequest fullForm() {
		return new AppDraftRequest("S10 整合測試執行（已補）", "P3", new AppDraftRequest.Applicant("資訊處", "1234",
				"it@example.com"), true, false, "ONSITE", null, null, "新主旨", null, null, null, null, null, null, null,
				null, null, List.of(new AppDraftRequest.Equipment("核心交換器", "A-1", null, null, null, null)),
				List.of("關機", "換板"), null, null, null);
	}

	private void assumeFullFlow() {
		assumeTrue(exists("SELECT COUNT(*) AS OK FROM " + schema.table("IM_FLOW_STEP")
				+ " WHERE FLOW_ID = 'full' AND STATUS = 1 AND IS_NOTIFY_ONLY = 0", Map.of()), "full 流程沒有啟用關卡，略過");
	}

	private long countAt(String table, int verNo) {
		return count("SELECT COUNT(*) AS OK FROM " + schema.table(table) + " WHERE APP_ID = :id AND APP_VER_NO = :v",
				Map.of("id", appId, "v", verNo));
	}

	@Test
	void 完成條件_治理通過EXECUTED_結案後三支端點都409() {
		long ver = createPendingReview();
		assertThat(ver).isEqualTo(2L);

		assertThatThrownBy(() -> appExecutionService.review(appId, new GovernanceReviewRequest(ver, "PASS", null), IDC))
				.isInstanceOf(AccessDeniedException.class);
		assertThat(appIs("PENDING_REVIEW", 2L)).as("非治理 403 不加版本").isTrue();

		AppExecutionService.SaveResult passed = appExecutionService.review(appId,
				new GovernanceReviewRequest(ver, "PASS", "  "), GOV);
		assertThat(passed.rowVerNo()).isEqualTo(3L);
		assertThat(passed.statusCode()).isEqualTo("EXECUTED");
		assertThat(appIs("EXECUTED", 3L)).isTrue();
		assertThat(exists("SELECT COUNT(*) AS OK FROM " + schema.table("IM_APP_EVENT") + " WHERE APP_ID = :id"
				+ " AND APP_VER_NO = 1 AND EVENT_CODE = 'GOV_PASS' AND USER_ID = :u AND MEMO IS NULL",
				Map.of("id", appId, "u", GOV.userId()))).as("事件 GOV_PASS、空白意見存 null").isTrue();

		assertThatThrownBy(() -> appExecutionService.review(appId, new GovernanceReviewRequest(3L, "PASS", null), GOV))
				.isInstanceOf(ApiConflictException.class).hasMessage(AppExecutionService.MSG_REVIEW_NOT_PENDING);
		assertThatThrownBy(() -> appExecutionService.save(appId, draft(3L, List.of()), IDC))
				.isInstanceOf(ApiConflictException.class).hasMessage(AppExecutionService.MSG_EXEC_NOT_EXECUTABLE);
		assertThatThrownBy(() -> appExecutionService.reject(appId, new ExecRejectRequest(3L, "重做"), IDC))
				.isInstanceOf(ApiConflictException.class).hasMessage(AppExecutionService.MSG_REJECT_NOT_EXECUTABLE);
		assertThat(appIs("EXECUTED", 3L)).as("409 都 rollback").isTrue();
	}

	@Test
	void 治理退回REJECTED_補件後v1記GOV_RETURNED_v2執行資料從空白開始() {
		assumeFullFlow();
		long ver = createPendingReview();
		assertThatThrownBy(() -> appExecutionService.review(appId, new GovernanceReviewRequest(ver, "RETURN", " "), GOV))
				.isInstanceOf(ApiBadRequestException.class).hasMessage(AppExecutionService.MSG_REJECT_NEEDS_MEMO);

		AppExecutionService.SaveResult returned = appExecutionService.review(appId,
				new GovernanceReviewRequest(ver, "RETURN", "備份紀錄不完整"), GOV);
		assertThat(returned.statusCode()).isEqualTo("REJECTED");
		assertThat(appIs("REJECTED", 3L)).isTrue();

		long newVer = appFlowService.resubmit(appId, new ResubmitRequest(3L, "已補備份紀錄", fullForm()), APPLICANT);
		assertThat(newVer).isEqualTo(4L);
		assertThat(appIs("IN_REVIEW", 4L)).isTrue();
		List<AppVerRow> versions = appVerDao.findByApp(appId);
		assertThat(versions).hasSize(1);
		assertThat(versions.get(0).getAppVerNo()).isEqualTo(1);
		assertThat(versions.get(0).getCloseStatusCode()).isEqualTo("GOV_RETURNED");
		assertThat(versions.get(0).getVerReason()).isEqualTo("備份紀錄不完整");
		assertThat(countAt("IM_APP_CHECK_LIST", 1)).as("v1 檢核表留著").isEqualTo(checkListCount);
		assertThat(countAt("IM_APP_EXEC", 1)).isEqualTo(1);
		assertThat(countAt("IM_APP_CHECK_LIST", 2)).as("v2 檢核表空白").isZero();
		assertThat(countAt("IM_APP_EXEC", 2)).as("v2 執行結果空白").isZero();
	}

	@Test
	void 執行端退回REJECTED_不清執行資料_補件後v1記EXEC_REJECTED() {
		assumeFullFlow();
		createApproved();
		long ver = appExecutionService.save(appId,
				draft(0L, List.of(new ExecutionRequest.CheckItem(1, true, null, APPLICANT_ID, null))), IDC).rowVerNo();

		assertThatThrownBy(() -> appExecutionService.reject(appId, new ExecRejectRequest(ver, null), IDC))
				.isInstanceOf(ApiBadRequestException.class).hasMessage(AppExecutionService.MSG_REJECT_NEEDS_MEMO);
		assertThatThrownBy(() -> appExecutionService.reject(appId, new ExecRejectRequest(ver, "x"), OTHER))
				.isInstanceOf(AccessDeniedException.class);
		assertThat(appIs("IN_EXECUTION", 1L)).as("400／403 不加版本").isTrue();

		long rejected = appExecutionService.reject(appId, new ExecRejectRequest(ver, "設備未到貨"), IDC);
		assertThat(rejected).isEqualTo(2L);
		assertThat(appIs("REJECTED", 2L)).isTrue();
		assertThat(exists("SELECT COUNT(*) AS OK FROM " + schema.table("IM_APP_EVENT") + " WHERE APP_ID = :id"
				+ " AND APP_VER_NO = 1 AND EVENT_CODE = 'EXEC_REJECT' AND USER_ID = :u",
				Map.of("id", appId, "u", IDC.userId()))).isTrue();
		assertThat(countAt("IM_APP_CHECK_LIST", 1)).as("退回不清檢核表").isEqualTo(checkListCount);
		assertThat(countAt("IM_APP_EXEC", 1)).as("退回不清執行結果").isEqualTo(1);

		appFlowService.resubmit(appId, new ResubmitRequest(2L, null, fullForm()), APPLICANT);
		AppVerRow v1 = appVerDao.findByApp(appId).get(0);
		assertThat(v1.getCloseStatusCode()).isEqualTo("EXEC_REJECTED");
		assertThat(v1.getVerReason()).isEqualTo("設備未到貨");
		assertThat(countAt("IM_APP_CHECK_LIST", 2)).as("v2 檢核表空白").isZero();
	}
}
