package com.mpx.infra_manager_java.service.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：填寫執行紀錄（S10 R1，施工計畫 ①～⑦、⑫）。單一交易：
//           鎖外驗單號格式（不符 404）、rowVerNo 必填 → 便宜預檢（findLockState：單不存在 404；不是 idc_admin 也不是申請人 403，
//           不取鎖）→ ExecutionValidator 檢格式（送治理審查時另檢必填）→ 檢核項執行人工號須為啟用使用者 →
//           AppWriteDao.lockForUpdate 只比版本取鎖（0 列：單不存在 404、其餘 409 版本過期）→
//           鎖內重讀：狀態不是 APPROVED／IN_EXECUTION 409（修舊系統可重開已結案單）、權限重判 403 →
//           該版次第一次儲存時依啟用中的 CHECK_LIST 選項（SORT_NO 順序）展開檢核表 → 逐列更新本文帶到的檢核項
//           （未知序號 400；完成沒填時間補伺服器現在時間）→ 執行結果 upsert（送治理審查時寫結案人與結案時間）→
//           主檔狀態改 PENDING_REVIEW（有結果）或 IN_EXECUTION（暫存）。不寫事件（⑩：DDL 沒有 EXECUTE 事件碼）
//           2026-10-07 S10 R2：加執行端退回 reject（⑧：權限與可呼叫狀態同儲存、意見必填 → REJECTED、事件 EXEC_REJECT，
//           已暫存的檢核表與執行結果留在該版次不清）與治理審查 review（⑨：只限 governance 角色，鎖外先擋 403；
//           鎖用 transition(PENDING_REVIEW → EXECUTED／REJECTED)，0 列分流 404／409 狀態不對／409 版本過期；
//           PASS 事件 GOV_PASS、RETURN 事件 GOV_RETURN，退回意見必填、通過選填；審查人是申請人或執行人不擋，使用者裁示）。
//           補件時 AppFlowService.closeOf 依這兩種事件把舊版記為 EXEC_REJECTED／GOV_RETURNED
//           2026-10-07 S10 結案 review ①B（使用者裁示）：檢核項執行人工號改為鎖內檢查，只收「登入者本人」或「該列已存的原值」，
//           其餘 400（關掉「拿工號查同事姓名」的小路，落實 ⑪）；拿掉鎖外逐一查工號是否啟用。save 拆出展開與逐列更新兩段
//           2026-10-08 S8 R3：接信件事件 7～10（MailNotifier，寫 IM_MAIL_OUTBOX、與業務同交易）：save 送治理審查寄
//           「待審核執行結果」給治理（帶執行結果名稱；暫存不寄）；執行端退回寄「退件 - 執行階段」、治理退回寄
//           「退件 - 資訊治理審核」給退件群組；治理通過寄「執行結果已通過」給申請人。都在狀態與事件寫完之後呼叫
// ============================================================

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mpx.infra_manager_java.dao.changerequest.AppDao;
import com.mpx.infra_manager_java.dao.changerequest.AppWriteDao;
import com.mpx.infra_manager_java.dao.changerequest.ApprovalWriteDao;
import com.mpx.infra_manager_java.dao.changerequest.ExecWriteDao;
import com.mpx.infra_manager_java.dao.changerequest.FormOptionDao;
import com.mpx.infra_manager_java.model.auth.AuthUser;
import com.mpx.infra_manager_java.model.changerequest.AppLockRow;
import com.mpx.infra_manager_java.model.changerequest.AppRow;
import com.mpx.infra_manager_java.model.changerequest.ExecRejectRequest;
import com.mpx.infra_manager_java.model.changerequest.ExecutionDraft;
import com.mpx.infra_manager_java.model.changerequest.ExecutionRequest;
import com.mpx.infra_manager_java.model.changerequest.FormOptionRow;
import com.mpx.infra_manager_java.model.changerequest.GovernanceReviewRequest;
import com.mpx.infra_manager_java.service.mail.MailNotifier;
import com.mpx.infra_manager_java.util.TaiwanTime;
import com.mpx.infra_manager_java.util.TextLength;
import com.mpx.infra_manager_java.web.ApiBadRequestException;
import com.mpx.infra_manager_java.web.ApiConflictException;
import com.mpx.infra_manager_java.web.ApiNotFoundException;

@Service
public class AppExecutionService {

	public static final String MSG_EXEC_FORBIDDEN = "只有機房管理員或申請人可以填寫執行紀錄";
	public static final String MSG_EXEC_NOT_EXECUTABLE = "申請單不在待執行或執行中，無法填寫執行紀錄，請重新載入頁面";
	public static final String MSG_BAD_EXECUTOR = "檢核項執行人只能填自己，或保留該項原本登記的人";
	public static final String MSG_REJECT_FORBIDDEN = "只有機房管理員或申請人可以退回";
	public static final String MSG_REJECT_NOT_EXECUTABLE = "申請單不在待執行或執行中，無法退回，請重新載入頁面";
	public static final String MSG_REJECT_NEEDS_MEMO = "退回請填寫意見";
	public static final String MSG_REVIEW_FORBIDDEN = "只有資訊治理人員可以審核執行結果";
	public static final String MSG_REVIEW_NOT_PENDING = "申請單不是待治理審核狀態，無法審核，請重新載入頁面";
	public static final String MSG_BAD_REVIEW_DECISION = "審核決定只能是通過或退回";
	public static final String PASS = "PASS";
	public static final String RETURN = "RETURN";
	public static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
	public static final String STATUS_EXECUTED = "EXECUTED";
	public static final String EVENT_EXEC_REJECT = "EXEC_REJECT";
	public static final String EVENT_GOV_PASS = "GOV_PASS";
	public static final String EVENT_GOV_RETURN = "GOV_RETURN";
	static final String ROLE_IDC_ADMIN = "idc_admin";
	static final String ROLE_GOVERNANCE = "governance";
	static final String GROUP_CHECK_LIST = "CHECK_LIST";
	static final String GROUP_EXEC_RESULT = "EXEC_RESULT";
	static final Set<String> EXECUTABLE = Set.of("APPROVED", "IN_EXECUTION");

	private static final Pattern APP_ID = Pattern.compile("^[A-Z0-9-]{1,20}$");

	/** 儲存結果：新的 ROW_VER_NO 與儲存後的主檔狀態 */
	public record SaveResult(long rowVerNo, String statusCode) {
	}

	private final AppDao appDao;
	private final AppWriteDao appWriteDao;
	private final ExecWriteDao execWriteDao;
	private final FormOptionDao formOptionDao;
	private final ApprovalWriteDao approvalWriteDao;
	private final MailNotifier mailNotifier;

	public AppExecutionService(AppDao appDao, AppWriteDao appWriteDao, ExecWriteDao execWriteDao,
			FormOptionDao formOptionDao, ApprovalWriteDao approvalWriteDao, MailNotifier mailNotifier) {
		this.appDao = appDao;
		this.appWriteDao = appWriteDao;
		this.execWriteDao = execWriteDao;
		this.formOptionDao = formOptionDao;
		this.approvalWriteDao = approvalWriteDao;
		this.mailNotifier = mailNotifier;
	}

	@Transactional
	public SaveResult save(String appId, ExecutionRequest request, AuthUser me) {
		long rowVerNo = requireVersion(appId, request == null ? null : request.rowVerNo());

		// 鎖外便宜預檢：沒有權限的人不取鎖、不加 ROW_VER_NO
		AppLockRow state = appWriteDao.findLockState(appId);
		if (state == null) {
			throw new ApiNotFoundException(AppQueryService.MSG_APP_NOT_FOUND);
		}
		if (!mayExecute(me, state.getApplyUserId())) {
			throw new AccessDeniedException(MSG_EXEC_FORBIDDEN);
		}
		List<FormOptionRow> options = formOptionDao.findActive();
		Set<String> resultCodes = options.stream().filter(o -> GROUP_EXEC_RESULT.equals(o.getGroupCode()))
				.map(FormOptionRow::getOptionCode).collect(Collectors.toSet());
		ExecutionDraft draft = ExecutionValidator.validate(request, resultCodes);

		if (appWriteDao.lockForUpdate(appId, rowVerNo, me.userId()) == 0) {
			if (appWriteDao.findLockState(appId) == null) {
				throw new ApiNotFoundException(AppQueryService.MSG_APP_NOT_FOUND);
			}
			throw new ApiConflictException(AppFlowService.MSG_STALE);
		}

		// 鎖內依現況重判狀態與權限
		AppRow app = appDao.findById(appId)
				.orElseThrow(() -> new ApiNotFoundException(AppQueryService.MSG_APP_NOT_FOUND));
		if (!EXECUTABLE.contains(app.getAppStatusCode())) {
			throw new ApiConflictException(MSG_EXEC_NOT_EXECUTABLE);
		}
		if (!mayExecute(me, app.getApplyUserId())) {
			throw new AccessDeniedException(MSG_EXEC_FORBIDDEN);
		}
		int verNo = app.getCurrVerNo() == null ? 1 : app.getCurrVerNo();

		applyChecklist(appId, verNo, draft.checklist(), expandChecklistIfNeeded(appId, verNo, options, me), me);
		execWriteDao.upsertExec(appId, verNo, draft, draft.submit() ? me.userId() : null, me.userId());
		String toStatus = draft.submit() ? "PENDING_REVIEW" : "IN_EXECUTION";
		if (!toStatus.equals(app.getAppStatusCode())) {
			appWriteDao.updateStatus(appId, toStatus, me.userId());
		}
		if (draft.submit()) {
			mailNotifier.govReviewRequest(appId, resultName(options, draft.resultCode()), me.userId());
		}
		return new SaveResult(rowVerNo + 1, toStatus);
	}

	/** 執行結果代碼 → 選項名稱（信件顯示用）；對不到或名稱空白就用代碼本身 */
	private static String resultName(List<FormOptionRow> options, String resultCode) {
		return options.stream()
				.filter(o -> GROUP_EXEC_RESULT.equals(o.getGroupCode()) && resultCode.equals(o.getOptionCode()))
				.map(FormOptionRow::getOptionName).filter(n -> n != null && !n.isBlank()).findFirst()
				.orElse(resultCode);
	}

	/** 該版次已展開的檢核項（序號 → 已存執行人工號）；還沒展開就依啟用中的 CHECK_LIST 選項（SORT_NO 順序）展開 */
	private Map<Integer, String> expandChecklistIfNeeded(String appId, int verNo, List<FormOptionRow> options,
			AuthUser me) {
		Map<Integer, String> stored = new HashMap<>(execWriteDao.findCheckExecutors(appId, verNo));
		if (stored.isEmpty()) {
			List<FormOptionRow> items = options.stream().filter(o -> GROUP_CHECK_LIST.equals(o.getGroupCode()))
					.toList();
			for (int i = 0; i < items.size(); i++) {
				execWriteDao.insertCheckItem(appId, verNo, i + 1, items.get(i).getFormOptionId(), me.userId());
				stored.put(i + 1, null);
			}
		}
		return stored;
	}

	/**
	 * 先檢查全部本文檢核項（未知序號 400；執行人工號只能是登入者本人或該列已存的原值，否則 400），再逐列更新
	 * （完成沒填時間補伺服器現在時間）
	 */
	private void applyChecklist(String appId, int verNo, List<ExecutionDraft.CheckItem> items,
			Map<Integer, String> stored, AuthUser me) {
		for (ExecutionDraft.CheckItem item : items) {
			if (!stored.containsKey(item.seqNo())) {
				throw new ApiBadRequestException(ExecutionValidator.MSG_BAD_SEQ);
			}
			String userId = item.userId();
			if (userId != null && !userId.equals(me.userId()) && !userId.equals(stored.get(item.seqNo()))) {
				throw new ApiBadRequestException(MSG_BAD_EXECUTOR);
			}
		}
		Timestamp now = Timestamp.valueOf(LocalDateTime.now(TaiwanTime.ZONE));
		for (ExecutionDraft.CheckItem item : items) {
			Timestamp doneAt = !item.done() ? null : item.doneAt() != null ? item.doneAt() : now;
			execWriteDao.updateCheckItem(appId, verNo, item, doneAt, me.userId());
		}
	}

	/**
	 * 執行端退回給申請人：APPROVED／IN_EXECUTION → REJECTED，事件 EXEC_REJECT（意見進 MEMO）；回新的 rowVerNo。
	 * 權限與鎖的做法同 save；已暫存的檢核表與執行結果不清（留在該版次，補件後新版次從空白開始）
	 */
	@Transactional
	public long reject(String appId, ExecRejectRequest request, AuthUser me) {
		long rowVerNo = requireVersion(appId, request == null ? null : request.rowVerNo());

		AppLockRow state = appWriteDao.findLockState(appId);
		if (state == null) {
			throw new ApiNotFoundException(AppQueryService.MSG_APP_NOT_FOUND);
		}
		if (!mayExecute(me, state.getApplyUserId())) {
			throw new AccessDeniedException(MSG_REJECT_FORBIDDEN);
		}
		String memo = requireMemo(request.memo());

		if (appWriteDao.lockForUpdate(appId, rowVerNo, me.userId()) == 0) {
			if (appWriteDao.findLockState(appId) == null) {
				throw new ApiNotFoundException(AppQueryService.MSG_APP_NOT_FOUND);
			}
			throw new ApiConflictException(AppFlowService.MSG_STALE);
		}

		AppRow app = appDao.findById(appId)
				.orElseThrow(() -> new ApiNotFoundException(AppQueryService.MSG_APP_NOT_FOUND));
		if (!EXECUTABLE.contains(app.getAppStatusCode())) {
			throw new ApiConflictException(MSG_REJECT_NOT_EXECUTABLE);
		}
		if (!mayExecute(me, app.getApplyUserId())) {
			throw new AccessDeniedException(MSG_REJECT_FORBIDDEN);
		}
		int verNo = app.getCurrVerNo() == null ? 1 : app.getCurrVerNo();
		appWriteDao.updateStatus(appId, AppFlowService.STATUS_REJECTED, me.userId());
		approvalWriteDao.insertEvent(appId, verNo, EVENT_EXEC_REJECT, me.userId(), memo);
		mailNotifier.rejectedAtVersion(appId, verNo, MailNotifier.STAGE_EXECUTION, me.userName(), memo, me.userId());
		return rowVerNo + 1;
	}

	/**
	 * 治理審查：PENDING_REVIEW → EXECUTED（PASS，事件 GOV_PASS）或 REJECTED（RETURN，事件 GOV_RETURN）；
	 * 回新的 rowVerNo 與審查後狀態。角色不需查 DB，鎖外先擋；鎖是狀態條件式 UPDATE，兩人同時審後到者 409
	 */
	@Transactional
	public SaveResult review(String appId, GovernanceReviewRequest request, AuthUser me) {
		long rowVerNo = requireVersion(appId, request == null ? null : request.rowVerNo());
		if (!me.roles().contains(ROLE_GOVERNANCE)) {
			throw new AccessDeniedException(MSG_REVIEW_FORBIDDEN);
		}
		String decision = request.decision();
		if (!PASS.equals(decision) && !RETURN.equals(decision)) {
			throw new ApiBadRequestException(MSG_BAD_REVIEW_DECISION);
		}
		String memo = RETURN.equals(decision) ? requireMemo(request.memo()) : optionalMemo(request.memo());
		String toStatus = PASS.equals(decision) ? STATUS_EXECUTED : AppFlowService.STATUS_REJECTED;

		int rows = appWriteDao.transition(appId, rowVerNo, STATUS_PENDING_REVIEW, toStatus, me.userId(), false);
		if (rows == 0) {
			AppLockRow state = appWriteDao.findLockState(appId);
			if (state == null) {
				throw new ApiNotFoundException(AppQueryService.MSG_APP_NOT_FOUND);
			}
			if (!STATUS_PENDING_REVIEW.equals(state.getAppStatusCode())) {
				throw new ApiConflictException(MSG_REVIEW_NOT_PENDING);
			}
			throw new ApiConflictException(AppFlowService.MSG_STALE);
		}

		AppRow app = appDao.findById(appId)
				.orElseThrow(() -> new ApiNotFoundException(AppQueryService.MSG_APP_NOT_FOUND));
		int verNo = app.getCurrVerNo() == null ? 1 : app.getCurrVerNo();
		approvalWriteDao.insertEvent(appId, verNo, PASS.equals(decision) ? EVENT_GOV_PASS : EVENT_GOV_RETURN,
				me.userId(), memo);
		if (PASS.equals(decision)) {
			mailNotifier.govPassed(appId, memo, me.userId());
		} else {
			mailNotifier.rejectedAtVersion(appId, verNo, MailNotifier.STAGE_GOVERNANCE, me.userName(), memo,
					me.userId());
		}
		return new SaveResult(rowVerNo + 1, toStatus);
	}

	/** 退回意見：超過 2000 字 400；去頭尾空白後為空 400 */
	private static String requireMemo(String memo) {
		String n = optionalMemo(memo);
		if (n == null) {
			throw new ApiBadRequestException(MSG_REJECT_NEEDS_MEMO);
		}
		return n;
	}

	/** 選填意見：超過 2000 字 400；空白轉 null，其餘去頭尾空白 */
	private static String optionalMemo(String memo) {
		String n = TextLength.check("memo", "意見", memo, TextLength.LIMIT_SHORT);
		return n == null || n.isBlank() ? null : n.strip();
	}

	/** 機房管理員或申請人本人（修舊系統執行不檢查角色，第 44 項） */
	static boolean mayExecute(AuthUser me, String applyUserId) {
		return me.roles().contains(ROLE_IDC_ADMIN) || me.userId().equals(applyUserId);
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
