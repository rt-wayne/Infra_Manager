package com.mpx.im_acct_insp.controller;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：POST /api/v1/jobs/{code}/run 手動觸發排程（spec v2.0 §4.11、§6.7）；code＝J01／J02，query baseDate 選填
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S7：code 加 J03（緊急存取逾期通知；baseDate 為時間點 yyyy-MM-dd HH:mm:ss，判斷在 JobService）
// ============================================================

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mpx.im_acct_insp.common.Identity;
import com.mpx.im_acct_insp.model.JobRunResult;
import com.mpx.im_acct_insp.service.JobService;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

	private final JobService jobService;

	public JobController(JobService jobService) {
		this.jobService = jobService;
	}

	@PostMapping("/{code}/run")
	public JobRunResult run(Identity id, @PathVariable("code") String code, @RequestParam(required = false) String baseDate) {
		return jobService.runManual(id, code, baseDate);
	}
}
