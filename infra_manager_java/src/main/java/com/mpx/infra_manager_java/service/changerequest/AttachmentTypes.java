package com.mpx.infra_manager_java.service.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：附件副檔名白名單與 MIME 對照表（S6 裁示 ⑧A＝舊系統 accept 清單＋.msg）。
//           下載（回合一-2，第 95 項 N6）與上傳（回合三）共用同一份：MIME 一律由副檔名決定、不信任 DB 或瀏覽器給的值，
//           不在白名單的副檔名回 application/octet-stream
// ============================================================

import java.util.Locale;
import java.util.Map;

public final class AttachmentTypes {

	public static final String OCTET_STREAM = "application/octet-stream";

	private static final Map<String, String> MIME_BY_EXT = Map.ofEntries(
			Map.entry("jpg", "image/jpeg"),
			Map.entry("jpeg", "image/jpeg"),
			Map.entry("png", "image/png"),
			Map.entry("gif", "image/gif"),
			Map.entry("bmp", "image/bmp"),
			Map.entry("webp", "image/webp"),
			Map.entry("pdf", "application/pdf"),
			Map.entry("doc", "application/msword"),
			Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
			Map.entry("xls", "application/vnd.ms-excel"),
			Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
			Map.entry("ppt", "application/vnd.ms-powerpoint"),
			Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
			Map.entry("txt", "text/plain"),
			Map.entry("csv", "text/csv"),
			Map.entry("zip", "application/zip"),
			Map.entry("msg", "application/vnd.ms-outlook"));

	private AttachmentTypes() {
	}

	/** 小寫副檔名（不含點）；沒有副檔名回 null */
	public static String extension(String fileName) {
		if (fileName == null) {
			return null;
		}
		int slash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
		int dot = fileName.lastIndexOf('.');
		if (dot <= slash + 1 || dot == fileName.length() - 1) {
			return null;
		}
		return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	public static boolean isAllowed(String fileName) {
		String ext = extension(fileName);
		return ext != null && MIME_BY_EXT.containsKey(ext);
	}

	public static String mimeFor(String fileName) {
		String ext = extension(fileName);
		return ext == null ? OCTET_STREAM : MIME_BY_EXT.getOrDefault(ext, OCTET_STREAM);
	}
}
