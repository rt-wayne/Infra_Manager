package com.mpx.im_acct_insp_web.controller;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：im_acct_insp 殼 jar 共用轉送（開發文件 spec_im_acct_insp.md 第 5 節）
//           變更單 #2（2026-10-07，Claude Fable 5.1）：host.properties 組合 key 依使用者命名改讀 im.acct.insp.domain.path
//           照 README 3.1：本文以 byte[] 原樣轉送、不解析 payload；回傳去掉 Content-Length／Transfer-Encoding／Connection
//           額外：瀏覽器送來的 X-User-Id 轉給後端（範本不自動帶）
//           後端回 4xx／5xx（RestClientResponseException）時，把後端狀態碼、本文原樣回頁面，讓前端顯示 {message}；
//           連不上等其他例外記 log 後 rethrow（Spring 預設 500）
//           SA v0.5 改版（2026-10-07，Claude Opus 5.5，開發文件 spec_im_acct_insp_v2.0.md 第 7.1 節）：
//           不再轉 X-User-Id，改為瀏覽器送來的 Authorization 原樣轉送（沒有就不帶；不驗、不記 log）
//           POST 不再強制標 JSON：Content-Type 照請求原值（含 multipart 的 boundary），本文自行從請求串流讀成 byte[]
//           本文超過 11 MB（11×1024×1024 byte）直接回 413 E15、不轉送（Content-Length 已超過時不讀本文）
//           GET／POST 查詢字串一律原樣轉送；二進位下載的 Content-Type、Content-Disposition 隨 header 照抄回傳
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 1：後端位址組合 key 改讀 infra.manager.domain.path（原 im.acct.insp.domain.path；專案改名 infra_manager）
// ============================================================

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

/**
 * 各 Controller 共用的後端呼叫：組後端 URL、原樣帶 Authorization／Content-Type、byte[] 原樣送出與回傳。
 * 不解析 payload、不驗 token；後端位址只讀 host.properties 的組合 key {@code infra.manager.domain.path}
 * （變更單 #2：key 名稱由使用者訂，原範本的 backend.api.domain.path 已改名；2026-10-16 隨專案改名由 im.acct.insp.domain.path 改為現名）。
 * 殼 jar 的 multipart 解析已關閉（application.properties 的 spring.servlet.multipart.enabled=false），
 * 所以 multipart 本文會完整留在請求串流裡，由這裡原樣轉給後端。
 */
@PropertySource("classpath:config/host.properties")
@Component
public class BackendForwarder {

	/** 請求本文上限（開發文件 7.1：整體 11 MB，與後端 max-request-size 一致）；超過回 413 E15 */
	public static final int MAX_BODY_BYTES = 11 * 1024 * 1024;

	/** 413 回應本文（開發文件 4.15 E15） */
	static final String E15_BODY = "{\"message\":\"附件大小不可超過 10 MB\"}";

	private static final Logger log = LoggerFactory.getLogger(BackendForwarder.class);

	/** com.mpx.common.web.RestTemplateConfig 提供（connect 5 秒、read 120 秒） */
	private final RestTemplate restTemplate;

	/** 後端 API 根位址：host.properties 的組合 key（domain＋port＋path），不得寫死在程式 */
	private final String backendApi;

	public BackendForwarder(RestTemplate restTemplate, @Value("${infra.manager.domain.path}") String backendApi) {
		this.restTemplate = restTemplate;
		this.backendApi = backendApi;
	}

	/** 路徑參數（單號、項目代碼）編碼成單一 path segment 後再接進後端路徑 */
	public static String seg(String value) {
		return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
	}

	/** GET <backend>/path?query；query 為瀏覽器送來的原始查詢字串（已編碼），原樣接上 */
	public ResponseEntity<byte[]> get(String path, HttpServletRequest request) {
		return forward(HttpMethod.GET, path, request, null);
	}

	/**
	 * POST <backend>/path?query：本文從請求串流原樣讀出（JSON 或 multipart 都不解析），Content-Type 照請求原值。
	 * 本文超過 {@link #MAX_BODY_BYTES} 時回 413 E15、不呼叫後端。
	 */
	public ResponseEntity<byte[]> post(String path, HttpServletRequest request) {
		if (request.getContentLengthLong() > MAX_BODY_BYTES) {
			// 宣告長度已超過：不讀本文、直接回 413
			return tooLarge(path);
		}
		byte[] body;
		try {
			body = readLimited(request.getInputStream());
		} catch (IOException e) {
			// 讀瀏覽器本文失敗（中斷上傳等）：記 log 後往上拋，Spring 預設 500
			log.error("POST {} read body failed: {}", path, e.getClass().getName());
			throw new UncheckedIOException(e);
		}
		if (body == null) {
			// 未宣告長度（chunked）但實際超過上限
			return tooLarge(path);
		}
		return forward(HttpMethod.POST, path, request, body.length == 0 ? null : body);
	}

	/** 讀到上限＋1 byte 為止；超過上限回 null（不把整個超大本文讀進記憶體） */
	private static byte[] readLimited(InputStream in) throws IOException {
		byte[] data = in.readNBytes(MAX_BODY_BYTES + 1);
		return data.length > MAX_BODY_BYTES ? null : data;
	}

	private static ResponseEntity<byte[]> tooLarge(String path) {
		log.warn("POST {} body too large, status 413", path);
		return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
				.contentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8))
				.body(E15_BODY.getBytes(StandardCharsets.UTF_8));
	}

	private ResponseEntity<byte[]> forward(HttpMethod method, String path, HttpServletRequest request, byte[] body) {
		log.info("{} {}", method, path);
		HttpHeaders reqHeaders = new HttpHeaders();
		// Authorization 原樣轉送（開發文件 2.1、7.1）；殼 jar 不驗 token，也不寫進 log
		String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (authorization != null && !authorization.isEmpty()) {
			reqHeaders.set(HttpHeaders.AUTHORIZATION, authorization);
		}
		// Content-Type 以原字串照抄（multipart 的 boundary 一字不改）；沒有本文時不帶
		String contentType = request.getContentType();
		if (body != null && contentType != null && !contentType.isEmpty()) {
			reqHeaders.set(HttpHeaders.CONTENT_TYPE, contentType);
		}
		String query = request.getQueryString();
		try {
			// 用 URI 物件：查詢字串已是瀏覽器編碼過的，交給 String 版 exchange 會再編一次（% 變 %25）
			URI uri = URI.create(backendApi + path + (query == null || query.isEmpty() ? "" : "?" + query));
			ResponseEntity<byte[]> resp = restTemplate.exchange(uri, method, new HttpEntity<>(body, reqHeaders),
					byte[].class);
			return passThrough(resp.getStatusCode(), resp.getHeaders(), resp.getBody());
		} catch (RestClientResponseException e) {
			// 後端有回應但非 2xx（400／401／403／404／409／413／500）：狀態碼與本文原樣回頁面，前端顯示後端的 message
			// 只記方法、路徑、狀態碼與例外類別，不記 payload、Authorization
			log.warn("{} {} backend status {}: {}", method, path, e.getStatusCode().value(), e.getClass().getName());
			return passThrough(e.getStatusCode(), e.getResponseHeaders(), e.getResponseBodyAsByteArray());
		} catch (RuntimeException e) {
			// 連不上、逾時等：記 log 後原樣往上拋，由 Spring 預設處理成 500；前端 catch 後統一 toast
			log.error("{} {} failed: {}", method, path, e.getClass().getName());
			throw e;
		}
	}

	/**
	 * 後端回應原樣回頁面：狀態碼、本文 byte 照抄；header 照抄但去掉長度／連線類，由殼 jar 依實際本文重算。
	 * 照抄 Content-Length 會在長度對不上時讓呼叫端一直等到逾時，不可省略這一步。
	 * 二進位下載的 Content-Type、Content-Disposition 也在照抄範圍內。
	 */
	private static ResponseEntity<byte[]> passThrough(HttpStatusCode status, HttpHeaders headers, byte[] body) {
		HttpHeaders h = headers == null ? new HttpHeaders() : HttpHeaders.copyOf(headers);
		h.remove(HttpHeaders.CONTENT_LENGTH);
		h.remove(HttpHeaders.TRANSFER_ENCODING);
		h.remove(HttpHeaders.CONNECTION);
		return ResponseEntity.status(status).headers(h).body(body);
	}
}
