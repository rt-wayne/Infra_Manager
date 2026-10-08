package com.mpx.infra_manager_java.dao.mail;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-08
// 變更說明: 新增：信件收件人查詢（S8 R2，只讀）。全部寫成 join 型查詢、不用 IN 清單（DbClient 是否支援集合參數未查證）。
//           規則（施工計畫 backlog/8-mail.md）：一律只取 IM_USER.STATUS=1 的人；申請人以 IM_USER.EMAIL 為準、
//           空的才用填單值 IM_APP.APPLY_EMAIL（2026-10-08 使用者裁示 ②A）；申請人帳號已停用則不寄。
//           退件群組＝申請人＋該簽核實例所有關卡候選人（含還沒輪到的）＋實際簽核人＋執行人（任一版次）＋啟用中 governance，
//           UNION 自然去重；email 不分大小寫去重由 MailOutboxService 做
// ============================================================

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.mpx.common.db.DbClient;
import com.mpx.infra_manager_java.config.DbSchema;
import com.mpx.infra_manager_java.model.mail.RecipientRow;

@Repository
public class MailRecipientDao {

	/** 資訊治理角色代碼（同 AppExecutionService.ROLE_GOVERNANCE） */
	public static final String ROLE_GOVERNANCE = "governance";

	private final DbClient dbClient;
	private final DbSchema schema;
	private final String itflowDb;

	public MailRecipientDao(DbClient dbClient, DbSchema schema, @Value("${db.connect.itflow}") String itflowDb) {
		this.dbClient = dbClient;
		this.schema = schema;
		this.itflowDb = itflowDb;
	}

	/** 某一關的候選簽核人（IM_APPR_CAND_MAP），只取啟用帳號 */
	public List<RecipientRow> findStepCandidates(long apprStepId) {
		String sql = "SELECT U.USER_ID, U.USER_NAME, U.EMAIL"
				+ " FROM " + schema.table("IM_APPR_CAND_MAP") + " C"
				+ " JOIN " + schema.table("IM_USER") + " U ON U.USER_ID = C.USER_ID AND U.STATUS = 1"
				+ " WHERE C.APPR_STEP_ID = :stepId AND C.STATUS = 1 ORDER BY U.USER_ID";
		return dbClient.query(itflowDb, sql, Map.of("stepId", apprStepId), RecipientRow.class);
	}

	/** 申請人：帳號 email 優先、空的用填單 email；帳號停用則回空清單 */
	public List<RecipientRow> findApplicant(String appId) {
		String sql = "SELECT " + applicantColumns() + " FROM " + schema.table("IM_APP") + " A"
				+ " JOIN " + schema.table("IM_USER") + " U ON U.USER_ID = A.APPLY_USER_ID AND U.STATUS = 1"
				+ " WHERE A.APP_ID = :appId AND A.STATUS = 1";
		return dbClient.query(itflowDb, sql, Map.of("appId", appId), RecipientRow.class);
	}

	/** 啟用中的資訊治理角色成員（帳號、角色、對照都啟用） */
	public List<RecipientRow> findGovernance() {
		String sql = "SELECT U.USER_ID, U.USER_NAME, U.EMAIL FROM " + schema.table("IM_USER") + " U"
				+ " WHERE U.STATUS = 1 AND " + governanceExists("U") + " ORDER BY U.USER_ID";
		return dbClient.query(itflowDb, sql, Map.of("roleId", ROLE_GOVERNANCE), RecipientRow.class);
	}

	/** 退件群組（舊系統 notifyRejectAll）：見類別說明；UNION 去重後依工號排序 */
	public List<RecipientRow> findRejectGroup(String appId, long apprId) {
		String user = schema.table("IM_USER");
		String step = schema.table("IM_APPR_STEP");
		String sql = "SELECT USER_ID, USER_NAME, EMAIL FROM ("
				// 申請人
				+ " SELECT " + applicantColumns() + " FROM " + schema.table("IM_APP") + " A"
				+ " JOIN " + user + " U ON U.USER_ID = A.APPLY_USER_ID AND U.STATUS = 1"
				+ " WHERE A.APP_ID = :appId AND A.STATUS = 1"
				// 該實例所有關卡候選人
				+ " UNION SELECT U.USER_ID, U.USER_NAME, U.EMAIL FROM " + schema.table("IM_APPR_CAND_MAP") + " C"
				+ " JOIN " + step + " S ON S.APPR_STEP_ID = C.APPR_STEP_ID AND S.STATUS = 1"
				+ " JOIN " + user + " U ON U.USER_ID = C.USER_ID AND U.STATUS = 1"
				+ " WHERE S.APPR_ID = :apprId AND C.STATUS = 1"
				// 實際簽核人
				+ " UNION SELECT U.USER_ID, U.USER_NAME, U.EMAIL FROM " + step + " S"
				+ " JOIN " + user + " U ON U.USER_ID = S.USER_ID AND U.STATUS = 1"
				+ " WHERE S.APPR_ID = :apprId AND S.STATUS = 1"
				// 執行人（送治理審查的人，任一版次）
				+ " UNION SELECT U.USER_ID, U.USER_NAME, U.EMAIL FROM " + schema.table("IM_APP_EXEC") + " E"
				+ " JOIN " + user + " U ON U.USER_ID = E.USER_ID AND U.STATUS = 1"
				+ " WHERE E.APP_ID = :appId AND E.STATUS = 1"
				// 啟用中 governance
				+ " UNION SELECT U.USER_ID, U.USER_NAME, U.EMAIL FROM " + user + " U"
				+ " WHERE U.STATUS = 1 AND " + governanceExists("U")
				+ ") ORDER BY USER_ID";
		return dbClient.query(itflowDb, sql, Map.of("appId", appId, "apprId", apprId, "roleId", ROLE_GOVERNANCE),
				RecipientRow.class);
	}

	/** 申請人三欄：email 以帳號為準、空白才用填單值（②A） */
	private static String applicantColumns() {
		return "U.USER_ID, U.USER_NAME, NVL(TRIM(U.EMAIL), A.APPLY_EMAIL) AS EMAIL";
	}

	private String governanceExists(String userAlias) {
		return "EXISTS (SELECT 1 FROM " + schema.table("IM_USER_ROLE_MAP") + " M"
				+ " JOIN " + schema.table("IM_ROLE") + " R ON R.ROLE_ID = M.ROLE_ID AND R.STATUS = 1"
				+ " WHERE M.USER_ID = " + userAlias + ".USER_ID AND M.ROLE_ID = :roleId AND M.STATUS = 1)";
	}
}
