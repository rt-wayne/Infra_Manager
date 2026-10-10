package com.mpx.im_acct_insp.controller;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：GET /api/v1/attachments/{id} 附件下載（spec v2.0 §4.10、§6.5）；須可看該申請單，回原始位元組
// ============================================================

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mpx.im_acct_insp.common.Identity;
import com.mpx.im_acct_insp.service.AttachmentService;

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
