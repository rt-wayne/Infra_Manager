package com.mpx.im_acct_insp.common;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：目前身分（spec v2.0 §2.2）：實際使用者、有效身分（切換中為代理身分）、是否切換中，
//           以及有效身分當下的權限碼（含 GENERAL）、角色、執行人類別。
//           權限與資料範圍一律用有效身分；稽核欄位（ACT_BY、INSP_BY、FIX_BY、CREATE_BY、UPDATE_BY）用實際使用者，
//           代理身分記在 ACT_AS_*。由 IdentityProvider 每次請求自 DB 建立，不跨請求快取（spec §2.3）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-11
// 變更說明: BACKLOG 第 40 項：加 deptName（有效身分的 IM_ACCT_USER.DEPT_NAME，供 /me 給新增申請帶入申請單位）
// ============================================================

import java.util.List;
import java.util.Set;

public class Identity {

	private final String tokenHash;
	private final String realUserId;
	private final String realUserName;
	private final String userId;
	private final String userName;
	private final boolean impersonating;
	private final Set<String> permissions;
	private final List<String> roleIds;
	private final List<String> execCatgIds;
	private final String email;
	private final String telNo;
	private final String deptName;

	public Identity(String tokenHash, String realUserId, String realUserName, String userId, String userName,
			boolean impersonating, Set<String> permissions, List<String> roleIds, List<String> execCatgIds, String email,
			String telNo, String deptName) {
		this.tokenHash = tokenHash;
		this.realUserId = realUserId;
		this.realUserName = realUserName;
		this.userId = userId;
		this.userName = userName;
		this.impersonating = impersonating;
		this.permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
		this.roleIds = roleIds == null ? List.of() : List.copyOf(roleIds);
		this.execCatgIds = execCatgIds == null ? List.of() : List.copyOf(execCatgIds);
		this.email = email;
		this.telNo = telNo;
		this.deptName = deptName;
	}

	/** 登入 token 的 SHA-256（登出、切換身分用；不是 token 本身） */
	public String getTokenHash() {
		return tokenHash;
	}

	public String getRealUserId() {
		return realUserId;
	}

	public String getRealUserName() {
		return realUserName;
	}

	/** 有效身分工號（切換中為代理身分） */
	public String getUserId() {
		return userId;
	}

	public String getUserName() {
		return userName;
	}

	public boolean isImpersonating() {
		return impersonating;
	}

	public Set<String> getPermissions() {
		return permissions;
	}

	public List<String> getRoleIds() {
		return roleIds;
	}

	public List<String> getExecCatgIds() {
		return execCatgIds;
	}

	public String getEmail() {
		return email;
	}

	public String getTelNo() {
		return telNo;
	}

	public String getDeptName() {
		return deptName;
	}

	/** 寫入 ACT_AS_ID 的值：切換中為有效身分，否則 null */
	public String actAsId() {
		return impersonating ? userId : null;
	}

	public String actAsName() {
		return impersonating ? userName : null;
	}
}
