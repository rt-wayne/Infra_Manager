package com.mpx.infra_manager_java.dao.imports;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：使用者匯入的 IM_USER／IM_ROLE／IM_USER_ROLE_MAP 讀寫（S2 回合二匯入器）。
//           表名經 DbSchema.table() 加 schema 前綴（第 77 項裁示 A）；所有值一律 :name 綁定，含 CREATE_BY／UPDATE_BY 的 SYSTEM。
//           每個方法都是單句；交易範圍由 service 的 @Transactional 決定（AppTransactionConfig），本類別不管 commit。
//           可為 NULL 的欄位（EMAIL 等）用 HashMap 綁 null（Map.of 不收 null）。
// ============================================================

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.mpx.common.db.DbClient;
import com.mpx.infra_manager_java.config.DbSchema;
import com.mpx.infra_manager_java.model.auth.RoleRow;
import com.mpx.infra_manager_java.model.auth.UserRow;
import com.mpx.infra_manager_java.model.imports.UserRoleRow;

@Repository
public class UserImportDao {

	/** 匯入寫入的 CREATE_BY／UPDATE_BY */
	public static final String IMPORT_BY = "SYSTEM";

	private final DbClient dbClient;
	private final DbSchema schema;
	private final String itflowDb;

	public UserImportDao(DbClient dbClient, DbSchema schema, @Value("${db.connect.itflow}") String itflowDb) {
		this.dbClient = dbClient;
		this.schema = schema;
		this.itflowDb = itflowDb;
	}

	/** 角色主檔全部 ROLE_ID（不分啟用狀態；未知角色的判斷以主檔有無為準） */
	public List<String> findAllRoleIds() {
		String sql = "SELECT ROLE_ID FROM " + schema.table("IM_ROLE") + " ORDER BY ROLE_ID";
		return dbClient.query(itflowDb, sql, Map.of(), RoleRow.class).stream().map(RoleRow::getRoleId).toList();
	}

	/** 所有使用者的工號、帳號、狀態（判斷新增或更新、檢查帳號是否被別的工號佔用） */
	public List<UserRow> findAllUsers() {
		String sql = "SELECT USER_ID, LOGIN_ID, STATUS FROM " + schema.table("IM_USER");
		return dbClient.query(itflowDb, sql, Map.of(), UserRow.class);
	}

	/** 某工號目前所有角色對照（含停用） */
	public List<UserRoleRow> findRoles(String userId) {
		String sql = "SELECT ROLE_ID, STATUS FROM " + schema.table("IM_USER_ROLE_MAP") + " WHERE USER_ID = :userId";
		return dbClient.query(itflowDb, sql, Map.of("userId", userId), UserRoleRow.class);
	}

	/** 新增使用者：預設密碼狀態（IS_DFLT_PWD=1、PWD_CHANGE_DATE=SYSDATE）、CREATE_BY=SYSTEM */
	public int insertUser(UserRow row) {
		String sql = "INSERT INTO " + schema.table("IM_USER")
				+ " (USER_ID, LOGIN_ID, USER_NAME, EMAIL, JOB_TITLE, DEPT_NAME, TEL, PWD_HASH, IS_DFLT_PWD,"
				+ " PWD_CHANGE_DATE, STATUS, CREATE_DATE, CREATE_BY)"
				+ " VALUES (:userId, :loginId, :userName, :email, :jobTitle, :deptName, :tel, :pwdHash, 1,"
				+ " SYSDATE, :status, SYSDATE, :by)";
		return dbClient.update(itflowDb, sql, userParams(row));
	}

	/** 更新既有使用者：覆寫基本資料與狀態，密碼重設為預設密碼狀態，UPDATE_BY=SYSTEM */
	public int updateUser(UserRow row) {
		String sql = "UPDATE " + schema.table("IM_USER")
				+ " SET LOGIN_ID = :loginId, USER_NAME = :userName, EMAIL = :email, JOB_TITLE = :jobTitle,"
				+ " DEPT_NAME = :deptName, TEL = :tel, PWD_HASH = :pwdHash, IS_DFLT_PWD = 1, PWD_CHANGE_DATE = SYSDATE,"
				+ " STATUS = :status, UPDATE_DATE = SYSDATE, UPDATE_BY = :by"
				+ " WHERE USER_ID = :userId";
		return dbClient.update(itflowDb, sql, userParams(row));
	}

	/** 新增啟用中的角色對照 */
	public int insertRole(String userId, String roleId) {
		String sql = "INSERT INTO " + schema.table("IM_USER_ROLE_MAP")
				+ " (USER_ID, ROLE_ID, STATUS, CREATE_DATE, CREATE_BY) VALUES (:userId, :roleId, 1, SYSDATE, :by)";
		return dbClient.update(itflowDb, sql, Map.of("userId", userId, "roleId", roleId, "by", IMPORT_BY));
	}

	/** 既有角色對照改啟用（1）或停用（0） */
	public int setRoleStatus(String userId, String roleId, int status) {
		String sql = "UPDATE " + schema.table("IM_USER_ROLE_MAP")
				+ " SET STATUS = :status, UPDATE_DATE = SYSDATE, UPDATE_BY = :by"
				+ " WHERE USER_ID = :userId AND ROLE_ID = :roleId";
		return dbClient.update(itflowDb, sql,
				Map.of("userId", userId, "roleId", roleId, "status", status, "by", IMPORT_BY));
	}

	private static Map<String, Object> userParams(UserRow row) {
		Map<String, Object> params = new HashMap<>();
		params.put("userId", row.getUserId());
		params.put("loginId", row.getLoginId());
		params.put("userName", row.getUserName());
		params.put("email", row.getEmail());
		params.put("jobTitle", row.getJobTitle());
		params.put("deptName", row.getDeptName());
		params.put("tel", row.getTel());
		params.put("pwdHash", row.getPwdHash());
		params.put("status", row.getStatus());
		params.put("by", IMPORT_BY);
		return params;
	}
}
