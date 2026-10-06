package com.mpx.infra_manager_java.util;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-05
// 變更說明: 新增：使用者自由文字欄位的字數檢核（S1，PRD「CLOB 欄位長度」裁示）
//           字數以 Unicode code point 計（一個中文字、一個 emoji 都算 1），不用 String.length()（UTF-16 單位）也不用 @Size；
//           檢核前先把 CRLF / CR 統一成 LF，避免 Windows 換行讓同一段文字多算字數。
//           兩級上限：改型別的 8 個 CLOB 欄 2000 字（LIMIT_SHORT）；原本就是 CLOB 的 4 欄 20000 字（LIMIT_LONG）。
//           check() 回傳正規化後的字串，呼叫端應以回傳值寫入 DB。
//           2026-10-06 code review：check() 多帶中文標籤 label，例外訊息用它組；field 填 API 的 JSON 欄位名，不填 DB 欄名。
// ============================================================

public final class TextLength {

	/** 改型別的 8 個 CLOB 欄位上限（字） */
	public static final int LIMIT_SHORT = 2000;
	/** 原本就是 CLOB 的 4 個欄位上限（字） */
	public static final int LIMIT_LONG = 20000;

	private TextLength() {
	}

	/** 換行統一成 LF；null 原樣回傳 */
	public static String normalize(String value) {
		if (value == null) {
			return null;
		}
		return value.replace("\r\n", "\n").replace('\r', '\n');
	}

	/** 正規化後的 code point 數；null 視為 0 */
	public static int length(String value) {
		if (value == null) {
			return 0;
		}
		String n = normalize(value);
		return n.codePointCount(0, n.length());
	}

	/**
	 * 超過 max 字丟 TextTooLongException（→ 400）；通過則回傳正規化後的字串
	 * @param field API 的 JSON 欄位名（camelCase），回給前端標示哪一欄
	 * @param label 畫面上的中文欄位名，用來組錯誤訊息
	 */
	public static String check(String field, String label, String value, int max) {
		String n = normalize(value);
		int len = n == null ? 0 : n.codePointCount(0, n.length());
		if (len > max) {
			throw new TextTooLongException(field, label, max, len);
		}
		return n;
	}
}
