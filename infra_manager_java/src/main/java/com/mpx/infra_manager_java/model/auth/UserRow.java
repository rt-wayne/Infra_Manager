package com.mpx.infra_manager_java.model.auth;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：IM_USER 查詢結果（S2）；BeanPropertyRowMapper 需無參數建構子與 setter，欄位名由底線轉駝峰
// ============================================================

public class UserRow {

	private String userId;
	private String loginId;
	private String userName;
	private String email;
	private String jobTitle;
	private String deptName;
	private String tel;
	private String pwdHash;
	private Integer isDfltPwd;
	private Integer status;

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getLoginId() {
		return loginId;
	}

	public void setLoginId(String loginId) {
		this.loginId = loginId;
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getJobTitle() {
		return jobTitle;
	}

	public void setJobTitle(String jobTitle) {
		this.jobTitle = jobTitle;
	}

	public String getDeptName() {
		return deptName;
	}

	public void setDeptName(String deptName) {
		this.deptName = deptName;
	}

	public String getTel() {
		return tel;
	}

	public void setTel(String tel) {
		this.tel = tel;
	}

	public String getPwdHash() {
		return pwdHash;
	}

	public void setPwdHash(String pwdHash) {
		this.pwdHash = pwdHash;
	}

	public Integer getIsDfltPwd() {
		return isDfltPwd;
	}

	public void setIsDfltPwd(Integer isDfltPwd) {
		this.isDfltPwd = isDfltPwd;
	}

	public Integer getStatus() {
		return status;
	}

	public void setStatus(Integer status) {
		this.status = status;
	}
}
