package com.mpx.infra_manager_web.controller;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：殼 jar 自建轉發器（BACKLOG 第 78 項，使用者裁示 ①B，取代範本 com.mpx.common.web.ApiForwarder）
//           /api/v1/** 的 GET／HEAD／POST／PUT／PATCH／DELETE → host.properties 的 backend.api.domain.path + 子路徑 + 查詢字串
//           （OPTIONS 由 Spring MVC 自己回 Allow、不轉；其他 method 由 Spring MVC 回 405）
//           請求：本文與 Content-Type／Accept 原樣轉（本文直接寫出、不經轉換器，瀏覽器沒送 Content-Type 就不補）；
//                cookie 只帶 IM_ 開頭的；X-IM-XSRF 原樣轉；附 X-Forwarded-For（取連線來源 IP，不信任瀏覽器送來的同名 header）
//           回應：後端原狀態碼（含 3xx／4xx／5xx）、本文、Content-Type、IM_ 開頭的 Set-Cookie；其他 header（含 Location）不轉；
//                後端有本文卻沒給 Content-Type 時標 application/octet-stream，不照 Accept 猜
//           400 固定訊息「請求格式錯誤」：子路徑含 .／..／;／\／編碼過的斜線／中間空段（//）、不是 /api/v1 開頭、
//                查詢字串或 Content-Type 格式不合法、組出的目標不在 host.properties 指定的 scheme／host／port／路徑前綴之下
//                （防路徑跳脫與 SSRF）；結尾斜線放行。部分格式（原始 \、%2F、%5C、%00、跳出根目錄的 ..）Tomcat 會先回它自己的 400
//           502 固定訊息「後端服務呼叫失敗」：後端位址未設定或格式不合法（只接受 http／https）、連不上、逾時、I/O 失敗
//                （範本原為 500；改 502 讓前端能分辨「殼 jar 自己壞」與「後端不通」）
//           不解析本文（byte[] 進、byte[] 出；有本文時補 Content-Length，讓後端能在讀本文前就判 413）、不開 CrossOrigin
//                例外：multipart/* 本文會先被 Spring 的 multipart 解析器讀走，轉到後端是空本文——首版不支援上傳，見 BACKLOG 第 79 項
//           log 只記 method、路徑、來源 IP、狀態或失敗例外鏈的類別名（不記查詢字串、本文、cookie、後端位址）
//           S1 的 HealthController 併入本類別（/api/v1/health 走同一條路）
//           2026-10-06 複審修正：路徑跳脫與 SSRF 防護、400 與 502 分開、log 記根因與來源 IP、本文與 Content-Type 真正原樣、
//                base URL 去尾斜線並在建構時驗證、Cookie 讀全部 header、method 改為明列
//           2026-10-06 第二輪複審修正：轉發本文補 Content-Length（原本一律 chunked）、後端位址限 http／https（ftp:// 原會變 500）、
//                502 的 log 改記整條例外鏈（原只記最底層，ConnectException 會被顯示成 ClosedChannelException）
// ============================================================

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriUtils;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping(ApiProxyController.PREFIX)
public class ApiProxyController {

	static final String PREFIX = "/api/v1";
	static final String COOKIE_PREFIX = "IM_";
	static final String XSRF_HEADER = "X-IM-XSRF";
	static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
	static final String MESSAGE = "後端服務呼叫失敗";
	static final String BAD_REQUEST_MESSAGE = "請求格式錯誤";

	private static final Logger log = LoggerFactory.getLogger(ApiProxyController.class);
	private static final MediaType JSON_UTF8 = new MediaType("application", "json", StandardCharsets.UTF_8);
	private static final byte[] FAILURE_BODY = jsonMessage(MESSAGE);
	private static final byte[] BAD_REQUEST_BODY = jsonMessage(BAD_REQUEST_MESSAGE);

	private final RestClient restClient;
	/** 已去尾斜線的後端位址；未設定或格式不合法時為 null，呼叫時回 502 */
	private final URI base;

	public ApiProxyController(RestClient backendRestClient, @Value("${backend.api.domain.path:}") String baseUrl) {
		this.restClient = backendRestClient;
		this.base = parseBase(baseUrl);
		if (this.base == null) {
			log.warn("backend.api.domain.path 未設定或格式不合法（需為 http(s)://主機[:port][/路徑]，主機名不可含底線），/api/v1/** 會一律回 502");
		}
	}

	@RequestMapping(path = "/**", method = { RequestMethod.GET, RequestMethod.HEAD, RequestMethod.POST,
			RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE })
	public ResponseEntity<byte[]> proxy(HttpServletRequest request) throws IOException {
		HttpMethod method = HttpMethod.valueOf(request.getMethod());
		String remoteAddr = request.getRemoteAddr();
		String path = pathWithinPrefix(request);
		if (path == null) {
			return badRequest(method, request.getRequestURI(), remoteAddr, "path");
		}
		String contentType = request.getHeader(HttpHeaders.CONTENT_TYPE);
		if (contentType != null && !isValidMediaType(contentType)) {
			return badRequest(method, path, remoteAddr, "content-type");
		}
		if (base == null) {
			log.warn("forward {} {} from {} failed: backend not configured", method, path, remoteAddr);
			return badGateway();
		}
		URI target = buildTarget(path, request.getQueryString());
		if (target == null) {
			return badRequest(method, path, remoteAddr, "uri");
		}

		byte[] body = request.getInputStream().readAllBytes();
		try {
			RestClient.RequestBodySpec spec = restClient.method(method).uri(target)
					.headers(h -> copyRequestHeaders(request, h));
			if (body.length > 0) {
				// 帶長度：JDK 用戶端才會送 Content-Length 而非 chunked，後端的 Content-Length 快速 413 才用得到
				spec.headers(h -> h.setContentLength(body.length));
				// 直接寫出，不經 HttpMessageConverter（它會在沒有 Content-Type 時補 application/octet-stream）
				spec.body(out -> out.write(body));
			}
			ResponseEntity<byte[]> resp = spec.exchange((req, res) -> toResponse(res.getStatusCode(), res.getHeaders(), res.getBody().readAllBytes()));
			log.info("forward {} {} from {} -> {}", method, path, remoteAddr, resp.getStatusCode().value());
			return resp;
		} catch (RestClientException e) {
			log.warn("forward {} {} from {} failed: {}", method, path, remoteAddr, causeChain(e));
			return badGateway();
		}
	}

	private static ResponseEntity<byte[]> toResponse(HttpStatusCode status, HttpHeaders in, byte[] body) {
		HttpHeaders out = new HttpHeaders();
		String contentType = in.getFirst(HttpHeaders.CONTENT_TYPE);
		if (contentType != null) {
			out.set(HttpHeaders.CONTENT_TYPE, contentType);
		} else if (body.length > 0) {
			out.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE);
		}
		for (String cookie : in.getOrEmpty(HttpHeaders.SET_COOKIE)) {
			if (cookie.startsWith(COOKIE_PREFIX)) {
				out.add(HttpHeaders.SET_COOKIE, cookie);
			}
		}
		ResponseEntity.BodyBuilder builder = ResponseEntity.status(status).headers(out);
		return body.length > 0 ? builder.body(body) : builder.build();
	}

	private static ResponseEntity<byte[]> badRequest(HttpMethod method, String path, String remoteAddr, String reason) {
		log.warn("reject {} {} from {}: {}", method, path, remoteAddr, reason);
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(JSON_UTF8).body(BAD_REQUEST_BODY);
	}

	private static ResponseEntity<byte[]> badGateway() {
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY).contentType(JSON_UTF8).body(FAILURE_BODY);
	}

	/**
	 * 去尾斜線後解析；必須是 http／https 的絕對位址（有 host）且不含 userinfo／查詢／fragment，否則回 null。
	 * 其他 scheme 不收：JDK HttpClient 對非 http(s) 丟 IllegalArgumentException，不是 IOException，會變成 500 而非 502。
	 * 主機名含底線時 java.net.URI 的 getHost() 回 null，也會落到這裡回 null。
	 */
	static URI parseBase(String baseUrl) {
		String s = baseUrl == null ? "" : baseUrl.trim();
		while (s.endsWith("/")) {
			s = s.substring(0, s.length() - 1);
		}
		if (s.isEmpty()) {
			return null;
		}
		try {
			URI u = URI.create(s);
			if (!isHttpScheme(u.getScheme()) || u.getHost() == null || u.getRawUserInfo() != null
					|| u.getRawQuery() != null || u.getRawFragment() != null) {
				return null;
			}
			return u;
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/**
	 * 去掉 context path 與 /api/v1 後的子路徑（原始、未解碼）。
	 * 不是 /api/v1 開頭、或任一段解碼後是 .／..、含 ;／\／斜線（即 %2F／%5C）、或中間有空段時回 null。
	 */
	static String pathWithinPrefix(HttpServletRequest request) {
		String uri = request.getRequestURI();
		String contextPath = request.getContextPath();
		if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
			uri = uri.substring(contextPath.length());
		}
		if (!uri.startsWith(PREFIX)) {
			return null;
		}
		String path = uri.substring(PREFIX.length());
		if (path.isEmpty()) {
			return path;
		}
		if (!path.startsWith("/")) {
			return null;
		}
		String[] segments = path.split("/", -1);
		for (int i = 1; i < segments.length; i++) {
			String raw = segments[i];
			if (raw.isEmpty()) {
				if (i == segments.length - 1) {
					continue; // 允許結尾斜線
				}
				return null;
			}
			if (raw.indexOf(';') >= 0 || raw.indexOf('\\') >= 0) {
				return null;
			}
			String decoded;
			try {
				decoded = UriUtils.decode(raw, StandardCharsets.UTF_8);
			} catch (IllegalArgumentException e) {
				return null;
			}
			if (decoded.equals(".") || decoded.equals("..") || decoded.indexOf('/') >= 0 || decoded.indexOf('\\') >= 0) {
				return null;
			}
		}
		return path;
	}

	/** 組出目標並確認仍在 base 之下（scheme／host／port 相同、路徑以 base 路徑開頭、無 userinfo），否則回 null */
	private URI buildTarget(String path, String queryString) {
		try {
			URI target = URI.create(base + path + (queryString == null ? "" : "?" + queryString));
			boolean sameOrigin = base.getScheme().equalsIgnoreCase(target.getScheme())
					&& base.getHost().equalsIgnoreCase(target.getHost())
					&& base.getPort() == target.getPort()
					&& target.getRawUserInfo() == null
					&& target.getRawPath() != null
					&& target.getRawPath().startsWith(base.getRawPath());
			return sameOrigin ? target : null;
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static boolean isValidMediaType(String contentType) {
		try {
			MediaType.parseMediaType(contentType);
			return true;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	private static void copyRequestHeaders(HttpServletRequest request, HttpHeaders h) {
		String contentType = request.getHeader(HttpHeaders.CONTENT_TYPE);
		if (contentType != null) {
			h.set(HttpHeaders.CONTENT_TYPE, contentType);
		}
		String accept = request.getHeader(HttpHeaders.ACCEPT);
		if (accept != null) {
			h.set(HttpHeaders.ACCEPT, accept);
		}
		String xsrf = request.getHeader(XSRF_HEADER);
		if (xsrf != null) {
			h.set(XSRF_HEADER, xsrf);
		}
		String cookies = filterCookies(request.getHeaders(HttpHeaders.COOKIE) == null
				? Collections.<String>emptyList()
				: Collections.list(request.getHeaders(HttpHeaders.COOKIE)));
		if (!cookies.isEmpty()) {
			h.set(HttpHeaders.COOKIE, cookies);
		}
		h.set(FORWARDED_FOR_HEADER, request.getRemoteAddr());
	}

	/** 多個 Cookie header 一起看，只留 IM_ 開頭的，合併成一個 header 值 */
	static String filterCookies(List<String> cookieHeaders) {
		List<String> kept = new ArrayList<>();
		for (String header : cookieHeaders) {
			if (header == null) {
				continue;
			}
			for (String part : header.split(";")) {
				String cookie = part.trim();
				if (cookie.startsWith(COOKIE_PREFIX)) {
					kept.add(cookie);
				}
			}
		}
		return String.join("; ", kept);
	}

	private static boolean isHttpScheme(String scheme) {
		return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
	}

	/**
	 * 失敗原因的類別名，由外往內以 " <- " 串起（最多 8 層、不含 Spring 的包裝層），
	 * 例：ConnectException <- ClosedChannelException。只記最底層會把「後端沒在聽」顯示成「連線被中途關閉」
	 */
	static String causeChain(Throwable wrapper) {
		Throwable cur = wrapper.getCause() != null ? wrapper.getCause() : wrapper;
		StringBuilder sb = new StringBuilder(cur.getClass().getSimpleName());
		for (int depth = 1; depth < 8 && cur.getCause() != null && cur.getCause() != cur; depth++) {
			cur = cur.getCause();
			sb.append(" <- ").append(cur.getClass().getSimpleName());
		}
		return sb.toString();
	}

	private static byte[] jsonMessage(String message) {
		return ("{\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
	}
}
