package com.mpx.im_acct_insp.common;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：註冊 IdentityArgumentResolver（spec v2.0 §2.2）；不開 CORS（前端經殼 jar 同源轉送）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-11
// 變更說明: BACKLOG 第 28 項：/api/** 掛 NoStoreInterceptor（回應一律 Cache-Control: no-store；殼 jar 照抄回應標頭給瀏覽器）
// ============================================================

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	private final IdentityArgumentResolver identityArgumentResolver;

	public WebConfig(IdentityArgumentResolver identityArgumentResolver) {
		this.identityArgumentResolver = identityArgumentResolver;
	}

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(identityArgumentResolver);
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new NoStoreInterceptor()).addPathPatterns("/api/**");
	}
}
