package com.mpx.infra_manager_java.service.mail;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-08
// 變更說明: 新增：MailDispatcher 的單元測試（S8 R1；JavaMailSender 用 mock，不連 SMTP）。鎖定：
//           沒有 JavaMailSender bean 或 im.mail.from 空 → isConfigured false、send 丟 IllegalStateException；
//           正常：From／To／CC／BCC／主旨／HTML 內容正確組進 MimeMessage，回傳 Message-ID；
//           收件人 JSON 壞掉 → MailPreparationException、不呼叫 send；
//           SMTP 整封失敗 → MailSendException 原樣拋；部分寄達（SendFailedException 有 validSent）→ 視為成功並附備註
//           2026-10-08 S8a 階段末 review 第 1 項（Claude Opus 5.5）：有設 override-to 時寄送當下收件人換成改寄地址、
//           CC／BCC 清空
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import com.mpx.infra_manager_java.config.MailProperties;
import com.mpx.infra_manager_java.model.mail.MailOutboxRow;
import com.mpx.infra_manager_java.service.mail.MailDispatcher.SendResult;

import jakarta.mail.Address;
import jakarta.mail.Message.RecipientType;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

class MailDispatcherTest {

	private final JavaMailSender sender = mock(JavaMailSender.class);
	@SuppressWarnings("unchecked")
	private final ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
	private final MailProperties properties = new MailProperties();
	private final MailDispatcher dispatcher = new MailDispatcher(provider, properties);

	@BeforeEach
	void setUp() {
		properties.setFrom("Infra-Manager@pxmart.com.tw");
		when(provider.getIfAvailable()).thenReturn(sender);
		when(sender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
	}

	private static MailOutboxRow row(String toJson, String ccJson, String bccJson) {
		MailOutboxRow r = new MailOutboxRow();
		r.setMailOutboxId(7L);
		r.setToJson(toJson);
		r.setCcJson(ccJson);
		r.setBccJson(bccJson);
		r.setMailSubj("[Infra] 待簽核");
		r.setHtmlBody("<p>請簽核</p>");
		r.setTryCnt(0);
		return r;
	}

	@Test
	void 沒有sender或from空時視為未設定() {
		when(provider.getIfAvailable()).thenReturn(null);
		assertThat(dispatcher.isConfigured()).isFalse();
		assertThatThrownBy(() -> dispatcher.send(row("[\"a@pxmart.com.tw\"]", null, null)))
				.isInstanceOf(IllegalStateException.class);

		when(provider.getIfAvailable()).thenReturn(sender);
		properties.setFrom("");
		assertThat(dispatcher.isConfigured()).isFalse();
		assertThatThrownBy(() -> dispatcher.send(row("[\"a@pxmart.com.tw\"]", null, null)))
				.isInstanceOf(IllegalStateException.class);
		verify(sender, never()).send(any(MimeMessage.class));
	}

	@Test
	void 正常組信並回傳MessageId() throws Exception {
		SendResult result = dispatcher.send(
				row("[\"a@pxmart.com.tw\",\"b@pxmart.com.tw\"]", "[\"c@pxmart.com.tw\"]", "[\"d@pxmart.com.tw\"]"));

		ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
		verify(sender).send(sent.capture());
		MimeMessage mime = sent.getValue();
		assertThat(addresses(mime.getFrom())).containsExactly("Infra-Manager@pxmart.com.tw");
		assertThat(addresses(mime.getRecipients(RecipientType.TO))).containsExactly("a@pxmart.com.tw", "b@pxmart.com.tw");
		assertThat(addresses(mime.getRecipients(RecipientType.CC))).containsExactly("c@pxmart.com.tw");
		assertThat(addresses(mime.getRecipients(RecipientType.BCC))).containsExactly("d@pxmart.com.tw");
		assertThat(mime.getSubject()).isEqualTo("[Infra] 待簽核");
		assertThat(mime.getContent().toString()).contains("<p>請簽核</p>");
		assertThat(mime.getDataHandler().getContentType()).startsWith("text/html");
		assertThat(result.note()).isNull();
		// saveChanges 後 Message-ID 才產生；dispatcher 用 getMessageID 取，mock sender 不會 saveChanges，故可能為 null
		assertThat(result.messageId()).isEqualTo(mime.getMessageID());
	}

	@Test
	void 有設改寄時寄送當下收件人一律換成改寄地址且清掉副本() throws Exception {
		properties.setOverrideTo("tester@pxmart.com.tw");

		dispatcher.send(row("[\"a@pxmart.com.tw\",\"b@pxmart.com.tw\"]", "[\"c@pxmart.com.tw\"]", "[\"d@pxmart.com.tw\"]"));

		ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
		verify(sender).send(sent.capture());
		MimeMessage mime = sent.getValue();
		assertThat(addresses(mime.getRecipients(RecipientType.TO))).containsExactly("tester@pxmart.com.tw");
		assertThat(mime.getRecipients(RecipientType.CC)).isNull();
		assertThat(mime.getRecipients(RecipientType.BCC)).isNull();
	}

	@Test
	void 收件人JSON壞掉時不送出() {
		assertThatThrownBy(() -> dispatcher.send(row("not json", null, null)))
				.isInstanceOf(MailPreparationException.class);
		assertThatThrownBy(() -> dispatcher.send(row("[]", null, null))).isInstanceOf(MailPreparationException.class);
		verify(sender, never()).send(any(MimeMessage.class));
	}

	@Test
	void SMTP整封失敗時例外原樣拋() {
		doThrow(new MailSendException("Could not connect")).when(sender).send(any(MimeMessage.class));

		assertThatThrownBy(() -> dispatcher.send(row("[\"a@pxmart.com.tw\"]", null, null)))
				.isInstanceOf(MailSendException.class).hasMessageContaining("Could not connect");
	}

	@Test
	void 部分寄達視為成功並附備註() throws MessagingException {
		Address[] sentOk = { new InternetAddress("a@pxmart.com.tw") };
		Address[] invalid = { new InternetAddress("bad@nowhere.invalid") };
		SendFailedException partial = new SendFailedException("Invalid Addresses", null, sentOk, new Address[0], invalid);
		doThrow(new MailSendException(Map.of(new Object(), partial))).when(sender).send(any(MimeMessage.class));

		SendResult result = dispatcher.send(row("[\"a@pxmart.com.tw\",\"bad@nowhere.invalid\"]", null, null));

		assertThat(result.note()).contains("無效 1 人").contains("未送出 0 人");
	}

	@Test
	void 部分寄達但其實一個都沒寄到時仍算失敗() throws MessagingException {
		Address[] invalid = { new InternetAddress("bad@nowhere.invalid") };
		SendFailedException none = new SendFailedException("Invalid Addresses", null, new Address[0], new Address[0], invalid);
		doThrow(new MailSendException(Map.of(new Object(), none))).when(sender).send(any(MimeMessage.class));

		assertThatThrownBy(() -> dispatcher.send(row("[\"bad@nowhere.invalid\"]", null, null)))
				.isInstanceOf(MailSendException.class);
	}

	private static java.util.List<String> addresses(Address[] addrs) {
		return java.util.Arrays.stream(addrs).map(Address::toString).toList();
	}
}
