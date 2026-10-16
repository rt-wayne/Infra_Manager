package com.mpx.infra_manager.service;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-11
// 變更說明: 新增：SMTP 寄信實作（BACKLOG 第 25 項，寄法同 Infra_Manager_Java 的 MailDispatcher）。
//           JavaMailSender 由 Spring Boot 依 spring.mail.* 建立；純文字本文、UTF-8；同步寄送、不重試。
//           組信失敗包成 MailPreparationException，SMTP 失敗由 JavaMailSender 丟 MailException，
//           都是 RuntimeException，由 NotificationService 記 FAIL（ERR_MSG＝例外類別名）。
//           不記 log（收件人工號與結果由 NotificationService 記，Email 與本文都不記）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-11
// 變更說明: BACKLOG 第 27 項：本文改以 HTML 寄出（同 Infra_Manager_Java 的 setText(html, true)），只寄 HTML、不附純文字
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// ============================================================

import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

public class SmtpMailSender implements MailSender {

	private final JavaMailSender sender;
	private final String from;

	public SmtpMailSender(JavaMailSender sender, String from) {
		this.sender = sender;
		this.from = from;
	}

	@Override
	public void send(String toEmail, String subject, String body) {
		MimeMessage mime = sender.createMimeMessage();
		try {
			MimeMessageHelper helper = new MimeMessageHelper(mime, false, "UTF-8");
			helper.setFrom(from);
			helper.setTo(toEmail);
			helper.setSubject(subject);
			helper.setText(body, true);
		} catch (MessagingException e) {
			throw new MailPreparationException("組信失敗", e);
		}
		sender.send(mime);
	}
}
