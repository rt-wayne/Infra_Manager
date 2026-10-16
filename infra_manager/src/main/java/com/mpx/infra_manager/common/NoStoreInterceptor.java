package com.mpx.infra_manager.common;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-11
// 變更說明: 新增（BACKLOG 第 28 項）：API 回應一律帶 Cache-Control: no-store。
//           原因：瀏覽器按上一頁／下一頁重新載入時會優先用硬碟快取裡的 GET /me，
//           切換身分後回上一頁會顯示舊身分；另外 API 回應含個資，不該留在共用電腦的硬碟快取
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// ============================================================

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 由 WebConfig 掛在 /api/**；涵蓋範圍是「Spring 找到處理者之後」的回應：在 Controller 執行前就設好標頭，
 * 之後丟例外轉成的錯誤回應（含驗 token 失敗的 401、路徑不存在的 404）也會帶到。
 * 找處理者之前就失敗的 405／406／415 與上傳超限的 413 不帶（都是 POST 或前端不會發的請求，瀏覽器不會快取）
 */
public class NoStoreInterceptor implements HandlerInterceptor {

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
		return true;
	}
}
