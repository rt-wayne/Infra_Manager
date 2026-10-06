package com.mpx.infra_manager_java.web;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：業務層「找不到資源」例外（S4）。ApiExceptionHandler 轉成 404 並帶出本例外的訊息；
//           不用 ResponseStatusException，因為它實作 ErrorResponse，會被統一改成「請求無法處理」
// ============================================================

public class ApiNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ApiNotFoundException(String message) {
		super(message);
	}
}
