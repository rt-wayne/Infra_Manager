package com.mpx.im_acct_insp.controller;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：GET /api/v1/codes（代碼表，不需身分）、/home/stats、/home/expiring、/todos（spec §4.1）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（spec_im_acct_insp_v2.0.md §6.1、§6.3）：身分改 Identity（Bearer）；/codes 也需登入
// ============================================================

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mpx.im_acct_insp.common.Identity;
import com.mpx.im_acct_insp.model.Codes;
import com.mpx.im_acct_insp.model.ExpiringAcct;
import com.mpx.im_acct_insp.model.HomeStats;
import com.mpx.im_acct_insp.model.TodoItem;
import com.mpx.im_acct_insp.service.CodeService;
import com.mpx.im_acct_insp.service.HomeService;

@RestController
@RequestMapping("/api/v1")
public class HomeController {

	private final CodeService codeService;
	private final HomeService homeService;

	public HomeController(CodeService codeService, HomeService homeService) {
		this.codeService = codeService;
		this.homeService = homeService;
	}

	@GetMapping("/codes")
	public Codes codes(Identity id) {
		return codeService.getCodes();
	}

	@GetMapping("/home/stats")
	public HomeStats stats(Identity id) {
		return homeService.stats(id);
	}

	@GetMapping("/home/expiring")
	public List<ExpiringAcct> expiring(Identity id) {
		return homeService.expiring(id);
	}

	@GetMapping("/todos")
	public List<TodoItem> todos(Identity id) {
		return homeService.todos(id);
	}
}
