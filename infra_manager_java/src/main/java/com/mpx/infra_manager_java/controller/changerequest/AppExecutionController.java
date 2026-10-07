package com.mpx.infra_manager_java.controller.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：執行與治理審查端點（S10 R1）。PUT /api/apps/{id}/execution 填寫執行紀錄（本文見 ExecutionRequest），
//           成功回 200 {appId, rowVerNo(新), statusCode}——resultCode 有值時 statusCode 為 PENDING_REVIEW，暫存為 IN_EXECUTION；
//           不是 idc_admin 也不是申請人 403、找不到 404、狀態不是待執行／執行中或版本過期 409、格式或送審必填缺漏 400。
//           2026-10-07 S10 R2：加 POST /{id}/execution/reject（本文 {rowVerNo, memo}，回 {appId, rowVerNo}）與
//           POST /{id}/governance-review（本文 {rowVerNo, decision: PASS|RETURN, memo}，回 {appId, rowVerNo, statusCode}）
// ============================================================

import java.util.Map;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mpx.infra_manager_java.model.auth.AuthUser;
import com.mpx.infra_manager_java.model.changerequest.ExecRejectRequest;
import com.mpx.infra_manager_java.model.changerequest.ExecutionRequest;
import com.mpx.infra_manager_java.model.changerequest.GovernanceReviewRequest;
import com.mpx.infra_manager_java.service.changerequest.AppExecutionService;

@RestController
@RequestMapping("/api/apps")
public class AppExecutionController {

	private final AppExecutionService appExecutionService;

	public AppExecutionController(AppExecutionService appExecutionService) {
		this.appExecutionService = appExecutionService;
	}

	@PutMapping("/{id}/execution")
	public Map<String, Object> saveExecution(@PathVariable("id") String id, @RequestBody ExecutionRequest request,
			Authentication authentication) {
		AppExecutionService.SaveResult result = appExecutionService.save(id, request, principal(authentication));
		return Map.of("appId", id, "rowVerNo", result.rowVerNo(), "statusCode", result.statusCode());
	}

	@PostMapping("/{id}/execution/reject")
	public Map<String, Object> rejectExecution(@PathVariable("id") String id, @RequestBody ExecRejectRequest request,
			Authentication authentication) {
		long rowVerNo = appExecutionService.reject(id, request, principal(authentication));
		return Map.of("appId", id, "rowVerNo", rowVerNo);
	}

	@PostMapping("/{id}/governance-review")
	public Map<String, Object> review(@PathVariable("id") String id, @RequestBody GovernanceReviewRequest request,
			Authentication authentication) {
		AppExecutionService.SaveResult result = appExecutionService.review(id, request, principal(authentication));
		return Map.of("appId", id, "rowVerNo", result.rowVerNo(), "statusCode", result.statusCode());
	}

	private static AuthUser principal(Authentication authentication) {
		if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser user)) {
			throw new AuthenticationCredentialsNotFoundException("尚未登入");
		}
		return user;
	}
}
