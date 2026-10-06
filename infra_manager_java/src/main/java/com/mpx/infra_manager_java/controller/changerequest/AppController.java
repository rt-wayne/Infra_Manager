package com.mpx.infra_manager_java.controller.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：申請單唯讀 API（S4）。GET /api/apps 列表（篩選 status／priority／source／mine／q／from／to／page）、
//           GET /api/apps/{id} 檢視、GET /api/apps/{id}/attachments/{attachId} 下載。三者皆須登入（S4 裁示 ①A）；
//           登入與密碼閘門由 SecurityConfig 處理，這裡只從 Authentication 取 AuthUser。
//           日期參數格式 yyyy-MM-dd；格式錯誤由 ApiExceptionHandler 回 400「請求格式錯誤」
// ============================================================

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mpx.infra_manager_java.model.auth.AuthUser;
import com.mpx.infra_manager_java.model.changerequest.AppDetail;
import com.mpx.infra_manager_java.model.changerequest.AppListQuery;
import com.mpx.infra_manager_java.model.changerequest.AppListResponse;
import com.mpx.infra_manager_java.model.changerequest.AttachmentFile;
import com.mpx.infra_manager_java.service.changerequest.AppQueryService;
import com.mpx.infra_manager_java.service.changerequest.AttachmentService;

@RestController
@RequestMapping("/api/apps")
public class AppController {

	private final AppQueryService appQueryService;
	private final AttachmentService attachmentService;

	public AppController(AppQueryService appQueryService, AttachmentService attachmentService) {
		this.appQueryService = appQueryService;
		this.attachmentService = attachmentService;
	}

	@GetMapping
	public AppListResponse list(@RequestParam(required = false) String status,
			@RequestParam(required = false) String priority, @RequestParam(required = false) String source,
			@RequestParam(required = false) Boolean mine, @RequestParam(required = false) String q,
			@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to,
			@RequestParam(defaultValue = "1") int page, Authentication authentication) {
		AppListQuery query = new AppListQuery(status, priority, source, Boolean.TRUE.equals(mine), q, from, to, page);
		return appQueryService.list(query, principal(authentication));
	}

	@GetMapping("/{id}")
	public AppDetail detail(@PathVariable String id, Authentication authentication) {
		return appQueryService.detail(id, principal(authentication));
	}

	@GetMapping("/{id}/attachments/{attachId}")
	public ResponseEntity<Resource> download(@PathVariable String id, @PathVariable long attachId,
			Authentication authentication) {
		principal(authentication);
		AttachmentFile file = attachmentService.open(id, attachId);
		MediaType mediaType;
		try {
			mediaType = MediaType.parseMediaType(file.mimeType());
		} catch (RuntimeException e) {
			mediaType = MediaType.APPLICATION_OCTET_STREAM;
		}
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
				.header("X-Content-Type-Options", "nosniff")
				.contentType(mediaType)
				.contentLength(file.length())
				.body(file.resource());
	}

	private static AuthUser principal(Authentication authentication) {
		if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser user)) {
			throw new AuthenticationCredentialsNotFoundException("尚未登入");
		}
		return user;
	}
}
