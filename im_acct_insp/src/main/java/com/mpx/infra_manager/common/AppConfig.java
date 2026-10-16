package com.mpx.infra_manager.common;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：業務共用 Bean。Clock 供取號日期、審查日期計算使用（單元測試以固定時鐘替換）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（spec_im_acct_insp_v2.0.md §3、§5、§4.11、§6.1、§9.4）：
//           Clock 固定 Asia/Taipei（業務時間一律由此產生並以參數綁定寫入）；
//           TransactionTemplate：以 LazyDataSourceTransactionManager 延遲到第一次開交易才取
//           dbConnectionManager.getJdbcTemplate(${itflow.db.alias}).getJdbcTemplate().getDataSource()；
//           @EnableScheduling（J01／J02，cron 與開關見 JobService）；
//           @PropertySource 載入選用的 config/environment.properties（檔案不存在仍可啟動；缺 key 用程式預設，
//           唯 im.acct.insp.attach.dir 必須設定且為絕對路徑，否則啟動失敗——變更單 #3，檢查在 AttachmentService）；
//           MultipartConfigElement：spring.servlet.multipart.max-file-size／max-request-size 未設定時預設 10MB／11MB
//           （Spring 內建預設 1MB 會擋下合法附件）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-14
// 變更說明: DB 別名 key 由 db.connect.itflow 改名為 itflow.db.alias（使用者改名）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// ============================================================

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.unit.DataSize;

import com.mpx.common.db.DbConnectionManager;

import jakarta.servlet.MultipartConfigElement;

@Configuration
@EnableScheduling
@PropertySource(value = "classpath:config/environment.properties", ignoreResourceNotFound = true, encoding = "UTF-8")
public class AppConfig {

	public static final ZoneId ZONE = ZoneId.of("Asia/Taipei");

	@Bean
	public Clock clock() {
		return Clock.system(ZONE);
	}

	/** 業務交易（spec §5）；DataSource 延遲到第一次使用才取，與 DbClient 共用同一個連線池 */
	@Bean
	public TransactionTemplate transactionTemplate(DbConnectionManager dbConnectionManager,
			@Value("${itflow.db.alias}") String itflowDb) {
		String key = itflowDb == null ? "" : itflowDb.trim();
		return new TransactionTemplate(new LazyDataSourceTransactionManager(
				() -> dbConnectionManager.getJdbcTemplate(key).getJdbcTemplate().getDataSource()));
	}

	/** 附件上限（spec §6.1）；超過由 GlobalExceptionHandler 轉 413 E15 */
	@Bean
	public MultipartConfigElement multipartConfigElement(
			@Value("${spring.servlet.multipart.max-file-size:10MB}") String maxFileSize,
			@Value("${spring.servlet.multipart.max-request-size:11MB}") String maxRequestSize) {
		return new MultipartConfigElement("", DataSize.parse(maxFileSize).toBytes(), DataSize.parse(maxRequestSize).toBytes(), 0);
	}
}
