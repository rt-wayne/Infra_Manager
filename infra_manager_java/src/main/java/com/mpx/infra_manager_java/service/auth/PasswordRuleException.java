package com.mpx.infra_manager_java.service.auth;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：改密碼不符規則（S2 回合二）。訊息是給使用者看的固定句子，由 AuthController 以 400 回出；
//           reason 是 log 用的英文代碼（too_short／too_long／equals_default／same_as_old／bad_old_password／account_unavailable）。
// ============================================================

public class PasswordRuleException extends RuntimeException {

	private final String reason;

	public PasswordRuleException(String reason, String message) {
		super(message);
		this.reason = reason;
	}

	public String getReason() {
		return reason;
	}
}
