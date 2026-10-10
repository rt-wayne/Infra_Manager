package com.mpx.im_acct_insp.dao;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：帳號申請單 IM_ACCT_REQ 存取（spec §3.2～§3.4、§4.3）
//           狀態轉移 UPDATE 一律帶 REQ_STATE_CODE＝:expectState 與 CURR_STAGE_ID 條件（樂觀鎖），影響 0 筆由 Service 回 409；
//           參數名統一 reqId／expectState／expectStage／newState／newStage／userId
//           送出用 INSERT … SELECT … FROM DUAL WHERE NOT EXISTS（同帳號在途），重新送出的 UPDATE 也帶同條件，
//           把「檢查在途」與「寫入」合成單句，縮小無交易下的競態窗口
//           變更單 #1：COUNT_APPLIED_BY 加上「對其 ORIG_ACCT_ID 送過申請者」（展延申請人可看新帳號）
//           測試第 1 輪修正：IM_USER 改 schema 限定名稱（引用 AcctConst.EXISTING_USER_TABLE）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（spec_im_acct_insp_v2.0.md §4.4～§4.6、§5、§6.5；Table v0.2 7.15）：
//           欄位依新表（刪 ORIG_REQ_ID、APPLY_TEL_NO、APPLY_EMAIL；加 EFFECT_DATE、REJECT_CNT、VERSION_NO）；
//           送出改一般 INSERT（在交易內），同帳號在途由唯一函式索引 IND_IM_ACCT_REQ_04 擋下（ORA-00001 由 Service 轉 409 E07）；
//           所有狀態轉移 UPDATE 帶 REQ_ID、VERSION_NO、REQ_STATE_CODE、CURR_STAGE_ID 條件並 VERSION_NO＋1（樂觀鎖）；
//           業務欄位（APPLY_BY、REJECT_BY、EXEC_BY、VERIFY_BY）記有效身分（:actorId），UPDATE_BY 記實際使用者（:userId）；
//           姓名改 JOIN IM_ACCT_USER（不再引用既有模組使用者表）；時間一律綁定 :now；刪展延作廢 SQL
// ============================================================

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.mpx.common.db.DbClient;
import com.mpx.im_acct_insp.model.AcctReq;
import com.mpx.im_acct_insp.model.AcctReqDetail;
import com.mpx.im_acct_insp.model.CountRow;
import com.mpx.im_acct_insp.model.ReqListItem;
import com.mpx.im_acct_insp.model.TodoItem;

@Repository
public class AcctReqDao {

	/** 人員顯示名（英文名 (中文名)）；u 為 IM_ACCT_USER 別名 */
	static String nameExpr(String u) {
		return "CASE WHEN " + u + ".USER_ID IS NULL THEN NULL WHEN " + u + ".USER_NAME IS NULL THEN " + u + ".USER_EN_NAME ELSE "
				+ u + ".USER_EN_NAME || ' (' || " + u + ".USER_NAME || ')' END";
	}

	static final String SELECT_BASE = """
			SELECT R.REQ_ID, R.ACCT_ID, R.REQ_TYPE_CODE, R.REQ_STATE_CODE, R.CURR_STAGE_ID, R.APPLY_BY, R.APPLY_DEPT, R.APPLY_DATE,
			       R.CATG_ID, R.ACCT_NAME, R.SYS_NAME, R.DOOR_SCOPE_CODE, R.ASSET_CLASS_ID, R.IS_PRIV,
			       R.HOLDER_NAME, R.HOLDER_DEPT, R.HOLDER_CNTCT, R.HOLDER_EMAIL, R.ACCESS_LEVEL_CODE, R.ACCESS_TARGET,
			       R.APPLY_REASON, R.EXPIRY_TYPE_CODE, R.EXPIRY_DATE, R.IS_UNIQUE_CONF, R.SUBMIT_DATE, R.RESUB_CNT, R.REJECT_CNT,
			       R.REJECT_STAGE_ID, R.REJECT_BY, R.REJECT_DATE, R.REJECT_REASON, R.EXEC_BY, R.EXEC_DATE, R.EXEC_DESCR, R.CARD_NO,
			       R.EFFECT_DATE, R.VERIFY_BY, R.VERIFY_DATE, R.VERIFY_REJECT_CNT, R.VERIFY_REJECT_REASON, R.DONE_DATE,
			       R.VERSION_NO, R.STATUS, R.CREATE_DATE, R.CREATE_BY, R.UPDATE_DATE, R.UPDATE_BY,
			       S.STAGE_NAME, C.CATG_NAME, AC.ASSET_CLASS_NAME,
			""" + "       " + nameExpr("UA") + " AS APPLY_BY_NAME,\n"
			+ "       " + nameExpr("UE") + " AS EXEC_BY_NAME,\n"
			+ "       " + nameExpr("UV") + " AS VERIFY_BY_NAME\n" + """
			  FROM IM_ACCT_REQ R
			  LEFT JOIN IM_ACCT_APPR_STAGE S ON S.STAGE_ID = R.CURR_STAGE_ID
			  LEFT JOIN IM_ACCT_CATG C ON C.CATG_ID = R.CATG_ID
			  LEFT JOIN IM_ASSET_CLASS AC ON AC.ASSET_CLASS_ID = R.ASSET_CLASS_ID
			  LEFT JOIN IM_ACCT_USER UA ON UA.USER_ID = R.APPLY_BY
			  LEFT JOIN IM_ACCT_USER UE ON UE.USER_ID = R.EXEC_BY
			  LEFT JOIN IM_ACCT_USER UV ON UV.USER_ID = R.VERIFY_BY
			""";

	public static final String FIND_BY_ID = SELECT_BASE + """
			 WHERE R.REQ_ID = :reqId
			""";

	public static final String FIND_MINE = """
			SELECT R.REQ_ID, R.ACCT_ID, R.REQ_TYPE_CODE, R.REQ_STATE_CODE, R.CURR_STAGE_ID, S.STAGE_NAME, R.CATG_ID, C.CATG_NAME,
			       R.ACCT_NAME, R.SYS_NAME, R.APPLY_DATE, R.SUBMIT_DATE, R.REJECT_REASON, R.APPLY_BY
			  FROM IM_ACCT_REQ R
			  LEFT JOIN IM_ACCT_APPR_STAGE S ON S.STAGE_ID = R.CURR_STAGE_ID
			  LEFT JOIN IM_ACCT_CATG C ON C.CATG_ID = R.CATG_ID
			 WHERE R.APPLY_BY = :userId
			   AND (:stateCode IS NULL OR R.REQ_STATE_CODE = :stateCode)
			 ORDER BY R.SUBMIT_DATE DESC, R.REQ_ID DESC
			""";

	/** 某帳號目前的在途申請單（同帳號至多一筆，IND_IM_ACCT_REQ_04） */
	public static final String FIND_IN_FLIGHT_BY_ACCT = SELECT_BASE + """
			 WHERE R.ACCT_ID = :acctId
			   AND R.REQ_STATE_CODE IN ('REVIEW', 'EXEC', 'VERIFY')
			""";

	public static final String COUNT_IN_FLIGHT = """
			SELECT COUNT(*) AS CNT
			  FROM IM_ACCT_REQ
			 WHERE ACCT_ID = :acctId
			   AND REQ_STATE_CODE IN ('REVIEW', 'EXEC', 'VERIFY')
			   AND REQ_ID <> NVL(:excludeReqId, '-')
			""";

	/** 我曾對該帳號送過申請，或曾在該帳號的申請單上操作過（spec §2.4 可看帳號第 3、4 條） */
	public static final String COUNT_ACCT_INVOLVED = """
			SELECT COUNT(*) AS CNT
			  FROM DUAL
			 WHERE EXISTS (SELECT 1 FROM IM_ACCT_REQ R WHERE R.ACCT_ID = :acctId AND R.APPLY_BY = :userId)
			    OR EXISTS (SELECT 1 FROM IM_ACCT_HIST H
			                WHERE H.ACCT_ID = :acctId
			                  AND H.REQ_ID IS NOT NULL
			                  AND (H.ACT_BY = :userId OR H.ACT_AS_ID = :userId))
			""";

	public static final String INSERT_REQ = """
			INSERT INTO IM_ACCT_REQ (REQ_ID, ACCT_ID, REQ_TYPE_CODE, REQ_STATE_CODE, CURR_STAGE_ID, APPLY_BY, APPLY_DEPT, APPLY_DATE,
			       CATG_ID, ACCT_NAME, SYS_NAME, DOOR_SCOPE_CODE, ASSET_CLASS_ID, IS_PRIV,
			       HOLDER_NAME, HOLDER_DEPT, HOLDER_CNTCT, HOLDER_EMAIL, ACCESS_LEVEL_CODE, ACCESS_TARGET,
			       APPLY_REASON, EXPIRY_TYPE_CODE, EXPIRY_DATE, IS_UNIQUE_CONF, SUBMIT_DATE, VERSION_NO, CREATE_DATE, CREATE_BY)
			VALUES (:reqId, :acctId, :reqTypeCode, 'REVIEW', 2, :applyBy, :applyDept, :now,
			       :catgId, :acctName, :sysName, :doorScopeCode, :assetClassId, :isPriv,
			       :holderName, :holderDept, :holderCntct, :holderEmail, :accessLevelCode, :accessTarget,
			       :applyReason, :expiryTypeCode, :expiryDate, :isUniqueConf, :now, 0, :now, :userId)
			""";

	/** 樂觀鎖共用條件（spec §4.4） */
	static final String WHERE_EXPECT = """
			 WHERE REQ_ID = :reqId
			   AND VERSION_NO = :versionNo
			   AND REQ_STATE_CODE = :expectState
			   AND CURR_STAGE_ID = :expectStage
			""";

	/** 重新送出：已退件（CURR_STAGE_ID 為空）、原申請人；內容覆寫，APPLY_DATE 與最近一次退件欄位保留 */
	public static final String UPDATE_RESUBMIT = """
			UPDATE IM_ACCT_REQ
			   SET APPLY_DEPT = :applyDept, CATG_ID = :catgId, ACCT_NAME = :acctName, SYS_NAME = :sysName,
			       DOOR_SCOPE_CODE = :doorScopeCode, ASSET_CLASS_ID = :assetClassId, IS_PRIV = :isPriv,
			       HOLDER_NAME = :holderName, HOLDER_DEPT = :holderDept, HOLDER_CNTCT = :holderCntct, HOLDER_EMAIL = :holderEmail,
			       ACCESS_LEVEL_CODE = :accessLevelCode, ACCESS_TARGET = :accessTarget, APPLY_REASON = :applyReason,
			       EXPIRY_TYPE_CODE = :expiryTypeCode, EXPIRY_DATE = :expiryDate, IS_UNIQUE_CONF = :isUniqueConf,
			       REQ_STATE_CODE = 'REVIEW', CURR_STAGE_ID = 2, SUBMIT_DATE = :now, RESUB_CNT = RESUB_CNT + 1,
			       VERSION_NO = VERSION_NO + 1, UPDATE_DATE = :now, UPDATE_BY = :userId
			 WHERE REQ_ID = :reqId
			   AND VERSION_NO = :versionNo
			   AND REQ_STATE_CODE = 'REJECTED'
			   AND CURR_STAGE_ID IS NULL
			   AND APPLY_BY = :actorId
			""";

	public static final String UPDATE_APPROVE = """
			UPDATE IM_ACCT_REQ
			   SET REQ_STATE_CODE = :newState, CURR_STAGE_ID = :newStage, VERSION_NO = VERSION_NO + 1,
			       UPDATE_DATE = :now, UPDATE_BY = :userId
			""" + WHERE_EXPECT;

	public static final String UPDATE_REJECT = """
			UPDATE IM_ACCT_REQ
			   SET REQ_STATE_CODE = 'REJECTED', CURR_STAGE_ID = NULL, REJECT_CNT = REJECT_CNT + 1,
			       REJECT_STAGE_ID = :expectStage, REJECT_BY = :actorId, REJECT_DATE = :now, REJECT_REASON = :reason,
			       VERSION_NO = VERSION_NO + 1, UPDATE_DATE = :now, UPDATE_BY = :userId
			""" + WHERE_EXPECT;

	/** 回報執行完畢；EFFECT_DATE 只在第一次寫；CARD_NO 只在門禁卡新增時以 :cardNo 覆寫（:setCard＝1） */
	public static final String UPDATE_EXEC_DONE = """
			UPDATE IM_ACCT_REQ
			   SET REQ_STATE_CODE = 'VERIFY', CURR_STAGE_ID = 7, EXEC_BY = :actorId, EXEC_DATE = :now,
			       EXEC_DESCR = :execDescr, CARD_NO = CASE WHEN :setCard = 1 THEN :cardNo ELSE CARD_NO END,
			       EFFECT_DATE = NVL(EFFECT_DATE, :now),
			       VERSION_NO = VERSION_NO + 1, UPDATE_DATE = :now, UPDATE_BY = :userId
			""" + WHERE_EXPECT;

	public static final String UPDATE_VERIFY_OK = """
			UPDATE IM_ACCT_REQ
			   SET REQ_STATE_CODE = 'APPROVED', CURR_STAGE_ID = NULL, VERIFY_BY = :actorId, VERIFY_DATE = :now,
			       DONE_DATE = :now, VERSION_NO = VERSION_NO + 1, UPDATE_DATE = :now, UPDATE_BY = :userId
			""" + WHERE_EXPECT;

	public static final String UPDATE_VERIFY_REJECT = """
			UPDATE IM_ACCT_REQ
			   SET REQ_STATE_CODE = 'EXEC', CURR_STAGE_ID = 6, VERIFY_REJECT_CNT = VERIFY_REJECT_CNT + 1,
			       VERIFY_REJECT_REASON = :reason, VERSION_NO = VERSION_NO + 1, UPDATE_DATE = :now, UPDATE_BY = :userId
			""" + WHERE_EXPECT;

	// ---- 待辦（spec §4.5、§6.3）；關卡／類別清單為空時 Service 不呼叫（避免 IN () 語法錯）
	static final String TODO_BASE = """
			SELECT 'ACCT_REQ' AS SOURCE, R.REQ_ID, R.REQ_TYPE_CODE, R.CATG_ID, C.CATG_NAME, R.ACCT_NAME, R.IS_PRIV, R.APPLY_BY,
			""" + "       " + nameExpr("UA") + " AS APPLY_BY_NAME,\n" + """
			       R.CURR_STAGE_ID AS STAGE_ID, S.STAGE_NAME, R.SUBMIT_DATE, R.VERSION_NO, R.VERIFY_REJECT_CNT,
			       R.VERIFY_REJECT_REASON
			  FROM IM_ACCT_REQ R
			  LEFT JOIN IM_ACCT_CATG C ON C.CATG_ID = R.CATG_ID
			  LEFT JOIN IM_ACCT_APPR_STAGE S ON S.STAGE_ID = R.CURR_STAGE_ID
			  LEFT JOIN IM_ACCT_USER UA ON UA.USER_ID = R.APPLY_BY
			""";

	public static final String FIND_TODO_APPROVE = TODO_BASE + """
			 WHERE R.REQ_STATE_CODE = 'REVIEW'
			   AND R.CURR_STAGE_ID IN (:stageIds)
			 ORDER BY R.SUBMIT_DATE, R.REQ_ID
			""";

	public static final String FIND_TODO_EXEC = TODO_BASE + """
			 WHERE R.REQ_STATE_CODE = 'EXEC'
			   AND R.CURR_STAGE_ID = 6
			   AND R.CATG_ID IN (:catgIds)
			 ORDER BY R.SUBMIT_DATE, R.REQ_ID
			""";

	public static final String FIND_TODO_VERIFY = TODO_BASE + """
			 WHERE R.REQ_STATE_CODE = 'VERIFY'
			   AND R.CURR_STAGE_ID = 7
			 ORDER BY R.SUBMIT_DATE, R.REQ_ID
			""";

	private final DbClient dbClient;
	private final String itflowDb;

	public AcctReqDao(DbClient dbClient, @Value("${db.connect.itflow}") String itflowDb) {
		this.dbClient = dbClient;
		this.itflowDb = itflowDb;
	}

	public AcctReqDetail findById(String reqId) {
		Map<String, Object> params = new HashMap<>();
		params.put("reqId", reqId);
		return DaoSupport.first(dbClient.query(itflowDb, FIND_BY_ID, params, AcctReqDetail.class));
	}

	public List<ReqListItem> findMine(String userId, String stateCode) {
		Map<String, Object> params = new HashMap<>();
		params.put("userId", userId);
		params.put("stateCode", stateCode);
		return dbClient.query(itflowDb, FIND_MINE, params, ReqListItem.class);
	}

	public AcctReqDetail findInFlightByAcct(String acctId) {
		Map<String, Object> params = new HashMap<>();
		params.put("acctId", acctId);
		return DaoSupport.first(dbClient.query(itflowDb, FIND_IN_FLIGHT_BY_ACCT, params, AcctReqDetail.class));
	}

	/** 同帳號在途件數（excludeReqId 為 null 時不排除） */
	public long countInFlight(String acctId, String excludeReqId) {
		Map<String, Object> params = new HashMap<>();
		params.put("acctId", acctId);
		params.put("excludeReqId", excludeReqId);
		return DaoSupport.count(dbClient.query(itflowDb, COUNT_IN_FLIGHT, params, CountRow.class));
	}

	public long countAcctInvolved(String acctId, String userId) {
		Map<String, Object> params = new HashMap<>();
		params.put("acctId", acctId);
		params.put("userId", userId);
		return DaoSupport.count(dbClient.query(itflowDb, COUNT_ACCT_INVOLVED, params, CountRow.class));
	}

	/** 新單 INSERT（REVIEW／關卡 2、VERSION_NO＝0）；同帳號已有在途時由 IND_IM_ACCT_REQ_04 丟 DuplicateKeyException */
	public int insert(AcctReq c, LocalDateTime now, String userId) {
		Map<String, Object> params = contentParams(c);
		params.put("reqId", c.getReqId());
		params.put("acctId", c.getAcctId());
		params.put("reqTypeCode", c.getReqTypeCode());
		params.put("applyBy", c.getApplyBy());
		params.put("now", DaoSupport.ts(now));
		params.put("userId", userId);
		return dbClient.update(itflowDb, INSERT_REQ, params);
	}

	public int resubmit(AcctReq c, int versionNo, String actorId, LocalDateTime now, String userId) {
		Map<String, Object> params = contentParams(c);
		params.put("reqId", c.getReqId());
		params.put("versionNo", versionNo);
		params.put("actorId", actorId);
		params.put("now", DaoSupport.ts(now));
		params.put("userId", userId);
		return dbClient.update(itflowDb, UPDATE_RESUBMIT, params);
	}

	public int approve(String reqId, int versionNo, int expectStage, String newState, int newStage, LocalDateTime now,
			String userId) {
		Map<String, Object> params = expect(reqId, versionNo, "REVIEW", expectStage, now, userId);
		params.put("newState", newState);
		params.put("newStage", newStage);
		return dbClient.update(itflowDb, UPDATE_APPROVE, params);
	}

	public int reject(String reqId, int versionNo, int expectStage, String reason, String actorId, LocalDateTime now,
			String userId) {
		Map<String, Object> params = expect(reqId, versionNo, "REVIEW", expectStage, now, userId);
		params.put("reason", reason);
		params.put("actorId", actorId);
		return dbClient.update(itflowDb, UPDATE_REJECT, params);
	}

	public int execDone(String reqId, int versionNo, String execDescr, boolean setCard, String cardNo, String actorId,
			LocalDateTime now, String userId) {
		Map<String, Object> params = expect(reqId, versionNo, "EXEC", 6, now, userId);
		params.put("execDescr", execDescr);
		params.put("setCard", setCard ? 1 : 0);
		params.put("cardNo", cardNo);
		params.put("actorId", actorId);
		return dbClient.update(itflowDb, UPDATE_EXEC_DONE, params);
	}

	public int verifyOk(String reqId, int versionNo, String actorId, LocalDateTime now, String userId) {
		Map<String, Object> params = expect(reqId, versionNo, "VERIFY", 7, now, userId);
		params.put("actorId", actorId);
		return dbClient.update(itflowDb, UPDATE_VERIFY_OK, params);
	}

	public int verifyReject(String reqId, int versionNo, String reason, LocalDateTime now, String userId) {
		Map<String, Object> params = expect(reqId, versionNo, "VERIFY", 7, now, userId);
		params.put("reason", reason);
		return dbClient.update(itflowDb, UPDATE_VERIFY_REJECT, params);
	}

	public List<TodoItem> findTodoApprove(List<Integer> stageIds) {
		Map<String, Object> params = new HashMap<>();
		params.put("stageIds", stageIds);
		return dbClient.query(itflowDb, FIND_TODO_APPROVE, params, TodoItem.class);
	}

	public List<TodoItem> findTodoExec(List<String> catgIds) {
		Map<String, Object> params = new HashMap<>();
		params.put("catgIds", catgIds);
		return dbClient.query(itflowDb, FIND_TODO_EXEC, params, TodoItem.class);
	}

	public List<TodoItem> findTodoVerify() {
		return dbClient.query(itflowDb, FIND_TODO_VERIFY, null, TodoItem.class);
	}

	private static Map<String, Object> expect(String reqId, int versionNo, String expectState, int expectStage,
			LocalDateTime now, String userId) {
		Map<String, Object> params = new HashMap<>();
		params.put("reqId", reqId);
		params.put("versionNo", versionNo);
		params.put("expectState", expectState);
		params.put("expectStage", expectStage);
		params.put("now", DaoSupport.ts(now));
		params.put("userId", userId);
		return params;
	}

	/** 申請內容欄位（送出、重新送出、主表寫入共用） */
	static Map<String, Object> contentParams(AcctReq c) {
		Map<String, Object> params = new HashMap<>();
		params.put("applyDept", c.getApplyDept());
		params.put("catgId", c.getCatgId());
		params.put("acctName", c.getAcctName());
		params.put("sysName", c.getSysName());
		params.put("doorScopeCode", c.getDoorScopeCode());
		params.put("assetClassId", c.getAssetClassId());
		params.put("isPriv", c.getIsPriv());
		params.put("holderName", c.getHolderName());
		params.put("holderDept", c.getHolderDept());
		params.put("holderCntct", c.getHolderCntct());
		params.put("holderEmail", c.getHolderEmail());
		params.put("accessLevelCode", c.getAccessLevelCode());
		params.put("accessTarget", c.getAccessTarget());
		params.put("applyReason", c.getApplyReason());
		params.put("expiryTypeCode", c.getExpiryTypeCode());
		params.put("expiryDate", DaoSupport.ts(c.getExpiryDate()));
		params.put("isUniqueConf", c.getIsUniqueConf());
		return params;
	}
}
