package com.mpx.infra_manager_web.config;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：轉發器用的 RestClient（BACKLOG 第 78 項，使用者裁示 ①B 自建轉發器、刪除範本 com.mpx.common.web）
//           connect 5 秒／read 120 秒沿用範本 RestTemplateConfig 的值（read 與前端 axios timeout 一致）
//           底層用 JDK HttpClient：所有 HTTP method 都能送（HttpURLConnection 不支援 PATCH）
//           host.properties 的 @PropertySource 原本掛在範本 ApiForwarder 上，隨其刪除搬到這裡；
//           刻意不加 ignoreResourceNotFound：沒有 host.properties 就啟動失敗（與範本行為一致，快速失敗）
//           2026-10-06 複審修正：固定 HTTP/1.1（JDK HttpClient 預設 HTTP/2，對 http:// 後端會多送 h2c 升級 header）
// ============================================================

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@PropertySource("classpath:config/host.properties")
public class BackendClientConfig {

	static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
	static final Duration READ_TIMEOUT = Duration.ofSeconds(120);

	@Bean
	public RestClient backendRestClient(RestClient.Builder builder) {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(CONNECT_TIMEOUT)
				.version(HttpClient.Version.HTTP_1_1)
				.followRedirects(HttpClient.Redirect.NEVER)
				.build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
		factory.setReadTimeout(READ_TIMEOUT);
		return builder.requestFactory(factory).build();
	}
}
