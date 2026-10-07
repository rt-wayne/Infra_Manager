package com.mpx.infra_manager_java.model.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：編輯草稿失敗時判斷原因用的最小欄位（S6 回合二 b-2）：狀態、申請人、樂觀鎖版本
// ============================================================

public class AppLockRow {

	private String appStatusCode;
	private String applyUserId;
	private Long rowVerNo;

	public String getAppStatusCode() { return appStatusCode; }
	public void setAppStatusCode(String appStatusCode) { this.appStatusCode = appStatusCode; }
	public String getApplyUserId() { return applyUserId; }
	public void setApplyUserId(String applyUserId) { this.applyUserId = applyUserId; }
	public Long getRowVerNo() { return rowVerNo; }
	public void setRowVerNo(Long rowVerNo) { this.rowVerNo = rowVerNo; }
}
