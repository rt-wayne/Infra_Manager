package com.mpx.infra_manager_java.web;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：CSRF token 只從 header（X-IM-XSRF）讀（S6 回合三 B5）。
//           Spring 內建的 CsrfTokenRequestAttributeHandler 在 header 沒帶時會退回 request.getParameter("_csrf")；
//           對 multipart 請求，Tomcat 在 getParameter 時就會解析整個本文並把每個 part 寫成暫存檔——
//           等於沒帶 CSRF header 的請求也能讓伺服器先寫磁碟、之後才回 403。本系統前端一律送 header，
//           不需要表單參數這條路，所以整個關掉
// ============================================================

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import jakarta.servlet.http.HttpServletRequest;

public class HeaderOnlyCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {

	@Override
	public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
		return request.getHeader(csrfToken.getHeaderName());
	}
}
