package com.mpx.im_acct_insp.model;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：GROUP BY 計數查詢的一列（別名 CODE、CNT），帳號列表各狀態／類別數量用
// ============================================================

public class CodeCountRow {

	private String code;
	private Long cnt;

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public Long getCnt() {
		return cnt;
	}

	public void setCnt(Long cnt) {
		this.cnt = cnt;
	}
}
