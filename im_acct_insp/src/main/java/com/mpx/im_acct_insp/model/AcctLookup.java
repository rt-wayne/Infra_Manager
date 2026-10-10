package com.mpx.im_acct_insp.model;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：GET /accounts/lookup 精簡欄位（帶入異動申請表單所需內容）＋inFlight＋allowedReqTypes（spec §4.2）
//           變更單 #1：改作 GET /accounts/lookup/{id} 的回傳（完整內容欄位）；清單列改用 AcctLookupBrief
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（spec_im_acct_insp_v2.0.md §6.4）：GET /accounts/lookup/{id} 的 IM_ACCT 內容欄位（供異動表單帶入），
//           由 AccountService 自 Acct 複製；加 applyReason
// ============================================================

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

public class AcctLookup {

	private String acctId;
	private String catgId;
	private String catgName;
	private String acctName;
	private String sysName;
	private String doorScopeCode;
	private String assetClassId;
	private String assetClassName;
	private Integer isPriv;
	private String holderName;
	private String holderDept;
	private String holderCntct;
	private String holderEmail;
	private String accessLevelCode;
	private String accessTarget;
	private String applyReason;
	private String expiryTypeCode;
	@JsonFormat(pattern = "yyyy-MM-dd")
	private LocalDateTime expiryDate;
	private String acctStateCode;
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

	public String getDoorScopeCode() {
		return doorScopeCode;
	}

	public void setDoorScopeCode(String doorScopeCode) {
		this.doorScopeCode = doorScopeCode;
	}

	public String getAssetClassId() {
		return assetClassId;
	}

	public void setAssetClassId(String assetClassId) {
		this.assetClassId = assetClassId;
	}

	public String getAssetClassName() {
		return assetClassName;
	}

	public void setAssetClassName(String assetClassName) {
		this.assetClassName = assetClassName;
	}

	public Integer getIsPriv() {
		return isPriv;
	}

	public void setIsPriv(Integer isPriv) {
		this.isPriv = isPriv;
	}

	public String getHolderName() {
		return holderName;
	}

	public void setHolderName(String holderName) {
		this.holderName = holderName;
	}

	public String getHolderDept() {
		return holderDept;
	}

	public void setHolderDept(String holderDept) {
		this.holderDept = holderDept;
	}

	public String getHolderCntct() {
		return holderCntct;
	}

	public void setHolderCntct(String holderCntct) {
		this.holderCntct = holderCntct;
	}

	public String getHolderEmail() {
		return holderEmail;
	}

	public void setHolderEmail(String holderEmail) {
		this.holderEmail = holderEmail;
	}

	public String getAccessLevelCode() {
		return accessLevelCode;
	}

	public void setAccessLevelCode(String accessLevelCode) {
		this.accessLevelCode = accessLevelCode;
	}

	public String getAccessTarget() {
		return accessTarget;
	}

	public void setAccessTarget(String accessTarget) {
		this.accessTarget = accessTarget;
	}

	public String getApplyReason() {
		return applyReason;
	}

	public void setApplyReason(String applyReason) {
		this.applyReason = applyReason;
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

	public String getAcctStateCode() {
		return acctStateCode;
	}

	public void setAcctStateCode(String acctStateCode) {
		this.acctStateCode = acctStateCode;
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
