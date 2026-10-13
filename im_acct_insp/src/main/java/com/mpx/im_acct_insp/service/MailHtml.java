package com.mpx.im_acct_insp.service;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-11
// 變更說明: 新增：通知信 HTML 版面（BACKLOG 第 27 項，版面比照 Infra_Manager_Java 的信件範本）。
//           標題＝主旨、兩欄表或多欄表、「前往系統」連結、頁尾（自動發送請勿回覆＋發送時間）。
//           所有填入的值（含標籤與主旨）一律以 HtmlUtils 跳脫，多行文字跳脫後再把換行轉成 <br>
//           （Outlook 不支援 white-space: pre-wrap）。
//           連結＝im.acct.insp.mail.site-url＋前端 hash 路徑。網址容錯：去掉 # 之後（從網址列整串貼上時）、
//           結尾的 index.html 與斜線；留空、不是 http:// https:// 開頭、或格式不正確／沒有主機（含空白、雙引號等）
//           時不放連結（後兩者記 WARN）。表格欄位各自指定字型與字級（Outlook 表格不繼承外層樣式）
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S2：新增前端路徑 PATH_EMG（#/emergency，連結文字「緊急存取」）
// ============================================================

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class MailHtml {

	private static final Logger log = LoggerFactory.getLogger(MailHtml.class);
	/** 系統名稱（與前端畫面左上角一致） */
	static final String SITE_NAME = "機房與帳號權限管理系統";
	/** 前端路徑（hash 路由）；連結文字見 pathName */
	public static final String PATH_TODO = "#/todo";
	public static final String PATH_REQUESTS = "#/requests";
	public static final String PATH_HOME = "#/";
	public static final String PATH_EMG = "#/emergency";
	private static final DateTimeFormatter SENT_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	/** 字型；表格欄位要各自再指定一次（Outlook 的表格不繼承 body 字型，會退回新細明體等襯線字） */
	private static final String FONT = "font-family: 'Microsoft JhengHei', Arial, sans-serif;";
	/** 欄位字型與字級（字級同理不繼承 table 上的設定） */
	private static final String CELL_FONT = FONT + " font-size: 14px;";
	private static final String CELL = CELL_FONT + " border: 1px solid #ddd;";
	private static final String HEAD_CELL = CELL_FONT + " border: 1px solid #ddd; background: #fafafa;";

	/** 已去尾端斜線；空字串＝不放連結 */
	private final String siteUrl;
	private final Clock clock;

	public MailHtml(@Value("${im.acct.insp.mail.site-url:}") String siteUrl, Clock clock) {
		this.siteUrl = normalize(siteUrl);
		this.clock = clock;
	}

	/** 回去掉 #、結尾 index.html 與斜線的網址；不可用時回空字串 */
	static String normalize(String url) {
		String u = url == null ? "" : url;
		int hash = u.indexOf('#');
		if (hash >= 0) {
			u = u.substring(0, hash);
		}
		u = u.trim();
		if (u.isEmpty()) {
			return "";
		}
		String lower = u.toLowerCase(Locale.ROOT);
		if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
			log.warn("寄信：im.acct.insp.mail.site-url 不是 http:// 或 https:// 開頭，信件不放「前往系統」連結");
			return "";
		}
		// 用 authority 判斷有無主機（getHost 遇到含底線的主機名會回 null）；不記原值（網址可能帶帳號密碼）
		URI uri = parse(u);
		if (uri == null || uri.getRawAuthority() == null || uri.getRawAuthority().isBlank()) {
			log.warn("寄信：im.acct.insp.mail.site-url 格式不正確或沒有主機（例如含空白、雙引號），信件不放「前往系統」連結");
			return "";
		}
		u = stripSlash(u);
		// 只去路徑結尾的 index.html（主機名剛好叫 index.html 時不動）
		URI trimmed = parse(u);
		if (u.toLowerCase(Locale.ROOT).endsWith("/index.html") && trimmed != null && trimmed.getRawPath() != null
				&& trimmed.getRawPath().toLowerCase(Locale.ROOT).endsWith("/index.html")) {
			u = stripSlash(u.substring(0, u.length() - "index.html".length()));
		}
		return u;
	}

	private static URI parse(String u) {
		try {
			return new URI(u);
		} catch (URISyntaxException e) {
			return null;
		}
	}

	private static String stripSlash(String u) {
		String s = u;
		while (s.endsWith("/")) {
			s = s.substring(0, s.length() - 1);
		}
		return s;
	}

	/**
	 * 整封信的 HTML。
	 *
	 * @param title   標題（通常＝主旨，會跳脫）
	 * @param content 已組好的 HTML 片段（由本類別的 table／rowsTable／para 產生，值已跳脫）
	 * @param path    前往系統的前端路徑（PATH_*）；null＝不放連結
	 */
	public String page(String title, String content, String path) {
		StringBuilder b = new StringBuilder();
		b.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>").append(esc(title)).append("</title></head>");
		b.append("<body style=\"").append(FONT).append(" font-size: 14px; color: #333;\">");
		b.append("<h2 style=\"color: #1a5fb4;\">").append(esc(title)).append("</h2>");
		b.append(content);
		if (path != null && !siteUrl.isEmpty()) {
			b.append("<p style=\"margin: 16px 0;\"><a href=\"").append(esc(siteUrl + "/" + path))
					.append("\" style=\"color: #1a5fb4;\">前往「").append(esc(pathName(path))).append("」</a></p>");
		}
		b.append("<p style=\"margin-top: 24px; font-size: 12px; color: #888; border-top: 1px solid #eee; padding-top: 8px;\">")
				.append("本信件由「").append(SITE_NAME).append("」自動發送，請勿直接回覆。發送時間：")
				.append(LocalDateTime.now(clock).format(SENT_AT)).append("</p>");
		return b.append("</body></html>").toString();
	}

	private static String pathName(String path) {
		return switch (path) {
		case PATH_TODO -> "待辦簽核";
		case PATH_REQUESTS -> "申請單列表";
		case PATH_EMG -> "緊急存取";
		default -> SITE_NAME;
		};
	}

	/** 兩欄表：每列 {標籤, 值}；值含換行時照原樣換行 */
	public static String table(List<String[]> rows) {
		StringBuilder b = new StringBuilder(
				"<table cellpadding=\"6\" cellspacing=\"0\" style=\"border-collapse: collapse; font-size: 14px;\">");
		for (String[] r : rows) {
			b.append("<tr><td width=\"130\" style=\"").append(HEAD_CELL).append("\">").append(esc(r[0])).append("</td>")
					.append("<td style=\"").append(CELL).append("\">").append(multiline(r[1])).append("</td></tr>");
		}
		return b.append("</table>").toString();
	}

	/** 多欄表：第一列為表頭 */
	public static String rowsTable(List<String> header, List<List<String>> rows) {
		StringBuilder b = new StringBuilder(
				"<table cellpadding=\"6\" cellspacing=\"0\" style=\"border-collapse: collapse; font-size: 14px;\"><tr>");
		for (String h : header) {
			b.append("<th style=\"").append(HEAD_CELL).append(" text-align: left;\">").append(esc(h)).append("</th>");
		}
		b.append("</tr>");
		for (List<String> r : rows) {
			b.append("<tr>");
			for (String v : r) {
				b.append("<td style=\"").append(CELL).append("\">").append(esc(v)).append("</td>");
			}
			b.append("</tr>");
		}
		return b.append("</table>").toString();
	}

	/** 區塊標題 */
	public static String heading(String text) {
		return "<h3 style=\"margin: 16px 0 8px;\">" + esc(text) + "</h3>";
	}

	/** 一段文字 */
	public static String para(String text) {
		return "<p>" + multiline(text) + "</p>";
	}

	/** HTML 跳脫（< > & " '）；null 視為空字串 */
	static String esc(String s) {
		return s == null ? "" : HtmlUtils.htmlEscape(s, "UTF-8");
	}

	/** 跳脫後把換行轉成 <br> */
	static String multiline(String s) {
		return esc(s).replace("\r\n", "\n").replace("\r", "\n").replace("\n", "<br>");
	}
}
