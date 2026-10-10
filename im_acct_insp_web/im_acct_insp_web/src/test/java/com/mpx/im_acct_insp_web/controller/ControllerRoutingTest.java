package com.mpx.im_acct_insp_web.controller;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：殼 jar 全部 Controller 路徑對應測試（開發文件 spec_im_acct_insp_v2.0.md 第 7.2 節）
//           standalone MockMvc＋MockRestServiceServer 假後端，不啟動 Spring 環境、不讀 host.properties、不連外
//           驗：7.2 清單 32 條路徑（JobController 帶與不帶 baseDate 各一，共 33 案）各自轉到後端同路徑同方法、Authorization 原樣帶到後端；
//           經 Controller 的 multipart 送出（Content-Type 含 boundary、本文 byte 原樣）、11 MB 超過回 413 不轉送、
//           附件下載回瀏覽器的 Content-Type／Content-Disposition 照抄、Content-Length 為殼 jar 依實際本文重算
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestTemplate;

class ControllerRoutingTest {

	private static final String BACKEND = "http://backend.test:8989/api/v1";

	private static final String TOKEN = "Bearer tok-routing";

	private MockRestServiceServer server;
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		RestTemplate restTemplate = new RestTemplate();
		server = MockRestServiceServer.bindTo(restTemplate).build();
		BackendForwarder fw = new BackendForwarder(restTemplate, BACKEND);
		mvc = MockMvcBuilders.standaloneSetup(new AuthController(fw), new HomeController(fw),
				new AccountController(fw), new RequestController(fw), new AttachmentController(fw),
				new InspectionController(fw), new JobController(fw)).build();
	}

	/** 方法、殼 jar 路徑（/api/v1 之後）、後端路徑；開發文件 7.2 全部路徑 */
	static Stream<Arguments> routes() {
		return Stream.of(
				// AuthController
				Arguments.of("POST", "/auth/login", "/auth/login"),
				Arguments.of("POST", "/auth/logout", "/auth/logout"),
				Arguments.of("POST", "/auth/impersonate", "/auth/impersonate"),
				Arguments.of("POST", "/auth/impersonate/end", "/auth/impersonate/end"),
				Arguments.of("GET", "/me", "/me"),
				Arguments.of("GET", "/users", "/users"),
				// HomeController
				Arguments.of("GET", "/codes", "/codes"),
				Arguments.of("GET", "/home/stats", "/home/stats"),
				Arguments.of("GET", "/home/expiring", "/home/expiring"),
				Arguments.of("GET", "/todos", "/todos"),
				// AccountController
				Arguments.of("GET", "/accounts?keyword=a&expiring=1", "/accounts?keyword=a&expiring=1"),
				Arguments.of("GET", "/accounts/mine", "/accounts/mine"),
				Arguments.of("GET", "/accounts/lookup?acctName=sa", "/accounts/lookup?acctName=sa"),
				Arguments.of("GET", "/accounts/lookup/AR1?acctName=sa", "/accounts/lookup/AR1?acctName=sa"),
				Arguments.of("GET", "/accounts/AR1", "/accounts/AR1"),
				Arguments.of("GET", "/accounts/AR1/history", "/accounts/AR1/history"),
				// RequestController
				Arguments.of("POST", "/requests", "/requests"),
				Arguments.of("GET", "/requests?stateCode=REVIEW", "/requests?stateCode=REVIEW"),
				Arguments.of("GET", "/requests/AR1", "/requests/AR1"),
				Arguments.of("POST", "/requests/AR1/approve", "/requests/AR1/approve"),
				Arguments.of("POST", "/requests/AR1/reject", "/requests/AR1/reject"),
				Arguments.of("POST", "/requests/AR1/resubmit", "/requests/AR1/resubmit"),
				Arguments.of("POST", "/requests/AR1/exec-done", "/requests/AR1/exec-done"),
				Arguments.of("POST", "/requests/AR1/verify-ok", "/requests/AR1/verify-ok"),
				Arguments.of("POST", "/requests/AR1/verify-reject", "/requests/AR1/verify-reject"),
				// AttachmentController
				Arguments.of("GET", "/attachments/15", "/attachments/15"),
				// InspectionController
				Arguments.of("GET", "/inspection-items", "/inspection-items"),
				Arguments.of("POST", "/inspections", "/inspections"),
				Arguments.of("GET", "/inspections?stateCode=EXCPT", "/inspections?stateCode=EXCPT"),
				Arguments.of("GET", "/inspections/IN1", "/inspections/IN1"),
				Arguments.of("POST", "/inspections/IN1/items/3/fix", "/inspections/IN1/items/3/fix"),
				// JobController
				Arguments.of("POST", "/jobs/J01/run?baseDate=2026-10-09", "/jobs/J01/run?baseDate=2026-10-09"),
				Arguments.of("POST", "/jobs/J02/run", "/jobs/J02/run"));
	}

	@ParameterizedTest(name = "{0} {1}")
	@MethodSource("routes")
	void everyRouteForwardsToSameBackendPathWithAuthorization(String httpMethod, String shellPath, String backendPath)
			throws Exception {
		server.expect(requestTo(URI.create(BACKEND + backendPath)))
				.andExpect(method(HttpMethod.valueOf(httpMethod)))
				.andExpect(header(HttpHeaders.AUTHORIZATION, TOKEN))
				.andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

		mvc.perform(request(HttpMethod.valueOf(httpMethod), URI.create("/api/v1" + shellPath))
				.header(HttpHeaders.AUTHORIZATION, TOKEN))
				.andExpect(status().isOk());

		server.verify();
	}

	@Test
	void execDoneMultipartIsForwardedUnparsed() throws Exception {
		String type = "multipart/form-data; boundary=----b0undary";
		byte[] body = ("------b0undary\r\nContent-Disposition: form-data; name=\"proofFile\"; filename=\"p.png\"\r\n"
				+ "Content-Type: image/png\r\n\r\n\u0089PNG\r\n------b0undary--\r\n").getBytes(StandardCharsets.ISO_8859_1);
		server.expect(requestTo(BACKEND + "/requests/AR1/exec-done"))
				.andExpect(header(HttpHeaders.CONTENT_TYPE, type))
				.andExpect(content().bytes(body))
				.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		mvc.perform(post("/api/v1/requests/AR1/exec-done").header(HttpHeaders.AUTHORIZATION, TOKEN)
				.contentType(type).content(body))
				.andExpect(status().isOk());

		server.verify();
	}

	@Test
	void oversizedUploadReturns413AndIsNotForwarded() throws Exception {
		MockHttpServletResponse resp = mvc.perform(post("/api/v1/requests").header(HttpHeaders.AUTHORIZATION, TOKEN)
				.contentType("multipart/form-data; boundary=x").content(new byte[BackendForwarder.MAX_BODY_BYTES + 1]))
				.andExpect(status().is(413))
				.andReturn().getResponse();

		server.verify();
		assertThat(resp.getContentAsString(StandardCharsets.UTF_8)).isEqualTo(BackendForwarder.E15_BODY);
	}

	@Test
	void downloadReachesBrowserWithTypeDispositionAndRecomputedLength() throws Exception {
		byte[] file = new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x00, (byte) 0xFF };
		String disposition = "attachment; filename*=UTF-8''%E4%BD%90%E8%AD%89.png";
		HttpHeaders h = new HttpHeaders();
		h.set(HttpHeaders.CONTENT_DISPOSITION, disposition);
		h.setContentLength(99999);
		server.expect(requestTo(BACKEND + "/attachments/7"))
				.andRespond(withSuccess(file, MediaType.IMAGE_PNG).headers(h));

		MockHttpServletResponse resp = mvc.perform(get("/api/v1/attachments/7").header(HttpHeaders.AUTHORIZATION, TOKEN))
				.andExpect(status().isOk())
				.andReturn().getResponse();

		server.verify();
		assertThat(resp.getContentAsByteArray()).isEqualTo(file);
		assertThat(resp.getContentType()).isEqualTo(MediaType.IMAGE_PNG_VALUE);
		assertThat(resp.getHeader(HttpHeaders.CONTENT_DISPOSITION)).isEqualTo(disposition);
		assertThat(resp.getContentLength()).isEqualTo(file.length);
	}
}
