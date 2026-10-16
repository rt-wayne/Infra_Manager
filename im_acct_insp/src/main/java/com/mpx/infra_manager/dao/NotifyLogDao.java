package com.mpx.infra_manager.dao;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：通知紀錄 IM_ACCT_NOTIFY_LOG（spec v2.0 §4.11、§4.12；Table v0.2 7.20）
//           每位收件人每封信一筆（SUCCESS／FAIL／SKIP）；NOTIFY_ID 取 SEQ_IM_ACCT_NOTIFY_LOG；
//           J02 以（WEEKLY_SUMMARY、週鍵）是否已有任何紀錄判斷本週是否已寄（J01 的判斷寫在 AcctDao 的 NOT EXISTS）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-14
// 變更說明: DB 別名 key 由 db.connect.itflow 改名為 itflow.db.alias（使用者改名）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// ============================================================

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.mpx.common.db.DbClient;
import com.mpx.infra_manager.model.CountRow;

@Repository
public class NotifyLogDao {

	public static final String INSERT = """
			INSERT INTO IM_ACCT_NOTIFY_LOG (NOTIFY_ID, EVENT_CODE, REF_TYPE_CODE, REF_ID, RCPT_USER_ID, RCPT_EMAIL, SEND_DATE,
			       RESULT_CODE, ERR_MSG, CREATE_DATE, CREATE_BY)
			VALUES (SEQ_IM_ACCT_NOTIFY_LOG.NEXTVAL, :eventCode, :refTypeCode, :refId, :rcptUserId, :rcptEmail, :now,
			       :resultCode, :errMsg, :now, :createBy)
			""";

	public static final String COUNT_BY_EVENT_REF = """
			SELECT COUNT(*) AS CNT
			  FROM IM_ACCT_NOTIFY_LOG
			 WHERE EVENT_CODE = :eventCode
			   AND REF_TYPE_CODE = :refTypeCode
			   AND REF_ID = :refId
			""";

	private final DbClient dbClient;
	private final String itflowDb;

	public NotifyLogDao(DbClient dbClient, @Value("${itflow.db.alias}") String itflowDb) {
		this.dbClient = dbClient;
		this.itflowDb = itflowDb;
	}

	public int insert(String eventCode, String refTypeCode, String refId, String rcptUserId, String rcptEmail,
			String resultCode, String errMsg, LocalDateTime now, String createBy) {
		Map<String, Object> params = new HashMap<>();
		params.put("eventCode", eventCode);
		params.put("refTypeCode", refTypeCode);
		params.put("refId", refId);
		params.put("rcptUserId", rcptUserId);
		params.put("rcptEmail", rcptEmail);
		params.put("resultCode", resultCode);
		params.put("errMsg", errMsg);
		params.put("now", DaoSupport.ts(now));
		params.put("createBy", createBy);
		return dbClient.update(itflowDb, INSERT, params);
	}

	public long countByEventRef(String eventCode, String refTypeCode, String refId) {
		Map<String, Object> params = new HashMap<>();
		params.put("eventCode", eventCode);
		params.put("refTypeCode", refTypeCode);
		params.put("refId", refId);
		return DaoSupport.count(dbClient.query(itflowDb, COUNT_BY_EVENT_REF, params, CountRow.class));
	}
}
