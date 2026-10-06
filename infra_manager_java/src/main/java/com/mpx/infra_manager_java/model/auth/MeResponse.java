package com.mpx.infra_manager_java.model.auth;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：GET /api/auth/me 與登入成功的回應（S2）。未登入時只有 loggedIn:false，其餘欄位不輸出
// ============================================================

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MeResponse(boolean loggedIn, String userId, String loginId, String userName, List<String> roles,
		Boolean mustChangePassword) {

	public static MeResponse anonymous() {
		return new MeResponse(false, null, null, null, null, null);
	}

	public static MeResponse of(AuthUser user) {
		return new MeResponse(true, user.userId(), user.loginId(), user.userName(), user.roles(),
				user.mustChangePassword());
	}
}
