package com.mpx.infra_manager_java.web;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：業務層「請求內容不合法」例外（S4，例如列表篩選值不在允許清單）。ApiExceptionHandler 轉成 400 並帶出訊息
// ============================================================

public class ApiBadRequestException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ApiBadRequestException(String message) {
		super(message);
	}
}
