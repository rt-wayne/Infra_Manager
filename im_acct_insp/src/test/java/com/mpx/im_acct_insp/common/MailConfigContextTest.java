package com.mpx.im_acct_insp.common;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-11
// 變更說明: 新增：載入 Spring Boot 寄信自動設定＋MailConfig＋MailAllowList，驗寄信實作的選擇（BACKLOG 第 25 項）。
//           主機有值 → SmtpMailSender，且與 Boot 建立的 JavaMailSender 並存（不撞 bean 名稱）；
//           主機空值、沒有主機設定、寄件人空白 → LogMailSender（只寫 log、不實際寄出）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 1：設定鍵前綴改為 infra.manager.*（專案改名 infra_manager）
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;

import com.mpx.im_acct_insp.service.LogMailSender;
import com.mpx.im_acct_insp.service.MailAllowList;
import com.mpx.im_acct_insp.service.MailSender;
import com.mpx.im_acct_insp.service.SmtpMailSender;

class MailConfigContextTest {

	final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class,
					MailSenderAutoConfiguration.class))
			.withUserConfiguration(MailConfig.class, MailAllowList.class)
			.withPropertyValues("infra.manager.mail.from=Infra-Manager@pxmart.com.tw",
					"infra.manager.mail.allow-list=2410305");

	@Test
	void 主機有值_選SMTP_與Boot的JavaMailSender並存() {
		runner.withPropertyValues("spring.mail.host=mail.example.com").run(ctx -> {
			assertThat(ctx).hasNotFailed();
			assertThat(ctx).hasSingleBean(JavaMailSender.class);
			assertThat(ctx.getBean(MailSender.class)).isInstanceOf(SmtpMailSender.class);
		});
	}

	@Test
	void 主機空值_選log版() {
		runner.withPropertyValues("spring.mail.host=").run(ctx -> {
			assertThat(ctx).hasNotFailed();
			assertThat(ctx.getBean(MailSender.class)).isInstanceOf(LogMailSender.class);
		});
	}

	@Test
	void 沒有主機設定_選log版() {
		runner.run(ctx -> {
			assertThat(ctx).hasNotFailed();
			assertThat(ctx).doesNotHaveBean(JavaMailSender.class);
			assertThat(ctx.getBean(MailSender.class)).isInstanceOf(LogMailSender.class);
		});
	}

	@Test
	void 主機有值但寄件人空白_選log版() {
		runner.withPropertyValues("spring.mail.host=mail.example.com", "infra.manager.mail.from=").run(ctx -> {
			assertThat(ctx).hasNotFailed();
			assertThat(ctx.getBean(MailSender.class)).isInstanceOf(LogMailSender.class);
		});
	}
}
