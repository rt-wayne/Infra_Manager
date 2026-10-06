package com.mpx.infra_manager_java.web;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-05
// 變更說明: 新增：BodyLimitFilter 單元測試（S1）：Content-Length 超過直接 413、未超過放行且本文可讀、
//           長度未知時讀超過丟 BodyTooLargeException、multipart 不過濾
//           2026-10-06：加非法 charset → UnsupportedEncodingException（IOException，不是 RuntimeException）
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;

class BodyLimitFilterTest {

	private final BodyLimitFilter filter = new BodyLimitFilter(10);

	private static MockHttpServletRequest jsonRequest(byte[] body) {
		MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/x");
		req.setContentType("application/json");
		req.setContent(body);
		return req;
	}

	@Test
	void ContentLength超過時不讀本文直接回413() throws ServletException, IOException {
		MockHttpServletRequest req = jsonRequest("12345678901".getBytes(StandardCharsets.UTF_8));
		MockHttpServletResponse res = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(req, res, chain);

		assertThat(res.getStatus()).isEqualTo(413);
		assertThat(res.getContentAsString()).contains("請求內容過大");
		assertThat(chain.getRequest()).isNull();
	}

	@Test
	void 未超過時放行且本文完整可讀() throws ServletException, IOException {
		MockHttpServletRequest req = jsonRequest("1234567890".getBytes(StandardCharsets.UTF_8));
		MockHttpServletResponse res = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(req, res, chain);

		assertThat(res.getStatus()).isEqualTo(200);
		HttpServletRequest wrapped = (HttpServletRequest) chain.getRequest();
		assertThat(wrapped).isNotNull();
		assertThat(new String(wrapped.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("1234567890");
	}

	@Test
	void 長度未知時讀超過上限丟BodyTooLargeException() throws ServletException, IOException {
		// 模擬 chunked：MockHttpServletRequest 的 Content-Length 由內容陣列算出，要覆寫才會是未知（-1）
		MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/x") {
			@Override
			public long getContentLengthLong() {
				return -1L;
			}

			@Override
			public int getContentLength() {
				return -1;
			}
		};
		req.setContentType("application/json");
		req.setContent("12345678901".getBytes(StandardCharsets.UTF_8));
		MockHttpServletResponse res = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(req, res, chain);

		HttpServletRequest wrapped = (HttpServletRequest) chain.getRequest();
		assertThat(wrapped).isNotNull();
		assertThatThrownBy(() -> wrapped.getInputStream().readAllBytes())
				.isInstanceOf(BodyTooLargeException.class);
	}

	@Test
	void 非法charset取Reader時丟UnsupportedEncodingException() throws ServletException, IOException {
		MockHttpServletRequest req = jsonRequest("{}".getBytes(StandardCharsets.UTF_8));
		req.setCharacterEncoding("not a charset!!");
		MockHttpServletResponse res = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(req, res, chain);

		HttpServletRequest wrapped = (HttpServletRequest) chain.getRequest();
		assertThatThrownBy(wrapped::getReader).isInstanceOf(UnsupportedEncodingException.class);
	}

	@Test
	void multipart不經本過濾器() throws ServletException, IOException {
		MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/x");
		req.setContentType("multipart/form-data; boundary=abc");
		req.setContent(new byte[100]);
		MockHttpServletResponse res = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(req, res, chain);

		assertThat(res.getStatus()).isEqualTo(200);
		assertThat(chain.getRequest()).isSameAs(req);
	}
}
