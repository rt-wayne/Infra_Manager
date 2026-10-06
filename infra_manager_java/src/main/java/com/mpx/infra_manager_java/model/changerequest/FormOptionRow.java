package com.mpx.infra_manager_java.model.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-06
// 變更說明: 新增：IM_FORM_OPTION 查詢列（S6 回合二 a，表單選項 API）
// ============================================================

public class FormOptionRow {

	private Long formOptionId;
	private String groupCode;
	private String optionCode;
	private Long upFormOptionId;
	private String optionName;
	private String colorCode;
	private String optionDesc;
	private String timeLimitDesc;
	private String prioFlowDesc;
	private String sampleDesc;
	private String flowId;
	private Integer sortNo;

	public Long getFormOptionId() { return formOptionId; }
	public void setFormOptionId(Long formOptionId) { this.formOptionId = formOptionId; }
	public String getGroupCode() { return groupCode; }
	public void setGroupCode(String groupCode) { this.groupCode = groupCode; }
	public String getOptionCode() { return optionCode; }
	public void setOptionCode(String optionCode) { this.optionCode = optionCode; }
	public Long getUpFormOptionId() { return upFormOptionId; }
	public void setUpFormOptionId(Long upFormOptionId) { this.upFormOptionId = upFormOptionId; }
	public String getOptionName() { return optionName; }
	public void setOptionName(String optionName) { this.optionName = optionName; }
	public String getColorCode() { return colorCode; }
	public void setColorCode(String colorCode) { this.colorCode = colorCode; }
	public String getOptionDesc() { return optionDesc; }
	public void setOptionDesc(String optionDesc) { this.optionDesc = optionDesc; }
	public String getTimeLimitDesc() { return timeLimitDesc; }
	public void setTimeLimitDesc(String timeLimitDesc) { this.timeLimitDesc = timeLimitDesc; }
	public String getPrioFlowDesc() { return prioFlowDesc; }
	public void setPrioFlowDesc(String prioFlowDesc) { this.prioFlowDesc = prioFlowDesc; }
	public String getSampleDesc() { return sampleDesc; }
	public void setSampleDesc(String sampleDesc) { this.sampleDesc = sampleDesc; }
	public String getFlowId() { return flowId; }
	public void setFlowId(String flowId) { this.flowId = flowId; }
	public Integer getSortNo() { return sortNo; }
	public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
}
