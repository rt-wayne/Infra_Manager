package com.mpx.infra_manager_java.service.auth;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：AuthService 單元測試（S2 回合一；mock UserDao、真的 bcrypt encoder 與 HttpSession 儲存庫，不連 DB）
//           驗證：帳號正規化、查無／停用／無雜湊／密碼錯都回 empty、成功時寫入 session 並換 session id、
//                權限（ROLE_ 前綴、PWD_OK 只在非預設密碼時給）、登出讓 session 失效、me 的兩種回應
//           2026-10-06 code review：加登入成功時寫出新的 CSRF cookie、登出時 CSRF cookie 被清掉（Max-Age=0）、
//                AuthUser.toString 只含工號、CSRF 儲存庫改由建構子注入
//           2026-10-06 S2 回合二：加 changePassword——規則不符（太短／超過 72 bytes／等於預設／等於舊密碼）不碰 DB、
//                舊密碼錯、帳號停用、成功時寫新雜湊並重建 session（PWD_OK、換 id、換 IM_XSRF）、更新 0 列丟例外
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import com.mpx.infra_manager_java.model.auth.ChangePasswordRequest;
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

	/** loggedInWithDefaultPassword 用的那筆帳號（改密碼成功要驗 UPDATE 帶的是這筆的舊雜湊） */
	private UserRow currentRow;

	/** 以預設密碼登入後的狀態（session 已建、mustChangePassword=true） */
	private AuthUser loggedInWithDefaultPassword() {
		currentRow = user("wayne", 1, 1);
		when(userDao.findByLoginId("wayne")).thenReturn(Optional.of(currentRow));
		when(userDao.findActiveRoleIds("E0001")).thenReturn(List.of("infra"));
		return service.login("wayne", "wayne", request, response).orElseThrow();
	}

	/** 失敗路徑共同要求：session id 與 IM_XSRF 都不能被換掉、不能寫 DB */
	private void assertSessionUntouched(String sessionIdBefore, Cookie xsrfBefore, MockHttpServletResponse failResponse) {
		assertThat(request.getSession(false).getId()).isEqualTo(sessionIdBefore);
		assertThat(failResponse.getCookie("XSRF-TOKEN")).isNull();
		assertThat(response.getCookie("XSRF-TOKEN").getValue()).isEqualTo(xsrfBefore.getValue());
		verify(userDao, never()).updatePassword(anyString(), anyString(), anyString());
	}

	@Test
	void 改密碼規則不符時不碰DB並回對應訊息() {
		AuthUser current = loggedInWithDefaultPassword();
		clearInvocations(userDao);

		assertThatThrownBy(() -> service.changePassword(current, "wayne", "      ", request, response))
				.isInstanceOf(PasswordRuleException.class).hasMessage(AuthService.MSG_PWD_BLANK);
		// 5 字不足、6 字才夠（恰好 6 字通過見另一條測試）；emoji 一個算一字，3 個 emoji 是 3 字不是 6 個 code unit
		assertThatThrownBy(() -> service.changePassword(current, "wayne", "abcde", request, response))
				.isInstanceOf(PasswordRuleException.class).hasMessage(AuthService.MSG_PWD_TOO_SHORT);
		assertThatThrownBy(() -> service.changePassword(current, "wayne", "😀".repeat(3), request, response))
				.isInstanceOf(PasswordRuleException.class).hasMessage(AuthService.MSG_PWD_TOO_SHORT);
		// 24 個中文字 = 72 bytes 剛好可以；25 個 = 75 bytes 超過；超過 128 字也是「過長」不是「請求格式錯誤」
		assertThatThrownBy(() -> service.changePassword(current, "wayne", "密".repeat(25), request, response))
				.isInstanceOf(PasswordRuleException.class).hasMessage(AuthService.MSG_PWD_TOO_LONG);
		assertThatThrownBy(() -> service.changePassword(current, "wayne", "x".repeat(129), request, response))
				.isInstanceOf(PasswordRuleException.class).hasMessage(AuthService.MSG_PWD_TOO_LONG);
		// wayne 只有 5 字，先撞到「至少 6 字」（預設密碼本身不受 6 字限制，只有新密碼受限）；
		// 等於預設密碼那條用 6 字以上的帳號驗，不分大小寫，且比「等於舊密碼」先判
		assertThatThrownBy(() -> service.changePassword(current, "wayne", "wayne", request, response))
				.isInstanceOf(PasswordRuleException.class).hasMessage(AuthService.MSG_PWD_TOO_SHORT);
		AuthUser alan = new AuthUser("E0002", "alan kuo", "Alan", List.of(), true);
		assertThatThrownBy(() -> service.changePassword(alan, "alan kuo", "alan kuo", request, response))
				.isInstanceOf(PasswordRuleException.class).hasMessage(AuthService.MSG_PWD_EQUALS_DEFAULT);
		assertThatThrownBy(() -> service.changePassword(alan, "alan kuo", "ALAN KUO", request, response))
				.isInstanceOf(PasswordRuleException.class).hasMessage(AuthService.MSG_PWD_EQUALS_DEFAULT);
		assertThatThrownBy(() -> service.changePassword(current, "abcdef", "abcdef", request, response))
				.isInstanceOf(PasswordRuleException.class).hasMessage(AuthService.MSG_PWD_SAME_AS_OLD);

		verifyNoInteractions(userDao);
	}

	@Test
	void 改密碼舊密碼錯回舊密碼錯誤且session不變() {
		AuthUser current = loggedInWithDefaultPassword();
		String sessionIdBefore = request.getSession(false).getId();
		Cookie xsrfBefore = response.getCookie("XSRF-TOKEN");
		MockHttpServletResponse failResponse = new MockHttpServletResponse();

		assertThatThrownBy(() -> service.changePassword(current, "wrong", "newpass1", request, failResponse))
				.isInstanceOf(PasswordRuleException.class).hasMessage(AuthService.MSG_OLD_PWD_WRONG)
				.extracting("reason").isEqualTo("bad_old_password");

		assertSessionUntouched(sessionIdBefore, xsrfBefore, failResponse);
	}

	@Test
	void 改密碼時帳號查無_無雜湊_停用_工號不同都回請重新登入() {
		AuthUser current = loggedInWithDefaultPassword();
		String sessionIdBefore = request.getSession(false).getId();
		Cookie xsrfBefore = response.getCookie("XSRF-TOKEN");
		MockHttpServletResponse failResponse = new MockHttpServletResponse();
		UserRow renamed = user("wayne", 1, 1);
		renamed.setUserId("E0099");

		for (Optional<UserRow> found : List.of(Optional.<UserRow>empty(), Optional.of(user(null, 1, 1)),
				Optional.of(user("wayne", 0, 1)), Optional.of(renamed))) {
			when(userDao.findByLoginId("wayne")).thenReturn(found);
			assertThatThrownBy(() -> service.changePassword(current, "wayne", "newpass1", request, failResponse))
					.isInstanceOf(PasswordRuleException.class).hasMessage(AuthService.MSG_ACCOUNT_UNAVAILABLE)
					.extracting("reason").isEqualTo("account_unavailable");
		}

		assertSessionUntouched(sessionIdBefore, xsrfBefore, failResponse);
	}

	@Test
	void 改密碼恰好6字通過() {
		AuthUser current = loggedInWithDefaultPassword();
		when(userDao.updatePassword(eq("E0001"), anyString(), eq(currentRow.getPwdHash()))).thenReturn(1);

		assertThat(service.changePassword(current, "wayne", "abcdef", request, response).mustChangePassword()).isFalse();
	}

	@Test
	void 改密碼成功寫入bcrypt並重建session() {
		AuthUser current = loggedInWithDefaultPassword();
		String oldSessionId = request.getSession(false).getId();
		Cookie xsrfBefore = response.getCookie("XSRF-TOKEN");
		when(userDao.updatePassword(eq("E0001"), anyString(), eq(currentRow.getPwdHash()))).thenReturn(1);
		when(userDao.findActiveRoleIds("E0001")).thenReturn(List.of("infra", "admin"));
		MockHttpServletResponse changeResponse = new MockHttpServletResponse();

		AuthUser user = service.changePassword(current, "wayne", "密".repeat(24), request, changeResponse);

		assertThat(user.mustChangePassword()).isFalse();
		assertThat(user.roles()).containsExactly("infra", "admin");
		verify(userDao).updatePassword(eq("E0001"), argThat(hash -> encoder.matches("密".repeat(24), hash)),
				eq(currentRow.getPwdHash()));

		MockHttpSession session = (MockHttpSession) request.getSession(false);
		assertThat(session.getId()).isNotEqualTo(oldSessionId);
		SecurityContext saved = (SecurityContext) session
				.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
		assertThat(saved.getAuthentication().getPrincipal()).isEqualTo(user);
		assertThat(saved.getAuthentication().getAuthorities()).extracting(GrantedAuthority::getAuthority)
				.containsExactlyInAnyOrder("ROLE_infra", "ROLE_admin", "PWD_OK");
		Cookie xsrfAfter = changeResponse.getCookie("XSRF-TOKEN");
		assertThat(xsrfAfter).isNotNull();
		assertThat(xsrfAfter.getValue()).isNotBlank().isNotEqualTo(xsrfBefore.getValue());
	}

	@Test
	void 改密碼更新0列視為帳號狀態已變更且session不變() {
		AuthUser current = loggedInWithDefaultPassword();
		String sessionIdBefore = request.getSession(false).getId();
		Cookie xsrfBefore = response.getCookie("XSRF-TOKEN");
		MockHttpServletResponse failResponse = new MockHttpServletResponse();
		// 查到之後、UPDATE 之前被停用或密碼被另一個請求改掉：WHERE 的舊雜湊對不上 → 0 列
		when(userDao.updatePassword(anyString(), anyString(), anyString())).thenReturn(0);

		assertThatThrownBy(() -> service.changePassword(current, "wayne", "newpass1", request, failResponse))
				.isInstanceOf(PasswordRuleException.class).hasMessage(AuthService.MSG_ACCOUNT_UNAVAILABLE)
				.extracting("reason").isEqualTo("account_unavailable");

		assertThat(request.getSession(false).getId()).isEqualTo(sessionIdBefore);
		assertThat(failResponse.getCookie("XSRF-TOKEN")).isNull();
		assertThat(response.getCookie("XSRF-TOKEN").getValue()).isEqualTo(xsrfBefore.getValue());
	}

	@Test
	void 改密碼請求本文格式檢查() {
		assertThat(new ChangePasswordRequest("a", "b").isValid()).isTrue();
		assertThat(new ChangePasswordRequest(null, "b").isValid()).isFalse();
		assertThat(new ChangePasswordRequest("a", null).isValid()).isFalse();
		assertThat(new ChangePasswordRequest("a", "").isValid()).isFalse();
		assertThat(new ChangePasswordRequest("", "b").isValid()).isFalse();
		assertThat(new ChangePasswordRequest("x".repeat(129), "b").isValid()).isFalse();
		// 新密碼超長不在這裡擋，交給 AuthService 回「新密碼過長」
		assertThat(new ChangePasswordRequest("a", "x".repeat(129)).isValid()).isTrue();
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
