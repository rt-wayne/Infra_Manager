package com.mpx.infra_manager_java.model.auth;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：POST /api/auth/login 請求本文（S2）。長度上限對齊 IM_USER.LOGIN_ID VARCHAR2(64)；
//           密碼上限 128 只是擋住明顯不合理的本文（bcrypt 本身只看前 72 bytes）
// ============================================================

public record LoginRequest(String loginId, String password) {

	public static final int LOGIN_ID_MAX = 64;
	public static final int PASSWORD_MAX = 128;

	/** 兩個欄位都要有值且不超過上限；不合法由 controller 回 400 */
	public boolean isValid() {
		return loginId != null && !loginId.isBlank() && loginId.length() <= LOGIN_ID_MAX
				&& password != null && !password.isEmpty() && password.length() <= PASSWORD_MAX;
	}
}
