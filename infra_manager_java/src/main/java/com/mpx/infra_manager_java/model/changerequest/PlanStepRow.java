package com.mpx.infra_manager_java.model.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：IM_APP_PLAN_STEP 施工步驟查詢列（S4）
// ============================================================

public class PlanStepRow {

	private Integer seqNo;
	private String stepText;

	public Integer getSeqNo() { return seqNo; }
	public void setSeqNo(Integer seqNo) { this.seqNo = seqNo; }
	public String getStepText() { return stepText; }
	public void setStepText(String stepText) { this.stepText = stepText; }
}
