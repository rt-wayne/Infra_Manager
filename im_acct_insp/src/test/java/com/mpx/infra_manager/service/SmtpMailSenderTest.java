package com.mpx.infra_manager.service;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-11
// 變更說明: 新增：SMTP 寄信實作與實作選擇單元測試（BACKLOG 第 25 項）。
//           寄件人、收件人、主旨、純文字 UTF-8 本文；SMTP 失敗原例外外拋（NotificationService 記 FAIL）；
//           MailConfig：沒有 JavaMailSender、主機空白或寄件人空白 → LogMailSender，三者齊全 → SmtpMailSender
//           （載入 Boot 自動設定的版本見 common.MailConfigContextTest）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-11
// 變更說明: BACKLOG 第 27 項：本文改驗 text/html UTF-8
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import com.mpx.infra_manager.common.MailConfig;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

class SmtpMailSenderTest {

	static final String FROM = "Infra-Manager@pxmart.com.tw";
	static final String HOST = "mail.example.com";

	@Test
	void 寄出_寄件人收件人主旨_HTML_UTF8() throws Exception {
		JavaMailSender js = mock(JavaMailSender.class);
		when(js.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
		String html = "<html><body><p>單號：AR20261011-001</p></body></html>";
		new SmtpMailSender(js, FROM).send("a@example.com", "【帳號申請】AR20261011-001 待您審核", html);

		ArgumentCaptor<MimeMessage> cap = ArgumentCaptor.forClass(MimeMessage.class);
		verify(js).send(cap.capture());
		MimeMessage m = cap.getValue();
		assertThat(m.getFrom()[0].toString()).isEqualTo(FROM);
		assertThat(m.getRecipients(Message.RecipientType.TO)).extracting(Object::toString).containsExactly("a@example.com");
		assertThat(m.getSubject()).isEqualTo("【帳號申請】AR20261011-001 待您審核");
		m.saveChanges();
		assertThat(m.getContentType()).startsWith("text/html").containsIgnoringCase("charset=UTF-8");
		assertThat(m.getContent()).isEqualTo(html);
	}

	@Test
	void SMTP失敗_原例外外拋() {
		JavaMailSender js = mock(JavaMailSender.class);
		when(js.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
		doThrow(new MailSendException("smtp down")).when(js).send(any(MimeMessage.class));
		assertThatThrownBy(() -> new SmtpMailSender(js, FROM).send("a@example.com", "s", "b"))
				.isInstanceOf(MailSendException.class);
	}

	@Test
	@SuppressWarnings("unchecked")
	void 實作選擇_SMTP主機與寄件人齊全才實際寄出() {
		MailConfig cfg = new MailConfig();
		MailAllowList list = new MailAllowList("2410305", false);
		ObjectProvider<JavaMailSender> none = mock(ObjectProvider.class);
		ObjectProvider<JavaMailSender> some = mock(ObjectProvider.class);
		when(some.getIfAvailable()).thenReturn(mock(JavaMailSender.class));

		assertThat(cfg.notifyMailSender(none, HOST, FROM, list)).isInstanceOf(LogMailSender.class);
		assertThat(cfg.notifyMailSender(some, " ", FROM, list)).isInstanceOf(LogMailSender.class);
		assertThat(cfg.notifyMailSender(some, null, FROM, list)).isInstanceOf(LogMailSender.class);
		assertThat(cfg.notifyMailSender(some, HOST, " ", list)).isInstanceOf(LogMailSender.class);
		assertThat(cfg.notifyMailSender(some, HOST, null, list)).isInstanceOf(LogMailSender.class);
		assertThat(cfg.notifyMailSender(some, HOST, " " + FROM + " ", list)).isInstanceOf(SmtpMailSender.class);

		// 退回實作不寄信、不拋例外
		cfg.notifyMailSender(none, HOST, FROM, list).send("a@example.com", "s", "b");
	}
}
