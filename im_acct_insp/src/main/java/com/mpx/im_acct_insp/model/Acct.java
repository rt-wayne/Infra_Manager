package com.mpx.im_acct_insp.model;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：帳號主表 IM_ACCT 全欄位＋catgName、assetClassName、inFlight（spec §4.2）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（spec_im_acct_insp_v2.0.md §6.4；Table v0.2 7.14）：刪 DISABLE_TYPE_CODE、IMPORT_ID、ORIG_ACCT_ID、NEW_ACCT_ID、
//           REVIEW_BASIS_CODE，加 REVIEW_FLAG_SOURCE_CODE、REVIEW_FLAG_BY；加 inFlightReqId、pendingVerify（待複驗，計算值）、allowedReqTypes；
//           時間格式改 yyyy-MM-dd HH:mm:ss（spec §3）
// ============================================================

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

public class Acct {

	private String acctId;
	private String catgId;
	private String acctName;
	private String sysName;
	private String doorScopeCode;
	private String assetClassId;
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
	private String sourceCode;
	private String applyBy;
	@JsonFormat(pattern = "yyyy-MM-dd")
	private LocalDateTime lastReviewDate;
	@JsonFormat(pattern = "yyyy-MM-dd")
	private LocalDateTime nextReviewDate;
	private Integer isReviewFlag;
	private String reviewFlagReason;
	private String reviewFlagSourceCode;
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime reviewFlagDate;
	private String reviewFlagBy;
	private Integer status;
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime createDate;
	private String createBy;
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime updateDate;
	private String updateBy;
	private String catgName;
	private String assetClassName;
	private Boolean inFlight;
	private String inFlightReqId;
	private Boolean pendingVerify;
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

	public String getSourceCode() {
		return sourceCode;
	}

	public void setSourceCode(String sourceCode) {
		this.sourceCode = sourceCode;
	}

	public String getApplyBy() {
		return applyBy;
	}

	public void setApplyBy(String applyBy) {
		this.applyBy = applyBy;
	}

	public LocalDateTime getLastReviewDate() {
		return lastReviewDate;
	}

	public void setLastReviewDate(LocalDateTime lastReviewDate) {
		this.lastReviewDate = lastReviewDate;
	}

	public LocalDateTime getNextReviewDate() {
		return nextReviewDate;
	}

	public void setNextReviewDate(LocalDateTime nextReviewDate) {
		this.nextReviewDate = nextReviewDate;
	}

	public Integer getIsReviewFlag() {
		return isReviewFlag;
	}

	public void setIsReviewFlag(Integer isReviewFlag) {
		this.isReviewFlag = isReviewFlag;
	}

	public String getReviewFlagReason() {
		return reviewFlagReason;
	}

	public void setReviewFlagReason(String reviewFlagReason) {
		this.reviewFlagReason = reviewFlagReason;
	}

	public String getReviewFlagSourceCode() {
		return reviewFlagSourceCode;
	}

	public void setReviewFlagSourceCode(String reviewFlagSourceCode) {
		this.reviewFlagSourceCode = reviewFlagSourceCode;
	}

	public LocalDateTime getReviewFlagDate() {
		return reviewFlagDate;
	}

	public void setReviewFlagDate(LocalDateTime reviewFlagDate) {
		this.reviewFlagDate = reviewFlagDate;
	}

	public String getReviewFlagBy() {
		return reviewFlagBy;
	}

	public void setReviewFlagBy(String reviewFlagBy) {
		this.reviewFlagBy = reviewFlagBy;
	}

	public Integer getStatus() {
		return status;
	}

	public void setStatus(Integer status) {
		this.status = status;
	}

	public LocalDateTime getCreateDate() {
		return createDate;
	}

	public void setCreateDate(LocalDateTime createDate) {
		this.createDate = createDate;
	}

	public String getCreateBy() {
		return createBy;
	}

	public void setCreateBy(String createBy) {
		this.createBy = createBy;
	}

	public LocalDateTime getUpdateDate() {
		return updateDate;
	}

	public void setUpdateDate(LocalDateTime updateDate) {
		this.updateDate = updateDate;
	}

	public String getUpdateBy() {
		return updateBy;
	}

	public void setUpdateBy(String updateBy) {
		this.updateBy = updateBy;
	}

	public String getCatgName() {
		return catgName;
	}

	public void setCatgName(String catgName) {
		this.catgName = catgName;
	}

	public String getAssetClassName() {
		return assetClassName;
	}

	public void setAssetClassName(String assetClassName) {
		this.assetClassName = assetClassName;
	}

	public Boolean getInFlight() {
		return inFlight;
	}

	public void setInFlight(Boolean inFlight) {
		this.inFlight = inFlight;
	}

	public String getInFlightReqId() {
		return inFlightReqId;
	}

	public void setInFlightReqId(String inFlightReqId) {
		this.inFlightReqId = inFlightReqId;
	}

	public Boolean getPendingVerify() {
		return pendingVerify;
	}

	public void setPendingVerify(Boolean pendingVerify) {
		this.pendingVerify = pendingVerify;
	}

	public List<String> getAllowedReqTypes() {
		return allowedReqTypes;
	}

	public void setAllowedReqTypes(List<String> allowedReqTypes) {
		this.allowedReqTypes = allowedReqTypes;
	}
}
