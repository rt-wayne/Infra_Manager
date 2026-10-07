package com.mpx.infra_manager_java.model.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-07
// 變更說明: 新增：IM_APP_VER 歷史版次完整列（S9 R1，含 FORM_JSON 快照本體與建立者）；
//           列表與檢視頁仍用不帶快照的 VerRow，避免每次檢視都搬整份 CLOB
// ============================================================

import java.sql.Timestamp;

public class AppVerRow {

	private Integer appVerNo;
	private String closeStatusCode;
	private String verReason;
	private Timestamp snapDate;
	private String formJson;
	private String createBy;

	public Integer getAppVerNo() { return appVerNo; }
	public void setAppVerNo(Integer appVerNo) { this.appVerNo = appVerNo; }
	public String getCloseStatusCode() { return closeStatusCode; }
	public void setCloseStatusCode(String closeStatusCode) { this.closeStatusCode = closeStatusCode; }
	public String getVerReason() { return verReason; }
	public void setVerReason(String verReason) { this.verReason = verReason; }
	public Timestamp getSnapDate() { return snapDate; }
	public void setSnapDate(Timestamp snapDate) { this.snapDate = snapDate; }
	public String getFormJson() { return formJson; }
	public void setFormJson(String formJson) { this.formJson = formJson; }
	public String getCreateBy() { return createBy; }
	public void setCreateBy(String createBy) { this.createBy = createBy; }
}
