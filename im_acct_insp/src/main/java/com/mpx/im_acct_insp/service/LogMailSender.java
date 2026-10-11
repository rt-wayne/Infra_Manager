package com.mpx.im_acct_insp.service;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：第一階段寄信實作（spec v2.0 §4.12、§1.2：不實際寄出）——只寫 log 並視為成功。
//           log 只記主旨；不記收件 Email 與本文（事件與收件人數由 NotificationService 彙總記錄）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-11
// 變更說明: BACKLOG 第 25 項：改為 SMTP 未設定時的退回實作，由 MailConfig 選用（拿掉 @Component）
// ============================================================

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogMailSender implements MailSender {

	private static final Logger log = LoggerFactory.getLogger(LogMailSender.class);

	@Override
	public void send(String toEmail, String subject, String body) {
		log.info("Email（log 版，未實際寄出）主旨={}", subject);
	}
}
