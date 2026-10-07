package com.mpx.infra_manager_web.config;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：殼 jar 不解析 multipart（S6 回合一，裁示 ①A）。Spring Boot 預設的 multipart 解析器會在進轉發器前
//           把本文讀光，轉到後端變 0 byte。以同名 bean 取代（MultipartAutoConfiguration 遇到既有 MultipartResolver 會退讓），
//           一律回「不是 multipart」，本文留給轉發器原樣串流。用程式而非 spring.servlet.multipart.enabled=false：
//           真檔 application.properties 不進 git，漏改就會靜默壞掉；寫在程式裡部署不必手動改設定
// ============================================================

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.servlet.DispatcherServlet;

import jakarta.servlet.http.HttpServletRequest;

@Configuration(proxyBeanMethods = false)
public class NoMultipartConfig {

	@Bean(name = DispatcherServlet.MULTIPART_RESOLVER_BEAN_NAME)
	public MultipartResolver multipartResolver() {
		return new PassThroughMultipartResolver();
	}

	static final class PassThroughMultipartResolver implements MultipartResolver {

		@Override
		public boolean isMultipart(HttpServletRequest request) {
			return false;
		}

		@Override
		public MultipartHttpServletRequest resolveMultipart(HttpServletRequest request) throws MultipartException {
			throw new MultipartException("殼 jar 不解析 multipart");
		}

		@Override
		public void cleanupMultipart(MultipartHttpServletRequest request) {
		}
	}
}
