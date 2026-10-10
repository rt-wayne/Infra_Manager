package com.mpx.im_acct_insp.model.request;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：exec-done 的 body {cardNo, execDescr, attachFileName}
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（spec_im_acct_insp_v2.0.md §6.5）：改為 multipart 的 part form {versionNo, cardNo, execDescr}；佐證改上傳檔案 part proofFile
// ============================================================

public class ExecDoneForm {

	private Integer versionNo;
	private String cardNo;
	private String execDescr;

	public Integer getVersionNo() {
		return versionNo;
	}

	public void setVersionNo(Integer versionNo) {
		this.versionNo = versionNo;
	}

	public String getCardNo() {
		return cardNo;
	}

	public void setCardNo(String cardNo) {
		this.cardNo = cardNo;
	}

	public String getExecDescr() {
		return execDescr;
	}

	public void setExecDescr(String execDescr) {
		this.execDescr = execDescr;
	}
}
