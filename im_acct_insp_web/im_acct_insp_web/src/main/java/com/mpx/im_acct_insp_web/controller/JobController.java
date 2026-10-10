package com.mpx.im_acct_insp_web.controller;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：排程手動觸發轉送（開發文件 spec_im_acct_insp_v2.0.md 第 6.7、7.2 節）：POST /jobs/{code}/run
//           查詢字串（baseDate）原樣接上；code 是否合法（J01／J02，其他 404）由後端判斷
// ============================================================

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 頁面打 /im_acct_insp_web/api/v1/jobs/{code}/run，原樣轉給後端同路徑。
 * 不解析 payload、不開 CrossOrigin。
 */
@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

	private final BackendForwarder backend;

	public JobController(BackendForwarder backend) {
		this.backend = backend;
	}

	/** 手動執行排程：POST <backend>/jobs/{code}/run?baseDate */
	@PostMapping("/{code}/run")
	public ResponseEntity<byte[]> run(@PathVariable("code") String code, HttpServletRequest request) {
		return backend.post("/jobs/" + BackendForwarder.seg(code) + "/run", request);
	}
}
