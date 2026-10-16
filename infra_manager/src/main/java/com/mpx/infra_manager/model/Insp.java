package com.mpx.infra_manager.model;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：巡檢單 IM_INSP 全欄位＋inspByName（spec §4.4）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（spec_im_acct_insp_v2.0.md §4.14、§6.6；Table v0.2 7.18）：刪 INSP_STATE_CODE、EXCPT_CNT 欄位，
//           改由共用 SQL 即時計算 excptCnt、unfixedCnt、inspStateCode；inspByName、actAsName 為快照欄
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// ============================================================

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

public class Insp {

	private String inspId;
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime inspDate;
	private String inspBy;
	private String inspByName;
	private String actAsId;
	private String actAsName;
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime signDate;
	private Integer excptCnt;
	private Integer unfixedCnt;
	private String inspStateCode;

	public String getInspId() {
		return inspId;
	}

	public void setInspId(String inspId) {
		this.inspId = inspId;
	}

	public LocalDateTime getInspDate() {
		return inspDate;
	}

	public void setInspDate(LocalDateTime inspDate) {
		this.inspDate = inspDate;
	}

	public String getInspBy() {
		return inspBy;
	}

	public void setInspBy(String inspBy) {
		this.inspBy = inspBy;
	}

	public String getInspByName() {
		return inspByName;
	}

	public void setInspByName(String inspByName) {
		this.inspByName = inspByName;
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

	public LocalDateTime getSignDate() {
		return signDate;
	}

	public void setSignDate(LocalDateTime signDate) {
		this.signDate = signDate;
	}

	public Integer getExcptCnt() {
		return excptCnt;
	}

	public void setExcptCnt(Integer excptCnt) {
		this.excptCnt = excptCnt;
	}

	public Integer getUnfixedCnt() {
		return unfixedCnt;
	}

	public void setUnfixedCnt(Integer unfixedCnt) {
		this.unfixedCnt = unfixedCnt;
	}

	public String getInspStateCode() {
		return inspStateCode;
	}

	public void setInspStateCode(String inspStateCode) {
		this.inspStateCode = inspStateCode;
	}
}
