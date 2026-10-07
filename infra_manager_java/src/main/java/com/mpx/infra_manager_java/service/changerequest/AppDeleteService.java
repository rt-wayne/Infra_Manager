package com.mpx.infra_manager_java.service.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：刪除申請單（S9 R2，施工計畫 ⑧～⑫）。單一交易：
//           鎖外驗單號格式（不符 404）、rowVerNo 必填、confirmId 須等於單號（400「確認編號不符，已取消刪除」）、
//           原因 trim 後必填且上限 500 字 →
//           AppWriteDao.lockForUpdate 只比版本取鎖（0 列：單不存在或已刪 404、其餘 409 版本過期）→
//           鎖內依現況用 AppPermissionService 重算 deleteMode（admin 任何狀態 ADMIN；申請人在沒有任何關卡簽過、且狀態不是
//           APPROVED／IN_EXECUTION／PENDING_REVIEW／EXECUTED／REJECTED 時 APPLICANT_PRE_REVIEW；都不是 403）→
//           主檔 STATUS 0 並寫 DELETE_* 四欄 → 進行中的簽核實例與未結關卡改 CANCELLED → 事件 DELETE（原因進 MEMO）。
//           附件實體檔保留、申請單流水號不回收（施工計畫 ⑪）
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
import com.mpx.infra_manager_java.model.auth.AuthUser;
import com.mpx.infra_manager_java.model.changerequest.AppPermissions;
import com.mpx.infra_manager_java.model.changerequest.AppRow;
import com.mpx.infra_manager_java.model.changerequest.ApprRow;
import com.mpx.infra_manager_java.model.changerequest.ApprStepRow;
import com.mpx.infra_manager_java.model.changerequest.CandRow;
import com.mpx.infra_manager_java.model.changerequest.DeleteRequest;
import com.mpx.infra_manager_java.model.changerequest.ExecRow;
import com.mpx.infra_manager_java.util.TextLength;
import com.mpx.infra_manager_java.web.ApiBadRequestException;
import com.mpx.infra_manager_java.web.ApiConflictException;
import com.mpx.infra_manager_java.web.ApiNotFoundException;

@Service
public class AppDeleteService {

	public static final String EVENT_DELETE = "DELETE";
	public static final String MSG_CONFIRM_MISMATCH = "確認編號不符，已取消刪除";
	public static final String MSG_REASON_REQUIRED = "請填寫刪除原因";
	public static final String MSG_DELETE_FORBIDDEN = "您沒有權限刪除這張申請單";
	/** DELETE_REASON 是 VARCHAR2(500 CHAR) */
	static final int REASON_MAX = 500;

	private static final Pattern APP_ID = Pattern.compile("^[A-Z0-9-]{1,20}$");

	private final AppDao appDao;
	private final AppWriteDao appWriteDao;
	private final ApprovalDao approvalDao;
	private final ApprovalWriteDao approvalWriteDao;
	private final AppPermissionService permissionService;

	public AppDeleteService(AppDao appDao, AppWriteDao appWriteDao, ApprovalDao approvalDao,
			ApprovalWriteDao approvalWriteDao, AppPermissionService permissionService) {
		this.appDao = appDao;
		this.appWriteDao = appWriteDao;
		this.approvalDao = approvalDao;
		this.approvalWriteDao = approvalWriteDao;
		this.permissionService = permissionService;
	}

	@Transactional
	public void delete(String appId, DeleteRequest request, AuthUser me) {
		if (appId == null || !APP_ID.matcher(appId).matches()) {
			throw new ApiNotFoundException(AppQueryService.MSG_APP_NOT_FOUND);
		}
		if (request == null || request.rowVerNo() == null) {
			throw new ApiBadRequestException(AppDraftService.MSG_NO_VERSION);
		}
		String confirmId = request.confirmId() == null ? null : request.confirmId().trim();
		if (!appId.equals(confirmId)) {
			throw new ApiBadRequestException(MSG_CONFIRM_MISMATCH);
		}
		String reason = request.reason() == null ? null : request.reason().trim();
		if (reason == null || reason.isEmpty()) {
			throw new ApiBadRequestException(MSG_REASON_REQUIRED);
		}
		reason = TextLength.check("reason", "刪除原因", reason, REASON_MAX);

		if (appWriteDao.lockForUpdate(appId, request.rowVerNo(), me.userId()) == 0) {
			if (appWriteDao.findLockState(appId) == null) {
				throw new ApiNotFoundException(AppQueryService.MSG_APP_NOT_FOUND);
			}
			throw new ApiConflictException(AppFlowService.MSG_STALE);
		}

		// 鎖內依現況重算權限（與檢視頁 permissions 同一套運算，不信任前端）
		AppRow app = appDao.findById(appId)
				.orElseThrow(() -> new ApiNotFoundException(AppQueryService.MSG_APP_NOT_FOUND));
		int verNo = app.getCurrVerNo() == null ? 1 : app.getCurrVerNo();
		ApprRow appr = approvalDao.findCurrent(appId, verNo).orElse(null);
		List<ApprStepRow> steps = appr != null ? approvalDao.findSteps(appr.getApprId()) : List.of();
		List<CandRow> cands = appr != null ? approvalDao.findCandidates(appr.getApprId()) : List.of();
		ExecRow exec = appDao.findExec(appId, verNo).orElse(null);
		AppPermissions permissions = permissionService.compute(app, appr, steps, cands, exec, me);
		String mode = permissions.deleteMode();
		if (mode == null) {
			throw new AccessDeniedException(MSG_DELETE_FORBIDDEN);
		}

		appWriteDao.deleteApp(appId, me.userId(), reason, mode);
		ApprRow pending = approvalWriteDao.findPendingAppr(appId, verNo);
		if (pending != null) {
			approvalWriteDao.closeOpenSteps(pending.getApprId(), "CANCELLED", me.userId());
			approvalWriteDao.closeAppr(pending.getApprId(), "CANCELLED", me.userId());
		}
		approvalWriteDao.insertEvent(appId, verNo, EVENT_DELETE, me.userId(), reason);
	}
}
