package com.mpx.im_acct_insp.dao;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: 新增：緊急存取 IM_EMG_ACCESS／IM_EMG_AFFECT_ACCT 存取（BACKLOG 第 46 項 S2；SA §5.11）
//           計算狀態不存欄位：STATE_EXPR 以 :now 綁參數算 PENDING／OVERDUE／APPROVED／RETURNED／CLOSED，列表、明細、篩選共用
//           （S7 逾期掃描、S8 統計同一口徑）；Oracle WHERE 不能用別名，FIND_LIST 以子查詢 X 包住後再篩；
//           人名取 IM_ACCT_USER（申請人 UA、處理人 UD、結案人 UC）、類別名取 IM_ACCT_CATG；
//           關鍵字比對單號、申請人姓名（英中文）、帳號或系統，LIKE 加 ESCAPE '\'（關鍵字由 Service 跳脫）；
//           日期區間依 SUBMIT_DATE 含頭尾（:dateFrom ≤ SUBMIT_DATE < :dateToNext）；:applyBy 為空＝不限申請人（EMG_VIEW_ALL）
//           INSERT 只寫送出欄位：IS_SELF_DECIDE／IS_SELF_CLOSE＝0、VERSION_NO＝0、STATUS＝1；時間一律綁參數
// ============================================================

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.mpx.common.db.DbClient;
import com.mpx.im_acct_insp.model.Emg;
import com.mpx.im_acct_insp.model.EmgAffectAcct;
import com.mpx.im_acct_insp.model.EmgDetail;

@Repository
public class EmgDao {

	/** 計算狀態（SA §5.11）：未處理且未逾期 PENDING、未處理已逾期 OVERDUE、已補核准 APPROVED、退回未結案 RETURNED、退回已結案 CLOSED */
	public static final String STATE_EXPR = """
			CASE
			         WHEN E.DECIDE_CODE IS NULL AND E.DUE_DATE >= :now THEN 'PENDING'
			         WHEN E.DECIDE_CODE IS NULL THEN 'OVERDUE'
			         WHEN E.DECIDE_CODE = 'APPROVE' THEN 'APPROVED'
			         WHEN E.CLOSE_DATE IS NULL THEN 'RETURNED'
			         ELSE 'CLOSED'
			       END""";

	/** 主表全欄＋類別名、三種人名、計算狀態；列表、明細共用 */
	public static final String SELECT_BASE = """
			SELECT E.EMG_ID, E.CATG_ID, C.CATG_NAME, E.TARGET_DESCR, E.APPLY_BY,
			       """ + AcctReqDao.nameExpr("UA") + """
			 AS APPLY_BY_NAME,
			       E.APPLY_REASON, E.EXPECT_HOUR_QTY, E.SUBMIT_DATE, E.DUE_DATE,
			       E.REPORT_TYPE_CODE, E.REPORT_DESCR, E.REPORT_DATE, E.REPORT_ACT_BY,
			       E.DECIDE_CODE, E.DECIDE_BY,
			       """ + AcctReqDao.nameExpr("UD") + """
			 AS DECIDE_BY_NAME,
			       E.DECIDE_DATE, E.DECIDE_ACT_BY, E.RETURN_REASON, E.IS_SELF_DECIDE,
			       E.CLOSE_BY,
			       """ + AcctReqDao.nameExpr("UC") + """
			 AS CLOSE_BY_NAME,
			       E.CLOSE_DATE, E.CLOSE_ACT_BY, E.CLOSE_DESCR, E.CLOSE_RESULT_CODE, E.IS_SELF_CLOSE,
			       E.VERSION_NO,
			       """ + STATE_EXPR + """
			 AS EMG_STATE_CODE
			  FROM IM_EMG_ACCESS E
			  LEFT JOIN IM_ACCT_CATG C ON C.CATG_ID = E.CATG_ID
			  LEFT JOIN IM_ACCT_USER UA ON UA.USER_ID = E.APPLY_BY
			  LEFT JOIN IM_ACCT_USER UD ON UD.USER_ID = E.DECIDE_BY
			  LEFT JOIN IM_ACCT_USER UC ON UC.USER_ID = E.CLOSE_BY
			 WHERE E.STATUS = 1
			""";

	public static final String FIND_LIST = """
			SELECT X.*
			  FROM (""" + SELECT_BASE + """
			       ) X
			 WHERE (:applyBy IS NULL OR X.APPLY_BY = :applyBy)
			   AND (:stateCode IS NULL OR X.EMG_STATE_CODE = :stateCode)
			   AND (:keyword IS NULL
			        OR UPPER(X.EMG_ID) LIKE '%' || UPPER(:keyword) || '%' ESCAPE '\\'
			        OR UPPER(X.APPLY_BY_NAME) LIKE '%' || UPPER(:keyword) || '%' ESCAPE '\\'
			        OR UPPER(X.TARGET_DESCR) LIKE '%' || UPPER(:keyword) || '%' ESCAPE '\\')
			   AND (:dateFrom IS NULL OR X.SUBMIT_DATE >= :dateFrom)
			   AND (:dateToNext IS NULL OR X.SUBMIT_DATE < :dateToNext)
			 ORDER BY X.SUBMIT_DATE DESC, X.EMG_ID DESC
			""";

	public static final String FIND_BY_ID = SELECT_BASE + """
			   AND E.EMG_ID = :emgId
			""";

	public static final String FIND_AFFECTS = """
			SELECT A.EMG_ID, A.AFFECT_TYPE_CODE, A.ACCT_ID, T.ACCT_NAME, T.SYS_NAME, T.CATG_ID, A.AFFECT_DESCR,
			       A.RECORD_BY,
			       """ + AcctReqDao.nameExpr("UR") + """
			 AS RECORD_BY_NAME,
			       A.RECORD_DATE
			  FROM IM_EMG_AFFECT_ACCT A
			  LEFT JOIN IM_ACCT T ON T.ACCT_ID = A.ACCT_ID
			  LEFT JOIN IM_ACCT_USER UR ON UR.USER_ID = A.RECORD_BY
			 WHERE A.EMG_ID = :emgId
			   AND A.STATUS = 1
			 ORDER BY A.AFFECT_TYPE_CODE, A.ACCT_ID
			""";

	public static final String INSERT_EMG = """
			INSERT INTO IM_EMG_ACCESS (EMG_ID, CATG_ID, TARGET_DESCR, APPLY_BY, APPLY_REASON, EXPECT_HOUR_QTY, SUBMIT_DATE, DUE_DATE,
			       IS_SELF_DECIDE, IS_SELF_CLOSE, VERSION_NO, STATUS, CREATE_DATE, CREATE_BY)
			VALUES (:emgId, :catgId, :targetDescr, :applyBy, :applyReason, :expectHourQty, :now, :dueDate,
			       0, 0, 0, 1, :now, :createBy)
			""";

	private final DbClient dbClient;
	private final String itflowDb;

	public EmgDao(DbClient dbClient, @Value("${db.connect.itflow}") String itflowDb) {
		this.dbClient = dbClient;
		this.itflowDb = itflowDb;
	}

	/** 送出：e 的 submitDate 同時作為 CREATE_DATE；createBy＝實際操作人 */
	public int insert(Emg e, String createBy) {
		Map<String, Object> params = new HashMap<>();
		params.put("emgId", e.getEmgId());
		params.put("catgId", e.getCatgId());
		params.put("targetDescr", e.getTargetDescr());
		params.put("applyBy", e.getApplyBy());
		params.put("applyReason", e.getApplyReason());
		params.put("expectHourQty", e.getExpectHourQty());
		params.put("now", DaoSupport.ts(e.getSubmitDate()));
		params.put("dueDate", DaoSupport.ts(e.getDueDate()));
		params.put("createBy", createBy);
		return dbClient.update(itflowDb, INSERT_EMG, params);
	}

	/** applyBy 為 null＝不限申請人；keyword 已跳脫；dateFrom／dateTo 含頭尾，null 表示不限 */
	public List<Emg> findList(String applyBy, String keyword, String stateCode, LocalDate dateFrom, LocalDate dateTo,
			LocalDateTime now) {
		Map<String, Object> params = new HashMap<>();
		params.put("applyBy", applyBy);
		params.put("keyword", keyword);
		params.put("stateCode", stateCode);
		params.put("dateFrom", DaoSupport.ts(dateFrom));
		params.put("dateToNext", DaoSupport.ts(dateTo == null ? null : dateTo.plusDays(1)));
		params.put("now", DaoSupport.ts(now));
		return dbClient.query(itflowDb, FIND_LIST, params, Emg.class);
	}

	public EmgDetail findById(String emgId, LocalDateTime now) {
		Map<String, Object> params = new HashMap<>();
		params.put("emgId", emgId);
		params.put("now", DaoSupport.ts(now));
		return DaoSupport.first(dbClient.query(itflowDb, FIND_BY_ID, params, EmgDetail.class));
	}

	public List<EmgAffectAcct> findAffects(String emgId) {
		Map<String, Object> params = new HashMap<>();
		params.put("emgId", emgId);
		return dbClient.query(itflowDb, FIND_AFFECTS, params, EmgAffectAcct.class);
	}
}
