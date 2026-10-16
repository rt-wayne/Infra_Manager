package com.mpx.infra_manager.dao;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：權限、角色、執行人對照與通知收件人查詢（spec v2.0 §2.3、§4.12）
//           權限集合＝使用者 IM_ACCT_ROLE_USER（STATUS＝1）的角色經 IM_ACCT_ROLE_PERM（STATUS＝1）展開，再加上 GENERAL 角色的權限
//           （GENERAL 不存角色人員列；角色碼以參數 :generalRoleId 綁定，不寫死權限碼）；角色或權限 STATUS＝0 不計。
//           收件人一律只含 IM_ACCT_USER.STATUS＝1；每次查詢都讀當下 DB（不快取，spec §2.3、§4.12）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-14
// 變更說明: DB 別名 key 由 db.connect.itflow 改名為 itflow.db.alias（使用者改名）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// ============================================================

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.mpx.common.db.DbClient;
import com.mpx.infra_manager.common.AcctConst;
import com.mpx.infra_manager.model.CodeRow;
import com.mpx.infra_manager.model.Recipient;

@Repository
public class PermDao {

	/** 啟用中的角色權限列（RP 別名；需再加 RP.PERM_ID／RP.ROLE_ID 條件） */
	static final String ACTIVE_ROLE_PERM = """
			IM_ACCT_ROLE_PERM RP
			  JOIN IM_ACCT_ROLE R ON R.ROLE_ID = RP.ROLE_ID AND R.STATUS = 1
			  JOIN IM_ACCT_PERM P ON P.PERM_ID = RP.PERM_ID AND P.STATUS = 1
			""";

	public static final String FIND_PERMS = """
			SELECT DISTINCT RP.PERM_ID AS CODE
			  FROM
			""" + ACTIVE_ROLE_PERM + """
			 WHERE RP.STATUS = 1
			   AND (RP.ROLE_ID = :generalRoleId
			        OR EXISTS (SELECT 1 FROM IM_ACCT_ROLE_USER RU
			                    WHERE RU.ROLE_ID = RP.ROLE_ID AND RU.USER_ID = :userId AND RU.STATUS = 1))
			""";

	public static final String FIND_ROLE_IDS = """
			SELECT RU.ROLE_ID AS CODE
			  FROM IM_ACCT_ROLE_USER RU
			  JOIN IM_ACCT_ROLE R ON R.ROLE_ID = RU.ROLE_ID AND R.STATUS = 1
			 WHERE RU.USER_ID = :userId
			   AND RU.STATUS = 1
			 ORDER BY R.SORT_SEQ, RU.ROLE_ID
			""";

	public static final String FIND_EXEC_CATGS = """
			SELECT CATG_ID AS CODE
			  FROM IM_EXEC_MAP
			 WHERE USER_ID = :userId
			   AND STATUS = 1
			 ORDER BY CATG_ID
			""";

	static final String RCPT_SELECT = """
			SELECT U.USER_ID, U.USER_EN_NAME, U.USER_NAME, U.EMAIL
			  FROM IM_ACCT_USER U
			 WHERE U.STATUS = 1
			""";

	public static final String FIND_USERS_WITH_PERM = RCPT_SELECT + """
			   AND EXISTS (SELECT 1 FROM
			""" + ACTIVE_ROLE_PERM + """
			                WHERE RP.STATUS = 1
			                  AND RP.PERM_ID = :permId
			                  AND (RP.ROLE_ID = :generalRoleId
			                       OR EXISTS (SELECT 1 FROM IM_ACCT_ROLE_USER RU
			                                   WHERE RU.ROLE_ID = RP.ROLE_ID AND RU.USER_ID = U.USER_ID AND RU.STATUS = 1)))
			 ORDER BY U.USER_ID
			""";

	public static final String FIND_USERS_IN_ROLES = RCPT_SELECT + """
			   AND (:hasGeneral = 1
			        OR EXISTS (SELECT 1 FROM IM_ACCT_ROLE_USER RU
			                     JOIN IM_ACCT_ROLE R ON R.ROLE_ID = RU.ROLE_ID AND R.STATUS = 1
			                    WHERE RU.USER_ID = U.USER_ID AND RU.STATUS = 1 AND RU.ROLE_ID IN (:roleIds)))
			 ORDER BY U.USER_ID
			""";

	public static final String FIND_EXEC_USERS = RCPT_SELECT + """
			   AND EXISTS (SELECT 1 FROM IM_EXEC_MAP M
			                WHERE M.USER_ID = U.USER_ID AND M.CATG_ID = :catgId AND M.STATUS = 1)
			 ORDER BY U.USER_ID
			""";

	public static final String FIND_ACTIVE_USER = RCPT_SELECT + """
			   AND U.USER_ID = :userId
			""";

	private final DbClient dbClient;
	private final String itflowDb;

	public PermDao(DbClient dbClient, @Value("${itflow.db.alias}") String itflowDb) {
		this.dbClient = dbClient;
		this.itflowDb = itflowDb;
	}

	public List<String> findPerms(String userId) {
		Map<String, Object> params = new HashMap<>();
		params.put("userId", userId);
		params.put("generalRoleId", AcctConst.ROLE_GENERAL);
		return codes(dbClient.query(itflowDb, FIND_PERMS, params, CodeRow.class));
	}

	public List<String> findRoleIds(String userId) {
		Map<String, Object> params = new HashMap<>();
		params.put("userId", userId);
		return codes(dbClient.query(itflowDb, FIND_ROLE_IDS, params, CodeRow.class));
	}

	public List<String> findExecCatgs(String userId) {
		Map<String, Object> params = new HashMap<>();
		params.put("userId", userId);
		return codes(dbClient.query(itflowDb, FIND_EXEC_CATGS, params, CodeRow.class));
	}

	/** 擁有權限 permId（依 §2.3 展開）的所有啟用中使用者 */
	public List<Recipient> findUsersWithPerm(String permId) {
		Map<String, Object> params = new HashMap<>();
		params.put("permId", permId);
		params.put("generalRoleId", AcctConst.ROLE_GENERAL);
		return dbClient.query(itflowDb, FIND_USERS_WITH_PERM, params, Recipient.class);
	}

	/** 角色成員（roleIds 含 GENERAL 時＝全部啟用中使用者）；roleIds 不可為空 */
	public List<Recipient> findUsersInRoles(List<String> roleIds) {
		Map<String, Object> params = new HashMap<>();
		params.put("roleIds", roleIds);
		params.put("hasGeneral", roleIds.contains(AcctConst.ROLE_GENERAL) ? 1 : 0);
		return dbClient.query(itflowDb, FIND_USERS_IN_ROLES, params, Recipient.class);
	}

	public List<Recipient> findExecUsers(String catgId) {
		Map<String, Object> params = new HashMap<>();
		params.put("catgId", catgId);
		return dbClient.query(itflowDb, FIND_EXEC_USERS, params, Recipient.class);
	}

	/** 啟用中的單一使用者；停用或查無回 null */
	public Recipient findActiveUser(String userId) {
		Map<String, Object> params = new HashMap<>();
		params.put("userId", userId);
		return DaoSupport.first(dbClient.query(itflowDb, FIND_ACTIVE_USER, params, Recipient.class));
	}

	private static List<String> codes(List<CodeRow> rows) {
		return rows.stream().map(CodeRow::getCode).toList();
	}
}
