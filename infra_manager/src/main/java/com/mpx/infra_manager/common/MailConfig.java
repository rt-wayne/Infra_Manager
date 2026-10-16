package com.mpx.infra_manager.common;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-11
// 變更說明: 新增：選擇寄信實作（BACKLOG 第 25 項）。spring.mail.host 這個 key 存在時 Spring Boot 就建立 JavaMailSender
//           （留空也會建），所以另外檢查主機不可空白：JavaMailSender、主機、寄件人 im.acct.insp.mail.from 三者齊全
//           → SmtpMailSender（實際寄出），否則 → LogMailSender（只寫 log，與第一階段相同）。
//           bean 名稱刻意不用 mailSender（那是 Boot JavaMailSender 的名稱，同名會啟動失敗）。
//           啟動時記一行 log 說明用哪一種與寄送名單人數（不記工號）；SMTP 啟用但名單空、又沒開寄給所有人時另記 WARN
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 1：設定鍵前綴 im.acct.insp.* 改為 infra.manager.*（專案改名 infra_manager）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// ============================================================

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

import com.mpx.infra_manager.service.LogMailSender;
import com.mpx.infra_manager.service.MailAllowList;
import com.mpx.infra_manager.service.MailSender;
import com.mpx.infra_manager.service.SmtpMailSender;

@Configuration
public class MailConfig {

	private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

	@Bean
	public MailSender notifyMailSender(ObjectProvider<JavaMailSender> javaMailSender,
			@Value("${spring.mail.host:}") String host, @Value("${infra.manager.mail.from:}") String from,
			MailAllowList allowList) {
		JavaMailSender sender = javaMailSender.getIfAvailable();
		String fromAddr = from == null ? "" : from.trim();
		String scope = allowList.isSendToAll() ? "寄給所有收件人" : "寄送名單 " + allowList.size() + " 人";
		if (sender == null || host == null || host.isBlank() || fromAddr.isEmpty()) {
			log.info("寄信：SMTP 未設定（spring.mail.host 或 infra.manager.mail.from 為空），只寫 log、不實際寄出；{}", scope);
			return new LogMailSender();
		}
		log.info("寄信：SMTP 實際寄出；{}", scope);
		if (!allowList.isSendToAll() && allowList.size() == 0) {
			// 正式環境漏設 send-to-all 時，所有通知會記 SKIP，J01／J02 這一輪也被當成已寄
			log.warn("寄信：SMTP 已啟用但寄送名單是空的、也沒開 infra.manager.mail.send-to-all，所有通知都不會寄出（記 SKIP「不在寄送名單」）");
		}
		return new SmtpMailSender(sender, fromAddr);
	}
}
