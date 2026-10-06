package com.mpx.infra_manager_java.model;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-05
// 變更說明: 新增：SELECT 1 AS OK FROM DUAL 的對應 model（S1 健康檢查用）；BeanPropertyRowMapper 需無參數建構子與 setter
// ============================================================

public class DualRow {

	private Integer ok;

	public Integer getOk() {
		return ok;
	}

	public void setOk(Integer ok) {
		this.ok = ok;
	}
}
