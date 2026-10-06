package com.mpx.infra_manager_java.model.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：IM_APP_EXEC 實際執行紀錄查詢列（S4），含結果選項名稱與執行人姓名
// ============================================================

import java.sql.Timestamp;

public class ExecRow {

	private Integer appVerNo;
	private Timestamp actualStartDate;
	private Timestamp actualEndDate;
	private String resultCode;
	private String resultName;
	private Integer isExcpt;
	private String excptDesc;
	private Integer isFollowUp;
	private String followUpDesc;
	private String execMemo;
	private String userId;
	private String userName;
	private Timestamp closeDate;

	public Integer getAppVerNo() { return appVerNo; }
	public void setAppVerNo(Integer appVerNo) { this.appVerNo = appVerNo; }
	public Timestamp getActualStartDate() { return actualStartDate; }
	public void setActualStartDate(Timestamp actualStartDate) { this.actualStartDate = actualStartDate; }
	public Timestamp getActualEndDate() { return actualEndDate; }
	public void setActualEndDate(Timestamp actualEndDate) { this.actualEndDate = actualEndDate; }
	public String getResultCode() { return resultCode; }
	public void setResultCode(String resultCode) { this.resultCode = resultCode; }
	public String getResultName() { return resultName; }
	public void setResultName(String resultName) { this.resultName = resultName; }
	public Integer getIsExcpt() { return isExcpt; }
	public void setIsExcpt(Integer isExcpt) { this.isExcpt = isExcpt; }
	public String getExcptDesc() { return excptDesc; }
	public void setExcptDesc(String excptDesc) { this.excptDesc = excptDesc; }
	public Integer getIsFollowUp() { return isFollowUp; }
	public void setIsFollowUp(Integer isFollowUp) { this.isFollowUp = isFollowUp; }
	public String getFollowUpDesc() { return followUpDesc; }
	public void setFollowUpDesc(String followUpDesc) { this.followUpDesc = followUpDesc; }
	public String getExecMemo() { return execMemo; }
	public void setExecMemo(String execMemo) { this.execMemo = execMemo; }
	public String getUserId() { return userId; }
	public void setUserId(String userId) { this.userId = userId; }
	public String getUserName() { return userName; }
	public void setUserName(String userName) { this.userName = userName; }
	public Timestamp getCloseDate() { return closeDate; }
	public void setCloseDate(Timestamp closeDate) { this.closeDate = closeDate; }
}
