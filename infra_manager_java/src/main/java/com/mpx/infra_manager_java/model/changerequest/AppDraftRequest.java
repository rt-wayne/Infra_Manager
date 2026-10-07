package com.mpx.infra_manager_java.model.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：建草稿／編輯草稿的請求本文（S6 回合二 b-1）。欄位名與 GET /api/apps/{id} 的 AppDetail 對齊，
//           前端編輯頁可直接把檢視回應轉成本格式送回。申請人工號、申請日、狀態、流程由伺服器決定，不收；
//           選項只收 formOptionId：categoryItemIds 為 CATG_ITEM、categoryOthers 為 CATG 的「其他」補充、
//           reasonIds 為 REASON、scopeIds 為 SCOPE。位置在 S13 前只收「不適用」原因（裁示 ⑦A）。
//           rowVerNo 只有 PUT 用（樂觀鎖，回合二 b-2）
// ============================================================

import java.math.BigDecimal;
import java.util.List;

public record AppDraftRequest(String title, String prioCode, Applicant applicant, Boolean selfExec,
		Boolean supplierExec, String workModeCode, String remoteMethod, Supplier supplier, String workSubject,
		String impactDesc, String workDetail, String riskDesc, String rollbackPlan, List<Long> categoryItemIds,
		List<CategoryOther> categoryOthers, List<Long> reasonIds, String otherReason, List<Long> scopeIds,
		List<Equipment> equipments, List<String> planSteps, Schedule schedule, Location location, Long rowVerNo) {

	public record Applicant(String deptName, String tel, String email) {
	}

	public record Supplier(String name, String contact, String tel, Integer headCount) {
	}

	public record CategoryOther(Long formOptionId, String text) {
	}

	public record Equipment(String name, String assetNo, String modelNo, String serialNo, String purpose,
			String mgmtIp) {
	}

	/** start／end 接受 yyyy-MM-dd HH:mm 或 yyyy-MM-ddTHH:mm（datetime-local 的格式） */
	public record Schedule(String start, String end, BigDecimal estHours) {
	}

	public record Location(String omitReason) {
	}
}
