package com.mpx.infra_manager.model;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：申請單簽核動作（approve／reject／resubmit／exec-done／verify-ok／verify-reject）回傳：單號與動作後狀態
//           spec 未定義這幾支的回傳內容，回最小必要欄位供前端更新畫面
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（spec_im_acct_insp_v2.0.md §6.5）：加 versionNo（動作後的版本號）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// ============================================================

public class ReqActionResult {

	private String reqId;
	private String reqStateCode;
	private Integer currStageId;
	private Integer versionNo;

	public String getReqId() {
		return reqId;
	}

	public void setReqId(String reqId) {
		this.reqId = reqId;
	}

	public String getReqStateCode() {
		return reqStateCode;
	}

	public void setReqStateCode(String reqStateCode) {
		this.reqStateCode = reqStateCode;
	}

	public Integer getCurrStageId() {
		return currStageId;
	}

	public void setCurrStageId(Integer currStageId) {
		this.currStageId = currStageId;
	}

	public Integer getVersionNo() {
		return versionNo;
	}

	public void setVersionNo(Integer versionNo) {
		this.versionNo = versionNo;
	}
}
