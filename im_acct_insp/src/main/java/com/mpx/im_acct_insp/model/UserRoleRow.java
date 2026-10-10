package com.mpx.im_acct_insp.model;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：身分查詢的一列（KIND＝ROLE 時 CODE 為 ROLE_ID，KIND＝EXEC 時 CODE 為 CATG_ID），由 IdentityService 依工號彙整
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0：改為 GET /users 名單查詢的一列（IM_ACCT_USER LEFT JOIN 啟用中的 IM_ACCT_ROLE_USER），由 AuthService 依工號彙整
// ============================================================

public class UserRoleRow {

	private String userId;
	private String userEnName;
	private String userName;
	private String roleId;

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getUserEnName() {
		return userEnName;
	}

	public void setUserEnName(String userEnName) {
		this.userEnName = userEnName;
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public String getRoleId() {
		return roleId;
	}

	public void setRoleId(String roleId) {
		this.roleId = roleId;
	}
}
