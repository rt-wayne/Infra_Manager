package com.mpx.infra_manager_web.config;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：用真的 JDK 用戶端（BackendClientConfig 產生的 RestClient）打 JDK 內建 HttpServer 假後端（BACKLOG 第 78 項第二輪複審 S4）
//           ApiProxyControllerTest 的 MockRestServiceServer 會換掉底層用戶端，所以 HTTP/1.1、不跟隨導向、本文長度這些設定
//           只有這裡能驗。不引入新套件、不連網（只綁 127.0.0.1 隨機 port）。逾時（connect 5 秒／read 120 秒）太長不在此測
//           驗證：固定 HTTP/1.1 且不送 h2c 升級 header、302 不會再打第二次、POST 本文帶 Content-Length 不用 chunked、
//                連沒人聽的 port 回 502 並記 ConnectException
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

import com.mpx.infra_manager_web.controller.ApiProxyController;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

@ExtendWith(OutputCaptureExtension.class)
class BackendClientConfigTest {

	/** 假後端收到的每一筆請求 */
	record Received(String method, String path, String protocol, Headers headers, byte[] body) {
	}

	private HttpServer backend;
	private final List<Received> received = new CopyOnWriteArrayList<>();
	private MockMvc mockMvc;

	@BeforeEach
	void startBackend() throws IOException {
		// 綁定與用戶端位址都寫 127.0.0.1：getLoopbackAddress() 在 preferIPv6Addresses 時會回 ::1，與用戶端對不上
		backend = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
		backend.createContext("/api", exchange -> {
			received.add(new Received(exchange.getRequestMethod(), exchange.getRequestURI().getRawPath(),
					exchange.getProtocol(), exchange.getRequestHeaders(), exchange.getRequestBody().readAllBytes()));
			respond(exchange);
		});
		backend.start();
		mockMvc = mockMvcFor("http://127.0.0.1:" + backend.getAddress().getPort() + "/api");
	}

	@AfterEach
	void stopBackend() {
		backend.stop(0);
	}

	/** 真的 RestClient（BackendClientConfig）＋真的轉發器，只有 Servlet 這層是 MockMvc */
	private static MockMvc mockMvcFor(String baseUrl) {
		RestClient restClient = new BackendClientConfig().backendRestClient(RestClient.builder());
		return MockMvcBuilders.standaloneSetup(new ApiProxyController(restClient, baseUrl)).build();
	}

	/** /api/redirect 回 302 到 /api/health；其他路徑回 200 JSON，並把收到的本文長度放進回應 */
	private void respond(HttpExchange exchange) throws IOException {
		if (exchange.getRequestURI().getRawPath().equals("/api/redirect")) {
			exchange.getResponseHeaders().set(HttpHeaders.LOCATION, "/api/health");
			exchange.sendResponseHeaders(302, -1);
			exchange.close();
			return;
		}
		byte[] out = ("{\"received\":" + received.get(received.size() - 1).body().length + "}").getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
		exchange.sendResponseHeaders(200, out.length);
		try (OutputStream os = exchange.getResponseBody()) {
			os.write(out);
		}
	}

	@Test
	void get_usesHttp11WithoutH2cUpgrade() throws Exception {
		mockMvc.perform(get("/api/v1/health"))
				.andExpect(status().isOk())
				.andExpect(content().json("{\"received\":0}"));

		assertThat(received).hasSize(1);
		Received r = received.get(0);
		assertThat(r.method()).isEqualTo("GET");
		assertThat(r.path()).isEqualTo("/api/health");
		assertThat(r.protocol()).isEqualTo("HTTP/1.1");
		assertThat(r.headers().containsKey("Upgrade")).isFalse();
		assertThat(r.headers().containsKey("HTTP2-Settings")).isFalse();
	}

	@Test
	void redirect_isReturnedAsIs_andNotFollowed() throws Exception {
		mockMvc.perform(get("/api/v1/redirect"))
				.andExpect(status().isFound())
				.andExpect(header().doesNotExist(HttpHeaders.LOCATION));

		assertThat(received).extracting(Received::path).containsExactly("/api/redirect");
	}

	@Test
	void post_sendsContentLength_notChunked() throws Exception {
		String json = "{\"n\":\"中文\"}";
		int length = json.getBytes(StandardCharsets.UTF_8).length;

		mockMvc.perform(post("/api/v1/apps").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isOk())
				.andExpect(content().json("{\"received\":" + length + "}"));

		Received r = received.get(0);
		assertThat(r.method()).isEqualTo("POST");
		assertThat(r.headers().getFirst("Content-Length")).isEqualTo(String.valueOf(length));
		assertThat(r.headers().containsKey("Transfer-Encoding")).isFalse();
		assertThat(r.headers().getFirst("Content-Type")).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
		assertThat(new String(r.body(), StandardCharsets.UTF_8)).isEqualTo(json);
	}

	@Test
	void closedPort_returns502AndLogsConnectException(CapturedOutput output) throws Exception {
		int freePort;
		try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
			freePort = socket.getLocalPort();
		}
		mockMvc = mockMvcFor("http://127.0.0.1:" + freePort + "/api");

		mockMvc.perform(get("/api/v1/health"))
				.andExpect(status().isBadGateway())
				.andExpect(content().json("{\"message\":\"後端服務呼叫失敗\"}"));

		assertThat(received).isEmpty();
		assertThat(output.getOut()).contains("failed: ConnectException");
	}
}
