package com.mpx.infra_manager_java.model.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：歷次簽核查詢列（S9 R2，施工計畫 ⑥）。一列＝一個簽核實例的一個關卡，實例欄位（apprId、版次、實例狀態、
//           起訖時間）加在 ApprStepRow 的關卡欄位之上；ApprovalDao.findHistory 一次 JOIN 查回，由 AppQueryService 依 apprId 分組
// ============================================================

import java.sql.Timestamp;

public class ApprHistoryRow extends ApprStepRow {

	private Long apprId;
	private Integer docVerNo;
	private String apprStatusCode;
	private Timestamp apprStartDate;
	private Timestamp apprCloseDate;

	public Long getApprId() { return apprId; }
	public void setApprId(Long apprId) { this.apprId = apprId; }
	public Integer getDocVerNo() { return docVerNo; }
	public void setDocVerNo(Integer docVerNo) { this.docVerNo = docVerNo; }
	public String getApprStatusCode() { return apprStatusCode; }
	public void setApprStatusCode(String apprStatusCode) { this.apprStatusCode = apprStatusCode; }
	public Timestamp getApprStartDate() { return apprStartDate; }
	public void setApprStartDate(Timestamp apprStartDate) { this.apprStartDate = apprStartDate; }
	public Timestamp getApprCloseDate() { return apprCloseDate; }
	public void setApprCloseDate(Timestamp apprCloseDate) { this.apprCloseDate = apprCloseDate; }
}
