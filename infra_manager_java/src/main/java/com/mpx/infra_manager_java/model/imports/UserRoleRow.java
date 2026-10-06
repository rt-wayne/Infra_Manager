package com.mpx.infra_manager_java.model.imports;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：IM_USER_ROLE_MAP 查詢結果（S2 回合二匯入器）；BeanPropertyRowMapper 需無參數建構子與 setter
// ============================================================

public class UserRoleRow {

	private String roleId;
	private Integer status;

	public String getRoleId() {
		return roleId;
	}

	public void setRoleId(String roleId) {
		this.roleId = roleId;
	}

	public Integer getStatus() {
		return status;
	}

	public void setStatus(Integer status) {
		this.status = status;
	}
}
