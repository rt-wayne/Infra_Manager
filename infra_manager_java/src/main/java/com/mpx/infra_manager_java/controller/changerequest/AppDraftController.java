package com.mpx.infra_manager_java.controller.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：POST /api/apps 建立草稿（S6 回合二 b-1），回 201 {appId, rowVerNo}。
//           與唯讀的 AppController 分開，寫入端點（回合二 b-2 的 PUT）集中在這裡。
//           登入、密碼閘門與 CSRF 由 SecurityConfig 處理；欄位錯誤由 ApiExceptionHandler 回 400
//           2026-10-07 回合二 b-2：加 PUT /api/apps/{id} 編輯草稿，回 200 {appId, rowVerNo(新)}；
//           非申請人 403、找不到 404、非草稿或版本不符 409
//           2026-10-07 回合三：加 POST /api/apps/{id}/attachments 上傳草稿附件（multipart，part 名 file，一次一檔），
//           回 201，格式同檢視 API 的 attachments 元素；不是 multipart 回 415、缺 part 回 400
// ============================================================

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.mpx.infra_manager_java.model.auth.AuthUser;
import com.mpx.infra_manager_java.model.changerequest.AppDetail;
import com.mpx.infra_manager_java.model.changerequest.AppDraftRequest;
import com.mpx.infra_manager_java.service.changerequest.AppDraftService;
import com.mpx.infra_manager_java.service.changerequest.AttachmentUploadService;

@RestController
@RequestMapping("/api/apps")
public class AppDraftController {

	private final AppDraftService appDraftService;
	private final AttachmentUploadService attachmentUploadService;

	public AppDraftController(AppDraftService appDraftService, AttachmentUploadService attachmentUploadService) {
		this.appDraftService = appDraftService;
		this.attachmentUploadService = attachmentUploadService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Map<String, Object> create(@RequestBody AppDraftRequest request, Authentication authentication) {
		String appId = appDraftService.create(request, principal(authentication));
		return Map.of("appId", appId, "rowVerNo", 0);
	}

	@PutMapping("/{id}")
	public Map<String, Object> update(@PathVariable("id") String id, @RequestBody AppDraftRequest request,
			Authentication authentication) {
		long rowVerNo = appDraftService.update(id, request, principal(authentication));
		return Map.of("appId", id, "rowVerNo", rowVerNo);
	}

	@PostMapping(path = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public AppDetail.Attachment upload(@PathVariable("id") String id, @RequestPart("file") MultipartFile file,
			Authentication authentication) {
		return attachmentUploadService.upload(id, file, principal(authentication));
	}

	private static AuthUser principal(Authentication authentication) {
		if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser user)) {
			throw new AuthenticationCredentialsNotFoundException("尚未登入");
		}
		return user;
	}
}
