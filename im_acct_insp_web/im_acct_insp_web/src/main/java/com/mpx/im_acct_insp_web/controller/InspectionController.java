package com.mpx.im_acct_insp_web.controller;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：機房巡檢 API 轉送（開發文件 spec_im_acct_insp.md 第 4.4、5 節）：
//           /inspection-items、/inspections（新增、列表）、/inspections/{id}（明細）、
//           /inspections/{id}/items/{itemId}/fix（標記已處理）
//           SA v0.5 改版（2026-10-07，Claude Opus 5.5，spec_im_acct_insp_v2.0.md 第 6.6、7.2 節）：路徑不變；
//           改傳 HttpServletRequest 給 BackendForwarder（Authorization、Content-Type、查詢字串原樣轉送），
//           不再接 X-User-Id 與 @RequestBody
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 1：說明註解的網址前綴改 /infra_manager_web（專案改名 infra_manager），程式未動
// ============================================================

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 頁面打 /infra_manager_web/api/v1/inspection-items、/inspections/...，原樣轉給後端同路徑。
 * 不解析 payload、不開 CrossOrigin。
 */
@RestController
@RequestMapping("/api/v1")
public class InspectionController {

	private final BackendForwarder backend;

	public InspectionController(BackendForwarder backend) {
		this.backend = backend;
	}

	/** 啟用的巡檢項目：GET <backend>/inspection-items */
	@GetMapping("/inspection-items")
	public ResponseEntity<byte[]> items(HttpServletRequest request) {
		return backend.get("/inspection-items", request);
	}

	/** 新增巡檢：POST <backend>/inspections，JSON {inspDate, results[]} */
	@PostMapping("/inspections")
	public ResponseEntity<byte[]> create(HttpServletRequest request) {
		return backend.post("/inspections", request);
	}

	/** 巡檢紀錄：GET <backend>/inspections?keyword&stateCode&dateFrom&dateTo */
	@GetMapping("/inspections")
	public ResponseEntity<byte[]> list(HttpServletRequest request) {
		return backend.get("/inspections", request);
	}

	/** 巡檢明細：GET <backend>/inspections/{id} */
	@GetMapping("/inspections/{id}")
	public ResponseEntity<byte[]> detail(@PathVariable("id") String id, HttpServletRequest request) {
		return backend.get("/inspections/" + BackendForwarder.seg(id), request);
	}

	/** 標記異常已處理：POST <backend>/inspections/{id}/items/{itemId}/fix，JSON {fixDescr} */
	@PostMapping("/inspections/{id}/items/{itemId}/fix")
	public ResponseEntity<byte[]> fix(@PathVariable("id") String id, @PathVariable("itemId") String itemId,
			HttpServletRequest request) {
		return backend.post("/inspections/" + BackendForwarder.seg(id) + "/items/" + BackendForwarder.seg(itemId)
				+ "/fix", request);
	}
}
