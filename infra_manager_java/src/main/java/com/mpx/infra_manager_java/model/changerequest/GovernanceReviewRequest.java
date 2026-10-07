package com.mpx.infra_manager_java.model.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：治理審查的請求本文（S10 R2，施工計畫 ⑨）。rowVerNo 樂觀鎖版本（必填）；decision 為 PASS／RETURN；
//           memo 審查意見（≤ 2000 字；退回必填，通過選填）
// ============================================================

public record GovernanceReviewRequest(Long rowVerNo, String decision, String memo) {
}
