package com.mpx.infra_manager_web.controller;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：首頁與共用 API 轉送（開發文件 spec_im_acct_insp.md 第 4.1、5 節）：
//           /codes、/home/stats、/home/expiring、/todos
//           SA v0.5 改版（2026-10-07，Claude Opus 5.5，spec_im_acct_insp_v2.0.md 第 7.2 節）：路徑不變；
//           改傳 HttpServletRequest 給 BackendForwarder（Authorization、查詢字串原樣轉送），不再接 X-User-Id
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 頁面打 /infra_manager_web/api/v1/codes、/home/...、/todos，原樣轉給後端同路徑。
 * 不解析 payload、不開 CrossOrigin。
 */
@RestController
@RequestMapping("/api/v1")
public class HomeController {

	private final BackendForwarder backend;

	public HomeController(BackendForwarder backend) {
		this.backend = backend;
	}

	/** 代碼表：GET <backend>/codes */
	@GetMapping("/codes")
	public ResponseEntity<byte[]> codes(HttpServletRequest request) {
		return backend.get("/codes", request);
	}

	/** 首頁卡片：GET <backend>/home/stats */
	@GetMapping("/home/stats")
	public ResponseEntity<byte[]> stats(HttpServletRequest request) {
		return backend.get("/home/stats", request);
	}

	/** 鈴鐺到期清單：GET <backend>/home/expiring */
	@GetMapping("/home/expiring")
	public ResponseEntity<byte[]> expiring(HttpServletRequest request) {
		return backend.get("/home/expiring", request);
	}

	/** 待辦簽核：GET <backend>/todos */
	@GetMapping("/todos")
	public ResponseEntity<byte[]> todos(HttpServletRequest request) {
		return backend.get("/todos", request);
	}
}
