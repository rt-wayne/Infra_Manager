package com.mpx.common.db;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-04
// 變更說明: 新增：依 dbName 建立並快取 NamedParameterJdbcTemplate（規格 D-03、§6.3、§6.4）
//           ConcurrentHashMap.computeIfAbsent：同名並發只打一次 API、只建一次池
//           建立失敗丟 DbConnectException，不寫入快取，下次呼叫自動重試
//           池已建但後續步驟失敗時先 close 再丟例外
//           initializationFailTimeout 用 Hikari 預設（建池當下實際連一次，連不上即失敗，規格 D-09）
//           createDataSource 為 protected，單元測試以子類別覆寫避免真連 Oracle（規格 TC-B06/B07）
//           destroy 逐一 close，單一池失敗不中斷其他池
//           審查修正：summarize 改由例外鏈最外層往下找第一個 errorCode > 0 的 SQLException 取 ORA 碼；
//           建 NamedParameterJdbcTemplate 抽成 protected createJdbcTemplate，供測試覆蓋「池建好後失敗先關池」
//           v3.4：支援 ORACLE 與 MSSQL——dbType → driverClassName 對應集中在 DRIVERS；
//                 Hikari 設定抽成 protected buildHikariConfig，測試可在不真連的情況下檢查 driverClassName
//                 summarize 錯誤碼前綴依 dbType：ORACLE 顯示 ORA-xxxxx，其他（MSSQL 等）顯示 code=xxxx
// ============================================================

import java.io.Closeable;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

@Component
public class DbConnectionManager implements DisposableBean {

	Logger devTemplateDb = LoggerFactory.getLogger("devTemplate.db");

	/** 支援的 dbType（大寫）→ JDBC driver；檢核（DbConnectApiClient）與建池都以此為準 */
	static final Map<String, String> DRIVERS;
	static {
		Map<String, String> m = new LinkedHashMap<>();
		m.put("ORACLE", "oracle.jdbc.OracleDriver");
		m.put("MSSQL", "com.microsoft.sqlserver.jdbc.SQLServerDriver");
		DRIVERS = Collections.unmodifiableMap(m);
	}

	/** dbType（忽略大小寫、前後空白）對應的 driverClassName；不支援或 null 回 null */
	static String driverClassName(String dbType) {
		return dbType == null ? null : DRIVERS.get(dbType.trim().toUpperCase(Locale.ROOT));
	}

	/** 支援清單文字，例：ORACLE、MSSQL */
	static String supportedDbTypes() {
		return String.join("、", DRIVERS.keySet());
	}

	static final int DEFAULT_MAX_POOL_SIZE = 10;
	static final int DEFAULT_QUERY_TIMEOUT_SECONDS = 60;

	private final DbConnectApiClient apiClient;
	private final Map<String, NamedParameterJdbcTemplate> cache = new ConcurrentHashMap<>();

	public DbConnectionManager(DbConnectApiClient apiClient) {
		this.apiClient = apiClient;
	}

	/**
	 * 取得 dbName 對應的 NamedParameterJdbcTemplate；dbName 須已由呼叫端 trim 並檢查非空。
	 */
	public NamedParameterJdbcTemplate getJdbcTemplate(String dbName) {
		return cache.computeIfAbsent(dbName, this::create);
	}

	private NamedParameterJdbcTemplate create(String dbName) {
		DbConnectionInfo info = apiClient.fetch(dbName);
		int maxPoolSize = positiveOrDefault(info.getMaxPoolSize(), DEFAULT_MAX_POOL_SIZE);
		int queryTimeout = positiveOrDefault(info.getQueryTimeoutSeconds(), DEFAULT_QUERY_TIMEOUT_SECONDS);

		DataSource dataSource;
		try {
			dataSource = createDataSource(dbName, info, maxPoolSize);
		} catch (DbConnectException e) {
			throw e;
		} catch (RuntimeException e) {
			String summary = summarize(e, info.getDbType());
			devTemplateDb.info("建池失敗 alias={} dbType={} 原因={}", dbName, info.getDbType(), summary);
			throw new DbConnectException(dbName, "建立連線池失敗（" + summary + "）", e);
		}

		try {
			NamedParameterJdbcTemplate template = createJdbcTemplate(dataSource, queryTimeout);
			devTemplateDb.info("建池成功 alias={} dbType={} maxPoolSize={} queryTimeout={}s",
					dbName, info.getDbType(), maxPoolSize, queryTimeout);
			return template;
		} catch (RuntimeException e) {
			closeQuietly(dbName, dataSource);
			devTemplateDb.info("建池失敗 alias={} dbType={} 原因={}", dbName, info.getDbType(), summarize(e, info.getDbType()));
			throw new DbConnectException(dbName, "建立 NamedParameterJdbcTemplate 失敗（" + summarize(e, info.getDbType()) + "）", e);
		}
	}

	/**
	 * 依連線資訊建立連線池；建立當下即實際連線一次，連不上丟例外（Hikari 預設 initializationFailTimeout）。
	 * 單元測試以子類別覆寫此方法，避免真連 DB。
	 */
	protected DataSource createDataSource(String dbName, DbConnectionInfo info, int maxPoolSize) {
		return new HikariDataSource(buildHikariConfig(dbName, info, maxPoolSize));
	}

	/** 連線池設定；driverClassName 依 dbType（DRIVERS），其餘參數各 dbType 相同（規格 §6.3） */
	protected HikariConfig buildHikariConfig(String dbName, DbConnectionInfo info, int maxPoolSize) {
		String driver = driverClassName(info.getDbType());
		if (driver == null) {
			throw new DbConnectException(dbName, "dbType 不支援：" + info.getDbType() + "（支援：" + supportedDbTypes() + "）");
		}
		HikariConfig config = new HikariConfig();
		config.setDriverClassName(driver);
		config.setJdbcUrl(info.getJdbcUrl());
		config.setUsername(info.getUsername());
		config.setPassword(info.getPassword());
		config.setMaximumPoolSize(maxPoolSize);
		config.setPoolName("db-" + dbName);
		config.setMaxLifetime(1800000L);
		config.setIdleTimeout(600000L);
		config.setConnectionTimeout(10000L);
		config.setMinimumIdle(1);
		return config;
	}

	/** 以已建好的池建立 NamedParameterJdbcTemplate 並設查詢逾時；單元測試可覆寫以模擬此步驟失敗 */
	protected NamedParameterJdbcTemplate createJdbcTemplate(DataSource dataSource, int queryTimeout) {
		NamedParameterJdbcTemplate template = new NamedParameterJdbcTemplate(dataSource);
		template.getJdbcTemplate().setQueryTimeout(queryTimeout);
		return template;
	}

	@Override
	public void destroy() {
		for (Map.Entry<String, NamedParameterJdbcTemplate> entry : cache.entrySet()) {
			DataSource dataSource = entry.getValue().getJdbcTemplate().getDataSource();
			closeQuietly(entry.getKey(), dataSource);
		}
		cache.clear();
	}

	private void closeQuietly(String dbName, DataSource dataSource) {
		if (!(dataSource instanceof Closeable)) {
			return;
		}
		try {
			((Closeable) dataSource).close();
			devTemplateDb.info("池關閉 alias={}", dbName);
		} catch (Exception e) {
			devTemplateDb.info("池關閉失敗 alias={} 原因={}", dbName, summarize(e));
		}
	}

	private static int positiveOrDefault(Integer value, int defaultValue) {
		return (value == null || value <= 0) ? defaultValue : value;
	}

	/**
	 * 原因摘要：只取例外類別與 Oracle 錯誤碼，不帶例外訊息（可能含主機資訊）。
	 * 從最外層往下找第一個 errorCode > 0 的 SQLException 取 ORA 碼
	 * （ojdbc8 的 SQLException 底下常掛 OracleDatabaseException，非 SQLException，只看最底層會拿不到）；
	 * 整條鏈都沒有才只記類別名。
	 */
	static String summarize(Throwable e) {
		return summarize(e, null);
	}

	/**
	 * 同上；錯誤碼前綴依 dbType：ORACLE → ORA-xxxxx（5 碼補零），其他或未知 → code=xxxx（通用形式）
	 */
	static String summarize(Throwable e, String dbType) {
		Throwable root = e;
		SQLException oraError = null;
		int depth = 0;
		for (Throwable t = e; t != null && depth < 32; t = t.getCause(), depth++) {
			root = t;
			if (oraError == null && t instanceof SQLException && ((SQLException) t).getErrorCode() > 0) {
				oraError = (SQLException) t;
			}
			if (t.getCause() == t) {
				break;
			}
		}
		if (oraError != null) {
			boolean oracle = dbType != null && "ORACLE".equalsIgnoreCase(dbType.trim());
			String code = oracle ? "ORA-" + String.format("%05d", oraError.getErrorCode()) : "code=" + oraError.getErrorCode();
			String ora = oraError.getClass().getSimpleName() + " " + code;
			return oraError == e ? ora : e.getClass().getSimpleName() + " / " + ora;
		}
		return root == e ? e.getClass().getSimpleName()
				: e.getClass().getSimpleName() + " / " + root.getClass().getSimpleName();
	}
}
