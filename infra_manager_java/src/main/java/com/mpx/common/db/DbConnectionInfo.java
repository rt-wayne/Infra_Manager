package com.mpx.common.db;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-02
// 變更說明: 新增：連線資訊 API 回應 data 的 DTO（規格 §5.2）
//           手寫 getter/setter（不用 lombok，規格 D-01）
//           toString 遮蔽 password，不輸出原值也不輸出長度；jdbcUrl、username 也不輸出（規格 §4.3、§9）
//           多出的欄位忽略不報錯
// ============================================================

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DbConnectionInfo {

	private String alias;
	private String dbType;
	private String jdbcUrl;
	private String username;
	private String password;
	private Integer maxPoolSize;
	private Integer queryTimeoutSeconds;

	public String getAlias() {
		return alias;
	}

	public void setAlias(String alias) {
		this.alias = alias;
	}

	public String getDbType() {
		return dbType;
	}

	public void setDbType(String dbType) {
		this.dbType = dbType;
	}

	public String getJdbcUrl() {
		return jdbcUrl;
	}

	public void setJdbcUrl(String jdbcUrl) {
		this.jdbcUrl = jdbcUrl;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public Integer getMaxPoolSize() {
		return maxPoolSize;
	}

	public void setMaxPoolSize(Integer maxPoolSize) {
		this.maxPoolSize = maxPoolSize;
	}

	public Integer getQueryTimeoutSeconds() {
		return queryTimeoutSeconds;
	}

	public void setQueryTimeoutSeconds(Integer queryTimeoutSeconds) {
		this.queryTimeoutSeconds = queryTimeoutSeconds;
	}

	@Override
	public String toString() {
		return "DbConnectionInfo{alias=" + alias
				+ ", dbType=" + dbType
				+ ", jdbcUrl=***"
				+ ", username=***"
				+ ", password=***"
				+ ", maxPoolSize=" + maxPoolSize
				+ ", queryTimeoutSeconds=" + queryTimeoutSeconds
				+ "}";
	}
}
