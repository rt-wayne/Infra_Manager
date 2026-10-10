package com.mpx.im_acct_insp.dao;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：帳號歷史 IM_ACCT_HIST（spec §3.6）：只新增不修改，HIST_ID 取 SEQ_IM_ACCT_HIST.NEXTVAL，
//           SOURCE_CODE 固定 REQ（簽核流程事件）；查詢依 ACT_DATE、HIST_ID 排序，附 actByName、stageName
//           測試第 1 輪修正：IM_USER 改 schema 限定名稱（引用 AcctConst.EXISTING_USER_TABLE）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（spec_im_acct_insp_v2.0.md §4.8；Table v0.2 7.17）：寫入加 SOURCE_CODE（REQ／SYS）、ACT_BY_NAME、ACT_AS_ID、
//           ACT_AS_NAME、IS_SELF_ACTION、ACT_DATE（:now）、CREATE_DATE；查詢改用快照姓名（不 JOIN 使用者表），
//           附該單申請人 REQ_APPLY_BY（Service 算自行核准／同人兼任標籤用）；
//           新增「曾在此單操作過」計數（spec §2.4 可看申請單第 4 條）與 IS_SELF_ACTION 判斷用的歷次動作查詢
// ============================================================

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.mpx.common.db.DbClient;
import com.mpx.im_acct_insp.common.AcctConst;
import com.mpx.im_acct_insp.model.AcctHist;
import com.mpx.im_acct_insp.model.CountRow;

@Repository
public class AcctHistDao {

	public static final String INSERT_HIST = """
			INSERT INTO IM_ACCT_HIST (HIST_ID, ACCT_ID, REQ_ID, SOURCE_CODE, ACTION_CODE, STAGE_ID, ACT_BY, ACT_BY_NAME,
			       ACT_AS_ID, ACT_AS_NAME, ACT_DATE, APPLY_DEPT, RESULT_DESCR, IS_SELF_ACTION, CREATE_DATE, CREATE_BY)
			VALUES (SEQ_IM_ACCT_HIST.NEXTVAL, :acctId, :reqId, :sourceCode, :actionCode, :stageId, :actBy, :actByName,
			       :actAsId, :actAsName, :actDate, :applyDept, :resultDescr, :isSelfAction, :actDate, :createBy)
			""";

	static final String SELECT_BASE = """
			SELECT H.HIST_ID, H.ACCT_ID, H.REQ_ID, H.SOURCE_CODE, H.ACTION_CODE, H.STAGE_ID, S.STAGE_NAME, H.ACT_BY,
			       H.ACT_BY_NAME, H.ACT_AS_ID, H.ACT_AS_NAME, H.ACT_DATE, H.APPLY_DEPT, H.RESULT_DESCR, H.IS_SELF_ACTION,
			       R.APPLY_BY AS REQ_APPLY_BY
			  FROM IM_ACCT_HIST H
			  LEFT JOIN IM_ACCT_APPR_STAGE S ON S.STAGE_ID = H.STAGE_ID
			  LEFT JOIN IM_ACCT_REQ R ON R.REQ_ID = H.REQ_ID
			""";

	public static final String FIND_BY_ACCT = SELECT_BASE + """
			 WHERE H.ACCT_ID = :acctId
			   AND H.STATUS = 1
			 ORDER BY H.ACT_DATE, H.HIST_ID
			""";

	public static final String FIND_BY_REQ = SELECT_BASE + """
			 WHERE H.REQ_ID = :reqId
			   AND H.STATUS = 1
			 ORDER BY H.ACT_DATE, H.HIST_ID
			""";

	/** 同一申請單上需判斷 IS_SELF_ACTION 的歷次動作（spec §4.8 第 2 條） */
	public static final String FIND_SELF_CHECK_ACTS = SELECT_BASE + """
			 WHERE H.REQ_ID = :reqId
			   AND H.ACTION_CODE IN (:actionCodes)
			 ORDER BY H.ACT_DATE, H.HIST_ID
			""";

	/** 我曾在這張單上操作過（ACT_BY 或 ACT_AS_ID＝我；spec §2.4 可看申請單第 4 條） */
	public static final String COUNT_REQ_ACTOR = """
			SELECT COUNT(*) AS CNT
			  FROM IM_ACCT_HIST
			 WHERE REQ_ID = :reqId
			   AND (ACT_BY = :userId OR ACT_AS_ID = :userId)
			""";

	private final DbClient dbClient;
	private final String itflowDb;

	public AcctHistDao(DbClient dbClient, @Value("${db.connect.itflow}") String itflowDb) {
		this.dbClient = dbClient;
		this.itflowDb = itflowDb;
	}

	/** h 需備齊 acctId、reqId、sourceCode、actionCode、stageId、actBy、actByName、actAsId、actAsName、actDate、applyDept、
	 *  resultDescr、isSelfAction、createBy */
	public int insert(AcctHist h) {
		Map<String, Object> params = new HashMap<>();
		params.put("acctId", h.getAcctId());
		params.put("reqId", h.getReqId());
		params.put("sourceCode", h.getSourceCode());
		params.put("actionCode", h.getActionCode());
		params.put("stageId", h.getStageId());
		params.put("actBy", h.getActBy());
		params.put("actByName", h.getActByName());
		params.put("actAsId", h.getActAsId());
		params.put("actAsName", h.getActAsName());
		params.put("actDate", DaoSupport.ts(h.getActDate()));
		params.put("applyDept", h.getApplyDept());
		params.put("resultDescr", h.getResultDescr());
		params.put("isSelfAction", h.getIsSelfAction() == null ? 0 : h.getIsSelfAction());
		params.put("createBy", h.getCreateBy());
		return dbClient.update(itflowDb, INSERT_HIST, params);
	}

	public List<AcctHist> findByAcct(String acctId) {
		Map<String, Object> params = new HashMap<>();
		params.put("acctId", acctId);
		return dbClient.query(itflowDb, FIND_BY_ACCT, params, AcctHist.class);
	}

	public List<AcctHist> findByReq(String reqId) {
		Map<String, Object> params = new HashMap<>();
		params.put("reqId", reqId);
		return dbClient.query(itflowDb, FIND_BY_REQ, params, AcctHist.class);
	}

	public List<AcctHist> findSelfCheckActs(String reqId) {
		Map<String, Object> params = new HashMap<>();
		params.put("reqId", reqId);
		params.put("actionCodes", AcctConst.SELF_CHECK_ACTIONS);
		return dbClient.query(itflowDb, FIND_SELF_CHECK_ACTS, params, AcctHist.class);
	}

	public long countReqActor(String reqId, String userId) {
		Map<String, Object> params = new HashMap<>();
		params.put("reqId", reqId);
		params.put("userId", userId);
		return DaoSupport.count(dbClient.query(itflowDb, COUNT_REQ_ACTOR, params, CountRow.class));
	}
}
