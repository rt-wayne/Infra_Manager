package com.mpx.infra_manager.model;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：IM_DOC_SEQ 目前流水號（取號 CAS 用）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
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
