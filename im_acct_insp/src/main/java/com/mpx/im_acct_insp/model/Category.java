package com.mpx.im_acct_insp.model;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：帳號類別 IM_ACCT_CATG（GET /codes categories）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（spec_im_acct_insp_v2.0.md §6.3）：加 enabled（IS_ENABLED）、suggestExpiry（IS_SUGGEST_EXPIRY）、sortSeq
// ============================================================

public class Category {

	private String catgId;
	private String catgName;
	private String catgDescr;
	private Boolean enabled;
	private Boolean suggestExpiry;
	private Integer sortSeq;

	public String getCatgId() {
		return catgId;
	}

	public void setCatgId(String catgId) {
		this.catgId = catgId;
	}

	public String getCatgName() {
		return catgName;
	}

	public void setCatgName(String catgName) {
		this.catgName = catgName;
	}

	public String getCatgDescr() {
		return catgDescr;
	}

	public void setCatgDescr(String catgDescr) {
		this.catgDescr = catgDescr;
	}

	public Boolean getEnabled() {
		return enabled;
	}

	public void setEnabled(Boolean enabled) {
		this.enabled = enabled;
	}

	public Boolean getSuggestExpiry() {
		return suggestExpiry;
	}

	public void setSuggestExpiry(Boolean suggestExpiry) {
		this.suggestExpiry = suggestExpiry;
	}

	public Integer getSortSeq() {
		return sortSeq;
	}

	public void setSortSeq(Integer sortSeq) {
		this.sortSeq = sortSeq;
	}
}
