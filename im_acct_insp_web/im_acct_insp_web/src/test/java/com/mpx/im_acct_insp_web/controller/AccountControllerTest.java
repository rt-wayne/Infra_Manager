package com.mpx.im_acct_insp_web.controller;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：AccountController 路徑對應測試（變更單 #1：S1 /accounts/lookup/{id}）
//           standalone MockMvc＋MockRestServiceServer 假後端，不啟動 Spring 環境、不讀 host.properties、不連外
//           驗：/lookup/{id}、/lookup、/{id}、/{id}/history 各自轉到後端同路徑，單號以單一 path segment 編碼
//           SA v0.5 改版（2026-10-07，Claude Opus 5.5，spec_im_acct_insp_v2.0.md 第 6.4 節）：X-User-Id 改為 Authorization 轉送；
//           /lookup 查詢參數改 acctName；/lookup/{id} 的 acctName 查詢字串要轉到後端
// ============================================================

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestTemplate;

class AccountControllerTest {

	private static final String BACKEND = "http://backend.test:8989/api/v1";

	private static final String TOKEN = "Bearer tok-0000001";

	private MockRestServiceServer server;
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		RestTemplate restTemplate = new RestTemplate();
		server = MockRestServiceServer.bindTo(restTemplate).build();
		mvc = MockMvcBuilders.standaloneSetup(new AccountController(new BackendForwarder(restTemplate, BACKEND))).build();
	}

	private void expectBackend(String path, String body) {
		server.expect(requestTo(URI.create(BACKEND + path)))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header(HttpHeaders.AUTHORIZATION, TOKEN))
				.andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
	}

	@Test
	void lookupDetailForwardsToBackendLookupPathWithQuery() throws Exception {
		expectBackend("/accounts/lookup/AC20261007-001?acctName=sa", "{\"acctId\":\"AC20261007-001\"}");
		mvc.perform(get("/api/v1/accounts/lookup/AC20261007-001?acctName=sa").header(HttpHeaders.AUTHORIZATION, TOKEN))
				.andExpect(status().isOk())
				.andExpect(content().string("{\"acctId\":\"AC20261007-001\"}"));
		server.verify();
	}

	@Test
	void lookupListAndDetailAndHistoryKeepTheirOwnPaths() throws Exception {
		expectBackend("/accounts/lookup?acctName=sa", "[]");
		expectBackend("/accounts/AC20261007-001", "{}");
		expectBackend("/accounts/AC20261007-001/history", "[]");
		mvc.perform(get("/api/v1/accounts/lookup?acctName=sa").header(HttpHeaders.AUTHORIZATION, TOKEN))
				.andExpect(status().isOk());
		mvc.perform(get("/api/v1/accounts/AC20261007-001").header(HttpHeaders.AUTHORIZATION, TOKEN))
				.andExpect(status().isOk());
		mvc.perform(get("/api/v1/accounts/AC20261007-001/history").header(HttpHeaders.AUTHORIZATION, TOKEN))
				.andExpect(status().isOk());
		server.verify();
	}

	@Test
	void lookupDetailEncodesIdAsSinglePathSegment() throws Exception {
		expectBackend("/accounts/lookup/A%20B", "{}");
		mvc.perform(get("/api/v1/accounts/lookup/{id}", "A B").header(HttpHeaders.AUTHORIZATION, TOKEN))
				.andExpect(status().isOk());
		server.verify();
	}
}
