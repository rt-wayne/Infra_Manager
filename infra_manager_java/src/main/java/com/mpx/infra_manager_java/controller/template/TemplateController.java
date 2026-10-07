package com.mpx.infra_manager_java.controller.template;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：範本 CRUD（S5 R1）。GET 列表／單筆、POST 新增（201 {tmplId}）、PUT 修改（200 {tmplId}）、
//           DELETE 軟刪除（204）。登入、密碼閘門與 CSRF 由 SecurityConfig 處理；
//           非建立者也非 admin 修改或刪除 → 403、找不到 → 404、欄位錯誤 → 400（ApiExceptionHandler）
// ============================================================

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mpx.infra_manager_java.model.auth.AuthUser;
import com.mpx.infra_manager_java.model.template.TemplateDetail;
import com.mpx.infra_manager_java.model.template.TemplateListItem;
import com.mpx.infra_manager_java.model.template.TemplateRequest;
import com.mpx.infra_manager_java.service.template.TemplateService;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {

	private final TemplateService templateService;

	public TemplateController(TemplateService templateService) {
		this.templateService = templateService;
	}

	@GetMapping
	public List<TemplateListItem> list(Authentication authentication) {
		return templateService.list(principal(authentication));
	}

	@GetMapping("/{id}")
	public TemplateDetail get(@PathVariable("id") String id, Authentication authentication) {
		return templateService.get(id, principal(authentication));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Map<String, Object> create(@RequestBody TemplateRequest request, Authentication authentication) {
		return Map.of("tmplId", templateService.create(request, principal(authentication)));
	}

	@PutMapping("/{id}")
	public Map<String, Object> update(@PathVariable("id") String id, @RequestBody TemplateRequest request,
			Authentication authentication) {
		templateService.update(id, request, principal(authentication));
		return Map.of("tmplId", id);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable("id") String id, Authentication authentication) {
		templateService.delete(id, principal(authentication));
	}

	private static AuthUser principal(Authentication authentication) {
		if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser user)) {
			throw new AuthenticationCredentialsNotFoundException("尚未登入");
		}
		return user;
	}
}
