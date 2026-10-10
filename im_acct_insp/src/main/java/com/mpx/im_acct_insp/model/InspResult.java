package com.mpx.im_acct_insp.model;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：巡檢結果 IM_INSP_RESULT＋itemName、sectCode、sectName、sortSeq、fixByName（spec §4.4）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（spec_im_acct_insp_v2.0.md §6.6；Table v0.2 7.19）：名稱改用快照 itemNameSnap、sectNameSnap（不 JOIN 項目表）；
//           fixByName、fixActAsName 為快照欄
// ============================================================

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class InspResult {

	@JsonIgnore
	private String inspId;
	private String inspItemId;
	private String sectNameSnap;
	private String itemNameSnap;
	private String resultCode;
	private String excptDescr;
	private Integer isFixed;
	private String fixDescr;
	@JsonIgnore
	private String fixBy;
	private String fixByName;
	@JsonIgnore
	private String fixActAsId;
	private String fixActAsName;
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime fixDate;

	public String getInspId() {
		return inspId;
	}

	public void setInspId(String inspId) {
		this.inspId = inspId;
	}

	public String getInspItemId() {
		return inspItemId;
	}

	public void setInspItemId(String inspItemId) {
		this.inspItemId = inspItemId;
	}

	public String getSectNameSnap() {
		return sectNameSnap;
	}

	public void setSectNameSnap(String sectNameSnap) {
		this.sectNameSnap = sectNameSnap;
	}

	public String getItemNameSnap() {
		return itemNameSnap;
	}

	public void setItemNameSnap(String itemNameSnap) {
		this.itemNameSnap = itemNameSnap;
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

	public Integer getIsFixed() {
		return isFixed;
	}

	public void setIsFixed(Integer isFixed) {
		this.isFixed = isFixed;
	}

	public String getFixDescr() {
		return fixDescr;
	}

	public void setFixDescr(String fixDescr) {
		this.fixDescr = fixDescr;
	}

	public String getFixBy() {
		return fixBy;
	}

	public void setFixBy(String fixBy) {
		this.fixBy = fixBy;
	}

	public String getFixByName() {
		return fixByName;
	}

	public void setFixByName(String fixByName) {
		this.fixByName = fixByName;
	}

	public String getFixActAsId() {
		return fixActAsId;
	}

	public void setFixActAsId(String fixActAsId) {
		this.fixActAsId = fixActAsId;
	}

	public String getFixActAsName() {
		return fixActAsName;
	}

	public void setFixActAsName(String fixActAsName) {
		this.fixActAsName = fixActAsName;
	}

	public LocalDateTime getFixDate() {
		return fixDate;
	}

	public void setFixDate(LocalDateTime fixDate) {
		this.fixDate = fixDate;
	}
}
