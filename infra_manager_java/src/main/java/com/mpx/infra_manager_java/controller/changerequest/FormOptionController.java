package com.mpx.infra_manager_java.controller.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-06
// 變更說明: 新增：GET /api/form-options（S6 回合二 a）。須登入且通過密碼閘門（由 SecurityConfig 的 /api/** 規則處理）
// ============================================================

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mpx.infra_manager_java.model.changerequest.FormOptionsResponse;
import com.mpx.infra_manager_java.service.changerequest.FormOptionService;

@RestController
public class FormOptionController {

	private final FormOptionService formOptionService;

	public FormOptionController(FormOptionService formOptionService) {
		this.formOptionService = formOptionService;
	}

	@GetMapping("/api/form-options")
	public FormOptionsResponse formOptions() {
		return formOptionService.load();
	}
}
