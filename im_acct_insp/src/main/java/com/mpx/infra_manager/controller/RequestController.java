package com.mpx.infra_manager.controller;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：申請單 API（spec §4.3）：送出、我的申請單、明細、approve／reject／resubmit／exec-done／verify-ok／verify-reject
//           approve、verify-ok 不需 body（有送也忽略）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（spec_im_acct_insp_v2.0.md §6.1、§6.5）：身分改 Identity（Bearer）；
//           送出、重新送出、回報執行完畢改 multipart（part form＝JSON，檔案 part docFile／proofFile）；
//           approve、verify-ok 改帶 body {versionNo}；GET /requests 回 ReqListItem
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// ============================================================

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.mpx.infra_manager.common.Identity;
import com.mpx.infra_manager.model.AcctReqDetail;
import com.mpx.infra_manager.model.ReqActionResult;
import com.mpx.infra_manager.model.ReqIdResult;
import com.mpx.infra_manager.model.ReqListItem;
import com.mpx.infra_manager.model.request.AcctReqForm;
import com.mpx.infra_manager.model.request.ExecDoneForm;
import com.mpx.infra_manager.model.request.ReasonForm;
import com.mpx.infra_manager.model.request.VersionForm;
import com.mpx.infra_manager.service.RequestService;

@RestController
@RequestMapping("/api/v1/requests")
public class RequestController {

	private final RequestService requestService;

	public RequestController(RequestService requestService) {
		this.requestService = requestService;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ReqIdResult submit(Identity id, @RequestPart("form") AcctReqForm form,
			@RequestPart(value = "docFile", required = false) MultipartFile docFile) {
		return requestService.submit(id, form, docFile);
	}

	@GetMapping
	public List<ReqListItem> listMine(Identity id, @RequestParam(required = false) String stateCode) {
		return requestService.listMine(id, stateCode);
	}

	@GetMapping("/{id}")
	public AcctReqDetail detail(Identity id, @PathVariable("id") String reqId) {
		return requestService.detail(id, reqId);
	}

	@PostMapping("/{id}/approve")
	public ReqActionResult approve(Identity id, @PathVariable("id") String reqId,
			@RequestBody(required = false) VersionForm form) {
		return requestService.approve(id, reqId, form);
	}

	@PostMapping("/{id}/reject")
	public ReqActionResult reject(Identity id, @PathVariable("id") String reqId, @RequestBody(required = false) ReasonForm form) {
		return requestService.reject(id, reqId, form);
	}

	@PostMapping(value = "/{id}/resubmit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ReqActionResult resubmit(Identity id, @PathVariable("id") String reqId, @RequestPart("form") AcctReqForm form,
			@RequestPart(value = "docFile", required = false) MultipartFile docFile) {
		return requestService.resubmit(id, reqId, form, docFile);
	}

	@PostMapping(value = "/{id}/exec-done", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ReqActionResult execDone(Identity id, @PathVariable("id") String reqId, @RequestPart("form") ExecDoneForm form,
			@RequestPart(value = "proofFile", required = false) MultipartFile proofFile) {
		return requestService.execDone(id, reqId, form, proofFile);
	}

	@PostMapping("/{id}/verify-ok")
	public ReqActionResult verifyOk(Identity id, @PathVariable("id") String reqId,
			@RequestBody(required = false) VersionForm form) {
		return requestService.verifyOk(id, reqId, form);
	}

	@PostMapping("/{id}/verify-reject")
	public ReqActionResult verifyReject(Identity id, @PathVariable("id") String reqId,
			@RequestBody(required = false) ReasonForm form) {
		return requestService.verifyReject(id, reqId, form);
	}
}
