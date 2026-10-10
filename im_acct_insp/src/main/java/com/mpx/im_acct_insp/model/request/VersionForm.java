package com.mpx.im_acct_insp.model.request;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：approve、verify-ok 的 body {versionNo}（spec v2.0 §4.4 樂觀鎖；未帶 → 400 E02）
// ============================================================

public class VersionForm {

	private Integer versionNo;

	public Integer getVersionNo() {
		return versionNo;
	}

	public void setVersionNo(Integer versionNo) {
		this.versionNo = versionNo;
	}
}
