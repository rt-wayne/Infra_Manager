package com.mpx.infra_manager_java.model.imports;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：舊系統 users.json 外層結構 { "users": [ ... ] }（S2 回合二匯入器）
// ============================================================

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LegacyUserFile(List<LegacyUser> users) {
}
