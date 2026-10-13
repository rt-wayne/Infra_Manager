package com.mpx.im_acct_insp.model;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: 新增：GET /emergencies/{id} 明細＝緊急存取單欄位＋受影響帳號 affectAccts（BACKLOG 第 46 項 S2；S2 階段恆為空）
// ============================================================

import java.util.List;

public class EmgDetail extends Emg {

	private List<EmgAffectAcct> affectAccts;

	public List<EmgAffectAcct> getAffectAccts() {
		return affectAccts;
	}

	public void setAffectAccts(List<EmgAffectAcct> affectAccts) {
		this.affectAccts = affectAccts;
	}
}
