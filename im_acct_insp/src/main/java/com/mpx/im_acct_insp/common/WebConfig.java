package com.mpx.im_acct_insp.common;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：註冊 IdentityArgumentResolver（spec v2.0 §2.2）；不開 CORS（前端經殼 jar 同源轉送）
// ============================================================

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
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
}
