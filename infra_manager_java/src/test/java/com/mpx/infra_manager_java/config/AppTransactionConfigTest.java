package com.mpx.infra_manager_java.config;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：LazyAliasTransactionManager 單元測試（code review 第 3 項）：
//           afterPropertiesSet 不碰 DbConnectionManager（延後取池）、obtainDataSource 回傳 template 的 DataSource 並快取、
//           池為 null 時丟 IllegalStateException、alias 空白時建構即失敗
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.mpx.common.db.DbConnectionManager;
import com.mpx.infra_manager_java.config.AppTransactionConfig.LazyAliasTransactionManager;

class AppTransactionConfigTest {

	private final DbConnectionManager manager = mock(DbConnectionManager.class);

	@Test
	void afterPropertiesSet不向連線管理器取池() {
		LazyAliasTransactionManager tm = new LazyAliasTransactionManager(manager, "itflow");

		tm.afterPropertiesSet();

		verifyNoInteractions(manager);
		assertThat(tm.getDataSource()).isNull();
	}

	@Test
	void obtainDataSource回傳template的DataSource並快取() {
		DataSource ds = mock(DataSource.class);
		when(manager.getJdbcTemplate("itflow")).thenReturn(new NamedParameterJdbcTemplate(new JdbcTemplate(ds)));
		LazyAliasTransactionManager tm = new LazyAliasTransactionManager(manager, " itflow ");

		assertThat(tm.obtainDataSource()).isSameAs(ds);
		assertThat(tm.obtainDataSource()).isSameAs(ds);

		verify(manager, times(1)).getJdbcTemplate("itflow");
		assertThat(tm.getDataSource()).isSameAs(ds);
	}

	@Test
	void 池為null時丟IllegalStateException() {
		when(manager.getJdbcTemplate("itflow")).thenReturn(new NamedParameterJdbcTemplate(new JdbcTemplate()));
		LazyAliasTransactionManager tm = new LazyAliasTransactionManager(manager, "itflow");

		assertThatThrownBy(tm::obtainDataSource).isInstanceOf(IllegalStateException.class);
		assertThat(tm.getDataSource()).isNull();
	}

	@Test
	void alias空白時建構即失敗() {
		assertThatThrownBy(() -> new LazyAliasTransactionManager(manager, " "))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("db.connect.itflow");
		assertThatThrownBy(() -> new LazyAliasTransactionManager(manager, null))
				.isInstanceOf(IllegalStateException.class);
	}
}
