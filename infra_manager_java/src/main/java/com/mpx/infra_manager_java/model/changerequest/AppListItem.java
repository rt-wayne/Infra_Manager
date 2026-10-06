package com.mpx.infra_manager_java.model.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：列表一列的回應（S4）。狀態、來源回代碼由前端對應中文；優先等級名稱與顏色由 IM_FORM_OPTION 帶出；
//           currentStep 是目前待簽關卡名稱（非簽核中為 null），currentApprover 是候選人姓名（一位）或「N 人待簽」
// ============================================================

public record AppListItem(String appId, String title, String prioCode, String prioName, String prioColor,
		String workSubject, String applyDeptName, String applicantName, Integer verNo, String statusCode,
		String sourceCode, String currentStep, String currentApprover, boolean mine, String createdAt) {
}
