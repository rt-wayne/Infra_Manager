package com.mpx.im_acct_insp.service;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：寄信介面（spec v2.0 §4.12）：收件 Email、主旨、本文；第一階段實作為 LogMailSender，
//           日後換共用 Email 機制只換實作。寄送失敗以例外表示（由 NotificationService 記 FAIL）
// ============================================================

public interface MailSender {

	void send(String toEmail, String subject, String body);
}
