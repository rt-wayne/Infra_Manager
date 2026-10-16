package com.mpx.infra_manager.model;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：POST /jobs/{code}/run 回傳（spec v2.0 §6.7）：runId、resultCode、affectedCnt
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
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
