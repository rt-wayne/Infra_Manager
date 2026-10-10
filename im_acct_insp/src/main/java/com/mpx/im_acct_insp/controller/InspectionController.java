package com.mpx.im_acct_insp.controller;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：巡檢 API（spec §4.4）：/inspection-items、/inspections（新增、列表）、/inspections/{id}、
//           /inspections/{id}/items/{itemId}/fix（回傳更新後的巡檢明細）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（spec_im_acct_insp_v2.0.md §6.6）：身分改 Identity（Bearer）；fix 改回 {inspId, inspStateCode}
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
import com.mpx.im_acct_insp.model.Insp;
import com.mpx.im_acct_insp.model.InspDetail;
import com.mpx.im_acct_insp.model.InspFixResult;
import com.mpx.im_acct_insp.model.InspIdResult;
import com.mpx.im_acct_insp.model.InspItem;
import com.mpx.im_acct_insp.model.request.FixForm;
import com.mpx.im_acct_insp.model.request.InspForm;
import com.mpx.im_acct_insp.service.InspectionService;

@RestController
@RequestMapping("/api/v1")
public class InspectionController {

	private final InspectionService inspectionService;

	public InspectionController(InspectionService inspectionService) {
		this.inspectionService = inspectionService;
	}

	@GetMapping("/inspection-items")
	public List<InspItem> items(Identity id) {
		return inspectionService.items(id);
	}

	@PostMapping("/inspections")
	public InspIdResult create(Identity id, @RequestBody(required = false) InspForm form) {
		return inspectionService.create(id, form);
	}

	@GetMapping("/inspections")
	public List<Insp> list(Identity id, @RequestParam(required = false) String keyword,
			@RequestParam(required = false) String stateCode, @RequestParam(required = false) String dateFrom,
			@RequestParam(required = false) String dateTo) {
		return inspectionService.list(id, keyword, stateCode, dateFrom, dateTo);
	}

	@GetMapping("/inspections/{id}")
	public InspDetail detail(Identity id, @PathVariable("id") String inspId) {
		return inspectionService.detail(id, inspId);
	}

	@PostMapping("/inspections/{id}/items/{itemId}/fix")
	public InspFixResult fix(Identity id, @PathVariable("id") String inspId, @PathVariable("itemId") String itemId,
			@RequestBody(required = false) FixForm form) {
		return inspectionService.fix(id, inspId, itemId, form);
	}
}
