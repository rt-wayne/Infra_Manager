package com.mpx.infra_manager_java.model.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：IM_APPR 簽核實例查詢列（S4）
// ============================================================

import java.sql.Timestamp;

public class ApprRow {

	private Long apprId;
	private Integer docVerNo;
	private String flowId;
	private String apprStatusCode;
	private Timestamp startDate;
	private Timestamp closeDate;

	public Long getApprId() { return apprId; }
	public void setApprId(Long apprId) { this.apprId = apprId; }
	public Integer getDocVerNo() { return docVerNo; }
	public void setDocVerNo(Integer docVerNo) { this.docVerNo = docVerNo; }
	public String getFlowId() { return flowId; }
	public void setFlowId(String flowId) { this.flowId = flowId; }
	public String getApprStatusCode() { return apprStatusCode; }
	public void setApprStatusCode(String apprStatusCode) { this.apprStatusCode = apprStatusCode; }
	public Timestamp getStartDate() { return startDate; }
	public void setStartDate(Timestamp startDate) { this.startDate = startDate; }
	public Timestamp getCloseDate() { return closeDate; }
	public void setCloseDate(Timestamp closeDate) { this.closeDate = closeDate; }
}
