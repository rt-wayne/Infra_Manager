package com.mpx.infra_manager_web.controller;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：附件下載轉送（開發文件 spec_im_acct_insp_v2.0.md 第 4.10、6.5、7.2 節）：GET /attachments/{id}
//           二進位本文 byte[] 原樣回傳，Content-Type、Content-Disposition 照抄；長度類 header 由 BackendForwarder 去掉
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 1：說明註解的網址前綴改 /infra_manager_web（專案改名 infra_manager），程式未動
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp_web 改名為 com.mpx.infra_manager_web（專案改名 infra_manager），只改 package／import／全名引用
// ============================================================

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 頁面打 /infra_manager_web/api/v1/attachments/{id}，原樣轉給後端同路徑；驗權（可看申請單）由後端做。
 * 不解析 payload、不開 CrossOrigin。
 */
@RestController
@RequestMapping("/api/v1/attachments")
public class AttachmentController {

	private final BackendForwarder backend;

	public AttachmentController(BackendForwarder backend) {
		this.backend = backend;
	}

	/** 附件下載：GET <backend>/attachments/{id} */
	@GetMapping("/{id}")
	public ResponseEntity<byte[]> download(@PathVariable("id") String id, HttpServletRequest request) {
		return backend.get("/attachments/" + BackendForwarder.seg(id), request);
	}
}
