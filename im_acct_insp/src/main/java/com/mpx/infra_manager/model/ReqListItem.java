package com.mpx.infra_manager.model;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：GET /requests 我的申請單一列（spec v2.0 §6.5）；canResubmit＝已退件且為原申請人
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// ============================================================

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class ReqListItem {

	private String reqId;
	private String acctId;
	private String reqTypeCode;
	private String reqStateCode;
	private Integer currStageId;
	private String stageName;
	private String catgId;
	private String catgName;
	private String acctName;
	private String sysName;
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime applyDate;
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime submitDate;
	private String rejectReason;
	private Boolean canResubmit;
	@JsonIgnore
	private String applyBy;

	public String getReqId() {
		return reqId;
	}

	public void setReqId(String reqId) {
		this.reqId = reqId;
	}

	public String getAcctId() {
		return acctId;
	}

	public void setAcctId(String acctId) {
		this.acctId = acctId;
	}

	public String getReqTypeCode() {
		return reqTypeCode;
	}

	public void setReqTypeCode(String reqTypeCode) {
		this.reqTypeCode = reqTypeCode;
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

	public String getStageName() {
		return stageName;
	}

	public void setStageName(String stageName) {
		this.stageName = stageName;
	}

	public String getCatgId() {
		return catgId;
	}

	public void setCatgId(String catgId) {
		this.catgId = catgId;
	}

	public String getCatgName() {
		return catgName;
	}

	public void setCatgName(String catgName) {
		this.catgName = catgName;
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

	public LocalDateTime getApplyDate() {
		return applyDate;
	}

	public void setApplyDate(LocalDateTime applyDate) {
		this.applyDate = applyDate;
	}

	public LocalDateTime getSubmitDate() {
		return submitDate;
	}

	public void setSubmitDate(LocalDateTime submitDate) {
		this.submitDate = submitDate;
	}

	public String getRejectReason() {
		return rejectReason;
	}

	public void setRejectReason(String rejectReason) {
		this.rejectReason = rejectReason;
	}

	public Boolean getCanResubmit() {
		return canResubmit;
	}

	public void setCanResubmit(Boolean canResubmit) {
		this.canResubmit = canResubmit;
	}

	public String getApplyBy() {
		return applyBy;
	}

	public void setApplyBy(String applyBy) {
		this.applyBy = applyBy;
	}
}
