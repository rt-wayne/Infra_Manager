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
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: 修正：人名與狀態欄改用 AcctReqDao 的「"       " + 片段 + " AS 別名,\n"」串接寫法；
//           原寫法讓「 AS 別名」那行成為 text block 最短縮排，前導空白被剝掉而拼出 ENDAS（ORA-00905，S3 瀏覽器實測發現）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S4a：回報 UPDATE_REPORT（條件：版本相符、APPLY_BY＝本人、尚未回報、尚未處理；VERSION_NO＋1）、
//           受影響帳號 INSERT_AFFECT（只增不改；RECORD_BY 有效身分、CREATE_BY 實際操作人）
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S4b-1：主管處理 UPDATE_DECIDE（補核准與退回共用；條件：版本相符、尚未處理；不看逾期；
//           DECIDE_BY 有效身分、DECIDE_ACT_BY／UPDATE_BY 實際操作人、IS_SELF_DECIDE 由 Service 算；VERSION_NO＋1）
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S5：SELECT_BASE 多回送出的實際操作人 CREATE_BY，並對四個實際操作人
//           （CREATE_BY、REPORT_ACT_BY、DECIDE_ACT_BY、CLOSE_ACT_BY）各 LEFT JOIN IM_ACCT_USER 取姓名（UX／URA／UDA／UCA），
//           供詳情彈窗在實際操作人≠有效身分時顯示「{代理}（實際操作人：{實際}）」（S3 審查第 4 項、S5 裁示①）
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

	/**
	 * 主表全欄＋類別名、三種有效身分人名、四種實際操作人（送出 CREATE_BY、回報／處理／結案 *_ACT_BY）及其姓名、計算狀態；
	 * 列表、明細共用。姓名一律 LEFT JOIN IM_ACCT_USER：UA 申請人、UD 處理人、UC 結案人、UX 送出操作人、URA／UDA／UCA 三個節點的實際操作人
	 */
	public static final String SELECT_BASE = """
			SELECT E.EMG_ID, E.CATG_ID, C.CATG_NAME, E.TARGET_DESCR, E.APPLY_BY,
			""" + "       " + AcctReqDao.nameExpr("UA") + " AS APPLY_BY_NAME,\n" + """
			       E.CREATE_BY,
			""" + "       " + AcctReqDao.nameExpr("UX") + " AS CREATE_BY_NAME,\n" + """
			       E.APPLY_REASON, E.EXPECT_HOUR_QTY, E.SUBMIT_DATE, E.DUE_DATE,
			       E.REPORT_TYPE_CODE, E.REPORT_DESCR, E.REPORT_DATE, E.REPORT_ACT_BY,
			""" + "       " + AcctReqDao.nameExpr("URA") + " AS REPORT_ACT_BY_NAME,\n" + """
			       E.DECIDE_CODE, E.DECIDE_BY,
			""" + "       " + AcctReqDao.nameExpr("UD") + " AS DECIDE_BY_NAME,\n" + """
			       E.DECIDE_DATE, E.DECIDE_ACT_BY,
			""" + "       " + AcctReqDao.nameExpr("UDA") + " AS DECIDE_ACT_BY_NAME,\n" + """
			       E.RETURN_REASON, E.IS_SELF_DECIDE,
			       E.CLOSE_BY,
			""" + "       " + AcctReqDao.nameExpr("UC") + " AS CLOSE_BY_NAME,\n" + """
			       E.CLOSE_DATE, E.CLOSE_ACT_BY,
			""" + "       " + AcctReqDao.nameExpr("UCA") + " AS CLOSE_ACT_BY_NAME,\n" + """
			       E.CLOSE_DESCR, E.CLOSE_RESULT_CODE, E.IS_SELF_CLOSE,
			       E.VERSION_NO,
			""" + "       " + STATE_EXPR + " AS EMG_STATE_CODE\n" + """
			  FROM IM_EMG_ACCESS E
			  LEFT JOIN IM_ACCT_CATG C ON C.CATG_ID = E.CATG_ID
			  LEFT JOIN IM_ACCT_USER UA ON UA.USER_ID = E.APPLY_BY
			  LEFT JOIN IM_ACCT_USER UX ON UX.USER_ID = E.CREATE_BY
			  LEFT JOIN IM_ACCT_USER URA ON URA.USER_ID = E.REPORT_ACT_BY
			  LEFT JOIN IM_ACCT_USER UD ON UD.USER_ID = E.DECIDE_BY
			  LEFT JOIN IM_ACCT_USER UDA ON UDA.USER_ID = E.DECIDE_ACT_BY
			  LEFT JOIN IM_ACCT_USER UC ON UC.USER_ID = E.CLOSE_BY
			  LEFT JOIN IM_ACCT_USER UCA ON UCA.USER_ID = E.CLOSE_ACT_BY
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
			""" + "       " + AcctReqDao.nameExpr("UR") + " AS RECORD_BY_NAME,\n" + """
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

	/** 回報（S4a）：本人、尚未回報、主管尚未處理、版本相符才更新；VERSION_NO＋1（樂觀鎖，0 筆＝409） */
	public static final String UPDATE_REPORT = """
			UPDATE IM_EMG_ACCESS
			   SET REPORT_TYPE_CODE = :reportTypeCode, REPORT_DESCR = :reportDescr, REPORT_DATE = :now,
			       REPORT_ACT_BY = :actBy, VERSION_NO = VERSION_NO + 1, UPDATE_DATE = :now, UPDATE_BY = :actBy
			 WHERE EMG_ID = :emgId
			   AND STATUS = 1
			   AND VERSION_NO = :versionNo
			   AND APPLY_BY = :applyBy
			   AND REPORT_TYPE_CODE IS NULL
			   AND DECIDE_CODE IS NULL
			""";

	/** 主管處理（S4b）：尚未處理、版本相符才更新；逾期後仍可處理，所以不看 DUE_DATE；VERSION_NO＋1（樂觀鎖，0 筆＝409） */
	public static final String UPDATE_DECIDE = """
			UPDATE IM_EMG_ACCESS
			   SET DECIDE_CODE = :decideCode, DECIDE_BY = :decideBy, DECIDE_DATE = :now, DECIDE_ACT_BY = :actBy,
			       RETURN_REASON = :returnReason, IS_SELF_DECIDE = :isSelfDecide, VERSION_NO = VERSION_NO + 1,
			       UPDATE_DATE = :now, UPDATE_BY = :actBy
			 WHERE EMG_ID = :emgId
			   AND STATUS = 1
			   AND VERSION_NO = :versionNo
			   AND DECIDE_CODE IS NULL
			""";

	/** 受影響帳號（只增不改）：RECORD_BY＝有效身分、CREATE_BY＝實際操作人 */
	public static final String INSERT_AFFECT = """
			INSERT INTO IM_EMG_AFFECT_ACCT (EMG_ID, AFFECT_TYPE_CODE, ACCT_ID, AFFECT_DESCR, RECORD_BY, RECORD_DATE,
			       STATUS, CREATE_DATE, CREATE_BY)
			VALUES (:emgId, :affectTypeCode, :acctId, :affectDescr, :recordBy, :now,
			       1, :now, :createBy)
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

	/** 回報；applyBy＝有效身分（須等於單上 APPLY_BY），actBy＝實際操作人；回傳更新筆數（0＝版本或狀態不符） */
	public int report(String emgId, int versionNo, String applyBy, String reportTypeCode, String reportDescr,
			LocalDateTime now, String actBy) {
		Map<String, Object> params = new HashMap<>();
		params.put("emgId", emgId);
		params.put("versionNo", versionNo);
		params.put("applyBy", applyBy);
		params.put("reportTypeCode", reportTypeCode);
		params.put("reportDescr", reportDescr);
		params.put("now", DaoSupport.ts(now));
		params.put("actBy", actBy);
		return dbClient.update(itflowDb, UPDATE_REPORT, params);
	}

	/**
	 * 主管處理；decideBy＝有效身分，actBy＝實際操作人；returnReason 補核准時為 null；isSelfDecide 1＝處理人即申請人；
	 * 回傳更新筆數（0＝已處理或版本不符）
	 */
	public int decide(String emgId, int versionNo, String decideCode, String decideBy, String returnReason, int isSelfDecide,
			LocalDateTime now, String actBy) {
		Map<String, Object> params = new HashMap<>();
		params.put("emgId", emgId);
		params.put("versionNo", versionNo);
		params.put("decideCode", decideCode);
		params.put("decideBy", decideBy);
		params.put("returnReason", returnReason);
		params.put("isSelfDecide", isSelfDecide);
		params.put("now", DaoSupport.ts(now));
		params.put("actBy", actBy);
		return dbClient.update(itflowDb, UPDATE_DECIDE, params);
	}

	public int insertAffect(String emgId, String affectTypeCode, String acctId, String affectDescr, String recordBy,
			LocalDateTime now, String createBy) {
		Map<String, Object> params = new HashMap<>();
		params.put("emgId", emgId);
		params.put("affectTypeCode", affectTypeCode);
		params.put("acctId", acctId);
		params.put("affectDescr", affectDescr);
		params.put("recordBy", recordBy);
		params.put("now", DaoSupport.ts(now));
		params.put("createBy", createBy);
		return dbClient.update(itflowDb, INSERT_AFFECT, params);
	}
}
