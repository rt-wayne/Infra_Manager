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
//           2026-10-07 S6 回合一（裁示 ①A ③A）：請求與回應改全程串流（不再整包讀進記憶體），multipart 本文改能原樣轉到後端
//                （解析器由 NoMultipartConfig 關掉，取代上方「首版不支援上傳」的例外）；
//                本文上限：非 multipart 1 MB、multipart 51 MB，Content-Length 超過直接 413 不呼叫後端，沒帶長度的邊轉邊計數；
//                回應 header 白名單加 Content-Length／Content-Disposition／X-Content-Type-Options／Cache-Control（亦完成第 83 項 ②）；
//                回應已開始寫才斷線時不能改回 502，改丟例外讓連線中斷，瀏覽器才看得出檔案不完整
// ============================================================

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping(ApiProxyController.PREFIX)
public class ApiProxyController {

	static final String PREFIX = "/api/v1";
	static final String COOKIE_PREFIX = "IM_";
	static final String XSRF_HEADER = "X-IM-XSRF";
	static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
	static final String MESSAGE = "後端服務呼叫失敗";
	static final String BAD_REQUEST_MESSAGE = "請求格式錯誤";
	static final String TOO_LARGE_MESSAGE = "請求內容過大";
	static final long MAX_BODY = 1L << 20;
	static final long MAX_MULTIPART_BODY = 51L << 20;
	static final List<String> PASS_RESPONSE_HEADERS = List.of(HttpHeaders.CONTENT_DISPOSITION,
			"X-Content-Type-Options", HttpHeaders.CACHE_CONTROL);

	private static final Logger log = LoggerFactory.getLogger(ApiProxyController.class);
	private static final int BUFFER_SIZE = 64 * 1024;
	private static final MediaType JSON_UTF8 = new MediaType("application", "json", StandardCharsets.UTF_8);
	private static final byte[] FAILURE_BODY = jsonMessage(MESSAGE);
	private static final byte[] BAD_REQUEST_BODY = jsonMessage(BAD_REQUEST_MESSAGE);
	private static final byte[] TOO_LARGE_BODY = jsonMessage(TOO_LARGE_MESSAGE);

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
	public void proxy(HttpServletRequest request, HttpServletResponse response) throws IOException {
		HttpMethod method = HttpMethod.valueOf(request.getMethod());
		String remoteAddr = request.getRemoteAddr();
		String path = pathWithinPrefix(request);
		if (path == null) {
			badRequest(response, method, request.getRequestURI(), remoteAddr, "path");
			return;
		}
		String contentType = request.getHeader(HttpHeaders.CONTENT_TYPE);
		if (contentType != null && !isValidMediaType(contentType)) {
			badRequest(response, method, path, remoteAddr, "content-type");
			return;
		}
		if (base == null) {
			log.warn("forward {} {} from {} failed: backend not configured", method, path, remoteAddr);
			writeJson(response, HttpStatus.BAD_GATEWAY, FAILURE_BODY);
			return;
		}
		URI target = buildTarget(path, request.getQueryString());
		if (target == null) {
			badRequest(response, method, path, remoteAddr, "uri");
			return;
		}
		long limit = isMultipart(contentType) ? MAX_MULTIPART_BODY : MAX_BODY;
		long declared = request.getContentLengthLong();
		if (declared > limit) {
			tooLarge(response, method, path, remoteAddr);
			return;
		}

		boolean hasBody = declared > 0 || (declared < 0 && request.getHeader(HttpHeaders.TRANSFER_ENCODING) != null);
		Forward state = new Forward(limit);
		try {
			RestClient.RequestBodySpec spec = restClient.method(method).uri(target)
					.headers(h -> copyRequestHeaders(request, h));
			if (hasBody) {
				if (declared > 0) {
					// 帶長度：JDK 用戶端才會送 Content-Length 而非 chunked，後端的 Content-Length 快速 413 才用得到
					spec.headers(h -> h.setContentLength(declared));
				}
				// 直接寫出，不經 HttpMessageConverter（它會在沒有 Content-Type 時補 application/octet-stream）
				spec.body(out -> state.copyRequestBody(request.getInputStream(), out));
			}
			Integer status = spec.exchange((req, res) -> state.tooLarge ? null : state.writeResponse(res, response));
			if (status == null) {
				tooLarge(response, method, path, remoteAddr);
				return;
			}
			log.info("forward {} {} from {} -> {}", method, path, remoteAddr, status);
		} catch (RestClientException e) {
			if (state.tooLarge) {
				tooLarge(response, method, path, remoteAddr);
			} else if (state.clientGone) {
				log.info("forward {} {} from {} aborted by client", method, path, remoteAddr);
			} else if (state.responseStarted) {
				// 已開始回後端的狀態與 header：改不成 502，往外丟讓 Tomcat 中斷連線，瀏覽器才知道檔案不完整
				// 不丟原例外：它的訊息含後端位址，Tomcat 會整段印進 ERROR log
				log.warn("forward {} {} from {} broken mid-response: {}", method, path, remoteAddr, causeChain(e));
				throw new BrokenResponseException();
			} else {
				log.warn("forward {} {} from {} failed: {}", method, path, remoteAddr, causeChain(e));
				writeJson(response, HttpStatus.BAD_GATEWAY, FAILURE_BODY);
			}
		}
	}

	/** 一次轉發的進度：請求本文計數、是否已開始寫回應、瀏覽器是否已斷線 */
	private static final class Forward {

		private final long limit;
		private long count;
		boolean tooLarge;
		boolean responseStarted;
		boolean clientGone;

		Forward(long limit) {
			this.limit = limit;
		}

		/** 邊讀邊轉；累計超過上限就中止（沒有 Content-Length 的 chunked 本文靠這裡擋） */
		void copyRequestBody(InputStream in, OutputStream out) throws IOException {
			byte[] buf = new byte[BUFFER_SIZE];
			int n;
			while ((n = in.read(buf)) != -1) {
				count += n;
				if (count > limit) {
					tooLarge = true;
					throw new IOException("request body over limit");
				}
				out.write(buf, 0, n);
			}
		}

		/** 寫狀態、白名單 header，再把後端本文串流給瀏覽器；回後端狀態碼 */
		Integer writeResponse(ClientHttpResponse res, HttpServletResponse out) throws IOException {
			HttpHeaders in = res.getHeaders();
			InputStream body = res.getBody();
			int first = body.read();
			responseStarted = true;
			out.setStatus(res.getStatusCode().value());
			String contentType = in.getFirst(HttpHeaders.CONTENT_TYPE);
			if (contentType != null) {
				out.setHeader(HttpHeaders.CONTENT_TYPE, contentType);
			} else if (first != -1) {
				out.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE);
			}
			if (in.getContentLength() >= 0) {
				out.setContentLengthLong(in.getContentLength());
			}
			for (String name : PASS_RESPONSE_HEADERS) {
				String value = in.getFirst(name);
				if (value != null) {
					out.setHeader(name, value);
				}
			}
			for (String cookie : in.getOrEmpty(HttpHeaders.SET_COOKIE)) {
				if (cookie.startsWith(COOKIE_PREFIX)) {
					out.addHeader(HttpHeaders.SET_COOKIE, cookie);
				}
			}
			if (first != -1) {
				OutputStream os = out.getOutputStream();
				byte[] buf = new byte[BUFFER_SIZE];
				buf[0] = (byte) first;
				int pending = 1;
				int n;
				while (true) {
					write(os, buf, pending);
					if ((n = body.read(buf)) == -1) {
						break;
					}
					pending = n;
				}
				flush(os);
			}
			return res.getStatusCode().value();
		}

		/** 寫給瀏覽器失敗 = 瀏覽器已斷線（例如取消下載），與後端讀取失敗分開記 */
		private void write(OutputStream os, byte[] buf, int len) throws IOException {
			try {
				os.write(buf, 0, len);
			} catch (IOException e) {
				clientGone = true;
				throw e;
			}
		}

		private void flush(OutputStream os) throws IOException {
			try {
				os.flush();
			} catch (IOException e) {
				clientGone = true;
				throw e;
			}
		}
	}

	/** 回應寫到一半後端斷了：不帶訊息與原因，避免後端位址進 log */
	static final class BrokenResponseException extends IOException {

		BrokenResponseException() {
			super("backend response broken");
		}
	}

	private static boolean isMultipart(String contentType) {
		return contentType != null && contentType.regionMatches(true, 0, "multipart/", 0, "multipart/".length());
	}

	private static void tooLarge(HttpServletResponse response, HttpMethod method, String path, String remoteAddr)
			throws IOException {
		log.warn("reject {} {} from {}: too-large", method, path, remoteAddr);
		writeJson(response, HttpStatus.PAYLOAD_TOO_LARGE, TOO_LARGE_BODY);
	}

	private static void badRequest(HttpServletResponse response, HttpMethod method, String path, String remoteAddr,
			String reason) throws IOException {
		log.warn("reject {} {} from {}: {}", method, path, remoteAddr, reason);
		writeJson(response, HttpStatus.BAD_REQUEST, BAD_REQUEST_BODY);
	}

	private static void writeJson(HttpServletResponse response, HttpStatus status, byte[] body) throws IOException {
		response.setStatus(status.value());
		response.setContentType(JSON_UTF8.toString());
		response.setContentLength(body.length);
		response.getOutputStream().write(body);
	}

	/**
	 * 去尾斜線後解析；必須是 http／https 的絕對位址（有 host）且不含 userinfo／查詢／fragment，否則回 null。
	 * 其他 scheme 不收：JDK HttpClient 對非 http(s) 丟 IllegalArgumentException，不是 IOException，會變成 500 而非 502。
	 * 主機名含底線時 java.net.URI 的 getHost() 回 null，也會落到這裡回 null。
	 * port 超過 65535 也不收：URI 解析不檢查上限，連線時 InetSocketAddress 丟 IllegalArgumentException，同樣會變 500。
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
			if (!isHttpScheme(u.getScheme()) || u.getHost() == null || u.getPort() > 65535 || u.getRawUserInfo() != null
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
	 * 失敗原因的類別名，由外往內以 " <- " 串起（最多 8 層；跳過最外層 Spring 包裝，包裝層沒有 cause 時才用它自己），
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
