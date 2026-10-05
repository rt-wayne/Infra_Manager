package com.mpx.common.web;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-04
// 變更說明: 新增：轉發失敗例外（規格 v4 複審修正）；由 ApiForwarder 把 RestClientException／IllegalArgumentException 包成本例外
//           訊息只含 method 與 path（不含完整 URL、不含後端回應本文），原例外掛在 cause
//           ForwardExceptionHandler 只接本例外，業務程式自己丟的例外不受影響
//           共用元件，複製後不要改
// ============================================================

public class ForwardException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ForwardException(String method, String path, Throwable cause) {
		super("forward failed " + method + " " + path, cause);
	}
}
