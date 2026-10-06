package com.mpx.infra_manager_java.config;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：Spring Security 設定的 MockMvc 測試（S2 回合一）。@WebMvcTest 只載入 web 切片，
//           匯入 SecurityConfig 與真的 AuthService，UserDao 以 @MockitoBean 取代（不連 DB）。
//           CSRF 刻意不用 spring-security-test 的 csrf()：它會把整條 chain 的 CsrfFilter 儲存庫換成
//           TestCsrfTokenRepository(HttpSessionCsrfTokenRepository)，之後所有測試都拿不到 IM_XSRF cookie。
//           改成跟前端一樣：先 GET /api/auth/me 拿 IM_XSRF cookie，再以 cookie + X-IM-XSRF header 送 POST。
//           驗證：/me 未登入 200 且寫 IM_XSRF cookie（Secure、非 HttpOnly、Path=/、SameSite=Lax）、
//                login 沒帶或帶錯 X-IM-XSRF 403、帶對且帳密對 200 並建 session、帳密錯 401、本文缺欄位 400、
//                /api/** 未登入 401 不導頁、預設密碼登入後打 /api/** 403「請先修改預設密碼」、logout 204 且 session 失效、
//                session cookie 初始化器設的名稱／HttpOnly／Secure／Path／逾時
//           2026-10-06 code review：加 logout 不帶 CSRF header 403 且 session 仍在、/api/auth/** 非公開路徑未登入 401 而
//                預設密碼者可通過、已登入者打非 /api 路徑 403、超過 72 bytes 的密碼登入是 401 不是 500、
//                login 成功後 IM_XSRF 換新值且 logout 後被清掉、沒有 Boot 預設帳號且 AuthenticationManager 一律拒絕。
//                真 Tomcat 的 Set-Cookie 屬性另見 SessionCookieTomcatTest。
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.mpx.infra_manager_java.controller.auth.AuthController;
import com.mpx.infra_manager_java.dao.auth.UserDao;
import com.mpx.infra_manager_java.model.auth.UserRow;
import com.mpx.infra_manager_java.service.auth.AuthService;

import jakarta.servlet.http.Cookie;

@WebMvcTest(controllers = AuthController.class)
@Import({ SecurityConfig.class, AuthService.class })
class SecurityConfigTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PasswordEncoder encoder;

	@Autowired
	private AuthenticationManager authenticationManager;

	@Autowired
	private ApplicationContext context;

	@MockitoBean
	private UserDao userDao;

	private void stubUser(String password, int isDefault) {
		UserRow row = new UserRow();
		row.setUserId("E0001");
		row.setLoginId("wayne");
		row.setUserName("測試人員");
		row.setPwdHash(encoder.encode(password));
		row.setStatus(1);
		row.setIsDfltPwd(isDefault);
		when(userDao.findByLoginId("wayne")).thenReturn(Optional.of(row));
		when(userDao.findActiveRoleIds("E0001")).thenReturn(List.of("infra"));
	}

	/** 跟前端一樣：先 GET /me 拿 IM_XSRF cookie */
	private Cookie xsrfCookie() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/auth/me")).andExpect(status().isOk()).andReturn();
		Cookie cookie = result.getResponse().getCookie(SecurityConfig.XSRF_COOKIE);
		assertThat(cookie).isNotNull();
		return cookie;
	}

	private MockHttpServletRequestBuilder withXsrf(MockHttpServletRequestBuilder builder) throws Exception {
		Cookie cookie = xsrfCookie();
		return builder.cookie(cookie).header(SecurityConfig.XSRF_HEADER, cookie.getValue());
	}

	private MockHttpServletRequestBuilder loginJson(String json) throws Exception {
		return withXsrf(post("/api/auth/login")).contentType(MediaType.APPLICATION_JSON).content(json);
	}

	private MockHttpSession loginAs(String password, int isDefault) throws Exception {
		stubUser(password, isDefault);
		MvcResult result = mockMvc.perform(loginJson("{\"loginId\":\"Wayne\",\"password\":\"" + password + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.loggedIn").value(true))
				.andExpect(jsonPath("$.userId").value("E0001"))
				.andExpect(jsonPath("$.roles[0]").value("infra"))
				.andExpect(jsonPath("$.mustChangePassword").value(isDefault == 1))
				.andReturn();
		MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
		assertThat(session).isNotNull();
		return session;
	}

	@Test
	void me未登入回200且寫IM_XSRF_cookie() throws Exception {
		mockMvc.perform(get("/api/auth/me"))
				.andExpect(status().isOk())
				.andExpect(content().json("{\"loggedIn\":false}", true))
				.andExpect(cookie().exists(SecurityConfig.XSRF_COOKIE))
				.andExpect(cookie().secure(SecurityConfig.XSRF_COOKIE, true))
				.andExpect(cookie().httpOnly(SecurityConfig.XSRF_COOKIE, false))
				.andExpect(cookie().path(SecurityConfig.XSRF_COOKIE, "/"))
				.andExpect(cookie().domain(SecurityConfig.XSRF_COOKIE, (String) null))
				.andExpect(cookie().sameSite(SecurityConfig.XSRF_COOKIE, "Lax"));
	}

	@Test
	void login沒帶或帶錯CSRF_header回403() throws Exception {
		stubUser("secret", 0);
		String json = "{\"loginId\":\"wayne\",\"password\":\"secret\"}";
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isForbidden())
				.andExpect(content().json("{\"message\":\"" + SecurityConfig.MSG_CSRF + "\"}"));

		Cookie cookie = xsrfCookie();
		mockMvc.perform(post("/api/auth/login").cookie(cookie).header(SecurityConfig.XSRF_HEADER, "wrong-token")
				.contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isForbidden())
				.andExpect(content().json("{\"message\":\"" + SecurityConfig.MSG_CSRF + "\"}"));
	}

	@Test
	void login成功建session_之後me回使用者() throws Exception {
		MockHttpSession session = loginAs("secret", 0);

		mockMvc.perform(get("/api/auth/me").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.loggedIn").value(true))
				.andExpect(jsonPath("$.loginId").value("wayne"))
				.andExpect(jsonPath("$.userName").value("測試人員"));
	}

	@Test
	void login帳密錯回401() throws Exception {
		stubUser("secret", 0);
		mockMvc.perform(loginJson("{\"loginId\":\"wayne\",\"password\":\"wrong\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().json("{\"message\":\"帳號或密碼錯誤\"}"));
		when(userDao.findByLoginId("nobody")).thenReturn(Optional.empty());
		mockMvc.perform(loginJson("{\"loginId\":\"nobody\",\"password\":\"secret\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().json("{\"message\":\"帳號或密碼錯誤\"}"));
	}

	@Test
	void login本文缺欄位或超長回400() throws Exception {
		mockMvc.perform(loginJson("{\"loginId\":\"wayne\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(content().json("{\"message\":\"請求格式錯誤\"}"));
		mockMvc.perform(loginJson("{\"loginId\":\"" + "a".repeat(65) + "\",\"password\":\"x\"}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(loginJson("not json"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void health免登入() throws Exception {
		// 監控用；本切片沒有 HealthController，通過安全層後是 404
		mockMvc.perform(get("/api/health")).andExpect(status().isNotFound());
	}

	@Test
	void api未登入回401_JSON不導頁() throws Exception {
		mockMvc.perform(get("/api/apps"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().doesNotExist(HttpHeaders.LOCATION))
				.andExpect(content().json("{\"message\":\"" + SecurityConfig.MSG_NOT_LOGGED_IN + "\"}"));
		mockMvc.perform(withXsrf(post("/api/apps")).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 預設密碼登入後打其他api回403請先改密碼() throws Exception {
		MockHttpSession session = loginAs("wayne", 1);

		mockMvc.perform(get("/api/apps").session(session))
				.andExpect(status().isForbidden())
				.andExpect(content().json("{\"message\":\"" + SecurityConfig.MSG_MUST_CHANGE_PWD + "\"}"));
		mockMvc.perform(get("/api/auth/me").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mustChangePassword").value(true));
	}

	@Test
	void 已改密碼者可打其他api_進到controller層() throws Exception {
		MockHttpSession session = loginAs("secret", 0);

		// 本測試切片沒有其他 controller，通過安全層後是 404（請求無法處理），不是 401／403
		mockMvc.perform(get("/api/apps").session(session))
				.andExpect(status().isNotFound());
	}

	@Test
	void logout回204且session失效() throws Exception {
		MockHttpSession session = loginAs("secret", 0);

		mockMvc.perform(withXsrf(post("/api/auth/logout")).session(session))
				.andExpect(status().isNoContent());

		assertThat(session.isInvalid()).isTrue();
		mockMvc.perform(get("/api/auth/me"))
				.andExpect(content().json("{\"loggedIn\":false}", true));
	}

	@Test
	void 未登入logout也是204() throws Exception {
		mockMvc.perform(withXsrf(post("/api/auth/logout"))).andExpect(status().isNoContent());
	}

	@Test
	void 非api路徑一律拒絕() throws Exception {
		mockMvc.perform(get("/actuator")).andExpect(status().isUnauthorized());

		MockHttpSession session = loginAs("secret", 0);
		mockMvc.perform(get("/actuator").session(session))
				.andExpect(status().isForbidden())
				.andExpect(content().json("{\"message\":\"" + SecurityConfig.MSG_FORBIDDEN + "\"}"));
	}

	@Test
	void logout不帶CSRF_header回403且session仍有效() throws Exception {
		MockHttpSession session = loginAs("secret", 0);

		mockMvc.perform(post("/api/auth/logout").session(session))
				.andExpect(status().isForbidden())
				.andExpect(content().json("{\"message\":\"" + SecurityConfig.MSG_CSRF + "\"}"));

		assertThat(session.isInvalid()).isFalse();
	}

	@Test
	void auth底下非公開路徑未登入401_預設密碼者可通過安全層() throws Exception {
		mockMvc.perform(get("/api/auth/password"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().json("{\"message\":\"" + SecurityConfig.MSG_NOT_LOGGED_IN + "\"}"));

		// 本切片沒有改密碼端點（回合二），通過安全層後是 404，不是 401／403
		MockHttpSession session = loginAs("wayne", 1);
		mockMvc.perform(get("/api/auth/password").session(session)).andExpect(status().isNotFound());
	}

	@Test
	void 超過72bytes的密碼登入回401不是500() throws Exception {
		stubUser("secret", 0);
		// bcrypt 只在 encode 時檢查 72 bytes 上限，matches 只比對前 72 bytes；100 字元 < LoginRequest.PASSWORD_MAX
		mockMvc.perform(loginJson("{\"loginId\":\"wayne\",\"password\":\"" + "a".repeat(100) + "\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void login成功後IM_XSRF換新值_logout後被清掉() throws Exception {
		stubUser("secret", 0);
		Cookie before = xsrfCookie();

		MvcResult login = mockMvc.perform(post("/api/auth/login").cookie(before)
				.header(SecurityConfig.XSRF_HEADER, before.getValue())
				.contentType(MediaType.APPLICATION_JSON).content("{\"loginId\":\"wayne\",\"password\":\"secret\"}"))
				.andExpect(status().isOk())
				.andReturn();
		Cookie after = login.getResponse().getCookie(SecurityConfig.XSRF_COOKIE);
		assertThat(after).isNotNull();
		assertThat(after.getValue()).isNotBlank().isNotEqualTo(before.getValue());
		assertThat(after.getSecure()).isTrue();
		assertThat(after.getPath()).isEqualTo("/");

		MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
		MvcResult logout = mockMvc.perform(post("/api/auth/logout").session(session).cookie(after)
				.header(SecurityConfig.XSRF_HEADER, after.getValue()))
				.andExpect(status().isNoContent())
				.andReturn();
		Cookie cleared = logout.getResponse().getCookie(SecurityConfig.XSRF_COOKIE);
		assertThat(cleared).isNotNull();
		assertThat(cleared.getMaxAge()).isZero();
	}

	@Test
	void 沒有Boot預設帳號_AuthenticationManager一律拒絕() {
		assertThat(context.getBeanProvider(UserDetailsService.class).getIfAvailable()).isNull();

		assertThatThrownBy(() -> authenticationManager
				.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("user", "whatever")))
				.isInstanceOf(AuthenticationException.class);
	}

	@Test
	void sessionCookie初始化器設定名稱屬性與逾時() throws Exception {
		ServletContextInitializer initializer = new SecurityConfig().sessionCookieInitializer();
		MockServletContext context = new MockServletContext();

		initializer.onStartup(context);

		assertThat(context.getSessionCookieConfig().getName()).isEqualTo("IM_SESSION");
		assertThat(context.getSessionCookieConfig().isHttpOnly()).isTrue();
		assertThat(context.getSessionCookieConfig().isSecure()).isTrue();
		assertThat(context.getSessionCookieConfig().getPath()).isEqualTo("/");
		assertThat(context.getSessionCookieConfig().getDomain()).isNull();
		assertThat(context.getSessionTimeout()).isEqualTo(480);
	}
}
