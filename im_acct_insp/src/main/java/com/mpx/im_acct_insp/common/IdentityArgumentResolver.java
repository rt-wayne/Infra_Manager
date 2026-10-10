package com.mpx.im_acct_insp.common;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：Controller 方法宣告 Identity 參數即取得目前身分（spec v2.0 §2.2 單一介面）
//           讀 Authorization header 交給 IdentityProvider；驗證失敗丟 401（由 GlobalExceptionHandler 轉 {message}）。
//           除 POST /auth/login 外所有 API 方法都宣告 Identity（ControllerIdentityTest 檢查），即「都要驗 token」
// ============================================================

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class IdentityArgumentResolver implements HandlerMethodArgumentResolver {

	private final IdentityProvider identityProvider;

	public IdentityArgumentResolver(IdentityProvider identityProvider) {
		this.identityProvider = identityProvider;
	}

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return Identity.class.equals(parameter.getParameterType());
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest,
			WebDataBinderFactory binderFactory) {
		return identityProvider.resolve(webRequest.getHeader(HttpHeaders.AUTHORIZATION));
	}
}
