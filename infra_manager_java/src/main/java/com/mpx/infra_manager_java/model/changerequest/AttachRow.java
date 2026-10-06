package com.mpx.infra_manager_java.model.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：IM_ATTACH 附件查詢列（S4）；FILE_PATH 只在後端用來定位檔案，不回給前端
// ============================================================

import java.sql.Timestamp;

public class AttachRow {

	private Long attachId;
	private String ownerType;
	private String ownerId;
	private String origFileName;
	private String filePath;
	private Long fileByteQty;
	private String mimeType;
	private Timestamp createDate;

	public Long getAttachId() { return attachId; }
	public void setAttachId(Long attachId) { this.attachId = attachId; }
	public String getOwnerType() { return ownerType; }
	public void setOwnerType(String ownerType) { this.ownerType = ownerType; }
	public String getOwnerId() { return ownerId; }
	public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
	public String getOrigFileName() { return origFileName; }
	public void setOrigFileName(String origFileName) { this.origFileName = origFileName; }
	public String getFilePath() { return filePath; }
	public void setFilePath(String filePath) { this.filePath = filePath; }
	public Long getFileByteQty() { return fileByteQty; }
	public void setFileByteQty(Long fileByteQty) { this.fileByteQty = fileByteQty; }
	public String getMimeType() { return mimeType; }
	public void setMimeType(String mimeType) { this.mimeType = mimeType; }
	public Timestamp getCreateDate() { return createDate; }
	public void setCreateDate(Timestamp createDate) { this.createDate = createDate; }
}
