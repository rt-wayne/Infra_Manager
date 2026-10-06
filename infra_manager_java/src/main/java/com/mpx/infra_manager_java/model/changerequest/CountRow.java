package com.mpx.infra_manager_java.model.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：COUNT(*) AS CNT 的承接列（S4；DbClient 只提供類別對應，沒有單值查詢）
// ============================================================

public class CountRow {

	private Long cnt;

	public Long getCnt() { return cnt; }
	public void setCnt(Long cnt) { this.cnt = cnt; }
}
