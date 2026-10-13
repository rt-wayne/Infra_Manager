package com.mpx.im_acct_insp.model;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: 新增：緊急存取 IM_EMG_ACCESS 全欄位（BACKLOG 第 46 項 S2）＋類別名稱、申請人／處理人／結案人姓名（JOIN）、
//           即時計算的 emgStateCode（PENDING／OVERDUE／APPROVED／RETURNED／CLOSED，由 EmgDao.STATE_EXPR 算出，不存欄位）
// ============================================================

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

public class Emg {

	private String emgId;
	private String catgId;
	private String catgName;
	private String targetDescr;
	private String applyBy;
	private String applyByName;
	private String applyReason;
	private Integer expectHourQty;
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime submitDate;
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime dueDate;
	private String reportTypeCode;
	private String reportDescr;
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime reportDate;
	private String reportActBy;
	private String decideCode;
	private String decideBy;
	private String decideByName;
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime decideDate;
	private String decideActBy;
	private String returnReason;
	private Integer isSelfDecide;
	private String closeBy;
	private String closeByName;
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime closeDate;
	private String closeActBy;
	private String closeDescr;
	private String closeResultCode;
	private Integer isSelfClose;
	private Integer versionNo;
	private String emgStateCode;

	public String getEmgId() {
		return emgId;
	}

	public void setEmgId(String emgId) {
		this.emgId = emgId;
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

	public String getTargetDescr() {
		return targetDescr;
	}

	public void setTargetDescr(String targetDescr) {
		this.targetDescr = targetDescr;
	}

	public String getApplyBy() {
		return applyBy;
	}

	public void setApplyBy(String applyBy) {
		this.applyBy = applyBy;
	}

	public String getApplyByName() {
		return applyByName;
	}

	public void setApplyByName(String applyByName) {
		this.applyByName = applyByName;
	}

	public String getApplyReason() {
		return applyReason;
	}

	public void setApplyReason(String applyReason) {
		this.applyReason = applyReason;
	}

	public Integer getExpectHourQty() {
		return expectHourQty;
	}

	public void setExpectHourQty(Integer expectHourQty) {
		this.expectHourQty = expectHourQty;
	}

	public LocalDateTime getSubmitDate() {
		return submitDate;
	}

	public void setSubmitDate(LocalDateTime submitDate) {
		this.submitDate = submitDate;
	}

	public LocalDateTime getDueDate() {
		return dueDate;
	}

	public void setDueDate(LocalDateTime dueDate) {
		this.dueDate = dueDate;
	}

	public String getReportTypeCode() {
		return reportTypeCode;
	}

	public void setReportTypeCode(String reportTypeCode) {
		this.reportTypeCode = reportTypeCode;
	}

	public String getReportDescr() {
		return reportDescr;
	}

	public void setReportDescr(String reportDescr) {
		this.reportDescr = reportDescr;
	}

	public LocalDateTime getReportDate() {
		return reportDate;
	}

	public void setReportDate(LocalDateTime reportDate) {
		this.reportDate = reportDate;
	}

	public String getReportActBy() {
		return reportActBy;
	}

	public void setReportActBy(String reportActBy) {
		this.reportActBy = reportActBy;
	}

	public String getDecideCode() {
		return decideCode;
	}

	public void setDecideCode(String decideCode) {
		this.decideCode = decideCode;
	}

	public String getDecideBy() {
		return decideBy;
	}

	public void setDecideBy(String decideBy) {
		this.decideBy = decideBy;
	}

	public String getDecideByName() {
		return decideByName;
	}

	public void setDecideByName(String decideByName) {
		this.decideByName = decideByName;
	}

	public LocalDateTime getDecideDate() {
		return decideDate;
	}

	public void setDecideDate(LocalDateTime decideDate) {
		this.decideDate = decideDate;
	}

	public String getDecideActBy() {
		return decideActBy;
	}

	public void setDecideActBy(String decideActBy) {
		this.decideActBy = decideActBy;
	}

	public String getReturnReason() {
		return returnReason;
	}

	public void setReturnReason(String returnReason) {
		this.returnReason = returnReason;
	}

	public Integer getIsSelfDecide() {
		return isSelfDecide;
	}

	public void setIsSelfDecide(Integer isSelfDecide) {
		this.isSelfDecide = isSelfDecide;
	}

	public String getCloseBy() {
		return closeBy;
	}

	public void setCloseBy(String closeBy) {
		this.closeBy = closeBy;
	}

	public String getCloseByName() {
		return closeByName;
	}

	public void setCloseByName(String closeByName) {
		this.closeByName = closeByName;
	}

	public LocalDateTime getCloseDate() {
		return closeDate;
	}

	public void setCloseDate(LocalDateTime closeDate) {
		this.closeDate = closeDate;
	}

	public String getCloseActBy() {
		return closeActBy;
	}

	public void setCloseActBy(String closeActBy) {
		this.closeActBy = closeActBy;
	}

	public String getCloseDescr() {
		return closeDescr;
	}

	public void setCloseDescr(String closeDescr) {
		this.closeDescr = closeDescr;
	}

	public String getCloseResultCode() {
		return closeResultCode;
	}

	public void setCloseResultCode(String closeResultCode) {
		this.closeResultCode = closeResultCode;
	}

	public Integer getIsSelfClose() {
		return isSelfClose;
	}

	public void setIsSelfClose(Integer isSelfClose) {
		this.isSelfClose = isSelfClose;
	}

	public Integer getVersionNo() {
		return versionNo;
	}

	public void setVersionNo(Integer versionNo) {
		this.versionNo = versionNo;
	}

	public String getEmgStateCode() {
		return emgStateCode;
	}

	public void setEmgStateCode(String emgStateCode) {
		this.emgStateCode = emgStateCode;
	}
}
