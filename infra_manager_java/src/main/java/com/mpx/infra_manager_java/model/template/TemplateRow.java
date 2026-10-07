package com.mpx.infra_manager_java.model.template;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：IM_TMPL 一列（S5 R1）。列表查詢不帶 FORM_JSON（formJson 為 null），優先等級以 JSON_VALUE 取出；
//           建立者與最後套用者姓名由 IM_USER 帶出
// ============================================================

import java.sql.Timestamp;

public class TemplateRow {

	private String tmplId;
	private String tmplName;
	private String prioCode;
	private String prioName;
	private String prioColor;
	private String formJson;
	private String ownerUserId;
	private String ownerName;
	private Integer useCnt;
	private Timestamp lastUseDate;
	private String lastUseUserName;
	private Timestamp createDate;
	private Timestamp updateDate;

	public String getTmplId() { return tmplId; }
	public void setTmplId(String tmplId) { this.tmplId = tmplId; }
	public String getTmplName() { return tmplName; }
	public void setTmplName(String tmplName) { this.tmplName = tmplName; }
	public String getPrioCode() { return prioCode; }
	public void setPrioCode(String prioCode) { this.prioCode = prioCode; }
	public String getPrioName() { return prioName; }
	public void setPrioName(String prioName) { this.prioName = prioName; }
	public String getPrioColor() { return prioColor; }
	public void setPrioColor(String prioColor) { this.prioColor = prioColor; }
	public String getFormJson() { return formJson; }
	public void setFormJson(String formJson) { this.formJson = formJson; }
	public String getOwnerUserId() { return ownerUserId; }
	public void setOwnerUserId(String ownerUserId) { this.ownerUserId = ownerUserId; }
	public String getOwnerName() { return ownerName; }
	public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
	public Integer getUseCnt() { return useCnt; }
	public void setUseCnt(Integer useCnt) { this.useCnt = useCnt; }
	public Timestamp getLastUseDate() { return lastUseDate; }
	public void setLastUseDate(Timestamp lastUseDate) { this.lastUseDate = lastUseDate; }
	public String getLastUseUserName() { return lastUseUserName; }
	public void setLastUseUserName(String lastUseUserName) { this.lastUseUserName = lastUseUserName; }
	public Timestamp getCreateDate() { return createDate; }
	public void setCreateDate(Timestamp createDate) { this.createDate = createDate; }
	public Timestamp getUpdateDate() { return updateDate; }
	public void setUpdateDate(Timestamp updateDate) { this.updateDate = updateDate; }
}
