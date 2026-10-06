package com.mpx.infra_manager_java.util;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：日期時間顯示格式（S4）。DB 的 DATE 欄位依裁示存台灣牆上時間，JDBC 取回的 Timestamp
//           直接以 LocalDateTime 格式化、不再做時區換算；「今天」以 Asia/Taipei 計（列表預設 90 天的起點）
// ============================================================

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class TaiwanTime {

	public static final ZoneId ZONE = ZoneId.of("Asia/Taipei");

	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

	private TaiwanTime() {
	}

	/** yyyy-MM-dd HH:mm；null 回 null */
	public static String formatDateTime(Timestamp ts) {
		return ts == null ? null : ts.toLocalDateTime().format(DATE_TIME);
	}

	/** yyyy-MM-dd；null 回 null */
	public static String formatDate(Timestamp ts) {
		return ts == null ? null : ts.toLocalDateTime().toLocalDate().format(DATE);
	}

	public static LocalDate today() {
		return LocalDate.now(ZONE);
	}

	/** 該日 00:00 的 Timestamp，供 DATE 欄位比較用 */
	public static Timestamp startOf(LocalDate date) {
		return Timestamp.valueOf(date.atStartOfDay());
	}
}
