package com.mpx.infra_manager_java.model.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-07
// 變更說明: 新增：補件時寫進 IM_APP_VER.FORM_JSON 的舊版表單快照（S9 R1，施工計畫 ③）。
//           snapshotSchema 固定 1，日後欄位有變就加版號，讀取端依版號解讀（第 69 項歷史匯入也要寫同一格式）。
//           只放「表單內容」：標題、優先等級（代碼＋名稱）、申請人、執行方式、廠商、五段文字、類別／原因／範圍、
//           設備、步驟、排程、位置、流程、該版的補件說明、附件索引（只列 OWNER_TYPE=APP 且在該版結束前上傳的檔）。
//           不放狀態、權限、簽核、事件、版次、rowVerNo、檢核表、執行紀錄——那些各有自己的表可查。
//           巢狀型別重用 AppDetail 的 record，前端日後檢視舊版可用同一套顯示元件
// ============================================================

import java.util.List;

public record AppVersionSnapshot(int snapshotSchema, String appId, Integer verNo, String title, String prioCode,
		String prioName, AppDetail.Applicant applicant, String applyDate, boolean selfExec, boolean supplierExec,
		String workModeCode, String remoteMethod, AppDetail.Supplier supplier, String workSubject, String impactDesc,
		String workDetail, String riskDesc, String rollbackPlan, List<AppDetail.Option> categories,
		List<AppDetail.Option> reasons, String otherReason, List<AppDetail.Option> scopes,
		List<AppDetail.Equipment> equipments, List<AppDetail.PlanStep> planSteps, AppDetail.Schedule schedule,
		AppDetail.Location location, String flowId, String flowName, String resubmitMemo,
		List<AppDetail.Attachment> attachments) {

	public static final int SCHEMA = 1;
}
