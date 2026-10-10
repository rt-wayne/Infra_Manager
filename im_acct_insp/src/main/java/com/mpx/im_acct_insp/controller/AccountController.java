package com.mpx.im_acct_insp.controller;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：帳號 API（spec §4.2）：/accounts、/accounts/mine、/accounts/lookup、/accounts/{id}、/accounts/{id}/history
//           mine、lookup 為固定路徑，Spring 比對時優先於 {id}
//           變更單 #1：新增 /accounts/lookup/{id}（兩段路徑，不會被單段的 /{id} 吃掉）；/accounts/lookup 改回精簡欄位
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（spec_im_acct_insp_v2.0.md §6.4）：身分改 Identity（Bearer）；/accounts 回 AcctListResult、加 expiring 條件；
//           /accounts/lookup 參數改 acctName（完整精確比對）；/accounts/lookup/{id} 加 acctName 參數
// ============================================================

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mpx.im_acct_insp.common.Identity;
import com.mpx.im_acct_insp.model.Acct;
import com.mpx.im_acct_insp.model.AcctHist;
import com.mpx.im_acct_insp.model.AcctListResult;
import com.mpx.im_acct_insp.model.AcctLookup;
import com.mpx.im_acct_insp.model.AcctLookupBrief;
import com.mpx.im_acct_insp.service.AccountService;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

	private final AccountService accountService;

	public AccountController(AccountService accountService) {
		this.accountService = accountService;
	}

	@GetMapping
	public AcctListResult list(Identity id, @RequestParam(required = false) String keyword,
			@RequestParam(required = false) String catgId, @RequestParam(required = false) String stateCode,
			@RequestParam(required = false) String assetClassId, @RequestParam(required = false) String expiring) {
		return accountService.list(id, keyword, catgId, stateCode, assetClassId, expiring);
	}

	@GetMapping("/mine")
	public List<Acct> mine(Identity id) {
		return accountService.mine(id);
	}

	@GetMapping("/lookup")
	public List<AcctLookupBrief> lookup(Identity id, @RequestParam(required = false) String acctName) {
		return accountService.lookup(id, acctName);
	}

	@GetMapping("/lookup/{id}")
	public AcctLookup lookupById(Identity id, @PathVariable("id") String acctId,
			@RequestParam(required = false) String acctName) {
		return accountService.lookupById(id, acctId, acctName);
	}

	@GetMapping("/{id}")
	public Acct detail(Identity id, @PathVariable("id") String acctId) {
		return accountService.detail(id, acctId);
	}

	@GetMapping("/{id}/history")
	public List<AcctHist> history(Identity id, @PathVariable("id") String acctId) {
		return accountService.history(id, acctId);
	}
}
