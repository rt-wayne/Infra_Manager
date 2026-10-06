package com.mpx.infra_manager_java.model.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：列表回應（S4）。total 是套用篩選後的總筆數；mineCount 是不套篩選的「待我簽核」總數（與舊系統一致）
// ============================================================

import java.util.List;

public record AppListResponse(List<AppListItem> items, long total, int page, int size, long mineCount) {
}
