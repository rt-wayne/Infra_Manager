package com.mpx.infra_manager_java.config;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：Spring Security 設定（S2 回合一，PRD「認證」段；裁示 ③A 不做 remember-me、④A 不接 Spring Session）。
//           session cookie IM_SESSION：HttpOnly、Secure 明確設 true（殼 jar 轉來的是 http，不能靠 request.isSecure()）、
//             Path=/、不設 Domain、SameSite=Lax（CookieSameSiteSupplier 對 IM_ 開頭的 cookie 生效）、閒置 8 小時；
//             這些是產品決策，寫在程式常數、進 git，不放 properties 真檔（與 RequestLimitConfig 同一原則）。
//           CSRF：cookie IM_XSRF（非 HttpOnly、Secure、SameSite=Lax、Path=/）＋ header X-IM-XSRF；login 也要帶。
//           未登入打 /api/** → 401 JSON、不導頁、不記 request cache；權限不足／CSRF 不符 → 403 JSON；
//           預設密碼未改（沒有 PWD_OK 權限）只能用 /api/auth/**，其餘 403「請先修改預設密碼」。
//           formLogin／httpBasic／logout 全關，由 AuthController 自己處理；密碼雜湊 bcrypt（裁示 ⑦A，DelegatingPasswordEncoder {bcrypt}）。
//           2026-10-06 code review：
//           ①A 宣告一個一律拒絕的 AuthenticationManager，讓 Boot 的 UserDetailsServiceAutoConfiguration 不啟用
//              （它會建帳號 user 並把隨機密碼用 WARN 印進 log）；本系統登入不經 AuthenticationManager。
//           刪掉沒作用的 .sessionFixation()（手動登入不會經過 Spring 的登入後策略；換 session id 由 AuthService 自己做）。
//           CsrfTokenRepository 改成 bean，AuthService 登入時輪替、登出時清掉 IM_XSRF（與 Spring 內建 formLogin 行為一致）。
//           403（CSRF 不符、預設密碼未改、權限不足）記 log：路徑、工號、類別、來源 IP（security.md A09）。
//           2026-10-06 S2 回合二：SecurityFilterChain、session cookie、SameSite 三個 bean 加 @ConditionalOnWebApplication(SERVLET)，
//           匯入器以 web-application-type=none 啟動時沒有 HttpSecurity 也能起；PasswordEncoder 等純物件 bean 不設條件（匯入器要用）
//           2026-10-07 S6 回合三 B5（Claude Opus 5.5）：CSRF token 改只從 header 讀（HeaderOnlyCsrfTokenRequestHandler），
//           不再退回表單參數，避免沒帶 header 的 multipart 在被擋下前就讓 Tomcat 解析本文、寫暫存檔
// ============================================================

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.web.server.servlet.CookieSameSiteSupplier;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderNotFoundException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;

import com.mpx.infra_manager_java.model.auth.AuthUser;
import com.mpx.infra_manager_java.service.auth.AuthService;
import com.mpx.infra_manager_java.web.ClientIp;
import com.mpx.infra_manager_java.web.CsrfCookieFilter;
import com.mpx.infra_manager_java.web.HeaderOnlyCsrfTokenRequestHandler;
import com.mpx.infra_manager_java.web.JsonResponses;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

	private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

	public static final String SESSION_COOKIE = "IM_SESSION";
	public static final String XSRF_COOKIE = "IM_XSRF";
	public static final String XSRF_HEADER = "X-IM-XSRF";
	/** 閒置逾時（分鐘） */
	public static final int SESSION_TIMEOUT_MINUTES = 8 * 60;

	public static final String MSG_NOT_LOGGED_IN = "尚未登入";
	public static final String MSG_FORBIDDEN = "無權限執行此操作";
	public static final String MSG_CSRF = "安全驗證失敗，請重新整理頁面後再試";
	public static final String MSG_MUST_CHANGE_PWD = "請先修改預設密碼";

	@Bean
	public PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	public SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	/** IM_XSRF cookie 儲存庫；AuthService 登入時輪替、登出時清掉，與 CsrfFilter 用同一個實例 */
	@Bean
	public CsrfTokenRepository csrfTokenRepository() {
		CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		repository.setCookieName(XSRF_COOKIE);
		repository.setHeaderName(XSRF_HEADER);
		repository.setCookiePath("/");
		repository.setCookieCustomizer(cookie -> cookie.secure(true).sameSite("Lax"));
		return repository;
	}

	/**
	 * 本系統登入由 AuthService 直接比對密碼，不經 AuthenticationManager；這個 bean 只為了讓 Boot 的
	 * UserDetailsServiceAutoConfiguration 不啟用（它會建預設帳號 user 並把隨機密碼印進 log，code review 第 1 項、裁示 ①A），
	 * 任何人經此路徑認證一律拒絕
	 */
	@Bean
	public AuthenticationManager authenticationManager() {
		return authentication -> {
			throw new ProviderNotFoundException("本系統不經 AuthenticationManager 認證");
		};
	}

	@Bean
	@ConditionalOnWebApplication(type = Type.SERVLET)
	public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, SecurityContextRepository contextRepository,
			CsrfTokenRepository csrfRepository) throws Exception {
		http.securityContext(sc -> sc.securityContextRepository(contextRepository))
				.csrf(csrf -> csrf.csrfTokenRepository(csrfRepository)
						.csrfTokenRequestHandler(new HeaderOnlyCsrfTokenRequestHandler()))
				.addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
				.authorizeHttpRequests(auth -> auth
						.dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.ASYNC).permitAll()
						.requestMatchers("/api/health", "/api/auth/login", "/api/auth/logout", "/api/auth/me").permitAll()
						.requestMatchers("/api/auth/**").authenticated()
						.requestMatchers("/api/**").hasAuthority(AuthService.AUTHORITY_PWD_OK)
						.anyRequest().denyAll())
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint((request, response, e) -> JsonResponses.write(response,
								HttpServletResponse.SC_UNAUTHORIZED, MSG_NOT_LOGGED_IN))
						.accessDeniedHandler(accessDeniedHandler()))
				.requestCache(RequestCacheConfigurer::disable)
				// session fixation 防護不在這裡設：Spring 的登入後策略只對 formLogin 等內建機制生效，
				// 本系統手動登入，由 AuthService.establishSession 自己 changeSessionId()
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable);
		return http.build();
	}

	/** CSRF 不符、預設密碼未改、一般權限不足各給一句固定訊息；全部 403，並記一行 log（工號、類別、來源 IP） */
	static AccessDeniedHandler accessDeniedHandler() {
		return (request, response, e) -> {
			String message = MSG_FORBIDDEN;
			String kind = "forbidden";
			AuthUser user = authUser(request);
			if (e instanceof CsrfException) {
				message = MSG_CSRF;
				kind = "csrf";
			} else if (user != null && user.mustChangePassword()) {
				message = MSG_MUST_CHANGE_PWD;
				kind = "default_password";
			}
			log.info("權限拒絕 {} {} user={} kind={} srcIp={}", request.getMethod(), request.getRequestURI(),
					user == null ? "-" : user.userId(), kind, ClientIp.of(request));
			JsonResponses.write(response, HttpServletResponse.SC_FORBIDDEN, message);
		};
	}

	private static AuthUser authUser(HttpServletRequest request) {
		return request.getUserPrincipal() instanceof org.springframework.security.core.Authentication auth
				&& auth.getPrincipal() instanceof AuthUser user ? user : null;
	}

	/** session cookie 屬性與閒置逾時；在 Boot 自己的 session 設定之後執行，所以會覆蓋 properties 的同名設定 */
	@Bean
	@ConditionalOnWebApplication(type = Type.SERVLET)
	public ServletContextInitializer sessionCookieInitializer() {
		return servletContext -> {
			SessionCookieConfig cookie = servletContext.getSessionCookieConfig();
			cookie.setName(SESSION_COOKIE);
			cookie.setHttpOnly(true);
			cookie.setSecure(true);
			cookie.setPath("/");
			servletContext.setSessionTimeout(SESSION_TIMEOUT_MINUTES);
		};
	}

	/** Tomcat 層對 IM_ 開頭的 cookie 加 SameSite=Lax（Servlet 標準的 SessionCookieConfig 沒有 SameSite 欄位） */
	@Bean
	@ConditionalOnWebApplication(type = Type.SERVLET)
	public CookieSameSiteSupplier imCookieSameSite() {
		return CookieSameSiteSupplier.ofLax().whenHasNameMatching("IM_.*");
	}
}
