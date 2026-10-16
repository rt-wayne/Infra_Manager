package com.mpx.infra_manager.common;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：取身分的單一介面（spec v2.0 §2.2、§1.3 第 1 點）
//           Controller／Service 只透過 IdentityArgumentResolver 拿到 Identity；登入、token 驗證、切換身分的實作藏在此介面後面
//           （第一階段實作 service.TokenIdentityProvider），日後換共用登入或 AD 只換實作、不改業務程式
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// ============================================================

public interface IdentityProvider {

	/**
	 * 依請求的 Authorization header 取得目前身分。
	 *
	 * @param authorizationHeader 原始 header 值（例 {@code Bearer xxx}），可為 null
	 * @return 目前身分
	 * @throws ApiException 401 A03：未帶、無效、過期或已登出
	 */
	Identity resolve(String authorizationHeader);
}
