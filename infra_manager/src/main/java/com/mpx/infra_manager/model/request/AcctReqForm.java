package com.mpx.infra_manager.model.request;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：POST /requests、POST /requests/{id}/resubmit 的 body（spec §4.3）
//           isPriv、isUniqueConf 為 1／0；expiryDate 為 yyyy-MM-dd 字串；resubmit 時 reqTypeCode、acctId 不採用
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（spec_im_acct_insp_v2.0.md §6.5）：改為 multipart 的 part form（JSON）；刪 applyTelNo、applyEmail（不儲存，送了也忽略）、
//           attachFileName（改上傳檔案 part docFile）；加 versionNo（resubmit 用）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// ============================================================

public class AcctReqForm {

	private String reqTypeCode;
	private String acctId;
	private String applyDept;
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
	private String expiryDate;
	private Integer isUniqueConf;
	private Integer versionNo;

	public String getReqTypeCode() {
		return reqTypeCode;
	}

	public void setReqTypeCode(String reqTypeCode) {
		this.reqTypeCode = reqTypeCode;
	}

	public String getAcctId() {
		return acctId;
	}

	public void setAcctId(String acctId) {
		this.acctId = acctId;
	}

	public String getApplyDept() {
		return applyDept;
	}

	public void setApplyDept(String applyDept) {
		this.applyDept = applyDept;
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

	public String getExpiryDate() {
		return expiryDate;
	}

	public void setExpiryDate(String expiryDate) {
		this.expiryDate = expiryDate;
	}

	public Integer getIsUniqueConf() {
		return isUniqueConf;
	}

	public void setIsUniqueConf(Integer isUniqueConf) {
		this.isUniqueConf = isUniqueConf;
	}

	public Integer getVersionNo() {
		return versionNo;
	}

	public void setVersionNo(Integer versionNo) {
		this.versionNo = versionNo;
	}
}
