package com.mpx.im_acct_insp.model;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：GET /home/stats 首頁卡片（spec §4.1、§3.7）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S8：加 emgUndecided（緊急存取待補核准，含逾期）、emgReturned（異常待追蹤）
// ============================================================

public class HomeStats {

	private Long acctTotal;
	private Long acctReviewing;
	private Long acctExpiring;
	private Long acctPriv;
	private Long inspThisMonth;
	private Long inspExcptThisMonth;
	private Long emgUndecided;
	private Long emgReturned;

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

	public Long getEmgUndecided() {
		return emgUndecided;
	}

	public void setEmgUndecided(Long emgUndecided) {
		this.emgUndecided = emgUndecided;
	}

	public Long getEmgReturned() {
		return emgReturned;
	}

	public void setEmgReturned(Long emgReturned) {
		this.emgReturned = emgReturned;
	}
}
