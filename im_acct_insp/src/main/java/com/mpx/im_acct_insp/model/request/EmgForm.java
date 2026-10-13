package com.mpx.im_acct_insp.model.request;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: 新增：POST /emergencies 的 body（BACKLOG 第 46 項 S2）：catgId、targetDescr（≤200 字）、applyReason（≤500 字）、
//           expectHourQty（整數 1～24 小時）
// ============================================================

public class EmgForm {

	private String catgId;
	private String targetDescr;
	private String applyReason;
	private Integer expectHourQty;

	public String getCatgId() {
		return catgId;
	}

	public void setCatgId(String catgId) {
		this.catgId = catgId;
	}

	public String getTargetDescr() {
		return targetDescr;
	}

	public void setTargetDescr(String targetDescr) {
		this.targetDescr = targetDescr;
	}

	public String getApplyReason() {
		return applyReason;
	}

	public void setApplyReason(String applyReason) {
		this.applyReason = applyReason;
	}

	public Integer getExpectHourQty() {
		return expectHourQty;
	}

	public void setExpectHourQty(Integer expectHourQty) {
		this.expectHourQty = expectHourQty;
	}
}
