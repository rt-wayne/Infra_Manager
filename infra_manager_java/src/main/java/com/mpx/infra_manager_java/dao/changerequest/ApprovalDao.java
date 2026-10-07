package com.mpx.infra_manager_java.dao.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：簽核實例、關卡與候選人的唯讀查詢（S4）。
//           findCurrent 取申請單目前版次的簽核實例（排除已收回／已取消，照 UK 應只有一筆）；
//           findFlowSteps 在沒有實例時以流程定義展開關卡（草稿、已收回），狀態一律 WAITING
//           2026-10-07 S9 R2（Claude Opus 5.5）：加 findHistory（目前實例以外的歷次簽核，含撤回那一輪，順便處理第 101 項 ⑤）
// ============================================================

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.mpx.common.db.DbClient;
import com.mpx.infra_manager_java.config.DbSchema;
import com.mpx.infra_manager_java.model.changerequest.ApprHistoryRow;
import com.mpx.infra_manager_java.model.changerequest.ApprRow;
import com.mpx.infra_manager_java.model.changerequest.ApprStepRow;
import com.mpx.infra_manager_java.model.changerequest.CandRow;

@Repository
public class ApprovalDao {

	private final DbClient dbClient;
	private final DbSchema schema;
	private final String itflowDb;

	public ApprovalDao(DbClient dbClient, DbSchema schema, @Value("${db.connect.itflow}") String itflowDb) {
		this.dbClient = dbClient;
		this.schema = schema;
		this.itflowDb = itflowDb;
	}

	public Optional<ApprRow> findCurrent(String appId, int verNo) {
		String sql = "SELECT APPR_ID, DOC_VER_NO, FLOW_ID, APPR_STATUS_CODE, START_DATE, CLOSE_DATE FROM "
				+ schema.table("IM_APPR")
				+ " WHERE DOC_TYPE = 'CR' AND DOC_ID = :appId AND DOC_VER_NO = :verNo AND STATUS = 1"
				+ " AND APPR_STATUS_CODE NOT IN ('RECALLED', 'CANCELLED') ORDER BY APPR_ID DESC";
		List<ApprRow> rows = dbClient.query(itflowDb, sql, Map.of("appId", appId, "verNo", verNo), ApprRow.class);
		return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
	}

	public List<ApprStepRow> findSteps(long apprId) {
		String sql = "SELECT S.APPR_STEP_ID, S.SEQ_NO, FS.STEP_CODE, FS.STEP_NAME, FS.STEP_MODE_CODE, FS.APPR_TYPE,"
				+ " FS.USER_ID AS FLOW_USER_ID, FS.ROLE_ID, FS.IS_NOTIFY_ONLY, S.STEP_STATUS_CODE, S.DECIDE_DATE,"
				+ " S.USER_ID, U.USER_NAME, S.MEMO"
				+ " FROM " + schema.table("IM_APPR_STEP") + " S"
				+ " JOIN " + schema.table("IM_FLOW_STEP") + " FS ON FS.FLOW_STEP_ID = S.FLOW_STEP_ID"
				+ " LEFT JOIN " + schema.table("IM_USER") + " U ON U.USER_ID = S.USER_ID"
				+ " WHERE S.APPR_ID = :apprId AND S.STATUS = 1 ORDER BY S.SEQ_NO";
		return dbClient.query(itflowDb, sql, Map.of("apprId", apprId), ApprStepRow.class);
	}

	public List<CandRow> findCandidates(long apprId) {
		String sql = "SELECT C.APPR_STEP_ID, C.USER_ID, U.USER_NAME"
				+ " FROM " + schema.table("IM_APPR_CAND_MAP") + " C"
				+ " JOIN " + schema.table("IM_APPR_STEP") + " S ON S.APPR_STEP_ID = C.APPR_STEP_ID"
				+ " LEFT JOIN " + schema.table("IM_USER") + " U ON U.USER_ID = C.USER_ID"
				+ " WHERE S.APPR_ID = :apprId AND C.STATUS = 1 AND S.STATUS = 1 ORDER BY S.SEQ_NO, U.USER_NAME, C.USER_ID";
		return dbClient.query(itflowDb, sql, Map.of("apprId", apprId), CandRow.class);
	}

	/**
	 * 歷次簽核：該單目前那筆以外的所有簽核實例（含已退件、已撤回、已取消）與各自關卡，一次 JOIN、依實例與關卡序排序；
	 * excludeApprId 為 null 表示沒有目前實例（草稿、已撤回），全部列出
	 */
	public List<ApprHistoryRow> findHistory(String appId, Long excludeApprId) {
		String sql = "SELECT R.APPR_ID, R.DOC_VER_NO, R.APPR_STATUS_CODE, R.START_DATE AS APPR_START_DATE,"
				+ " R.CLOSE_DATE AS APPR_CLOSE_DATE, S.APPR_STEP_ID, S.SEQ_NO, FS.STEP_CODE, FS.STEP_NAME, FS.STEP_MODE_CODE,"
				+ " FS.APPR_TYPE, FS.USER_ID AS FLOW_USER_ID, FS.ROLE_ID, FS.IS_NOTIFY_ONLY, S.STEP_STATUS_CODE, S.DECIDE_DATE,"
				+ " S.USER_ID, U.USER_NAME, S.MEMO"
				+ " FROM " + schema.table("IM_APPR") + " R"
				+ " JOIN " + schema.table("IM_APPR_STEP") + " S ON S.APPR_ID = R.APPR_ID AND S.STATUS = 1"
				+ " JOIN " + schema.table("IM_FLOW_STEP") + " FS ON FS.FLOW_STEP_ID = S.FLOW_STEP_ID"
				+ " LEFT JOIN " + schema.table("IM_USER") + " U ON U.USER_ID = S.USER_ID"
				+ " WHERE R.DOC_TYPE = 'CR' AND R.DOC_ID = :appId AND R.STATUS = 1 AND R.APPR_ID <> :excludeId"
				+ " ORDER BY R.APPR_ID, S.SEQ_NO";
		// 沒有要排除的實例時用 -1（IDENTITY 從 1 起），避免綁 null 撞驅動的型別推斷
		long excludeId = excludeApprId == null ? -1L : excludeApprId;
		return dbClient.query(itflowDb, sql, Map.of("appId", appId, "excludeId", excludeId), ApprHistoryRow.class);
	}

	public List<ApprStepRow> findFlowSteps(String flowId) {
		String sql = "SELECT CAST(NULL AS NUMBER(19)) AS APPR_STEP_ID, FS.SEQ_NO, FS.STEP_CODE, FS.STEP_NAME, FS.STEP_MODE_CODE,"
				+ " FS.APPR_TYPE, FS.USER_ID AS FLOW_USER_ID, FS.ROLE_ID, FS.IS_NOTIFY_ONLY, 'WAITING' AS STEP_STATUS_CODE,"
				+ " CAST(NULL AS DATE) AS DECIDE_DATE, CAST(NULL AS VARCHAR2(30)) AS USER_ID,"
				+ " CAST(NULL AS VARCHAR2(50 CHAR)) AS USER_NAME, CAST(NULL AS VARCHAR2(1)) AS MEMO"
				+ " FROM " + schema.table("IM_FLOW_STEP") + " FS WHERE FS.FLOW_ID = :flowId AND FS.STATUS = 1 ORDER BY FS.SEQ_NO";
		return dbClient.query(itflowDb, sql, Map.of("flowId", flowId), ApprStepRow.class);
	}
}
