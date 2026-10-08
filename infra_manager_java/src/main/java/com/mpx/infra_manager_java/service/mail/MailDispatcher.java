package com.mpx.infra_manager_java.service.mail;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-08
// 變更說明: 新增：把 outbox 一列真的交給 SMTP（S8 R1）。
//           JavaMailSender 由 Boot 依 spring.mail.host 自動建立，host 未設時沒有這個 bean，所以用 ObjectProvider 取；
//           未設定時 send 丟 IllegalStateException，worker 據此只記一次 log、不寄。
//           mail.smtp.sendpartial=true（example 設定）時部分收件人無效仍會寄給其他人：JavaMailSenderImpl 會把
//           SendFailedException 包成 MailSendException 丟出，本類別檢查 getValidSentAddresses 判斷「其實已寄出」，
//           回傳附備註的成功結果，避免一個錯字地址讓其他人每次重試都再收一封。
//           不記 log（成功／失敗由 worker 統一記，且不記地址）
//           2026-10-08 S8a 階段末 review 第 1 項（Claude Opus 5.5）：im.mail.override-to 有值時，寄送當下也把收件人
//           一律換成改寄地址、清掉 cc／bcc——寫入時沒設改寄的舊信（例如整合測試留下的）在驗收期間也不會寄給原收件人
// ============================================================

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import com.mpx.infra_manager_java.config.MailProperties;
import com.mpx.infra_manager_java.model.mail.MailOutboxRow;

import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.MimeMessage;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Component
public class MailDispatcher {

	/** SMTP_MSG_ID VARCHAR2(255) */
	static final int MSG_ID_MAX = 255;

	private static final JsonMapper MAPPER = JsonMapper.builder().build();
	private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
	};

	/** 寄送結果：Message-ID（可能 null）與備註（部分收件人無效時才有） */
	public record SendResult(String messageId, String note) {
	}

	private final ObjectProvider<JavaMailSender> senderProvider;
	private final MailProperties properties;

	public MailDispatcher(ObjectProvider<JavaMailSender> senderProvider, MailProperties properties) {
		this.senderProvider = senderProvider;
		this.properties = properties;
	}

	/** spring.mail.host 與 im.mail.from 都有值才算設定完成 */
	public boolean isConfigured() {
		return senderProvider.getIfAvailable() != null && !properties.getFrom().isBlank();
	}

	/**
	 * 寄出一列。SMTP 未設定丟 IllegalStateException；收件人 JSON 壞掉丟 MailPreparationException；
	 * SMTP 失敗丟 Spring 的 MailException（部分寄達視為成功、備註無效人數）
	 */
	public SendResult send(MailOutboxRow row) {
		JavaMailSender sender = senderProvider.getIfAvailable();
		if (sender == null || properties.getFrom().isBlank()) {
			throw new IllegalStateException("SMTP 未設定（spring.mail.host 或 im.mail.from 為空）");
		}
		MimeMessage mime = sender.createMimeMessage();
		try {
			MimeMessageHelper helper = new MimeMessageHelper(mime, false, "UTF-8");
			helper.setFrom(properties.getFrom());
			List<String> to = addresses(row.getToJson());
			List<String> cc = addresses(row.getCcJson());
			List<String> bcc = addresses(row.getBccJson());
			if (properties.hasOverrideTo()) {
				// 寄送當下再改寄一次：不管這封信寫入時有沒有設改寄，驗收期間都只寄到改寄地址
				to = List.of(properties.getOverrideTo());
				cc = List.of();
				bcc = List.of();
			}
			if (to.isEmpty() && cc.isEmpty() && bcc.isEmpty()) {
				throw new MailPreparationException("沒有收件人");
			}
			if (!to.isEmpty()) {
				helper.setTo(to.toArray(String[]::new));
			}
			if (!cc.isEmpty()) {
				helper.setCc(cc.toArray(String[]::new));
			}
			if (!bcc.isEmpty()) {
				helper.setBcc(bcc.toArray(String[]::new));
			}
			helper.setSubject(row.getMailSubj());
			helper.setText(row.getHtmlBody(), true);
		} catch (MessagingException e) {
			throw new MailPreparationException("組信失敗", e);
		}
		try {
			sender.send(mime);
			return new SendResult(messageId(mime), null);
		} catch (MailSendException e) {
			SendFailedException partial = partialSuccess(e);
			if (partial == null) {
				throw e;
			}
			int invalid = partial.getInvalidAddresses() == null ? 0 : partial.getInvalidAddresses().length;
			int unsent = partial.getValidUnsentAddresses() == null ? 0 : partial.getValidUnsentAddresses().length;
			return new SendResult(messageId(mime), "部分收件人未寄達：無效 " + invalid + " 人、未送出 " + unsent + " 人");
		}
	}

	/** sendpartial 下「至少寄給了一個人」的 SendFailedException；否則回 null */
	private static SendFailedException partialSuccess(MailException e) {
		if (!(e instanceof MailSendException mse)) {
			return null;
		}
		for (Exception cause : mse.getFailedMessages().values()) {
			if (cause instanceof SendFailedException sfe && sfe.getValidSentAddresses() != null
					&& sfe.getValidSentAddresses().length > 0) {
				return sfe;
			}
		}
		return null;
	}

	private static String messageId(MimeMessage mime) {
		try {
			String id = mime.getMessageID();
			if (id == null) {
				return null;
			}
			return id.length() <= MSG_ID_MAX ? id : id.substring(0, MSG_ID_MAX);
		} catch (MessagingException e) {
			return null;
		}
	}

	private static List<String> addresses(String json) {
		if (json == null || json.isBlank()) {
			return List.of();
		}
		try {
			List<String> list = MAPPER.readValue(json, STRING_LIST);
			return list == null ? List.of() : list;
		} catch (RuntimeException e) {
			throw new MailPreparationException("收件人 JSON 無法解析", e);
		}
	}
}
