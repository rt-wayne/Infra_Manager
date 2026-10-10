package com.mpx.im_acct_insp.model;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：POST /jobs/{code}/run 回傳（spec v2.0 §6.7）：runId、resultCode、affectedCnt
// ============================================================

public class JobRunResult {

	private Long runId;
	private String resultCode;
	private Integer affectedCnt;

	public Long getRunId() {
		return runId;
	}

	public void setRunId(Long runId) {
		this.runId = runId;
	}

	public String getResultCode() {
		return resultCode;
	}

	public void setResultCode(String resultCode) {
		this.resultCode = resultCode;
	}

	public Integer getAffectedCnt() {
		return affectedCnt;
	}

	public void setAffectedCnt(Integer affectedCnt) {
		this.affectedCnt = affectedCnt;
	}
}
