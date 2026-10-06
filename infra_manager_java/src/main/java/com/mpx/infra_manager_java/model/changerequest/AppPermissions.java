package com.mpx.infra_manager_java.model.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：檢視頁的 9 個動作權限旗標（S4，PRD「GET /api/apps/{id} 含 9 個 canX」）。
//           由 AppPermissionService 在伺服器端算好，前端只負責顯示；deleteMode 是 canDelete 為 true 時的刪除模式
//           （ADMIN／APPLICANT_PRE_REVIEW，對應 IM_APP.DELETE_MODE_CODE）
// ============================================================

public record AppPermissions(boolean canDecide, boolean canResubmit, boolean canRecall, boolean canExecute,
		boolean canReview, boolean canDelete, boolean canAiReview, boolean canSubmit, boolean canEditDraft,
		String deleteMode) {
}
