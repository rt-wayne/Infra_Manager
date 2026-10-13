package com.mpx.im_acct_insp.model;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: 新增：POST /emergencies 回傳 {emgId}（BACKLOG 第 46 項 S2）
// ============================================================

public class EmgIdResult {

	private String emgId;

	public String getEmgId() {
		return emgId;
	}

	public void setEmgId(String emgId) {
		this.emgId = emgId;
	}
}
