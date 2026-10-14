package com.mpx.im_acct_insp.dao;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：帳號主表 IM_ACCT 存取（spec §3.3 同步規則、§4.2 帳號 API）
//           查詢附 catgName、assetClassName、inFlight（是否有 REVIEW／EXEC／VERIFY 的申請單）
//           篩選條件一律寫成 (:x IS NULL OR …)，不拼接 SQL
//           變更單 #1：LIKE 加 ESCAPE '\'（關鍵字由 Service 跳脫）；lookup 清單改回精簡欄位（AcctLookupBrief）；
//           新增 FIND_LOOKUP_BY_ID（限 APPROVED／DISABLED，完整內容欄位）；新增申請退件後重新送出的狀態條件放寬為
//           IN ('REJECTED','REVIEW')；展延新帳號審查日期改由 Service 決定後傳入
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（spec_im_acct_insp_v2.0.md §4.5～§4.7、§4.11、§6.4；Table v0.2 7.14）：
//           欄位依新表（刪展延／匯入／審查依據欄）；查詢附 inFlightReqId、pendingVerify（有 VERIFY 申請單）；
//           到期提醒範圍條件 EXPIRING_COND 為首頁卡片、鈴鐺、列表橫幅共用（:today 綁定，不用 SYSDATE）；
//           列表篩選拆成片段：列表＝全部條件、stateCounts＝狀態以外、catgCounts＝類別以外、expiringCount 不受篩選；
//           lookup 改 ACCT_NAME 完整精確比對（區分大小寫），狀態 APPROVED／EXPIRED／DISABLED；
//           主表寫入改在關卡 6（APPLY_ADD／APPLY_MODIFY／APPLY_ENABLE／APPLY_STATE），修改時已逾期且新期限有效改回 APPROVED；
//           審查日期另句寫入；J01 用的轉逾期、尚未寄信查詢（參照鍵 ACCT_ID_到期日yyyymmdd）；
//           刪展延、作廢相關 SQL；時間一律綁定 :now
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S4b-1：審查異常旗標 UPDATE_REVIEW_FLAG（緊急存取退回且申請人回報為異動時逐帳號標記；
//           同帳號再次標記直接覆蓋原因與時間，S4-①）；REVIEW_FLAG_BY 有效身分、UPDATE_BY 實際操作人
// AI版本  : Claude Fable 5 (claude-fable-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S10：審查異常旗標清除 CLEAR_REVIEW_FLAG（修改／停用／重新啟用申請關卡 7
//           複驗通過時清 5 欄，D42＋裁示 ①B；帶 IS_REVIEW_FLAG = 1 條件當雙保險）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S10 審查修正 ①B：CLEAR_REVIEW_FLAG 加 REVIEW_FLAG_DATE <= :execDate，
//           只清本單關卡 6 回報前就存在的旗標（回報後新標或覆蓋的異常留著，也避免清錯、記錯原因）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-14
// 變更說明: BACKLOG 第 46 項 S10 複審：CLEAR_REVIEW_FLAG 註解改寫成直接寫內容；上方兩區塊的代號對照——
//           Fable 區塊「裁示 ①B」＝重新啟用也清（S10 開工裁示），上一區塊「審查修正 ①B」＝只清回報前旗標（S10 審查裁示）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-14
// 變更說明: DB 別名 key 由 db.connect.itflow 改名為 itflow.db.alias（使用者改名）
// ============================================================

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.mpx.common.db.DbClient;
import com.mpx.im_acct_insp.model.Acct;
import com.mpx.im_acct_insp.model.AcctLookupBrief;
import com.mpx.im_acct_insp.model.AcctReq;
import com.mpx.im_acct_insp.model.CodeCountRow;
import com.mpx.im_acct_insp.model.CountRow;

@Repository
public class AcctDao {

	/** 到期提醒範圍（spec §4.11）；首頁卡片、鈴鐺、帳號列表橫幅共用；需綁定 :today（當天 00:00） */
	public static final String EXPIRING_COND = """
			((A.ACCT_STATE_CODE = 'APPROVED' AND A.EXPIRY_TYPE_CODE = 'DATE' AND A.EXPIRY_DATE <= :today + 30)
			 OR A.ACCT_STATE_CODE = 'EXPIRED')
			""";

	/** 通知紀錄參照鍵 ACCT_ID_到期日yyyymmdd（Table 7.20；Java 端見 NotificationService.acctRefId） */
	static final String ACCT_REF_EXPR = "A.ACCT_ID || '_' || TO_CHAR(A.EXPIRY_DATE, 'YYYYMMDD')";

	static final String SELECT_BASE = """
			SELECT A.ACCT_ID, A.CATG_ID, A.ACCT_NAME, A.SYS_NAME, A.DOOR_SCOPE_CODE, A.ASSET_CLASS_ID, A.IS_PRIV,
			       A.HOLDER_NAME, A.HOLDER_DEPT, A.HOLDER_CNTCT, A.HOLDER_EMAIL, A.ACCESS_LEVEL_CODE, A.ACCESS_TARGET,
			       A.APPLY_REASON, A.EXPIRY_TYPE_CODE, A.EXPIRY_DATE, A.ACCT_STATE_CODE, A.SOURCE_CODE, A.APPLY_BY,
			       A.LAST_REVIEW_DATE, A.NEXT_REVIEW_DATE, A.IS_REVIEW_FLAG, A.REVIEW_FLAG_REASON, A.REVIEW_FLAG_SOURCE_CODE,
			       A.REVIEW_FLAG_DATE, A.REVIEW_FLAG_BY, A.STATUS, A.CREATE_DATE, A.CREATE_BY, A.UPDATE_DATE, A.UPDATE_BY,
			       C.CATG_NAME, AC.ASSET_CLASS_NAME,
			       (SELECT MAX(R.REQ_ID) FROM IM_ACCT_REQ R
			         WHERE R.ACCT_ID = A.ACCT_ID AND R.REQ_STATE_CODE IN ('REVIEW', 'EXEC', 'VERIFY')) AS IN_FLIGHT_REQ_ID,
			       CASE WHEN EXISTS (SELECT 1 FROM IM_ACCT_REQ R
			                          WHERE R.ACCT_ID = A.ACCT_ID AND R.REQ_STATE_CODE IN ('REVIEW', 'EXEC', 'VERIFY'))
			            THEN 1 ELSE 0 END AS IN_FLIGHT,
			       CASE WHEN EXISTS (SELECT 1 FROM IM_ACCT_REQ R
			                          WHERE R.ACCT_ID = A.ACCT_ID AND R.REQ_STATE_CODE = 'VERIFY')
			            THEN 1 ELSE 0 END AS PENDING_VERIFY
			  FROM IM_ACCT A
			  LEFT JOIN IM_ACCT_CATG C ON C.CATG_ID = A.CATG_ID
			  LEFT JOIN IM_ASSET_CLASS AC ON AC.ASSET_CLASS_ID = A.ASSET_CLASS_ID
			""";

	public static final String FIND_BY_ID = SELECT_BASE + """
			 WHERE A.ACCT_ID = :acctId
			""";

	// ---- 帳號列表篩選片段（spec §6.4）
	static final String F_KEYWORD = """
			(:keyword IS NULL
			        OR UPPER(A.ACCT_ID) LIKE '%' || UPPER(:keyword) || '%' ESCAPE '\\'
			        OR UPPER(A.ACCT_NAME) LIKE '%' || UPPER(:keyword) || '%' ESCAPE '\\'
			        OR UPPER(A.HOLDER_NAME) LIKE '%' || UPPER(:keyword) || '%' ESCAPE '\\')
			""";
	static final String F_CATG = " AND (:catgId IS NULL OR A.CATG_ID = :catgId)\n";
	static final String F_ASSET = " AND (:assetClassId IS NULL OR A.ASSET_CLASS_ID = :assetClassId)\n";
	/** 未指定狀態時預設不含 DELETED */
	static final String F_STATE = " AND ((:stateCode IS NULL AND A.ACCT_STATE_CODE <> 'DELETED') OR A.ACCT_STATE_CODE = :stateCode)\n";
	static final String F_EXPIRING = " AND (:expiring = 0 OR " + EXPIRING_COND + ")\n";

	public static final String FIND_LIST = SELECT_BASE + " WHERE " + F_KEYWORD + F_CATG + F_ASSET + F_STATE + F_EXPIRING
			+ " ORDER BY A.ACCT_ID DESC\n";

	public static final String COUNT_BY_STATE = "SELECT A.ACCT_STATE_CODE AS CODE, COUNT(*) AS CNT FROM IM_ACCT A WHERE "
			+ F_KEYWORD + F_CATG + F_ASSET + F_EXPIRING + " GROUP BY A.ACCT_STATE_CODE\n";

	public static final String COUNT_BY_CATG = "SELECT A.CATG_ID AS CODE, COUNT(*) AS CNT FROM IM_ACCT A WHERE "
			+ F_KEYWORD + F_ASSET + F_STATE + F_EXPIRING + " GROUP BY A.CATG_ID\n";

	public static final String COUNT_EXPIRING = "SELECT COUNT(*) AS CNT FROM IM_ACCT A WHERE " + EXPIRING_COND;

	public static final String FIND_MINE = SELECT_BASE + """
			 WHERE A.APPLY_BY = :userId
			   AND A.ACCT_STATE_CODE NOT IN ('REJECTED', 'DELETED')
			 ORDER BY A.ACCT_ID DESC
			""";

	/** 對既有帳號提出異動：完整精確比對（區分大小寫），不提供模糊查詢（spec §2.4） */
	public static final String FIND_LOOKUP = """
			SELECT A.ACCT_ID, A.CATG_ID, C.CATG_NAME, A.ACCT_NAME, A.SYS_NAME, A.HOLDER_NAME, A.ACCT_STATE_CODE,
			       A.EXPIRY_TYPE_CODE, A.EXPIRY_DATE,
			       CASE WHEN EXISTS (SELECT 1 FROM IM_ACCT_REQ R
			                          WHERE R.ACCT_ID = A.ACCT_ID AND R.REQ_STATE_CODE IN ('REVIEW', 'EXEC', 'VERIFY'))
			            THEN 1 ELSE 0 END AS IN_FLIGHT
			  FROM IM_ACCT A
			  LEFT JOIN IM_ACCT_CATG C ON C.CATG_ID = A.CATG_ID
			 WHERE A.ACCT_NAME = :acctName
			   AND A.ACCT_STATE_CODE IN ('APPROVED', 'EXPIRED', 'DISABLED')
			 ORDER BY A.ACCT_ID DESC
			""";

	/** 新增申請送出時建立（狀態 REVIEW、來源 APPLY，spec §4.5 主表連動） */
	public static final String INSERT_ON_SUBMIT = """
			INSERT INTO IM_ACCT (ACCT_ID, CATG_ID, ACCT_NAME, SYS_NAME, DOOR_SCOPE_CODE, ASSET_CLASS_ID, IS_PRIV,
			       HOLDER_NAME, HOLDER_DEPT, HOLDER_CNTCT, HOLDER_EMAIL, ACCESS_LEVEL_CODE, ACCESS_TARGET, APPLY_REASON,
			       EXPIRY_TYPE_CODE, EXPIRY_DATE, ACCT_STATE_CODE, SOURCE_CODE, APPLY_BY, CREATE_DATE, CREATE_BY)
			VALUES (:acctId, :catgId, :acctName, :sysName, :doorScopeCode, :assetClassId, :isPriv,
			       :holderName, :holderDept, :holderCntct, :holderEmail, :accessLevelCode, :accessTarget, :applyReason,
			       :expiryTypeCode, :expiryDate, 'REVIEW', 'APPLY', :applyBy, :now, :userId)
			""";

	static final String SET_CONTENT = """
			       CATG_ID = :catgId, ACCT_NAME = :acctName, SYS_NAME = :sysName, DOOR_SCOPE_CODE = :doorScopeCode,
			       ASSET_CLASS_ID = :assetClassId, IS_PRIV = :isPriv, HOLDER_NAME = :holderName, HOLDER_DEPT = :holderDept,
			       HOLDER_CNTCT = :holderCntct, HOLDER_EMAIL = :holderEmail, ACCESS_LEVEL_CODE = :accessLevelCode,
			       ACCESS_TARGET = :accessTarget, APPLY_REASON = :applyReason, EXPIRY_TYPE_CODE = :expiryTypeCode,
			       EXPIRY_DATE = :expiryDate,
			""";

	/** 修改、重新啟用覆寫的欄位（spec §4.6；帳號類別、名稱、所屬系統不動） */
	static final String SET_MODIFY = """
			       HOLDER_NAME = :holderName, HOLDER_DEPT = :holderDept, HOLDER_CNTCT = :holderCntct,
			       HOLDER_EMAIL = :holderEmail, ACCESS_LEVEL_CODE = :accessLevelCode, ACCESS_TARGET = :accessTarget,
			       ASSET_CLASS_ID = :assetClassId, IS_PRIV = :isPriv, EXPIRY_TYPE_CODE = :expiryTypeCode,
			       EXPIRY_DATE = :expiryDate, DOOR_SCOPE_CODE = :doorScopeCode, APPLY_REASON = :applyReason,
			""";

	/** 新增申請被退件 */
	public static final String UPDATE_ADD_REJECTED = """
			UPDATE IM_ACCT
			   SET ACCT_STATE_CODE = 'REJECTED', UPDATE_DATE = :now, UPDATE_BY = :userId
			 WHERE ACCT_ID = :acctId
			   AND ACCT_STATE_CODE = 'REVIEW'
			""";

	/** 新增申請退件後重新送出：內容覆寫、狀態回 REVIEW */
	public static final String UPDATE_ADD_RESUBMIT = """
			UPDATE IM_ACCT
			   SET
			""" + SET_CONTENT + """
			       ACCT_STATE_CODE = 'REVIEW', UPDATE_DATE = :now, UPDATE_BY = :userId
			 WHERE ACCT_ID = :acctId
			   AND ACCT_STATE_CODE = 'REJECTED'
			""";

	/** 關卡 6 第一次回報：新增 → 寫入全部內容、APPROVED（門禁卡 ACCT_NAME＝卡號由 Service 傳入） */
	public static final String APPLY_ADD = """
			UPDATE IM_ACCT
			   SET
			""" + SET_CONTENT + """
			       ACCT_STATE_CODE = 'APPROVED', UPDATE_DATE = :now, UPDATE_BY = :userId
			 WHERE ACCT_ID = :acctId
			   AND ACCT_STATE_CODE = 'REVIEW'
			""";

	/** 關卡 6 第一次回報：修改 → 狀態不變；在途期間已被 J01 轉 EXPIRED 且新期限有效（:revive＝1）時改回 APPROVED */
	public static final String APPLY_MODIFY = """
			UPDATE IM_ACCT
			   SET
			""" + SET_MODIFY + """
			       ACCT_STATE_CODE = CASE WHEN ACCT_STATE_CODE = 'EXPIRED' AND :revive = 1 THEN 'APPROVED'
			                              ELSE ACCT_STATE_CODE END,
			       UPDATE_DATE = :now, UPDATE_BY = :userId
			 WHERE ACCT_ID = :acctId
			""";

	/** 關卡 6 第一次回報：重新啟用 → 同修改的欄位、APPROVED */
	public static final String APPLY_ENABLE = """
			UPDATE IM_ACCT
			   SET
			""" + SET_MODIFY + """
			       ACCT_STATE_CODE = 'APPROVED', UPDATE_DATE = :now, UPDATE_BY = :userId
			 WHERE ACCT_ID = :acctId
			""";

	/** 關卡 6 第一次回報：停用（DISABLED）、刪除（DELETED，軟刪除）→ 狀態與申請原因 */
	public static final String APPLY_STATE = """
			UPDATE IM_ACCT
			   SET ACCT_STATE_CODE = :newState, APPLY_REASON = :applyReason, UPDATE_DATE = :now, UPDATE_BY = :userId
			 WHERE ACCT_ID = :acctId
			""";

	/** 複驗退回後再次回報：門禁卡新增卡號改變時同步主表帳號名稱（spec §4.6 唯一例外） */
	public static final String UPDATE_ACCT_NAME = """
			UPDATE IM_ACCT
			   SET ACCT_NAME = :acctName, UPDATE_DATE = :now, UPDATE_BY = :userId
			 WHERE ACCT_ID = :acctId
			""";

	/** 審查日期（spec §4.7；第一次回報時符合條件才寫） */
	public static final String UPDATE_REVIEW_DATES = """
			UPDATE IM_ACCT
			   SET LAST_REVIEW_DATE = :lastReviewDate, NEXT_REVIEW_DATE = :nextReviewDate, UPDATE_DATE = :now, UPDATE_BY = :userId
			 WHERE ACCT_ID = :acctId
			""";

	/** 審查異常旗標（S4-①）：不看現值，第二次標記直接覆蓋；清除見 CLEAR_REVIEW_FLAG（S10） */
	public static final String UPDATE_REVIEW_FLAG = """
			UPDATE IM_ACCT
			   SET IS_REVIEW_FLAG = 1, REVIEW_FLAG_REASON = :reason, REVIEW_FLAG_SOURCE_CODE = :sourceCode,
			       REVIEW_FLAG_DATE = :now, REVIEW_FLAG_BY = :flagBy, UPDATE_DATE = :now, UPDATE_BY = :userId
			 WHERE ACCT_ID = :acctId
			""";

	/**
	 * 審查異常旗標清除（S10，D42 修改／停用，重新啟用也清（S10 開工裁示））：清 5 欄；只清回報前旗標（S10 審查裁示）——
	 * 本單關卡 6 回報（:execDate）後才標上（或被覆蓋）的異常不可能是本單的處理結果，留著；已被清過或不符影響 0 筆
	 */
	public static final String CLEAR_REVIEW_FLAG = """
			UPDATE IM_ACCT
			   SET IS_REVIEW_FLAG = 0, REVIEW_FLAG_REASON = NULL, REVIEW_FLAG_SOURCE_CODE = NULL,
			       REVIEW_FLAG_DATE = NULL, REVIEW_FLAG_BY = NULL, UPDATE_DATE = :now, UPDATE_BY = :userId
			 WHERE ACCT_ID = :acctId
			   AND IS_REVIEW_FLAG = 1
			   AND REVIEW_FLAG_DATE <= :execDate
			""";

	// ---- J01／J02（spec §4.11）
	public static final String FIND_TO_EXPIRE = SELECT_BASE + """
			 WHERE A.ACCT_STATE_CODE = 'APPROVED'
			   AND A.EXPIRY_TYPE_CODE = 'DATE'
			   AND A.EXPIRY_DATE < :today
			 ORDER BY A.EXPIRY_DATE, A.ACCT_ID
			""";

	public static final String UPDATE_EXPIRE = """
			UPDATE IM_ACCT
			   SET ACCT_STATE_CODE = 'EXPIRED', UPDATE_DATE = :now, UPDATE_BY = :userId
			 WHERE ACCT_ID = :acctId
			   AND ACCT_STATE_CODE = 'APPROVED'
			   AND EXPIRY_TYPE_CODE = 'DATE'
			   AND EXPIRY_DATE < :today
			""";

	static final String NOT_NOTIFIED = """
			   AND NOT EXISTS (SELECT 1 FROM IM_ACCT_NOTIFY_LOG N
			                    WHERE N.EVENT_CODE = :eventCode
			                      AND N.REF_TYPE_CODE = 'ACCT'
			                      AND N.REF_ID =""" + " " + ACCT_REF_EXPR + ")\n";

	/** 已逾期、且此參照鍵尚無任何 EXPIRED 通知紀錄 */
	public static final String FIND_EXPIRED_UNNOTIFIED = SELECT_BASE + """
			 WHERE A.ACCT_STATE_CODE = 'EXPIRED'
			   AND A.EXPIRY_DATE IS NOT NULL
			""" + NOT_NOTIFIED + """
			 ORDER BY A.EXPIRY_DATE, A.ACCT_ID
			""";

	/** 已核准、today ≤ 到期日 ≤ today＋7，且此參照鍵尚無任何 EXPIRY_7D 通知紀錄 */
	public static final String FIND_EXPIRY_7D_UNNOTIFIED = SELECT_BASE + """
			 WHERE A.ACCT_STATE_CODE = 'APPROVED'
			   AND A.EXPIRY_TYPE_CODE = 'DATE'
			   AND A.EXPIRY_DATE >= :today
			   AND A.EXPIRY_DATE <= :todayPlus7
			""" + NOT_NOTIFIED + """
			 ORDER BY A.EXPIRY_DATE, A.ACCT_ID
			""";

	/** J02 週彙總：所有已逾期帳號（不含停用、刪除），依到期日 */
	public static final String FIND_EXPIRED_ALL = SELECT_BASE + """
			 WHERE A.ACCT_STATE_CODE = 'EXPIRED'
			 ORDER BY A.EXPIRY_DATE, A.ACCT_ID
			""";

	private final DbClient dbClient;
	private final String itflowDb;

	public AcctDao(DbClient dbClient, @Value("${itflow.db.alias}") String itflowDb) {
		this.dbClient = dbClient;
		this.itflowDb = itflowDb;
	}

	public Acct findById(String acctId) {
		Map<String, Object> params = new HashMap<>();
		params.put("acctId", acctId);
		return DaoSupport.first(dbClient.query(itflowDb, FIND_BY_ID, params, Acct.class));
	}

	/** 列表篩選參數（keyword 已跳脫；expiring 1／0） */
	public static Map<String, Object> listParams(String keyword, String catgId, String stateCode, String assetClassId,
			boolean expiring, LocalDate today) {
		Map<String, Object> params = new HashMap<>();
		params.put("keyword", keyword);
		params.put("catgId", catgId);
		params.put("stateCode", stateCode);
		params.put("assetClassId", assetClassId);
		params.put("expiring", expiring ? 1 : 0);
		params.put("today", DaoSupport.ts(today));
		return params;
	}

	public List<Acct> findList(Map<String, Object> listParams) {
		return dbClient.query(itflowDb, FIND_LIST, listParams, Acct.class);
	}

	public List<CodeCountRow> countByState(Map<String, Object> listParams) {
		return dbClient.query(itflowDb, COUNT_BY_STATE, listParams, CodeCountRow.class);
	}

	public List<CodeCountRow> countByCatg(Map<String, Object> listParams) {
		return dbClient.query(itflowDb, COUNT_BY_CATG, listParams, CodeCountRow.class);
	}

	public long countExpiring(LocalDate today) {
		Map<String, Object> params = new HashMap<>();
		params.put("today", DaoSupport.ts(today));
		return DaoSupport.count(dbClient.query(itflowDb, COUNT_EXPIRING, params, CountRow.class));
	}

	public List<Acct> findMine(String userId) {
		Map<String, Object> params = new HashMap<>();
		params.put("userId", userId);
		return dbClient.query(itflowDb, FIND_MINE, params, Acct.class);
	}

	public List<AcctLookupBrief> findLookup(String acctName) {
		Map<String, Object> params = new HashMap<>();
		params.put("acctName", acctName);
		return dbClient.query(itflowDb, FIND_LOOKUP, params, AcctLookupBrief.class);
	}

	public int insertOnSubmit(AcctReq c, LocalDateTime now, String userId) {
		Map<String, Object> params = AcctReqDao.contentParams(c);
		params.put("acctId", c.getAcctId());
		params.put("applyBy", c.getApplyBy());
		params.put("now", DaoSupport.ts(now));
		params.put("userId", userId);
		return dbClient.update(itflowDb, INSERT_ON_SUBMIT, params);
	}

	public int markAddRejected(String acctId, LocalDateTime now, String userId) {
		return dbClient.update(itflowDb, UPDATE_ADD_REJECTED, idParams(acctId, now, userId));
	}

	public int resubmitAdd(AcctReq c, LocalDateTime now, String userId) {
		Map<String, Object> params = AcctReqDao.contentParams(c);
		params.put("acctId", c.getAcctId());
		params.put("now", DaoSupport.ts(now));
		params.put("userId", userId);
		return dbClient.update(itflowDb, UPDATE_ADD_RESUBMIT, params);
	}

	public int applyAdd(AcctReq r, String acctName, LocalDateTime now, String userId) {
		Map<String, Object> params = AcctReqDao.contentParams(r);
		params.put("acctId", r.getAcctId());
		params.put("acctName", acctName);
		params.put("now", DaoSupport.ts(now));
		params.put("userId", userId);
		return dbClient.update(itflowDb, APPLY_ADD, params);
	}

	public int applyModify(AcctReq r, boolean revive, LocalDateTime now, String userId) {
		Map<String, Object> params = AcctReqDao.contentParams(r);
		params.put("acctId", r.getAcctId());
		params.put("revive", revive ? 1 : 0);
		params.put("now", DaoSupport.ts(now));
		params.put("userId", userId);
		return dbClient.update(itflowDb, APPLY_MODIFY, params);
	}

	public int applyEnable(AcctReq r, LocalDateTime now, String userId) {
		Map<String, Object> params = AcctReqDao.contentParams(r);
		params.put("acctId", r.getAcctId());
		params.put("now", DaoSupport.ts(now));
		params.put("userId", userId);
		return dbClient.update(itflowDb, APPLY_ENABLE, params);
	}

	/** newState 限 DISABLED／DELETED（由 Service 依申請類型決定，非外部輸入） */
	public int applyState(String acctId, String newState, String applyReason, LocalDateTime now, String userId) {
		Map<String, Object> params = idParams(acctId, now, userId);
		params.put("newState", newState);
		params.put("applyReason", applyReason);
		return dbClient.update(itflowDb, APPLY_STATE, params);
	}

	public int updateAcctName(String acctId, String acctName, LocalDateTime now, String userId) {
		Map<String, Object> params = idParams(acctId, now, userId);
		params.put("acctName", acctName);
		return dbClient.update(itflowDb, UPDATE_ACCT_NAME, params);
	}

	public int updateReviewDates(String acctId, LocalDate last, LocalDate next, LocalDateTime now, String userId) {
		Map<String, Object> params = idParams(acctId, now, userId);
		params.put("lastReviewDate", DaoSupport.ts(last));
		params.put("nextReviewDate", DaoSupport.ts(next));
		return dbClient.update(itflowDb, UPDATE_REVIEW_DATES, params);
	}

	/** 標審查異常旗標；flagBy＝有效身分（REVIEW_FLAG_BY）、userId＝實際操作人（UPDATE_BY）；reason 由 Service 截到 500 字 */
	public int flagReview(String acctId, String reason, String sourceCode, String flagBy, LocalDateTime now, String userId) {
		Map<String, Object> params = idParams(acctId, now, userId);
		params.put("reason", reason);
		params.put("sourceCode", sourceCode);
		params.put("flagBy", flagBy);
		return dbClient.update(itflowDb, UPDATE_REVIEW_FLAG, params);
	}

	/** 清審查異常旗標（S10，D42：修改／停用／重新啟用申請關卡 7 複驗通過時）；execDate＝本單關卡 6 回報時間；已被清過或旗標晚於回報回 0 */
	public int clearReview(String acctId, LocalDateTime execDate, LocalDateTime now, String userId) {
		Map<String, Object> params = idParams(acctId, now, userId);
		params.put("execDate", DaoSupport.ts(execDate));
		return dbClient.update(itflowDb, CLEAR_REVIEW_FLAG, params);
	}

	public List<Acct> findToExpire(LocalDate today) {
		Map<String, Object> params = new HashMap<>();
		params.put("today", DaoSupport.ts(today));
		return dbClient.query(itflowDb, FIND_TO_EXPIRE, params, Acct.class);
	}

	public int expire(String acctId, LocalDate today, LocalDateTime now, String userId) {
		Map<String, Object> params = idParams(acctId, now, userId);
		params.put("today", DaoSupport.ts(today));
		return dbClient.update(itflowDb, UPDATE_EXPIRE, params);
	}

	public List<Acct> findExpiredUnnotified(String eventCode) {
		Map<String, Object> params = new HashMap<>();
		params.put("eventCode", eventCode);
		return dbClient.query(itflowDb, FIND_EXPIRED_UNNOTIFIED, params, Acct.class);
	}

	public List<Acct> findExpiry7dUnnotified(String eventCode, LocalDate today, LocalDate todayPlus7) {
		Map<String, Object> params = new HashMap<>();
		params.put("eventCode", eventCode);
		params.put("today", DaoSupport.ts(today));
		params.put("todayPlus7", DaoSupport.ts(todayPlus7));
		return dbClient.query(itflowDb, FIND_EXPIRY_7D_UNNOTIFIED, params, Acct.class);
	}

	public List<Acct> findExpiredAll() {
		return dbClient.query(itflowDb, FIND_EXPIRED_ALL, null, Acct.class);
	}

	private static Map<String, Object> idParams(String acctId, LocalDateTime now, String userId) {
		Map<String, Object> params = new HashMap<>();
		params.put("acctId", acctId);
		params.put("now", DaoSupport.ts(now));
		params.put("userId", userId);
		return params;
	}
}
