package com.mpx.infra_manager.controller;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：身分 API（spec v2.0 §6.2；取代舊 UserController）：
//           POST /auth/login（唯一不需 token 的 API）、POST /auth/logout、GET /me、GET /users、
//           POST /auth/impersonate、POST /auth/impersonate/end；權限判斷都在 AuthService／PermissionService
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// ============================================================

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mpx.infra_manager.common.Identity;
import com.mpx.infra_manager.model.LoginResult;
import com.mpx.infra_manager.model.MeInfo;
import com.mpx.infra_manager.model.UserInfo;
import com.mpx.infra_manager.model.request.ImpersonateForm;
import com.mpx.infra_manager.model.request.LoginForm;
import com.mpx.infra_manager.service.AuthService;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/auth/login")
	public LoginResult login(@RequestBody(required = false) LoginForm form) {
		return authService.login(form);
	}

	@PostMapping("/auth/logout")
	public ResponseEntity<Void> logout(Identity id) {
		authService.logout(id);
		return ResponseEntity.ok().build();
	}

	@GetMapping("/me")
	public MeInfo me(Identity id) {
		return authService.me(id);
	}

	@GetMapping("/users")
	public List<UserInfo> users(Identity id) {
		return authService.users(id);
	}

	@PostMapping("/auth/impersonate")
	public MeInfo impersonate(Identity id, @RequestBody(required = false) ImpersonateForm form) {
		return authService.impersonate(id, form);
	}

	@PostMapping("/auth/impersonate/end")
	public MeInfo endImpersonate(Identity id) {
		return authService.endImpersonate(id);
	}
}
