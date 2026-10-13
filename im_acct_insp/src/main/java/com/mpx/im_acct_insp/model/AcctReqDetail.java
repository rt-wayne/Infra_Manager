package com.mpx.im_acct_insp.model;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：GET /requests/{id} 明細＝申請單欄位＋history（IM_ACCT_HIST，依 ACT_DATE、HIST_ID）＋retryHint（VERIFY_REJECT_REASON）（spec §4.3）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（spec_im_acct_insp_v2.0.md §6.5）：加 attachments（STATUS＝1）、actions（我此刻可做）；刪 retryHint（改由 verifyRejectReason）
// AI版本  : Claude Fable 5 (claude-fable-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S10：加 acctReviewFlag／acctReviewFlagReason／acctReviewFlagDate（目標帳號現掛的
//           審查異常旗標，spec §3.4 關卡 2～5、7 警示用；未標旗標時三欄皆 null）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S10 審查修正 ②A：加 acctReviewFlagWillClear（複驗通過會不會清，由後端判斷）；
//           旗標欄改為只在關卡 2～5、7 帶出（③A）
// ============================================================

import java.time.LocalDateTime;
import java.util.List;

public class AcctReqDetail extends AcctReq {

	private List<AcctAttach> attachments;
	private List<AcctHist> history;
	private List<String> actions;
	private Integer acctReviewFlag;
	private String acctReviewFlagReason;
	private LocalDateTime acctReviewFlagDate;
	/** 關卡 7 且本單複驗通過會清這個旗標（RequestService.flagWillClear）；未帶旗標時 null */
	private Boolean acctReviewFlagWillClear;

	public List<AcctAttach> getAttachments() {
		return attachments;
	}

	public void setAttachments(List<AcctAttach> attachments) {
		this.attachments = attachments;
	}

	public List<AcctHist> getHistory() {
		return history;
	}

	public void setHistory(List<AcctHist> history) {
		this.history = history;
	}

	public List<String> getActions() {
		return actions;
	}

	public void setActions(List<String> actions) {
		this.actions = actions;
	}

	public Integer getAcctReviewFlag() {
		return acctReviewFlag;
	}

	public void setAcctReviewFlag(Integer acctReviewFlag) {
		this.acctReviewFlag = acctReviewFlag;
	}

	public String getAcctReviewFlagReason() {
		return acctReviewFlagReason;
	}

	public void setAcctReviewFlagReason(String acctReviewFlagReason) {
		this.acctReviewFlagReason = acctReviewFlagReason;
	}

	public LocalDateTime getAcctReviewFlagDate() {
		return acctReviewFlagDate;
	}

	public void setAcctReviewFlagDate(LocalDateTime acctReviewFlagDate) {
		this.acctReviewFlagDate = acctReviewFlagDate;
	}

	public Boolean getAcctReviewFlagWillClear() {
		return acctReviewFlagWillClear;
	}

	public void setAcctReviewFlagWillClear(Boolean acctReviewFlagWillClear) {
		this.acctReviewFlagWillClear = acctReviewFlagWillClear;
	}
}
