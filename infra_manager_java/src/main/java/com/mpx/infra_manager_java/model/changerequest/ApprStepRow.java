package com.mpx.infra_manager_java.model.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：簽核關卡查詢列（S4）＝ IM_APPR_STEP 接 IM_FLOW_STEP 的定義欄位；
//           沒有簽核實例時（草稿、已收回）也用本類別承接流程定義的關卡（APPR_STEP_ID 為 null、狀態 WAITING）
// ============================================================

import java.sql.Timestamp;

public class ApprStepRow {

	private Long apprStepId;
	private Integer seqNo;
	private String stepCode;
	private String stepName;
	private String stepModeCode;
	private String apprType;
	private String flowUserId;
	private String roleId;
	private Integer isNotifyOnly;
	private String stepStatusCode;
	private Timestamp decideDate;
	private String userId;
	private String userName;
	private String memo;

	public Long getApprStepId() { return apprStepId; }
	public void setApprStepId(Long apprStepId) { this.apprStepId = apprStepId; }
	public Integer getSeqNo() { return seqNo; }
	public void setSeqNo(Integer seqNo) { this.seqNo = seqNo; }
	public String getStepCode() { return stepCode; }
	public void setStepCode(String stepCode) { this.stepCode = stepCode; }
	public String getStepName() { return stepName; }
	public void setStepName(String stepName) { this.stepName = stepName; }
	public String getStepModeCode() { return stepModeCode; }
	public void setStepModeCode(String stepModeCode) { this.stepModeCode = stepModeCode; }
	public String getApprType() { return apprType; }
	public void setApprType(String apprType) { this.apprType = apprType; }
	public String getFlowUserId() { return flowUserId; }
	public void setFlowUserId(String flowUserId) { this.flowUserId = flowUserId; }
	public String getRoleId() { return roleId; }
	public void setRoleId(String roleId) { this.roleId = roleId; }
	public Integer getIsNotifyOnly() { return isNotifyOnly; }
	public void setIsNotifyOnly(Integer isNotifyOnly) { this.isNotifyOnly = isNotifyOnly; }
	public String getStepStatusCode() { return stepStatusCode; }
	public void setStepStatusCode(String stepStatusCode) { this.stepStatusCode = stepStatusCode; }
	public Timestamp getDecideDate() { return decideDate; }
	public void setDecideDate(Timestamp decideDate) { this.decideDate = decideDate; }
	public String getUserId() { return userId; }
	public void setUserId(String userId) { this.userId = userId; }
	public String getUserName() { return userName; }
	public void setUserName(String userName) { this.userName = userName; }
	public String getMemo() { return memo; }
	public void setMemo(String memo) { this.memo = memo; }
}
