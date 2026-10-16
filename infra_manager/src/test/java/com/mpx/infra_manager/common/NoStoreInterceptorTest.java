package com.mpx.infra_manager.common;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-11
// 變更說明: 新增（BACKLOG 第 28 項）：API 回應帶 Cache-Control: no-store；
//           成功回應與 ApiException 轉成的 401 都要帶（GET /me 驗 token 失敗走後者）；
//           另驗 WebConfig 的路徑註冊（/api 底下多層路徑都中、/api 以外不中），避免日後改壞註冊而測試照過
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;
import org.springframework.web.util.ServletRequestPathUtils;

class NoStoreInterceptorTest {

	/** 取出 WebConfig 註冊的攔截器（getInterceptors 是 protected） */
	static class OpenRegistry extends InterceptorRegistry {
		List<Object> all() {
			return getInterceptors();
		}
	}

	/** 此路徑是否會套用 WebConfig 註冊的 NoStoreInterceptor */
	private static boolean registeredFor(MappedInterceptor mapped, String uri) {
		MockHttpServletRequest req = new MockHttpServletRequest("GET", uri);
		ServletRequestPathUtils.parseAndCache(req);
		return mapped.matches(req);
	}

	@Test
	void WebConfig只在api底下掛攔截器() {
		OpenRegistry registry = new OpenRegistry();
		new WebConfig(null).addInterceptors(registry);
		List<Object> all = registry.all();
		assertThat(all).hasSize(1).first().isInstanceOf(MappedInterceptor.class);
		MappedInterceptor mapped = (MappedInterceptor) all.get(0);
		assertThat(mapped.getInterceptor()).isInstanceOf(NoStoreInterceptor.class);
		assertThat(registeredFor(mapped, "/api/v1/me")).isTrue();
		assertThat(registeredFor(mapped, "/api/v1/attachments/1")).isTrue();
		assertThat(registeredFor(mapped, "/error")).isFalse();
		assertThat(registeredFor(mapped, "/index.html")).isFalse();
	}

	/** 測試用 Controller：一個正常回應、一個丟 401 */
	@RestController
	static class DummyController {
		@GetMapping("/api/v1/ok")
		Map<String, String> ok() {
			return Map.of("a", "b");
		}

		@GetMapping("/api/v1/denied")
		Map<String, String> denied() {
			throw ApiException.unauthorized("x");
		}
	}

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(new DummyController())
				.setControllerAdvice(new GlobalExceptionHandler())
				.addInterceptors(new NoStoreInterceptor())
				.build();
	}

	@Test
	void 成功回應帶_no_store() throws Exception {
		mvc.perform(get("/api/v1/ok"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
	}

	@Test
	void 例外轉成的401也帶_no_store() throws Exception {
		mvc.perform(get("/api/v1/denied"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
	}
}
