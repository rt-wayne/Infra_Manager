package com.mpx.im_acct_insp.model;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：帳號申請單 IM_ACCT_REQ 全欄位＋stageName、catgName、assetClassName、applyByName（spec §4.3）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（spec_im_acct_insp_v2.0.md §6.5；Table v0.2 7.15）：刪 ORIG_REQ_ID、APPLY_TEL_NO、APPLY_EMAIL；
//           加 EFFECT_DATE、REJECT_CNT、VERSION_NO；加 execByName、verifyByName（取自 IM_ACCT_USER）；時間格式 yyyy-MM-dd HH:mm:ss
// ============================================================

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

public class AcctReq {

	private String reqId;
	private String acctId;
	private String reqTypeCode;
	private String reqStateCode;
	private Integer currStageId;
	private String applyBy;
	private String applyDept;
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime applyDate;
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
	private Integer isUniqueConf;
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime submitDate;
	private Integer resubCnt;
	private Integer rejectCnt;
	private Integer rejectStageId;
	private String rejectBy;
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime rejectDate;
	private String rejectReason;
	private String execBy;
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime execDate;
	private String execDescr;
	private String cardNo;
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime effectDate;
	private String verifyBy;
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime verifyDate;
	private Integer verifyRejectCnt;
	private String verifyRejectReason;
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime doneDate;
	private Integer versionNo;
	private Integer status;
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime createDate;
	private String createBy;
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime updateDate;
	private String updateBy;
	private String stageName;
	private String catgName;
	private String assetClassName;
	private String applyByName;
	private String execByName;
	private String verifyByName;

	public String getReqId() {
		return reqId;
	}

	public void setReqId(String reqId) {
		this.reqId = reqId;
	}

	public String getAcctId() {
		return acctId;
	}

	public void setAcctId(String acctId) {
		this.acctId = acctId;
	}

	public String getReqTypeCode() {
		return reqTypeCode;
	}

	public void setReqTypeCode(String reqTypeCode) {
		this.reqTypeCode = reqTypeCode;
	}

	public String getReqStateCode() {
		return reqStateCode;
	}

	public void setReqStateCode(String reqStateCode) {
		this.reqStateCode = reqStateCode;
	}

	public Integer getCurrStageId() {
		return currStageId;
	}

	public void setCurrStageId(Integer currStageId) {
		this.currStageId = currStageId;
	}

	public String getApplyBy() {
		return applyBy;
	}

	public void setApplyBy(String applyBy) {
		this.applyBy = applyBy;
	}

	public String getApplyDept() {
		return applyDept;
	}

	public void setApplyDept(String applyDept) {
		this.applyDept = applyDept;
	}

	public LocalDateTime getApplyDate() {
		return applyDate;
	}

	public void setApplyDate(LocalDateTime applyDate) {
		this.applyDate = applyDate;
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

	public Integer getIsUniqueConf() {
		return isUniqueConf;
	}

	public void setIsUniqueConf(Integer isUniqueConf) {
		this.isUniqueConf = isUniqueConf;
	}

	public LocalDateTime getSubmitDate() {
		return submitDate;
	}

	public void setSubmitDate(LocalDateTime submitDate) {
		this.submitDate = submitDate;
	}

	public Integer getResubCnt() {
		return resubCnt;
	}

	public void setResubCnt(Integer resubCnt) {
		this.resubCnt = resubCnt;
	}

	public Integer getRejectCnt() {
		return rejectCnt;
	}

	public void setRejectCnt(Integer rejectCnt) {
		this.rejectCnt = rejectCnt;
	}

	public Integer getRejectStageId() {
		return rejectStageId;
	}

	public void setRejectStageId(Integer rejectStageId) {
		this.rejectStageId = rejectStageId;
	}

	public String getRejectBy() {
		return rejectBy;
	}

	public void setRejectBy(String rejectBy) {
		this.rejectBy = rejectBy;
	}

	public LocalDateTime getRejectDate() {
		return rejectDate;
	}

	public void setRejectDate(LocalDateTime rejectDate) {
		this.rejectDate = rejectDate;
	}

	public String getRejectReason() {
		return rejectReason;
	}

	public void setRejectReason(String rejectReason) {
		this.rejectReason = rejectReason;
	}

	public String getExecBy() {
		return execBy;
	}

	public void setExecBy(String execBy) {
		this.execBy = execBy;
	}

	public LocalDateTime getExecDate() {
		return execDate;
	}

	public void setExecDate(LocalDateTime execDate) {
		this.execDate = execDate;
	}

	public String getExecDescr() {
		return execDescr;
	}

	public void setExecDescr(String execDescr) {
		this.execDescr = execDescr;
	}

	public String getCardNo() {
		return cardNo;
	}

	public void setCardNo(String cardNo) {
		this.cardNo = cardNo;
	}

	public LocalDateTime getEffectDate() {
		return effectDate;
	}

	public void setEffectDate(LocalDateTime effectDate) {
		this.effectDate = effectDate;
	}

	public String getVerifyBy() {
		return verifyBy;
	}

	public void setVerifyBy(String verifyBy) {
		this.verifyBy = verifyBy;
	}

	public LocalDateTime getVerifyDate() {
		return verifyDate;
	}

	public void setVerifyDate(LocalDateTime verifyDate) {
		this.verifyDate = verifyDate;
	}

	public Integer getVerifyRejectCnt() {
		return verifyRejectCnt;
	}

	public void setVerifyRejectCnt(Integer verifyRejectCnt) {
		this.verifyRejectCnt = verifyRejectCnt;
	}

	public String getVerifyRejectReason() {
		return verifyRejectReason;
	}

	public void setVerifyRejectReason(String verifyRejectReason) {
		this.verifyRejectReason = verifyRejectReason;
	}

	public LocalDateTime getDoneDate() {
		return doneDate;
	}

	public void setDoneDate(LocalDateTime doneDate) {
		this.doneDate = doneDate;
	}

	public Integer getVersionNo() {
		return versionNo;
	}

	public void setVersionNo(Integer versionNo) {
		this.versionNo = versionNo;
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

	public String getStageName() {
		return stageName;
	}

	public void setStageName(String stageName) {
		this.stageName = stageName;
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

	public String getApplyByName() {
		return applyByName;
	}

	public void setApplyByName(String applyByName) {
		this.applyByName = applyByName;
	}

	public String getExecByName() {
		return execByName;
	}

	public void setExecByName(String execByName) {
		this.execByName = execByName;
	}

	public String getVerifyByName() {
		return verifyByName;
	}

	public void setVerifyByName(String verifyByName) {
		this.verifyByName = verifyByName;
	}
}
