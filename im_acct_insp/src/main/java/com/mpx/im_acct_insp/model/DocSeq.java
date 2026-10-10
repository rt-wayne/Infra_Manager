package com.mpx.im_acct_insp.model;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：IM_DOC_SEQ 目前流水號（取號 CAS 用）
// ============================================================

public class DocSeq {

	private Integer currSeq;

	public Integer getCurrSeq() {
		return currSeq;
	}

	public void setCurrSeq(Integer currSeq) {
		this.currSeq = currSeq;
	}
}
