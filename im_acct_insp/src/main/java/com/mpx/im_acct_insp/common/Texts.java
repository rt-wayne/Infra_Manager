package com.mpx.im_acct_insp.common;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：文字共用處理（spec v2.0 §2.2 顯示名格式、§3 欄寬與 LIKE 跳脫）
//           自 AccountService／RequestService 搬出 trimToNull、escapeLike；
//           欄寬：n CHAR 以字數（code point）、VARCHAR2(n) 以 UTF-8 byte 計，超過丟 400 E11
// ============================================================

import java.nio.charset.StandardCharsets;

public final class Texts {

	private Texts() {
	}

	public static String trimToNull(String s) {
		if (s == null) {
			return null;
		}
		String t = s.trim();
		return t.isEmpty() ? null : t;
	}

	/** LIKE 關鍵字跳脫：\ → \\、% → \%、_ → \_（SQL 搭配 ESCAPE '\'）；null 回 null */
	public static String escapeLike(String s) {
		if (s == null) {
			return null;
		}
		return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	/** 顯示名「英文名 (中文名)」，中文名空白時只顯示英文名；英文名也空白時退回中文名，再退回工號 */
	public static String displayName(String enName, String zhName, String userId) {
		String en = trimToNull(enName);
		String zh = trimToNull(zhName);
		if (en == null) {
			return zh != null ? zh : userId;
		}
		return zh == null ? en : en + " (" + zh + ")";
	}

	/** n CHAR 欄位：字數（code point）超過 max 丟 E11 */
	public static String maxChars(String label, String value, int max) {
		if (value != null && value.codePointCount(0, value.length()) > max) {
			throw AcctMsg.tooLong(label);
		}
		return value;
	}

	/** VARCHAR2(n) BYTE 欄位：UTF-8 位元組數超過 max 丟 E11 */
	public static String maxBytes(String label, String value, int max) {
		if (value != null && value.getBytes(StandardCharsets.UTF_8).length > max) {
			throw AcctMsg.tooLong(label);
		}
		return value;
	}

	/** 寫入 RESULT_DESCR、ERR_MSG 等組字欄位前截斷（以字數計），避免超過欄寬 */
	public static String truncate(String value, int max) {
		if (value == null || value.codePointCount(0, value.length()) <= max) {
			return value;
		}
		return value.substring(0, value.offsetByCodePoints(0, max));
	}
}
