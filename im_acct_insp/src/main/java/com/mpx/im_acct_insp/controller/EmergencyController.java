package com.mpx.im_acct_insp.controller;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: 新增：緊急存取 API（BACKLOG 第 46 項 S2）：POST /emergencies（送出）、GET /emergencies（列表）、
//           GET /emergencies/{id}（明細）；回報／處理／結案由 S4～S6 追加
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S4a：POST /emergencies/{id}/report（申請人回報）
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S4b-1：POST /emergencies/{id}/approve（主管補核准）、POST /emergencies/{id}/return（主管退回）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S6：POST /emergencies/{id}/close（異常結案）
// ============================================================

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mpx.im_acct_insp.common.Identity;
import com.mpx.im_acct_insp.model.Emg;
import com.mpx.im_acct_insp.model.EmgActionResult;
import com.mpx.im_acct_insp.model.EmgDetail;
import com.mpx.im_acct_insp.model.EmgIdResult;
import com.mpx.im_acct_insp.model.request.EmgCloseForm;
import com.mpx.im_acct_insp.model.request.EmgForm;
import com.mpx.im_acct_insp.model.request.EmgReportForm;
import com.mpx.im_acct_insp.model.request.ReasonForm;
import com.mpx.im_acct_insp.model.request.VersionForm;
import com.mpx.im_acct_insp.service.EmergencyService;

@RestController
@RequestMapping("/api/v1")
public class EmergencyController {

	private final EmergencyService emergencyService;

	public EmergencyController(EmergencyService emergencyService) {
		this.emergencyService = emergencyService;
	}

	@PostMapping("/emergencies")
	public EmgIdResult create(Identity id, @RequestBody(required = false) EmgForm form) {
		return emergencyService.create(id, form);
	}

	@GetMapping("/emergencies")
	public List<Emg> list(Identity id, @RequestParam(required = false) String keyword,
			@RequestParam(required = false) String stateCode, @RequestParam(required = false) String dateFrom,
			@RequestParam(required = false) String dateTo) {
		return emergencyService.list(id, keyword, stateCode, dateFrom, dateTo);
	}

	@GetMapping("/emergencies/{id}")
	public EmgDetail detail(Identity id, @PathVariable("id") String emgId) {
		return emergencyService.detail(id, emgId);
	}

	@PostMapping("/emergencies/{id}/report")
	public EmgActionResult report(Identity id, @PathVariable("id") String emgId,
			@RequestBody(required = false) EmgReportForm form) {
		return emergencyService.report(id, emgId, form);
	}

	@PostMapping("/emergencies/{id}/approve")
	public EmgActionResult approve(Identity id, @PathVariable("id") String emgId,
			@RequestBody(required = false) VersionForm form) {
		return emergencyService.approve(id, emgId, form);
	}

	@PostMapping("/emergencies/{id}/return")
	public EmgActionResult returnEmg(Identity id, @PathVariable("id") String emgId,
			@RequestBody(required = false) ReasonForm form) {
		return emergencyService.returnEmg(id, emgId, form);
	}

	@PostMapping("/emergencies/{id}/close")
	public EmgActionResult close(Identity id, @PathVariable("id") String emgId,
			@RequestBody(required = false) EmgCloseForm form) {
		return emergencyService.close(id, emgId, form);
	}
}
