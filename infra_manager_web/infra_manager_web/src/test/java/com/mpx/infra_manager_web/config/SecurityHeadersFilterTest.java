package com.mpx.infra_manager_web.config;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-06
// 變更說明: 新增：SecurityHeadersFilter 單元測試（第 83 項 ①A）。驗三個標頭都有、chain 有往下走、
//           下游即使回 4xx／5xx 標頭仍在
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class SecurityHeadersFilterTest {

	private final SecurityHeadersFilter filter = new SecurityHeadersFilter();

	@Test
	void 靜態頁回應帶三個資安標頭且chain有往下走() throws ServletException, IOException {
		MockHttpServletRequest req = new MockHttpServletRequest("GET", "/infra_manager_web/index.html");
		MockHttpServletResponse res = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(req, res, chain);

		assertThat(chain.getRequest()).isSameAs(req);
		assertThat(res.getHeader(SecurityHeadersFilter.NOSNIFF)).isEqualTo("nosniff");
		assertThat(res.getHeader(SecurityHeadersFilter.FRAME)).isEqualTo("DENY");
		assertThat(res.getHeader(SecurityHeadersFilter.REFERRER)).isEqualTo("same-origin");
	}

	@Test
	void 下游回錯誤狀態時標頭仍在() throws ServletException, IOException {
		MockHttpServletRequest req = new MockHttpServletRequest("GET", "/infra_manager_web/api/v1/apps");
		MockHttpServletResponse res = new MockHttpServletResponse();
		HttpServlet failing = new HttpServlet() {
			@Override
			protected void service(HttpServletRequest rq, HttpServletResponse rs) {
				rs.setStatus(502);
			}
		};

		filter.doFilter(req, res, new MockFilterChain(failing));

		assertThat(res.getStatus()).isEqualTo(502);
		assertThat(res.getHeader(SecurityHeadersFilter.NOSNIFF)).isEqualTo("nosniff");
		assertThat(res.getHeader(SecurityHeadersFilter.FRAME)).isEqualTo("DENY");
	}
}
