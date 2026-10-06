package com.mpx.infra_manager_java.config;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-05
// 變更說明: 新增：請求本文上限（S1，裁示 ⑤A）
//           JSON 本文 1 MB（BodyLimitFilter）、單一附件 50 MB、整個 multipart 請求 500 MB（MultipartConfigElement）；
//           超過一律回 413。上限是產品決策，寫在程式常數、進 git，不放 properties 真檔。
//           自行宣告 MultipartConfigElement 後，Boot 的 MultipartAutoConfiguration 只讓出這一個 bean（@ConditionalOnMissingBean），
//           StandardServletMultipartResolver 仍由 Boot 建立，所以 application.properties 的 spring.servlet.multipart.resolve-lazily=true
//           有效：只有真的取 MultipartFile 參數的端點才會解析並落暫存檔，其他端點收到 multipart 不會寫磁碟（code review 2026-10-06）。
// ============================================================

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import com.mpx.infra_manager_java.web.BodyLimitFilter;

import jakarta.servlet.MultipartConfigElement;

@Configuration
public class RequestLimitConfig {

	/** JSON 等非 multipart 本文上限 */
	public static final long JSON_MAX_BYTES = 1L * 1024 * 1024;
	/** 單一附件上限（沿用舊系統 50 MB） */
	public static final long FILE_MAX_BYTES = 50L * 1024 * 1024;
	/** 整個 multipart 請求上限 */
	public static final long REQUEST_MAX_BYTES = 500L * 1024 * 1024;

	@Bean
	public MultipartConfigElement multipartConfigElement() {
		return new MultipartConfigElement("", FILE_MAX_BYTES, REQUEST_MAX_BYTES, 0);
	}

	@Bean
	public FilterRegistrationBean<BodyLimitFilter> bodyLimitFilter() {
		FilterRegistrationBean<BodyLimitFilter> bean = new FilterRegistrationBean<>(new BodyLimitFilter(JSON_MAX_BYTES));
		bean.addUrlPatterns("/api/*");
		bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
		return bean;
	}
}
