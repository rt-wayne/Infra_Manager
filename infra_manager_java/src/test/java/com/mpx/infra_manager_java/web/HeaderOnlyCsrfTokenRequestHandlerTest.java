package com.mpx.infra_manager_java.web;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：CSRF token 只讀 header 的單元測試（S6 回合三 B5）。
//           鎖定：有 header 回 header 值；沒有 header 回 null，而且完全不碰 getParameter／getParts——
//           對 multipart 請求，碰了就會讓 Tomcat 解析本文、寫暫存檔（MockMvc 的假請求不會真的寫檔，所以在這一層鎖）
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;

import com.mpx.infra_manager_java.config.SecurityConfig;

import jakarta.servlet.http.HttpServletRequest;

class HeaderOnlyCsrfTokenRequestHandlerTest {

	private final CsrfToken token = new DefaultCsrfToken(SecurityConfig.XSRF_HEADER, "_csrf", "token-value");
	private final HeaderOnlyCsrfTokenRequestHandler handler = new HeaderOnlyCsrfTokenRequestHandler();

	@Test
	void 有header回header值() {
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getHeader(SecurityConfig.XSRF_HEADER)).thenReturn("abc");

		assertThat(handler.resolveCsrfTokenValue(request, token)).isEqualTo("abc");
	}

	@Test
	void 沒有header回null且不讀表單參數也不解析multipart() throws Exception {
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getContentType()).thenReturn("multipart/form-data; boundary=x");

		assertThat(handler.resolveCsrfTokenValue(request, token)).isNull();
		verify(request, never()).getParameter(anyString());
		verify(request, never()).getParameterMap();
		verify(request, never()).getParameterValues(anyString());
		verify(request, never()).getParts();
		verify(request, never()).getInputStream();
	}
}
