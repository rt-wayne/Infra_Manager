package com.mpx.infra_manager_java.model;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-05
// 變更說明: 新增：GET /api/health 的回應（S1）。status 固定 UP（程式活著）；db 為 UP／DOWN；time 為台灣時間
// ============================================================

public class HealthStatus {

	private String status;
	private String db;
	private String time;

	public HealthStatus() {
	}

	public HealthStatus(String status, String db, String time) {
		this.status = status;
		this.db = db;
		this.time = time;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getDb() {
		return db;
	}

	public void setDb(String db) {
		this.db = db;
	}

	public String getTime() {
		return time;
	}

	public void setTime(String time) {
		this.time = time;
	}
}
