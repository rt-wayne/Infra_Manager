package com.mpx.infra_manager.model;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：GET /accounts 回傳（spec v2.0 §6.4）：items＋stateCounts（套用狀態以外的條件）＋catgCounts（套用類別以外的條件）＋expiringCount（不受篩選影響）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// ============================================================

import java.util.List;
import java.util.Map;

public class AcctListResult {

	private List<Acct> items;
	private Map<String, Long> stateCounts;
	private Map<String, Long> catgCounts;
	private Long expiringCount;

	public List<Acct> getItems() {
		return items;
	}

	public void setItems(List<Acct> items) {
		this.items = items;
	}

	public Map<String, Long> getStateCounts() {
		return stateCounts;
	}

	public void setStateCounts(Map<String, Long> stateCounts) {
		this.stateCounts = stateCounts;
	}

	public Map<String, Long> getCatgCounts() {
		return catgCounts;
	}

	public void setCatgCounts(Map<String, Long> catgCounts) {
		this.catgCounts = catgCounts;
	}

	public Long getExpiringCount() {
		return expiringCount;
	}

	public void setExpiringCount(Long expiringCount) {
		this.expiringCount = expiringCount;
	}
}
