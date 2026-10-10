package com.mpx.im_acct_insp.model;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：GET /requests/{id} 明細＝申請單欄位＋history（IM_ACCT_HIST，依 ACT_DATE、HIST_ID）＋retryHint（VERIFY_REJECT_REASON）（spec §4.3）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（spec_im_acct_insp_v2.0.md §6.5）：加 attachments（STATUS＝1）、actions（我此刻可做）；刪 retryHint（改由 verifyRejectReason）
// ============================================================

import java.util.List;

public class AcctReqDetail extends AcctReq {

	private List<AcctAttach> attachments;
	private List<AcctHist> history;
	private List<String> actions;

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
}
