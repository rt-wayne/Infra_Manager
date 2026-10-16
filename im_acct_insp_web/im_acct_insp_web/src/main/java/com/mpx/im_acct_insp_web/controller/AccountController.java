package com.mpx.im_acct_insp_web.controller;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：帳號 API 轉送（開發文件 spec_im_acct_insp.md 第 4.2、5 節）：
//           /accounts、/accounts/mine、/accounts/lookup、/accounts/{id}、/accounts/{id}/history
//           查詢字串（keyword、catgId、stateCode、assetClassId）原樣接上，不解析
//           變更單 #1：S1 新增 /accounts/lookup/{id}（異動表單帶入用）轉送；Spring 依路徑樣式比對、與方法宣告順序無關：
//           /accounts/{id} 只對應單段，不會吃到 /lookup/{id}；唯一重疊是單號字面為 history（單號為 AC 開頭，不會發生）
//           SA v0.5 改版（2026-10-07，Claude Opus 5.5，spec_im_acct_insp_v2.0.md 第 6.4、7.2 節）：路徑不變；
//           所有端點的查詢字串一律原樣轉送（/accounts/lookup 改 acctName、/accounts/lookup/{id} 帶 acctName、
//           /accounts 加 expiring，殼 jar 不需知道參數名）；改傳 HttpServletRequest 給 BackendForwarder
//           （Authorization 原樣轉送），不再接 X-User-Id
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 1：說明註解的網址前綴改 /infra_manager_web（專案改名 infra_manager），程式未動
// ============================================================

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 頁面打 /infra_manager_web/api/v1/accounts/...，原樣轉給後端同路徑（查詢字串不解析、原樣接上）。
 * 不解析 payload、不開 CrossOrigin。
 */
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

	private final BackendForwarder backend;

	public AccountController(BackendForwarder backend) {
		this.backend = backend;
	}

	/** 帳號列表：GET <backend>/accounts?keyword&catgId&stateCode&assetClassId&expiring */
	@GetMapping
	public ResponseEntity<byte[]> list(HttpServletRequest request) {
		return backend.get("/accounts", request);
	}

	/** 我曾申請新增的帳號：GET <backend>/accounts/mine */
	@GetMapping("/mine")
	public ResponseEntity<byte[]> mine(HttpServletRequest request) {
		return backend.get("/accounts/mine", request);
	}

	/** 對既有帳號提出異動（精確查詢）：GET <backend>/accounts/lookup?acctName */
	@GetMapping("/lookup")
	public ResponseEntity<byte[]> lookup(HttpServletRequest request) {
		return backend.get("/accounts/lookup", request);
	}

	/** 異動表單帶入：GET <backend>/accounts/lookup/{id}?acctName（範圍由後端驗） */
	@GetMapping("/lookup/{id}")
	public ResponseEntity<byte[]> lookupDetail(@PathVariable("id") String id, HttpServletRequest request) {
		return backend.get("/accounts/lookup/" + BackendForwarder.seg(id), request);
	}

	/** 帳號明細：GET <backend>/accounts/{id} */
	@GetMapping("/{id}")
	public ResponseEntity<byte[]> detail(@PathVariable("id") String id, HttpServletRequest request) {
		return backend.get("/accounts/" + BackendForwarder.seg(id), request);
	}

	/** 帳號歷史：GET <backend>/accounts/{id}/history */
	@GetMapping("/{id}/history")
	public ResponseEntity<byte[]> history(@PathVariable("id") String id, HttpServletRequest request) {
		return backend.get("/accounts/" + BackendForwarder.seg(id) + "/history", request);
	}
}
