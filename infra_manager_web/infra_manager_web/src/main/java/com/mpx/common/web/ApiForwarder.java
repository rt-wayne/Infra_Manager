package com.mpx.common.web;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-05
// 變更說明: 新增：前端殼 jar 的轉發元件（規格 v4），比照 store_web_barcode 的 Controller 轉發寫法
//           轉發目標＝host.properties 的 backend.api.domain.path（由 domain＋port＋path 組成）＋ path
//           轉發層不解析 payload（Object.class）：後端回應欄位變動時本檔與 Controller 都不需改
//           log 只記 path、HTTP 狀態、例外類別名（不記 payload、不記完整 URL）
//           後端回 4xx／5xx 或連不上時 RestTemplate 會丟例外：log 後包成 ForwardException 拋出，由 ForwardExceptionHandler 回 500 固定訊息，
//           前端 catch 後統一提示「查詢失敗」
//           刻意不開 CrossOrigin：頁面與殼 jar 同源，無跨域需求
//           審查修正：回傳只帶狀態碼與本文、不轉發後端 header；例外由 ForwardExceptionHandler 統一轉成 500 固定訊息
//           複審修正：RestClientException 與 IllegalArgumentException（例：未設定後端位址）包成 ForwardException 後拋出
//           jdk25 階段 3（規格 D-36）：getStatusCodeValue／getRawStatusCode 改 getStatusCode().value()（Framework 7 已移除）；邏輯不變
//           共用元件，複製後不要改
// ============================================================

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
@PropertySource("classpath:config/host.properties")
public class ApiForwarder {

	private static final Logger log = LoggerFactory.getLogger(ApiForwarder.class);

	private final RestTemplate restTemplate;
	private final String baseUrl;

	public ApiForwarder(RestTemplate restTemplate, @Value("${backend.api.domain.path:}") String baseUrl) {
		this.restTemplate = restTemplate;
		this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
	}

	/** POST 轉發：body 原樣送出，回應原樣回傳 */
	public ResponseEntity<Object> post(String path, Object body) {
		try {
			ResponseEntity<Object> resp = restTemplate.postForEntity(baseUrl + path, body, Object.class);
			log.info("POST {} -> HTTP {}", path, resp.getStatusCode().value());
			return statusAndBody(resp);
		} catch (RestClientException | IllegalArgumentException e) {
			logFailure("POST", path, e);
			throw new ForwardException("POST", path, e);
		}
	}

	/** GET 轉發：回應原樣回傳 */
	public ResponseEntity<Object> get(String path) {
		try {
			ResponseEntity<Object> resp = restTemplate.getForEntity(baseUrl + path, Object.class);
			log.info("GET {} -> HTTP {}", path, resp.getStatusCode().value());
			return statusAndBody(resp);
		} catch (RestClientException | IllegalArgumentException e) {
			logFailure("GET", path, e);
			throw new ForwardException("GET", path, e);
		}
	}

	/** 只帶狀態碼與本文，不轉發後端 header（Content-Length、Transfer-Encoding 等由本殼 jar 重新產生） */
	private static ResponseEntity<Object> statusAndBody(ResponseEntity<Object> resp) {
		return ResponseEntity.status(resp.getStatusCode().value()).body(resp.getBody());
	}

	private static void logFailure(String method, String path, RuntimeException e) {
		if (e instanceof HttpStatusCodeException) {
			log.error("forward failed {} {} -> HTTP {} : {}", method, path,
					((HttpStatusCodeException) e).getStatusCode().value(), e.getClass().getSimpleName());
		} else {
			log.error("forward failed {} {} : {}", method, path, e.getClass().getSimpleName());
		}
	}
}
