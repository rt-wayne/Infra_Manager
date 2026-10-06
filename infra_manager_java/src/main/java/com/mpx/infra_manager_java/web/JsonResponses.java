package com.mpx.infra_manager_java.web;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：在 filter 層（進不了 @RestControllerAdvice 的地方）直接寫 {"message": …} JSON 回應（S2）。
//           2026-10-06 code review：訊息雖只來自程式常數，仍做 JSON 字串跳脫（"、\、控制字元），日後傳入含引號的文字不會壞掉；
//           BodyLimitFilter 的 413 改共用本類別。
// ============================================================

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;

import jakarta.servlet.http.HttpServletResponse;

public final class JsonResponses {

	private JsonResponses() {
	}

	public static void write(HttpServletResponse response, int status, String message) throws IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write("{\"message\":\"" + escape(message) + "\"}");
	}

	/** JSON 字串內容跳脫：引號、反斜線、U+0000～U+001F */
	static String escape(String text) {
		if (text == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder(text.length() + 8);
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			switch (c) {
			case '"' -> sb.append("\\\"");
			case '\\' -> sb.append("\\\\");
			case '\n' -> sb.append("\\n");
			case '\r' -> sb.append("\\r");
			case '\t' -> sb.append("\\t");
			default -> {
				if (c < 0x20) {
					sb.append(String.format("\\u%04x", (int) c));
				} else {
					sb.append(c);
				}
			}
			}
		}
		return sb.toString();
	}
}
