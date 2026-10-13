package com.mpx.im_acct_insp_web.controller;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: 新增：緊急存取 API 轉送（BACKLOG 第 46 項 S2）：/emergencies（送出、列表）、/emergencies/{id}（明細）；
//           Authorization、Content-Type、查詢字串原樣轉送，不解析 payload
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S4a：/emergencies/{id}/report（申請人回報）轉送
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S4b-1：/emergencies/{id}/approve（主管補核准）、/emergencies/{id}/return（主管退回）轉送
// ============================================================

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 頁面打 /im_acct_insp_web/api/v1/emergencies...，原樣轉給後端同路徑。
 * 不解析 payload、不開 CrossOrigin。
 */
@RestController
@RequestMapping("/api/v1")
public class EmergencyController {

	private final BackendForwarder backend;

	public EmergencyController(BackendForwarder backend) {
		this.backend = backend;
	}

	/** 送出緊急存取：POST <backend>/emergencies，JSON {catgId, targetDescr, applyReason, expectHourQty} */
	@PostMapping("/emergencies")
	public ResponseEntity<byte[]> create(HttpServletRequest request) {
		return backend.post("/emergencies", request);
	}

	/** 緊急存取列表：GET <backend>/emergencies?keyword&stateCode&dateFrom&dateTo */
	@GetMapping("/emergencies")
	public ResponseEntity<byte[]> list(HttpServletRequest request) {
		return backend.get("/emergencies", request);
	}

	/** 緊急存取明細：GET <backend>/emergencies/{id} */
	@GetMapping("/emergencies/{id}")
	public ResponseEntity<byte[]> detail(@PathVariable("id") String id, HttpServletRequest request) {
		return backend.get("/emergencies/" + BackendForwarder.seg(id), request);
	}

	/** 申請人回報：POST <backend>/emergencies/{id}/report，JSON {reportTypeCode, reportDescr, versionNo, affectAccts} */
	@PostMapping("/emergencies/{id}/report")
	public ResponseEntity<byte[]> report(@PathVariable("id") String id, HttpServletRequest request) {
		return backend.post("/emergencies/" + BackendForwarder.seg(id) + "/report", request);
	}

	/** 主管補核准：POST <backend>/emergencies/{id}/approve，JSON {versionNo} */
	@PostMapping("/emergencies/{id}/approve")
	public ResponseEntity<byte[]> approve(@PathVariable("id") String id, HttpServletRequest request) {
		return backend.post("/emergencies/" + BackendForwarder.seg(id) + "/approve", request);
	}

	/** 主管退回：POST <backend>/emergencies/{id}/return，JSON {versionNo, reason} */
	@PostMapping("/emergencies/{id}/return")
	public ResponseEntity<byte[]> returnEmg(@PathVariable("id") String id, HttpServletRequest request) {
		return backend.post("/emergencies/" + BackendForwarder.seg(id) + "/return", request);
	}
}
