package com.mpx.infra_manager_java.model.sysparam;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-06
// 變更說明: 新增：SYS_PARAM 查詢列（S6 回合二 a），只取參數值
// ============================================================

public class SysParamRow {

	private String paramValue;

	public String getParamValue() { return paramValue; }
	public void setParamValue(String paramValue) { this.paramValue = paramValue; }
}
