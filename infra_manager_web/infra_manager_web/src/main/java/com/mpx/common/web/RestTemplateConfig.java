package com.mpx.common.web;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-05
// 變更說明: 新增：轉發用 RestTemplate（規格 v4），比照 store_web_barcode
//           connect 5 秒：後端連不到就快速失敗，讓前端能提示
//           read 120 秒：與前端 axios timeout 一致；太短會把「正常但很慢」誤判成故障
//           不設 timeout 的後果：後端卡住時執行緒無限期掛著，連線耗盡後整頁失效
//           jdk25 階段 3（規格 D-36）：RestTemplateBuilder 改 org.springframework.boot.restclient，逾時改 connectTimeout／readTimeout
//           共用元件，複製後不要改
// ============================================================

import java.time.Duration;

import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

	@Bean
	public RestTemplate restTemplate(RestTemplateBuilder builder) {
		return builder
				.connectTimeout(Duration.ofSeconds(5))
				.readTimeout(Duration.ofSeconds(120))
				.build();
	}
}
