package com.mpx.infra_manager_java.config;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-08
// 變更說明: 新增：背景寄信排程的開關（S8 R1）。只有 im.mail.enabled=true 才啟用 @EnableScheduling，
//           MailWorker 的 @Scheduled 才會跑；預設關閉，開發機與 mvnw verify 不會每 30 秒去碰 DB 與 SMTP。
//           不動 Application.java（屬範本層）
// ============================================================

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "im.mail", name = "enabled", havingValue = "true")
public class MailSchedulingConfig {
	// 只負責帶上 @EnableScheduling，沒有 bean
}
