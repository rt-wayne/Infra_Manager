package com.mpx.im_acct_insp.model;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：資產分類碼 IM_ASSET_CLASS（GET /codes assetClasses）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（spec_im_acct_insp_v2.0.md §6.3）：IS_DIRECT → reviewTrigger（IS_REVIEW_TRIGGER）、IS_SHOW → selectable（IS_SELECTABLE）；加 sortSeq
// ============================================================

public class AssetClass {

	private String assetClassId;
	private String assetTypeCode;
	private String assetClassName;
	private Boolean reviewTrigger;
	private Boolean selectable;
	private String ctrlDescr;
	private Integer sortSeq;

	public String getAssetClassId() {
		return assetClassId;
	}

	public void setAssetClassId(String assetClassId) {
		this.assetClassId = assetClassId;
	}

	public String getAssetTypeCode() {
		return assetTypeCode;
	}

	public void setAssetTypeCode(String assetTypeCode) {
		this.assetTypeCode = assetTypeCode;
	}

	public String getAssetClassName() {
		return assetClassName;
	}

	public void setAssetClassName(String assetClassName) {
		this.assetClassName = assetClassName;
	}

	public Boolean getReviewTrigger() {
		return reviewTrigger;
	}

	public void setReviewTrigger(Boolean reviewTrigger) {
		this.reviewTrigger = reviewTrigger;
	}

	public Boolean getSelectable() {
		return selectable;
	}

	public void setSelectable(Boolean selectable) {
		this.selectable = selectable;
	}

	public String getCtrlDescr() {
		return ctrlDescr;
	}

	public void setCtrlDescr(String ctrlDescr) {
		this.ctrlDescr = ctrlDescr;
	}

	public Integer getSortSeq() {
		return sortSeq;
	}

	public void setSortSeq(Integer sortSeq) {
		this.sortSeq = sortSeq;
	}
}
