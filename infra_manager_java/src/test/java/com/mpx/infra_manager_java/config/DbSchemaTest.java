package com.mpx.infra_manager_java.config;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：DbSchema 白名單驗證與前綴組合（S2）
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class DbSchemaTest {

	@Test
	void 合法名稱轉大寫並組出前綴() {
		assertThat(new DbSchema(" rd_user ").table("IM_USER")).isEqualTo("RD_USER.IM_USER");
		assertThat(new DbSchema("a$b#9_").table("IM_ROLE")).isEqualTo("A$B#9_.IM_ROLE");
	}

	@Test
	void 最長128字元() {
		String ok = "A".repeat(128);
		assertThat(new DbSchema(ok).table("T")).startsWith(ok + ".");
		assertThatThrownBy(() -> new DbSchema("A".repeat(129))).isInstanceOf(IllegalStateException.class);
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = { "", "   ", "1abc", "_abc", "rd user", "rd.user", "a;drop table x", "\"RD_USER\"", "rd-user" })
	void 不合法名稱啟動即失敗(String bad) {
		assertThatThrownBy(() -> new DbSchema(bad)).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("db.schema.itflow");
	}
}
