package com.mpx.infra_manager_java.model.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：列表查詢條件（S4）。由 AppQueryService 正規化後交給 AppDao：
//           status／priority／source 為 null 表示不篩；q 已 trim；from／to 為含頭含尾的建立日期範圍（台灣日期）；
//           page 從 1 起算，每頁固定 PAGE_SIZE 筆（前端規範 20 列／頁）
// ============================================================

import java.time.LocalDate;

public record AppListQuery(String status, String priority, String source, boolean mine, String q, LocalDate from,
		LocalDate to, int page) {

	public static final int PAGE_SIZE = 20;

	/** 完全不篩選（算「待我簽核」總數用） */
	public static AppListQuery none() {
		return new AppListQuery(null, null, null, false, null, null, null, 1);
	}

	public int offset() {
		return (page - 1) * PAGE_SIZE;
	}
}
