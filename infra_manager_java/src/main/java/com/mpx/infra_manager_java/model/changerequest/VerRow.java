package com.mpx.infra_manager_java.model.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：IM_APP_VER 歷史版次查詢列（S4；不取 FORM_JSON 快照本體）
// ============================================================

import java.sql.Timestamp;

public class VerRow {

	private Integer appVerNo;
	private String closeStatusCode;
	private String verReason;
	private Timestamp snapDate;

	public Integer getAppVerNo() { return appVerNo; }
	public void setAppVerNo(Integer appVerNo) { this.appVerNo = appVerNo; }
	public String getCloseStatusCode() { return closeStatusCode; }
	public void setCloseStatusCode(String closeStatusCode) { this.closeStatusCode = closeStatusCode; }
	public String getVerReason() { return verReason; }
	public void setVerReason(String verReason) { this.verReason = verReason; }
	public Timestamp getSnapDate() { return snapDate; }
	public void setSnapDate(Timestamp snapDate) { this.snapDate = snapDate; }
}
