package com.mpx.im_acct_insp.dao;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：系統管理軌跡 IM_ACCT_ADMIN_LOG（spec v2.0 §2.5；只增不改）
//           第一階段只記 IMPERSONATE／IMPERSONATE_END（SUCCESS）與無權限切換（DENY）；LOG_ID 取 SEQ_IM_ACCT_ADMIN_LOG
// ============================================================

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.mpx.common.db.DbClient;

@Repository
public class AdminLogDao {

	public static final String INSERT = """
			INSERT INTO IM_ACCT_ADMIN_LOG (LOG_ID, ACT_DATE, ACT_BY, ACT_BY_NAME, ACTION_CODE, TARGET_USER_ID, CONTENT_JSON,
			       REASON, RESULT_CODE, CREATE_DATE, CREATE_BY)
			VALUES (SEQ_IM_ACCT_ADMIN_LOG.NEXTVAL, :now, :actBy, :actByName, :actionCode, :targetUserId, :contentJson,
			       NULL, :resultCode, :now, :actBy)
			""";

	private final DbClient dbClient;
	private final String itflowDb;

	public AdminLogDao(DbClient dbClient, @Value("${db.connect.itflow}") String itflowDb) {
		this.dbClient = dbClient;
		this.itflowDb = itflowDb;
	}

	public int insert(LocalDateTime now, String actBy, String actByName, String actionCode, String targetUserId,
			String contentJson, String resultCode) {
		Map<String, Object> params = new HashMap<>();
		params.put("now", DaoSupport.ts(now));
		params.put("actBy", actBy);
		params.put("actByName", actByName);
		params.put("actionCode", actionCode);
		params.put("targetUserId", targetUserId);
		params.put("contentJson", contentJson);
		params.put("resultCode", resultCode);
		return dbClient.update(itflowDb, INSERT, params);
	}
}
