package com.mpx.im_acct_insp.model;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：簽核關卡 IM_ACCT_APPR_STAGE（GET /codes stages）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（Table v0.2 7.9）：ROLE_ID 換成 PERM_ID（處理此關所需權限；關卡 1、6 為空）
// ============================================================

public class ApprStage {

	private Integer stageId;
	private String stageName;
	private String stageTypeCode;
	private String permId;

	public Integer getStageId() {
		return stageId;
	}

	public void setStageId(Integer stageId) {
		this.stageId = stageId;
	}

	public String getStageName() {
		return stageName;
	}

	public void setStageName(String stageName) {
		this.stageName = stageName;
	}

	public String getStageTypeCode() {
		return stageTypeCode;
	}

	public void setStageTypeCode(String stageTypeCode) {
		this.stageTypeCode = stageTypeCode;
	}

	public String getPermId() {
		return permId;
	}

	public void setPermId(String permId) {
		this.permId = permId;
	}
}
