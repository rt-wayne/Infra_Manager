package com.mpx.im_acct_insp.common;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：代碼值常數（值域依 im_acct_insp_create_table.sql 的 COMMENT）與共用訊息（spec §3.2）
//           測試第 1 輪修正：IM_USER 改 schema 限定名稱（新增 EXISTING_USER_TABLE）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0 重寫（spec_im_acct_insp_v2.0.md；Table 規劃 v0.2 第 5、6 節）：
//           單號 AR／IN；申請類型 5 種（刪 EXTEND）與前提；申請單狀態 5 種（刪 VOID）；帳號狀態 6 種（加 EXPIRED、刪 CONFIRM／VOID）；
//           關卡 1～7；歷史動作（刪 TO_EXEC／EXTEND_DONE／VOID／FLAG_CLEAR，加 EXPIRE）；附件、通知事件、排程、管理軌跡代碼；
//           第一階段用到的權限碼；登入時效常數（spec §2.1、§9.4：程式常數）。
//           移除 EXISTING_USER_TABLE（本模組不再引用既有模組使用者表）、角色碼常數（程式不判斷角色名稱，spec §2.3）、
//           審查依據碼（M14 不再存 REVIEW_BASIS_CODE）；訊息移到 AcctMsg
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 變更單 #2：新增通知收件角色常數 MAIL_ROLE_EXPIRY（GOV）、MAIL_ROLE_JOB_FAIL（SYS_ADMIN），
//           取代 properties key（依 SA 7.3 固定）；只用來查收件人，不用於權限判斷
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-11
// 變更說明: BACKLOG 第 28 項：TOKEN_IDLE_MINUTES 由 30 改為 TOKEN_MAX_HOURS * 60（480 分，實際只剩登入滿 8 小時失效；
//           前端 token 改跨分頁共用，一天登入一次）。閒置檢查保留，日後要縮短只改此值
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S2（緊急存取）：單號 EA；計算狀態碼 5 種（PENDING／OVERDUE／APPROVED／RETURNED／CLOSED）；
//           回報、處理、結案、受影響帳號登記來源代碼；補核准期限 24 小時與預估時長 1～24；
//           通知事件 EMG_SUBMIT／EMG_OVERDUE／EMG_RETURN、參照 EMG；權限碼 EMG_APPLY／EMG_VIEW_ALL／EMG_DECIDE
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S4a：回報受影響帳號上限 EMG_AFFECT_MAX＝20（S4-③）
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S4b-1：歷史來源 EMG 與動作 EMG_MODIFY（S4-⑧）、審查異常旗標來源 EMG（S4-①）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S6：歷史動作 EMG_CLOSE_MODIFY、自核標籤「自行結案」（S6-③）
// ============================================================

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AcctConst {

	private AcctConst() {
	}

	// 單號前綴（IM_DOC_SEQ.DOC_TYPE_ID，Table 第 5 節）
	public static final String DOC_AR = "AR";
	public static final String DOC_IN = "IN";
	public static final String DOC_EA = "EA";

	// 申請類型 REQ_TYPE_CODE（Table 6.5）
	public static final String REQ_ADD = "ADD";
	public static final String REQ_MODIFY = "MODIFY";
	public static final String REQ_DISABLE = "DISABLE";
	public static final String REQ_ENABLE = "ENABLE";
	public static final String REQ_DELETE = "DELETE";
	public static final List<String> REQ_TYPES = List.of(REQ_ADD, REQ_MODIFY, REQ_DISABLE, REQ_ENABLE, REQ_DELETE);
	/** 對既有帳號可發起的類型，順序即 allowedReqTypes 的回傳順序 */
	public static final List<String> CHANGE_REQ_TYPES = List.of(REQ_MODIFY, REQ_DISABLE, REQ_ENABLE, REQ_DELETE);
	/** 只填原因、內容複製主表的類型（spec §4.3） */
	public static final List<String> REASON_ONLY_TYPES = List.of(REQ_DISABLE, REQ_DELETE);
	public static final Map<String, String> REQ_TYPE_NAMES;

	// 申請單狀態 REQ_STATE_CODE
	public static final String RS_REVIEW = "REVIEW";
	public static final String RS_EXEC = "EXEC";
	public static final String RS_VERIFY = "VERIFY";
	public static final String RS_APPROVED = "APPROVED";
	public static final String RS_REJECTED = "REJECTED";
	public static final List<String> REQ_STATES = List.of(RS_REVIEW, RS_EXEC, RS_VERIFY, RS_APPROVED, RS_REJECTED);
	public static final List<String> RS_IN_FLIGHT = List.of(RS_REVIEW, RS_EXEC, RS_VERIFY);

	// 帳號狀態 ACCT_STATE_CODE（與申請單狀態是不同集合）
	public static final String AS_REVIEW = "REVIEW";
	public static final String AS_APPROVED = "APPROVED";
	public static final String AS_EXPIRED = "EXPIRED";
	public static final String AS_DISABLED = "DISABLED";
	public static final String AS_REJECTED = "REJECTED";
	public static final String AS_DELETED = "DELETED";
	public static final List<String> ACCT_STATES = List.of(AS_REVIEW, AS_APPROVED, AS_EXPIRED, AS_DISABLED, AS_REJECTED,
			AS_DELETED);

	/** 申請前提：目標帳號 ACCT_STATE_CODE（Table 6.5；ADD 無前提） */
	public static final Map<String, List<String>> REQ_PREREQ;

	// 帳號來源
	public static final String SOURCE_APPLY = "APPLY";

	// 關卡（IM_ACCT_APPR_STAGE.STAGE_ID，Table 6.4）
	public static final int STAGE_SUBMIT = 1;
	public static final int STAGE_FIRST_APPR = 2;
	public static final int STAGE_LAST_APPR = 5;
	public static final int STAGE_EXEC = 6;
	public static final int STAGE_VERIFY = 7;

	// 歷史 IM_ACCT_HIST
	public static final String HIST_SRC_REQ = "REQ";
	public static final String HIST_SRC_SYS = "SYS";
	/** 緊急存取事件（EMG_ID 有值、REQ_ID／STAGE_ID／APPLY_DEPT 空） */
	public static final String HIST_SRC_EMG = "EMG";
	public static final String ACT_SUBMIT = "SUBMIT";
	public static final String ACT_RESUB = "RESUB";
	public static final String ACT_APPROVE = "APPROVE";
	public static final String ACT_REJECT = "REJECT";
	public static final String ACT_EXEC_DONE = "EXEC_DONE";
	public static final String ACT_VERIFY_OK = "VERIFY_OK";
	public static final String ACT_VERIFY_REJECT = "VERIFY_REJECT";
	public static final String ACT_EXPIRE = "EXPIRE";
	/** 緊急存取異動：主管處理（補核准或退回）時依申請人回報的受影響帳號逐筆寫入（S4-⑧） */
	public static final String ACT_EMG_MODIFY = "EMG_MODIFY";
	/** 緊急存取結案登記異動：異常結案發現有異動時依結案登記的受影響帳號逐筆寫入，不覆蓋申請人回報（D13，S6） */
	public static final String ACT_EMG_CLOSE_MODIFY = "EMG_CLOSE_MODIFY";
	/** 判斷 IS_SELF_ACTION 的動作（spec §4.8） */
	public static final List<String> SELF_CHECK_ACTIONS = List.of(ACT_APPROVE, ACT_REJECT, ACT_EXEC_DONE, ACT_VERIFY_OK,
			ACT_VERIFY_REJECT);
	public static final String SELF_LABEL_APPLICANT = "自行核准";
	public static final String SELF_LABEL_SAME_PERSON = "同人兼任";
	/** EMG_CLOSE_MODIFY 的結案人即申請人（S6-③） */
	public static final String SELF_LABEL_CLOSE = "自行結案";

	// 附件 ATTACH_TYPE_CODE
	public static final String ATTACH_DOC = "DOC";
	public static final String ATTACH_PROOF = "PROOF";

	// 通知事件 EVENT_CODE、參照 REF_TYPE_CODE、結果 RESULT_CODE（Table 6.5）
	public static final String EV_SUBMIT = "SUBMIT";
	public static final String EV_RESUB = "RESUB";
	public static final String EV_NEXT_STAGE = "NEXT_STAGE";
	public static final String EV_TO_EXEC = "TO_EXEC";
	public static final String EV_REJECT = "REJECT";
	public static final String EV_EXEC_DONE = "EXEC_DONE";
	public static final String EV_VERIFY_REJECT = "VERIFY_REJECT";
	public static final String EV_EXPIRY_7D = "EXPIRY_7D";
	public static final String EV_EXPIRED = "EXPIRED";
	public static final String EV_WEEKLY_SUMMARY = "WEEKLY_SUMMARY";
	public static final String EV_JOB_FAIL = "JOB_FAIL";
	public static final String EV_EMG_SUBMIT = "EMG_SUBMIT";
	public static final String EV_EMG_OVERDUE = "EMG_OVERDUE";
	public static final String EV_EMG_RETURN = "EMG_RETURN";
	public static final String REF_REQ = "REQ";
	public static final String REF_ACCT = "ACCT";
	public static final String REF_WEEK = "WEEK";
	public static final String REF_JOB = "JOB";
	public static final String REF_EMG = "EMG";
	public static final String NR_SUCCESS = "SUCCESS";
	public static final String NR_FAIL = "FAIL";
	public static final String NR_SKIP = "SKIP";

	// 排程 JOB_CODE、RESULT_CODE
	public static final String JOB_J01 = "J01";
	public static final String JOB_J02 = "J02";
	public static final List<String> JOB_CODES = List.of(JOB_J01, JOB_J02);
	public static final String JR_RUNNING = "RUNNING";
	public static final String JR_SUCCESS = "SUCCESS";
	public static final String JR_PARTIAL = "PARTIAL";
	public static final String JR_FAIL = "FAIL";

	// 管理軌跡 IM_ACCT_ADMIN_LOG
	public static final String ADM_IMPERSONATE = "IMPERSONATE";
	public static final String ADM_IMPERSONATE_END = "IMPERSONATE_END";
	public static final String ADM_SUCCESS = "SUCCESS";
	public static final String ADM_DENY = "DENY";

	/** 所有啟用中使用者自動擁有、不存 IM_ACCT_ROLE_USER 的角色（Table 6.1）；只用來展開權限，不判斷角色名稱 */
	public static final String ROLE_GENERAL = "GENERAL";
	/** 到期前 7 天、轉已逾期、每週彙總信的收件角色（SA 7.3；spec §4.12 變更單 #2）；只用來查收件人，不判斷權限 */
	public static final String MAIL_ROLE_EXPIRY = "GOV";
	/** 排程失敗信的收件角色（SA 7.3；spec §4.12 變更單 #2）；只用來查收件人，不判斷權限 */
	public static final String MAIL_ROLE_JOB_FAIL = "SYS_ADMIN";

	// 權限碼（IM_ACCT_PERM，Table 6.2；只列第一階段程式會判斷的；關卡權限取自 IM_ACCT_APPR_STAGE.PERM_ID）
	public static final String PERM_ACC_APPLY = "ACC_APPLY";
	public static final String PERM_ACC_SELF_SERVICE = "ACC_SELF_SERVICE";
	public static final String PERM_ACC_LIST_FULL = "ACC_LIST_FULL";
	public static final String PERM_ACC_EXPIRY_ALL = "ACC_EXPIRY_ALL";
	public static final String PERM_INSP_VIEW = "INSP_VIEW";
	public static final String PERM_INSP_CREATE = "INSP_CREATE";
	public static final String PERM_INSP_RESOLVE = "INSP_RESOLVE";
	public static final String PERM_SYS_IMPERSONATE = "SYS_IMPERSONATE";
	public static final String PERM_SYS_JOB_RUN = "SYS_JOB_RUN";
	public static final String PERM_EMG_APPLY = "EMG_APPLY";
	public static final String PERM_EMG_VIEW_ALL = "EMG_VIEW_ALL";
	public static final String PERM_EMG_DECIDE = "EMG_DECIDE";

	// 類別、期限、門禁（Table 6.5、spec §4.9）
	public static final String CATG_DOOR = "DOOR";
	public static final String EXPIRY_PERM = "PERM";
	public static final String EXPIRY_DATE = "DATE";
	public static final List<String> EXPIRY_TYPES = List.of(EXPIRY_PERM, EXPIRY_DATE);
	public static final List<String> DOOR_SCOPES = List.of("TEST", "FULL");
	/** 一般帳號可選的權限範圍（DOOR 只給門禁卡，由後端強制） */
	public static final List<String> ACCESS_LEVELS = List.of("RO", "OP", "ADMIN");
	public static final String DOOR_SYS_NAME = "機房門禁管制系統";
	public static final String DOOR_ACCESS_LEVEL = "DOOR";
	public static final String DOOR_NO_CARD_NAME = "（門禁卡，待發卡）";

	// 審查、到期
	public static final int REVIEW_CYCLE_DAYS = 180;
	/** 到期提醒範圍：到期日 ≤ 今天＋30（spec §4.11） */
	public static final int EXPIRY_WARN_DAYS = 30;
	/** J01 到期前 7 天信 */
	public static final int EXPIRY_NOTICE_DAYS = 7;

	// 巡檢
	public static final String RESULT_NORMAL = "NORMAL";
	public static final String RESULT_EXCPT = "EXCPT";
	public static final List<String> RESULT_CODES = List.of(RESULT_NORMAL, RESULT_EXCPT);
	public static final String INSP_NORMAL = "NORMAL";
	public static final String INSP_EXCPT = "EXCPT";
	public static final String INSP_FIXED = "FIXED";
	public static final List<String> INSP_STATES = List.of(INSP_NORMAL, INSP_EXCPT, INSP_FIXED);

	// 緊急存取（BACKLOG 第 46 項；值域依 IM_EMG_ACCESS 的 COMMENT）
	/** 計算狀態 EMG_STATE_CODE（不存欄位，由 EmgDao.STATE_EXPR 算出）：待補核准、逾期未補核准、已補核准、已退回、已退回（已結案） */
	public static final String ES_PENDING = "PENDING";
	public static final String ES_OVERDUE = "OVERDUE";
	public static final String ES_APPROVED = "APPROVED";
	public static final String ES_RETURNED = "RETURNED";
	public static final String ES_CLOSED = "CLOSED";
	public static final List<String> EMG_STATES = List.of(ES_PENDING, ES_OVERDUE, ES_APPROVED, ES_RETURNED, ES_CLOSED);
	/** 申請人回報 REPORT_TYPE_CODE */
	public static final String EMG_REPORT_NONE = "NONE";
	public static final String EMG_REPORT_MODIFIED = "MODIFIED";
	/** 主管處理 DECIDE_CODE */
	public static final String EMG_DECIDE_APPROVE = "APPROVE";
	public static final String EMG_DECIDE_RETURN = "RETURN";
	/** 結案發現 CLOSE_RESULT_CODE */
	public static final String EMG_CLOSE_NO_CHANGE = "NO_CHANGE";
	public static final String EMG_CLOSE_CHANGED = "CHANGED";
	/** 受影響帳號登記來源 IM_EMG_AFFECT_ACCT.AFFECT_TYPE_CODE */
	public static final String AFFECT_REPORT = "REPORT";
	public static final String AFFECT_CLOSE = "CLOSE";
	/** 審查異常旗標來源 IM_ACCT.REVIEW_FLAG_SOURCE_CODE：緊急存取（退回且申請人回報為異動，S4-①） */
	public static final String FLAG_SRC_EMG = "EMG";
	/** 補核准期限＝送出＋24 小時（SA §5.11） */
	public static final int EMG_DUE_HOURS = 24;
	/** 預估時長（小時，整數）的範圍（裁示②） */
	public static final int EMG_HOUR_MIN = 1;
	public static final int EMG_HOUR_MAX = 24;
	/** 回報「異動了既有帳號」時受影響帳號的上限（S4-③；下限 1） */
	public static final int EMG_AFFECT_MAX = 20;

	// 系統寫入
	/** 系統排程寫入時的 CREATE_BY／UPDATE_BY（Table 第 2 節） */
	public static final String SYSTEM_USER = "SYSTEM";
	/** 系統排程事件的 ACT_BY_NAME（spec §4.8） */
	public static final String SYSTEM_ACTOR_NAME = "系統排程";

	// 登入（spec §2.1；程式常數，spec §9.4）
	public static final int LOGIN_MAX_FAIL = 5;
	public static final int TOKEN_MAX_HOURS = 8;
	/** 閒置上限；與 TOKEN_MAX_HOURS 同長，等於不另設閒置限制（BACKLOG 第 28 項）；要縮短時改成分鐘數字 */
	public static final int TOKEN_IDLE_MINUTES = TOKEN_MAX_HOURS * 60;
	/** LAST_ACCESS_DATE 節流：距上次寫入達此秒數才更新 */
	public static final int TOKEN_TOUCH_SECONDS = 60;
	public static final int TOKEN_BYTES = 32;

	// 附件（spec §4.10）
	public static final long ATTACH_MAX_BYTES = 10L * 1024 * 1024;

	static {
		Map<String, String> names = new LinkedHashMap<>();
		names.put(REQ_ADD, "新增");
		names.put(REQ_MODIFY, "修改");
		names.put(REQ_DISABLE, "停用");
		names.put(REQ_ENABLE, "重新啟用");
		names.put(REQ_DELETE, "刪除");
		REQ_TYPE_NAMES = Map.copyOf(names);

		Map<String, List<String>> pre = new LinkedHashMap<>();
		pre.put(REQ_MODIFY, List.of(AS_APPROVED));
		pre.put(REQ_DISABLE, List.of(AS_APPROVED, AS_EXPIRED));
		pre.put(REQ_ENABLE, List.of(AS_DISABLED));
		pre.put(REQ_DELETE, List.of(AS_APPROVED, AS_DISABLED, AS_EXPIRED));
		REQ_PREREQ = Map.copyOf(pre);
	}

	/** 申請類型中文名稱；未知回原碼 */
	public static String reqTypeName(String reqTypeCode) {
		return reqTypeCode == null ? "" : REQ_TYPE_NAMES.getOrDefault(reqTypeCode, reqTypeCode);
	}
}
