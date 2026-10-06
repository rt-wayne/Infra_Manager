package com.mpx.infra_manager_java.web;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-05
// 變更說明: 新增：HealthController 與 ApiExceptionHandler 的 MockMvc 測試（S1）；standalone 不啟 Spring context、不連 DB
//           驗：/api/health 回 JSON；字數超過 400；本文過大 413；JSON 壞掉 400；DB 例外 500 固定訊息
//           2026-10-06 code review：加 chunked（Content-Length 未知）→ Jackson 讀到一半超過 → 413；
//           交易例外 500 固定訊息；未預期例外 500 固定訊息且不帶細節；400 不帶 DB 欄名
//           2026-10-06 複審（③A）：加 Spring MVC 自己的例外不被吞成 500：404、405、415、缺參數 400、參數型別 400
//           2026-10-06 S2 code review：加方法層 AccessDeniedException → 403、AuthenticationException → 401 固定訊息
// ============================================================

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mpx.infra_manager_java.config.SecurityConfig;
import com.mpx.infra_manager_java.controller.HealthController;
import com.mpx.infra_manager_java.model.HealthStatus;
import com.mpx.infra_manager_java.service.HealthService;
import com.mpx.infra_manager_java.util.TextLength;

class ApiExceptionHandlerTest {

	/** 只給測試用的 controller：把各種例外丟出來給 handler 接 */
	@RestController
	static class ThrowingController {

		static class Body {
			public String text;
		}

		@PostMapping("/api/t/text")
		public String text(@RequestBody Body body) {
			return TextLength.check("impactDesc", "影響說明", body.text, 5);
		}

		@GetMapping("/api/t/db")
		public String db() {
			throw new DataAccessResourceFailureException("ORA-12541 TNS 無監聽程式 host=secret");
		}

		@GetMapping("/api/t/tx")
		public String tx() {
			throw new CannotCreateTransactionException("Could not open JDBC Connection host=secret port=1521");
		}

		@GetMapping("/api/t/boom")
		public String boom() {
			throw new IllegalStateException("內部細節 secret");
		}

		@GetMapping("/api/t/num")
		public String num(@RequestParam int n) {
			return String.valueOf(n);
		}

		@GetMapping("/api/t/denied")
		public String denied() {
			throw new AccessDeniedException("方法層權限不足 secret");
		}

		@GetMapping("/api/t/unauth")
		public String unauth() {
			throw new InsufficientAuthenticationException("方法層未認證 secret");
		}
	}

	/** 模擬 chunked：MockMvc 會依 content 自動帶 Content-Length，這裡把它改成未知（-1），逼過濾器走計數串流那條路 */
	private static final RequestPostProcessor CHUNKED = request -> {
		MockHttpServletRequest chunked = new MockHttpServletRequest(request.getServletContext(), request.getMethod(),
				request.getRequestURI()) {
			@Override
			public long getContentLengthLong() {
				return -1L;
			}

			@Override
			public int getContentLength() {
				return -1;
			}
		};
		chunked.setContentType(request.getContentType());
		chunked.setContent(request.getContentAsByteArray());
		chunked.setCharacterEncoding(request.getCharacterEncoding());
		return chunked;
	};

	private MockMvc mvc;
	private HealthService healthService;

	@BeforeEach
	void setUp() {
		healthService = mock(HealthService.class);
		mvc = MockMvcBuilders.standaloneSetup(new HealthController(healthService), new ThrowingController())
				.setControllerAdvice(new ApiExceptionHandler())
				.addFilters(new BodyLimitFilter(64))
				.build();
	}

	@Test
	void health回傳狀態JSON() throws Exception {
		when(healthService.check()).thenReturn(new HealthStatus("UP", "UP", "2026-10-05 10:00:00"));

		mvc.perform(get("/api/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.db").value("UP"))
				.andExpect(jsonPath("$.time").value("2026-10-05 10:00:00"));
	}

	@Test
	void 字數超過回400帶JSON欄位名與中文訊息() throws Exception {
		mvc.perform(post("/api/t/text").contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"123456\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.field").value("impactDesc"))
				.andExpect(jsonPath("$.message").value("「影響說明」超過 5 字（目前 6 字）"))
				.andExpect(jsonPath("$.max").value(5))
				.andExpect(jsonPath("$.actual").value(6))
				.andExpect(content().string(Matchers.not(Matchers.containsString("IMPACT_DESC"))));
	}

	@Test
	void 本文超過上限ContentLength已知回413() throws Exception {
		String big = "{\"text\":\"" + "x".repeat(100) + "\"}";
		mvc.perform(post("/api/t/text").contentType(MediaType.APPLICATION_JSON).content(big))
				.andExpect(status().isPayloadTooLarge())
				.andExpect(jsonPath("$.message").value("請求內容過大"));
	}

	@Test
	void 本文超過上限chunked由Jackson讀到一半也回413() throws Exception {
		String big = "{\"text\":\"" + "x".repeat(100) + "\"}";
		mvc.perform(post("/api/t/text").contentType(MediaType.APPLICATION_JSON).content(big).with(CHUNKED))
				.andExpect(status().isPayloadTooLarge())
				.andExpect(jsonPath("$.message").value("請求內容過大"));
	}

	@Test
	void chunked未超過上限正常處理() throws Exception {
		mvc.perform(post("/api/t/text").contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"123\"}").with(CHUNKED))
				.andExpect(status().isOk())
				.andExpect(content().string("123"));
	}

	@Test
	void 交易例外回500固定訊息不帶細節() throws Exception {
		mvc.perform(get("/api/t/tx"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.message").value("資料庫存取失敗"))
				.andExpect(content().string(Matchers.not(Matchers.containsString("secret"))));
	}

	@Test
	void 未預期例外回500固定訊息不帶細節() throws Exception {
		mvc.perform(get("/api/t/boom"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.message").value("系統發生錯誤"))
				.andExpect(content().string(Matchers.not(Matchers.containsString("secret"))));
	}

	@Test
	void 不存在的路徑回404不吞成500() throws Exception {
		mvc.perform(get("/api/nothing-here"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("請求無法處理"));
	}

	@Test
	void method不支援回405() throws Exception {
		mvc.perform(get("/api/t/text"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.message").value("請求無法處理"));
	}

	@Test
	void ContentType不支援回415() throws Exception {
		mvc.perform(post("/api/t/text").contentType(MediaType.TEXT_PLAIN).content("hello"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.message").value("請求無法處理"));
	}

	@Test
	void 缺少必要參數回400() throws Exception {
		mvc.perform(get("/api/t/num"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("請求無法處理"));
	}

	@Test
	void 參數型別錯誤回400() throws Exception {
		mvc.perform(get("/api/t/num").param("n", "abc"))
				.andExpect(status().isBadRequest())
				.andExpect(content().string(Matchers.not(Matchers.containsString("abc"))));
	}

	@Test
	void JSON格式錯誤回400() throws Exception {
		mvc.perform(post("/api/t/text").contentType(MediaType.APPLICATION_JSON).content("{not json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("請求格式錯誤"));
	}

	@Test
	void 方法層AccessDenied回403固定訊息() throws Exception {
		mvc.perform(get("/api/t/denied"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value(SecurityConfig.MSG_FORBIDDEN))
				.andExpect(content().string(Matchers.not(Matchers.containsString("secret"))));
	}

	@Test
	void 方法層AuthenticationException回401固定訊息() throws Exception {
		mvc.perform(get("/api/t/unauth"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value(SecurityConfig.MSG_NOT_LOGGED_IN))
				.andExpect(content().string(Matchers.not(Matchers.containsString("secret"))));
	}

	@Test
	void DB例外回500固定訊息不帶細節() throws Exception {
		mvc.perform(get("/api/t/db"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.message").value("資料庫存取失敗"))
				.andExpect(jsonPath("$.length()").value(1));
	}
}
