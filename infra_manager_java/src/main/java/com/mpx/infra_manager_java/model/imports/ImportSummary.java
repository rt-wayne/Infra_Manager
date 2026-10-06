package com.mpx.infra_manager_java.model.imports;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：使用者匯入結果摘要（S2 回合二匯入器）。
//           inserted／updated 為 IM_USER 筆數，rolesAdded／rolesDisabled 為 IM_USER_ROLE_MAP 異動筆數，
//           skipped 為對照表沒有、整筆略過的登入帳號（匯入結束時列給操作者補對照表）
// ============================================================

import java.util.List;

public record ImportSummary(
		int inserted,
		int updated,
		int rolesAdded,
		int rolesDisabled,
		List<String> skipped) {

	public ImportSummary {
		skipped = List.copyOf(skipped);
	}

	public int written() {
		return inserted + updated;
	}
}
