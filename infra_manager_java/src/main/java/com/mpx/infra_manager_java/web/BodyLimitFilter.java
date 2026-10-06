package com.mpx.infra_manager_java.web;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-05
// 變更說明: 新增：非 multipart 請求本文上限過濾器（S1，裁示 ⑤A）
//           Content-Length 已超過 → 不讀本文直接回 413 JSON；
//           Content-Length 未知（chunked）→ 包一層計數 InputStream，讀超過時丟 BodyTooLargeException。
//           multipart 不經本層（附件由 MultipartConfigElement 控管）。
//           已知限制：form-urlencoded 本文由 Tomcat 在 getParameter 時自行讀取、不經本層計數，
//           chunked 時上限為 Tomcat maxPostSize（2 MB）；本系統 API 只收 JSON 與 multipart，不受影響。
//           2026-10-06 code review：toLowerCase 指定 Locale.ROOT；非法 charset 改丟 UnsupportedEncodingException。
//           （Spring 讀 JSON 走 getInputStream 不走 getReader，正常流程碰不到；非法 charset 實際由 Spring 在解析
//           Content-Type 時先擋成 415。這裡只是讓自行呼叫 getReader 的人拿到 IOException 而不是未受檢例外。）
//           2026-10-06 S2 code review：413 的 JSON 改共用 JsonResponses.write，不再自己拼字串。
// ============================================================

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

public class BodyLimitFilter extends OncePerRequestFilter {

	private final long maxBytes;

	public BodyLimitFilter(long maxBytes) {
		this.maxBytes = maxBytes;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String contentType = request.getContentType();
		return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (request.getContentLengthLong() > maxBytes) {
			writeTooLarge(response);
			return;
		}
		chain.doFilter(new LimitedRequest(request, maxBytes), response);
	}

	/** 413 固定 JSON 訊息 */
	public static void writeTooLarge(HttpServletResponse response) throws IOException {
		JsonResponses.write(response, HttpStatus.PAYLOAD_TOO_LARGE.value(), "請求內容過大");
	}

	/** 讀取本文時計數，超過上限丟 BodyTooLargeException */
	static class LimitedRequest extends HttpServletRequestWrapper {

		private final long maxBytes;
		private ServletInputStream stream;
		private BufferedReader reader;

		LimitedRequest(HttpServletRequest request, long maxBytes) {
			super(request);
			this.maxBytes = maxBytes;
		}

		@Override
		public ServletInputStream getInputStream() throws IOException {
			if (stream == null) {
				stream = new LimitedInputStream(super.getInputStream(), maxBytes);
			}
			return stream;
		}

		@Override
		public BufferedReader getReader() throws IOException {
			if (reader == null) {
				String enc = getCharacterEncoding();
				Charset charset;
				try {
					charset = enc == null ? StandardCharsets.UTF_8 : Charset.forName(enc);
				} catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
					throw new UnsupportedEncodingException("不支援的字元編碼");
				}
				reader = new BufferedReader(new InputStreamReader(getInputStream(), charset));
			}
			return reader;
		}
	}

	static class LimitedInputStream extends ServletInputStream {

		private final ServletInputStream delegate;
		private final long maxBytes;
		private long count;

		LimitedInputStream(ServletInputStream delegate, long maxBytes) {
			this.delegate = delegate;
			this.maxBytes = maxBytes;
		}

		@Override
		public int read() throws IOException {
			int b = delegate.read();
			if (b >= 0) {
				add(1);
			}
			return b;
		}

		@Override
		public int read(byte[] buf, int off, int len) throws IOException {
			int n = delegate.read(buf, off, len);
			if (n > 0) {
				add(n);
			}
			return n;
		}

		private void add(int n) throws IOException {
			count += n;
			if (count > maxBytes) {
				throw new BodyTooLargeException(maxBytes);
			}
		}

		@Override
		public boolean isFinished() {
			return delegate.isFinished();
		}

		@Override
		public boolean isReady() {
			return delegate.isReady();
		}

		@Override
		public void setReadListener(ReadListener listener) {
			delegate.setReadListener(listener);
		}

		@Override
		public int available() throws IOException {
			return delegate.available();
		}

		@Override
		public void close() throws IOException {
			delegate.close();
		}
	}
}
