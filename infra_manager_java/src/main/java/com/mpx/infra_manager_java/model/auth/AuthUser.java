package com.mpx.infra_manager_java.model.auth;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：登入後放在 session（Spring Security principal）的使用者（S2）
//           只放授權需要的欄位，不放密碼雜湊；Serializable 是為了日後接 Spring Session JDBC 時可直接序列化。
//           2026-10-06 code review：覆寫 toString 只印工號——Spring Security 在 DEBUG 等級會把 Authentication（含 principal）
//           寫進 log，record 預設的 toString 會帶出帳號與姓名。
// ============================================================

import java.io.Serializable;
import java.util.List;

public record AuthUser(String userId, String loginId, String userName, List<String> roles, boolean mustChangePassword)
		implements Serializable {

	public AuthUser {
		roles = roles == null ? List.of() : List.copyOf(roles);
	}

	@Override
	public String toString() {
		return "AuthUser[userId=" + userId + "]";
	}
}
