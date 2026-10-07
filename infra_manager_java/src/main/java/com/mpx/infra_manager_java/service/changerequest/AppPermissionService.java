package com.mpx.infra_manager_java.service.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：檢視頁動作權限旗標的純運算（S4）。對應舊系統 lib/permissions.js，差異：
//           canDecide 多了「狀態須為 IN_REVIEW」（舊系統漏掉，第 30 項方向 A）；canAiReview、canSubmit 在 S4 一律 false，
//           等 S9／S5 接上功能再開。角色用 AuthUser.roles 的角色代碼（admin、idc_admin、governance⋯）
//           2026-10-07 S7 R1（Claude Fable 5.1）：canSubmit 開啟＝草稿且（申請人或 admin），對應 AppFlowService.submit 的鎖內判斷；
//           AI 閘門等第 17 項拍板，這裡不擋
//           2026-10-07 S9 R2（Claude Opus 5.5）：申請人不可刪的狀態加 REJECTED（施工計畫 ⑧）；AppDeleteService 在鎖內重用本運算
//           2026-10-07 S9 結案（Claude Opus 5.5）：版次大於 1（補件過）申請人也不能刪，只剩 admin（review ① 裁示 B）
// ============================================================

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.mpx.infra_manager_java.model.auth.AuthUser;
import com.mpx.infra_manager_java.model.changerequest.AppPermissions;
import com.mpx.infra_manager_java.model.changerequest.AppRow;
import com.mpx.infra_manager_java.model.changerequest.ApprRow;
import com.mpx.infra_manager_java.model.changerequest.ApprStepRow;
import com.mpx.infra_manager_java.model.changerequest.CandRow;
import com.mpx.infra_manager_java.model.changerequest.ExecRow;

@Service
public class AppPermissionService {

	public static final String DELETE_MODE_ADMIN = "ADMIN";
	public static final String DELETE_MODE_APPLICANT = "APPLICANT_PRE_REVIEW";

	/** 申請人不能刪的狀態；REJECTED 同舊系統不開放（S9 ⑧），明列避免日後出現「沒有關卡簽過的退件」時漏網 */
	private static final Set<String> NOT_DELETABLE_BY_APPLICANT = Set.of("APPROVED", "IN_EXECUTION", "PENDING_REVIEW",
			"EXECUTED", "REJECTED");

	/**
	 * @param appr  目前版次的簽核實例；沒有時為 null
	 * @param steps 簽核關卡（實例的或流程定義展開的）
	 * @param cands 該實例所有關卡的候選人
	 * @param exec  目前版次的實際執行紀錄；沒有時為 null
	 */
	public AppPermissions compute(AppRow app, ApprRow appr, List<ApprStepRow> steps, List<CandRow> cands, ExecRow exec,
			AuthUser me) {
		String status = app.getAppStatusCode();
		boolean applicant = me.userId().equals(app.getApplyUserId());
		boolean admin = hasRole(me, "admin");
		boolean anyDecided = steps.stream().anyMatch(s -> "APPROVED".equals(s.getStepStatusCode())
				|| "REJECTED".equals(s.getStepStatusCode()));
		// 版次大於 1 只會來自補件（補件只能從退件狀態發起），代表舊版次曾有人簽過；申請人不能藉補件繞過退件禁刪
		boolean resubmitted = app.getCurrVerNo() != null && app.getCurrVerNo() > 1;

		boolean canDecide = "IN_REVIEW".equals(status) && appr != null && isCurrentApprover(steps, cands, me.userId());
		boolean canResubmit = "REJECTED".equals(status) && applicant;
		boolean canRecall = "IN_REVIEW".equals(status) && applicant && !anyDecided;
		boolean canExecute = ("APPROVED".equals(status) || "IN_EXECUTION".equals(status))
				&& (exec == null || exec.getResultCode() == null) && (hasRole(me, "idc_admin") || applicant);
		boolean canReview = "PENDING_REVIEW".equals(status) && hasRole(me, "governance");
		String deleteMode = null;
		if (admin) {
			deleteMode = DELETE_MODE_ADMIN;
		} else if (applicant && !anyDecided && !resubmitted && !NOT_DELETABLE_BY_APPLICANT.contains(status)) {
			deleteMode = DELETE_MODE_APPLICANT;
		}
		boolean canEditDraft = applicant && "DRAFT".equals(status);
		boolean canSubmit = "DRAFT".equals(status) && (applicant || admin);

		return new AppPermissions(canDecide, canResubmit, canRecall, canExecute, canReview, deleteMode != null, false,
				canSubmit, canEditDraft, deleteMode);
	}

	/** 目前關卡＝序號最小的 PENDING 關卡；有候選人就看候選人，沒有候選人才看流程定義的指定簽核人 */
	static boolean isCurrentApprover(List<ApprStepRow> steps, List<CandRow> cands, String userId) {
		ApprStepRow current = steps.stream().filter(s -> "PENDING".equals(s.getStepStatusCode()))
				.filter(s -> s.getApprStepId() != null).findFirst().orElse(null);
		if (current == null) {
			return false;
		}
		List<CandRow> stepCands = cands.stream().filter(c -> current.getApprStepId().equals(c.getApprStepId())).toList();
		if (!stepCands.isEmpty()) {
			return stepCands.stream().anyMatch(c -> userId.equals(c.getUserId()));
		}
		return userId.equals(current.getFlowUserId());
	}

	private static boolean hasRole(AuthUser me, String roleId) {
		return me.roles() != null && me.roles().contains(roleId);
	}
}
