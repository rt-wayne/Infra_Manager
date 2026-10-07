package com.mpx.infra_manager_web.controller;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：轉發器串流透傳的端到端測試（S6 回合一，裁示 ①A ③A）。真的 Tomcat（隨機 port）＋真的 JDK 用戶端＋
//           JDK 內建 HttpServer 假後端（只綁 127.0.0.1），不連網、不引入新套件。不讀 host.properties（自組最小 context）。
//           驗證：multipart 本文原樣到後端（Spring multipart 解析器會先讀走本文，修正前後端收到 0 byte）；
//           20 MB 下載串流回來 sha256 一致、Content-Disposition／Content-Length／Cache-Control 透傳；
//           宣告長度超過 1 MB 回 413 且不打後端；chunked 本文邊轉邊計數超過 1 MB 回 413；
//           B6 逾時涵蓋範圍（read timeout 縮成 2 秒量測）：後端回應本文整段計入 read timeout（超過即中斷）、
//           用戶端上傳本文的時間不計入（慢慢傳完後端仍完整收到）
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import com.mpx.infra_manager_web.config.BackendClientConfig;
import com.mpx.infra_manager_web.config.NoMultipartConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

@SpringBootTest(classes = ApiProxyStreamingTest.App.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiProxyStreamingTest {

	/** 量測用的 read timeout（正式為 120 秒） */
	static final Duration TEST_READ_TIMEOUT = Duration.ofSeconds(2);

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	@Import({ ApiProxyController.class, NoMultipartConfig.class })
	static class App {
		@Bean
		RestClient backendRestClient() {
			return BackendClientConfig.build(RestClient.builder(), TEST_READ_TIMEOUT);
		}
	}

	/** 假後端收到的請求：本文只記長度與 sha256，不留在記憶體 */
	record Received(String method, String path, String contentType, String contentLength, long bodyLength,
			String sha256, boolean bodyFailed) {
	}

	private static HttpServer backend;
	private static final List<Received> received = new CopyOnWriteArrayList<>();

	@DynamicPropertySource
	static void backendUrl(DynamicPropertyRegistry registry) throws IOException {
		backend = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
		backend.createContext("/api", ApiProxyStreamingTest::handle);
		// daemon 執行緒：慢速端點可能在測試結束時還在睡，非 daemon 會讓 surefire 等 30 秒後強殺 fork
		backend.setExecutor(java.util.concurrent.Executors.newCachedThreadPool(r -> {
			Thread t = new Thread(r, "fake-backend");
			t.setDaemon(true);
			return t;
		}));
		backend.start();
		registry.add("backend.api.domain.path", () -> "http://127.0.0.1:" + backend.getAddress().getPort() + "/api");
	}

	@AfterAll
	static void stopBackend() {
		backend.stop(0);
	}

	@Autowired
	private Environment env;

	private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

	@BeforeEach
	void clear() {
		received.clear();
	}

	private URI shell(String subPath) {
		return URI.create("http://127.0.0.1:" + env.getProperty("local.server.port")
				+ env.getProperty("server.servlet.context-path", "") + "/api/v1" + subPath);
	}

	// ---------- 假後端 ----------

	private static void handle(HttpExchange exchange) throws IOException {
		String path = exchange.getRequestURI().getRawPath();
		String query = exchange.getRequestURI().getRawQuery();
		MessageDigest md = sha256();
		long length = 0;
		boolean failed = false;
		try (InputStream in = exchange.getRequestBody()) {
			byte[] buf = new byte[8192];
			int n;
			while ((n = in.read(buf)) != -1) {
				md.update(buf, 0, n);
				length += n;
			}
		} catch (IOException e) {
			failed = true;
		}
		received.add(new Received(exchange.getRequestMethod(), path,
				exchange.getRequestHeaders().getFirst("Content-Type"),
				exchange.getRequestHeaders().getFirst("Content-Length"), length, HexFormat.of().formatHex(md.digest()),
				failed));
		if (failed) {
			exchange.close();
			return;
		}
		if (path.equals("/api/download")) {
			long size = Long.parseLong(query.substring("size=".length()));
			exchange.getResponseHeaders().set("Content-Type", "application/pdf");
			exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename*=UTF-8''a.pdf");
			exchange.getResponseHeaders().set("Cache-Control", "private, no-store");
			exchange.getResponseHeaders().set("X-Backend-Only", "1");
			exchange.sendResponseHeaders(200, size);
			try (OutputStream os = exchange.getResponseBody()) {
				writePattern(os, size, 0);
			}
			return;
		}
		if (path.equals("/api/slow-download")) {
			exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
			exchange.sendResponseHeaders(200, 3 * 1024);
			try (OutputStream os = exchange.getResponseBody()) {
				for (int i = 0; i < 3; i++) {
					sleep(1100);
					os.write(new byte[1024]);
					os.flush();
				}
			} catch (IOException e) {
				// 轉發器放棄時連線被關，屬預期
			}
			return;
		}
		byte[] out = ("{\"received\":" + length + "}").getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(200, out.length);
		try (OutputStream os = exchange.getResponseBody()) {
			os.write(out);
		}
	}

	// ---------- 測試 ----------

	@Test
	void multipart_bodyReachesBackendIntact() throws Exception {
		String boundary = "----imTestBoundary7MA4YWxk";
		byte[] file = pattern(300 * 1024);
		byte[] body = concat(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a.bin\"\r\n"
				+ "Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8), file,
				("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

		HttpResponse<String> resp = client.send(HttpRequest.newBuilder(shell("/apps/IM1/attachments"))
				.header("Content-Type", "multipart/form-data; boundary=" + boundary)
				.POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.ofString());

		assertThat(resp.statusCode()).isEqualTo(200);
		assertThat(received).hasSize(1);
		Received r = received.get(0);
		assertThat(r.bodyLength()).isEqualTo(body.length);
		assertThat(r.sha256()).isEqualTo(sha256Hex(body));
		assertThat(r.contentType()).isEqualTo("multipart/form-data; boundary=" + boundary);
		assertThat(r.contentLength()).isEqualTo(String.valueOf(body.length));
	}

	@Test
	void download_isStreamedWithHeadersAndSameSha256() throws Exception {
		long size = 20L * 1024 * 1024;
		HttpResponse<InputStream> resp = client.send(HttpRequest.newBuilder(shell("/download?size=" + size)).build(),
				HttpResponse.BodyHandlers.ofInputStream());

		assertThat(resp.statusCode()).isEqualTo(200);
		assertThat(resp.headers().firstValue("Content-Type")).hasValue("application/pdf");
		assertThat(resp.headers().firstValue("Content-Disposition")).hasValue("attachment; filename*=UTF-8''a.pdf");
		assertThat(resp.headers().firstValue("Content-Length")).hasValue(String.valueOf(size));
		assertThat(resp.headers().firstValue("Cache-Control")).hasValue("private, no-store");
		assertThat(resp.headers().firstValue("X-Backend-Only")).isEmpty();
		MessageDigest md = sha256();
		long got = 0;
		try (InputStream in = resp.body()) {
			byte[] buf = new byte[65536];
			int n;
			while ((n = in.read(buf)) != -1) {
				md.update(buf, 0, n);
				got += n;
			}
		}
		assertThat(got).isEqualTo(size);
		MessageDigest expected = sha256();
		writePattern(new OutputStream() {
			@Override
			public void write(int b) {
				expected.update((byte) b);
			}

			@Override
			public void write(byte[] b, int off, int len) {
				expected.update(b, off, len);
			}
		}, size, 0);
		assertThat(HexFormat.of().formatHex(md.digest())).isEqualTo(HexFormat.of().formatHex(expected.digest()));
	}

	@Test
	void declaredLengthOverLimit_returns413WithoutCallingBackend() throws Exception {
		byte[] body = new byte[(int) ApiProxyController.MAX_BODY + 1];
		HttpResponse<String> resp = client.send(HttpRequest.newBuilder(shell("/apps"))
				.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(),
				HttpResponse.BodyHandlers.ofString());

		assertThat(resp.statusCode()).isEqualTo(413);
		assertThat(resp.body()).isEqualTo("{\"message\":\"請求內容過大\"}");
		assertThat(received).isEmpty();
	}

	@Test
	void chunkedBodyOverLimit_returns413() throws Exception {
		byte[] body = new byte[(int) ApiProxyController.MAX_BODY + 4096];
		HttpResponse<String> resp = client.send(HttpRequest.newBuilder(shell("/apps"))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofInputStream(() -> new java.io.ByteArrayInputStream(body))).build(),
				HttpResponse.BodyHandlers.ofString());

		assertThat(resp.statusCode()).isEqualTo(413);
		assertThat(resp.body()).isEqualTo("{\"message\":\"請求內容過大\"}");
	}

	@Test
	void chunkedBodyWithinLimit_isForwarded() throws Exception {
		byte[] body = pattern(64 * 1024);
		HttpResponse<String> resp = client.send(HttpRequest.newBuilder(shell("/apps"))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofInputStream(() -> new java.io.ByteArrayInputStream(body))).build(),
				HttpResponse.BodyHandlers.ofString());

		assertThat(resp.statusCode()).isEqualTo(200);
		assertThat(received.get(0).sha256()).isEqualTo(sha256Hex(body));
	}

	/**
	 * B6：後端 header 立刻回、本文 3.3 秒才吐完。read timeout 涵蓋整個回應本文（不是兩包之間的間隔），
	 * 超過就中斷；瀏覽器不得拿到「200 且完整」的回應（正式 120 秒 = 下載整體上限）
	 */
	@Test
	void b6_slowDownloadBody_isCutByReadTimeout() throws Exception {
		HttpResponse<byte[]> resp;
		try {
			resp = client.send(HttpRequest.newBuilder(shell("/slow-download")).build(),
					HttpResponse.BodyHandlers.ofByteArray());
		} catch (IOException e) {
			return; // 回應已送出後中斷：連線被切，等同不完整
		}
		assertThat(resp.statusCode()).isNotEqualTo(200);
	}

	/** B6：用戶端 3.3 秒才把本文傳完（超過 2 秒 read timeout）——上傳時間不計入 read timeout，後端完整收到 */
	@Test
	void b6_slowUploadBody_isNotCutByReadTimeout() throws Exception {
		HttpResponse<String> resp = client.send(HttpRequest.newBuilder(shell("/apps"))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.fromPublisher(
						HttpRequest.BodyPublishers.ofInputStream(() -> slowStream(3, 1024, 1100)), 3 * 1024))
				.build(), HttpResponse.BodyHandlers.ofString());
		assertThat(resp.statusCode()).isEqualTo(200);
		assertThat(received).hasSize(1);
		assertThat(received.get(0).bodyLength()).isEqualTo(3 * 1024);
	}

	// ---------- 工具 ----------

	private static InputStream slowStream(int chunks, int chunkSize, long delayMs) {
		return new InputStream() {
			private int sent;
			private int posInChunk = chunkSize;

			@Override
			public int read() {
				byte[] one = new byte[1];
				return read(one, 0, 1) == -1 ? -1 : one[0] & 0xff;
			}

			@Override
			public int read(byte[] b, int off, int len) {
				if (posInChunk >= chunkSize) {
					if (sent >= chunks) {
						return -1;
					}
					sleep(delayMs);
					sent++;
					posInChunk = 0;
				}
				int n = Math.min(len, chunkSize - posInChunk);
				posInChunk += n;
				return n;
			}
		};
	}

	private static void writePattern(OutputStream os, long size, int seed) throws IOException {
		byte[] buf = new byte[65536];
		long written = 0;
		while (written < size) {
			int n = (int) Math.min(buf.length, size - written);
			for (int i = 0; i < n; i++) {
				buf[i] = (byte) ((written + i) * 31 + seed);
			}
			os.write(buf, 0, n);
			written += n;
		}
	}

	private static byte[] pattern(int size) {
		byte[] b = new byte[size];
		for (int i = 0; i < size; i++) {
			b[i] = (byte) (i * 31);
		}
		return b;
	}

	private static byte[] concat(byte[]... parts) {
		int len = 0;
		for (byte[] p : parts) {
			len += p.length;
		}
		byte[] out = new byte[len];
		int pos = 0;
		for (byte[] p : parts) {
			System.arraycopy(p, 0, out, pos, p.length);
			pos += p.length;
		}
		return out;
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String sha256Hex(byte[] b) {
		return HexFormat.of().formatHex(sha256().digest(b));
	}

	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
