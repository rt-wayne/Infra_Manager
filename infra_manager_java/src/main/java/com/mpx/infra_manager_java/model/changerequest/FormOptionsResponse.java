package com.mpx.infra_manager_java.model.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-06
// 變更說明: 新增：GET /api/form-options 回應（S6 回合二 a）。表單選項只含啟用中（STATUS=1）；
//           upload 為當下系統參數的上傳限制，前端據以提示，實際檢核仍在後端
// ============================================================

import java.util.List;

public record FormOptionsResponse(List<FormOption> options, UploadLimits upload) {

	public record FormOption(Long formOptionId, String groupCode, String code, String name, Long upFormOptionId,
			String colorCode, String desc, String timeLimitDesc, String prioFlowDesc, String sampleDesc,
			String flowId, Integer sortNo) {
	}

	public record UploadLimits(int maxMb, int maxFiles) {
	}
}
