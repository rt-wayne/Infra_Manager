package com.mpx.infra_manager_java.config;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：啟真 Tomcat（隨機 port）驗證瀏覽器實際收到的 Set-Cookie（S2 回合一 code review 第 9(a) 項，裁示 ⑤A）。
//           MockMvc 不經 Tomcat，IM_SESSION 的 SameSite（由 CookieSameSiteSupplier 在 Tomcat 層加）與 Secure／HttpOnly／Path
//           只有這裡驗得到。刻意不用 com.mpx.Application（會載 host.properties、database.properties 與 common.db），
//           改以巢狀設定只匯入 Tomcat／MVC／Security 的自動設定與本專案認證相關 bean；UserDao 以 mock 取代，不連 DB。
//           同時驗證 UserDetailsServiceAutoConfiguration 在有 AuthenticationManager bean 時不建預設帳號（code review 第 1 項，裁示 ①A）。
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import com.mpx.infra_manager_java.controller.auth.AuthController;
import com.mpx.infra_manager_java.dao.auth.UserDao;
import com.mpx.infra_manager_java.model.auth.UserRow;
import com.mpx.infra_manager_java.service.auth.AuthService;
import com.mpx.infra_manager_java.web.ApiExceptionHandler;

import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SessionCookieTomcatTest {

	@SpringBootConfiguration
	@ImportAutoConfiguration({ TomcatServletWebServerAutoConfiguration.class, DispatcherServletAutoConfiguration.class,
			WebMvcAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class, JacksonAutoConfiguration.class,
			SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class,
			SecurityFilterAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class })
	@Import({ SecurityConfig.class, AuthController.class, AuthService.class, ApiExceptionHandler.class })
	static class Boot {
	}

	@MockitoBean
	private UserDao userDao;

	@Autowired
	private PasswordEncoder encoder;

	@Autowired
	private ApplicationContext context;

	@Value("${local.server.port}")
	private int port;

	private RestClient client;

	@BeforeEach
	void setUp() {
		// 4xx 不丟例外，直接看回應
		client = RestClient.builder().baseUrl("http://localhost:" + port)
				.defaultStatusHandler(status -> true, (request, response) -> {
				}).build();
		UserRow row = new UserRow();
		row.setUserId("E0001");
		row.setLoginId("wayne");
		row.setUserName("測試人員");
		row.setPwdHash(encoder.encode("secret"));
		row.setStatus(1);
		row.setIsDfltPwd(0);
		when(userDao.findByLoginId("wayne")).thenReturn(Optional.of(row));
		when(userDao.findActiveRoleIds("E0001")).thenReturn(List.of("infra"));
	}

	private static String setCookie(ResponseEntity<?> response, String name) {
		List<String> headers = response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE);
		return headers.stream().filter(h -> h.startsWith(name + "=")).findFirst().orElse(null);
	}

	private static String value(String setCookie) {
		int eq = setCookie.indexOf('=');
		int semi = setCookie.indexOf(';');
		return setCookie.substring(eq + 1, semi < 0 ? setCookie.length() : semi);
	}

	@Test
	void 真Tomcat回應的IM_XSRF與IM_SESSION帶正確屬性() {
		ResponseEntity<String> me = client.get().uri("/api/auth/me").retrieve().toEntity(String.class);
		assertThat(me.getStatusCode().value()).isEqualTo(200);
		String xsrf = setCookie(me, SecurityConfig.XSRF_COOKIE);
		assertThat(xsrf).isNotNull()
				.contains("; Path=/").contains("; Secure").contains("; SameSite=Lax")
				.doesNotContain("HttpOnly").doesNotContain("Domain=");
		assertThat(setCookie(me, SecurityConfig.SESSION_COOKIE)).as("未登入不建 session").isNull();

		String token = value(xsrf);
		ResponseEntity<String> login = client.post().uri("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.COOKIE, SecurityConfig.XSRF_COOKIE + "=" + token)
				.header(SecurityConfig.XSRF_HEADER, token)
				.body("{\"loginId\":\"wayne\",\"password\":\"secret\"}")
				.retrieve().toEntity(String.class);
		assertThat(login.getStatusCode().value()).isEqualTo(200);

		String session = setCookie(login, SecurityConfig.SESSION_COOKIE);
		assertThat(session).isNotNull()
				.contains("; Path=/").contains("; Secure").contains("; HttpOnly").contains("; SameSite=Lax")
				.doesNotContain("Domain=").doesNotContain("Max-Age=").doesNotContain("Expires=");
		assertThat(value(session)).isNotBlank();

		String rotated = setCookie(login, SecurityConfig.XSRF_COOKIE);
		assertThat(rotated).as("登入後 IM_XSRF 換新值").isNotNull();
		assertThat(value(rotated)).isNotBlank().isNotEqualTo(token);
	}

	@Test
	void 未登入打api回401不導頁() {
		ResponseEntity<String> apps = client.get().uri("/api/apps").retrieve().toEntity(String.class);
		assertThat(apps.getStatusCode().value()).isEqualTo(401);
		assertThat(apps.getHeaders().getLocation()).isNull();
		assertThat(apps.getBody()).isEqualTo("{\"message\":\"" + SecurityConfig.MSG_NOT_LOGGED_IN + "\"}");
	}

	@Test
	void Boot不會建預設帳號user() {
		assertThat(context.getBeanProvider(UserDetailsService.class).getIfAvailable()).isNull();
	}
}
