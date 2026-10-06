package com.mpx.infra_manager_java.dao.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-06
// 變更說明: 新增：表單選項讀取（S6 回合二 a）。只取 STATUS=1，依群組、排序、ID 排列
// ============================================================

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.mpx.common.db.DbClient;
import com.mpx.infra_manager_java.config.DbSchema;
import com.mpx.infra_manager_java.model.changerequest.FormOptionRow;

@Repository
public class FormOptionDao {

	private final DbClient dbClient;
	private final DbSchema schema;
	private final String itflowDb;

	public FormOptionDao(DbClient dbClient, DbSchema schema, @Value("${db.connect.itflow}") String itflowDb) {
		this.dbClient = dbClient;
		this.schema = schema;
		this.itflowDb = itflowDb;
	}

	public List<FormOptionRow> findActive() {
		String sql = "SELECT FORM_OPTION_ID, GROUP_CODE, OPTION_CODE, UP_FORM_OPTION_ID, OPTION_NAME, COLOR_CODE,"
				+ " OPTION_DESC, TIME_LIMIT_DESC, PRIO_FLOW_DESC, SAMPLE_DESC, FLOW_ID, SORT_NO"
				+ " FROM " + schema.table("IM_FORM_OPTION")
				+ " WHERE STATUS = 1"
				+ " ORDER BY GROUP_CODE, SORT_NO, FORM_OPTION_ID";
		return dbClient.query(itflowDb, sql, Map.of(), FormOptionRow.class);
	}
}
