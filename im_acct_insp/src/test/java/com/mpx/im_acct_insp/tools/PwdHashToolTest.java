package com.mpx.im_acct_insp.tools;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：雜湊工具讀行測試（spec v2.0 §9.2）：去行尾 LF／CRLF、保留其他字元、空輸入、超長輸入
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

import org.junit.jupiter.api.Test;

class PwdHashToolTest {

	private static char[] read(String input) throws IOException {
		return PwdHashTool.readLine(new BufferedReader(new StringReader(input)));
	}

	@Test
	void readLine_stripsLineEnding() throws IOException {
		assertThat(new String(read("ab c$1\n"))).isEqualTo("ab c$1");
		assertThat(new String(read("ab c$1\r\n"))).isEqualTo("ab c$1");
		assertThat(new String(read("ab c$1"))).isEqualTo("ab c$1");
		assertThat(new String(read("first\nsecond\n"))).isEqualTo("first");
	}

	@Test
	void readLine_empty() throws IOException {
		assertThat(read("")).isEmpty();
		assertThat(read("\n")).isEmpty();
	}

	@Test
	void readLine_tooLong() throws IOException {
		String max = "x".repeat(PwdHashTool.MAX_LEN);
		assertThat(read(max + "\r\n")).hasSize(PwdHashTool.MAX_LEN);
		assertThat(read(max + "x\n")).isNull();
	}
}
