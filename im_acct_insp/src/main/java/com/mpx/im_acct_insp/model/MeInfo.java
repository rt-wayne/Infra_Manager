package com.mpx.im_acct_insp.model;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：GET /me 回傳（spec §4.1）：身分名單欄位＋canAcctList、canInsp
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（spec_im_acct_insp_v2.0.md §6.2）：改為有效身分 userId／userName＋實際使用者 realUserId／realUserName、impersonating、
//           canImpersonate、permissions[]、roleIds[]、execCatgIds[]；另加 telNo、email（有效身分的 IM_ACCT_USER.TEL_NO、EMAIL，
//           供前端 §8.3「同申請人」帶入；主對話 2026-10-07 補需求）；刪 canAcctList、canInsp
// ============================================================

import java.util.List;

public class MeInfo {

	private String userId;
	private String userName;
	private String realUserId;
	private String realUserName;
	private Boolean impersonating;
	private Boolean canImpersonate;
	private List<String> permissions;
	private List<String> roleIds;
	private List<String> execCatgIds;
	private String telNo;
	private String email;

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public String getRealUserId() {
		return realUserId;
	}

	public void setRealUserId(String realUserId) {
		this.realUserId = realUserId;
	}

	public String getRealUserName() {
		return realUserName;
	}

	public void setRealUserName(String realUserName) {
		this.realUserName = realUserName;
	}

	public Boolean getImpersonating() {
		return impersonating;
	}

	public void setImpersonating(Boolean impersonating) {
		this.impersonating = impersonating;
	}

	public Boolean getCanImpersonate() {
		return canImpersonate;
	}

	public void setCanImpersonate(Boolean canImpersonate) {
		this.canImpersonate = canImpersonate;
	}

	public List<String> getPermissions() {
		return permissions;
	}

	public void setPermissions(List<String> permissions) {
		this.permissions = permissions;
	}

	public List<String> getRoleIds() {
		return roleIds;
	}

	public void setRoleIds(List<String> roleIds) {
		this.roleIds = roleIds;
	}

	public List<String> getExecCatgIds() {
		return execCatgIds;
	}

	public void setExecCatgIds(List<String> execCatgIds) {
		this.execCatgIds = execCatgIds;
	}

	public String getTelNo() {
		return telNo;
	}

	public void setTelNo(String telNo) {
		this.telNo = telNo;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}
}
