package com.mpx.infra_manager_java.service.auth;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：登入／登出／目前使用者（S2 回合一，裁示 ①A 修訂版：DB 帳密、bcrypt）。
//           登入不走 Spring Security 的 formLogin（那是 form-urlencoded + 302），由本類別自行比對後把
//           Authentication 存進 HttpSession（SecurityContextRepository），並換 session id 防 fixation。
//           查無帳號／停用／沒有雜湊時仍比對一次假雜湊，讓回應時間與密碼錯誤接近；三種失敗對外同一句訊息。
//           預設密碼（IS_DFLT_PWD=1）的人登入成功但不給 PWD_OK 權限，/api/** 除 /api/auth/** 外都會被擋 403。
//           log 不記輸入的帳號、不記密碼；成功／登出記工號，失敗記類別（帳號存在時也記工號），都附來源 IP
//           （ClientIp：殼 jar 附的 X-Forwarded-For，第 80 項定案前視為未驗證；code review 第 6 項、裁示 ②A）。
//           2026-10-06 code review：登出處理器改用注入的 SecurityContextRepository 與 holder strategy（與登入同一個實例）；
//           登入成功輪替 IM_XSRF、登出清掉 IM_XSRF（手動登入不會經過 Spring 內建的 CsrfAuthenticationStrategy／CsrfLogoutHandler）。
// ============================================================

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Service;

import com.mpx.infra_manager_java.dao.auth.UserDao;
import com.mpx.infra_manager_java.model.auth.AuthUser;
import com.mpx.infra_manager_java.model.auth.MeResponse;
import com.mpx.infra_manager_java.model.auth.UserRow;
import com.mpx.infra_manager_java.web.ClientIp;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	/** 已改過密碼才有的權限；/api/** 以此判斷是否放行 */
	public static final String AUTHORITY_PWD_OK = "PWD_OK";
	/** 角色權限前綴，與 @PreAuthorize("hasRole('admin')") 的慣例一致 */
	public static final String ROLE_PREFIX = "ROLE_";

	private final UserDao userDao;
	private final PasswordEncoder passwordEncoder;
	private final SecurityContextRepository contextRepository;
	private final CsrfTokenRepository csrfTokenRepository;
	private final SecurityContextHolderStrategy holderStrategy = SecurityContextHolder.getContextHolderStrategy();
	private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();
	/** 查無帳號時用來比對的假雜湊（啟動時算一次） */
	private final String dummyHash;

	public AuthService(UserDao userDao, PasswordEncoder passwordEncoder, SecurityContextRepository contextRepository,
			CsrfTokenRepository csrfTokenRepository) {
		this.userDao = userDao;
		this.passwordEncoder = passwordEncoder;
		this.contextRepository = contextRepository;
		this.csrfTokenRepository = csrfTokenRepository;
		this.logoutHandler.setSecurityContextRepository(contextRepository);
		this.logoutHandler.setSecurityContextHolderStrategy(holderStrategy);
		this.dummyHash = passwordEncoder.encode("dummy-for-constant-time");
	}

	/** 登入成功回使用者並寫入 session；失敗回 empty（原因不對外區分） */
	public Optional<AuthUser> login(String loginId, String password, HttpServletRequest request,
			HttpServletResponse response) {
		Optional<UserRow> found = userDao.findByLoginId(normalizeLoginId(loginId));
		if (found.isEmpty() || found.get().getPwdHash() == null || !Integer.valueOf(1).equals(found.get().getStatus())) {
			passwordEncoder.matches(password, dummyHash);
			log.info("登入失敗 reason={} user={} srcIp={}", found.isEmpty() ? "no_user" : "inactive_or_no_hash",
					found.map(UserRow::getUserId).orElse("-"), ClientIp.of(request));
			return Optional.empty();
		}
		UserRow row = found.get();
		if (!passwordEncoder.matches(password, row.getPwdHash())) {
			log.info("登入失敗 reason=bad_password user={} srcIp={}", row.getUserId(), ClientIp.of(request));
			return Optional.empty();
		}
		AuthUser user = new AuthUser(row.getUserId(), row.getLoginId(), row.getUserName(),
				userDao.findActiveRoleIds(row.getUserId()), Integer.valueOf(1).equals(row.getIsDfltPwd()));
		establishSession(user, request, response);
		log.info("登入成功 user={} defaultPassword={} srcIp={}", user.userId(), user.mustChangePassword(),
				ClientIp.of(request));
		return Optional.of(user);
	}

	/** 清掉 SecurityContext、讓 session 失效、清掉 IM_XSRF；未登入時呼叫也安全 */
	public void logout(HttpServletRequest request, HttpServletResponse response) {
		Authentication auth = holderStrategy.getContext().getAuthentication();
		logoutHandler.logout(request, response, auth);
		holderStrategy.clearContext();
		csrfTokenRepository.saveToken(null, request, response);
		if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
			log.info("登出 user={} srcIp={}", user.userId(), ClientIp.of(request));
		}
	}

	public MeResponse me(Authentication authentication) {
		if (authentication != null && authentication.isAuthenticated()
				&& authentication.getPrincipal() instanceof AuthUser user) {
			return MeResponse.of(user);
		}
		return MeResponse.anonymous();
	}

	/** 去頭尾空白、轉小寫（LOGIN_ID 表上有 CHECK 必為小寫）；中間空白保留（舊帳號如 "alan kuo"） */
	static String normalizeLoginId(String loginId) {
		return loginId == null ? "" : loginId.trim().toLowerCase(Locale.ROOT);
	}

	static Collection<GrantedAuthority> authorities(AuthUser user) {
		List<GrantedAuthority> list = new ArrayList<>();
		for (String role : user.roles()) {
			list.add(new SimpleGrantedAuthority(ROLE_PREFIX + role));
		}
		if (!user.mustChangePassword()) {
			list.add(new SimpleGrantedAuthority(AUTHORITY_PWD_OK));
		}
		return list;
	}

	/**
	 * 既有 session（例如只拿過 CSRF cookie）先換 id 防 session fixation，再把 Authentication 存進去，
	 * 最後換一個新的 CSRF token 寫回 IM_XSRF cookie（前端下一次請求從 cookie 讀新值）
	 */
	private void establishSession(AuthUser user, HttpServletRequest request, HttpServletResponse response) {
		HttpSession existing = request.getSession(false);
		if (existing != null) {
			request.changeSessionId();
		}
		Authentication auth = UsernamePasswordAuthenticationToken.authenticated(user, null, authorities(user));
		SecurityContext context = holderStrategy.createEmptyContext();
		context.setAuthentication(auth);
		holderStrategy.setContext(context);
		contextRepository.saveContext(context, request, response);
		csrfTokenRepository.saveToken(csrfTokenRepository.generateToken(request), request, response);
	}
}
