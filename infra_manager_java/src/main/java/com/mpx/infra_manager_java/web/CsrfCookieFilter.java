package com.mpx.infra_manager_java.web;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：讓每個請求都把 CSRF token 寫成 IM_XSRF cookie（S2）。
//           Spring Security 6 起 token 延後載入，沒人讀 token 就不會寫 cookie；SPA 第一個請求通常是 GET /api/auth/me，
//           前端要先拿到 cookie 才能在 POST 帶 X-IM-XSRF，所以這裡在 CsrfFilter 之後主動讀一次 token 觸發寫入
//           （Spring Security 官方 SPA 範例的 CsrfCookieFilter）
// ============================================================

import java.io.IOException;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class CsrfCookieFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
		if (token != null) {
			token.getToken();
		}
		chain.doFilter(request, response);
	}
}
