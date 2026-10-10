package com.mpx.im_acct_insp.model;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：變更單 #1（M1）GET /accounts/lookup?keyword 清單列精簡欄位＋inFlight＋allowedReqTypes；
//           完整內容欄位改由 GET /accounts/lookup/{id}（AcctLookup）取得
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（spec_im_acct_insp_v2.0.md §6.4）：改為 GET /accounts/lookup?acctName（完整帳號名稱精確比對）的回傳列
// ============================================================

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

public class AcctLookupBrief {

	private String acctId;
	private String catgId;
	private String catgName;
	private String acctName;
	private String sysName;
	private String holderName;
	private String acctStateCode;
	private String expiryTypeCode;
	@JsonFormat(pattern = "yyyy-MM-dd")
	private LocalDateTime expiryDate;
	private Boolean inFlight;
	private List<String> allowedReqTypes;

	public String getAcctId() {
		return acctId;
	}

	public void setAcctId(String acctId) {
		this.acctId = acctId;
	}

	public String getCatgId() {
		return catgId;
	}

	public void setCatgId(String catgId) {
		this.catgId = catgId;
	}

	public String getCatgName() {
		return catgName;
	}

	public void setCatgName(String catgName) {
		this.catgName = catgName;
	}

	public String getAcctName() {
		return acctName;
	}

	public void setAcctName(String acctName) {
		this.acctName = acctName;
	}

	public String getSysName() {
		return sysName;
	}

	public void setSysName(String sysName) {
		this.sysName = sysName;
	}

	public String getHolderName() {
		return holderName;
	}

	public void setHolderName(String holderName) {
		this.holderName = holderName;
	}

	public String getAcctStateCode() {
		return acctStateCode;
	}

	public void setAcctStateCode(String acctStateCode) {
		this.acctStateCode = acctStateCode;
	}

	public String getExpiryTypeCode() {
		return expiryTypeCode;
	}

	public void setExpiryTypeCode(String expiryTypeCode) {
		this.expiryTypeCode = expiryTypeCode;
	}

	public LocalDateTime getExpiryDate() {
		return expiryDate;
	}

	public void setExpiryDate(LocalDateTime expiryDate) {
		this.expiryDate = expiryDate;
	}

	public Boolean getInFlight() {
		return inFlight;
	}

	public void setInFlight(Boolean inFlight) {
		this.inFlight = inFlight;
	}

	public List<String> getAllowedReqTypes() {
		return allowedReqTypes;
	}

	public void setAllowedReqTypes(List<String> allowedReqTypes) {
		this.allowedReqTypes = allowedReqTypes;
	}
}
