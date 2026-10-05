package com.mpx.common.web;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-04
// 變更說明: 新增：轉發失敗處理的 MockMvc 測試（規格 v4 審查修正）；不連真後端
//           驗：後端 5xx／連線失敗時回 500 與固定訊息、回應本文不含後端內容、log 不含後端 URL
//           以測試內的小 Controller 呼叫 ApiForwarder，不依賴業務套件
//           複審修正：補「host 未設定（空字串）時回 500 固定訊息」一案；處理器改只接 ForwardException，補「業務 IllegalArgumentException 不被攔截」一案
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class ForwardExceptionHandlerTest {

	static final String HOST = "api.example.invalid";
	static final String BASE = "http://" + HOST + "/backend/api/v1";
	static final String BACKEND_BODY = "BACKEND_SECRET_BODY_TRACE";

	@RestController
	static class TestController {
		private final ApiForwarder forwarder;

		TestController(ApiForwarder forwarder) {
			this.forwarder = forwarder;
		}

		@PostMapping("/t/search")
		public ResponseEntity<Object> search(@RequestBody Object body) {
			return forwarder.post("/example/search", body);
		}

		@GetMapping("/t/list")
		public ResponseEntity<Object> list() {
			return forwarder.get("/example/list");
		}

		/** 模擬業務程式自己丟的 IllegalArgumentException（不經 ApiForwarder） */
		@GetMapping("/t/bad")
		public ResponseEntity<Object> bad() {
			throw new IllegalArgumentException("business validation failed");
		}
	}

	private MockRestServiceServer server;
	private MockMvc mvc;
	private ListAppender<ILoggingEvent> appender;
	private Logger root;

	@BeforeEach
	void setUp() {
		RestTemplate restTemplate = new RestTemplate();
		server = MockRestServiceServer.bindTo(restTemplate).build();
		ApiForwarder forwarder = new ApiForwarder(restTemplate, BASE);
		mvc = MockMvcBuilders.standaloneSetup(new TestController(forwarder))
				.setControllerAdvice(new ForwardExceptionHandler()).build();
		root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
		appender = new ListAppender<>();
		appender.start();
		root.addAppender(appender);
	}

	@AfterEach
	void tearDown() {
		root.detachAppender(appender);
	}

	private void assertLogClean() {
		// 只看 INFO 以上（正式環境 logback root 為 info；RestTemplate 自己的 DEBUG 不會輸出）
		for (ILoggingEvent ev : appender.list) {
			if (!ev.getLevel().isGreaterOrEqual(Level.INFO)) {
				continue;
			}
			String text = ev.getFormattedMessage() + (ev.getThrowableProxy() == null ? "" : ev.getThrowableProxy().getMessage());
			assertThat(text).doesNotContain(HOST, BACKEND_BODY);
		}
		assertThat(appender.list).anyMatch(ev -> ev.getFormattedMessage().startsWith("forward failed"));
	}

	@Test
	void backend5xx_returns500WithFixedMessage() throws Exception {
		server.expect(requestTo(BASE + "/example/search"))
				.andRespond(withServerError().body(BACKEND_BODY).contentType(MediaType.TEXT_PLAIN));

		MvcResult r = mvc.perform(post("/t/search").contentType(MediaType.APPLICATION_JSON).content("{\"k\":\"v\"}"))
				.andReturn();

		assertThat(r.getResponse().getStatus()).isEqualTo(500);
		String body = r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
		assertThat(body).contains(ForwardExceptionHandler.MESSAGE).doesNotContain(BACKEND_BODY, HOST);
		assertLogClean();
	}

	@Test
	void hostNotConfigured_returns500WithFixedMessage() throws Exception {
		ApiForwarder emptyHost = new ApiForwarder(new RestTemplate(), "");
		MockMvc emptyMvc = MockMvcBuilders.standaloneSetup(new TestController(emptyHost))
				.setControllerAdvice(new ForwardExceptionHandler()).build();

		MvcResult r = emptyMvc.perform(post("/t/search").contentType(MediaType.APPLICATION_JSON).content("{\"k\":\"v\"}"))
				.andReturn();

		assertThat(r.getResponse().getStatus()).isEqualTo(500);
		assertThat(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
				.contains(ForwardExceptionHandler.MESSAGE);
	}

	@Test
	void businessIllegalArgument_isNotHandledHere() {
		// 處理器只接 ForwardException：業務程式的例外不會被轉成固定訊息，而是交回 Spring 預設處理
		// （MockMvc standalone 沒有 Servlet 容器的錯誤頁，未處理的例外會直接從 perform 拋出）
		assertThatThrownBy(() -> mvc.perform(get("/t/bad")).andReturn())
				.hasRootCauseInstanceOf(IllegalArgumentException.class)
				.hasRootCauseMessage("business validation failed");
	}

	@Test
	void connectionFailure_returns500WithFixedMessage() throws Exception {
		server.expect(requestTo(BASE + "/example/list")).andRespond(request -> {
			throw new IOException("connect to " + HOST + " refused");
		});

		MvcResult r = mvc.perform(get("/t/list")).andReturn();

		assertThat(r.getResponse().getStatus()).isEqualTo(500);
		String body = r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
		assertThat(body).contains(ForwardExceptionHandler.MESSAGE).doesNotContain(HOST);
		assertLogClean();
	}
}
