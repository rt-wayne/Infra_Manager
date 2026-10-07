package com.mpx.infra_manager_java.controller.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：POST /api/apps 建立草稿（S6 回合二 b-1），回 201 {appId, rowVerNo}。
//           與唯讀的 AppController 分開，寫入端點（回合二 b-2 的 PUT）集中在這裡。
//           登入、密碼閘門與 CSRF 由 SecurityConfig 處理；欄位錯誤由 ApiExceptionHandler 回 400
// ============================================================

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mpx.infra_manager_java.model.auth.AuthUser;
import com.mpx.infra_manager_java.model.changerequest.AppDraftRequest;
import com.mpx.infra_manager_java.service.changerequest.AppDraftService;

@RestController
@RequestMapping("/api/apps")
public class AppDraftController {

	private final AppDraftService appDraftService;

	public AppDraftController(AppDraftService appDraftService) {
		this.appDraftService = appDraftService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Map<String, Object> create(@RequestBody AppDraftRequest request, Authentication authentication) {
		String appId = appDraftService.create(request, principal(authentication));
		return Map.of("appId", appId, "rowVerNo", 0);
	}

	private static AuthUser principal(Authentication authentication) {
		if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser user)) {
			throw new AuthenticationCredentialsNotFoundException("尚未登入");
		}
		return user;
	}
}
