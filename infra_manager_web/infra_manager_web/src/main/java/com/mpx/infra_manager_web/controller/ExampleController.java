package com.mpx.infra_manager_web.controller;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-04
// 變更說明: 新增：範例 Controller（規格 v4）；頁面只打本殼 jar 的 /<專案名>/api/v1/...，由 ApiForwarder 轉給後端 API
//           不解析 payload、不開 CrossOrigin；新增功能時照此寫法加一支 Controller（或在此加方法）
// ============================================================

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mpx.common.web.ApiForwarder;

@RestController
@RequestMapping("/api/v1/example")
public class ExampleController {

	private final ApiForwarder apiForwarder;

	public ExampleController(ApiForwarder apiForwarder) {
		this.apiForwarder = apiForwarder;
	}

	/** 查詢：轉發至後端 <backend.api.domain.path>/example/search */
	@PostMapping("/search")
	public ResponseEntity<Object> search(@RequestBody Object body) {
		return apiForwarder.post("/example/search", body);
	}

	/** 清單：轉發至後端 <backend.api.domain.path>/example/list */
	@GetMapping("/list")
	public ResponseEntity<Object> list() {
		return apiForwarder.get("/example/list");
	}
}
