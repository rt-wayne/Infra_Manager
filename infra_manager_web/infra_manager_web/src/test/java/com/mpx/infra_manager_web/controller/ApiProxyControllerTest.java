package com.mpx.infra_manager_web.controller;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：自建轉發器的單元測試（BACKLOG 第 78 項）
//           以 MockMvc standalone 走真實路由、MockRestServiceServer 假扮後端，不啟動 Spring context、不連網
//           驗證：cookie 只轉 IM_ 開頭（含多個 Cookie header）、X-IM-XSRF 與 X-Forwarded-For 有帶、各 method 與查詢字串、本文原樣轉
//                （沒送 Content-Type 就不補）、3xx／4xx／5xx 原樣回（Location 不轉）、Set-Cookie 只回 IM_ 開頭、
//                後端沒給 Content-Type 時標 octet-stream、連不上／逾時回 502 並記整條例外鏈、位址未設定或不合法回 502、
//                路徑跳脫（..、%2e%2e、;）與格式錯誤（%zz、壞 Content-Type、非 /api/v1 開頭）回 400 且後端沒被呼叫、
//                context path 不會被算進後端路徑、base 結尾斜線自動去掉、剛好打 /api/v1、HEAD 轉 OPTIONS 不轉
//           2026-10-06 第二輪複審：補 Content-Length 有帶、非 http(s) 位址回 502、502 的 log 記整條例外鏈
//                （真 JDK 用戶端的行為另見 config/BackendClientConfigTest）
//           2026-10-07 S6 回合一：本文上限邊界（JSON 1 MB、multipart 51 MB，剛好等於放行、多 1 byte 回 413 且不打後端；
//                multipart 判斷不分大小寫）、回應 header 白名單（Content-Disposition 等透傳、其他不轉）
//                （真 Tomcat 串流與 multipart 本文另見 ApiProxyStreamingTest）
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.channels.ClosedChannelException;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import jakarta.servlet.http.Cookie;

@ExtendWith(OutputCaptureExtension.class)
class ApiProxyControllerTest {

	static final String BASE = "http://api.example.invalid:3202/api";
	static final String BAD_REQUEST_JSON = "{\"message\":\"請求格式錯誤\"}";
	static final String BAD_GATEWAY_JSON = "{\"message\":\"後端服務呼叫失敗\"}";

	private MockRestServiceServer server;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = mockMvcFor(" " + BASE + " ");
	}

	/** 建立綁定同一個假後端的 MockMvc；每次呼叫會換掉 server */
	private MockMvc mockMvcFor(String baseUrl) {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		return MockMvcBuilders.standaloneSetup(new ApiProxyController(builder.build(), baseUrl))
				.dispatchOptions(true)
				.build();
	}

	// ---------- 請求 header 與 cookie ----------

	@Test
	void get_forwardsOnlyImCookiesAndXsrfHeader() throws Exception {
		server.expect(requestTo(BASE + "/health"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header(HttpHeaders.COOKIE, "IM_SESSION=abc; IM_XSRF=t1"))
				.andExpect(header(ApiProxyController.XSRF_HEADER, "t1"))
				.andExpect(header(ApiProxyController.FORWARDED_FOR_HEADER, "127.0.0.1"))
				.andRespond(withSuccess("{\"status\":\"UP\"}", MediaType.APPLICATION_JSON));

		mockMvc.perform(get("/api/v1/health")
						.cookie(new Cookie("IM_SESSION", "abc"), new Cookie("JSESSIONID", "zzz"), new Cookie("IM_XSRF", "t1"))
						.header(ApiProxyController.XSRF_HEADER, "t1")
						.header(ApiProxyController.FORWARDED_FOR_HEADER, "1.2.3.4"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
				.andExpect(MockMvcResultMatchers.content().json("{\"status\":\"UP\"}"));
		server.verify();
	}

	@Test
	void get_withoutImCookies_sendsNoCookieHeader() throws Exception {
		server.expect(requestTo(BASE + "/health"))
				.andExpect(headerDoesNotExist(HttpHeaders.COOKIE))
				.andExpect(headerDoesNotExist(ApiProxyController.XSRF_HEADER))
				.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		mockMvc.perform(get("/api/v1/health").cookie(new Cookie("JSESSIONID", "zzz")))
				.andExpect(status().isOk());
		server.verify();
	}

	@Test
	void get_multipleCookieHeaders_areMergedAndFiltered() throws Exception {
		server.expect(requestTo(BASE + "/health"))
				.andExpect(header(HttpHeaders.COOKIE, "IM_SESSION=a; IM_XSRF=b"))
				.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		mockMvc.perform(get("/api/v1/health")
						.header(HttpHeaders.COOKIE, "IM_SESSION=a; other=1")
						.header(HttpHeaders.COOKIE, "JSESSIONID=z; IM_XSRF=b"))
				.andExpect(status().isOk());
		server.verify();
	}

	@Test
	void filterCookies_keepsOnlyImPrefix() {
		assertThat(ApiProxyController.filterCookies(Arrays.asList("a=1; IM_X=2 ;IM_Y=3", null, " b=4"))).isEqualTo("IM_X=2; IM_Y=3");
		assertThat(ApiProxyController.filterCookies(List.of())).isEmpty();
	}

	// ---------- method、路徑、查詢字串、本文 ----------

	@Test
	void put_forwardsMethodQueryStringAndBody() throws Exception {
		server.expect(requestTo(BASE + "/apps/7?x=1&y=%E4%B8%AD"))
				.andExpect(method(HttpMethod.PUT))
				.andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
				.andExpect(header(HttpHeaders.CONTENT_LENGTH, "7"))
				.andExpect(content().json("{\"a\":1}"))
				.andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

		// 用 URI 物件：字串版 put(String) 會把已編碼的 %E4 再編成 %25E4（MockMvc 的 URI 模板展開行為），不是轉發器的問題
		mockMvc.perform(put(URI.create("/api/v1/apps/7?x=1&y=%E4%B8%AD"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"a\":1}"))
				.andExpect(status().isOk())
				.andExpect(MockMvcResultMatchers.content().json("{\"ok\":true}"));
		server.verify();
	}

	@Test
	void post_patch_delete_areForwardedWithSameMethod() throws Exception {
		server.expect(requestTo(BASE + "/apps")).andExpect(method(HttpMethod.POST))
				.andExpect(content().string("{\"n\":1}"))
				.andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("{\"id\":9}"));
		server.expect(requestTo(BASE + "/apps/9")).andExpect(method(HttpMethod.PATCH))
				.andExpect(content().string("{\"n\":2}"))
				.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
		server.expect(requestTo(BASE + "/apps/9")).andExpect(method(HttpMethod.DELETE))
				.andExpect(headerDoesNotExist(HttpHeaders.CONTENT_LENGTH))
				.andRespond(withStatus(HttpStatus.NO_CONTENT));

		mockMvc.perform(post("/api/v1/apps").contentType(MediaType.APPLICATION_JSON).content("{\"n\":1}"))
				.andExpect(status().isCreated())
				.andExpect(MockMvcResultMatchers.content().json("{\"id\":9}"));
		mockMvc.perform(patch("/api/v1/apps/9").contentType(MediaType.APPLICATION_JSON).content("{\"n\":2}"))
				.andExpect(status().isOk());
		mockMvc.perform(delete("/api/v1/apps/9"))
				.andExpect(status().isNoContent())
				.andExpect(header().doesNotExist(HttpHeaders.CONTENT_TYPE))
				.andExpect(MockMvcResultMatchers.content().string(""));
		server.verify();
	}

	@Test
	void post_withoutContentType_doesNotAddOne() throws Exception {
		server.expect(requestTo(BASE + "/apps"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(headerDoesNotExist(HttpHeaders.CONTENT_TYPE))
				.andExpect(content().string("raw"))
				.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		mockMvc.perform(post("/api/v1/apps").content("raw"))
				.andExpect(status().isOk());
		server.verify();
	}

	@Test
	void head_isForwardedAsHead_optionsIsAnsweredLocally() throws Exception {
		server.expect(requestTo(BASE + "/health"))
				.andExpect(method(HttpMethod.HEAD))
				.andRespond(withSuccess());

		mockMvc.perform(head("/api/v1/health")).andExpect(status().isOk());
		mockMvc.perform(options("/api/v1/health"))
				.andExpect(status().isOk())
				.andExpect(header().exists(HttpHeaders.ALLOW));
		server.verify();
	}

	@Test
	void exactPrefix_forwardsToBaseItself() throws Exception {
		server.expect(requestTo(BASE)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		mockMvc.perform(get("/api/v1")).andExpect(status().isOk());
		server.verify();
	}

	@Test
	void contextPath_isNotForwardedToBackend() throws Exception {
		server.expect(requestTo(BASE + "/health"))
				.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		mockMvc.perform(get("/infra_manager_web/api/v1/health").contextPath("/infra_manager_web"))
				.andExpect(status().isOk());
		server.verify();
	}

	@Test
	void baseUrlTrailingSlash_isStripped() throws Exception {
		mockMvc = mockMvcFor(BASE + "///");
		server.expect(requestTo(BASE + "/health")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		mockMvc.perform(get("/api/v1/health")).andExpect(status().isOk());
		server.verify();
	}

	// ---------- 回應 ----------

	@Test
	void backend4xx_isReturnedAsIs() throws Exception {
		server.expect(requestTo(BASE + "/apps"))
				.andRespond(withStatus(HttpStatus.BAD_REQUEST)
						.contentType(MediaType.APPLICATION_JSON)
						.body("{\"message\":\"欄位長度超過上限\",\"field\":\"workDetail\",\"max\":20000,\"actual\":20001}"));

		mockMvc.perform(get("/api/v1/apps"))
				.andExpect(status().isBadRequest())
				.andExpect(MockMvcResultMatchers.content().json(
						"{\"message\":\"欄位長度超過上限\",\"field\":\"workDetail\",\"max\":20000,\"actual\":20001}"));
		server.verify();
	}

	@Test
	void backend5xx_isReturnedAsIs() throws Exception {
		server.expect(requestTo(BASE + "/apps"))
				.andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
						.contentType(MediaType.APPLICATION_JSON)
						.body("{\"message\":\"系統發生錯誤\"}"));

		mockMvc.perform(get("/api/v1/apps"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(MockMvcResultMatchers.content().json("{\"message\":\"系統發生錯誤\"}"));
		server.verify();
	}

	@Test
	void backend302_isReturnedWithoutLocation() throws Exception {
		HttpHeaders headers = new HttpHeaders();
		headers.set(HttpHeaders.LOCATION, "http://api.example.invalid:3202/login");
		server.expect(requestTo(BASE + "/apps"))
				.andRespond(withStatus(HttpStatus.FOUND).headers(headers));

		mockMvc.perform(get("/api/v1/apps"))
				.andExpect(status().isFound())
				.andExpect(header().doesNotExist(HttpHeaders.LOCATION));
		server.verify();
	}

	@Test
	void backendBodyWithoutContentType_isLabelledOctetStream() throws Exception {
		server.expect(requestTo(BASE + "/raw")).andRespond(withSuccess().body("abc"));

		mockMvc.perform(get("/api/v1/raw").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE))
				.andExpect(MockMvcResultMatchers.content().string("abc"));
		server.verify();
	}

	@Test
	void response_returnsOnlyImSetCookies() throws Exception {
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.SET_COOKIE, "IM_SESSION=s1; Path=/; HttpOnly; Secure; SameSite=Lax");
		headers.add(HttpHeaders.SET_COOKIE, "JSESSIONID=zzz; Path=/");
		headers.add(HttpHeaders.SET_COOKIE, "IM_XSRF=t2; Path=/");
		headers.add("X-Backend-Only", "1");
		server.expect(requestTo(BASE + "/auth/login"))
				.andRespond(withSuccess("{\"loggedIn\":true}", MediaType.APPLICATION_JSON).headers(headers));

		mockMvc.perform(get("/api/v1/auth/login"))
				.andExpect(status().isOk())
				// MockHttpServletResponse 會把 Set-Cookie 重新解析再序列化（屬性順序可能變），所以逐屬性比對、不比對整串
				.andExpect(cookie().value("IM_SESSION", "s1"))
				.andExpect(cookie().httpOnly("IM_SESSION", true))
				.andExpect(cookie().secure("IM_SESSION", true))
				.andExpect(cookie().sameSite("IM_SESSION", "Lax"))
				.andExpect(cookie().value("IM_XSRF", "t2"))
				.andExpect(cookie().doesNotExist("JSESSIONID"))
				.andExpect(header().doesNotExist("X-Backend-Only"));
		server.verify();
	}

	// ---------- 502 ----------

	@Test
	void backendUnreachable_returns502AndLogsWholeCauseChain(CapturedOutput output) throws Exception {
		// 模擬 JDK HttpClient 的實際包法：ConnectException 裡再包一層 ClosedChannelException
		ConnectException refused = new ConnectException("connection refused");
		refused.initCause(new ClosedChannelException());
		server.expect(requestTo(BASE + "/health")).andRespond(withException(refused));

		mockMvc.perform(get("/api/v1/health"))
				.andExpect(status().isBadGateway())
				.andExpect(MockMvcResultMatchers.content().json(BAD_GATEWAY_JSON));
		server.verify();
		assertThat(output.getOut()).contains("failed: ConnectException <- ClosedChannelException");
	}

	@Test
	void causeChain_skipsSpringWrapper_andStopsAtCycleOrDepthLimit() {
		Throwable loop = new IOException("a");
		Throwable inner = new IOException("b", loop);
		loop.initCause(inner);
		assertThat(ApiProxyController.causeChain(new ResourceAccessException("x", (IOException) loop)))
				.isEqualTo("IOException <- IOException <- IOException <- IOException <- IOException <- IOException <- IOException <- IOException");
		assertThat(ApiProxyController.causeChain(new ResourceAccessException("no cause"))).isEqualTo("ResourceAccessException");
	}

	@Test
	void backendTimeout_returns502AndLogsCauseChain(CapturedOutput output) throws Exception {
		server.expect(requestTo(BASE + "/health"))
				.andRespond(withException(new HttpTimeoutException("read timed out")));

		mockMvc.perform(get("/api/v1/health"))
				.andExpect(status().isBadGateway())
				.andExpect(MockMvcResultMatchers.content().json(BAD_GATEWAY_JSON));
		server.verify();
		assertThat(output.getOut()).contains("failed: HttpTimeoutException");
	}

	@Test
	void baseUrlEmptyOrInvalid_returns502WithoutCallingBackend() throws Exception {
		for (String bad : new String[] { "", "   ", "api.example.invalid:3202", "/api", "http://", "http://user@h:1/api", "http://h:1/api?x",
				"ftp://h:1/api", "file:///api", "http://my_host:3202/api", "http://h:99999/api" }) {
			mockMvc = mockMvcFor(bad);
			mockMvc.perform(get("/api/v1/health"))
					.andExpect(status().isBadGateway())
					.andExpect(MockMvcResultMatchers.content().json(BAD_GATEWAY_JSON));
			server.verify();
		}
	}

	// ---------- 400：路徑跳脫與格式錯誤，後端都不能被呼叫 ----------

	@Test
	void pathTraversal_returns400WithoutCallingBackend() throws Exception {
		for (String path : new String[] {
				"/api/v1/../x", "/api/v1/%2e%2e/x", "/api/v1/a/../../x", "/api/v1/./x", "/api/v1/%2E/x",
				"/api/v1/a;x@evil.invalid/y", "/api/v1/a%2Fb", "/api/v1/a%5Cb", "/api/v1//x" }) {
			mockMvc.perform(get(URI.create(path)))
					.andExpect(status().isBadRequest())
					.andExpect(MockMvcResultMatchers.content().json(BAD_REQUEST_JSON));
		}
		// 路徑裡壞掉的百分比編碼（如 %zz）不在此測：Tomcat 與 Spring MVC 的路徑解析會先回 400，到不了轉發器；查詢字串的 %zz 見 badQueryString_returns400
		server.verify();
	}

	@Test
	void pathTraversal_withBaseWithoutPath_cannotChangeHost() throws Exception {
		mockMvc = mockMvcFor("http://api.example.invalid:3202");

		mockMvc.perform(get(URI.create("/api/v1/;x@evil.invalid/y")))
				.andExpect(status().isBadRequest());
		server.verify();
	}

	@Test
	void badQueryString_returns400() throws Exception {
		mockMvc.perform(get("/api/v1/health").with(req -> {
					req.setQueryString("a=%zz");
					return req;
				}))
				.andExpect(status().isBadRequest())
				.andExpect(MockMvcResultMatchers.content().json(BAD_REQUEST_JSON));
		server.verify();
	}

	@Test
	void badContentType_returns400() throws Exception {
		mockMvc.perform(post("/api/v1/apps").header(HttpHeaders.CONTENT_TYPE, "foo").content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(MockMvcResultMatchers.content().json(BAD_REQUEST_JSON));
		server.verify();
	}

	@Test
	void trailingSlash_isAllowed() throws Exception {
		server.expect(requestTo(BASE + "/apps/")).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

		mockMvc.perform(get("/api/v1/apps/")).andExpect(status().isOk());
		server.verify();
	}

	private static final String TOO_LARGE_JSON = "{\"message\":\"" + ApiProxyController.TOO_LARGE_MESSAGE + "\"}";

	@Test
	void jsonBody_exactlyAtLimit_isForwarded() throws Exception {
		server.expect(requestTo(BASE + "/apps"))
				.andExpect(header(HttpHeaders.CONTENT_LENGTH, String.valueOf(ApiProxyController.MAX_BODY)))
				.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		mockMvc.perform(post("/api/v1/apps").contentType(MediaType.APPLICATION_JSON)
						.content(new byte[(int) ApiProxyController.MAX_BODY]))
				.andExpect(status().isOk());
		server.verify();
	}

	@Test
	void jsonBody_overLimit_returns413WithoutCallingBackend() throws Exception {
		mockMvc.perform(post("/api/v1/apps").contentType(MediaType.APPLICATION_JSON)
						.content(new byte[(int) ApiProxyController.MAX_BODY + 1]))
				.andExpect(status().isPayloadTooLarge())
				.andExpect(MockMvcResultMatchers.content().json(TOO_LARGE_JSON));
		server.verify();
	}

	@Test
	void multipartBody_overJsonLimitButWithinMultipartLimit_isForwarded() throws Exception {
		server.expect(requestTo(BASE + "/apps/IM1/attachments"))
				.andExpect(header(HttpHeaders.CONTENT_LENGTH, String.valueOf(ApiProxyController.MAX_MULTIPART_BODY)))
				.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		mockMvc.perform(post("/api/v1/apps/IM1/attachments")
						.header(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=x")
						.content(new byte[(int) ApiProxyController.MAX_MULTIPART_BODY]))
				.andExpect(status().isOk());
		server.verify();
	}

	@Test
	void multipartBody_overMultipartLimit_returns413WithoutCallingBackend() throws Exception {
		mockMvc.perform(post("/api/v1/apps/IM1/attachments")
						.header(HttpHeaders.CONTENT_TYPE, "Multipart/Form-Data; boundary=x")
						.content(new byte[(int) ApiProxyController.MAX_MULTIPART_BODY + 1]))
				.andExpect(status().isPayloadTooLarge())
				.andExpect(MockMvcResultMatchers.content().json(TOO_LARGE_JSON));
		server.verify();
	}

	@Test
	void responseHeaderWhitelist_isPassedThrough_othersAreDropped() throws Exception {
		HttpHeaders backendHeaders = new HttpHeaders();
		backendHeaders.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''a.pdf");
		backendHeaders.set("X-Content-Type-Options", "nosniff");
		backendHeaders.set(HttpHeaders.CACHE_CONTROL, "private, no-store");
		backendHeaders.set("X-Backend-Only", "secret");
		server.expect(requestTo(BASE + "/attachments/1"))
				.andRespond(withSuccess(new byte[] { 1, 2, 3 }, MediaType.APPLICATION_PDF).headers(backendHeaders));

		mockMvc.perform(get("/api/v1/attachments/1"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
				.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''a.pdf"))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
				.andExpect(header().doesNotExist("X-Backend-Only"))
				.andExpect(MockMvcResultMatchers.content().bytes(new byte[] { 1, 2, 3 }));
		server.verify();
	}
}
