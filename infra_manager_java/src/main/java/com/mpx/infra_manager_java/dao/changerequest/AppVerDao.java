package com.mpx.infra_manager_java.dao.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-07
// 變更說明: 新增：IM_APP_VER 歷史版次的寫入與讀取（S9 R1）。insertVersion 在補件交易的鎖內寫入舊版快照：
//           APP_VER_NO 是被退件那一版的版次、CLOSE_STATUS_CODE 是該版怎麼結束的（DDL CHECK 限 REJECTED／EXEC_REJECTED／
//           GOV_RETURNED／RECALLED）、VER_REASON 放退件意見、FORM_JSON 放 AppVersionSnapshot 序列化後的 JSON；
//           兩個 CLOB 欄位以 Types.CLOB 綁定。findByApp 給檢視舊版與整合測試用，FORM_JSON 以 CLOB 讀回字串。
//           UK (APP_ID, APP_VER_NO) 保證同一版不會被快照兩次
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
import com.mpx.infra_manager_java.model.changerequest.AppVerRow;

@Repository
public class AppVerDao {

	private final DbClient dbClient;
	private final DbSchema schema;
	private final String itflowDb;

	public AppVerDao(DbClient dbClient, DbSchema schema, @Value("${db.connect.itflow}") String itflowDb) {
		this.dbClient = dbClient;
		this.schema = schema;
		this.itflowDb = itflowDb;
	}

	/** 寫入一筆歷史版次快照；SNAP_DATE 用欄位預設 SYSDATE */
	public void insertVersion(String appId, int verNo, String closeStatusCode, String verReason, String formJson,
			String by) {
		String sql = "INSERT INTO " + schema.table("IM_APP_VER")
				+ " (APP_ID, APP_VER_NO, CLOSE_STATUS_CODE, VER_REASON, FORM_JSON, STATUS, CREATE_DATE, CREATE_BY)"
				+ " VALUES (:appId, :verNo, :closeStatus, :reason, :formJson, 1, SYSDATE, :by)";
		Map<String, Object> p = new HashMap<>();
		p.put("appId", appId);
		p.put("verNo", verNo);
		p.put("closeStatus", closeStatusCode);
		p.put("reason", new SqlParameterValue(Types.CLOB, verReason));
		p.put("formJson", new SqlParameterValue(Types.CLOB, formJson));
		p.put("by", by);
		dbClient.update(itflowDb, sql, p);
	}

	/** 該單所有歷史版次（含 FORM_JSON），依版次排序 */
	public List<AppVerRow> findByApp(String appId) {
		String sql = "SELECT APP_VER_NO, CLOSE_STATUS_CODE, VER_REASON, SNAP_DATE, FORM_JSON, CREATE_BY FROM "
				+ schema.table("IM_APP_VER") + " WHERE APP_ID = :appId AND STATUS = 1 ORDER BY APP_VER_NO";
		return dbClient.query(itflowDb, sql, Map.of("appId", appId), AppVerRow.class);
	}
}
