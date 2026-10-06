package com.mpx.infra_manager_java.dao.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：申請單主表與子表的唯讀查詢（S4）。
//           列表：只取 STATUS=1；篩選 status／priority／source／mine／q／from～to；「待我簽核」MINE_FLAG 在 SQL 算：
//             狀態 IN_REVIEW 且目前 PENDING 關卡（同一簽核實例中序號最小的 PENDING）的候選人含我，
//             沒有候選人時退而比對流程定義的指定簽核人（IM_FLOW_STEP.USER_ID）；
//             排序待我簽核置頂、再依建立時間新到舊；分頁 OFFSET／FETCH，每頁 AppListQuery.PAGE_SIZE。
//           WHERE 片段依條件有無拼接，但片段全是程式常數、值一律 :name 綁定；q 以 LIKE 比對單號、標題、作業主題、
//             申請人姓名（UPPER 後 contains，% _ \ 以 \ 跳脫）。表名經 DbSchema.table()。
//           S4 審查修正（Claude Opus 5.5，2026-10-06）：findOptions 的類別「其他」補充改為獨立一段 UNION ALL
//             （原本以子項的 FORM_OPTION_ID JOIN 指向類別的 IM_APP_CATG_OTHER，永遠撈不到）
//           S6 回合二 a（Claude Opus 5.5，2026-10-06）：findById 加 ROW_VER_NO、findOptions 加 FORM_OPTION_ID（編輯頁回填）
// ============================================================

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.mpx.common.db.DbClient;
import com.mpx.infra_manager_java.config.DbSchema;
import com.mpx.infra_manager_java.model.changerequest.AppListQuery;
import com.mpx.infra_manager_java.model.changerequest.AppRow;
import com.mpx.infra_manager_java.model.changerequest.CheckListRow;
import com.mpx.infra_manager_java.model.changerequest.CountRow;
import com.mpx.infra_manager_java.model.changerequest.EquipRow;
import com.mpx.infra_manager_java.model.changerequest.EventRow;
import com.mpx.infra_manager_java.model.changerequest.ExecRow;
import com.mpx.infra_manager_java.model.changerequest.OptionRow;
import com.mpx.infra_manager_java.model.changerequest.PlanStepRow;
import com.mpx.infra_manager_java.model.changerequest.VerRow;
import com.mpx.infra_manager_java.util.TaiwanTime;

@Repository
public class AppDao {

	private final DbClient dbClient;
	private final DbSchema schema;
	private final String itflowDb;

	public AppDao(DbClient dbClient, DbSchema schema, @Value("${db.connect.itflow}") String itflowDb) {
		this.dbClient = dbClient;
		this.schema = schema;
		this.itflowDb = itflowDb;
	}

	// ---------- 列表 ----------

	public List<AppRow> findList(AppListQuery q, String me) {
		String sql = "SELECT X.* FROM (SELECT A.APP_ID, A.APP_TITLE, A.PRIO_CODE, P.OPTION_NAME AS PRIO_NAME,"
				+ " P.COLOR_CODE AS PRIO_COLOR, A.WORK_SUBJ, A.APPLY_DEPT_NAME, U.USER_NAME AS APPLY_USER_NAME,"
				+ " A.CURR_VER_NO, A.APP_STATUS_CODE, A.SOURCE_CODE, A.CREATE_DATE, FS.STEP_NAME AS CURR_STEP_NAME,"
				+ " (SELECT COUNT(*) FROM " + schema.table("IM_APPR_CAND_MAP") + " C3"
				+ "   WHERE C3.APPR_STEP_ID = S.APPR_STEP_ID AND C3.STATUS = 1) AS CURR_STEP_CAND_CNT,"
				+ " (SELECT MIN(U3.USER_NAME) FROM " + schema.table("IM_APPR_CAND_MAP") + " C3"
				+ "   JOIN " + schema.table("IM_USER") + " U3 ON U3.USER_ID = C3.USER_ID"
				+ "   WHERE C3.APPR_STEP_ID = S.APPR_STEP_ID AND C3.STATUS = 1) AS CURR_STEP_CAND_NAME,"
				+ " " + mineExpr() + " AS MINE_FLAG"
				+ listFrom(q) + ") X WHERE (:mine = 0 OR X.MINE_FLAG = 1)"
				+ " ORDER BY X.MINE_FLAG DESC, X.CREATE_DATE DESC, X.APP_ID DESC"
				+ " OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY";
		Map<String, Object> params = listParams(q, me);
		params.put("offset", q.offset());
		params.put("size", AppListQuery.PAGE_SIZE);
		return dbClient.query(itflowDb, sql, params, AppRow.class);
	}

	public long countList(AppListQuery q, String me) {
		String sql = "SELECT COUNT(*) AS CNT FROM (SELECT " + mineExpr() + " AS MINE_FLAG" + listFrom(q)
				+ ") X WHERE (:mine = 0 OR X.MINE_FLAG = 1)";
		return count(sql, listParams(q, me));
	}

	/** 不套任何篩選的「待我簽核」總數 */
	public long countMine(String me) {
		AppListQuery none = AppListQuery.none();
		String sql = "SELECT COUNT(*) AS CNT FROM (SELECT " + mineExpr() + " AS MINE_FLAG" + listFrom(none)
				+ ") X WHERE X.MINE_FLAG = 1";
		return count(sql, listParams(none, me));
	}

	private String listFrom(AppListQuery q) {
		StringBuilder sb = new StringBuilder();
		sb.append(" FROM ").append(schema.table("IM_APP")).append(" A")
				.append(" JOIN ").append(schema.table("IM_USER")).append(" U ON U.USER_ID = A.APPLY_USER_ID")
				.append(" LEFT JOIN ").append(schema.table("IM_FORM_OPTION"))
				.append(" P ON P.GROUP_CODE = 'PRIO' AND P.OPTION_CODE = A.PRIO_CODE AND P.STATUS = 1")
				.append(" LEFT JOIN ").append(schema.table("IM_APPR"))
				.append(" R ON R.DOC_TYPE = 'CR' AND R.DOC_ID = A.APP_ID AND R.DOC_VER_NO = A.CURR_VER_NO")
				.append("   AND R.APPR_STATUS_CODE = 'PENDING' AND R.STATUS = 1")
				.append(" LEFT JOIN ").append(schema.table("IM_APPR_STEP"))
				.append(" S ON S.APPR_ID = R.APPR_ID AND S.STATUS = 1 AND S.STEP_STATUS_CODE = 'PENDING'")
				.append("   AND S.SEQ_NO = (SELECT MIN(S2.SEQ_NO) FROM ").append(schema.table("IM_APPR_STEP"))
				.append(" S2 WHERE S2.APPR_ID = R.APPR_ID AND S2.STATUS = 1 AND S2.STEP_STATUS_CODE = 'PENDING')")
				.append(" LEFT JOIN ").append(schema.table("IM_FLOW_STEP")).append(" FS ON FS.FLOW_STEP_ID = S.FLOW_STEP_ID")
				.append(" WHERE A.STATUS = 1");
		if (q.status() != null) {
			sb.append(" AND A.APP_STATUS_CODE = :status");
		}
		if (q.priority() != null) {
			sb.append(" AND A.PRIO_CODE = :priority");
		}
		if (q.source() != null) {
			sb.append(" AND A.SOURCE_CODE = :source");
		}
		if (q.from() != null) {
			sb.append(" AND A.CREATE_DATE >= :fromDate");
		}
		if (q.to() != null) {
			sb.append(" AND A.CREATE_DATE < :toDateExclusive");
		}
		if (q.q() != null) {
			sb.append(" AND (UPPER(A.APP_ID) LIKE :q ESCAPE '\\' OR UPPER(A.APP_TITLE) LIKE :q ESCAPE '\\'")
					.append(" OR UPPER(A.WORK_SUBJ) LIKE :q ESCAPE '\\' OR UPPER(U.USER_NAME) LIKE :q ESCAPE '\\')");
		}
		return sb.toString();
	}

	private String mineExpr() {
		String cand = schema.table("IM_APPR_CAND_MAP");
		return "CASE WHEN A.APP_STATUS_CODE = 'IN_REVIEW' AND S.APPR_STEP_ID IS NOT NULL AND ("
				+ "EXISTS (SELECT 1 FROM " + cand + " C WHERE C.APPR_STEP_ID = S.APPR_STEP_ID AND C.STATUS = 1 AND C.USER_ID = :me)"
				+ " OR (FS.USER_ID = :me AND NOT EXISTS (SELECT 1 FROM " + cand
				+ " C2 WHERE C2.APPR_STEP_ID = S.APPR_STEP_ID AND C2.STATUS = 1))) THEN 1 ELSE 0 END";
	}

	private Map<String, Object> listParams(AppListQuery q, String me) {
		Map<String, Object> params = new HashMap<>();
		params.put("me", me);
		params.put("mine", q.mine() ? 1 : 0);
		if (q.status() != null) {
			params.put("status", q.status());
		}
		if (q.priority() != null) {
			params.put("priority", q.priority());
		}
		if (q.source() != null) {
			params.put("source", q.source());
		}
		if (q.from() != null) {
			params.put("fromDate", TaiwanTime.startOf(q.from()));
		}
		if (q.to() != null) {
			params.put("toDateExclusive", TaiwanTime.startOf(q.to().plusDays(1)));
		}
		if (q.q() != null) {
			params.put("q", likePattern(q.q()));
		}
		return params;
	}

	/** UPPER 後前後加 %，並把 LIKE 的萬用字元與跳脫字元本身跳脫（ESCAPE '\'） */
	static String likePattern(String text) {
		StringBuilder sb = new StringBuilder("%");
		for (char c : text.toUpperCase(Locale.ROOT).toCharArray()) {
			if (c == '%' || c == '_' || c == '\\') {
				sb.append('\\');
			}
			sb.append(c);
		}
		return sb.append('%').toString();
	}

	private long count(String sql, Map<String, Object> params) {
		List<CountRow> rows = dbClient.query(itflowDb, sql, params, CountRow.class);
		return rows.isEmpty() || rows.get(0).getCnt() == null ? 0L : rows.get(0).getCnt();
	}

	// ---------- 檢視 ----------

	public Optional<AppRow> findById(String appId) {
		String sql = "SELECT A.APP_ID, A.APP_TITLE, A.PRIO_CODE, P.OPTION_NAME AS PRIO_NAME, P.COLOR_CODE AS PRIO_COLOR,"
				+ " A.FLOW_ID, F.FLOW_NAME, A.APPLY_USER_ID, U.USER_NAME AS APPLY_USER_NAME, A.APP_STATUS_CODE,"
				+ " A.SOURCE_CODE, A.CURR_VER_NO, A.ROW_VER_NO, A.APPLY_DATE, A.APPLY_DEPT_NAME, A.APPLY_TEL, A.APPLY_EMAIL,"
				+ " A.IS_SELF_EXEC, A.IS_SUP_EXEC, A.WORK_MODE_CODE, A.REMOTE_METHOD, A.SUP_NAME, A.SUP_CNTCT, A.SUP_TEL,"
				+ " A.SUP_HEAD_CNT, A.WORK_SUBJ, A.IMPACT_DESC, A.WORK_DETAIL, A.RISK_DESC, A.ROLL_BACK_PLAN, A.OTHER_REASON,"
				+ " A.SCHED_START_DATE, A.SCHED_END_DATE, A.EST_HOUR_QTY, A.LOC_SOURCE_CODE, A.AREA_NAME, A.RACK_NAME,"
				+ " A.U_RANGE AS UNIT_RANGE, A.SITE_ID, A.RACK_ID, A.U_START_NO AS UNIT_START_NO, A.U_END_NO AS UNIT_END_NO,"
				+ " A.OMIT_REASON, A.RESUB_MEMO, A.CREATE_DATE, A.UPDATE_DATE"
				+ " FROM " + schema.table("IM_APP") + " A"
				+ " JOIN " + schema.table("IM_USER") + " U ON U.USER_ID = A.APPLY_USER_ID"
				+ " LEFT JOIN " + schema.table("IM_FORM_OPTION")
				+ " P ON P.GROUP_CODE = 'PRIO' AND P.OPTION_CODE = A.PRIO_CODE AND P.STATUS = 1"
				+ " LEFT JOIN " + schema.table("IM_FLOW") + " F ON F.FLOW_ID = A.FLOW_ID"
				+ " WHERE A.APP_ID = :appId AND A.STATUS = 1";
		List<AppRow> rows = dbClient.query(itflowDb, sql, Map.of("appId", appId), AppRow.class);
		return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
	}

	/**
	 * 類別子項、類別「其他」補充、原因、影響範圍一次取回，依群組、排序號排序。
	 * IM_APP_CATG_MAP 指向 CATG_ITEM、IM_APP_CATG_OTHER 指向 CATG，兩者 FORM_OPTION_ID 不會相等，所以「其他」另成一段
	 */
	public List<OptionRow> findOptions(String appId) {
		String option = schema.table("IM_FORM_OPTION");
		String sql = "SELECT O.FORM_OPTION_ID, O.GROUP_CODE, O.OPTION_CODE, O.OPTION_NAME, PO.OPTION_CODE AS UP_OPTION_CODE,"
				+ " CAST(NULL AS VARCHAR2(500 CHAR)) AS OTHER_TEXT, O.SORT_NO"
				+ " FROM " + schema.table("IM_APP_CATG_MAP") + " M"
				+ " JOIN " + option + " O ON O.FORM_OPTION_ID = M.FORM_OPTION_ID"
				+ " LEFT JOIN " + option + " PO ON PO.FORM_OPTION_ID = O.UP_FORM_OPTION_ID"
				+ " WHERE M.APP_ID = :appId AND M.STATUS = 1"
				+ " UNION ALL"
				+ " SELECT O.FORM_OPTION_ID, O.GROUP_CODE, O.OPTION_CODE, O.OPTION_NAME, NULL, OT.OTHER_TEXT, O.SORT_NO"
				+ " FROM " + schema.table("IM_APP_CATG_OTHER") + " OT"
				+ " JOIN " + option + " O ON O.FORM_OPTION_ID = OT.FORM_OPTION_ID"
				+ " WHERE OT.APP_ID = :appId AND OT.STATUS = 1"
				+ " UNION ALL"
				+ " SELECT O.FORM_OPTION_ID, O.GROUP_CODE, O.OPTION_CODE, O.OPTION_NAME, NULL, NULL, O.SORT_NO"
				+ " FROM " + schema.table("IM_APP_REASON_MAP") + " M"
				+ " JOIN " + option + " O ON O.FORM_OPTION_ID = M.FORM_OPTION_ID"
				+ " WHERE M.APP_ID = :appId AND M.STATUS = 1"
				+ " UNION ALL"
				+ " SELECT O.FORM_OPTION_ID, O.GROUP_CODE, O.OPTION_CODE, O.OPTION_NAME, NULL, NULL, O.SORT_NO"
				+ " FROM " + schema.table("IM_APP_SCOPE_MAP") + " M"
				+ " JOIN " + option + " O ON O.FORM_OPTION_ID = M.FORM_OPTION_ID"
				+ " WHERE M.APP_ID = :appId AND M.STATUS = 1"
				+ " ORDER BY 2, 7, 3";
		return dbClient.query(itflowDb, sql, Map.of("appId", appId), OptionRow.class);
	}

	public List<EquipRow> findEquipments(String appId) {
		String sql = "SELECT SEQ_NO, EQUIP_NAME, ASSET_NO, MODEL_NO, SERIAL_NO, PURP_DESC, MGMT_IP FROM "
				+ schema.table("IM_APP_EQUIP") + " WHERE APP_ID = :appId AND STATUS = 1 ORDER BY SEQ_NO";
		return dbClient.query(itflowDb, sql, Map.of("appId", appId), EquipRow.class);
	}

	public List<PlanStepRow> findPlanSteps(String appId) {
		String sql = "SELECT SEQ_NO, STEP_TEXT FROM " + schema.table("IM_APP_PLAN_STEP")
				+ " WHERE APP_ID = :appId AND STATUS = 1 ORDER BY SEQ_NO";
		return dbClient.query(itflowDb, sql, Map.of("appId", appId), PlanStepRow.class);
	}

	public List<CheckListRow> findCheckList(String appId, int verNo) {
		String sql = "SELECT L.SEQ_NO, O.OPTION_CODE, O.OPTION_NAME, L.IS_DONE, L.DONE_DATE, L.USER_ID, U.USER_NAME,"
				+ " L.EXEC_USER_DESC FROM " + schema.table("IM_APP_CHECK_LIST") + " L"
				+ " JOIN " + schema.table("IM_FORM_OPTION") + " O ON O.FORM_OPTION_ID = L.FORM_OPTION_ID"
				+ " LEFT JOIN " + schema.table("IM_USER") + " U ON U.USER_ID = L.USER_ID"
				+ " WHERE L.APP_ID = :appId AND L.APP_VER_NO = :verNo AND L.STATUS = 1 ORDER BY L.SEQ_NO";
		return dbClient.query(itflowDb, sql, Map.of("appId", appId, "verNo", verNo), CheckListRow.class);
	}

	public Optional<ExecRow> findExec(String appId, int verNo) {
		String sql = "SELECT E.APP_VER_NO, E.ACTUAL_START_DATE, E.ACTUAL_END_DATE, E.RESULT_CODE, O.OPTION_NAME AS RESULT_NAME,"
				+ " E.IS_EXCPT, E.EXCPT_DESC, E.IS_FOLLOW_UP, E.FOLLOW_UP_DESC, E.EXEC_MEMO, E.USER_ID, U.USER_NAME, E.CLOSE_DATE"
				+ " FROM " + schema.table("IM_APP_EXEC") + " E"
				+ " LEFT JOIN " + schema.table("IM_FORM_OPTION")
				+ " O ON O.GROUP_CODE = 'EXEC_RESULT' AND O.OPTION_CODE = E.RESULT_CODE AND O.STATUS = 1"
				+ " LEFT JOIN " + schema.table("IM_USER") + " U ON U.USER_ID = E.USER_ID"
				+ " WHERE E.APP_ID = :appId AND E.APP_VER_NO = :verNo AND E.STATUS = 1";
		List<ExecRow> rows = dbClient.query(itflowDb, sql, Map.of("appId", appId, "verNo", verNo), ExecRow.class);
		return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
	}

	public List<VerRow> findVersions(String appId) {
		String sql = "SELECT APP_VER_NO, CLOSE_STATUS_CODE, VER_REASON, SNAP_DATE FROM " + schema.table("IM_APP_VER")
				+ " WHERE APP_ID = :appId AND STATUS = 1 ORDER BY APP_VER_NO";
		return dbClient.query(itflowDb, sql, Map.of("appId", appId), VerRow.class);
	}

	public List<EventRow> findEvents(String appId) {
		String sql = "SELECT E.APP_EVENT_ID, E.APP_VER_NO, E.EVENT_CODE, E.USER_ID, U.USER_NAME, E.EVENT_DATE, E.MEMO"
				+ " FROM " + schema.table("IM_APP_EVENT") + " E"
				+ " LEFT JOIN " + schema.table("IM_USER") + " U ON U.USER_ID = E.USER_ID"
				+ " WHERE E.APP_ID = :appId AND E.STATUS = 1 ORDER BY E.EVENT_DATE, E.APP_EVENT_ID";
		return dbClient.query(itflowDb, sql, Map.of("appId", appId), EventRow.class);
	}
}
