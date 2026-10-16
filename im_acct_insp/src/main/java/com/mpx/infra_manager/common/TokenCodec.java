package com.mpx.infra_manager.common;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：登入 token 產生與雜湊（spec v2.0 §2.1）
//           token＝32 bytes SecureRandom，base64url（不補 =）回給前端；DB 只存其 SHA-256（16 進位小寫 64 碼，
//           以 token 字串的 ASCII 位元組計算）。Bearer 解析不分大小寫、去前後空白。不記 log
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// ============================================================

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
public class TokenCodec {

	static final String BEARER = "bearer ";
	/** 合理上限：32 bytes 的 base64url 為 43 字元，超過此長度直接視為無效（不做雜湊） */
	static final int MAX_TOKEN_LENGTH = 256;

	private final SecureRandom random = new SecureRandom();

	public String newToken() {
		byte[] b = new byte[AcctConst.TOKEN_BYTES];
		random.nextBytes(b);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
	}

	public static String sha256Hex(String token) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(md.digest(token.getBytes(StandardCharsets.US_ASCII)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 不可用", e);
		}
	}

	/** 取出 Bearer 後的 token；格式不符回 null */
	public static String parseBearer(String header) {
		if (header == null) {
			return null;
		}
		String h = header.trim();
		if (h.length() <= BEARER.length() || !h.substring(0, BEARER.length()).toLowerCase(Locale.ROOT).equals(BEARER)) {
			return null;
		}
		String token = h.substring(BEARER.length()).trim();
		return token.isEmpty() || token.length() > MAX_TOKEN_LENGTH ? null : token;
	}
}
