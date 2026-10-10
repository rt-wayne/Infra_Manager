package com.mpx.im_acct_insp.model;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：GET /home/expiring 鈴鐺清單一筆（spec §3.7）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（spec_im_acct_insp_v2.0.md §4.13、§6.3）：加 catgId、acctStateCode（含已逾期）；daysLeft 由 Service 以 today 計算；
//           applyBy（原新增申請人，判斷逐筆相關性用，不輸出）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 變更單 #1（L4）：applyBy 的 getter 也加 @JsonIgnore，確保 /home/expiring 回應不含 applyBy（spec §6.3）
// ============================================================

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class ExpiringAcct {

	private String acctId;
	private String acctName;
	private String catgId;
	private String catgName;
	private String acctStateCode;
	@JsonFormat(pattern = "yyyy-MM-dd")
	private LocalDateTime expiryDate;
	private Long daysLeft;
	@JsonIgnore
	private String applyBy;

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

	public String getAcctStateCode() {
		return acctStateCode;
	}

	public void setAcctStateCode(String acctStateCode) {
		this.acctStateCode = acctStateCode;
	}

	public LocalDateTime getExpiryDate() {
		return expiryDate;
	}

	public void setExpiryDate(LocalDateTime expiryDate) {
		this.expiryDate = expiryDate;
	}

	public Long getDaysLeft() {
		return daysLeft;
	}

	public void setDaysLeft(Long daysLeft) {
		this.daysLeft = daysLeft;
	}

	@JsonIgnore
	public String getApplyBy() {
		return applyBy;
	}

	public void setApplyBy(String applyBy) {
		this.applyBy = applyBy;
	}
}
