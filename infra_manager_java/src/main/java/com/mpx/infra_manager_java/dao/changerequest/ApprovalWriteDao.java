package com.mpx.infra_manager_java.dao.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-07
// 變更說明: 新增：簽核實例、關卡、候選人與申請單事件的寫入（S7 R1）。每個方法單句；交易由 AppFlowService 決定。
//           範本 DbClient 拿不到 IDENTITY 產生的鍵值，所以 insertAppr 之後用 findPendingApprId 依唯一索引
//           IND_IM_APPR_01 的條件（同文件同版次只有一筆進行中）查回 APPR_ID。
//           關卡與候選人各用一句 INSERT…SELECT 從流程定義展開：序號最小且非只通知的關卡 PENDING、只通知 SKIPPED（⑥A）、
//           其餘 WAITING；候選人 ROLE 型要求使用者、角色、使用者角色對照三者都啟用，USER 型取啟用中的指定人，
//           一律排除申請人（使用者裁示 ①B）。角色一律看 ROLE_ID，不看 STEP_CODE（第 54 項 key 錯位）
//           2026-10-07 S7 R2：加 decideStep（WHERE PENDING 的條件式 UPDATE，0 列＝被搶簽）與 activateNext（序號最小 WAITING → PENDING）
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
import com.mpx.infra_manager_java.model.changerequest.ApprRow;
import com.mpx.infra_manager_java.model.changerequest.ApprStepRow;
import com.mpx.infra_manager_java.model.changerequest.CountRow;

@Repository
public class ApprovalWriteDao {

	private final DbClient dbClient;
	private final DbSchema schema;
	private final String itflowDb;

	public ApprovalWriteDao(DbClient dbClient, DbSchema schema, @Value("${db.connect.itflow}") String itflowDb) {
		this.dbClient = dbClient;
		this.schema = schema;
		this.itflowDb = itflowDb;
	}

	/** 新增進行中的簽核實例（DOC_TYPE 固定 CR） */
	public void insertAppr(String appId, int verNo, String flowId, String by) {
		String sql = "INSERT INTO " + schema.table("IM_APPR")
				+ " (DOC_TYPE, DOC_ID, DOC_VER_NO, FLOW_ID, APPR_STATUS_CODE, START_DATE, STATUS, CREATE_DATE, CREATE_BY)"
				+ " VALUES ('CR', :appId, :verNo, :flowId, 'PENDING', SYSDATE, 1, SYSDATE, :by)";
		dbClient.update(itflowDb, sql, Map.of("appId", appId, "verNo", verNo, "flowId", flowId, "by", by));
	}

	/** 該文件該版次進行中的簽核實例；沒有回 null（唯一索引保證最多一筆） */
	public ApprRow findPendingAppr(String appId, int verNo) {
		String sql = "SELECT APPR_ID, DOC_VER_NO, FLOW_ID, APPR_STATUS_CODE, START_DATE, CLOSE_DATE FROM "
				+ schema.table("IM_APPR") + " WHERE DOC_TYPE = 'CR' AND DOC_ID = :appId AND DOC_VER_NO = :verNo"
				+ " AND APPR_STATUS_CODE = 'PENDING' AND STATUS = 1";
		List<ApprRow> rows = dbClient.query(itflowDb, sql, Map.of("appId", appId, "verNo", verNo), ApprRow.class);
		return rows.isEmpty() ? null : rows.get(0);
	}

	/**
	 * 依流程定義展開關卡。序號最小且非只通知的那一關 PENDING，只通知的 SKIPPED，其餘 WAITING。
	 * 回寫入筆數（0 表示流程沒有啟用中的關卡）
	 */
	public int insertSteps(long apprId, String flowId, String by) {
		String flowStep = schema.table("IM_FLOW_STEP");
		String sql = "INSERT INTO " + schema.table("IM_APPR_STEP")
				+ " (APPR_ID, FLOW_STEP_ID, SEQ_NO, STEP_STATUS_CODE, STATUS, CREATE_DATE, CREATE_BY)"
				+ " SELECT :apprId, FS.FLOW_STEP_ID, FS.SEQ_NO,"
				+ " CASE WHEN FS.IS_NOTIFY_ONLY = 1 THEN 'SKIPPED'"
				+ "      WHEN FS.SEQ_NO = (SELECT MIN(F2.SEQ_NO) FROM " + flowStep + " F2"
				+ "        WHERE F2.FLOW_ID = FS.FLOW_ID AND F2.STATUS = 1 AND F2.IS_NOTIFY_ONLY = 0) THEN 'PENDING'"
				+ "      ELSE 'WAITING' END, 1, SYSDATE, :by"
				+ " FROM " + flowStep + " FS WHERE FS.FLOW_ID = :flowId AND FS.STATUS = 1";
		return dbClient.update(itflowDb, sql, Map.of("apprId", apprId, "flowId", flowId, "by", by));
	}

	/**
	 * 為該實例所有待簽關卡（PENDING／WAITING）展開候選人並固化（⑧A）。
	 * ROLE 型：使用者、角色、使用者角色對照都要啟用；USER 型：指定人要啟用；一律排除申請人（①B）
	 */
	public int insertCandidates(long apprId, String applicantId, String by) {
		String sql = "INSERT INTO " + schema.table("IM_APPR_CAND_MAP")
				+ " (APPR_STEP_ID, USER_ID, STATUS, CREATE_DATE, CREATE_BY)"
				+ " SELECT S.APPR_STEP_ID, U.USER_ID, 1, SYSDATE, :by"
				+ " FROM " + schema.table("IM_APPR_STEP") + " S"
				+ " JOIN " + schema.table("IM_FLOW_STEP") + " FS ON FS.FLOW_STEP_ID = S.FLOW_STEP_ID"
				+ " JOIN " + schema.table("IM_USER") + " U ON U.STATUS = 1 AND ("
				+ "   (FS.APPR_TYPE = 'USER' AND U.USER_ID = FS.USER_ID) OR"
				+ "   (FS.APPR_TYPE = 'ROLE' AND EXISTS (SELECT 1 FROM " + schema.table("IM_USER_ROLE_MAP") + " M"
				+ "     JOIN " + schema.table("IM_ROLE") + " R ON R.ROLE_ID = M.ROLE_ID AND R.STATUS = 1"
				+ "     WHERE M.USER_ID = U.USER_ID AND M.ROLE_ID = FS.ROLE_ID AND M.STATUS = 1)))"
				+ " WHERE S.APPR_ID = :apprId AND S.STATUS = 1 AND S.STEP_STATUS_CODE IN ('PENDING', 'WAITING')"
				+ " AND U.USER_ID <> :applicantId";
		return dbClient.update(itflowDb, sql, Map.of("apprId", apprId, "applicantId", applicantId, "by", by));
	}

	/** 待簽關卡（PENDING／WAITING）數；0 表示整條流程沒有要簽的關卡 */
	public long countOpenSteps(long apprId) {
		String sql = "SELECT COUNT(*) AS CNT FROM " + schema.table("IM_APPR_STEP")
				+ " WHERE APPR_ID = :apprId AND STATUS = 1 AND STEP_STATUS_CODE IN ('PENDING', 'WAITING')";
		return count(sql, Map.of("apprId", apprId));
	}

	/** 沒有任何候選人的待簽關卡（序號、關卡名），依序號排序；送審時用來擋「永遠沒人能簽」 */
	public List<ApprStepRow> findOpenStepsWithoutCandidate(long apprId) {
		String sql = "SELECT S.APPR_STEP_ID, S.SEQ_NO, FS.STEP_NAME, S.STEP_STATUS_CODE"
				+ " FROM " + schema.table("IM_APPR_STEP") + " S"
				+ " JOIN " + schema.table("IM_FLOW_STEP") + " FS ON FS.FLOW_STEP_ID = S.FLOW_STEP_ID"
				+ " WHERE S.APPR_ID = :apprId AND S.STATUS = 1 AND S.STEP_STATUS_CODE IN ('PENDING', 'WAITING')"
				+ " AND NOT EXISTS (SELECT 1 FROM " + schema.table("IM_APPR_CAND_MAP") + " C"
				+ "   WHERE C.APPR_STEP_ID = S.APPR_STEP_ID AND C.STATUS = 1)"
				+ " ORDER BY S.SEQ_NO";
		return dbClient.query(itflowDb, sql, Map.of("apprId", apprId), ApprStepRow.class);
	}

	/** 已簽核完成（APPROVED／REJECTED）的關卡數；撤回只允許 0 */
	public long countDecidedSteps(long apprId) {
		String sql = "SELECT COUNT(*) AS CNT FROM " + schema.table("IM_APPR_STEP")
				+ " WHERE APPR_ID = :apprId AND STATUS = 1 AND STEP_STATUS_CODE IN ('APPROVED', 'REJECTED')";
		return count(sql, Map.of("apprId", apprId));
	}

	/** 把尚未結束的關卡（PENDING／WAITING）一律改成 toStatus（撤回 → CANCELLED、退件 → SKIPPED） */
	public int closeOpenSteps(long apprId, String toStatus, String by) {
		String sql = "UPDATE " + schema.table("IM_APPR_STEP") + " SET STEP_STATUS_CODE = :toStatus,"
				+ " UPDATE_DATE = SYSDATE, UPDATE_BY = :by"
				+ " WHERE APPR_ID = :apprId AND STATUS = 1 AND STEP_STATUS_CODE IN ('PENDING', 'WAITING')";
		return dbClient.update(itflowDb, sql, Map.of("apprId", apprId, "toStatus", toStatus, "by", by));
	}

	/** 結束進行中的簽核實例（APPROVED／REJECTED／RECALLED）；只動 PENDING 的，回影響筆數 */
	public int closeAppr(long apprId, String toStatus, String by) {
		String sql = "UPDATE " + schema.table("IM_APPR") + " SET APPR_STATUS_CODE = :toStatus, CLOSE_DATE = SYSDATE,"
				+ " UPDATE_DATE = SYSDATE, UPDATE_BY = :by"
				+ " WHERE APPR_ID = :apprId AND APPR_STATUS_CODE = 'PENDING' AND STATUS = 1";
		return dbClient.update(itflowDb, sql, Map.of("apprId", apprId, "toStatus", toStatus, "by", by));
	}

	/**
	 * 簽核目前關卡：只在該關仍是 PENDING 時寫入決定、簽核人、時間與意見（CHECK 約束要求 APPROVED／REJECTED 必帶人與時間）。
	 * 回 0 表示該關已被別人簽走（第二道防線，第一道是主檔的版本鎖）
	 */
	public int decideStep(long apprStepId, String toStatus, String userId, String memo) {
		String sql = "UPDATE " + schema.table("IM_APPR_STEP") + " SET STEP_STATUS_CODE = :toStatus, USER_ID = :userId,"
				+ " DECIDE_DATE = SYSDATE, MEMO = :memo, UPDATE_DATE = SYSDATE, UPDATE_BY = :userId"
				+ " WHERE APPR_STEP_ID = :apprStepId AND STATUS = 1 AND STEP_STATUS_CODE = 'PENDING'";
		Map<String, Object> p = new HashMap<>();
		p.put("apprStepId", apprStepId);
		p.put("toStatus", toStatus);
		p.put("userId", userId);
		p.put("memo", new SqlParameterValue(Types.CLOB, memo));
		return dbClient.update(itflowDb, sql, p);
	}

	/** 把序號最小的 WAITING 關卡改成 PENDING（進下一關）；回 0 表示沒有下一關、整條流程簽完 */
	public int activateNext(long apprId, String by) {
		String step = schema.table("IM_APPR_STEP");
		String sql = "UPDATE " + step + " SET STEP_STATUS_CODE = 'PENDING', UPDATE_DATE = SYSDATE, UPDATE_BY = :by"
				+ " WHERE APPR_ID = :apprId AND STATUS = 1 AND STEP_STATUS_CODE = 'WAITING'"
				+ " AND SEQ_NO = (SELECT MIN(S2.SEQ_NO) FROM " + step + " S2"
				+ "   WHERE S2.APPR_ID = :apprId AND S2.STATUS = 1 AND S2.STEP_STATUS_CODE = 'WAITING')";
		return dbClient.update(itflowDb, sql, Map.of("apprId", apprId, "by", by));
	}

	/** 寫申請單事件（SUBMIT／RECALL⋯）；memo 可為 null */
	public void insertEvent(String appId, int verNo, String eventCode, String userId, String memo) {
		String sql = "INSERT INTO " + schema.table("IM_APP_EVENT")
				+ " (APP_ID, APP_VER_NO, EVENT_CODE, USER_ID, EVENT_DATE, MEMO, STATUS, CREATE_DATE, CREATE_BY)"
				+ " VALUES (:appId, :verNo, :eventCode, :userId, SYSDATE, :memo, 1, SYSDATE, :userId)";
		Map<String, Object> p = new HashMap<>();
		p.put("appId", appId);
		p.put("verNo", verNo);
		p.put("eventCode", eventCode);
		p.put("userId", userId);
		p.put("memo", new SqlParameterValue(Types.CLOB, memo));
		dbClient.update(itflowDb, sql, p);
	}

	private long count(String sql, Map<String, Object> params) {
		List<CountRow> rows = dbClient.query(itflowDb, sql, params, CountRow.class);
		return rows.isEmpty() || rows.get(0).getCnt() == null ? 0 : rows.get(0).getCnt();
	}
}
