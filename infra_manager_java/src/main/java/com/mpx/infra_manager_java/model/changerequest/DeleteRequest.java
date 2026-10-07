package com.mpx.infra_manager_java.model.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：刪除申請單的請求本文（S9 R2，施工計畫 ⑨～⑫）。DELETE /api/apps/{id} 帶 JSON 本文：
//           rowVerNo 樂觀鎖（必填）、confirmId 使用者再輸入一次的單號（須與路徑單號相同）、reason 刪除原因（必填、上限 500 字）
// ============================================================

public record DeleteRequest(Long rowVerNo, String confirmId, String reason) {
}
