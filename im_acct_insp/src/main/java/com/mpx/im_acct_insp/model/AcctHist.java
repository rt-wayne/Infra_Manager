package com.mpx.im_acct_insp.model;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：帳號歷史 IM_ACCT_HIST＋actByName、stageName（spec §3.6、§4.2）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（spec_im_acct_insp_v2.0.md §4.8、§6.4；Table v0.2 7.17）：actByName 改為快照欄（不 JOIN 使用者表）；
//           加 actAsId、actAsName、isSelfAction、selfActionLabel（計算）；reqApplyBy（該單申請人，算標籤用，不輸出）；
//           寫入用 createBy（不輸出）
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S4b-1：加 emgId（SOURCE_CODE＝EMG 時的來源緊急存取單號）
// ============================================================

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class AcctHist {

	private Long histId;
	private String acctId;
	private String reqId;
	private String emgId;
	private String sourceCode;
	private String actionCode;
	private Integer stageId;
	private String stageName;
	private String actBy;
	private String actByName;
	private String actAsId;
	private String actAsName;
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime actDate;
	private String applyDept;
	private String resultDescr;
	private Integer isSelfAction;
	private String selfActionLabel;
	@JsonIgnore
	private String reqApplyBy;
	@JsonIgnore
	private String createBy;

	public Long getHistId() {
		return histId;
	}

	public void setHistId(Long histId) {
		this.histId = histId;
	}

	public String getAcctId() {
		return acctId;
	}

	public void setAcctId(String acctId) {
		this.acctId = acctId;
	}

	public String getEmgId() {
		return emgId;
	}

	public void setEmgId(String emgId) {
		this.emgId = emgId;
	}

	public String getReqId() {
		return reqId;
	}

	public void setReqId(String reqId) {
		this.reqId = reqId;
	}

	public String getSourceCode() {
		return sourceCode;
	}

	public void setSourceCode(String sourceCode) {
		this.sourceCode = sourceCode;
	}

	public String getActionCode() {
		return actionCode;
	}

	public void setActionCode(String actionCode) {
		this.actionCode = actionCode;
	}

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

	public String getActBy() {
		return actBy;
	}

	public void setActBy(String actBy) {
		this.actBy = actBy;
	}

	public String getActByName() {
		return actByName;
	}

	public void setActByName(String actByName) {
		this.actByName = actByName;
	}

	public String getActAsId() {
		return actAsId;
	}

	public void setActAsId(String actAsId) {
		this.actAsId = actAsId;
	}

	public String getActAsName() {
		return actAsName;
	}

	public void setActAsName(String actAsName) {
		this.actAsName = actAsName;
	}

	public LocalDateTime getActDate() {
		return actDate;
	}

	public void setActDate(LocalDateTime actDate) {
		this.actDate = actDate;
	}

	public String getApplyDept() {
		return applyDept;
	}

	public void setApplyDept(String applyDept) {
		this.applyDept = applyDept;
	}

	public String getResultDescr() {
		return resultDescr;
	}

	public void setResultDescr(String resultDescr) {
		this.resultDescr = resultDescr;
	}

	public Integer getIsSelfAction() {
		return isSelfAction;
	}

	public void setIsSelfAction(Integer isSelfAction) {
		this.isSelfAction = isSelfAction;
	}

	public String getSelfActionLabel() {
		return selfActionLabel;
	}

	public void setSelfActionLabel(String selfActionLabel) {
		this.selfActionLabel = selfActionLabel;
	}

	public String getReqApplyBy() {
		return reqApplyBy;
	}

	public void setReqApplyBy(String reqApplyBy) {
		this.reqApplyBy = reqApplyBy;
	}

	public String getCreateBy() {
		return createBy;
	}

	public void setCreateBy(String createBy) {
		this.createBy = createBy;
	}
}
