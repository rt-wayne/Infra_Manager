package com.mpx.im_acct_insp.model;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：POST /inspections/{id}/items/{itemId}/fix 回傳（spec v2.0 §6.6）：{inspId, inspStateCode}
// ============================================================

public class InspFixResult {

	private String inspId;
	private String inspStateCode;

	public String getInspId() {
		return inspId;
	}

	public void setInspId(String inspId) {
		this.inspId = inspId;
	}

	public String getInspStateCode() {
		return inspStateCode;
	}

	public void setInspStateCode(String inspStateCode) {
		this.inspStateCode = inspStateCode;
	}
}
