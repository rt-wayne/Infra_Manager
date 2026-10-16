package com.mpx.infra_manager.service;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：寄信介面（spec v2.0 §4.12）：收件 Email、主旨、本文；第一階段實作為 LogMailSender，
//           日後換共用 Email 機制只換實作。寄送失敗以例外表示（由 NotificationService 記 FAIL）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-11
// 變更說明: BACKLOG 第 25 項：實作改由 MailConfig 選用——SMTP 設定齊全用 SmtpMailSender，否則 LogMailSender
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-11
// 變更說明: BACKLOG 第 27 項：body 改為完整 HTML（由 MailHtml 組成、值已跳脫）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// ============================================================

public interface MailSender {

	/** @param body 完整 HTML（值已跳脫） */
	void send(String toEmail, String subject, String body);
}
