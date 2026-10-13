package com.mpx.im_acct_insp.model;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: 新增：緊急存取受影響帳號 IM_EMG_AFFECT_ACCT（BACKLOG 第 46 項 S2）＋帳號名稱、系統名稱、類別（JOIN IM_ACCT）、
//           登記人姓名（JOIN IM_ACCT_USER）
// ============================================================

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

public class EmgAffectAcct {

	private String emgId;
	private String affectTypeCode;
	private String acctId;
	private String acctName;
	private String sysName;
	private String catgId;
	private String affectDescr;
	private String recordBy;
	private String recordByName;
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime recordDate;

	public String getEmgId() {
		return emgId;
	}

	public void setEmgId(String emgId) {
		this.emgId = emgId;
	}

	public String getAffectTypeCode() {
		return affectTypeCode;
	}

	public void setAffectTypeCode(String affectTypeCode) {
		this.affectTypeCode = affectTypeCode;
	}

	public String getAcctId() {
		return acctId;
	}

	public void setAcctId(String acctId) {
		this.acctId = acctId;
	}

	public String getAcctName() {
		return acctName;
	}

	public void setAcctName(String acctName) {
		this.acctName = acctName;
	}

	public String getSysName() {
		return sysName;
	}

	public void setSysName(String sysName) {
		this.sysName = sysName;
	}

	public String getCatgId() {
		return catgId;
	}

	public void setCatgId(String catgId) {
		this.catgId = catgId;
	}

	public String getAffectDescr() {
		return affectDescr;
	}

	public void setAffectDescr(String affectDescr) {
		this.affectDescr = affectDescr;
	}

	public String getRecordBy() {
		return recordBy;
	}

	public void setRecordBy(String recordBy) {
		this.recordBy = recordBy;
	}

	public String getRecordByName() {
		return recordByName;
	}

	public void setRecordByName(String recordByName) {
		this.recordByName = recordByName;
	}

	public LocalDateTime getRecordDate() {
		return recordDate;
	}

	public void setRecordDate(LocalDateTime recordDate) {
		this.recordDate = recordDate;
	}
}
