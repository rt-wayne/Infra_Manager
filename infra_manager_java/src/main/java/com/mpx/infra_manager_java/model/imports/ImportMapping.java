package com.mpx.infra_manager_java.model.imports;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：帳號工號對照（S2 回合二匯入器；PRD「資料遷移」的帳號工號對照表）。
//           key 為小寫登入帳號、value 為工號；查詢時呼叫端先把帳號轉小寫
// ============================================================

import java.util.Map;
import java.util.Optional;

public record ImportMapping(Map<String, String> userIdByLoginId) {

	public ImportMapping {
		userIdByLoginId = Map.copyOf(userIdByLoginId);
	}

	/** 以小寫帳號查工號；對照表沒有時回 empty */
	public Optional<String> userIdOf(String loginId) {
		return Optional.ofNullable(userIdByLoginId.get(loginId));
	}

	public int size() {
		return userIdByLoginId.size();
	}
}
