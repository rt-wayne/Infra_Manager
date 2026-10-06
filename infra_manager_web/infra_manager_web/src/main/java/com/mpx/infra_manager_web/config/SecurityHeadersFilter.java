package com.mpx.infra_manager_web.config;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-06
// 變更說明: 新增：殼 jar 全站資安回應標頭（BACKLOG 第 83 項，裁示 ①A）。靜態頁與 /api/v1/** 轉發回應一律加上
//           X-Content-Type-Options: nosniff、X-Frame-Options: DENY、Referrer-Policy: same-origin；
//           在 chain 之前設定，轉發器與 Spring 的錯誤頁都會帶到。CSP 與 Cache-Control 透傳仍留在第 83 項
// ============================================================

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter extends OncePerRequestFilter {

	static final String NOSNIFF = "X-Content-Type-Options";
	static final String FRAME = "X-Frame-Options";
	static final String REFERRER = "Referrer-Policy";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		response.setHeader(NOSNIFF, "nosniff");
		response.setHeader(FRAME, "DENY");
		response.setHeader(REFERRER, "same-origin");
		chain.doFilter(request, response);
	}
}
