package com.mpx.im_acct_insp.common;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：統一錯誤回應 { "message": "…" }（spec §4 通則：400／401／403／404／409／500）
//           ApiException 依自帶狀態碼回；JSON 格式錯、參數型別錯回 400；
//           Spring 內建的 ErrorResponse 類例外（路徑不存在 404、方法不符 405 等）沿用其狀態碼；
//           其他例外記 error log 後回 500，訊息不帶例外內容（不外洩 SQL／連線資訊）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（spec_im_acct_insp_v2.0.md §6.1、§4.10）：附件超過 multipart 上限（MaxUploadSizeExceededException）回 413 E15；
//           其他 multipart 解析失敗回 400；缺 part（MissingServletRequestPartException）屬 ErrorResponse 照回 400。
//           500 的 error log 只記例外類別與堆疊，不記請求本文與參數
// ============================================================

import java.util.Collections;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	static final String MSG_BAD_JSON = "請求內容格式錯誤";
	static final String MSG_BAD_PARAM = "參數格式錯誤";
	static final String MSG_NOT_FOUND = "查無此功能";
	static final String MSG_BAD_REQUEST = "請求不正確";
	static final String MSG_INTERNAL = "系統發生錯誤，請稍後再試或聯絡系統管理員";

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<Map<String, String>> handleApi(ApiException ex) {
		if (ex.getStatus().is5xxServerError()) {
			log.error("業務處理失敗：{}", ex.getMessage(), ex);
		}
		return body(ex.getStatus(), ex.getMessage());
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<Map<String, String>> handleMaxUpload(MaxUploadSizeExceededException ex) {
		return body(HttpStatus.CONTENT_TOO_LARGE, AcctMsg.E15);
	}

	@ExceptionHandler(MultipartException.class)
	public ResponseEntity<Map<String, String>> handleMultipart(MultipartException ex) {
		return body(HttpStatus.BAD_REQUEST, MSG_BAD_REQUEST);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Map<String, String>> handleNotReadable(HttpMessageNotReadableException ex) {
		return body(HttpStatus.BAD_REQUEST, MSG_BAD_JSON);
	}

	@ExceptionHandler(TypeMismatchException.class)
	public ResponseEntity<Map<String, String>> handleTypeMismatch(TypeMismatchException ex) {
		return body(HttpStatus.BAD_REQUEST, MSG_BAD_PARAM);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, String>> handleOther(Exception ex) {
		if (ex instanceof ErrorResponse er) {
			HttpStatusCode code = er.getStatusCode();
			if (code.is4xxClientError()) {
				return body(code, code.value() == 404 ? MSG_NOT_FOUND : MSG_BAD_REQUEST);
			}
		}
		log.error("未預期的錯誤", ex);
		return body(HttpStatus.INTERNAL_SERVER_ERROR, MSG_INTERNAL);
	}

	private static ResponseEntity<Map<String, String>> body(HttpStatusCode status, String message) {
		return ResponseEntity.status(status).body(Collections.singletonMap("message", message));
	}
}
