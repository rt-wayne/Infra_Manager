package com.mpx.infra_manager.common;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：密碼雜湊（spec v2.0 §2.1、§9.2；Table v0.2 7.1 PWD_HASH）
//           PBKDF2WithHmacSHA256、salt 16 bytes（SecureRandom）、迭代 310000、輸出 32 bytes，
//           存成 PBKDF2$迭代$base64(salt)$base64(hash)；verify 以 MessageDigest.isEqual 常數時間比較；
//           格式錯誤或 stored 為空時仍做一次雜湊計算後回 false（回應時間不洩漏帳號是否存在）。
//           只用 JDK 內建；不記 log、不保留密碼
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// ============================================================

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.springframework.stereotype.Component;

@Component
public class PasswordHasher {

	static final String ALGORITHM = "PBKDF2WithHmacSHA256";
	static final String PREFIX = "PBKDF2";
	static final int ITERATIONS = 310000;
	static final int SALT_BYTES = 16;
	static final int HASH_BYTES = 32;
	/** 解析 stored 時接受的迭代次數上限（防止竄改值造成過長運算） */
	static final int MAX_ITERATIONS = 10_000_000;

	private static final String SEP = "$";

	private final SecureRandom random = new SecureRandom();

	/**
	 * 產生密碼雜湊字串。
	 *
	 * @param password 明文密碼（呼叫端用完自行清除）
	 * @return PBKDF2$310000$base64(salt)$base64(hash)
	 */
	public String hash(char[] password) {
		if (password == null || password.length == 0) {
			throw new IllegalArgumentException("password 不可為空");
		}
		byte[] salt = new byte[SALT_BYTES];
		random.nextBytes(salt);
		byte[] hash = pbkdf2(password, salt, ITERATIONS, HASH_BYTES);
		Base64.Encoder enc = Base64.getEncoder();
		String result = PREFIX + SEP + ITERATIONS + SEP + enc.encodeToString(salt) + SEP + enc.encodeToString(hash);
		Arrays.fill(hash, (byte) 0);
		return result;
	}

	/**
	 * 驗證密碼是否與 stored 相符。stored 為空或格式錯誤時視為失敗，但仍做一次雜湊計算。
	 *
	 * @param password 明文密碼
	 * @param stored   DB 的 PWD_HASH
	 * @return 相符回 true
	 */
	public boolean verify(char[] password, String stored) {
		char[] pwd = (password == null || password.length == 0) ? new char[] { '\0' } : password;
		Parsed parsed = parse(stored);
		if (parsed == null) {
			dummyHash(pwd);
			return false;
		}
		byte[] actual = pbkdf2(pwd, parsed.salt, parsed.iterations, parsed.hash.length);
		boolean match = MessageDigest.isEqual(actual, parsed.hash);
		Arrays.fill(actual, (byte) 0);
		return match && password != null && password.length > 0;
	}

	/** 工號不存在等情況下，呼叫端可用此方法做一次等量的雜湊計算以拉平回應時間 */
	public void dummyHash(char[] password) {
		char[] pwd = (password == null || password.length == 0) ? new char[] { '\0' } : password;
		byte[] salt = new byte[SALT_BYTES];
		Arrays.fill(pbkdf2(pwd, salt, ITERATIONS, HASH_BYTES), (byte) 0);
	}

	/** 解析 stored；格式不符回 null */
	static Parsed parse(String stored) {
		if (stored == null) {
			return null;
		}
		String[] parts = stored.split("[$]", -1);
		if (parts.length != 4 || !PREFIX.equals(parts[0])) {
			return null;
		}
		try {
			int iterations = Integer.parseInt(parts[1]);
			if (iterations < 1 || iterations > MAX_ITERATIONS) {
				return null;
			}
			Base64.Decoder dec = Base64.getDecoder();
			byte[] salt = dec.decode(parts[2]);
			byte[] hash = dec.decode(parts[3]);
			if (salt.length == 0 || hash.length == 0) {
				return null;
			}
			return new Parsed(iterations, salt, hash);
		} catch (IllegalArgumentException e) {
			// NumberFormatException 亦為 IllegalArgumentException
			return null;
		}
	}

	private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int hashBytes) {
		PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, hashBytes * 8);
		try {
			return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("密碼雜湊計算失敗：" + e.getClass().getSimpleName(), e);
		} finally {
			spec.clearPassword();
		}
	}

	record Parsed(int iterations, byte[] salt, byte[] hash) {
	}
}
