package com.mpx.infra_manager_java.model.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：IM_APP_EQUIP 設備清單查詢列（S4）
// ============================================================

public class EquipRow {

	private Integer seqNo;
	private String equipName;
	private String assetNo;
	private String modelNo;
	private String serialNo;
	private String purpDesc;
	private String mgmtIp;

	public Integer getSeqNo() { return seqNo; }
	public void setSeqNo(Integer seqNo) { this.seqNo = seqNo; }
	public String getEquipName() { return equipName; }
	public void setEquipName(String equipName) { this.equipName = equipName; }
	public String getAssetNo() { return assetNo; }
	public void setAssetNo(String assetNo) { this.assetNo = assetNo; }
	public String getModelNo() { return modelNo; }
	public void setModelNo(String modelNo) { this.modelNo = modelNo; }
	public String getSerialNo() { return serialNo; }
	public void setSerialNo(String serialNo) { this.serialNo = serialNo; }
	public String getPurpDesc() { return purpDesc; }
	public void setPurpDesc(String purpDesc) { this.purpDesc = purpDesc; }
	public String getMgmtIp() { return mgmtIp; }
	public void setMgmtIp(String mgmtIp) { this.mgmtIp = mgmtIp; }
}
