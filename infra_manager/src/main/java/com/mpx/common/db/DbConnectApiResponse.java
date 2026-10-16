package com.mpx.common.db;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-02
// 變更說明: 新增：連線資訊 API 回應外層（code / message / data）
//           toString 不輸出 data 內容，避免帶出連線資訊
// ============================================================

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DbConnectApiResponse {

	private String code;
	private String message;
	private DbConnectionInfo data;

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public DbConnectionInfo getData() {
		return data;
	}

	public void setData(DbConnectionInfo data) {
		this.data = data;
	}

	@Override
	public String toString() {
		return "DbConnectApiResponse{code=" + code + ", message=" + message + "}";
	}
}
