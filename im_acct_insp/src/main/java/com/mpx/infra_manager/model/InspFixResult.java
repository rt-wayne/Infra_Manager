package com.mpx.infra_manager.model;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：POST /inspections/{id}/items/{itemId}/fix 回傳（spec v2.0 §6.6）：{inspId, inspStateCode}
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
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
