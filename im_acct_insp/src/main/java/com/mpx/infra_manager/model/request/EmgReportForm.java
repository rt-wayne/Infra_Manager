package com.mpx.infra_manager.model.request;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: 新增：POST /emergencies/{id}/report 的 body（BACKLOG 第 46 項 S4a）：reportTypeCode（NONE／MODIFIED）、
//           reportDescr（≤500 字）、versionNo（樂觀鎖）、affectAccts（MODIFIED 時 1～20 個；每個帶帳號單號＋完整帳號名稱，
//           後端驗名稱相符，S4-⑥）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// ============================================================

import java.util.List;

public class EmgReportForm {

	private String reportTypeCode;
	private String reportDescr;
	private Integer versionNo;
	private List<AffectAcct> affectAccts;

	public String getReportTypeCode() {
		return reportTypeCode;
	}

	public void setReportTypeCode(String reportTypeCode) {
		this.reportTypeCode = reportTypeCode;
	}

	public String getReportDescr() {
		return reportDescr;
	}

	public void setReportDescr(String reportDescr) {
		this.reportDescr = reportDescr;
	}

	public Integer getVersionNo() {
		return versionNo;
	}

	public void setVersionNo(Integer versionNo) {
		this.versionNo = versionNo;
	}

	public List<AffectAcct> getAffectAccts() {
		return affectAccts;
	}

	public void setAffectAccts(List<AffectAcct> affectAccts) {
		this.affectAccts = affectAccts;
	}

	/** 受影響帳號：acctId＋acctName 須同時與 IM_ACCT 相符；affectDescr 選填（≤500 字，S4-②） */
	public static class AffectAcct {

		private String acctId;
		private String acctName;
		private String affectDescr;

		public String getAcctId() {
			return acctId;
		}

		public void setAcctId(String acctId) {
			this.acctId = acctId;
		}

		public String getAcctName() {
			return acctName;
		}

		public void setAcctName(String acctName) {
			this.acctName = acctName;
		}

		public String getAffectDescr() {
			return affectDescr;
		}

		public void setAffectDescr(String affectDescr) {
			this.affectDescr = affectDescr;
		}
	}
}
