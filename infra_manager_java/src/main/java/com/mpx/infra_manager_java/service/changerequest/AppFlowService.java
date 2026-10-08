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
//           2026-10-07 S9 R1：加 resubmit（補件並重送）。鎖外先驗表單（AppDraftValidator）與補件說明長度；鎖是
//           REJECTED → IN_REVIEW 的條件式 UPDATE（只限申請人，admin 不能代補）；鎖內先把舊版內容組成 AppVersionSnapshot
//           寫進 IM_APP_VER（APP_VER_NO＝舊版次，CLOSE_STATUS_CODE 依該版事件推算、VER_REASON 放退件意見）→ 覆寫主檔內容、
//           CURR_VER_NO + 1、RESUB_MEMO、依新優先等級重算 FLOW_ID → 子表整批重建 → 重讀主檔跑必填檢核（缺就 400 整筆
//           rollback）→ 用與送審共用的 startApproval 建新版簽核實例 → 事件 RESUBMIT（補件說明進 MEMO）。
//           舊版 IM_APPR 維持 REJECTED 不動，歷次簽核紀錄照 verNo 查得到
//           2026-10-08 S8 R2：接信件事件 1～6（MailNotifier，寫 IM_MAIL_OUTBOX、與業務同交易）：startApproval 末尾寄
//           「待簽核」給第一關候選人（送審與補件共用）；同意進下一關寄「待簽核」給新關卡、末關同意寄「核准完成」給申請人、
//           退件寄「退件」給退件群組；撤回在 closeOpenSteps 之前寄「撤回通知」給當下待簽關卡候選人（關掉就找不到目前關卡）
// ============================================================

import java.sql.Timestamp;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mpx.infra_manager_java.dao.changerequest.AppDao;
import com.mpx.infra_manager_java.dao.changerequest.AppVerDao;
import com.mpx.infra_manager_java.dao.changerequest.AppWriteDao;
import com.mpx.infra_manager_java.dao.changerequest.ApprovalDao;
import com.mpx.infra_manager_java.dao.changerequest.ApprovalWriteDao;
import com.mpx.infra_manager_java.dao.changerequest.AttachDao;
import com.mpx.infra_manager_java.dao.changerequest.FormOptionDao;
import com.mpx.infra_manager_java.model.auth.AuthUser;
import com.mpx.infra_manager_java.model.changerequest.AppDetail;
import com.mpx.infra_manager_java.model.changerequest.AppDraft;
import com.mpx.infra_manager_java.model.changerequest.AppLockRow;
import com.mpx.infra_manager_java.model.changerequest.AppRow;
import com.mpx.infra_manager_java.model.changerequest.AppVersionSnapshot;
import com.mpx.infra_manager_java.model.changerequest.ApprRow;
import com.mpx.infra_manager_java.model.changerequest.ApprStepRow;
import com.mpx.infra_manager_java.model.changerequest.CandRow;
import com.mpx.infra_manager_java.model.changerequest.DecisionRequest;
import com.mpx.infra_manager_java.model.changerequest.EventRow;
import com.mpx.infra_manager_java.model.changerequest.FlowActionRequest;
import com.mpx.infra_manager_java.model.changerequest.FormOptionRow;
import com.mpx.infra_manager_java.model.changerequest.OptionRow;
import com.mpx.infra_manager_java.model.changerequest.ResubmitRequest;
import com.mpx.infra_manager_java.service.mail.MailNotifier;
import com.mpx.infra_manager_java.service.sysparam.SysParamService;
import com.mpx.infra_manager_java.util.TaiwanTime;
import com.mpx.infra_manager_java.util.TextLength;
import com.mpx.infra_manager_java.web.ApiBadRequestException;
import com.mpx.infra_manager_java.web.ApiConflictException;
import com.mpx.infra_manager_java.web.ApiNotFoundException;

import tools.jackson.databind.ObjectMapper;

@Service
public class AppFlowService {

	public static final String STATUS_DRAFT = "DRAFT";
	public static final String STATUS_IN_REVIEW = "IN_REVIEW";
	public static final String EVENT_SUBMIT = "SUBMIT";
	public static final String EVENT_RECALL = "RECALL";
	public static final String EVENT_RESUBMIT = "RESUBMIT";
	/** 補件時依舊版事件推算的版次結束方式（IM_APP_VER.CLOSE_STATUS_CODE） */
	public static final String CLOSE_REJECTED = "REJECTED";
	public static final String CLOSE_EXEC_REJECTED = "EXEC_REJECTED";
	public static final String CLOSE_GOV_RETURNED = "GOV_RETURNED";
	public static final String MSG_RESUBMIT_NOT_REJECTED = "申請單不是退件狀態，無法補件，請重新載入頁面";
	public static final String MSG_RESUBMIT_FORBIDDEN = "只有申請人可以補件";

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
	private final AppVerDao appVerDao;
	private final AttachDao attachDao;
	private final ObjectMapper objectMapper;
	private final MailNotifier mailNotifier;

	public AppFlowService(AppDao appDao, AppWriteDao appWriteDao, ApprovalDao approvalDao,
			ApprovalWriteDao approvalWriteDao, FormOptionDao formOptionDao, SysParamService sysParamService,
			AppVerDao appVerDao, AttachDao attachDao, ObjectMapper objectMapper, MailNotifier mailNotifier) {
		this.appDao = appDao;
		this.appWriteDao = appWriteDao;
		this.approvalDao = approvalDao;
		this.approvalWriteDao = approvalWriteDao;
		this.formOptionDao = formOptionDao;
		this.sysParamService = sysParamService;
		this.appVerDao = appVerDao;
		this.attachDao = attachDao;
		this.objectMapper = objectMapper;
		this.mailNotifier = mailNotifier;
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
		startApproval(appId, verNo, flowId, app.getApplyUserId(), me.userId());

		approvalWriteDao.insertEvent(appId, verNo, EVENT_SUBMIT, me.userId(), null);
		return rowVerNo + 1;
	}

	/**
	 * 補件並重送：REJECTED → IN_REVIEW，只限申請人（admin 也不能代補）。
	 * 鎖內先把舊版快照寫進 IM_APP_VER，再以新表單覆寫主檔（CURR_VER_NO + 1）、重建子表、必填檢核、建新版簽核實例；
	 * 回新的 rowVerNo
	 */
	@Transactional
	public long resubmit(String appId, ResubmitRequest request, AuthUser me) {
		long rowVerNo = requireVersion(appId, request == null ? null : request.rowVerNo());
		String memo = TextLength.check("resubMemo", "補件說明", request.resubMemo(), TextLength.LIMIT_SHORT);
		if (memo != null && memo.isBlank()) {
			memo = null;
		}
		List<FormOptionRow> options = formOptionDao.findActive();
		AppDraft draft = AppDraftValidator.validate(request.form(), options);
		String flowId = AppDraftService.flowFor(draft.prioCode(), sysParamService.flowPolicy(), options);

		int rows = appWriteDao.transition(appId, rowVerNo, STATUS_REJECTED, STATUS_IN_REVIEW, me.userId(), true);
		if (rows == 0) {
			AppLockRow state = appWriteDao.findLockState(appId);
			if (state == null) {
				throw new ApiNotFoundException(AppQueryService.MSG_APP_NOT_FOUND);
			}
			if (!me.userId().equals(state.getApplyUserId())) {
				throw new AccessDeniedException(MSG_RESUBMIT_FORBIDDEN);
			}
			if (!STATUS_REJECTED.equals(state.getAppStatusCode())) {
				throw new ApiConflictException(MSG_RESUBMIT_NOT_REJECTED);
			}
			throw new ApiConflictException(MSG_STALE);
		}

		// 鎖內：舊版內容還在主檔與子表，先快照再覆寫
		AppRow old = appDao.findById(appId)
				.orElseThrow(() -> new ApiNotFoundException(AppQueryService.MSG_APP_NOT_FOUND));
		int oldVerNo = old.getCurrVerNo() == null ? 1 : old.getCurrVerNo();
		ApprRow oldAppr = approvalDao.findCurrent(appId, oldVerNo).orElse(null);
		List<ApprStepRow> oldSteps = oldAppr == null ? List.of() : approvalDao.findSteps(oldAppr.getApprId());
		List<EventRow> oldEvents = appDao.findEvents(appId).stream()
				.filter(e -> e.getAppVerNo() != null && e.getAppVerNo() == oldVerNo).toList();
		VersionClose close = closeOf(oldEvents, oldSteps);
		AppVersionSnapshot snapshot = snapshot(old, oldVerNo, oldAppr == null ? null : oldAppr.getCloseDate());
		appVerDao.insertVersion(appId, oldVerNo, close.code(), close.reason(), objectMapper.writeValueAsString(snapshot),
				me.userId());

		appWriteDao.updateForResubmit(appId, flowId, memo, me.userId(), draft);
		appWriteDao.deleteChildren(appId);
		appWriteDao.insertChildren(appId, me.userId(), draft);

		AppRow app = appDao.findById(appId)
				.orElseThrow(() -> new ApiNotFoundException(AppQueryService.MSG_APP_NOT_FOUND));
		AppSubmitValidator.validate(app);
		int verNo = oldVerNo + 1;
		startApproval(appId, verNo, flowId, app.getApplyUserId(), me.userId());

		approvalWriteDao.insertEvent(appId, verNo, EVENT_RESUBMIT, me.userId(), memo);
		return rowVerNo + 1;
	}

	/**
	 * 建立某一版的簽核實例：IM_APPR → 查回 APPR_ID → 展開關卡 → 展開候選人（排除申請人）；
	 * 沒有待簽關卡或任一待簽關卡 0 人就 400（送審與補件共用；S11 AI 閘門掛這裡）
	 */
	private void startApproval(String appId, int verNo, String flowId, String applyUserId, String by) {
		approvalWriteDao.insertAppr(appId, verNo, flowId, by);
		ApprRow appr = approvalWriteDao.findPendingAppr(appId, verNo);
		if (appr == null) {
			throw new IllegalStateException("簽核實例寫入後查不回來");
		}
		long apprId = appr.getApprId();

		approvalWriteDao.insertSteps(apprId, flowId, by);
		if (approvalWriteDao.countOpenSteps(apprId) == 0) {
			throw new ApiBadRequestException(MSG_NO_STEP);
		}
		approvalWriteDao.insertCandidates(apprId, applyUserId, by);
		List<ApprStepRow> empty = approvalWriteDao.findOpenStepsWithoutCandidate(apprId);
		if (!empty.isEmpty()) {
			ApprStepRow s = empty.get(0);
			throw new ApiBadRequestException("第 " + s.getSeqNo() + " 關（" + s.getStepName() + "）沒有可簽核的人，請聯絡管理員");
		}
		mailNotifier.stepPending(appId, apprId, by);
	}

	/** 版次結束方式與原因（IM_APP_VER 的 CLOSE_STATUS_CODE 與 VER_REASON） */
	record VersionClose(String code, String reason) {
	}

	/**
	 * 依該版事件推算：有 EXEC_REJECT → EXEC_REJECTED、有 GOV_RETURN → GOV_RETURNED（原因取該事件 MEMO，同類多筆取最後一筆）；
	 * 其他 → REJECTED，原因取簽核退件那一關的 MEMO
	 */
	static VersionClose closeOf(List<EventRow> events, List<ApprStepRow> steps) {
		EventRow execReject = lastEvent(events, "EXEC_REJECT");
		if (execReject != null) {
			return new VersionClose(CLOSE_EXEC_REJECTED, execReject.getMemo());
		}
		EventRow govReturn = lastEvent(events, "GOV_RETURN");
		if (govReturn != null) {
			return new VersionClose(CLOSE_GOV_RETURNED, govReturn.getMemo());
		}
		String reason = steps.stream().filter(s -> "REJECTED".equals(s.getStepStatusCode())).map(ApprStepRow::getMemo)
				.filter(m -> m != null && !m.isBlank()).findFirst().orElse(null);
		return new VersionClose(CLOSE_REJECTED, reason);
	}

	private static EventRow lastEvent(List<EventRow> events, String code) {
		EventRow found = null;
		for (EventRow e : events) {
			if (code.equals(e.getEventCode())) {
				found = e;
			}
		}
		return found;
	}

	/**
	 * 舊版表單快照（施工計畫 ③）：主檔與子表的現況，附件只列 OWNER_TYPE=APP 且在舊版結束（closedAt）之前上傳的檔，
	 * 退件後補上傳的附件屬於新版
	 */
	private AppVersionSnapshot snapshot(AppRow app, int verNo, Timestamp closedAt) {
		String appId = app.getAppId();
		List<OptionRow> options = appDao.findOptions(appId);
		List<AppDetail.Attachment> attachments = attachDao.findByApp(appId).stream()
				.filter(a -> "APP".equals(a.getOwnerType()))
				.filter(a -> closedAt == null || a.getCreateDate() == null || !a.getCreateDate().after(closedAt))
				.map(a -> new AppDetail.Attachment(a.getAttachId(), a.getOwnerType(), a.getOwnerId(), a.getOrigFileName(),
						a.getFileByteQty(), a.getMimeType(), TaiwanTime.formatDateTime(a.getCreateDate())))
				.toList();
		return new AppVersionSnapshot(AppVersionSnapshot.SCHEMA, appId, verNo, app.getAppTitle(), app.getPrioCode(),
				app.getPrioName(),
				new AppDetail.Applicant(app.getApplyUserId(), app.getApplyUserName(), app.getApplyDeptName(),
						app.getApplyTel(), app.getApplyEmail()),
				TaiwanTime.formatDate(app.getApplyDate()), flag(app.getIsSelfExec()), flag(app.getIsSupExec()),
				app.getWorkModeCode(), app.getRemoteMethod(),
				new AppDetail.Supplier(app.getSupName(), app.getSupCntct(), app.getSupTel(), app.getSupHeadCnt()),
				app.getWorkSubj(), app.getImpactDesc(), app.getWorkDetail(), app.getRiskDesc(), app.getRollBackPlan(),
				AppQueryService.optionsOf(options, Set.of("CATG", "CATG_ITEM")),
				AppQueryService.optionsOf(options, Set.of("REASON")), app.getOtherReason(),
				AppQueryService.optionsOf(options, Set.of("SCOPE")),
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
				app.getFlowId(), app.getFlowName(), app.getResubMemo(), attachments);
	}

	private static boolean flag(Integer value) {
		return value != null && value == 1;
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
		// 先寄再關關卡：關卡 CANCELLED 之後就找不到「目前待簽關卡」的候選人
		mailNotifier.recalled(appId, apprId, reason, me.userId());
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
				mailNotifier.approved(appId, apprId, me.userId());
			} else {
				mailNotifier.stepPending(appId, apprId, me.userId());
			}
		} else {
			approvalWriteDao.closeOpenSteps(apprId, "SKIPPED", me.userId());
			approvalWriteDao.closeAppr(apprId, "REJECTED", me.userId());
			appWriteDao.updateStatus(appId, STATUS_REJECTED, me.userId());
			mailNotifier.rejected(appId, apprId, current.getStepName(), me.userName(), memo, me.userId());
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
