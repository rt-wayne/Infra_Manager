package com.mpx.common.web;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-05
// 變更說明: 新增：ApiForwarder 單元測試（規格 v4）；以 MockRestServiceServer 模擬後端，不連真後端
//           驗：POST／GET 轉發到 base＋path、payload 原樣送出、回應原樣回傳；後端 4xx／5xx 與連線失敗時原樣 rethrow
//           審查修正：補「不轉發後端 header」一案
//           複審修正：失敗時預期 ForwardException（cause 為原例外，訊息只含 method 與 path）
//           jdk25 階段 3：getStatusCodeValue 改 getStatusCode().value()；HttpHeaders 不再是 Map（Framework 7），containsKey 改 containsHeader
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

class ApiForwarderTest {

	static final String BASE = "http://api.example.invalid/backend/api/v1";

	private MockRestServiceServer server;
	private ApiForwarder forwarder;

	@BeforeEach
	void setUp() {
		RestTemplate restTemplate = new RestTemplate();
		server = MockRestServiceServer.bindTo(restTemplate).build();
		forwarder = new ApiForwarder(restTemplate, " " + BASE + " ");
	}

	@Test
	void post_forwardsBodyAndReturnsResponseAsIs() {
		server.expect(requestTo(BASE + "/example/search"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().json("{\"keyword\":\"abc\"}"))
				.andRespond(withSuccess("[{\"code\":\"A1\",\"name\":\"n1\",\"extra\":1}]", MediaType.APPLICATION_JSON));

		ResponseEntity<Object> resp = forwarder.post("/example/search", Collections.singletonMap("keyword", "abc"));

		assertThat(resp.getStatusCode().value()).isEqualTo(200);
		assertThat(resp.getBody()).isInstanceOf(List.class);
		@SuppressWarnings("unchecked")
		Map<String, Object> row = ((List<Map<String, Object>>) resp.getBody()).get(0);
		assertThat(row).containsEntry("code", "A1").containsEntry("extra", 1);
		server.verify();
	}

	@Test
	void get_forwardsAndReturnsResponseAsIs() {
		server.expect(requestTo(BASE + "/example/list"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

		ResponseEntity<Object> resp = forwarder.get("/example/list");

		assertThat(resp.getStatusCode().value()).isEqualTo(200);
		assertThat((List<?>) resp.getBody()).isEmpty();
		server.verify();
	}

	@Test
	void response_doesNotForwardBackendHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentLength(2);
		headers.set("X-Backend-Only", "1");
		server.expect(requestTo(BASE + "/example/list"))
				.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON).headers(headers));

		ResponseEntity<Object> resp = forwarder.get("/example/list");

		assertThat(resp.getStatusCode().value()).isEqualTo(200);
		assertThat(resp.getHeaders().containsHeader(HttpHeaders.CONTENT_LENGTH)).isFalse();
		assertThat(resp.getHeaders().containsHeader("X-Backend-Only")).isFalse();
		server.verify();
	}

	@Test
	void post_backend5xx_rethrows() {
		server.expect(requestTo(BASE + "/example/search")).andRespond(withServerError());

		assertThatThrownBy(() -> forwarder.post("/example/search", Collections.emptyMap()))
				.isInstanceOf(ForwardException.class).hasCauseInstanceOf(HttpServerErrorException.class)
				.hasMessage("forward failed POST /example/search");
	}

	@Test
	void get_backend4xx_rethrows() {
		server.expect(requestTo(BASE + "/example/list")).andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThatThrownBy(() -> forwarder.get("/example/list"))
				.isInstanceOf(ForwardException.class).hasCauseInstanceOf(HttpClientErrorException.class)
				.hasMessage("forward failed GET /example/list");
	}

	@Test
	void post_connectionFailure_rethrows() {
		server.expect(requestTo(BASE + "/example/search")).andRespond(request -> {
			throw new IOException("connection refused");
		});

		assertThatThrownBy(() -> forwarder.post("/example/search", Collections.emptyMap()))
				.isInstanceOf(ForwardException.class).hasCauseInstanceOf(ResourceAccessException.class)
				.hasMessageNotContaining("api.example.invalid");
	}
}
