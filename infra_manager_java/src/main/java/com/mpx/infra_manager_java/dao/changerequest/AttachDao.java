package com.mpx.infra_manager_java.dao.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：申請單附件查詢（S4）。一張單的附件分三種擁有者：申請單本身（OWNER_ID＝單號）、
//           簽核關卡（OWNER_ID＝該單簽核實例底下的 APPR_STEP_ID）、事件（OWNER_ID＝該單的 APP_EVENT_ID）；
//           下載端點只接受在這份清單裡的 ATTACH_ID，等於用「附件屬於這張單」做授權
// ============================================================

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.mpx.common.db.DbClient;
import com.mpx.infra_manager_java.config.DbSchema;
import com.mpx.infra_manager_java.model.changerequest.AttachRow;

@Repository
public class AttachDao {

	private final DbClient dbClient;
	private final DbSchema schema;
	private final String itflowDb;

	public AttachDao(DbClient dbClient, DbSchema schema, @Value("${db.connect.itflow}") String itflowDb) {
		this.dbClient = dbClient;
		this.schema = schema;
		this.itflowDb = itflowDb;
	}

	public List<AttachRow> findByApp(String appId) {
		String sql = "SELECT T.ATTACH_ID, T.OWNER_TYPE, T.OWNER_ID, T.ORIG_FILE_NAME, T.FILE_PATH, T.FILE_BYTE_QTY,"
				+ " T.MIME_TYPE, T.CREATE_DATE FROM " + schema.table("IM_ATTACH") + " T"
				+ " WHERE T.STATUS = 1 AND ("
				+ " (T.OWNER_TYPE = 'APP' AND T.OWNER_ID = :appId)"
				+ " OR (T.OWNER_TYPE = 'STEP' AND T.OWNER_ID IN (SELECT TO_CHAR(S.APPR_STEP_ID) FROM "
				+ schema.table("IM_APPR_STEP") + " S JOIN " + schema.table("IM_APPR")
				+ " R ON R.APPR_ID = S.APPR_ID WHERE R.DOC_TYPE = 'CR' AND R.DOC_ID = :appId))"
				+ " OR (T.OWNER_TYPE = 'EVENT' AND T.OWNER_ID IN (SELECT TO_CHAR(E.APP_EVENT_ID) FROM "
				+ schema.table("IM_APP_EVENT") + " E WHERE E.APP_ID = :appId)))"
				+ " ORDER BY T.CREATE_DATE, T.ATTACH_ID";
		return dbClient.query(itflowDb, sql, Map.of("appId", appId), AttachRow.class);
	}
}
