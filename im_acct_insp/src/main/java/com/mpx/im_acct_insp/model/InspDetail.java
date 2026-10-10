package com.mpx.im_acct_insp.model;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：GET /inspections/{id} 明細＝巡檢單欄位＋results
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0：欄位不變（results 元素改用快照名稱與處理人代理身分）
// ============================================================

import java.util.List;

public class InspDetail extends Insp {

	private List<InspResult> results;

	public List<InspResult> getResults() {
		return results;
	}

	public void setResults(List<InspResult> results) {
		this.results = results;
	}
}
