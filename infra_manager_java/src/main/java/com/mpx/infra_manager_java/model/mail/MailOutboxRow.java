package com.mpx.infra_manager_java.model.mail;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-08
// 變更說明: 新增：IM_MAIL_OUTBOX 一列（S8 R1）。worker 鎖到待寄列後讀出寄送所需欄位；
//           TO_JSON／CC_JSON／BCC_JSON 仍是 JSON 字串，由 MailDispatcher 解析
// ============================================================

import java.sql.Timestamp;

public class MailOutboxRow {

	private Long mailOutboxId;
	private String toJson;
	private String ccJson;
	private String bccJson;
	private String mailSubj;
	private String htmlBody;
	private String mailStatusCode;
	private Integer tryCnt;
	private String errorText;
	private String smtpMsgId;
	private Timestamp sendDate;
	private String metaJson;
	private Timestamp createDate;
	private String createBy;
	private Timestamp updateDate;
	private String updateBy;

	public Long getMailOutboxId() { return mailOutboxId; }
	public void setMailOutboxId(Long mailOutboxId) { this.mailOutboxId = mailOutboxId; }
	public String getToJson() { return toJson; }
	public void setToJson(String toJson) { this.toJson = toJson; }
	public String getCcJson() { return ccJson; }
	public void setCcJson(String ccJson) { this.ccJson = ccJson; }
	public String getBccJson() { return bccJson; }
	public void setBccJson(String bccJson) { this.bccJson = bccJson; }
	public String getMailSubj() { return mailSubj; }
	public void setMailSubj(String mailSubj) { this.mailSubj = mailSubj; }
	public String getHtmlBody() { return htmlBody; }
	public void setHtmlBody(String htmlBody) { this.htmlBody = htmlBody; }
	public String getMailStatusCode() { return mailStatusCode; }
	public void setMailStatusCode(String mailStatusCode) { this.mailStatusCode = mailStatusCode; }
	public Integer getTryCnt() { return tryCnt; }
	public void setTryCnt(Integer tryCnt) { this.tryCnt = tryCnt; }
	public String getErrorText() { return errorText; }
	public void setErrorText(String errorText) { this.errorText = errorText; }
	public String getSmtpMsgId() { return smtpMsgId; }
	public void setSmtpMsgId(String smtpMsgId) { this.smtpMsgId = smtpMsgId; }
	public Timestamp getSendDate() { return sendDate; }
	public void setSendDate(Timestamp sendDate) { this.sendDate = sendDate; }
	public String getMetaJson() { return metaJson; }
	public void setMetaJson(String metaJson) { this.metaJson = metaJson; }
	public Timestamp getCreateDate() { return createDate; }
	public void setCreateDate(Timestamp createDate) { this.createDate = createDate; }
	public String getCreateBy() { return createBy; }
	public void setCreateBy(String createBy) { this.createBy = createBy; }
	public Timestamp getUpdateDate() { return updateDate; }
	public void setUpdateDate(Timestamp updateDate) { this.updateDate = updateDate; }
	public String getUpdateBy() { return updateBy; }
	public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }
}
