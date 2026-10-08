package com.mpx.infra_manager_java.model.mail;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-08
// 變更說明: 新增：只取 MAIL_OUTBOX_ID 的查詢結果（S8 R1）。worker 第一步「不鎖、先找候選」只需要主鍵
// ============================================================

public class MailIdRow {

	private Long mailOutboxId;

	public Long getMailOutboxId() { return mailOutboxId; }
	public void setMailOutboxId(Long mailOutboxId) { this.mailOutboxId = mailOutboxId; }
}
