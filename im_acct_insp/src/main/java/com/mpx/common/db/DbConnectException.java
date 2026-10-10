package com.mpx.common.db;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-02
// 變更說明: 新增：取得 DB 連線階段的統一例外（規格 §7）
//           訊息固定格式「取得 DB 連線失敗 alias=<alias>: <原因>」
//           原因由呼叫端組，不得含 password、完整 jdbcUrl、username（規格 §7.2）
//           SQL 執行期錯誤不包成本例外，原樣拋 Spring DataAccessException（規格 §13-3）
// ============================================================

public class DbConnectException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final String alias;

	public DbConnectException(String alias, String reason) {
		super(buildMessage(alias, reason));
		this.alias = alias;
	}

	public DbConnectException(String alias, String reason, Throwable cause) {
		super(buildMessage(alias, reason), cause);
		this.alias = alias;
	}

	public String getAlias() {
		return alias;
	}

	private static String buildMessage(String alias, String reason) {
		return "取得 DB 連線失敗 alias=" + alias + ": " + reason;
	}
}
