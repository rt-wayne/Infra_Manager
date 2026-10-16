package com.mpx.infra_manager.common;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：錯誤訊息文案（spec v2.0 §4.15，代號 E01～E20、A01～A03、P01、N01）與對應的 ApiException 工廠
//           文案原樣給前端顯示；帶參數的（E02、E10、E11）由方法組字；文件未定文案的 400 類集中在下方
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S2：緊急存取送出欄位的 E10 受詞（帳號類別、帳號或系統、緊急事由、預估時長）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S4a：緊急存取回報欄位的 E10／E11 受詞（回報結果、受影響帳號、回報說明、帳號異動說明）
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S4b-1：退回原因受詞；補核准前須有申請人回報的文案（S4-⑦）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S6：異常結案欄位的 E10／E11 受詞（結案發現、結案說明）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// ============================================================

import java.util.List;

public final class AcctMsg {

	private AcctMsg() {
	}

	public static final String E01 = "請勾選帳號唯一性聲明";
	public static final String E02_PREFIX = "請填寫必填欄位：";
	public static final String E03 = "請填寫到期日";
	public static final String E04 = "到期日不可早於今天";
	public static final String E05 = "此帳號類別尚未開放申請";
	public static final String E06 = "此資產分類碼不可選用";
	public static final String E07 = "此帳號目前已有一筆申請正在處理中";
	public static final String E08 = "此申請已由他人處理，請重新整理";
	public static final String E09 = "此帳號目前的狀態不可提出此類申請";
	public static final String E10_PREFIX = "請填寫";
	public static final String E11_PREFIX = "欄位長度超過上限：";
	public static final String E12 = "請上傳執行佐證附件";
	public static final String E13 = "請填寫門禁卡號";
	public static final String E14 = "附件格式僅限 .pdf .doc .docx .jpg .png";
	public static final String E15 = "附件大小不可超過 10 MB";
	public static final String E16 = "排程執行中，請稍後再試";
	public static final String E17 = "此環境不允許指定基準日";
	public static final String E18 = "巡檢項目不完整，請重新整理後再填寫";
	public static final String E19 = "此項目已處理或不是異常項目";
	public static final String E20 = "目前未切換身分";
	public static final String A01 = "帳號或密碼錯誤";
	public static final String A02 = "此帳號已鎖定，請聯絡系統管理員";
	public static final String A03 = "登入已逾時，請重新登入";
	public static final String P01 = "您沒有執行此操作的權限";
	public static final String N01 = "查無資料";

	// E10 的受詞
	public static final String WHAT_REASON = "原因";
	public static final String WHAT_EXCPT = "異常說明";
	public static final String WHAT_FIX = "處理方式與結果";
	// 緊急存取送出欄位（BACKLOG 第 46 項）
	public static final String WHAT_EMG_CATG = "帳號類別";
	public static final String WHAT_EMG_TARGET = "帳號或系統";
	public static final String WHAT_EMG_REASON = "緊急事由";
	public static final String WHAT_EMG_HOURS = "預估時長";
	// 緊急存取回報欄位（BACKLOG 第 46 項 S4a）
	public static final String WHAT_EMG_REPORT_TYPE = "回報結果";
	public static final String WHAT_EMG_AFFECT = "受影響帳號";
	public static final String WHAT_EMG_REPORT_DESCR = "回報說明";
	public static final String WHAT_EMG_AFFECT_DESCR = "帳號異動說明";
	// 緊急存取主管處理欄位（BACKLOG 第 46 項 S4b）
	public static final String WHAT_EMG_RETURN_REASON = "退回原因";
	// 緊急存取異常結案欄位（BACKLOG 第 46 項 S6）
	public static final String WHAT_EMG_CLOSE_RESULT = "結案發現";
	public static final String WHAT_EMG_CLOSE_DESCR = "結案說明";

	// 文件未定文案、由 developer 補的（400 類）
	public static final String BAD_CODE_PREFIX = "欄位內容不正確：";
	/** 補核准須先有申請人回報（S4-⑦，刻意偏離 SA v0.5 §5.11；未回報只能退回） */
	public static final String EMG_APPROVE_NO_REPORT = "申請人尚未回報，不能補核准";
	public static final String BAD_DATE = "日期格式錯誤（yyyy-MM-dd）";
	public static final String BAD_DATE_TIME = "時間格式錯誤（yyyy-MM-dd HH:mm:ss）";
	public static final String ADD_WITH_ACCT = "新增申請不可指定帳號";
	public static final String IMPERSONATE_SELF = "不可切換為自己";
	public static final String INSP_DATE_FUTURE = "巡檢時間不可晚於現在";

	public static ApiException required(List<String> fields) {
		return ApiException.badRequest(E02_PREFIX + String.join("、", fields));
	}

	public static ApiException required(String field) {
		return ApiException.badRequest(E02_PREFIX + field);
	}

	/** E10：請填寫{原因／異常說明／處理方式與結果} */
	public static ApiException fillIn(String what) {
		return ApiException.badRequest(E10_PREFIX + what);
	}

	public static ApiException tooLong(String field) {
		return ApiException.badRequest(E11_PREFIX + field);
	}

	public static ApiException badCode(String field) {
		return ApiException.badRequest(BAD_CODE_PREFIX + field);
	}

	public static ApiException forbidden() {
		return ApiException.forbidden(P01);
	}

	public static ApiException notFound() {
		return ApiException.notFound(N01);
	}

	public static ApiException concurrent() {
		return ApiException.conflict(E08);
	}

	public static ApiException unauthorized() {
		return ApiException.unauthorized(A03);
	}
}
