package com.mpx.common.db;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-02
// 變更說明: 新增：DB 存取對外唯一入口（規格 §6.1）
//           只做 dbName 檢查（trim、區分大小寫、null/空白直接丟例外不打 API，規格 D-04）與轉呼
//           SQL 一律以 :name 具名參數綁定，本類別不做任何字串拼接（規格 S-04）
//           update 為單句 autocommit，不支援交易
//           SQL 執行期錯誤原樣拋 Spring DataAccessException（規格 §13-3）
// ============================================================

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.stereotype.Component;

@Component
public class DbClient {

	private final DbConnectionManager connectionManager;

	public DbClient(DbConnectionManager connectionManager) {
		this.connectionManager = connectionManager;
	}

	/**
	 * 查詢並以 BeanPropertyRowMapper 對應成 modelClass；查無資料回空 List。
	 */
	public <T> List<T> query(String dbName, String sql, Map<String, Object> params, Class<T> modelClass) {
		String key = checkDbName(dbName);
		List<T> result = connectionManager.getJdbcTemplate(key)
				.query(sql, nullToEmpty(params), BeanPropertyRowMapper.newInstance(modelClass));
		return result == null ? Collections.<T>emptyList() : result;
	}

	/**
	 * 執行 INSERT / UPDATE / DELETE，回影響筆數；單句 autocommit。
	 */
	public int update(String dbName, String sql, Map<String, Object> params) {
		String key = checkDbName(dbName);
		return connectionManager.getJdbcTemplate(key).update(sql, nullToEmpty(params));
	}

	static String checkDbName(String dbName) {
		String key = dbName == null ? "" : dbName.trim();
		if (key.isEmpty()) {
			throw new DbConnectException(dbName, "dbName 不得為空");
		}
		return key;
	}

	private static Map<String, Object> nullToEmpty(Map<String, Object> params) {
		return params == null ? Collections.<String, Object>emptyMap() : params;
	}
}
