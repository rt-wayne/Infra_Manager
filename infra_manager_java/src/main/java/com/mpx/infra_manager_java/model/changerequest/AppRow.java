package com.mpx.infra_manager_java.model.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：IM_APP 查詢列（S4），列表與檢視共用；BeanPropertyRowMapper 需無參數建構子與 setter，
//           SQL 別名底線轉駝峰。U_RANGE／U_START_NO／U_END_NO 以 UNIT_ 開頭的別名取回，避開 Java bean
//           「u 開頭接大寫」的屬性名規則。DATE 欄位以 Timestamp 承接（台灣牆上時間，不換算）
//           S6 回合二 a（Claude Opus 5.5，2026-10-06）：加 rowVerNo（樂觀鎖版本號，檢視 API 帶給編輯頁）
// ============================================================

import java.math.BigDecimal;
import java.sql.Timestamp;

public class AppRow {

	private String appId;
	private String appTitle;
	private String prioCode;
	private String prioName;
	private String prioColor;
	private String flowId;
	private String flowName;
	private String applyUserId;
	private String applyUserName;
	private String appStatusCode;
	private String sourceCode;
	private Integer currVerNo;
	private Long rowVerNo;
	private Timestamp applyDate;
	private String applyDeptName;
	private String applyTel;
	private String applyEmail;
	private Integer isSelfExec;
	private Integer isSupExec;
	private String workModeCode;
	private String remoteMethod;
	private String supName;
	private String supCntct;
	private String supTel;
	private Integer supHeadCnt;
	private String workSubj;
	private String impactDesc;
	private String workDetail;
	private String riskDesc;
	private String rollBackPlan;
	private String otherReason;
	private Timestamp schedStartDate;
	private Timestamp schedEndDate;
	private BigDecimal estHourQty;
	private String locSourceCode;
	private String areaName;
	private String rackName;
	private String unitRange;
	private String siteId;
	private String rackId;
	private Integer unitStartNo;
	private Integer unitEndNo;
	private String omitReason;
	private String resubMemo;
	private Timestamp createDate;
	private Timestamp updateDate;
	/** 列表用：目前關卡名稱、候選人數、候選人（只有一位時）姓名、是否待我簽核 */
	private String currStepName;
	private Integer currStepCandCnt;
	private String currStepCandName;
	private Integer mineFlag;

	public String getAppId() { return appId; }
	public void setAppId(String appId) { this.appId = appId; }
	public String getAppTitle() { return appTitle; }
	public void setAppTitle(String appTitle) { this.appTitle = appTitle; }
	public String getPrioCode() { return prioCode; }
	public void setPrioCode(String prioCode) { this.prioCode = prioCode; }
	public String getPrioName() { return prioName; }
	public void setPrioName(String prioName) { this.prioName = prioName; }
	public String getPrioColor() { return prioColor; }
	public void setPrioColor(String prioColor) { this.prioColor = prioColor; }
	public String getFlowId() { return flowId; }
	public void setFlowId(String flowId) { this.flowId = flowId; }
	public String getFlowName() { return flowName; }
	public void setFlowName(String flowName) { this.flowName = flowName; }
	public String getApplyUserId() { return applyUserId; }
	public void setApplyUserId(String applyUserId) { this.applyUserId = applyUserId; }
	public String getApplyUserName() { return applyUserName; }
	public void setApplyUserName(String applyUserName) { this.applyUserName = applyUserName; }
	public String getAppStatusCode() { return appStatusCode; }
	public void setAppStatusCode(String appStatusCode) { this.appStatusCode = appStatusCode; }
	public String getSourceCode() { return sourceCode; }
	public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
	public Integer getCurrVerNo() { return currVerNo; }
	public void setCurrVerNo(Integer currVerNo) { this.currVerNo = currVerNo; }
	public Long getRowVerNo() { return rowVerNo; }
	public void setRowVerNo(Long rowVerNo) { this.rowVerNo = rowVerNo; }
	public Timestamp getApplyDate() { return applyDate; }
	public void setApplyDate(Timestamp applyDate) { this.applyDate = applyDate; }
	public String getApplyDeptName() { return applyDeptName; }
	public void setApplyDeptName(String applyDeptName) { this.applyDeptName = applyDeptName; }
	public String getApplyTel() { return applyTel; }
	public void setApplyTel(String applyTel) { this.applyTel = applyTel; }
	public String getApplyEmail() { return applyEmail; }
	public void setApplyEmail(String applyEmail) { this.applyEmail = applyEmail; }
	public Integer getIsSelfExec() { return isSelfExec; }
	public void setIsSelfExec(Integer isSelfExec) { this.isSelfExec = isSelfExec; }
	public Integer getIsSupExec() { return isSupExec; }
	public void setIsSupExec(Integer isSupExec) { this.isSupExec = isSupExec; }
	public String getWorkModeCode() { return workModeCode; }
	public void setWorkModeCode(String workModeCode) { this.workModeCode = workModeCode; }
	public String getRemoteMethod() { return remoteMethod; }
	public void setRemoteMethod(String remoteMethod) { this.remoteMethod = remoteMethod; }
	public String getSupName() { return supName; }
	public void setSupName(String supName) { this.supName = supName; }
	public String getSupCntct() { return supCntct; }
	public void setSupCntct(String supCntct) { this.supCntct = supCntct; }
	public String getSupTel() { return supTel; }
	public void setSupTel(String supTel) { this.supTel = supTel; }
	public Integer getSupHeadCnt() { return supHeadCnt; }
	public void setSupHeadCnt(Integer supHeadCnt) { this.supHeadCnt = supHeadCnt; }
	public String getWorkSubj() { return workSubj; }
	public void setWorkSubj(String workSubj) { this.workSubj = workSubj; }
	public String getImpactDesc() { return impactDesc; }
	public void setImpactDesc(String impactDesc) { this.impactDesc = impactDesc; }
	public String getWorkDetail() { return workDetail; }
	public void setWorkDetail(String workDetail) { this.workDetail = workDetail; }
	public String getRiskDesc() { return riskDesc; }
	public void setRiskDesc(String riskDesc) { this.riskDesc = riskDesc; }
	public String getRollBackPlan() { return rollBackPlan; }
	public void setRollBackPlan(String rollBackPlan) { this.rollBackPlan = rollBackPlan; }
	public String getOtherReason() { return otherReason; }
	public void setOtherReason(String otherReason) { this.otherReason = otherReason; }
	public Timestamp getSchedStartDate() { return schedStartDate; }
	public void setSchedStartDate(Timestamp schedStartDate) { this.schedStartDate = schedStartDate; }
	public Timestamp getSchedEndDate() { return schedEndDate; }
	public void setSchedEndDate(Timestamp schedEndDate) { this.schedEndDate = schedEndDate; }
	public BigDecimal getEstHourQty() { return estHourQty; }
	public void setEstHourQty(BigDecimal estHourQty) { this.estHourQty = estHourQty; }
	public String getLocSourceCode() { return locSourceCode; }
	public void setLocSourceCode(String locSourceCode) { this.locSourceCode = locSourceCode; }
	public String getAreaName() { return areaName; }
	public void setAreaName(String areaName) { this.areaName = areaName; }
	public String getRackName() { return rackName; }
	public void setRackName(String rackName) { this.rackName = rackName; }
	public String getUnitRange() { return unitRange; }
	public void setUnitRange(String unitRange) { this.unitRange = unitRange; }
	public String getSiteId() { return siteId; }
	public void setSiteId(String siteId) { this.siteId = siteId; }
	public String getRackId() { return rackId; }
	public void setRackId(String rackId) { this.rackId = rackId; }
	public Integer getUnitStartNo() { return unitStartNo; }
	public void setUnitStartNo(Integer unitStartNo) { this.unitStartNo = unitStartNo; }
	public Integer getUnitEndNo() { return unitEndNo; }
	public void setUnitEndNo(Integer unitEndNo) { this.unitEndNo = unitEndNo; }
	public String getOmitReason() { return omitReason; }
	public void setOmitReason(String omitReason) { this.omitReason = omitReason; }
	public String getResubMemo() { return resubMemo; }
	public void setResubMemo(String resubMemo) { this.resubMemo = resubMemo; }
	public Timestamp getCreateDate() { return createDate; }
	public void setCreateDate(Timestamp createDate) { this.createDate = createDate; }
	public Timestamp getUpdateDate() { return updateDate; }
	public void setUpdateDate(Timestamp updateDate) { this.updateDate = updateDate; }
	public String getCurrStepName() { return currStepName; }
	public void setCurrStepName(String currStepName) { this.currStepName = currStepName; }
	public Integer getCurrStepCandCnt() { return currStepCandCnt; }
	public void setCurrStepCandCnt(Integer currStepCandCnt) { this.currStepCandCnt = currStepCandCnt; }
	public String getCurrStepCandName() { return currStepCandName; }
	public void setCurrStepCandName(String currStepCandName) { this.currStepCandName = currStepCandName; }
	public Integer getMineFlag() { return mineFlag; }
	public void setMineFlag(Integer mineFlag) { this.mineFlag = mineFlag; }
}
