package com.mpx.im_acct_insp.model;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: 新增：緊急存取回報／補核准／退回的回傳 {emgId, versionNo}（BACKLOG 第 46 項 S4）；
//           versionNo 為動作成功後的新版本，前端可直接接續下一個動作或重新載入明細
// ============================================================

public class EmgActionResult {

	private String emgId;
	private Integer versionNo;

	public String getEmgId() {
		return emgId;
	}

	public void setEmgId(String emgId) {
		this.emgId = emgId;
	}

	public Integer getVersionNo() {
		return versionNo;
	}

	public void setVersionNo(Integer versionNo) {
		this.versionNo = versionNo;
	}
}
