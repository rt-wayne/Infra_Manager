package com.mpx.infra_manager_java.controller;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-05
// 變更說明: 新增：GET /api/health（S1）。後端 API 路徑一律以 /api 為前綴，版本號在殼 jar 那一層（/api/v1）
// ============================================================

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mpx.infra_manager_java.model.HealthStatus;
import com.mpx.infra_manager_java.service.HealthService;

@RestController
@RequestMapping("/api")
public class HealthController {

	private final HealthService healthService;

	public HealthController(HealthService healthService) {
		this.healthService = healthService;
	}

	@GetMapping("/health")
	public HealthStatus health() {
		return healthService.check();
	}
}
