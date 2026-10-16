package com.mpx.infra_manager.dao;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：申請單附件 IM_ACCT_ATTACH 存取（spec v2.0 §4.10；Table v0.2 7.16）
//           ATTACH_ID 取 SEQ_IM_ACCT_ATTACH；FILE_PATH 為相對附件根目錄的 REQ_ID/uuid.副檔名；
//           取代時同類型舊列 STATUS＝0（檔案保留）；明細只列 STATUS＝1；上傳人顯示名 JOIN IM_ACCT_USER
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-14
// 變更說明: DB 別名 key 由 db.connect.itflow 改名為 itflow.db.alias（使用者改名）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// ============================================================

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.mpx.common.db.DbClient;
import com.mpx.infra_manager.model.AcctAttach;

@Repository
public class AttachDao {

	public static final String INSERT = """
			INSERT INTO IM_ACCT_ATTACH (ATTACH_ID, REQ_ID, ATTACH_TYPE_CODE, FILE_NAME, FILE_PATH, FILE_SIZE, MIME_TYPE,
			       CREATE_DATE, CREATE_BY)
			VALUES (SEQ_IM_ACCT_ATTACH.NEXTVAL, :reqId, :attachTypeCode, :fileName, :filePath, :fileSize, :mimeType,
			       :now, :userId)
			""";

	public static final String DEACTIVATE = """
			UPDATE IM_ACCT_ATTACH
			   SET STATUS = 0, UPDATE_DATE = :now, UPDATE_BY = :userId
			 WHERE REQ_ID = :reqId
			   AND ATTACH_TYPE_CODE = :attachTypeCode
			   AND STATUS = 1
			""";

	static final String SELECT_BASE = """
			SELECT T.ATTACH_ID, T.REQ_ID, T.ATTACH_TYPE_CODE, T.FILE_NAME, T.FILE_PATH, T.FILE_SIZE, T.MIME_TYPE, T.STATUS,
			       T.CREATE_DATE, T.CREATE_BY,
			""" + "       " + AcctReqDao.nameExpr("U") + " AS CREATE_BY_NAME\n" + """
			  FROM IM_ACCT_ATTACH T
			  LEFT JOIN IM_ACCT_USER U ON U.USER_ID = T.CREATE_BY
			""";

	public static final String FIND_ACTIVE_BY_REQ = SELECT_BASE + """
			 WHERE T.REQ_ID = :reqId
			   AND T.STATUS = 1
			 ORDER BY T.CREATE_DATE, T.ATTACH_ID
			""";

	public static final String FIND_BY_ID = SELECT_BASE + """
			 WHERE T.ATTACH_ID = :attachId
			""";

	private final DbClient dbClient;
	private final String itflowDb;

	public AttachDao(DbClient dbClient, @Value("${itflow.db.alias}") String itflowDb) {
		this.dbClient = dbClient;
		this.itflowDb = itflowDb;
	}

	public int insert(String reqId, String attachTypeCode, String fileName, String filePath, long fileSize, String mimeType,
			LocalDateTime now, String userId) {
		Map<String, Object> params = new HashMap<>();
		params.put("reqId", reqId);
		params.put("attachTypeCode", attachTypeCode);
		params.put("fileName", fileName);
		params.put("filePath", filePath);
		params.put("fileSize", fileSize);
		params.put("mimeType", mimeType);
		params.put("now", DaoSupport.ts(now));
		params.put("userId", userId);
		return dbClient.update(itflowDb, INSERT, params);
	}

	public int deactivate(String reqId, String attachTypeCode, LocalDateTime now, String userId) {
		Map<String, Object> params = new HashMap<>();
		params.put("reqId", reqId);
		params.put("attachTypeCode", attachTypeCode);
		params.put("now", DaoSupport.ts(now));
		params.put("userId", userId);
		return dbClient.update(itflowDb, DEACTIVATE, params);
	}

	public List<AcctAttach> findActiveByReq(String reqId) {
		Map<String, Object> params = new HashMap<>();
		params.put("reqId", reqId);
		return dbClient.query(itflowDb, FIND_ACTIVE_BY_REQ, params, AcctAttach.class);
	}

	public AcctAttach findById(long attachId) {
		Map<String, Object> params = new HashMap<>();
		params.put("attachId", attachId);
		return DaoSupport.first(dbClient.query(itflowDb, FIND_BY_ID, params, AcctAttach.class));
	}
}
