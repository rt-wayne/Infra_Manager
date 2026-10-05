package com.mpx;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-04
// 變更說明: 新增：前端殼 jar 主程式（規格 v4）；以 com.mpx 為掃描根，涵蓋 com.mpx.common.web 與 com.mpx.<專案名>.*
//           本包不連 DB，不需排除 DataSourceAutoConfiguration（沒有 jdbc 依賴）
//           用產生器開新專案時本檔不改
// ============================================================

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
}
