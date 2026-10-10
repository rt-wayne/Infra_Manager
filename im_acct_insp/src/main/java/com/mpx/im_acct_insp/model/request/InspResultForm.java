package com.mpx.im_acct_insp.model.request;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：POST /inspections results 的一項
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0：resultCode 只接受 NORMAL／EXCPT（刪 NA）；欄位不變
// ============================================================

public class InspResultForm {

	private String inspItemId;
	private String resultCode;
	private String excptDescr;

	public String getInspItemId() {
		return inspItemId;
	}

	public void setInspItemId(String inspItemId) {
		this.inspItemId = inspItemId;
	}

	public String getResultCode() {
		return resultCode;
	}

	public void setResultCode(String resultCode) {
		this.resultCode = resultCode;
	}

	public String getExcptDescr() {
		return excptDescr;
	}

	public void setExcptDescr(String excptDescr) {
		this.excptDescr = excptDescr;
	}
}
