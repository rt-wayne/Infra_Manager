package com.mpx.infra_manager_java.model.imports;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：舊系統 users.json 單一帳號（S2 回合二匯入器）。
//           只取會進 IM_USER／IM_USER_ROLE_MAP 的欄位；passwordHash（scrypt）、rememberTokens 等一律忽略（裁示 ①A 修訂）
// ============================================================

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LegacyUser(
		String id,
		String name,
		String email,
		String title,
		String department,
		String phone,
		List<String> roles,
		Boolean active) {
}
