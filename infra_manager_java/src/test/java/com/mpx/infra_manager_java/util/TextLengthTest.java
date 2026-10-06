package com.mpx.infra_manager_java.util;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-05
// 變更說明: 新增：TextLength 單元測試（S1）：code point 計數、CRLF 正規化、上限邊界、null
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TextLengthTest {

	@Test
	void 中文與emoji都算一字() {
		// 😀 在 UTF-16 是 2 個 char，但只算 1 個 code point
		assertThat(TextLength.length("機房😀")).isEqualTo(3);
		assertThat("機房😀".length()).isEqualTo(4);
	}

	@Test
	void CRLF統一成LF後再計數() {
		assertThat(TextLength.normalize("a\r\nb\rc\n")).isEqualTo("a\nb\nc\n");
		assertThat(TextLength.length("a\r\nb")).isEqualTo(3);
	}

	@Test
	void null視為零字且原樣回傳() {
		assertThat(TextLength.length(null)).isZero();
		assertThat(TextLength.normalize(null)).isNull();
		assertThat(TextLength.check("x", "X", null, 10)).isNull();
	}

	@Test
	void 剛好上限通過並回傳正規化字串() {
		String s = "字".repeat(2000);
		assertThat(TextLength.check("impactDesc", "影響說明", s + "\r\n", 2001)).isEqualTo(s + "\n");
		assertThat(TextLength.check("impactDesc", "影響說明", s, TextLength.LIMIT_SHORT)).isEqualTo(s);
	}

	@Test
	void 超過上限丟例外帶JSON欄位名與中文訊息不帶DB欄名() {
		String s = "字".repeat(2001);
		assertThatThrownBy(() -> TextLength.check("impactDesc", "影響說明", s, TextLength.LIMIT_SHORT))
				.isInstanceOf(TextTooLongException.class)
				.satisfies(e -> {
					TextTooLongException t = (TextTooLongException) e;
					assertThat(t.getField()).isEqualTo("impactDesc");
					assertThat(t.getLabel()).isEqualTo("影響說明");
					assertThat(t.getMax()).isEqualTo(2000);
					assertThat(t.getActual()).isEqualTo(2001);
				})
				.hasMessage("「影響說明」超過 2000 字（目前 2001 字）");
	}

	@Test
	void 長欄位上限為兩萬字() {
		assertThat(TextLength.check("workDetail", "作業內容", "字".repeat(20000), TextLength.LIMIT_LONG)).hasSize(20000);
		assertThatThrownBy(() -> TextLength.check("workDetail", "作業內容", "字".repeat(20001), TextLength.LIMIT_LONG))
				.isInstanceOf(TextTooLongException.class);
	}
}
