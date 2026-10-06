package com.mpx.infra_manager_java.model.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：IM_APPR_CAND_MAP 關卡候選簽核人查詢列（S4）
// ============================================================

public class CandRow {

	private Long apprStepId;
	private String userId;
	private String userName;

	public Long getApprStepId() { return apprStepId; }
	public void setApprStepId(Long apprStepId) { this.apprStepId = apprStepId; }
	public String getUserId() { return userId; }
	public void setUserId(String userId) { this.userId = userId; }
	public String getUserName() { return userName; }
	public void setUserName(String userName) { this.userName = userName; }
}
