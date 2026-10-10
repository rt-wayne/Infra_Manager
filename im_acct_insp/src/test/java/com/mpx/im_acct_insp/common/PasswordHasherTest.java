package com.mpx.im_acct_insp.common;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：密碼雜湊單元測試（spec v2.0 §9.2）：hash 可被 verify、錯密碼失敗、
//           格式解析（正確格式可解析、各種錯誤格式視為驗證失敗）、同密碼兩次 hash 不同 salt
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {

	private static final char[] PWD = "Unit#Test-Pwd1".toCharArray();

	private final PasswordHasher hasher = new PasswordHasher();

	@Test
	void hash_thenVerify_ok() {
		String stored = hasher.hash(PWD.clone());
		assertThat(hasher.verify(PWD.clone(), stored)).isTrue();
	}

	@Test
	void verify_wrongPassword_fail() {
		String stored = hasher.hash(PWD.clone());
		assertThat(hasher.verify("Unit#Test-Pwd2".toCharArray(), stored)).isFalse();
		assertThat(hasher.verify("unit#test-pwd1".toCharArray(), stored)).isFalse();
		assertThat(hasher.verify(new char[0], stored)).isFalse();
		assertThat(hasher.verify(null, stored)).isFalse();
	}

	@Test
	void hash_format() {
		String stored = hasher.hash(PWD.clone());
		String[] parts = stored.split("[$]", -1);
		assertThat(parts).hasSize(4);
		assertThat(parts[0]).isEqualTo("PBKDF2");
		assertThat(parts[1]).isEqualTo("310000");
		assertThat(Base64.getDecoder().decode(parts[2])).hasSize(16);
		assertThat(Base64.getDecoder().decode(parts[3])).hasSize(32);
		assertThat(stored.length()).isLessThanOrEqualTo(200); // IM_ACCT_USER.PWD_HASH VARCHAR2(200)
		assertThat(stored).doesNotContain(new String(PWD));

		PasswordHasher.Parsed parsed = PasswordHasher.parse(stored);
		assertThat(parsed).isNotNull();
		assertThat(parsed.iterations()).isEqualTo(310000);
		assertThat(parsed.salt()).hasSize(16);
		assertThat(parsed.hash()).hasSize(32);
	}

	@Test
	void hash_twice_differentSalt() {
		String a = hasher.hash(PWD.clone());
		String b = hasher.hash(PWD.clone());
		assertThat(a).isNotEqualTo(b);
		assertThat(a.split("[$]")[2]).isNotEqualTo(b.split("[$]")[2]);
		assertThat(hasher.verify(PWD.clone(), a)).isTrue();
		assertThat(hasher.verify(PWD.clone(), b)).isTrue();
	}

	@Test
	void verify_badFormat_fail() {
		String stored = hasher.hash(PWD.clone());
		String[] p = stored.split("[$]", -1);
		String[] bad = {
				null,
				"",
				"PBKDF2",
				"plain-text",
				"SHA256$310000$" + p[2] + "$" + p[3],          // 前綴不符
				"PBKDF2$abc$" + p[2] + "$" + p[3],             // 迭代非數字
				"PBKDF2$0$" + p[2] + "$" + p[3],               // 迭代 0
				"PBKDF2$-1$" + p[2] + "$" + p[3],              // 迭代負數
				"PBKDF2$99999999$" + p[2] + "$" + p[3],        // 迭代超過上限
				"PBKDF2$310000$!!notbase64!!$" + p[3],         // salt 非 base64
				"PBKDF2$310000$" + p[2] + "$",                 // hash 空
				"PBKDF2$310000$$" + p[3],                      // salt 空
				"PBKDF2$310000$" + p[2],                       // 少一段
				stored + "$extra"                              // 多一段
		};
		for (String s : bad) {
			assertThat(PasswordHasher.parse(s)).as("parse(%s)", s).isNull();
			assertThat(hasher.verify(PWD.clone(), s)).as("verify(%s)", s).isFalse();
		}
	}

	@Test
	void verify_tamperedHash_fail() {
		String stored = hasher.hash(PWD.clone());
		String[] p = stored.split("[$]", -1);
		byte[] h = Base64.getDecoder().decode(p[3]);
		h[0] ^= 0x01;
		String tampered = p[0] + "$" + p[1] + "$" + p[2] + "$" + Base64.getEncoder().encodeToString(h);
		assertThat(hasher.verify(PWD.clone(), tampered)).isFalse();
	}

	@Test
	void hash_emptyPassword_reject() {
		assertThatThrownBy(() -> hasher.hash(new char[0])).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> hasher.hash(null)).isInstanceOf(IllegalArgumentException.class);
	}
}
