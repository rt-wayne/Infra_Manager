package com.mpx.infra_manager_java.config;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-05
// 變更說明: 新增：業務層交易管理（S1，裁示 ①A）
//           範本 common.db 的 DbClient.update 為單句 autocommit、不提供 TransactionManager；
//           本系統一張申請單要同時寫多張表，必須有交易，因此在業務 package 自建 DataSourceTransactionManager，
//           底層池直接沿用 DbConnectionManager.getJdbcTemplate(alias) 建好的那一個（不另建池、不改 com.mpx.common）。
//           池在第一次交易時才取（延後解析），維持範本「API 位址未設仍可啟動」的行為。
//           TxManager 綁定的 DataSource 與 DbClient 內部 NamedParameterJdbcTemplate 用的是同一個物件，
//           @Transactional 範圍內的 DbClient 呼叫才會共用同一條連線並一起 commit / rollback。
//           2026-10-06 code review：第一次取得池後以 setDataSource 快取（父類別 doCleanupAfterCompletion 直接讀欄位，
//           不經 obtainDataSource；不快取就只能靠 Spring 內部的 null 分支歸還連線）；alias 空白時啟動即失敗。
// ============================================================

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import com.mpx.common.db.DbConnectionManager;

@Configuration
public class AppTransactionConfig {

	@Bean
	public PlatformTransactionManager transactionManager(DbConnectionManager connectionManager,
			@Value("${db.connect.itflow}") String itflowDb) {
		return new LazyAliasTransactionManager(connectionManager, itflowDb);
	}

	/** 以 alias 延後取池的交易管理器；池由 DbConnectionManager 建立並快取 */
	static class LazyAliasTransactionManager extends DataSourceTransactionManager {

		private final transient DbConnectionManager connectionManager;
		private final String alias;

		LazyAliasTransactionManager(DbConnectionManager connectionManager, String alias) {
			if (connectionManager == null) {
				throw new IllegalArgumentException("connectionManager 不得為 null");
			}
			if (alias == null || alias.isBlank()) {
				throw new IllegalStateException("db.connect.itflow 未設定，交易管理器無法建立");
			}
			this.connectionManager = connectionManager;
			this.alias = alias.trim();
		}

		/** 第一次呼叫時向 DbConnectionManager 取池並快取；之後直接回傳快取（池建好後 DbConnectionManager 不會換物件） */
		@Override
		protected DataSource obtainDataSource() {
			DataSource cached = getDataSource();
			if (cached != null) {
				return cached;
			}
			DataSource dataSource = connectionManager.getJdbcTemplate(alias).getJdbcTemplate().getDataSource();
			if (dataSource == null) {
				throw new IllegalStateException("alias " + alias + " 的連線池尚未建立");
			}
			setDataSource(dataSource);
			return dataSource;
		}

		/** 父類別在此檢查 dataSource 非 null；本類別延後到第一次交易才取池，故略過 */
		@Override
		public void afterPropertiesSet() {
			// 刻意不檢查
		}
	}
}
