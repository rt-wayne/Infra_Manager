package com.mpx.infra_manager_java.dao.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：執行紀錄寫入 IM_APP_CHECK_LIST 與 IM_APP_EXEC（S10 R1，施工計畫 ⑤⑥⑦）。
//           全部在 AppExecutionService 以 AppWriteDao.lockForUpdate 取得主檔列鎖之後呼叫，同一張單不會兩個交易同時寫，
//           所以 IM_APP_EXEC 用「先 UPDATE、0 列再 INSERT」即可，不需 MERGE。CLOB 欄位以 Types.CLOB 綁定（同 AppWriteDao）
// ============================================================

import java.sql.Timestamp;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Repository;

import com.mpx.common.db.DbClient;
import com.mpx.infra_manager_java.config.DbSchema;
import com.mpx.infra_manager_java.model.changerequest.CheckListRow;
import com.mpx.infra_manager_java.model.changerequest.ExecutionDraft;

@Repository
public class ExecWriteDao {

	private final DbClient dbClient;
	private final DbSchema schema;
	private final String itflowDb;

	public ExecWriteDao(DbClient dbClient, DbSchema schema, @Value("${db.connect.itflow}") String itflowDb) {
		this.dbClient = dbClient;
		this.schema = schema;
		this.itflowDb = itflowDb;
	}

	/** 該版次已展開的檢核項序號（遞增）；還沒展開回空清單 */
	public List<Integer> findCheckSeqNos(String appId, int verNo) {
		String sql = "SELECT SEQ_NO FROM " + schema.table("IM_APP_CHECK_LIST")
				+ " WHERE APP_ID = :appId AND APP_VER_NO = :verNo AND STATUS = 1 ORDER BY SEQ_NO";
		return dbClient.query(itflowDb, sql, Map.of("appId", appId, "verNo", verNo), CheckListRow.class).stream()
				.map(CheckListRow::getSeqNo).toList();
	}

	/** 展開一列檢核項（未完成、無執行人） */
	public void insertCheckItem(String appId, int verNo, int seqNo, long formOptionId, String by) {
		String sql = "INSERT INTO " + schema.table("IM_APP_CHECK_LIST")
				+ " (APP_ID, APP_VER_NO, SEQ_NO, FORM_OPTION_ID, IS_DONE, STATUS, CREATE_DATE, CREATE_BY)"
				+ " VALUES (:appId, :verNo, :seqNo, :optionId, 0, 1, SYSDATE, :by)";
		dbClient.update(itflowDb, sql,
				Map.of("appId", appId, "verNo", verNo, "seqNo", seqNo, "optionId", formOptionId, "by", by));
	}

	/** 更新一列檢核項；doneAt 由呼叫端決定（未完成傳 null） */
	public int updateCheckItem(String appId, int verNo, ExecutionDraft.CheckItem item, Timestamp doneAt, String by) {
		String sql = "UPDATE " + schema.table("IM_APP_CHECK_LIST")
				+ " SET IS_DONE = :done, DONE_DATE = :doneAt, USER_ID = :userId, EXEC_USER_DESC = :desc,"
				+ " UPDATE_DATE = SYSDATE, UPDATE_BY = :by"
				+ " WHERE APP_ID = :appId AND APP_VER_NO = :verNo AND SEQ_NO = :seqNo AND STATUS = 1";
		Map<String, Object> p = new HashMap<>();
		p.put("appId", appId);
		p.put("verNo", verNo);
		p.put("seqNo", item.seqNo());
		p.put("done", item.done() ? 1 : 0);
		p.put("doneAt", doneAt);
		p.put("userId", item.userId());
		p.put("desc", item.executorDesc());
		p.put("by", by);
		return dbClient.update(itflowDb, sql, p);
	}

	/**
	 * 寫入該版次的執行結果：有列就 UPDATE、沒有就 INSERT。closeBy 非 null（送治理審查）時寫結案人與結案時間 SYSDATE；
	 * 暫存傳 null，兩欄維持 null
	 */
	public void upsertExec(String appId, int verNo, ExecutionDraft d, String closeBy, String by) {
		Map<String, Object> p = new HashMap<>();
		p.put("appId", appId);
		p.put("verNo", verNo);
		p.put("start", d.actualStart());
		p.put("end", d.actualEnd());
		p.put("resultCode", d.resultCode());
		p.put("exception", d.exception() ? 1 : 0);
		p.put("exceptionDesc", clob(d.exceptionDesc()));
		p.put("followUp", d.followUp() ? 1 : 0);
		p.put("followUpDesc", clob(d.followUpDesc()));
		p.put("memo", clob(d.memo()));
		p.put("closeBy", closeBy);
		p.put("close", closeBy != null ? 1 : 0);
		p.put("by", by);
		String update = "UPDATE " + schema.table("IM_APP_EXEC")
				+ " SET ACTUAL_START_DATE = :start, ACTUAL_END_DATE = :end, RESULT_CODE = :resultCode,"
				+ " IS_EXCPT = :exception, EXCPT_DESC = :exceptionDesc, IS_FOLLOW_UP = :followUp,"
				+ " FOLLOW_UP_DESC = :followUpDesc, EXEC_MEMO = :memo, USER_ID = :closeBy,"
				+ " CLOSE_DATE = CASE WHEN :close = 1 THEN SYSDATE END, UPDATE_DATE = SYSDATE, UPDATE_BY = :by"
				+ " WHERE APP_ID = :appId AND APP_VER_NO = :verNo AND STATUS = 1";
		if (dbClient.update(itflowDb, update, p) > 0) {
			return;
		}
		String insert = "INSERT INTO " + schema.table("IM_APP_EXEC")
				+ " (APP_ID, APP_VER_NO, ACTUAL_START_DATE, ACTUAL_END_DATE, RESULT_CODE, IS_EXCPT, EXCPT_DESC,"
				+ " IS_FOLLOW_UP, FOLLOW_UP_DESC, EXEC_MEMO, USER_ID, CLOSE_DATE, STATUS, CREATE_DATE, CREATE_BY)"
				+ " VALUES (:appId, :verNo, :start, :end, :resultCode, :exception, :exceptionDesc,"
				+ " :followUp, :followUpDesc, :memo, :closeBy, CASE WHEN :close = 1 THEN SYSDATE END, 1, SYSDATE, :by)";
		dbClient.update(itflowDb, insert, p);
	}

	private static SqlParameterValue clob(String value) {
		return new SqlParameterValue(Types.CLOB, value);
	}
}
