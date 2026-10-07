package com.mpx.infra_manager_java.dao.template;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：IM_TMPL 範本的讀寫（S5 R1）。只看 STATUS=1；刪除為軟刪除（STATUS=0）。
//           列表不搬 FORM_JSON，優先等級以 JSON_VALUE 取出後對 IM_FORM_OPTION 帶名稱與顏色；
//           FORM_JSON 以 Types.CLOB 綁定
// ============================================================

import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Repository;

import com.mpx.common.db.DbClient;
import com.mpx.infra_manager_java.config.DbSchema;
import com.mpx.infra_manager_java.model.template.TemplateRow;

@Repository
public class TemplateDao {

	private final DbClient dbClient;
	private final DbSchema schema;
	private final String itflowDb;

	public TemplateDao(DbClient dbClient, DbSchema schema, @Value("${db.connect.itflow}") String itflowDb) {
		this.dbClient = dbClient;
		this.schema = schema;
		this.itflowDb = itflowDb;
	}

	/** 有效範本列表（不含 FORM_JSON），依名稱排序 */
	public List<TemplateRow> findActive() {
		String sql = "SELECT T.TMPL_ID, T.TMPL_NAME, T.PRIO_CODE, P.OPTION_NAME AS PRIO_NAME, P.COLOR_CODE AS PRIO_COLOR,"
				+ " T.OWNER_USER_ID, U.USER_NAME AS OWNER_NAME, T.USE_CNT, T.LAST_USE_DATE,"
				+ " LU.USER_NAME AS LAST_USE_USER_NAME, T.CREATE_DATE, T.UPDATE_DATE"
				+ " FROM (SELECT TMPL_ID, TMPL_NAME, JSON_VALUE(FORM_JSON, '$.prioCode') AS PRIO_CODE, OWNER_USER_ID,"
				+ "   USE_CNT, LAST_USE_DATE, LAST_USE_USER_ID, CREATE_DATE, UPDATE_DATE"
				+ "   FROM " + schema.table("IM_TMPL") + " WHERE STATUS = 1) T"
				+ " LEFT JOIN " + schema.table("IM_USER") + " U ON U.USER_ID = T.OWNER_USER_ID"
				+ " LEFT JOIN " + schema.table("IM_USER") + " LU ON LU.USER_ID = T.LAST_USE_USER_ID"
				+ " LEFT JOIN " + schema.table("IM_FORM_OPTION")
				+ " P ON P.GROUP_CODE = 'PRIO' AND P.OPTION_CODE = T.PRIO_CODE AND P.STATUS = 1"
				+ " ORDER BY T.TMPL_NAME, T.TMPL_ID";
		return dbClient.query(itflowDb, sql, Map.of(), TemplateRow.class);
	}

	/** 單一有效範本（含 FORM_JSON）；找不到回 null */
	public TemplateRow findById(String tmplId) {
		String sql = "SELECT T.TMPL_ID, T.TMPL_NAME, T.FORM_JSON, T.OWNER_USER_ID, U.USER_NAME AS OWNER_NAME, T.USE_CNT,"
				+ " T.LAST_USE_DATE, LU.USER_NAME AS LAST_USE_USER_NAME, T.CREATE_DATE, T.UPDATE_DATE"
				+ " FROM " + schema.table("IM_TMPL") + " T"
				+ " LEFT JOIN " + schema.table("IM_USER") + " U ON U.USER_ID = T.OWNER_USER_ID"
				+ " LEFT JOIN " + schema.table("IM_USER") + " LU ON LU.USER_ID = T.LAST_USE_USER_ID"
				+ " WHERE T.TMPL_ID = :id AND T.STATUS = 1";
		List<TemplateRow> rows = dbClient.query(itflowDb, sql, Map.of("id", tmplId), TemplateRow.class);
		return rows.isEmpty() ? null : rows.get(0);
	}

	public void insert(String tmplId, String tmplName, String formJson, String userId) {
		String sql = "INSERT INTO " + schema.table("IM_TMPL")
				+ " (TMPL_ID, TMPL_NAME, FORM_JSON, OWNER_USER_ID, USE_CNT, STATUS, CREATE_DATE, CREATE_BY)"
				+ " VALUES (:id, :name, :formJson, :userId, 0, 1, SYSDATE, :userId)";
		Map<String, Object> p = new HashMap<>();
		p.put("id", tmplId);
		p.put("name", tmplName);
		p.put("formJson", new SqlParameterValue(Types.CLOB, formJson));
		p.put("userId", userId);
		dbClient.update(itflowDb, sql, p);
	}

	/** 改名稱與內容；回更新列數（0＝已不存在或已刪除） */
	public int update(String tmplId, String tmplName, String formJson, String userId) {
		String sql = "UPDATE " + schema.table("IM_TMPL")
				+ " SET TMPL_NAME = :name, FORM_JSON = :formJson, UPDATE_DATE = SYSDATE, UPDATE_BY = :userId"
				+ " WHERE TMPL_ID = :id AND STATUS = 1";
		Map<String, Object> p = new HashMap<>();
		p.put("id", tmplId);
		p.put("name", tmplName);
		p.put("formJson", new SqlParameterValue(Types.CLOB, formJson));
		p.put("userId", userId);
		return dbClient.update(itflowDb, sql, p);
	}

	/** 軟刪除；回更新列數（0＝已不存在或已刪除） */
	public int softDelete(String tmplId, String userId) {
		String sql = "UPDATE " + schema.table("IM_TMPL")
				+ " SET STATUS = 0, UPDATE_DATE = SYSDATE, UPDATE_BY = :userId WHERE TMPL_ID = :id AND STATUS = 1";
		return dbClient.update(itflowDb, sql, Map.of("id", tmplId, "userId", userId));
	}
}
