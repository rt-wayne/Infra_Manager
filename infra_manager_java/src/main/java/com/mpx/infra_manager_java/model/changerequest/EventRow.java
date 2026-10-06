package com.mpx.infra_manager_java.model.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：IM_APP_EVENT 事件紀錄查詢列（S4），含操作人姓名
// ============================================================

import java.sql.Timestamp;

public class EventRow {

	private Long appEventId;
	private Integer appVerNo;
	private String eventCode;
	private String userId;
	private String userName;
	private Timestamp eventDate;
	private String memo;

	public Long getAppEventId() { return appEventId; }
	public void setAppEventId(Long appEventId) { this.appEventId = appEventId; }
	public Integer getAppVerNo() { return appVerNo; }
	public void setAppVerNo(Integer appVerNo) { this.appVerNo = appVerNo; }
	public String getEventCode() { return eventCode; }
	public void setEventCode(String eventCode) { this.eventCode = eventCode; }
	public String getUserId() { return userId; }
	public void setUserId(String userId) { this.userId = userId; }
	public String getUserName() { return userName; }
	public void setUserName(String userName) { this.userName = userName; }
	public Timestamp getEventDate() { return eventDate; }
	public void setEventDate(Timestamp eventDate) { this.eventDate = eventDate; }
	public String getMemo() { return memo; }
	public void setMemo(String memo) { this.memo = memo; }
}
