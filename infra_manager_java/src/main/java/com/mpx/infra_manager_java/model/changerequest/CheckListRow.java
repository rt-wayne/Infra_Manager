package com.mpx.infra_manager_java.model.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：IM_APP_CHECK_LIST 檢核表查詢列（S4），含選項名稱與執行人姓名（USER_ID 與 EXEC_USER_DESC 互斥）
// ============================================================

import java.sql.Timestamp;

public class CheckListRow {

	private Integer seqNo;
	private String optionCode;
	private String optionName;
	private Integer isDone;
	private Timestamp doneDate;
	private String userId;
	private String userName;
	private String execUserDesc;

	public Integer getSeqNo() { return seqNo; }
	public void setSeqNo(Integer seqNo) { this.seqNo = seqNo; }
	public String getOptionCode() { return optionCode; }
	public void setOptionCode(String optionCode) { this.optionCode = optionCode; }
	public String getOptionName() { return optionName; }
	public void setOptionName(String optionName) { this.optionName = optionName; }
	public Integer getIsDone() { return isDone; }
	public void setIsDone(Integer isDone) { this.isDone = isDone; }
	public Timestamp getDoneDate() { return doneDate; }
	public void setDoneDate(Timestamp doneDate) { this.doneDate = doneDate; }
	public String getUserId() { return userId; }
	public void setUserId(String userId) { this.userId = userId; }
	public String getUserName() { return userName; }
	public void setUserName(String userName) { this.userName = userName; }
	public String getExecUserDesc() { return execUserDesc; }
	public void setExecUserDesc(String execUserDesc) { this.execUserDesc = execUserDesc; }
}
