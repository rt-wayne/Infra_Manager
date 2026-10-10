package com.mpx.im_acct_insp.model;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：身分（GET /users 一筆；也是後端驗權用的登入者資料）（spec §2、§4.1）
//           roleIds 來自 IM_ACCT_ROLE_USER，execCatgIds 來自 IM_EXEC_MAP（皆 STATUS＝1）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（spec_im_acct_insp_v2.0.md §6.2）：只作 GET /users 一筆（userId、userName 顯示名、roleIds）；驗權改用 common.Identity
// ============================================================

import java.util.List;

public class UserInfo {

	private String userId;
	private String userName;
	private List<String> roleIds;

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

	public List<String> getRoleIds() {
		return roleIds;
	}

	public void setRoleIds(List<String> roleIds) {
		this.roleIds = roleIds;
	}
}
