package com.mpx.infra_manager_java.model.template;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：範本的表單內容（S5 R1），也是 IM_TMPL.FORM_JSON 存的格式。欄位名與 AppDraftRequest 相同，
//           前端套用範本時可直接帶進新增頁；不收申請人聯絡資料與預定開始／結束時間（同舊系統範本只存預估工時），
//           schedule 只有 estHours。選項同草稿只存 formOptionId
// ============================================================

import java.math.BigDecimal;
import java.util.List;

import com.mpx.infra_manager_java.model.changerequest.AppDraftRequest;

public record TemplateForm(String title, String prioCode, Boolean selfExec, Boolean supplierExec, String workModeCode,
		String remoteMethod, AppDraftRequest.Supplier supplier, String workSubject, String impactDesc,
		String workDetail, String riskDesc, String rollbackPlan, List<Long> categoryItemIds,
		List<AppDraftRequest.CategoryOther> categoryOthers, List<Long> reasonIds, String otherReason,
		List<Long> scopeIds, List<AppDraftRequest.Equipment> equipments, List<String> planSteps, Schedule schedule,
		AppDraftRequest.Location location) {

	public record Schedule(BigDecimal estHours) {
	}
}
