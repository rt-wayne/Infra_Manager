package com.mpx.infra_manager.common;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：共用元件單元測試（spec v2.0 §2.1、§2.2、§3、§5、§6.1）：
//           TokenCodec（32 bytes base64url、SHA-256 64 碼小寫且不等於 token、Bearer 解析）；
//           Texts（顯示名格式、CHAR 字數與 BYTE 長度 E11、LIKE 跳脫）；
//           LazyDataSourceTransactionManager（建構時不取 DataSource、第一次開交易才取且只取一次、失敗下次重試）；
//           GlobalExceptionHandler（multipart 超過上限 413 E15）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.sql.Connection;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class CommonUtilTest {

	@Test
	void token_32bytes_base64url_雜湊為64碼小寫且不等於token() {
		TokenCodec codec = new TokenCodec();
		String t = codec.newToken();
		assertThat(Base64.getUrlDecoder().decode(t)).hasSize(32);
		assertThat(t).doesNotContain("=").doesNotContain("+").doesNotContain("/");
		assertThat(codec.newToken()).isNotEqualTo(t);
		String h = TokenCodec.sha256Hex(t);
		assertThat(h).hasSize(64).matches("[0-9a-f]{64}").isNotEqualTo(t);
		assertThat(TokenCodec.sha256Hex(t)).isEqualTo(h);
	}

	@Test
	void bearer解析_不分大小寫_格式不符回null() {
		assertThat(TokenCodec.parseBearer("Bearer abc")).isEqualTo("abc");
		assertThat(TokenCodec.parseBearer("  bearer   abc  ")).isEqualTo("abc");
		assertThat(TokenCodec.parseBearer(null)).isNull();
		assertThat(TokenCodec.parseBearer("Basic abc")).isNull();
		assertThat(TokenCodec.parseBearer("Bearer ")).isNull();
		assertThat(TokenCodec.parseBearer("Bearer " + "x".repeat(300))).isNull();
	}

	@Test
	void 顯示名格式_英文名括號中文名() {
		assertThat(Texts.displayName("Hank", "黃志翰", "0000011")).isEqualTo("Hank (黃志翰)");
		assertThat(Texts.displayName("Hank", " ", "0000011")).isEqualTo("Hank");
		assertThat(Texts.displayName(null, null, "0000011")).isEqualTo("0000011");
	}

	@Test
	void 欄寬_CHAR以字數_BYTE以UTF8位元組_超過E11() {
		assertThat(Texts.maxChars("申請單位", "中".repeat(50), 50)).hasSize(50);
		assertThatThrownBy(() -> Texts.maxChars("申請單位", "中".repeat(51), 50)).isInstanceOf(ApiException.class)
				.hasMessage(AcctMsg.E11_PREFIX + "申請單位");
		assertThat(Texts.maxBytes("卡號", "a".repeat(50), 50)).hasSize(50);
		assertThatThrownBy(() -> Texts.maxBytes("卡號", "中".repeat(17), 50)).hasMessage(AcctMsg.E11_PREFIX + "卡號");
		assertThat(Texts.truncate("一二三四", 2)).isEqualTo("一二");
	}

	@Test
	void LIKE跳脫() {
		assertThat(Texts.escapeLike("a\\b%c_d")).isEqualTo("a\\\\b\\%c\\_d");
		assertThat(Texts.escapeLike(null)).isNull();
	}

	@Test
	void 延遲交易管理器_第一次開交易才取DataSource_只取一次_失敗下次重試() throws Exception {
		DataSource ds = mock(DataSource.class);
		Connection conn = mock(Connection.class);
		Mockito.when(ds.getConnection()).thenReturn(conn);
		Mockito.when(conn.getAutoCommit()).thenReturn(true);
		AtomicInteger calls = new AtomicInteger();
		AtomicInteger fails = new AtomicInteger(1);
		LazyDataSourceTransactionManager m = new LazyDataSourceTransactionManager(() -> {
			calls.incrementAndGet();
			if (fails.getAndDecrement() > 0) {
				throw new IllegalStateException("連線資訊 API 失敗");
			}
			return ds;
		});
		TransactionTemplate tx = new TransactionTemplate(m);
		assertThat(calls.get()).isZero();
		assertThatThrownBy(() -> tx.execute(s -> 1)).isInstanceOf(IllegalStateException.class);
		Integer one = tx.execute(s -> 1);
		Integer two = tx.execute(s -> 2);
		assertThat(one).isEqualTo(1);
		assertThat(two).isEqualTo(2);
		assertThat(calls.get()).isEqualTo(2);
		Mockito.verify(conn, Mockito.times(2)).commit();
	}

	@Test
	void 附件超過multipart上限_413_E15() {
		GlobalExceptionHandler h = new GlobalExceptionHandler();
		var r = h.handleMaxUpload(new MaxUploadSizeExceededException(10L * 1024 * 1024));
		assertThat(r.getStatusCode().value()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE.value()).isEqualTo(413);
		assertThat(r.getBody()).containsEntry("message", AcctMsg.E15);
	}
}
