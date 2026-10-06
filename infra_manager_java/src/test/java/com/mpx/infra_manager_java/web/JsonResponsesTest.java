package com.mpx.infra_manager_java.web;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：JsonResponses 跳脫與 ClientIp 取值的單元測試（S2 回合一 code review 第 6、7 項）
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class JsonResponsesTest {

	@Test
	void 訊息含引號反斜線與控制字元時仍是合法JSON() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		JsonResponses.write(response, 403, "a\"b\\c\nd\u0001");

		assertThat(response.getStatus()).isEqualTo(403);
		assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
		assertThat(response.getContentAsString()).isEqualTo("{\"message\":\"a\\\"b\\\\c\\nd\\u0001\"}");
	}

	@Test
	void 一般中文訊息原樣輸出() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		JsonResponses.write(response, 401, "尚未登入");

		assertThat(response.getContentAsString()).isEqualTo("{\"message\":\"尚未登入\"}");
		assertThat(JsonResponses.escape(null)).isEmpty();
	}

	@Test
	void 來源IP取XForwardedFor第一段_沒有就取remoteAddr() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("10.0.0.5");
		assertThat(ClientIp.of(request)).isEqualTo("10.0.0.5");

		request.addHeader("X-Forwarded-For", "192.168.1.20, 10.0.0.5");
		assertThat(ClientIp.of(request)).isEqualTo("192.168.1.20");
	}

	@Test
	void 來源IP非法字元或過長回問號() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("10.0.0.5");
		request.addHeader("X-Forwarded-For", "evil\ninjected=1");
		assertThat(ClientIp.of(request)).isEqualTo("?");

		MockHttpServletRequest tooLong = new MockHttpServletRequest();
		tooLong.addHeader("X-Forwarded-For", "1".repeat(65));
		assertThat(ClientIp.of(tooLong)).isEqualTo("?");

		MockHttpServletRequest ipv6 = new MockHttpServletRequest();
		ipv6.addHeader("X-Forwarded-For", "fe80::1%eth0");
		assertThat(ClientIp.of(ipv6)).isEqualTo("fe80::1%eth0");
	}
}
