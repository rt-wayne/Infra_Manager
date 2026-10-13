package com.mpx.im_acct_insp.support;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：業務 Service 單元測試共用環境
//           以 Mockito mock DbClient（不連真 DB），其上建立真的 DAO 與 Service；
//           查詢依 DAO 的 SQL 常數 stub；人員角色、關卡、類別、資產分類照 im_acct_insp_create_table.sql 初始資料；
//           IM_ACCT_REQ 以 reqStore 模擬單筆申請單：狀態轉移 UPDATE 依 expectState／expectStage 判斷影響 1 或 0 筆
//           時鐘固定 2026-10-07 10:30
//           變更單 #1：資產分類補 H07（IS_SHOW＝0，畫面不可選）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0 重寫（spec_im_acct_insp_v2.0.md）：改為 mock 各 DAO（SQL 本身由 SqlConstantScanTest 檢查），其上建立真的 Service；
//           角色、權限、執行人對照照 Table v0.2 6.3、8.6、8.7 初始資料，以 id(工號) 組出 Identity（含 GENERAL 權限），
//           impersonate(實際, 代理) 組出切換中身分；關卡 1～7、類別 IS_ENABLED、資產分類 IS_REVIEW_TRIGGER／IS_SELECTABLE 照 Table 第 6、8 節；
//           IM_ACCT_REQ 以 req 模擬單筆申請單：狀態轉移依 VERSION_NO／狀態／關卡條件判斷影響 1 或 0 筆並 VERSION_NO＋1；
//           IM_ACCT_HIST 寫入收集在 hists；交易以 RecordingTxManager 記錄 commit／rollback；時鐘固定 2026-10-07 10:30（Asia/Taipei）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-11
// 變更說明: BACKLOG 第 40 項：Identity 建構子多一個 deptName 參數（此處給 null）
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S2：加 EmgDao（mock）與 EmergencyService（真）
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S7：jobService() 建構子多傳 EmgDao（J03 逾期掃描）
// AI版本  : Claude Fable 5 (claude-fable-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S10：acctDao.clearReview 預設回 1（複驗通過清審查異常旗標）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S10 審查修正：clearReview 多一個 execDate 參數；execDone 替身比照 SQL 寫 execDate（＝now）
// ============================================================

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.BeanUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.mpx.im_acct_insp.common.AppConfig;
import com.mpx.im_acct_insp.common.DocSeqService;
import com.mpx.im_acct_insp.common.Identity;
import com.mpx.im_acct_insp.dao.AcctDao;
import com.mpx.im_acct_insp.dao.AcctHistDao;
import com.mpx.im_acct_insp.dao.AcctReqDao;
import com.mpx.im_acct_insp.dao.AttachDao;
import com.mpx.im_acct_insp.dao.CodeDao;
import com.mpx.im_acct_insp.dao.DocSeqDao;
import com.mpx.im_acct_insp.dao.EmgDao;
import com.mpx.im_acct_insp.dao.HomeDao;
import com.mpx.im_acct_insp.dao.InspDao;
import com.mpx.im_acct_insp.dao.JobRunDao;
import com.mpx.im_acct_insp.dao.NotifyLogDao;
import com.mpx.im_acct_insp.dao.PermDao;
import com.mpx.im_acct_insp.model.Acct;
import com.mpx.im_acct_insp.model.AcctHist;
import com.mpx.im_acct_insp.model.AcctReq;
import com.mpx.im_acct_insp.model.AcctReqDetail;
import com.mpx.im_acct_insp.model.ApprStage;
import com.mpx.im_acct_insp.model.AssetClass;
import com.mpx.im_acct_insp.model.Category;
import com.mpx.im_acct_insp.service.AccountService;
import com.mpx.im_acct_insp.service.AttachmentService;
import com.mpx.im_acct_insp.service.CodeService;
import com.mpx.im_acct_insp.service.EmergencyService;
import com.mpx.im_acct_insp.service.HomeService;
import com.mpx.im_acct_insp.service.InspectionService;
import com.mpx.im_acct_insp.service.JobService;
import com.mpx.im_acct_insp.service.NotificationService;
import com.mpx.im_acct_insp.service.PermissionService;
import com.mpx.im_acct_insp.service.RequestService;

public class TestFixture {

	public static final String KEVIN = "0000001";
	public static final String MOLLY = "0000002";
	public static final String CATHY = "0000003";
	public static final String TERRY = "0000004";
	public static final String WENSEN = "0000005";
	public static final String ALEX = "0000006";
	public static final String ALAN = "0000007";
	public static final String MICHAEL = "0000008";
	public static final String SAGE = "0000009";
	public static final String GARY = "0000010";
	public static final String HANK = "0000011";
	public static final String TESTER = "0000012";

	public static final LocalDate TODAY = LocalDate.of(2026, 10, 7);
	public static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 7, 10, 30);

	/** Table 6.3 角色 × 權限 */
	public static final Map<String, List<String>> ROLE_PERMS = new LinkedHashMap<>();
	/** Table 8.6 角色人員 */
	public static final Map<String, List<String>> USER_ROLES = new LinkedHashMap<>();
	/** Table 8.7 執行人對照 */
	public static final Map<String, List<String>> USER_EXEC = new LinkedHashMap<>();
	public static final Map<String, String> NAMES = new LinkedHashMap<>();

	static {
		ROLE_PERMS.put("GENERAL", List.of("ACC_APPLY", "ACC_SELF_SERVICE", "EMG_APPLY", "STATS_VIEW"));
		ROLE_PERMS.put("DC_ADMIN", List.of("APPR_G2"));
		ROLE_PERMS.put("INFRA_MGR", List.of("APPR_G3", "ACC_EXPIRY_ALL", "REVIEW_LIST", "REVIEW_SIGN_INFRA", "EMG_VIEW_ALL", "EMG_DECIDE"));
		ROLE_PERMS.put("GOV", List.of("APPR_G4", "VERIFY_G7"));
		ROLE_PERMS.put("GOV_MGR", List.of("SYS_AUDIT_VIEW", "ACC_EXPIRY_ALL", "REVIEW_LIST", "REVIEW_SIGN_GOV", "EMG_VIEW_ALL", "EMG_DECIDE"));
		ROLE_PERMS.put("IT_HEAD", List.of("APPR_G5", "ACC_EXPIRY_ALL", "REVIEW_LIST", "EMG_VIEW_ALL", "EMG_DECIDE"));
		ROLE_PERMS.put("ACCT_REGISTRY", List.of("ACC_LIST_FULL", "ACC_EXPORT"));
		ROLE_PERMS.put("INSP_STAFF", List.of("INSP_VIEW", "INSP_CREATE", "INSP_RESOLVE", "INSP_EXPORT"));
		ROLE_PERMS.put("SYS_ADMIN", List.of("SYS_ROLE", "SYS_EXECUTOR", "SYS_IMPERSONATE", "SYS_AUDIT_VIEW", "SYS_JOB_RUN"));

		user(KEVIN, "Kevin", "李凱鈞", "GOV", "INSP_STAFF");
		user(MOLLY, "Molly", "林宜瑾", "GOV", "GOV_MGR", "ACCT_REGISTRY", "INSP_STAFF");
		user(CATHY, "Cathy", "周佩欣", "GOV");
		user(TERRY, "Terry", "邱健唐", "INFRA_MGR", "ACCT_REGISTRY", "INSP_STAFF");
		user(WENSEN, "Wensen", "林文松", "INFRA_MGR", "INSP_STAFF");
		user(ALEX, "Alex", "陳儀仁", "DC_ADMIN", "INSP_STAFF");
		user(ALAN, "Alan", "王祖樑", "DC_ADMIN", "INSP_STAFF");
		user(MICHAEL, "Michael", "許漢煒");
		user(SAGE, "Sage", "秦一中");
		user(GARY, "Gary", "許永成", "IT_HEAD", "ACCT_REGISTRY", "INSP_STAFF");
		user(HANK, "Hank", "黃志翰", "SYS_ADMIN");
		user(TESTER, "Tester", "一般測試員");

		exec("HOST", ALEX);
		exec("DB", TERRY, MICHAEL);
		exec("APP", ALEX);
		exec("SVC", ALEX, ALAN);
		exec("NET", ALEX);
		exec("VENDOR", SAGE);
		exec("CERT", ALEX, ALAN);
		exec("DOOR", ALEX, ALAN);
	}

	private static void user(String id, String en, String zh, String... roles) {
		NAMES.put(id, en + " (" + zh + ")");
		USER_ROLES.put(id, List.of(roles));
	}

	private static void exec(String catg, String... users) {
		for (String u : users) {
			USER_EXEC.computeIfAbsent(u, k -> new ArrayList<>()).add(catg);
		}
	}

	/** 依初始資料展開的權限（含 GENERAL） */
	public static Set<String> permsOf(String userId) {
		Set<String> p = new LinkedHashSet<>(ROLE_PERMS.get("GENERAL"));
		for (String r : USER_ROLES.getOrDefault(userId, List.of())) {
			p.addAll(ROLE_PERMS.get(r));
		}
		return p;
	}

	public static Identity id(String userId) {
		return new Identity("hash-" + userId, userId, NAMES.get(userId), userId, NAMES.get(userId), false, permsOf(userId),
				USER_ROLES.getOrDefault(userId, List.of()), USER_EXEC.getOrDefault(userId, List.of()), null, null, null);
	}

	/** 實際使用者 real 切換為 actAs */
	public static Identity impersonate(String real, String actAs) {
		return new Identity("hash-" + real, real, NAMES.get(real), actAs, NAMES.get(actAs), true, permsOf(actAs),
				USER_ROLES.getOrDefault(actAs, List.of()), USER_EXEC.getOrDefault(actAs, List.of()), null, null, null);
	}

	/** 自訂權限的身分（名單變更情境用） */
	public static Identity idWith(String userId, Set<String> perms, List<String> execCatgs) {
		return new Identity("hash-" + userId, userId, NAMES.get(userId), userId, NAMES.get(userId), false, perms, List.of(),
				execCatgs, null, null, null);
	}

	// ------------------------------------------------------------
	// 交易
	// ------------------------------------------------------------

	/** 不連 DB 的交易管理器：只記錄 commit／rollback 次數 */
	public static class RecordingTxManager extends AbstractPlatformTransactionManager {
		private static final long serialVersionUID = 1L;
		public int begins;
		public int commits;
		public int rollbacks;

		@Override
		protected Object doGetTransaction() {
			return new Object();
		}

		@Override
		protected void doBegin(Object transaction, TransactionDefinition definition) {
			begins++;
		}

		@Override
		protected void doCommit(DefaultTransactionStatus status) {
			commits++;
		}

		@Override
		protected void doRollback(DefaultTransactionStatus status) {
			rollbacks++;
		}
	}

	public final Clock clock = Clock.fixed(NOW.atZone(AppConfig.ZONE).toInstant(), AppConfig.ZONE);
	public final RecordingTxManager txm = new RecordingTxManager();
	public final TransactionTemplate tx = new TransactionTemplate(txm);

	// DAO（mock）
	public final PermDao permDao = mock(PermDao.class);
	public final CodeDao codeDao = mock(CodeDao.class);
	public final DocSeqDao docSeqDao = mock(DocSeqDao.class);
	public final AcctDao acctDao = mock(AcctDao.class);
	public final AcctReqDao acctReqDao = mock(AcctReqDao.class);
	public final AcctHistDao acctHistDao = mock(AcctHistDao.class);
	public final AttachDao attachDao = mock(AttachDao.class);
	public final HomeDao homeDao = mock(HomeDao.class);
	public final InspDao inspDao = mock(InspDao.class);
	public final EmgDao emgDao = mock(EmgDao.class);
	public final NotifyLogDao notifyLogDao = mock(NotifyLogDao.class);
	public final JobRunDao jobRunDao = mock(JobRunDao.class);
	public final AttachmentService attachmentService = mock(AttachmentService.class);
	public final NotificationService notificationService = mock(NotificationService.class);

	// Service（真）
	public final CodeService codeService = new CodeService(codeDao);
	public final DocSeqService docSeqService = new DocSeqService(docSeqDao, clock);
	public final PermissionService permissionService = new PermissionService(permDao, acctReqDao, acctHistDao);
	public final RequestService requestService = new RequestService(permissionService, codeService, docSeqService, acctReqDao,
			acctDao, acctHistDao, attachDao, attachmentService, notificationService, tx, clock);
	public final AccountService accountService = new AccountService(permissionService, codeService, acctDao, acctHistDao, clock);
	public final HomeService homeService = new HomeService(permissionService, codeService, homeDao, acctReqDao, clock);
	public final InspectionService inspectionService = new InspectionService(permissionService, docSeqService, inspDao, tx,
			clock);
	public final EmergencyService emergencyService = new EmergencyService(permissionService, codeService, docSeqService, emgDao,
			acctDao, acctHistDao, notificationService, tx, clock);

	/** 模擬 IM_ACCT_REQ 的單筆申請單（null＝查無） */
	public AcctReqDetail req;
	/** 模擬 IM_ACCT（依 ACCT_ID） */
	public final Map<String, Acct> accts = new HashMap<>();
	/** 已寫入的 IM_ACCT_HIST（依寫入順序） */
	public final List<AcctHist> hists = new ArrayList<>();

	public static final AttachmentService.PendingFile PDF = new AttachmentService.PendingFile("公文.pdf", "pdf",
			"application/pdf", new byte[] { 0x25, 0x50, 0x44, 0x46 });

	public TestFixture() {
		when(codeDao.findStages()).thenReturn(stages());
		when(codeDao.findCategories()).thenReturn(categories());
		when(codeDao.findAssetClasses()).thenReturn(assetClasses());
		when(docSeqDao.findCurrSeq(anyString(), anyString())).thenReturn(null);
		when(docSeqDao.insertFirst(anyString(), anyString(), any(), any())).thenReturn(1);
		when(attachmentService.check(any())).thenAnswer(inv -> inv.getArgument(0) == null ? null : PDF);

		when(acctDao.findById(anyString())).thenAnswer(inv -> accts.get(inv.<String>getArgument(0)));
		when(acctDao.insertOnSubmit(any(), any(), any())).thenReturn(1);
		when(acctDao.markAddRejected(any(), any(), any())).thenReturn(1);
		when(acctDao.resubmitAdd(any(), any(), any())).thenReturn(1);
		when(acctDao.applyAdd(any(), any(), any(), any())).thenReturn(1);
		when(acctDao.applyModify(any(), anyBoolean(), any(), any())).thenReturn(1);
		when(acctDao.applyEnable(any(), any(), any())).thenReturn(1);
		when(acctDao.applyState(any(), any(), any(), any(), any())).thenReturn(1);
		when(acctDao.updateAcctName(any(), any(), any(), any())).thenReturn(1);
		when(acctDao.updateReviewDates(any(), any(), any(), any(), any())).thenReturn(1);
		when(acctDao.flagReview(any(), any(), any(), any(), any(), any())).thenReturn(1);
		when(acctDao.clearReview(any(), any(), any(), any())).thenReturn(1);

		when(acctHistDao.insert(any())).thenAnswer(inv -> {
			AcctHist h = new AcctHist();
			BeanUtils.copyProperties(inv.getArgument(0), h);
			hists.add(h);
			return 1;
		});
		when(acctHistDao.findSelfCheckActs(anyString())).thenAnswer(inv -> hists.stream()
				.filter(h -> Objects.equals(h.getReqId(), inv.getArgument(0))).toList());
		when(acctHistDao.countReqActor(anyString(), anyString())).thenAnswer(inv -> hists.stream()
				.filter(h -> Objects.equals(h.getReqId(), inv.getArgument(0)) && (Objects.equals(h.getActBy(), inv.getArgument(1))
						|| Objects.equals(h.getActAsId(), inv.getArgument(1))))
				.count());

		stubRequestStore();
	}

	private void stubRequestStore() {
		when(acctReqDao.findById(anyString())).thenAnswer(inv -> req != null && req.getReqId().equals(inv.getArgument(0))
				? copy(req) : null);
		when(acctReqDao.insert(any(), any(), any())).thenAnswer(inv -> {
			AcctReq c = inv.getArgument(0);
			AcctReqDetail r = new AcctReqDetail();
			BeanUtils.copyProperties(c, r);
			r.setReqStateCode("REVIEW");
			r.setCurrStageId(2);
			r.setVersionNo(0);
			r.setResubCnt(0);
			r.setRejectCnt(0);
			r.setVerifyRejectCnt(0);
			r.setApplyDate(inv.getArgument(1));
			r.setSubmitDate(inv.getArgument(1));
			req = r;
			return 1;
		});
		when(acctReqDao.approve(anyString(), anyInt(), anyInt(), anyString(), anyInt(), any(), any())).thenAnswer(inv -> {
			if (!match(inv.getArgument(0), inv.getArgument(1), "REVIEW", inv.getArgument(2))) {
				return 0;
			}
			req.setReqStateCode(inv.getArgument(3));
			req.setCurrStageId(inv.getArgument(4));
			return bump();
		});
		when(acctReqDao.reject(anyString(), anyInt(), anyInt(), any(), any(), any(), any())).thenAnswer(inv -> {
			if (!match(inv.getArgument(0), inv.getArgument(1), "REVIEW", inv.getArgument(2))) {
				return 0;
			}
			req.setReqStateCode("REJECTED");
			req.setCurrStageId(null);
			req.setRejectCnt(req.getRejectCnt() + 1);
			req.setRejectStageId(inv.getArgument(2));
			req.setRejectReason(inv.getArgument(3));
			req.setRejectBy(inv.getArgument(4));
			return bump();
		});
		when(acctReqDao.execDone(anyString(), anyInt(), any(), anyBoolean(), any(), any(), any(), any())).thenAnswer(inv -> {
			if (!match(inv.getArgument(0), inv.getArgument(1), "EXEC", 6)) {
				return 0;
			}
			req.setReqStateCode("VERIFY");
			req.setCurrStageId(7);
			req.setExecDescr(inv.getArgument(2));
			if (inv.<Boolean>getArgument(3)) {
				req.setCardNo(inv.getArgument(4));
				req.setAcctName(inv.getArgument(4));
			}
			req.setExecBy(inv.getArgument(5));
			req.setExecDate(inv.getArgument(6));
			if (req.getEffectDate() == null) {
				req.setEffectDate(inv.getArgument(6));
			}
			return bump();
		});
		when(acctReqDao.verifyOk(anyString(), anyInt(), any(), any(), any())).thenAnswer(inv -> {
			if (!match(inv.getArgument(0), inv.getArgument(1), "VERIFY", 7)) {
				return 0;
			}
			req.setReqStateCode("APPROVED");
			req.setCurrStageId(null);
			req.setVerifyBy(inv.getArgument(2));
			req.setDoneDate(inv.getArgument(3));
			return bump();
		});
		when(acctReqDao.verifyReject(anyString(), anyInt(), any(), any(), any())).thenAnswer(inv -> {
			if (!match(inv.getArgument(0), inv.getArgument(1), "VERIFY", 7)) {
				return 0;
			}
			req.setReqStateCode("EXEC");
			req.setCurrStageId(6);
			req.setVerifyRejectCnt(req.getVerifyRejectCnt() + 1);
			req.setVerifyRejectReason(inv.getArgument(2));
			return bump();
		});
		when(acctReqDao.resubmit(any(), anyInt(), any(), any(), any())).thenAnswer(inv -> {
			AcctReq c = inv.getArgument(0);
			if (req == null || !req.getReqId().equals(c.getReqId()) || req.getVersionNo() != inv.<Integer>getArgument(1)
					|| !"REJECTED".equals(req.getReqStateCode()) || req.getCurrStageId() != null
					|| !req.getApplyBy().equals(inv.getArgument(2))) {
				return 0;
			}
			req.setApplyReason(c.getApplyReason());
			req.setApplyDept(c.getApplyDept());
			req.setReqStateCode("REVIEW");
			req.setCurrStageId(2);
			req.setResubCnt(req.getResubCnt() + 1);
			req.setSubmitDate(inv.getArgument(3));
			return bump();
		});
		when(acctReqDao.countInFlight(anyString(), any())).thenReturn(0L);
		when(attachDao.findById(anyLong())).thenReturn(null);
	}

	private boolean match(String reqId, int versionNo, String state, int stage) {
		return req != null && req.getReqId().equals(reqId) && req.getVersionNo() == versionNo
				&& state.equals(req.getReqStateCode()) && Integer.valueOf(stage).equals(req.getCurrStageId());
	}

	private int bump() {
		req.setVersionNo(req.getVersionNo() + 1);
		return 1;
	}

	public static AcctReqDetail copy(AcctReqDetail src) {
		AcctReqDetail d = new AcctReqDetail();
		BeanUtils.copyProperties(src, d);
		return d;
	}

	/** 依寫入順序的歷史動作 */
	public List<String> histActions() {
		return hists.stream().map(AcctHist::getActionCode).toList();
	}

	public JobService jobService(boolean allowBaseDate) {
		return new JobService(acctDao, acctHistDao, jobRunDao, notifyLogDao, emgDao, notificationService, permissionService, tx,
				clock, true, allowBaseDate);
	}

	// ------------------------------------------------------------
	// 初始資料（Table 6.4、8.2、8.3）
	// ------------------------------------------------------------

	public static List<ApprStage> stages() {
		return List.of(stage(1, "申請人送出", "SUBMIT", null), stage(2, "機房管理員", "APPR", "APPR_G2"),
				stage(3, "Infra 主管", "APPR", "APPR_G3"), stage(4, "資訊治理", "APPR", "APPR_G4"),
				stage(5, "資訊主管", "APPR", "APPR_G5"), stage(6, "執行人", "EXEC", null),
				stage(7, "資訊治理複驗", "VERIFY", "VERIFY_G7"));
	}

	private static ApprStage stage(int id, String name, String type, String permId) {
		ApprStage s = new ApprStage();
		s.setStageId(id);
		s.setStageName(name);
		s.setStageTypeCode(type);
		s.setPermId(permId);
		return s;
	}

	public static List<Category> categories() {
		List<Category> list = new ArrayList<>();
		String[][] rows = { { "HOST", "主機帳號", "1" }, { "DB", "資料庫帳號", "1" }, { "APP", "應用系統帳號", "0" },
				{ "SVC", "服務帳號", "0" }, { "NET", "網路設備帳號", "0" }, { "VENDOR", "廠商/委外帳號", "0" },
				{ "CERT", "憑證/金鑰", "0" }, { "DOOR", "機房門禁卡", "1" } };
		for (String[] r : rows) {
			Category c = new Category();
			c.setCatgId(r[0]);
			c.setCatgName(r[1]);
			c.setEnabled("1".equals(r[2]));
			list.add(c);
		}
		return list;
	}

	public static List<AssetClass> assetClasses() {
		return List.of(asset("H01", true, true), asset("H02", false, true), asset("H05", true, true), asset("H07", false, false),
				asset("S01", true, true), asset("S02", false, true));
	}

	private static AssetClass asset(String id, boolean trigger, boolean selectable) {
		AssetClass a = new AssetClass();
		a.setAssetClassId(id);
		a.setReviewTrigger(trigger);
		a.setSelectable(selectable);
		return a;
	}

	/** 已核准帳號 */
	public Acct acct(String acctId, String catgId, String state) {
		Acct a = new Acct();
		a.setAcctId(acctId);
		a.setCatgId(catgId);
		a.setAcctName("acct_" + acctId);
		a.setSysName("系統");
		a.setAssetClassId("H02");
		a.setIsPriv(0);
		a.setHolderName("使用人");
		a.setAccessLevelCode("RO");
		a.setExpiryTypeCode("PERM");
		a.setAcctStateCode(state);
		a.setApplyBy(KEVIN);
		a.setInFlight(false);
		accts.put(acctId, a);
		return a;
	}
}
