package com.mpx.im_acct_insp_web.controller;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：身分 API 轉送（開發文件 spec_im_acct_insp_v2.0.md 第 6.2、7.2 節），取代舊 UserController：
//           POST /auth/login、/auth/logout、/auth/impersonate、/auth/impersonate/end；GET /me、/users
//           Authorization 由 BackendForwarder 原樣轉送；殼 jar 不驗 token、不解析帳密本文
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 1：說明註解的網址前綴改 /infra_manager_web（專案改名 infra_manager），程式未動
// ============================================================

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 頁面打 /infra_manager_web/api/v1/auth/...、/me、/users，原樣轉給後端同路徑。
 * 不解析 payload、不開 CrossOrigin（頁面與殼 jar 同源）。
 */
@RestController
@RequestMapping("/api/v1")
public class AuthController {

	private final BackendForwarder backend;

	public AuthController(BackendForwarder backend) {
		this.backend = backend;
	}

	/** 登入：POST <backend>/auth/login，本文 {userId, password}（不記 log） */
	@PostMapping("/auth/login")
	public ResponseEntity<byte[]> login(HttpServletRequest request) {
		return backend.post("/auth/login", request);
	}

	/** 登出：POST <backend>/auth/logout */
	@PostMapping("/auth/logout")
	public ResponseEntity<byte[]> logout(HttpServletRequest request) {
		return backend.post("/auth/logout", request);
	}

	/** 切換身分：POST <backend>/auth/impersonate，本文 {targetUserId} */
	@PostMapping("/auth/impersonate")
	public ResponseEntity<byte[]> impersonate(HttpServletRequest request) {
		return backend.post("/auth/impersonate", request);
	}

	/** 切回：POST <backend>/auth/impersonate/end */
	@PostMapping("/auth/impersonate/end")
	public ResponseEntity<byte[]> impersonateEnd(HttpServletRequest request) {
		return backend.post("/auth/impersonate/end", request);
	}

	/** 目前身分：GET <backend>/me */
	@GetMapping("/me")
	public ResponseEntity<byte[]> me(HttpServletRequest request) {
		return backend.get("/me", request);
	}

	/** 身分切換名單：GET <backend>/users */
	@GetMapping("/users")
	public ResponseEntity<byte[]> users(HttpServletRequest request) {
		return backend.get("/users", request);
	}
}
