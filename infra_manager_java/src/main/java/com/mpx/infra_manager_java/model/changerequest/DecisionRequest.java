package com.mpx.infra_manager_java.model.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-07
// 變更說明: 新增：簽核的請求本文（S7 R2）。rowVerNo 樂觀鎖版本（必填）；decision 為 APPROVE／REJECT；
//           memo 簽核意見（≤ 2000 字；退件必填，同意空白自動填「同意」）
// ============================================================

public record DecisionRequest(Long rowVerNo, String decision, String memo) {
}
