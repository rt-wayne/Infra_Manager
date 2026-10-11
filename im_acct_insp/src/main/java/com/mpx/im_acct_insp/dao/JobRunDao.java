package com.mpx.im_acct_insp.dao;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：排程執行紀錄 IM_ACCT_JOB_RUN（spec v2.0 §4.11；Table v0.2 7.21）
//           開始先取 SEQ_IM_ACCT_JOB_RUN 再寫 RUNNING（RUN_BY＝手動觸發者、排程為空；BASE_DATE＝採用的今天），
//           結束更新 END_DATE、RESULT_CODE、AFFECTED_CNT、ERR_MSG
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-11
// 變更說明: BACKLOG 第 7 項：新增查同一排程停在 RUNNING 的紀錄、補記為 FAIL（帶 RESULT_CODE＝'RUNNING' 條件）
// ============================================================

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.mpx.common.db.DbClient;
import com.mpx.im_acct_insp.model.IdRow;

@Repository
public class JobRunDao {

	public static final String NEXT_ID = "SELECT SEQ_IM_ACCT_JOB_RUN.NEXTVAL AS ID FROM DUAL";

	public static final String INSERT_RUNNING = """
			INSERT INTO IM_ACCT_JOB_RUN (RUN_ID, JOB_CODE, RUN_BY, BASE_DATE, START_DATE, RESULT_CODE, AFFECTED_CNT,
			       CREATE_DATE, CREATE_BY)
			VALUES (:runId, :jobCode, :runBy, :baseDate, :now, 'RUNNING', 0, :now, :createBy)
			""";

	public static final String UPDATE_FINISH = """
			UPDATE IM_ACCT_JOB_RUN
			   SET END_DATE = :now, RESULT_CODE = :resultCode, AFFECTED_CNT = :affectedCnt, ERR_MSG = :errMsg,
			       UPDATE_DATE = :now, UPDATE_BY = :createBy
			 WHERE RUN_ID = :runId
			""";

	public static final String FIND_RUNNING = """
			SELECT RUN_ID AS ID FROM IM_ACCT_JOB_RUN
			 WHERE JOB_CODE = :jobCode AND RESULT_CODE = 'RUNNING'
			 ORDER BY RUN_ID
			""";

	public static final String UPDATE_STALE_FAIL = """
			UPDATE IM_ACCT_JOB_RUN
			   SET END_DATE = :now, RESULT_CODE = 'FAIL', ERR_MSG = :errMsg, UPDATE_DATE = :now, UPDATE_BY = :updateBy
			 WHERE RUN_ID = :runId AND RESULT_CODE = 'RUNNING'
			""";

	private final DbClient dbClient;
	private final String itflowDb;

	public JobRunDao(DbClient dbClient, @Value("${db.connect.itflow}") String itflowDb) {
		this.dbClient = dbClient;
		this.itflowDb = itflowDb;
	}

	public long nextId() {
		IdRow row = DaoSupport.first(dbClient.query(itflowDb, NEXT_ID, null, IdRow.class));
		if (row == null || row.getId() == null) {
			throw new IllegalStateException("取不到 SEQ_IM_ACCT_JOB_RUN");
		}
		return row.getId();
	}

	public int insertRunning(long runId, String jobCode, String runBy, LocalDate baseDate, LocalDateTime now, String createBy) {
		Map<String, Object> params = new HashMap<>();
		params.put("runId", runId);
		params.put("jobCode", jobCode);
		params.put("runBy", runBy);
		params.put("baseDate", DaoSupport.ts(baseDate));
		params.put("now", DaoSupport.ts(now));
		params.put("createBy", createBy);
		return dbClient.update(itflowDb, INSERT_RUNNING, params);
	}

	public int finish(long runId, String resultCode, int affectedCnt, String errMsg, LocalDateTime now, String createBy) {
		Map<String, Object> params = new HashMap<>();
		params.put("runId", runId);
		params.put("resultCode", resultCode);
		params.put("affectedCnt", affectedCnt);
		params.put("errMsg", errMsg);
		params.put("now", DaoSupport.ts(now));
		params.put("createBy", createBy);
		return dbClient.update(itflowDb, UPDATE_FINISH, params);
	}

	/** 同一排程停在 RUNNING 的 RUN_ID（由小到大） */
	public List<Long> findRunningIds(String jobCode) {
		Map<String, Object> params = new HashMap<>();
		params.put("jobCode", jobCode);
		List<Long> ids = new ArrayList<>();
		for (IdRow row : dbClient.query(itflowDb, FIND_RUNNING, params, IdRow.class)) {
			ids.add(row.getId());
		}
		return ids;
	}

	/** 把仍停在 RUNNING 的一筆補記為 FAIL；回影響筆數（已不是 RUNNING → 0） */
	public int markStaleFail(long runId, String errMsg, LocalDateTime now, String updateBy) {
		Map<String, Object> params = new HashMap<>();
		params.put("runId", runId);
		params.put("errMsg", errMsg);
		params.put("now", DaoSupport.ts(now));
		params.put("updateBy", updateBy);
		return dbClient.update(itflowDb, UPDATE_STALE_FAIL, params);
	}
}
