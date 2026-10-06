package com.mpx.infra_manager_java.model.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：申請單勾選的表單選項（類別／類別子項／原因／影響範圍）查詢列（S4）；
//           UP_OPTION_CODE 是上層類別代碼（子項才有），OTHER_TEXT 是類別「其他」補充文字
//           S6 回合二 a（Claude Opus 5.5，2026-10-06）：加 formOptionId（編輯頁回填勾選用）
// ============================================================

public class OptionRow {

	private Long formOptionId;
	private String groupCode;
	private String optionCode;
	private String optionName;
	private String upOptionCode;
	private String otherText;
	private Integer sortNo;

	public Long getFormOptionId() { return formOptionId; }
	public void setFormOptionId(Long formOptionId) { this.formOptionId = formOptionId; }
	public String getGroupCode() { return groupCode; }
	public void setGroupCode(String groupCode) { this.groupCode = groupCode; }
	public String getOptionCode() { return optionCode; }
	public void setOptionCode(String optionCode) { this.optionCode = optionCode; }
	public String getOptionName() { return optionName; }
	public void setOptionName(String optionName) { this.optionName = optionName; }
	public String getUpOptionCode() { return upOptionCode; }
	public void setUpOptionCode(String upOptionCode) { this.upOptionCode = upOptionCode; }
	public String getOtherText() { return otherText; }
	public void setOtherText(String otherText) { this.otherText = otherText; }
	public Integer getSortNo() { return sortNo; }
	public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
}
