package com.mpx.infra_manager_web.controller;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-05
// 變更說明: 新增：GET /api/v1/health → 轉發後端 <backend.api.domain.path>/health（S1）；不解析 payload、不開 CrossOrigin
// ============================================================

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mpx.common.web.ApiForwarder;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

	private final ApiForwarder apiForwarder;

	public HealthController(ApiForwarder apiForwarder) {
		this.apiForwarder = apiForwarder;
	}

	@GetMapping("/health")
	public ResponseEntity<Object> health() {
		return apiForwarder.get("/health");
	}
}
