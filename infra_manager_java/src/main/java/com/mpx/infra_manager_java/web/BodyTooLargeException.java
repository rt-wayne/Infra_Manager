package com.mpx.infra_manager_java.web;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-05
// 變更說明: 新增：請求本文超過上限（S1）。繼承 IOException：在讀取 ServletInputStream 時丟出，
//           會被 Jackson 包進 HttpMessageNotReadableException，由 ApiExceptionHandler 認出並回 413
// ============================================================

import java.io.IOException;

public class BodyTooLargeException extends IOException {

	private static final long serialVersionUID = 1L;

	private final long maxBytes;

	public BodyTooLargeException(long maxBytes) {
		super("請求本文超過上限 " + maxBytes + " bytes");
		this.maxBytes = maxBytes;
	}

	public long getMaxBytes() {
		return maxBytes;
	}
}
