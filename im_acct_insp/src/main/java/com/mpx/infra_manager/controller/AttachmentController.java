package com.mpx.infra_manager.controller;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：GET /api/v1/attachments/{id} 附件下載（spec v2.0 §4.10、§6.5）；須可看該申請單，回原始位元組
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// ============================================================

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mpx.infra_manager.common.Identity;
import com.mpx.infra_manager.service.AttachmentService;

@RestController
@RequestMapping("/api/v1/attachments")
public class AttachmentController {

	private final AttachmentService attachmentService;

	public AttachmentController(AttachmentService attachmentService) {
		this.attachmentService = attachmentService;
	}

	@GetMapping("/{id}")
	public ResponseEntity<byte[]> download(Identity id, @PathVariable("id") long attachId) {
		return attachmentService.download(id, attachId);
	}
}
