package com.mpx.infra_manager_java.dao.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：申請單附件查詢（S4）。一張單的附件分三種擁有者：申請單本身（OWNER_ID＝單號）、
//           簽核關卡（OWNER_ID＝該單簽核實例底下的 APPR_STEP_ID）、事件（OWNER_ID＝該單的 APP_EVENT_ID）；
//           下載端點只接受在這份清單裡的 ATTACH_ID，等於用「附件屬於這張單」做授權
//           2026-10-07 S6 回合三（Claude Opus 5.5）：加上傳用的 findAppState（不鎖）、lockApp（FOR UPDATE）、
//           countAppFiles（只算 OWNER_TYPE＝APP）、insertAppFile（寫入後以唯一的 FILE_PATH 查回 ATTACH_ID）
// ============================================================

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.mpx.common.db.DbClient;
import com.mpx.infra_manager_java.config.DbSchema;
import com.mpx.infra_manager_java.model.changerequest.AppLockRow;
import com.mpx.infra_manager_java.model.changerequest.AttachRow;
import com.mpx.infra_manager_java.model.changerequest.CountRow;

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

	/** 上傳用：不鎖，先擋掉明顯不能傳的請求（單不存在或已刪除回 null） */
	public AppLockRow findAppState(String appId) {
		return first(dbClient.query(itflowDb, appStateSql(""), Map.of("appId", appId), AppLockRow.class));
	}

	/** 上傳用：在交易內鎖住主檔列（同一張單的上傳在此排隊，計數與寫入才不會超過上限） */
	public AppLockRow lockApp(String appId) {
		return first(dbClient.query(itflowDb, appStateSql(" FOR UPDATE"), Map.of("appId", appId), AppLockRow.class));
	}

	private String appStateSql(String suffix) {
		return "SELECT APP_STATUS_CODE, APPLY_USER_ID, ROW_VER_NO FROM " + schema.table("IM_APP")
				+ " WHERE APP_ID = :appId AND STATUS = 1" + suffix;
	}

	/** 申請單本身（OWNER_TYPE＝APP）的有效附件數；簽核關卡與事件的附件不計入 */
	public int countAppFiles(String appId) {
		String sql = "SELECT COUNT(*) AS CNT FROM " + schema.table("IM_ATTACH")
				+ " WHERE OWNER_TYPE = 'APP' AND OWNER_ID = :appId AND STATUS = 1";
		List<CountRow> rows = dbClient.query(itflowDb, sql, Map.of("appId", appId), CountRow.class);
		return rows.isEmpty() || rows.get(0).getCnt() == null ? 0 : rows.get(0).getCnt().intValue();
	}

	/** 新增申請單附件索引列，回寫入後的那一列（ATTACH_ID 由 identity 產生，以唯一的 FILE_PATH 查回） */
	public AttachRow insertAppFile(String appId, String origFileName, String storeFileName, String filePath,
			long byteQty, String mimeType, String sha256, String by) {
		String sql = "INSERT INTO " + schema.table("IM_ATTACH")
				+ " (OWNER_TYPE, OWNER_ID, ORIG_FILE_NAME, STORE_FILE_NAME, FILE_PATH, FILE_BYTE_QTY, MIME_TYPE,"
				+ " SHA256_HASH, STATUS, CREATE_DATE, CREATE_BY)"
				+ " VALUES ('APP', :appId, :origName, :storeName, :filePath, :byteQty, :mime, :sha256, 1, SYSDATE, :by)";
		Map<String, Object> p = new HashMap<>();
		p.put("appId", appId);
		p.put("origName", origFileName);
		p.put("storeName", storeFileName);
		p.put("filePath", filePath);
		p.put("byteQty", byteQty);
		p.put("mime", mimeType);
		p.put("sha256", sha256);
		p.put("by", by);
		dbClient.update(itflowDb, sql, p);
		String select = "SELECT T.ATTACH_ID, T.OWNER_TYPE, T.OWNER_ID, T.ORIG_FILE_NAME, T.FILE_PATH, T.FILE_BYTE_QTY,"
				+ " T.MIME_TYPE, T.CREATE_DATE FROM " + schema.table("IM_ATTACH") + " T WHERE T.FILE_PATH = :filePath";
		AttachRow row = first(dbClient.query(itflowDb, select, Map.of("filePath", filePath), AttachRow.class));
		if (row == null) {
			throw new IllegalStateException("附件索引寫入後查不到");
		}
		return row;
	}

	private static <T> T first(List<T> rows) {
		return rows.isEmpty() ? null : rows.get(0);
	}
}
