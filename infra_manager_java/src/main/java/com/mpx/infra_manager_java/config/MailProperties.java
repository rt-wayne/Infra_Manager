package com.mpx.infra_manager_java.config;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-08
// 變更說明: 新增：信件 outbox 的專案自訂設定 im.mail.*（S8 R1，2026-10-08 使用者裁示 ①B：連線值放 properties 真檔）。
//           SMTP 連線本身用 Boot 內建的 spring.mail.*（host 有值才會建 JavaMailSender）；這裡只放專案自己的開關與參數。
//           enabled 預設 false：開發機與 mvnw verify 不啟動排程、不寄信，信只寫進 IM_MAIL_OUTBOX。
//           overrideTo 有值時所有信改寄到該地址（驗收用；測試 DB 可能有同事的真實 email）
// ============================================================

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "im.mail")
public class MailProperties {

	/** true 才啟動背景寄信排程（MailSchedulingConfig） */
	private boolean enabled = false;
	/** 寄件人地址（舊系統 Infra-Manager@pxmart.com.tw） */
	private String from = "";
	/** 信內連結的網址前綴（殼 jar 對外位址，不含結尾斜線） */
	private String siteUrl = "";
	/** 有值時所有收件人改成這個地址；正式環境留空 */
	private String overrideTo = "";
	/** 每輪最多寄幾封 */
	private int batchSize = 20;
	/** 重試上限；達上限標 FAILED */
	private int maxTry = 5;
	/** 重試退避：上次嘗試時間 + 已嘗試次數 × 此分鐘數 */
	private int retryBackoffMinutes = 1;
	/** 輪詢間隔（毫秒）；@Scheduled 直接讀 ${im.mail.poll-delay-ms}，這個欄位只供顯示 */
	private long pollDelayMs = 30_000L;

	public boolean isEnabled() { return enabled; }
	public void setEnabled(boolean enabled) { this.enabled = enabled; }
	public String getFrom() { return from; }
	public void setFrom(String from) { this.from = from == null ? "" : from.trim(); }
	public String getSiteUrl() { return siteUrl; }
	public void setSiteUrl(String siteUrl) { this.siteUrl = siteUrl == null ? "" : siteUrl.trim(); }
	public String getOverrideTo() { return overrideTo; }
	public void setOverrideTo(String overrideTo) { this.overrideTo = overrideTo == null ? "" : overrideTo.trim(); }
	public int getBatchSize() { return batchSize; }
	public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
	public int getMaxTry() { return maxTry; }
	public void setMaxTry(int maxTry) { this.maxTry = maxTry; }
	public int getRetryBackoffMinutes() { return retryBackoffMinutes; }
	public void setRetryBackoffMinutes(int retryBackoffMinutes) { this.retryBackoffMinutes = retryBackoffMinutes; }
	public long getPollDelayMs() { return pollDelayMs; }
	public void setPollDelayMs(long pollDelayMs) { this.pollDelayMs = pollDelayMs; }

	public boolean hasOverrideTo() {
		return !overrideTo.isBlank();
	}
}
