package com.mpx.infra_manager_java.service.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-07
// 變更說明: 新增：申請單流程動作（S7 R1：送審、撤回；R2 加簽核）。每個動作一個交易，第一句都是 AppWriteDao.transition
//           的條件式 UPDATE（版本＋狀態＋申請人條件、ROW_VER_NO + 1），同時取得整張單的列鎖；0 列時再查一次現況分流
//           404／403／409（與 S6 編輯草稿同一套）。權限在鎖內重判，不信任前端 permissions。
//           送審：鎖 → 讀單做必填檢核（②A）→ 依當下流程政策重算 FLOW_ID（⑦A）→ 建 IM_APPR 並查回 APPR_ID →
//           展開關卡（只通知 SKIPPED ⑥A）→ 展開候選人並排除申請人（使用者裁示 ①B）→ 任一待簽關卡 0 人就 400 擋下 → 事件 SUBMIT。
//           撤回：鎖（只限申請人）→ 已有關卡簽過 409 → 未結束關卡 CANCELLED、IM_APPR RECALLED → 事件 RECALL（原因進 MEMO）。
//           任何一步丟例外整筆 rollback，不會留下半套的簽核實例。
//           2026-10-07 S7 R2：加 decide（同意／退件）。鎖同樣是主檔條件式 UPDATE（IN_REVIEW → IN_REVIEW、版本 +1），
//           兩人同時簽後到者 409；鎖內重判是否為目前關卡簽核人（候選人優先、無候選人看流程指定人）；
//           關卡 WHERE PENDING 條件式 UPDATE 當第二道防線。同意→下一關 PENDING 或結案 APPROVED；退件→剩餘 SKIPPED、結案 REJECTED。
//           同意／退件不寫 IM_APP_EVENT（DDL 事件碼沒有），紀錄只在 IM_APPR_STEP
// ============================================================

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mpx.infra_manager_java.dao.changerequest.AppDao;
import com.mpx.infra_manager_java.dao.changerequest.AppWriteDao;
import com.mpx.infra_manager_java.dao.changerequest.ApprovalDao;
import com.mpx.infra_manager_java.dao.changerequest.ApprovalWriteDao;
import com.mpx.infra_manager_java.dao.changerequest.FormOptionDao;
import com.mpx.infra_manager_java.model.auth.AuthUser;
import com.mpx.infra_manager_java.model.changerequest.AppLockRow;
import com.mpx.infra_manager_java.model.changerequest.AppRow;
import com.mpx.infra_manager_java.model.changerequest.ApprRow;
import com.mpx.infra_manager_java.model.changerequest.ApprStepRow;
import com.mpx.infra_manager_java.model.changerequest.CandRow;
import com.mpx.infra_manager_java.model.changerequest.DecisionRequest;
import com.mpx.infra_manager_java.model.changerequest.FlowActionRequest;
import com.mpx.infra_manager_java.service.sysparam.SysParamService;
import com.mpx.infra_manager_java.util.TextLength;
import com.mpx.infra_manager_java.web.ApiBadRequestException;
import com.mpx.infra_manager_java.web.ApiConflictException;
import com.mpx.infra_manager_java.web.ApiNotFoundException;

@Service
public class AppFlowService {

	public static final String STATUS_DRAFT = "DRAFT";
	public static final String STATUS_IN_REVIEW = "IN_REVIEW";
	public static final String EVENT_SUBMIT = "SUBMIT";
	public static final String EVENT_RECALL = "RECALL";

	public static final String MSG_STALE = "申請單已在其他地方修改過，請重新載入頁面";
	public static final String MSG_SUBMIT_NOT_DRAFT = "申請單已不是草稿，無法送審，請重新載入頁面";
	public static final String MSG_SUBMIT_FORBIDDEN = "只有申請人或管理員可以送審";
	public static final String MSG_NO_STEP = "流程沒有可用的關卡，請聯絡管理員";
	public static final String MSG_RECALL_NOT_IN_REVIEW = "申請單不在審核中，無法撤回，請重新載入頁面";
	public static final String MSG_RECALL_FORBIDDEN = "只有申請人可以撤回";
	public static final String MSG_RECALL_DECIDED = "已有關卡簽核完成，無法撤回";
	public static final String STATUS_APPROVED = "APPROVED";
	public static final String STATUS_REJECTED = "REJECTED";
	public static final String MSG_DECIDE_NOT_IN_REVIEW = "申請單不在審核中，無法簽核，請重新載入頁面";
	public static final String MSG_DECIDE_STALE = "此關卡已被其他人簽核或申請單已變更，請重新載入頁面";
	public static final String MSG_DECIDE_FORBIDDEN = "您不是目前關卡的簽核人";

	private static final Pattern APP_ID = Pattern.compile("^[A-Z0-9-]{1,20}$");

	private final AppDao appDao;
	private final AppWriteDao appWriteDao;
	private final ApprovalDao approvalDao;
	private final ApprovalWriteDao approvalWriteDao;
	private final FormOptionDao formOptionDao;
	private final SysParamService sysParamService;

	public AppFlowService(AppDao appDao, AppWriteDao appWriteDao, ApprovalDao approvalDao,
			ApprovalWriteDao approvalWriteDao, FormOptionDao formOptionDao, SysParamService sysParamService) {
		this.appDao = appDao;
		this.appWriteDao = appWriteDao;
		this.approvalDao = approvalDao;
		this.approvalWriteDao = approvalWriteDao;
		this.formOptionDao = formOptionDao;
		this.sysParamService = sysParamService;
	}

	/** 送審：DRAFT → IN_REVIEW 並建立簽核實例；回新的 rowVerNo */
	@Transactional
	public long submit(String appId, FlowActionRequest request, AuthUser me) {
		long rowVerNo = requireVersion(appId, request);
		boolean admin = me.roles() != null && me.roles().contains("admin");
		int rows = appWriteDao.transition(appId, rowVerNo, STATUS_DRAFT, STATUS_IN_REVIEW, me.userId(), !admin);
		if (rows == 0) {
			AppLockRow state = appWriteDao.findLockState(appId);
			if (state == null) {
				throw new ApiNotFoundException(AppQueryService.MSG_APP_NOT_FOUND);
			}
			if (!admin && !me.userId().equals(state.getApplyUserId())) {
				throw new AccessDeniedException(MSG_SUBMIT_FORBIDDEN);
			}
			if (!STATUS_DRAFT.equals(state.getAppStatusCode())) {
				throw new ApiConflictException(MSG_SUBMIT_NOT_DRAFT);
			}
			throw new ApiConflictException(MSG_STALE);
		}

		AppRow app = appDao.findById(appId)
				.orElseThrow(() -> new ApiNotFoundException(AppQueryService.MSG_APP_NOT_FOUND));
		AppSubmitValidator.validate(app);

		String flowId = AppDraftService.flowFor(app.getPrioCode(), sysParamService.flowPolicy(),
				formOptionDao.findActive());
		appWriteDao.updateFlowId(appId, flowId, me.userId());

		int verNo = app.getCurrVerNo() == null ? 1 : app.getCurrVerNo();
		approvalWriteDao.insertAppr(appId, verNo, flowId, me.userId());
		ApprRow appr = approvalWriteDao.findPendingAppr(appId, verNo);
		if (appr == null) {
			throw new IllegalStateException("簽核實例寫入後查不回來");
		}
		long apprId = appr.getApprId();

		approvalWriteDao.insertSteps(apprId, flowId, me.userId());
		if (approvalWriteDao.countOpenSteps(apprId) == 0) {
			throw new ApiBadRequestException(MSG_NO_STEP);
		}
		approvalWriteDao.insertCandidates(apprId, app.getApplyUserId(), me.userId());
		List<ApprStepRow> empty = approvalWriteDao.findOpenStepsWithoutCandidate(apprId);
		if (!empty.isEmpty()) {
			ApprStepRow s = empty.get(0);
			throw new ApiBadRequestException("第 " + s.getSeqNo() + " 關（" + s.getStepName() + "）沒有可簽核的人，請聯絡管理員");
		}

		approvalWriteDao.insertEvent(appId, verNo, EVENT_SUBMIT, me.userId(), null);
		return rowVerNo + 1;
	}

	/** 撤回：IN_REVIEW → DRAFT，只限申請人且尚無任何關卡簽核完成；回新的 rowVerNo */
	@Transactional
	public long recall(String appId, FlowActionRequest request, AuthUser me) {
		long rowVerNo = requireVersion(appId, request);
		String reason = TextLength.check("reason", "撤回原因", request.reason(), TextLength.LIMIT_SHORT);
		if (reason != null && reason.isBlank()) {
			reason = null;
		}
		int rows = appWriteDao.transition(appId, rowVerNo, STATUS_IN_REVIEW, STATUS_DRAFT, me.userId(), true);
		if (rows == 0) {
			AppLockRow state = appWriteDao.findLockState(appId);
			if (state == null) {
				throw new ApiNotFoundException(AppQueryService.MSG_APP_NOT_FOUND);
			}
			if (!me.userId().equals(state.getApplyUserId())) {
				throw new AccessDeniedException(MSG_RECALL_FORBIDDEN);
			}
			if (!STATUS_IN_REVIEW.equals(state.getAppStatusCode())) {
				throw new ApiConflictException(MSG_RECALL_NOT_IN_REVIEW);
			}
			throw new ApiConflictException(MSG_STALE);
		}

		AppRow app = appDao.findById(appId)
				.orElseThrow(() -> new ApiNotFoundException(AppQueryService.MSG_APP_NOT_FOUND));
		int verNo = app.getCurrVerNo() == null ? 1 : app.getCurrVerNo();
		ApprRow appr = approvalWriteDao.findPendingAppr(appId, verNo);
		if (appr == null) {
			throw new IllegalStateException("審核中的申請單沒有進行中的簽核實例");
		}
		long apprId = appr.getApprId();
		if (approvalWriteDao.countDecidedSteps(apprId) > 0) {
			throw new ApiConflictException(MSG_RECALL_DECIDED);
		}
		approvalWriteDao.closeOpenSteps(apprId, "CANCELLED", me.userId());
		approvalWriteDao.closeAppr(apprId, "RECALLED", me.userId());
		approvalWriteDao.insertEvent(appId, verNo, EVENT_RECALL, me.userId(), reason);
		return rowVerNo + 1;
	}

	/**
	 * 簽核目前關卡（同意／退件）；回新的 rowVerNo。
	 * 鎖：主檔 IN_REVIEW → IN_REVIEW 的條件式 UPDATE（版本 +1），兩人同時簽時後到者版本不符 → 409；
	 * 第二道：關卡 WHERE PENDING 的條件式 UPDATE 0 列也 409。
	 * 同意：下一關 WAITING → PENDING，沒有下一關就結案 APPROVED（實例與主檔）；
	 * 退件：剩餘 WAITING → SKIPPED，實例與主檔 REJECTED
	 */
	@Transactional
	public long decide(String appId, DecisionRequest request, AuthUser me) {
		long rowVerNo = requireVersion(appId, request == null ? null : request.rowVerNo());
		String decision = DecisionPolicy.requireDecision(request.decision());
		String memo = DecisionPolicy.normalizeMemo(decision, request.memo());

		int rows = appWriteDao.transition(appId, rowVerNo, STATUS_IN_REVIEW, STATUS_IN_REVIEW, me.userId(), false);
		if (rows == 0) {
			AppLockRow state = appWriteDao.findLockState(appId);
			if (state == null) {
				throw new ApiNotFoundException(AppQueryService.MSG_APP_NOT_FOUND);
			}
			if (!STATUS_IN_REVIEW.equals(state.getAppStatusCode())) {
				throw new ApiConflictException(MSG_DECIDE_NOT_IN_REVIEW);
			}
			throw new ApiConflictException(MSG_DECIDE_STALE);
		}

		AppRow app = appDao.findById(appId)
				.orElseThrow(() -> new ApiNotFoundException(AppQueryService.MSG_APP_NOT_FOUND));
		int verNo = app.getCurrVerNo() == null ? 1 : app.getCurrVerNo();
		ApprRow appr = approvalWriteDao.findPendingAppr(appId, verNo);
		if (appr == null) {
			throw new IllegalStateException("審核中的申請單沒有進行中的簽核實例");
		}
		long apprId = appr.getApprId();
		List<ApprStepRow> steps = approvalDao.findSteps(apprId);
		List<CandRow> cands = approvalDao.findCandidates(apprId);
		ApprStepRow current = DecisionPolicy.currentStep(steps);
		if (current == null) {
			throw new IllegalStateException("審核中的申請單沒有待簽關卡");
		}
		if (!DecisionPolicy.isApprover(steps, cands, me.userId())) {
			throw new AccessDeniedException(MSG_DECIDE_FORBIDDEN);
		}
		if (approvalWriteDao.decideStep(current.getApprStepId(), DecisionPolicy.stepStatus(decision), me.userId(),
				memo) == 0) {
			throw new ApiConflictException(MSG_DECIDE_STALE);
		}

		if (DecisionPolicy.APPROVE.equals(decision)) {
			if (approvalWriteDao.activateNext(apprId, me.userId()) == 0) {
				approvalWriteDao.closeAppr(apprId, "APPROVED", me.userId());
				appWriteDao.updateStatus(appId, STATUS_APPROVED, me.userId());
			}
		} else {
			approvalWriteDao.closeOpenSteps(apprId, "SKIPPED", me.userId());
			approvalWriteDao.closeAppr(apprId, "REJECTED", me.userId());
			appWriteDao.updateStatus(appId, STATUS_REJECTED, me.userId());
		}
		return rowVerNo + 1;
	}

	private static long requireVersion(String appId, FlowActionRequest request) {
		return requireVersion(appId, request == null ? null : request.rowVerNo());
	}

	/** 單號格式不對視為找不到（404）；缺版本號 400 */
	private static long requireVersion(String appId, Long rowVerNo) {
		if (appId == null || !APP_ID.matcher(appId).matches()) {
			throw new ApiNotFoundException(AppQueryService.MSG_APP_NOT_FOUND);
		}
		if (rowVerNo == null) {
			throw new ApiBadRequestException(AppDraftService.MSG_NO_VERSION);
		}
		return rowVerNo;
	}
}
