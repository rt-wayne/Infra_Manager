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
//           2026-10-06 S2 回合二：加 changePassword。規則依序：不得全空白 → 至少 6 字（code point 計數，emoji 算 1）→
//           UTF-8 不超過 72 bytes（bcrypt encode 超過會丟例外，matches 不會，所以登入不受影響但這裡沒驗會 500）→
//           不得等於預設密碼（帳號，不分大小寫）→ 不得等於舊密碼 → 帳號仍啟用且是同一個工號 → 舊密碼要對。
//           這些規則只套在「新密碼」；預設密碼（帳號小寫）本身不受 6 字限制（如 wayne）。
//           成功後重建 session（換 session id、重查角色、補 PWD_OK、換發 IM_XSRF），不用重新登入。
//           2026-10-06 code review：UPDATE 以舊雜湊做樂觀鎖，0 列（期間被停用或密碼已被改）回 400「帳號狀態已變更」而非 500；
//           角色查詢移到 UPDATE 之前，DB 寫入後只剩純記憶體動作；查無／無雜湊／非啟用判斷抽成 usable() 與 login 共用。
// ============================================================

import java.nio.charset.StandardCharsets;
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

	/** 新密碼最少字元數（沿用舊系統） */
	public static final int PASSWORD_MIN_CHARS = 6;
	/** bcrypt 只看前 72 bytes，超過在 encode 時會丟例外 */
	public static final int PASSWORD_MAX_BYTES = 72;

	public static final String MSG_PWD_BLANK = "新密碼不得全為空白";
	public static final String MSG_PWD_TOO_SHORT = "新密碼至少 " + PASSWORD_MIN_CHARS + " 個字";
	public static final String MSG_PWD_TOO_LONG = "新密碼過長（上限英數 " + PASSWORD_MAX_BYTES + " 字，中文約 "
			+ (PASSWORD_MAX_BYTES / 3) + " 字）";
	public static final String MSG_PWD_EQUALS_DEFAULT = "新密碼不得與預設密碼（帳號）相同";
	public static final String MSG_PWD_SAME_AS_OLD = "新密碼不得與舊密碼相同";
	public static final String MSG_OLD_PWD_WRONG = "舊密碼錯誤";
	public static final String MSG_ACCOUNT_UNAVAILABLE = "帳號狀態已變更，請重新登入";

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
		if (!usable(found)) {
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

	/**
	 * 改密碼。規則不符丟 PasswordRuleException（controller 回 400 帶訊息）；成功回重建後的使用者（mustChangePassword=false）
	 * 並已寫入 session。UPDATE 以查到的舊雜湊當條件，期間帳號被停用或密碼已被另一個請求改掉都會是 0 列 → 一樣回
	 * 「帳號狀態已變更，請重新登入」，不會互相覆蓋也不會 500
	 */
	public AuthUser changePassword(AuthUser current, String oldPassword, String newPassword,
			HttpServletRequest request, HttpServletResponse response) {
		checkNewPassword(current, oldPassword, newPassword, request);
		Optional<UserRow> found = userDao.findByLoginId(current.loginId());
		if (!usable(found) || !found.get().getUserId().equals(current.userId())) {
			throw reject("account_unavailable", MSG_ACCOUNT_UNAVAILABLE, current, request);
		}
		UserRow row = found.get();
		if (!passwordEncoder.matches(oldPassword, row.getPwdHash())) {
			throw reject("bad_old_password", MSG_OLD_PWD_WRONG, current, request);
		}
		List<String> roles = userDao.findActiveRoleIds(row.getUserId());
		int updated = userDao.updatePassword(row.getUserId(), passwordEncoder.encode(newPassword), row.getPwdHash());
		if (updated != 1) {
			throw reject("account_unavailable", MSG_ACCOUNT_UNAVAILABLE, current, request);
		}
		AuthUser user = new AuthUser(row.getUserId(), row.getLoginId(), row.getUserName(), roles, false);
		establishSession(user, request, response);
		log.info("改密碼成功 user={} srcIp={}", user.userId(), ClientIp.of(request));
		return user;
	}

	/** 有這筆帳號、有雜湊、且啟用中，才可登入／改密碼 */
	private static boolean usable(Optional<UserRow> found) {
		return found.isPresent() && found.get().getPwdHash() != null
				&& Integer.valueOf(1).equals(found.get().getStatus());
	}

	/** 不碰 DB 就能判斷的規則；順序固定，讓使用者一次只看到一個最該先修的問題 */
	private void checkNewPassword(AuthUser current, String oldPassword, String newPassword, HttpServletRequest request) {
		if (newPassword.isBlank()) {
			throw reject("blank", MSG_PWD_BLANK, current, request);
		}
		if (newPassword.codePointCount(0, newPassword.length()) < PASSWORD_MIN_CHARS) {
			throw reject("too_short", MSG_PWD_TOO_SHORT, current, request);
		}
		if (newPassword.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES) {
			throw reject("too_long", MSG_PWD_TOO_LONG, current, request);
		}
		if (newPassword.equalsIgnoreCase(current.loginId())) {
			throw reject("equals_default", MSG_PWD_EQUALS_DEFAULT, current, request);
		}
		if (newPassword.equals(oldPassword)) {
			throw reject("same_as_old", MSG_PWD_SAME_AS_OLD, current, request);
		}
	}

	private static PasswordRuleException reject(String reason, String message, AuthUser current,
			HttpServletRequest request) {
		log.info("改密碼失敗 reason={} user={} srcIp={}", reason, current.userId(), ClientIp.of(request));
		return new PasswordRuleException(reason, message);
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
