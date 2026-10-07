package com.mpx.infra_manager_java.model.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：檢查與正規化後的草稿內容（S6 回合二 b-1），由 AppDraftValidator 產生、AppWriteDao 寫入。
//           字串已去頭尾空白（長文欄位只統一換行）、空白轉 null；子表清單已去掉空白列、依序編號
// ============================================================

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;

public record AppDraft(String title, String prioCode, String applyDeptName, String applyTel, String applyEmail,
		boolean selfExec, boolean supplierExec, String workModeCode, String remoteMethod, String supName,
		String supContact, String supTel, Integer supHeadCount, String workSubject, String impactDesc,
		String workDetail, String riskDesc, String rollbackPlan, String otherReason, Timestamp schedStart,
		Timestamp schedEnd, BigDecimal estHours, String omitReason, List<Long> categoryItemIds,
		List<CategoryOther> categoryOthers, List<Long> reasonIds, List<Long> scopeIds, List<Equipment> equipments,
		List<String> planSteps) {

	public record CategoryOther(Long formOptionId, String text) {
	}

	public record Equipment(String name, String assetNo, String modelNo, String serialNo, String purpose,
			String mgmtIp) {
	}
}
