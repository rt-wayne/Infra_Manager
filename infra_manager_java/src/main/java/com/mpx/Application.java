package com.mpx;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-05
// 變更說明: 新增：範本主程式（開發範本）
//           v3：固定為 com.mpx.Application，以 com.mpx 為掃描根，涵蓋 com.mpx.common.* 與 com.mpx.<專案名>.*（規格 D-17）；
//           複製或用產生器開新專案時本檔不改
//           變更單 #4：載入 config/database.properties（DB 別名），讓業務程式的 @Value 讀得到；檔案不存在時仍可啟動
//           jdk25 階段 2（規格 D-36）：DataSourceAutoConfiguration 改用 Boot 4 的 package（org.springframework.boot.jdbc.autoconfigure）
//           排除 DataSourceAutoConfiguration：本範本沒有 spring.datasource.url，
//           連線池由 common.db.DbConnectionManager 依 dbName 向連線資訊 API 取得後建立（規格 D-02）
//           複製進已有 DataSource 的專案時不必排除，那邊的設定照舊
// ============================================================

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.PropertySource;

@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
@PropertySource(value = "classpath:config/database.properties", ignoreResourceNotFound = true)
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
}
