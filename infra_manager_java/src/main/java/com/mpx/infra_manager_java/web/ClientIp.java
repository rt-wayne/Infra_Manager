package com.mpx.infra_manager_java.web;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：認證相關 log 用的來源 IP（S2 回合一 code review 第 6 項，裁示 ②A）。
//           後端只被殼 jar 轉發器呼叫，getRemoteAddr() 永遠是殼 jar；真實來源在殼 jar 附的 X-Forwarded-For 第一段。
//           3202 對誰開放尚未定案（BACKLOG 第 80 項），後端目前無法確認這個 header 真的來自殼 jar，
//           所以 log 欄位名用 srcIp 並視為「來源未驗證」，只供事後追查線索，不得拿來做授權或限流判斷。
//           長度上限 64、只留英數與 . : % - _（IPv6 zone id 會帶介面名），避免把任意字串（含換行）寫進 log。
// ============================================================

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIp {

	static final String FORWARDED_FOR = "X-Forwarded-For";
	static final int MAX_LENGTH = 64;

	private ClientIp() {
	}

	/**
	 * X-Forwarded-For 第一段（殼 jar 附上的瀏覽器 IP），沒有就 getRemoteAddr()；
	 * 只允許英數、.、:、%、-、_（IPv4、IPv6 含 zone id 如 %eth0），其他字元或過長一律回 "?"
	 */
	public static String of(HttpServletRequest request) {
		String forwarded = request.getHeader(FORWARDED_FOR);
		String ip;
		if (forwarded != null && !forwarded.isBlank()) {
			int comma = forwarded.indexOf(',');
			ip = (comma >= 0 ? forwarded.substring(0, comma) : forwarded).trim();
		} else {
			ip = request.getRemoteAddr();
		}
		if (ip == null || ip.isEmpty() || ip.length() > MAX_LENGTH) {
			return "?";
		}
		for (int i = 0; i < ip.length(); i++) {
			char c = ip.charAt(i);
			boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '.'
					|| c == ':' || c == '%' || c == '-' || c == '_';
			if (!ok) {
				return "?";
			}
		}
		return ip;
	}
}
