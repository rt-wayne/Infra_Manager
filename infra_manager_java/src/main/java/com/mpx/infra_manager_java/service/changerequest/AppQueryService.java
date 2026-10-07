package com.mpx.infra_manager_java.service.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：申請單列表與檢視的唯讀服務（S4）。
//           list：驗證篩選值（狀態 7 碼、分級 P1～P4、來源 ONLINE／IMPORTED，不合 → 400 帶訊息）、q 去空白並限 100 字、
//                 from>to → 400、page<1 視為 1、from 與 to 都沒給時預設最近 90 天（舊系統行為）；
//                 待我簽核總數 mineCount 不受篩選影響（給列表上方的計數用）。
//           detail：單號格式不符或查不到 → 404「找不到申請單」；簽核欄沒有實例時用流程定義展開（狀態 WAITING）；
//                 附件只給中繼資料，下載走 AttachmentService
//           S6 回合二 a（Claude Opus 5.5，2026-10-06）：detail 帶 rowVerNo 與選項的 formOptionId
// ============================================================

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.mpx.infra_manager_java.dao.changerequest.AppDao;
import com.mpx.infra_manager_java.dao.changerequest.ApprovalDao;
import com.mpx.infra_manager_java.dao.changerequest.AttachDao;
import com.mpx.infra_manager_java.model.auth.AuthUser;
import com.mpx.infra_manager_java.model.changerequest.AppDetail;
import com.mpx.infra_manager_java.model.changerequest.AppListItem;
import com.mpx.infra_manager_java.model.changerequest.AppListQuery;
import com.mpx.infra_manager_java.model.changerequest.AppListResponse;
import com.mpx.infra_manager_java.model.changerequest.AppPermissions;
import com.mpx.infra_manager_java.model.changerequest.AppRow;
import com.mpx.infra_manager_java.model.changerequest.ApprRow;
import com.mpx.infra_manager_java.model.changerequest.ApprStepRow;
import com.mpx.infra_manager_java.model.changerequest.CandRow;
import com.mpx.infra_manager_java.model.changerequest.ExecRow;
import com.mpx.infra_manager_java.model.changerequest.OptionRow;
import com.mpx.infra_manager_java.util.TaiwanTime;
import com.mpx.infra_manager_java.web.ApiBadRequestException;
import com.mpx.infra_manager_java.web.ApiNotFoundException;

@Service
public class AppQueryService {

	public static final String MSG_APP_NOT_FOUND = "找不到申請單";
	public static final String MSG_BAD_STATUS = "狀態篩選值不正確";
	public static final String MSG_BAD_PRIORITY = "分級篩選值不正確";
	public static final String MSG_BAD_SOURCE = "來源篩選值不正確";
	public static final String MSG_BAD_RANGE = "起日不得晚於迄日";
	public static final String MSG_Q_TOO_LONG = "關鍵字過長";

	static final int DEFAULT_RANGE_DAYS = 90;
	static final int Q_MAX = 100;

	private static final Set<String> STATUSES = Set.of("DRAFT", "IN_REVIEW", "APPROVED", "IN_EXECUTION", "PENDING_REVIEW",
			"EXECUTED", "REJECTED");
	private static final Set<String> PRIORITIES = Set.of("P1", "P2", "P3", "P4");
	private static final Set<String> SOURCES = Set.of("ONLINE", "IMPORTED");
	private static final Pattern APP_ID = Pattern.compile("^[A-Z0-9-]{1,20}$");

	private final AppDao appDao;
	private final ApprovalDao approvalDao;
	private final AttachDao attachDao;
	private final AppPermissionService permissionService;

	public AppQueryService(AppDao appDao, ApprovalDao approvalDao, AttachDao attachDao,
			AppPermissionService permissionService) {
		this.appDao = appDao;
		this.approvalDao = approvalDao;
		this.attachDao = attachDao;
		this.permissionService = permissionService;
	}

	// ---------- 列表 ----------

	public AppListResponse list(AppListQuery raw, AuthUser me) {
		AppListQuery q = normalize(raw);
		List<AppRow> rows = appDao.findList(q, me.userId());
		long total = appDao.countList(q, me.userId());
		long mineCount = appDao.countMine(me.userId());
		List<AppListItem> items = rows.stream().map(AppQueryService::toListItem).toList();
		return new AppListResponse(items, total, q.page(), AppListQuery.PAGE_SIZE, mineCount);
	}

	static AppListQuery normalize(AppListQuery raw) {
		String status = code(raw.status(), STATUSES, MSG_BAD_STATUS);
		String priority = code(raw.priority(), PRIORITIES, MSG_BAD_PRIORITY);
		String source = code(raw.source(), SOURCES, MSG_BAD_SOURCE);
		String q = raw.q() == null ? null : raw.q().trim();
		if (q != null && q.isEmpty()) {
			q = null;
		}
		if (q != null && q.length() > Q_MAX) {
			throw new ApiBadRequestException(MSG_Q_TOO_LONG);
		}
		LocalDate from = raw.from();
		LocalDate to = raw.to();
		if (from == null && to == null) {
			from = TaiwanTime.today().minusDays(DEFAULT_RANGE_DAYS);
		}
		if (from != null && to != null && from.isAfter(to)) {
			throw new ApiBadRequestException(MSG_BAD_RANGE);
		}
		int page = Math.max(1, raw.page());
		return new AppListQuery(status, priority, source, raw.mine(), q, from, to, page);
	}

	private static String code(String value, Set<String> allowed, String message) {
		if (value == null || value.isBlank()) {
			return null;
		}
		if (!allowed.contains(value)) {
			throw new ApiBadRequestException(message);
		}
		return value;
	}

	static AppListItem toListItem(AppRow r) {
		String approver = null;
		if (r.getCurrStepName() != null) {
			int cnt = r.getCurrStepCandCnt() == null ? 0 : r.getCurrStepCandCnt();
			approver = cnt == 1 ? r.getCurrStepCandName() : cnt > 1 ? cnt + " 人待簽" : null;
		}
		return new AppListItem(r.getAppId(), r.getAppTitle(), r.getPrioCode(), r.getPrioName(), r.getPrioColor(),
				r.getWorkSubj(), r.getApplyDeptName(), r.getApplyUserName(), r.getCurrVerNo(), r.getAppStatusCode(),
				r.getSourceCode(), r.getCurrStepName(), approver, r.getMineFlag() != null && r.getMineFlag() == 1,
				TaiwanTime.formatDateTime(r.getCreateDate()));
	}

	// ---------- 檢視 ----------

	public AppDetail detail(String appId, AuthUser me) {
		AppRow app = findApp(appId);
		int verNo = app.getCurrVerNo() == null ? 1 : app.getCurrVerNo();

		ApprRow appr = approvalDao.findCurrent(appId, verNo).orElse(null);
		List<ApprStepRow> steps = appr != null ? approvalDao.findSteps(appr.getApprId())
				: app.getFlowId() != null ? approvalDao.findFlowSteps(app.getFlowId()) : List.of();
		List<CandRow> cands = appr != null ? approvalDao.findCandidates(appr.getApprId()) : List.of();
		ExecRow exec = appDao.findExec(appId, verNo).orElse(null);

		List<OptionRow> options = appDao.findOptions(appId);
		Map<Long, List<String>> candNames = cands.stream().collect(Collectors.groupingBy(CandRow::getApprStepId,
				Collectors.mapping(c -> c.getUserName() != null ? c.getUserName() : c.getUserId(), Collectors.toList())));

		AppPermissions permissions = permissionService.compute(app, appr, steps, cands, exec, me);

		return new AppDetail(app.getAppId(), app.getAppTitle(), app.getPrioCode(), app.getPrioName(), app.getPrioColor(),
				app.getAppStatusCode(), app.getSourceCode(), app.getFlowId(), app.getFlowName(), app.getCurrVerNo(),
				app.getRowVerNo(),
				new AppDetail.Applicant(app.getApplyUserId(), app.getApplyUserName(), app.getApplyDeptName(),
						app.getApplyTel(), app.getApplyEmail()),
				TaiwanTime.formatDate(app.getApplyDate()), flag(app.getIsSelfExec()), flag(app.getIsSupExec()),
				app.getWorkModeCode(), app.getRemoteMethod(),
				new AppDetail.Supplier(app.getSupName(), app.getSupCntct(), app.getSupTel(), app.getSupHeadCnt()),
				app.getWorkSubj(), app.getImpactDesc(), app.getWorkDetail(), app.getRiskDesc(), app.getRollBackPlan(),
				optionsOf(options, Set.of("CATG", "CATG_ITEM")), optionsOf(options, Set.of("REASON")),
				app.getOtherReason(), optionsOf(options, Set.of("SCOPE")),
				appDao.findEquipments(appId).stream()
						.map(e -> new AppDetail.Equipment(e.getSeqNo(), e.getEquipName(), e.getAssetNo(), e.getModelNo(),
								e.getSerialNo(), e.getPurpDesc(), e.getMgmtIp()))
						.toList(),
				appDao.findPlanSteps(appId).stream().map(p -> new AppDetail.PlanStep(p.getSeqNo(), p.getStepText()))
						.toList(),
				new AppDetail.Schedule(TaiwanTime.formatDateTime(app.getSchedStartDate()),
						TaiwanTime.formatDateTime(app.getSchedEndDate()), app.getEstHourQty()),
				new AppDetail.Location(app.getLocSourceCode(), app.getAreaName(), app.getRackName(), app.getUnitRange(),
						app.getSiteId(), app.getRackId(), app.getUnitStartNo(), app.getUnitEndNo(), app.getOmitReason()),
				app.getResubMemo(),
				appDao.findCheckList(appId, verNo).stream()
						.map(c -> new AppDetail.CheckItem(c.getSeqNo(), c.getOptionCode(), c.getOptionName(),
								flag(c.getIsDone()), TaiwanTime.formatDateTime(c.getDoneDate()),
								c.getUserName() != null ? c.getUserName() : c.getExecUserDesc()))
						.toList(),
				exec == null ? null
						: new AppDetail.Execution(exec.getAppVerNo(), TaiwanTime.formatDateTime(exec.getActualStartDate()),
								TaiwanTime.formatDateTime(exec.getActualEndDate()), exec.getResultCode(),
								exec.getResultName(), flag(exec.getIsExcpt()), exec.getExcptDesc(),
								flag(exec.getIsFollowUp()), exec.getFollowUpDesc(), exec.getExecMemo(),
								exec.getUserName(), TaiwanTime.formatDateTime(exec.getCloseDate())),
				new AppDetail.Approval(appr == null ? null : appr.getApprId(),
						appr == null ? null : appr.getApprStatusCode(),
						appr == null ? null : TaiwanTime.formatDateTime(appr.getStartDate()),
						appr == null ? null : TaiwanTime.formatDateTime(appr.getCloseDate()),
						steps.stream().map(s -> new AppDetail.Step(s.getSeqNo(), s.getStepCode(), s.getStepName(),
								s.getStepModeCode(), flag(s.getIsNotifyOnly()), s.getStepStatusCode(), s.getUserName(),
								TaiwanTime.formatDateTime(s.getDecideDate()), s.getMemo(),
								s.getApprStepId() == null ? List.of() : candNames.getOrDefault(s.getApprStepId(), List.of())))
								.toList()),
				attachDao.findByApp(appId).stream()
						.map(a -> new AppDetail.Attachment(a.getAttachId(), a.getOwnerType(), a.getOwnerId(),
								a.getOrigFileName(), a.getFileByteQty(), a.getMimeType(),
								TaiwanTime.formatDateTime(a.getCreateDate())))
						.toList(),
				appDao.findVersions(appId).stream()
						.map(v -> new AppDetail.Version(v.getAppVerNo(), v.getCloseStatusCode(), v.getVerReason(),
								TaiwanTime.formatDateTime(v.getSnapDate())))
						.toList(),
				appDao.findEvents(appId).stream()
						.map(e -> new AppDetail.Event(e.getAppEventId(), e.getAppVerNo(), e.getEventCode(),
								e.getUserName() != null ? e.getUserName() : e.getUserId(),
								TaiwanTime.formatDateTime(e.getEventDate()), e.getMemo()))
						.toList(),
				permissions, TaiwanTime.formatDateTime(app.getCreateDate()), TaiwanTime.formatDateTime(app.getUpdateDate()));
	}

	/** 單號格式不符直接當不存在，不打 DB */
	AppRow findApp(String appId) {
		if (appId == null || !APP_ID.matcher(appId).matches()) {
			throw new ApiNotFoundException(MSG_APP_NOT_FOUND);
		}
		return appDao.findById(appId).orElseThrow(() -> new ApiNotFoundException(MSG_APP_NOT_FOUND));
	}

	/** 選項列依群組投影成檢視用 Option（AppFlowService 組版次快照時共用） */
	static List<AppDetail.Option> optionsOf(List<OptionRow> rows, Set<String> groups) {
		Function<OptionRow, AppDetail.Option> map = o -> new AppDetail.Option(o.getFormOptionId(), o.getGroupCode(),
				o.getOptionCode(), o.getOptionName(), o.getUpOptionCode(), o.getOtherText());
		return rows.stream().filter(o -> groups.contains(o.getGroupCode())).map(map).toList();
	}

	private static boolean flag(Integer value) {
		return value != null && value == 1;
	}
}
