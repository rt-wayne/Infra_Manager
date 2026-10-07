package com.mpx.infra_manager_java.model.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-07
// 變更說明: 新增：送審／撤回的請求本文（S7 R1）。rowVerNo 是樂觀鎖版本（必填）；reason 只有撤回用（選填，≤ 2000 字）
// ============================================================

public record FlowActionRequest(Long rowVerNo, String reason) {
}
