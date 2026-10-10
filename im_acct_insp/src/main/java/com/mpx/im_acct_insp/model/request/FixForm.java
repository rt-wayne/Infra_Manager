package com.mpx.im_acct_insp.model.request;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：POST /inspections/{id}/items/{itemId}/fix 的 body {fixDescr}
// ============================================================

public class FixForm {

	private String fixDescr;

	public String getFixDescr() {
		return fixDescr;
	}

	public void setFixDescr(String fixDescr) {
		this.fixDescr = fixDescr;
	}
}
