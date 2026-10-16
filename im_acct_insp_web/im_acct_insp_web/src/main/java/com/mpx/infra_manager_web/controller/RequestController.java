package com.mpx.infra_manager_web.controller;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：申請單 API 轉送（開發文件 spec_im_acct_insp.md 第 4.3、5 節）：
//           /requests（送出、我的申請單）、/requests/{id}（明細）、
//           /requests/{id}/approve、reject、resubmit、exec-done、verify-ok、verify-reject
//           POST 本文以 byte[] 原樣轉送；approve／verify-ok 本文可空
//           SA v0.5 改版（2026-10-07，Claude Opus 5.5，spec_im_acct_insp_v2.0.md 第 6.5、7.2 節）：路徑不變；
//           POST /requests、/resubmit、/exec-done 改為 multipart（form＋docFile／proofFile），其餘 POST 為 JSON；
//           殼 jar 一律不解析，本文與 Content-Type（含 boundary）由 BackendForwarder 原樣轉送、超過 11 MB 回 413；
//           改傳 HttpServletRequest（Authorization 原樣轉送），不再接 X-User-Id 與 @RequestBody
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 1：說明註解的網址前綴改 /infra_manager_web（專案改名 infra_manager），程式未動
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp_web 改名為 com.mpx.infra_manager_web（專案改名 infra_manager），只改 package／import／全名引用
// ============================================================

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 頁面打 /infra_manager_web/api/v1/requests/...，原樣轉給後端同路徑。
 * 不解析 payload（multipart 也不解析）、不開 CrossOrigin。
 */
@RestController
@RequestMapping("/api/v1/requests")
public class RequestController {

	private final BackendForwarder backend;

	public RequestController(BackendForwarder backend) {
		this.backend = backend;
	}

	/** 送出申請：POST <backend>/requests（multipart：form＋docFile 選填） */
	@PostMapping
	public ResponseEntity<byte[]> submit(HttpServletRequest request) {
		return backend.post("/requests", request);
	}

	/** 我的申請單：GET <backend>/requests?stateCode */
	@GetMapping
	public ResponseEntity<byte[]> list(HttpServletRequest request) {
		return backend.get("/requests", request);
	}

	/** 申請單明細：GET <backend>/requests/{id} */
	@GetMapping("/{id}")
	public ResponseEntity<byte[]> detail(@PathVariable("id") String id, HttpServletRequest request) {
		return backend.get("/requests/" + BackendForwarder.seg(id), request);
	}

	/** 關卡核准：POST <backend>/requests/{id}/approve，JSON {versionNo} */
	@PostMapping("/{id}/approve")
	public ResponseEntity<byte[]> approve(@PathVariable("id") String id, HttpServletRequest request) {
		return action(id, "approve", request);
	}

	/** 退件：POST <backend>/requests/{id}/reject，JSON {versionNo, reason} */
	@PostMapping("/{id}/reject")
	public ResponseEntity<byte[]> reject(@PathVariable("id") String id, HttpServletRequest request) {
		return action(id, "reject", request);
	}

	/** 退件後重新送出：POST <backend>/requests/{id}/resubmit（multipart：form＋docFile 選填） */
	@PostMapping("/{id}/resubmit")
	public ResponseEntity<byte[]> resubmit(@PathVariable("id") String id, HttpServletRequest request) {
		return action(id, "resubmit", request);
	}

	/** 執行人回報執行完畢：POST <backend>/requests/{id}/exec-done（multipart：form＋proofFile 必填） */
	@PostMapping("/{id}/exec-done")
	public ResponseEntity<byte[]> execDone(@PathVariable("id") String id, HttpServletRequest request) {
		return action(id, "exec-done", request);
	}

	/** 複驗通過：POST <backend>/requests/{id}/verify-ok，JSON {versionNo} */
	@PostMapping("/{id}/verify-ok")
	public ResponseEntity<byte[]> verifyOk(@PathVariable("id") String id, HttpServletRequest request) {
		return action(id, "verify-ok", request);
	}

	/** 複驗退回：POST <backend>/requests/{id}/verify-reject，JSON {versionNo, reason} */
	@PostMapping("/{id}/verify-reject")
	public ResponseEntity<byte[]> verifyReject(@PathVariable("id") String id, HttpServletRequest request) {
		return action(id, "verify-reject", request);
	}

	private ResponseEntity<byte[]> action(String id, String action, HttpServletRequest request) {
		return backend.post("/requests/" + BackendForwarder.seg(id) + "/" + action, request);
	}
}
