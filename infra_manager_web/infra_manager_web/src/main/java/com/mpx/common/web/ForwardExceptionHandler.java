package com.mpx.common.web;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-04
// 變更說明: 新增：轉發失敗的統一處理（規格 v4 審查修正）
//           只接 ForwardException（ApiForwarder 包裝的轉發失敗：RestClientException、IllegalArgumentException），回 HTTP 500 與固定短訊息
//           業務程式自己丟的例外（例 IllegalArgumentException）不經此處理，照 Spring 預設處理
//           不再記 log（ApiForwarder 已記 path 與例外類別名）：避免 Tomcat／Spring 印出含後端回應本文與完整 URL 的堆疊
//           回應本文不帶後端內容；前端 catch 後統一提示「查詢失敗」
//           共用元件，複製後不要改
// ============================================================

import java.util.Collections;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ForwardExceptionHandler {

	static final String MESSAGE = "後端服務呼叫失敗";

	@ExceptionHandler(ForwardException.class)
	public ResponseEntity<Map<String, String>> handle(ForwardException e) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.singletonMap("message", MESSAGE));
	}
}
