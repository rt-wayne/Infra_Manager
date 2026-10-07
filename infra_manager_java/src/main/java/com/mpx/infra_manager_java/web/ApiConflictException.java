package com.mpx.infra_manager_java.web;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：業務層「狀態衝突」例外（S6 回合二 b-2）。ApiExceptionHandler 轉成 409 並帶出本例外的訊息，
//           用於樂觀鎖版本不符、非草稿不可編輯。前端收到 409 提示重新載入
// ============================================================

public class ApiConflictException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ApiConflictException(String message) {
		super(message);
	}
}
