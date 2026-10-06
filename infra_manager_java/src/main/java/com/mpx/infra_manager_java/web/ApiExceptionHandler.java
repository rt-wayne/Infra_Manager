package com.mpx.infra_manager_java.web;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-05
// 變更說明: 新增：REST 例外統一回應（S1）
//           TextTooLongException → 400（帶欄位、上限、實際字數）；
//           請求本文過大（BodyTooLargeException，含被 Jackson 包進 HttpMessageNotReadableException 的情況）與
//           附件超過 MultipartConfigElement 上限（MaxUploadSizeExceededException）→ 413；
//           其他 HttpMessageNotReadableException → 400；
//           DB 連線／SQL 例外 → 500 固定訊息，log 只記例外類別名與 ORA 碼，不記訊息（可能含主機）、不記 SQL 參數值。
//           2026-10-06 code review：交易例外（TransactionException，如取連線失敗、commit 失敗）併入 500 DB 固定訊息；
//           其他未預期例外 → 500 固定訊息，log 只記類別名與 method/path，避免 Tomcat 預設把完整訊息寫進 log。
//           2026-10-06 複審（③A、②B）：有全域 advice 時 Spring MVC 自己的 404／405／406／415／缺參數 400 例外也會進
//           catch-all，之前全被吞成 500。改為：實作 ErrorResponse 的例外照其狀態碼回固定訊息、不記 error；
//           TypeMismatchException → 400；客戶端斷線（AsyncRequestNotUsableException）只記 debug；
//           真正未預期的例外才記 error，並加記堆疊前 10 個 frame（類別.方法:行號，不含訊息）方便定位。
// ============================================================

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import jakarta.servlet.http.HttpServletRequest;

import com.mpx.common.db.DbConnectException;
import com.mpx.infra_manager_java.util.TextTooLongException;

@RestControllerAdvice
public class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(TextTooLongException.class)
	public ResponseEntity<Map<String, Object>> tooLong(TextTooLongException e) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("message", e.getMessage());
		body.put("field", e.getField());
		body.put("max", e.getMax());
		body.put("actual", e.getActual());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
	}

	@ExceptionHandler({ BodyTooLargeException.class, MaxUploadSizeExceededException.class })
	public ResponseEntity<Map<String, Object>> tooLarge(Exception e) {
		return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(message("請求內容過大"));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Map<String, Object>> notReadable(HttpMessageNotReadableException e) {
		if (hasCause(e, BodyTooLargeException.class)) {
			return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(message("請求內容過大"));
		}
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message("請求格式錯誤"));
	}

	@ExceptionHandler({ DbConnectException.class, DataAccessException.class, TransactionException.class })
	public ResponseEntity<Map<String, Object>> dbError(RuntimeException e) {
		log.error("資料庫存取失敗 {}", summarize(e));
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(message("資料庫存取失敗"));
	}

	/** 堆疊最多記幾個 frame */
	static final int STACK_FRAMES = 10;

	/**
	 * 最後防線。先把 Spring MVC 自己帶狀態碼的例外放行（NoResourceFoundException 404、HttpRequestMethodNotSupportedException 405、
	 * HttpMediaTypeNotAcceptableException 406、HttpMediaTypeNotSupportedException 415、MissingServletRequestParameterException 400、
	 * ResponseStatusException 等都實作 ErrorResponse），照其狀態碼回固定訊息、不記 error；
	 * 參數型別轉換失敗 → 400；客戶端已斷線 → 只記 debug、交回 Spring 處理（回應已寫不出去）；
	 * 其餘才是真正未預期的例外 → 500 固定訊息，log 記類別名、method/path 與堆疊前 {@value #STACK_FRAMES} 個 frame，不記訊息
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, Object>> unexpected(Exception e, HttpServletRequest request) {
		if (e instanceof ErrorResponse er) {
			return ResponseEntity.status(er.getStatusCode()).body(message("請求無法處理"));
		}
		if (e instanceof TypeMismatchException) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message("請求格式錯誤"));
		}
		if (e instanceof AsyncRequestNotUsableException) {
			log.debug("客戶端已斷線 {} {}", request.getMethod(), request.getRequestURI());
			return null;
		}
		log.error("未預期例外 {} {} {}{}", e.getClass().getSimpleName(), request.getMethod(), request.getRequestURI(),
				frames(e, STACK_FRAMES));
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(message("系統發生錯誤"));
	}

	/** 堆疊前 max 個 frame，只有「類別.方法:行號」，沒有例外訊息 */
	static String frames(Throwable e, int max) {
		StackTraceElement[] stack = e.getStackTrace();
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < stack.length && i < max; i++) {
			sb.append(System.lineSeparator()).append("    at ").append(stack[i].getClassName()).append('.')
					.append(stack[i].getMethodName()).append(':').append(stack[i].getLineNumber());
		}
		return sb.toString();
	}

	static Map<String, Object> message(String text) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("message", text);
		return body;
	}

	static boolean hasCause(Throwable e, Class<? extends Throwable> type) {
		int depth = 0;
		for (Throwable t = e; t != null && depth < 32; t = t.getCause(), depth++) {
			if (type.isInstance(t)) {
				return true;
			}
			if (t.getCause() == t) {
				break;
			}
		}
		return false;
	}

	/** 例外類別名 + 鏈中第一個 SQLException 的錯誤碼；不帶訊息 */
	static String summarize(Throwable e) {
		StringBuilder sb = new StringBuilder(e.getClass().getSimpleName());
		int depth = 0;
		for (Throwable t = e.getCause(); t != null && depth < 32; t = t.getCause(), depth++) {
			if (t instanceof SQLException sqlEx && sqlEx.getErrorCode() > 0) {
				sb.append(" / ").append(t.getClass().getSimpleName()).append(" code=").append(sqlEx.getErrorCode());
				break;
			}
			if (t.getCause() == t) {
				break;
			}
		}
		return sb.toString();
	}
}
