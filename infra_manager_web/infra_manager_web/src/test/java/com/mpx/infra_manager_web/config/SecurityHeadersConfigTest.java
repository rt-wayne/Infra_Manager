package com.mpx.infra_manager_web.config;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-11
// 變更說明: 新增：安全標頭測試（BACKLOG 第 30 項）
//           驗：元件掃描找得到、過濾器掛在所有路徑（/*）；四個標頭的值與份數；不帶 X-Frame-Options（使用者裁示允許嵌入）；
//           以模擬 Controller 驗回應原有標頭保留、401 也帶、回應帶同名標頭時以回應的為準（模擬後端送 Referrer-Policy）
//           實際經 BackendForwarder 轉送、Tomcat 錯誤頁（404、後端斷線 500）由 curl 實測（2026-10-11），本測試不涵蓋
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-12
// 變更說明: BACKLOG 第 43 項：CSP 預期值加 style-src-attr 'unsafe-inline'（附件新分頁的瀏覽器內建檢視頁需要）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp_web 改名為 com.mpx.infra_manager_web（專案改名 infra_manager），只改 package／import／全名引用
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 80 項：CSP 預期值加 worker-src 'self' blob:（帳號列表匯出 xlsx 的壓縮背景執行緒需要）
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mpx.infra_manager_web.config.SecurityHeadersConfig.SecurityHeadersFilter;

class SecurityHeadersConfigTest {

	@RestController
	static class DummyController {
		@GetMapping("/api/v1/ok")
		ResponseEntity<String> ok() {
			return ResponseEntity.ok().header("X-From-Backend", "1").body("{}");
		}

		@GetMapping("/api/v1/denied")
		ResponseEntity<String> denied() {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("{}");
		}

		/** 模擬後端回應自帶 Referrer-Policy */
		@GetMapping("/api/v1/own-referrer")
		ResponseEntity<String> ownReferrer() {
			return ResponseEntity.ok().header("Referrer-Policy", "no-referrer").body("{}");
		}
	}

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(new DummyController()).addFilters(new SecurityHeadersFilter()).build();
	}

	/** 只掃 config 套件（不讀 host.properties、不啟動 Tomcat）；拿掉 @Configuration 或 @Bean 時本測試失敗 */
	@Test
	@SuppressWarnings("rawtypes")
	void 元件掃描找得到且過濾器掛在所有路徑() {
		try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
			ctx.scan("com.mpx.infra_manager_web.config");
			ctx.refresh();
			Map<String, FilterRegistrationBean> regs = ctx.getBeansOfType(FilterRegistrationBean.class);
			assertThat(regs).hasSize(1);
			FilterRegistrationBean<?> reg = regs.values().iterator().next();
			assertThat(reg.getFilter()).isInstanceOf(SecurityHeadersFilter.class);
			assertThat(reg.getUrlPatterns()).containsExactly("/*");
		}
	}

	@Test
	void 成功回應帶四個標頭且保留原本的標頭() throws Exception {
		MockHttpServletResponse res = mvc.perform(get("/api/v1/ok")).andReturn().getResponse();
		assertHeaders(res);
		assertThat(res.getHeader("X-From-Backend")).isEqualTo("1");
	}

	@Test
	void 錯誤回應也帶() throws Exception {
		assertHeaders(mvc.perform(get("/api/v1/denied")).andReturn().getResponse());
	}

	@Test
	void 回應自帶同名標頭時以回應的為準() throws Exception {
		MockHttpServletResponse res = mvc.perform(get("/api/v1/own-referrer")).andReturn().getResponse();
		assertThat(res.getHeaderValues("Referrer-Policy")).containsExactly("no-referrer");
		assertThat(res.getHeader("Content-Security-Policy")).isEqualTo(SecurityHeadersConfig.CSP);
	}

	private static void assertHeaders(MockHttpServletResponse res) {
		assertThat(res.getHeaderValues("Content-Security-Policy"))
				.containsExactly("default-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; style-src-attr 'unsafe-inline'; worker-src 'self' blob:");
		assertThat(res.getHeaderValues("X-Content-Type-Options")).containsExactly("nosniff");
		assertThat(res.getHeaderValues("Referrer-Policy")).containsExactly("same-origin");
		assertThat(res.getHeaderValues("Permissions-Policy"))
				.containsExactly("camera=(), microphone=(), geolocation=(), payment=(), usb=()");
		assertThat(res.getHeader("X-Frame-Options")).isNull();
	}
}
