package com.mpx.im_acct_insp.model;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：GET /home/stats 首頁卡片（spec §4.1、§3.7）
// ============================================================

public class HomeStats {

	private Long acctTotal;
	private Long acctReviewing;
	private Long acctExpiring;
	private Long acctPriv;
	private Long inspThisMonth;
	private Long inspExcptThisMonth;

	public Long getAcctTotal() {
		return acctTotal;
	}

	public void setAcctTotal(Long acctTotal) {
		this.acctTotal = acctTotal;
	}

	public Long getAcctReviewing() {
		return acctReviewing;
	}

	public void setAcctReviewing(Long acctReviewing) {
		this.acctReviewing = acctReviewing;
	}

	public Long getAcctExpiring() {
		return acctExpiring;
	}

	public void setAcctExpiring(Long acctExpiring) {
		this.acctExpiring = acctExpiring;
	}

	public Long getAcctPriv() {
		return acctPriv;
	}

	public void setAcctPriv(Long acctPriv) {
		this.acctPriv = acctPriv;
	}

	public Long getInspThisMonth() {
		return inspThisMonth;
	}

	public void setInspThisMonth(Long inspThisMonth) {
		this.inspThisMonth = inspThisMonth;
	}

	public Long getInspExcptThisMonth() {
		return inspExcptThisMonth;
	}

	public void setInspExcptThisMonth(Long inspExcptThisMonth) {
		this.inspExcptThisMonth = inspExcptThisMonth;
	}
}
