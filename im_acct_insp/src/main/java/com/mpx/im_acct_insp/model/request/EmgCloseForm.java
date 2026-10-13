package com.mpx.im_acct_insp.model.request;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: 新增：POST /emergencies/{id}/close 的 body（BACKLOG 第 46 項 S6）：closeResultCode（NO_CHANGE／CHANGED）、
//           closeDescr（兩種結果都必填、≤500 字，S6-①）、versionNo（樂觀鎖）、affectAccts（CHANGED 時 1～20 個；
//           格式同回報，帶帳號單號＋完整帳號名稱，後端驗名稱相符，S4-⑥）
// ============================================================

import java.util.List;

public class EmgCloseForm {

	private String closeResultCode;
	private String closeDescr;
	private Integer versionNo;
	private List<EmgReportForm.AffectAcct> affectAccts;

	public String getCloseResultCode() {
		return closeResultCode;
	}

	public void setCloseResultCode(String closeResultCode) {
		this.closeResultCode = closeResultCode;
	}

	public String getCloseDescr() {
		return closeDescr;
	}

	public void setCloseDescr(String closeDescr) {
		this.closeDescr = closeDescr;
	}

	public Integer getVersionNo() {
		return versionNo;
	}

	public void setVersionNo(Integer versionNo) {
		this.versionNo = versionNo;
	}

	public List<EmgReportForm.AffectAcct> getAffectAccts() {
		return affectAccts;
	}

	public void setAffectAccts(List<EmgReportForm.AffectAcct> affectAccts) {
		this.affectAccts = affectAccts;
	}
}
