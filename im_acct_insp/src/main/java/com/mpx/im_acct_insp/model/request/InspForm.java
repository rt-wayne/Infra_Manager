package com.mpx.im_acct_insp.model.request;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：POST /inspections 的 body；inspDate 為 yyyy-MM-dd HH:mm（亦接受 yyyy-MM-ddTHH:mm），空值＝現在
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（spec_im_acct_insp_v2.0.md §6.6）：inspDate 改 yyyy-MM-dd HH:mm:ss（亦接受不帶秒、T 分隔），空值＝現在
// ============================================================

import java.util.List;

public class InspForm {

	private String inspDate;
	private List<InspResultForm> results;

	public String getInspDate() {
		return inspDate;
	}

	public void setInspDate(String inspDate) {
		this.inspDate = inspDate;
	}

	public List<InspResultForm> getResults() {
		return results;
	}

	public void setResults(List<InspResultForm> results) {
		this.results = results;
	}
}
