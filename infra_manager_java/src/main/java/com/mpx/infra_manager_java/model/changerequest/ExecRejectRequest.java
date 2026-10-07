package com.mpx.infra_manager_java.model.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：執行端退回的請求本文（S10 R2，施工計畫 ⑧）。rowVerNo 樂觀鎖版本（必填）；
//           memo 退回意見（必填，去頭尾空白後不可空，≤ 2000 字）
// ============================================================

public record ExecRejectRequest(Long rowVerNo, String memo) {
}
