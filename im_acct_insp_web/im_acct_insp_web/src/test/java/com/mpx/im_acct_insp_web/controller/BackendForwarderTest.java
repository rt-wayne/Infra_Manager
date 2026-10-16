package com.mpx.im_acct_insp_web.controller;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：BackendForwarder 單元測試（開發文件 spec_im_acct_insp.md 第 5 節）
//           以 MockRestServiceServer 假後端，不啟動 web 環境、不讀 host.properties、不連外
//           驗：X-User-Id 轉送、查詢字串不重複編碼、本文 byte 原樣、長度類 header 去掉、
//           後端 4xx 原樣回狀態碼與本文、連不上時 rethrow
//           SA v0.5 改版（2026-10-07，Claude Opus 5.5，spec_im_acct_insp_v2.0.md 第 7.1 節）：改以 MockHttpServletRequest 當瀏覽器請求；
//           X-User-Id 案改為 Authorization 原樣轉送／沒有就不帶（X-User-Id 不再轉）；JSON 案改驗 Content-Type 照原值；
//           新增：multipart Content-Type（含 boundary）與二進位本文原樣轉、POST 查詢字串轉送、無本文不帶 Content-Type、
//           二進位下載 Content-Type／Content-Disposition 照抄且去掉 Content-Length／Transfer-Encoding／Connection、
//           本文超過 11 MB 回 413 E15 且不呼叫後端（宣告長度超過、chunked 實際超過兩種）、剛好 11 MB 照轉
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 1：模擬瀏覽器請求的網址改 /infra_manager_web（專案改名 infra_manager）
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

class BackendForwarderTest {

	private static final String BACKEND = "http://backend.test:8989/api/v1";

	private static final String TOKEN = "Bearer abcDEF123-_xyz";

	private static final String MULTIPART_TYPE = "multipart/form-data; boundary=----WebKitFormBoundaryAb12Cd34";

	private MockRestServiceServer server;
	private BackendForwarder forwarder;

	@BeforeEach
	void setUp() {
		RestTemplate restTemplate = new RestTemplate();
		server = MockRestServiceServer.bindTo(restTemplate).build();
		forwarder = new BackendForwarder(restTemplate, BACKEND);
	}

	private static MockHttpServletRequest browser(String method, String query) {
		MockHttpServletRequest req = new MockHttpServletRequest(method, "/infra_manager_web/api/v1/x");
		req.setQueryString(query);
		return req;
	}

	/** 模擬 chunked 上傳：不宣告長度 */
	private static MockHttpServletRequest chunked(byte[] body) {
		MockHttpServletRequest req = new MockHttpServletRequest("POST", "/infra_manager_web/api/v1/x") {
			@Override
			public long getContentLengthLong() {
				return -1;
			}

			@Override
			public int getContentLength() {
				return -1;
			}
		};
		req.setContent(body);
		req.setContentType(MULTIPART_TYPE);
		return req;
	}

	@Test
	void getForwardsAuthorizationAndRawQueryAndStripsLengthHeaders() {
		byte[] body = "[{\"amt\":10.50}]".getBytes(StandardCharsets.UTF_8);
		HttpHeaders respHeaders = new HttpHeaders();
		respHeaders.setContentLength(999);
		respHeaders.set("X-Trace", "abc");
		server.expect(requestTo(URI.create(BACKEND + "/accounts?keyword=%E5%B8%B3&catgId=DB")))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header(HttpHeaders.AUTHORIZATION, TOKEN))
				.andRespond(withSuccess(body, MediaType.APPLICATION_JSON).headers(respHeaders));

		MockHttpServletRequest req = browser("GET", "keyword=%E5%B8%B3&catgId=DB");
		req.addHeader(HttpHeaders.AUTHORIZATION, TOKEN);
		ResponseEntity<byte[]> resp = forwarder.get("/accounts", req);

		server.verify();
		assertThat(resp.getStatusCode().value()).isEqualTo(200);
		assertThat(resp.getBody()).isEqualTo(body);
		assertThat(resp.getHeaders().getFirst(HttpHeaders.CONTENT_LENGTH)).isNull();
		assertThat(resp.getHeaders().getFirst("X-Trace")).isEqualTo("abc");
	}

	@Test
	void noAuthorizationHeaderIsNotForwardedAndUserIdIsNoLongerForwarded() {
		server.expect(requestTo(BACKEND + "/me"))
				.andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
				.andExpect(headerDoesNotExist("X-User-Id"))
				.andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
						.body("{\"message\":\"登入已逾時，請重新登入\"}"));

		MockHttpServletRequest req = browser("GET", null);
		req.addHeader("X-User-Id", "0000001");
		ResponseEntity<byte[]> resp = forwarder.get("/me", req);

		server.verify();
		assertThat(resp.getStatusCode().value()).isEqualTo(401);
	}

	@Test
	void postJsonKeepsBrowserContentTypeAndBodyUnchanged() {
		byte[] reqBody = "{ \"versionNo\" : 3, \"reason\" : \"不符\" }".getBytes(StandardCharsets.UTF_8);
		server.expect(requestTo(BACKEND + "/requests/AR20261007001/reject"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header(HttpHeaders.CONTENT_TYPE, "application/json;charset=UTF-8"))
				.andExpect(header(HttpHeaders.AUTHORIZATION, TOKEN))
				.andExpect(content().bytes(reqBody))
				.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		MockHttpServletRequest req = browser("POST", null);
		req.addHeader(HttpHeaders.AUTHORIZATION, TOKEN);
		req.setContentType("application/json;charset=UTF-8");
		req.setContent(reqBody);
		ResponseEntity<byte[]> resp = forwarder.post(
				"/requests/" + BackendForwarder.seg("AR20261007001") + "/reject", req);

		server.verify();
		assertThat(resp.getStatusCode().value()).isEqualTo(200);
	}

	@Test
	void postMultipartKeepsContentTypeWithBoundaryAndRawBytes() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(("------WebKitFormBoundaryAb12Cd34\r\n"
				+ "Content-Disposition: form-data; name=\"form\"; filename=\"blob\"\r\n"
				+ "Content-Type: application/json\r\n\r\n"
				+ "{\"reqTypeCode\":\"NEW\",\"acctName\":\"帳號\"}\r\n"
				+ "------WebKitFormBoundaryAb12Cd34\r\n"
				+ "Content-Disposition: form-data; name=\"docFile\"; filename=\"公文.pdf\"\r\n"
				+ "Content-Type: application/pdf\r\n\r\n").getBytes(StandardCharsets.UTF_8));
		out.write(new byte[] { '%', 'P', 'D', 'F', 0x00, (byte) 0xFF, (byte) 0x80, 0x0D, 0x0A });
		out.write("\r\n------WebKitFormBoundaryAb12Cd34--\r\n".getBytes(StandardCharsets.UTF_8));
		byte[] reqBody = out.toByteArray();

		server.expect(requestTo(BACKEND + "/requests"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header(HttpHeaders.CONTENT_TYPE, MULTIPART_TYPE))
				.andExpect(header(HttpHeaders.AUTHORIZATION, TOKEN))
				.andExpect(content().bytes(reqBody))
				.andRespond(withSuccess("{\"reqId\":\"AR20261007001\"}", MediaType.APPLICATION_JSON));

		MockHttpServletRequest req = browser("POST", null);
		req.addHeader(HttpHeaders.AUTHORIZATION, TOKEN);
		req.setContentType(MULTIPART_TYPE);
		req.setContent(reqBody);
		ResponseEntity<byte[]> resp = forwarder.post("/requests", req);

		server.verify();
		assertThat(resp.getStatusCode().value()).isEqualTo(200);
	}

	@Test
	void postWithoutBodySendsNoContentTypeAndKeepsQuery() {
		server.expect(requestTo(URI.create(BACKEND + "/jobs/J01/run?baseDate=2026-10-09")))
				.andExpect(method(HttpMethod.POST))
				.andExpect(headerDoesNotExist(HttpHeaders.CONTENT_TYPE))
				.andRespond(withSuccess("{\"runId\":1}", MediaType.APPLICATION_JSON));

		ResponseEntity<byte[]> resp = forwarder.post("/jobs/J01/run", browser("POST", "baseDate=2026-10-09"));

		server.verify();
		assertThat(resp.getStatusCode().value()).isEqualTo(200);
	}

	@Test
	void binaryDownloadKeepsTypeAndDispositionAndStripsLengthHeaders() {
		byte[] file = new byte[] { '%', 'P', 'D', 'F', 0x00, (byte) 0xFF, (byte) 0xFE, 0x01 };
		String disposition = "attachment; filename*=UTF-8''%E5%85%AC%E6%96%87.pdf";
		HttpHeaders respHeaders = new HttpHeaders();
		respHeaders.set(HttpHeaders.CONTENT_DISPOSITION, disposition);
		respHeaders.setContentLength(12345);
		respHeaders.set(HttpHeaders.TRANSFER_ENCODING, "chunked");
		respHeaders.set(HttpHeaders.CONNECTION, "keep-alive");
		server.expect(requestTo(BACKEND + "/attachments/15"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header(HttpHeaders.AUTHORIZATION, TOKEN))
				.andRespond(withSuccess(file, MediaType.APPLICATION_PDF).headers(respHeaders));

		MockHttpServletRequest req = browser("GET", null);
		req.addHeader(HttpHeaders.AUTHORIZATION, TOKEN);
		ResponseEntity<byte[]> resp = forwarder.get("/attachments/15", req);

		server.verify();
		assertThat(resp.getStatusCode().value()).isEqualTo(200);
		assertThat(resp.getBody()).isEqualTo(file);
		assertThat(resp.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
		assertThat(resp.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).isEqualTo(disposition);
		assertThat(resp.getHeaders().getFirst(HttpHeaders.CONTENT_LENGTH)).isNull();
		assertThat(resp.getHeaders().getFirst(HttpHeaders.TRANSFER_ENCODING)).isNull();
		assertThat(resp.getHeaders().getFirst(HttpHeaders.CONNECTION)).isNull();
	}

	@Test
	void bodyOverLimitByContentLengthReturns413WithoutCallingBackend() {
		MockHttpServletRequest req = browser("POST", null);
		req.setContentType(MULTIPART_TYPE);
		req.setContent(new byte[BackendForwarder.MAX_BODY_BYTES + 1]);

		ResponseEntity<byte[]> resp = forwarder.post("/requests", req);

		server.verify(); // 沒有設定任何後端預期：有呼叫後端就會失敗
		assertThat(resp.getStatusCode().value()).isEqualTo(413);
		assertThat(resp.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
		assertThat(new String(resp.getBody(), StandardCharsets.UTF_8))
				.isEqualTo("{\"message\":\"附件大小不可超過 10 MB\"}");
	}

	@Test
	void chunkedBodyOverLimitReturns413WithoutCallingBackend() {
		ResponseEntity<byte[]> resp = forwarder.post("/requests/AR1/exec-done",
				chunked(new byte[BackendForwarder.MAX_BODY_BYTES + 1]));

		server.verify();
		assertThat(resp.getStatusCode().value()).isEqualTo(413);
	}

	@Test
	void bodyExactlyAtLimitIsForwarded() {
		byte[] reqBody = new byte[BackendForwarder.MAX_BODY_BYTES];
		reqBody[reqBody.length - 1] = 7;
		server.expect(requestTo(BACKEND + "/requests"))
				.andExpect(content().bytes(reqBody))
				.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		ResponseEntity<byte[]> resp = forwarder.post("/requests", chunked(reqBody));

		server.verify();
		assertThat(resp.getStatusCode().value()).isEqualTo(200);
	}

	@Test
	void backend4xxIsPassedThroughWithBody() {
		byte[] err = "{\"message\":\"此申請已由他人處理，請重新整理\"}".getBytes(StandardCharsets.UTF_8);
		server.expect(requestTo(BACKEND + "/requests/AR1/approve"))
				.andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON).body(err));

		ResponseEntity<byte[]> resp = forwarder.post("/requests/AR1/approve", browser("POST", null));

		assertThat(resp.getStatusCode().value()).isEqualTo(409);
		assertThat(resp.getBody()).isEqualTo(err);
		assertThat(resp.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
	}

	@Test
	void backend413IsPassedThroughWithBody() {
		byte[] err = "{\"message\":\"附件大小不可超過 10 MB\"}".getBytes(StandardCharsets.UTF_8);
		server.expect(requestTo(BACKEND + "/requests"))
				.andRespond(withStatus(HttpStatus.CONTENT_TOO_LARGE).contentType(MediaType.APPLICATION_JSON).body(err));

		MockHttpServletRequest req = browser("POST", null);
		req.setContentType(MULTIPART_TYPE);
		req.setContent(new byte[] { 1, 2, 3 });
		ResponseEntity<byte[]> resp = forwarder.post("/requests", req);

		assertThat(resp.getStatusCode().value()).isEqualTo(413);
		assertThat(resp.getBody()).isEqualTo(err);
	}

	@Test
	void backend5xxIsPassedThroughWithBody() {
		byte[] err = "{\"message\":\"系統錯誤\"}".getBytes(StandardCharsets.UTF_8);
		server.expect(requestTo(BACKEND + "/todos"))
				.andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_JSON).body(err));

		ResponseEntity<byte[]> resp = forwarder.get("/todos", browser("GET", null));

		assertThat(resp.getStatusCode().value()).isEqualTo(500);
		assertThat(resp.getBody()).isEqualTo(err);
	}

	@Test
	void connectionFailureIsRethrown() {
		server.expect(requestTo(BACKEND + "/users")).andRespond(withException(new IOException("refused")));

		assertThatThrownBy(() -> forwarder.get("/users", browser("GET", null)))
				.isInstanceOf(ResourceAccessException.class);
	}

	@Test
	void pathSegmentIsEncoded() {
		assertThat(BackendForwarder.seg("AC20261007-001")).isEqualTo("AC20261007-001");
		assertThat(BackendForwarder.seg("a/b")).isEqualTo("a%2Fb");
	}
}
