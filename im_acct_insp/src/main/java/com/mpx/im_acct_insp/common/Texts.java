package com.mpx.im_acct_insp.common;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：文字共用處理（spec v2.0 §2.2 顯示名格式、§3 欄寬與 LIKE 跳脫）
//           自 AccountService／RequestService 搬出 trimToNull、escapeLike；
//           欄寬：n CHAR 以字數（code point）、VARCHAR2(n) 以 UTF-8 byte 計，超過丟 400 E11
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S2：列表日期篩選的 parseDate（yyyy-MM-dd 嚴格解析，空值回 null，格式錯丟 400 BAD_DATE）
//           自 InspectionService 搬入，供巡檢與緊急存取列表共用
// ============================================================

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

public final class Texts {

	private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

	private Texts() {
	}

	/** 列表日期篩選：yyyy-MM-dd（嚴格，2026-02-30 不收）；空白回 null，格式錯丟 400 BAD_DATE */
	public static LocalDate parseDate(String s) {
		String v = trimToNull(s);
		if (v == null) {
			return null;
		}
		try {
			return LocalDate.parse(v, DATE_FMT);
		} catch (DateTimeParseException ex) {
			throw ApiException.badRequest(AcctMsg.BAD_DATE);
		}
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
