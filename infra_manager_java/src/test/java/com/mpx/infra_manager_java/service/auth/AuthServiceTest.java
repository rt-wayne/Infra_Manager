package com.mpx.infra_manager_java.service.auth;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：AuthService 單元測試（S2 回合一；mock UserDao、真的 bcrypt encoder 與 HttpSession 儲存庫，不連 DB）
//           驗證：帳號正規化、查無／停用／無雜湊／密碼錯都回 empty、成功時寫入 session 並換 session id、
//                權限（ROLE_ 前綴、PWD_OK 只在非預設密碼時給）、登出讓 session 失效、me 的兩種回應
//           2026-10-06 code review：加登入成功時寫出新的 CSRF cookie、登出時 CSRF cookie 被清掉（Max-Age=0）、
//                AuthUser.toString 只含工號、CSRF 儲存庫改由建構子注入
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import com.mpx.infra_manager_java.dao.auth.UserDao;
import com.mpx.infra_manager_java.model.auth.AuthUser;
import com.mpx.infra_manager_java.model.auth.MeResponse;
import com.mpx.infra_manager_java.model.auth.UserRow;

import jakarta.servlet.http.Cookie;

class AuthServiceTest {

	private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
	private final CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
	private UserDao userDao;
	private AuthService service;
	private MockHttpServletRequest request;
	private MockHttpServletResponse response;

	@BeforeEach
	void setUp() {
		userDao = mock(UserDao.class);
		service = new AuthService(userDao, encoder, new HttpSessionSecurityContextRepository(), csrfRepository);
		request = new MockHttpServletRequest("POST", "/api/auth/login");
		response = new MockHttpServletResponse();
	}

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	private UserRow user(String password, int status, int isDefault) {
		UserRow row = new UserRow();
		row.setUserId("E0001");
		row.setLoginId("wayne");
		row.setUserName("測試人員");
		row.setPwdHash(password == null ? null : encoder.encode(password));
		row.setStatus(status);
		row.setIsDfltPwd(isDefault);
		return row;
	}

	@Test
	void 帳號去空白轉小寫後查詢() {
		when(userDao.findByLoginId(anyString())).thenReturn(Optional.empty());

		assertThat(service.login("  Wayne ", "x", request, response)).isEmpty();

		verify(userDao).findByLoginId("wayne");
		assertThat(AuthService.normalizeLoginId(" Alan Kuo ")).isEqualTo("alan kuo");
		assertThat(AuthService.normalizeLoginId(null)).isEmpty();
	}

	@Test
	void 查無帳號回empty且不建session() {
		when(userDao.findByLoginId("nobody")).thenReturn(Optional.empty());

		assertThat(service.login("nobody", "secret", request, response)).isEmpty();
		assertThat(request.getSession(false)).isNull();
	}

	@Test
	void 停用帳號回empty() {
		when(userDao.findByLoginId("wayne")).thenReturn(Optional.of(user("secret", 0, 0)));

		assertThat(service.login("wayne", "secret", request, response)).isEmpty();
	}

	@Test
	void 沒有密碼雜湊回empty() {
		when(userDao.findByLoginId("wayne")).thenReturn(Optional.of(user(null, 1, 0)));

		assertThat(service.login("wayne", "", request, response)).isEmpty();
	}

	@Test
	void 密碼錯回empty() {
		when(userDao.findByLoginId("wayne")).thenReturn(Optional.of(user("secret", 1, 0)));

		assertThat(service.login("wayne", "wrong", request, response)).isEmpty();
		assertThat(request.getSession(false)).isNull();
	}

	@Test
	void 成功時寫入session並給角色與PWD_OK權限() {
		when(userDao.findByLoginId("wayne")).thenReturn(Optional.of(user("secret", 1, 0)));
		when(userDao.findActiveRoleIds("E0001")).thenReturn(List.of("admin", "infra"));

		Optional<AuthUser> result = service.login("wayne", "secret", request, response);

		assertThat(result).isPresent();
		assertThat(result.get().userId()).isEqualTo("E0001");
		assertThat(result.get().roles()).containsExactly("admin", "infra");
		assertThat(result.get().mustChangePassword()).isFalse();

		MockHttpSession session = (MockHttpSession) request.getSession(false);
		assertThat(session).isNotNull();
		SecurityContext saved = (SecurityContext) session
				.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
		assertThat(saved).isNotNull();
		assertThat(saved.getAuthentication().getPrincipal()).isEqualTo(result.get());
		assertThat(saved.getAuthentication().getAuthorities()).extracting(GrantedAuthority::getAuthority)
				.containsExactlyInAnyOrder("ROLE_admin", "ROLE_infra", "PWD_OK");
	}

	@Test
	void 預設密碼登入成功但沒有PWD_OK權限() {
		when(userDao.findByLoginId("wayne")).thenReturn(Optional.of(user("wayne", 1, 1)));
		when(userDao.findActiveRoleIds("E0001")).thenReturn(List.of("infra"));

		Optional<AuthUser> result = service.login("wayne", "wayne", request, response);

		assertThat(result).isPresent();
		assertThat(result.get().mustChangePassword()).isTrue();
		SecurityContext saved = (SecurityContext) request.getSession(false)
				.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
		assertThat(saved.getAuthentication().getAuthorities()).extracting(GrantedAuthority::getAuthority)
				.containsExactly("ROLE_infra");
	}

	@Test
	void 已有session時登入會換sessionId() {
		MockHttpSession before = new MockHttpSession();
		request.setSession(before);
		String oldId = before.getId();
		when(userDao.findByLoginId("wayne")).thenReturn(Optional.of(user("secret", 1, 0)));
		when(userDao.findActiveRoleIds("E0001")).thenReturn(List.of());

		assertThat(service.login("wayne", "secret", request, response)).isPresent();

		assertThat(request.getSession(false).getId()).isNotEqualTo(oldId);
	}

	@Test
	void 登出讓session失效並清掉context() {
		when(userDao.findByLoginId("wayne")).thenReturn(Optional.of(user("secret", 1, 0)));
		when(userDao.findActiveRoleIds("E0001")).thenReturn(List.of());
		service.login("wayne", "secret", request, response);
		MockHttpSession session = (MockHttpSession) request.getSession(false);

		service.logout(request, response);

		assertThat(session.isInvalid()).isTrue();
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	@Test
	void 未登入時登出不丟例外() {
		service.logout(new MockHttpServletRequest(), new MockHttpServletResponse());
	}

	@Test
	void 登入成功寫出新的CSRF_cookie_登出時清掉() {
		when(userDao.findByLoginId("wayne")).thenReturn(Optional.of(user("secret", 1, 0)));
		when(userDao.findActiveRoleIds("E0001")).thenReturn(List.of());
		request.setCookies(new Cookie("XSRF-TOKEN", "old-token"));

		service.login("wayne", "secret", request, response);

		Cookie rotated = response.getCookie("XSRF-TOKEN");
		assertThat(rotated).isNotNull();
		assertThat(rotated.getValue()).isNotBlank().isNotEqualTo("old-token");
		assertThat(rotated.getMaxAge()).isEqualTo(-1);

		MockHttpServletResponse logoutResponse = new MockHttpServletResponse();
		service.logout(request, logoutResponse);

		Cookie cleared = logoutResponse.getCookie("XSRF-TOKEN");
		assertThat(cleared).isNotNull();
		assertThat(cleared.getMaxAge()).isZero();
		assertThat(cleared.getValue()).isNullOrEmpty();
	}

	@Test
	void 密碼錯時不寫CSRF_cookie() {
		when(userDao.findByLoginId("wayne")).thenReturn(Optional.of(user("secret", 1, 0)));

		service.login("wayne", "wrong", request, response);

		assertThat(response.getCookie("XSRF-TOKEN")).isNull();
	}

	@Test
	void AuthUser的toString只含工號() {
		AuthUser user = new AuthUser("E0001", "wayne", "測試人員", List.of("admin"), false);

		assertThat(user.toString()).isEqualTo("AuthUser[userId=E0001]").doesNotContain("wayne", "測試人員");
	}

	@Test
	void me依Authentication回兩種結果() {
		assertThat(service.me(null)).isEqualTo(MeResponse.anonymous());

		when(userDao.findByLoginId("wayne")).thenReturn(Optional.of(user("secret", 1, 0)));
		when(userDao.findActiveRoleIds("E0001")).thenReturn(List.of("admin"));
		AuthUser user = service.login("wayne", "secret", request, response).orElseThrow();
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();

		MeResponse me = service.me(auth);
		assertThat(me.loggedIn()).isTrue();
		assertThat(me.userId()).isEqualTo(user.userId());
		assertThat(me.roles()).containsExactly("admin");
		assertThat(me.mustChangePassword()).isFalse();
	}
}
