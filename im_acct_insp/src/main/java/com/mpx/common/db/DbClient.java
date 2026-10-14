package com.mpx.common.db;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-02
// 變更說明: 新增：DB 存取對外唯一入口（規格 §6.1）
//           只做 dbName 檢查（trim、區分大小寫、null/空白直接丟例外不打 API，規格 D-04）與轉呼
//           SQL 一律以 :name 具名參數綁定，本類別不做任何字串拼接（規格 S-04）
//           update 本身不開交易：呼叫端沒開交易時每句 autocommit；在 TransactionTemplate 內呼叫則加入該交易
//           （僅限 itflow.db.alias 別名：LazyDataSourceTransactionManager 綁該別名的 Hikari 池；
//           其他別名仍每句 autocommit、不隨交易回滾）
//           SQL 執行期錯誤原樣拋 Spring DataAccessException（規格 §13-3）
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S4b-2（S4 架構審查 F8）：修正上方過時說明——原寫「update 為單句 autocommit，不支援交易」，
//           但 2026-10-07 起 LazyDataSourceTransactionManager 已讓 update 在 TransactionTemplate 內加入交易，
//           RequestService／EmergencyService 皆依賴此行為。只改檔頭與 README，程式未動
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-14
// 變更說明: 上方說明中的 DB 別名 key 由 db.connect.itflow 更名為 itflow.db.alias（隨 im_acct_insp 改名），程式未動
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
