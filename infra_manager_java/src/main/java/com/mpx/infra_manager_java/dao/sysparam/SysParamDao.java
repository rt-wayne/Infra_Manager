package com.mpx.infra_manager_java.dao.sysparam;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-06
// 變更說明: 新增：SYS_PARAM 讀取（S6 回合二 a）。主鍵是 PARAM_NAME＋PARAM_VALUE，同名可有多列（多值參數一值一列），
//           只取 STATUS=1，依最後更新、建立時間新到舊排序；單值參數由呼叫端取第一列
// ============================================================

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.mpx.common.db.DbClient;
import com.mpx.infra_manager_java.config.DbSchema;
import com.mpx.infra_manager_java.model.sysparam.SysParamRow;

@Repository
public class SysParamDao {

	private final DbClient dbClient;
	private final DbSchema schema;
	private final String itflowDb;

	public SysParamDao(DbClient dbClient, DbSchema schema, @Value("${db.connect.itflow}") String itflowDb) {
		this.dbClient = dbClient;
		this.schema = schema;
		this.itflowDb = itflowDb;
	}

	public List<String> findValues(String paramName) {
		String sql = "SELECT PARAM_VALUE FROM " + schema.table("SYS_PARAM")
				+ " WHERE PARAM_NAME = :name AND STATUS = 1"
				+ " ORDER BY NVL(UPDATE_DATE, CREATE_DATE) DESC, PARAM_VALUE";
		return dbClient.query(itflowDb, sql, Map.of("name", paramName), SysParamRow.class).stream()
				.map(SysParamRow::getParamValue).toList();
	}
}
