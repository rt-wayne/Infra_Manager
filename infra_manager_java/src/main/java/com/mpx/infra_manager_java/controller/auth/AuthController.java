package com.mpx.infra_manager_java.controller.auth;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：POST /api/auth/login、POST /api/auth/logout、GET /api/auth/me（S2 回合一）。
//           login 需帶 X-IM-XSRF（前端先 GET /me 取 IM_XSRF cookie）；失敗一律 401「帳號或密碼錯誤」；
//           本文欄位缺漏或超長 400；me 永遠 200（未登入 loggedIn:false）；logout 永遠 204。
//           2026-10-06 S2 回合二：加 POST /api/auth/password（須已登入；安全層對 /api/auth/** 只要求 authenticated，
//           所以預設密碼者也能打；規則不符 400 帶明確訊息，舊密碼錯也是 400 而不是 401，免得前端誤判成登入過期；
//           成功回 200 MeResponse，mustChangePassword 變 false）
// ============================================================

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mpx.infra_manager_java.config.SecurityConfig;
import com.mpx.infra_manager_java.model.auth.AuthUser;
import com.mpx.infra_manager_java.model.auth.ChangePasswordRequest;
import com.mpx.infra_manager_java.model.auth.LoginRequest;
import com.mpx.infra_manager_java.model.auth.MeResponse;
import com.mpx.infra_manager_java.service.auth.AuthService;
import com.mpx.infra_manager_java.service.auth.PasswordRuleException;
import com.mpx.infra_manager_java.web.ApiExceptionHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/login")
	public ResponseEntity<Object> login(@RequestBody LoginRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		if (!body.isValid()) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiExceptionHandler.message("請求格式錯誤"));
		}
		Optional<AuthUser> user = authService.login(body.loginId(), body.password(), request, response);
		if (user.isEmpty()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiExceptionHandler.message("帳號或密碼錯誤"));
		}
		return ResponseEntity.ok(MeResponse.of(user.get()));
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(HttpServletRequest request, HttpServletResponse response) {
		authService.logout(request, response);
	}

	@PostMapping("/password")
	public ResponseEntity<Object> changePassword(@RequestBody ChangePasswordRequest body, Authentication authentication,
			HttpServletRequest request, HttpServletResponse response) {
		if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser current)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(ApiExceptionHandler.message(SecurityConfig.MSG_NOT_LOGGED_IN));
		}
		if (!body.isValid()) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiExceptionHandler.message("請求格式錯誤"));
		}
		try {
			AuthUser user = authService.changePassword(current, body.oldPassword(), body.newPassword(), request,
					response);
			return ResponseEntity.ok(MeResponse.of(user));
		} catch (PasswordRuleException e) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiExceptionHandler.message(e.getMessage()));
		}
	}

	@GetMapping("/me")
	public MeResponse me(Authentication authentication) {
		return authService.me(authentication);
	}
}
