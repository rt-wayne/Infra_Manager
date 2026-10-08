package com.mpx.infra_manager_java.model.mail;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-08
// 變更說明: 新增：信件收件人查詢列（S8 R2，MailRecipientDao 回傳）。EMAIL 可能為 null（帳號沒填），由呼叫端略過
// ============================================================

public class RecipientRow {

	private String userId;
	private String userName;
	private String email;

	public String getUserId() { return userId; }
	public void setUserId(String userId) { this.userId = userId; }
	public String getUserName() { return userName; }
	public void setUserName(String userName) { this.userName = userName; }
	public String getEmail() { return email; }
	public void setEmail(String email) { this.email = email; }
}
