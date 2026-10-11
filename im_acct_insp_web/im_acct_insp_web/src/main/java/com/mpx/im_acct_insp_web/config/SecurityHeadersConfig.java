package com.mpx.im_acct_insp_web.config;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-11
// 變更說明: 新增：殼 jar 所有回應加安全標頭（BACKLOG 第 30 項）；畫面、靜態檔、轉送後端的 API 回應都帶
//           CSP 只准載入本站的程式、樣式、圖片與連線（前端建置產物無行內程式、無 data: 網址、無 eval、無外站資源；
//             Vue 的 :style 走 DOM 屬性設定，不受 style-src 限制；附件下載用 blob 網址存檔，屬導覽不受限）
//           不加 X-Frame-Options／frame-ancestors：使用者裁示允許任何網站嵌入（2026-10-11），誘騙點擊風險見 PRD 已知限制
//           不加 HSTS：正式環境改走 HTTPS 後再加（BACKLOG 第 34 項）
//           前端日後若引入外站資源、行內程式或 data: 圖片，要同步放寬 CSP，否則畫面會被瀏覽器擋下（console 有 CSP 錯誤）
// ============================================================

import java.io.IOException;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class SecurityHeadersConfig {

	static final String CSP = "default-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'";
	/** 連到外站時不帶本站網址；站內照常帶 */
	static final String REFERRER_POLICY = "same-origin";
	/** 本系統用不到的瀏覽器功能一律關閉 */
	static final String PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=(), payment=(), usb=()";

	@Bean
	public FilterRegistrationBean<SecurityHeadersFilter> securityHeadersFilter() {
		FilterRegistrationBean<SecurityHeadersFilter> reg = new FilterRegistrationBean<>(new SecurityHeadersFilter());
		reg.addUrlPatterns("/*");
		return reg;
	}

	/**
	 * 處理請求前先設好標頭，畫面、靜態檔、轉送回應與錯誤回應都會帶到；
	 * 但後端回應若帶同名標頭，以後端為準（BackendForwarder 照抄後端標頭，Spring 7 寫回應標頭時會先移除同名）
	 */
	public static class SecurityHeadersFilter extends OncePerRequestFilter {

		@Override
		protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
				throws ServletException, IOException {
			response.setHeader("Content-Security-Policy", CSP);
			response.setHeader("X-Content-Type-Options", "nosniff");
			response.setHeader("Referrer-Policy", REFERRER_POLICY);
			response.setHeader("Permissions-Policy", PERMISSIONS_POLICY);
			chain.doFilter(request, response);
		}
	}
}
