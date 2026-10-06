package com.mpx.infra_manager_java.dao.auth;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：IM_USER／IM_USER_ROLE_MAP 查詢（S2 回合一，只讀）。
//           表名經 DbSchema.table() 加 schema 前綴（第 77 項裁示 A）；條件值一律 :name 綁定。
//           LOGIN_ID 在表上有 CHECK 必為小寫，呼叫端先正規化再查。
//           2026-10-06 code review：登入只查需要的 6 欄，不查 EMAIL／JOB_TITLE／DEPT_NAME／TEL（最小查詢）
//           2026-10-06 S2 回合二：加 updatePassword（改密碼；只更新啟用中的帳號，回更新列數讓呼叫端判斷）
//           2026-10-06 code review：updatePassword 多帶剛比對過的舊雜湊當 WHERE 條件（樂觀鎖），兩個請求同時改密碼
//           只有先到的那個會成功，後到的 0 列
// ============================================================

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.mpx.common.db.DbClient;
import com.mpx.infra_manager_java.config.DbSchema;
import com.mpx.infra_manager_java.model.auth.RoleRow;
import com.mpx.infra_manager_java.model.auth.UserRow;

@Repository
public class UserDao {

	private final DbClient dbClient;
	private final DbSchema schema;
	private final String itflowDb;

	public UserDao(DbClient dbClient, DbSchema schema, @Value("${db.connect.itflow}") String itflowDb) {
		this.dbClient = dbClient;
		this.schema = schema;
		this.itflowDb = itflowDb;
	}

	/** 以登入帳號查使用者（不分啟用狀態，由 service 判斷）；查無回 empty */
	public Optional<UserRow> findByLoginId(String loginId) {
		String sql = "SELECT USER_ID, LOGIN_ID, USER_NAME, PWD_HASH, IS_DFLT_PWD, STATUS"
				+ " FROM " + schema.table("IM_USER") + " WHERE LOGIN_ID = :loginId";
		List<UserRow> rows = dbClient.query(itflowDb, sql, Map.of("loginId", loginId), UserRow.class);
		return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
	}

	/** 使用者目前啟用中的角色（對照與角色主檔都要啟用），依角色排序 */
	public List<String> findActiveRoleIds(String userId) {
		String sql = "SELECT M.ROLE_ID FROM " + schema.table("IM_USER_ROLE_MAP") + " M"
				+ " JOIN " + schema.table("IM_ROLE") + " R ON R.ROLE_ID = M.ROLE_ID"
				+ " WHERE M.USER_ID = :userId AND M.STATUS = 1 AND R.STATUS = 1"
				+ " ORDER BY R.SORT_NO, M.ROLE_ID";
		return dbClient.query(itflowDb, sql, Map.of("userId", userId), RoleRow.class).stream()
				.map(RoleRow::getRoleId).toList();
	}

	/**
	 * 改密碼：寫入新雜湊、清預設密碼標記、記改密碼時間；只更新「啟用中、且雜湊仍是 oldHash」的帳號。
	 * 回更新列數（預期 1；0 代表期間被停用或密碼已被別的請求改掉）
	 */
	public int updatePassword(String userId, String pwdHash, String oldHash) {
		String sql = "UPDATE " + schema.table("IM_USER")
				+ " SET PWD_HASH = :pwdHash, IS_DFLT_PWD = 0, PWD_CHANGE_DATE = SYSDATE,"
				+ " UPDATE_DATE = SYSDATE, UPDATE_BY = :userId"
				+ " WHERE USER_ID = :userId AND STATUS = 1 AND PWD_HASH = :oldHash";
		return dbClient.update(itflowDb, sql, Map.of("userId", userId, "pwdHash", pwdHash, "oldHash", oldHash));
	}
}
