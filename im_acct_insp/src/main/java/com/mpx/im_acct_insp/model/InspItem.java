package com.mpx.im_acct_insp.model;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：巡檢項目 IM_INSP_ITEM（GET /inspection-items）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（Table v0.2 7.13）：加 itemDescr
// ============================================================

public class InspItem {

	private String inspItemId;
	private String sectCode;
	private String sectName;
	private String itemName;
	private String itemDescr;
	private Integer sortSeq;

	public String getInspItemId() {
		return inspItemId;
	}

	public void setInspItemId(String inspItemId) {
		this.inspItemId = inspItemId;
	}

	public String getSectCode() {
		return sectCode;
	}

	public void setSectCode(String sectCode) {
		this.sectCode = sectCode;
	}

	public String getSectName() {
		return sectName;
	}

	public void setSectName(String sectName) {
		this.sectName = sectName;
	}

	public String getItemName() {
		return itemName;
	}

	public void setItemName(String itemName) {
		this.itemName = itemName;
	}

	public String getItemDescr() {
		return itemDescr;
	}

	public void setItemDescr(String itemDescr) {
		this.itemDescr = itemDescr;
	}

	public Integer getSortSeq() {
		return sortSeq;
	}

	public void setSortSeq(Integer sortSeq) {
		this.sortSeq = sortSeq;
	}
}
