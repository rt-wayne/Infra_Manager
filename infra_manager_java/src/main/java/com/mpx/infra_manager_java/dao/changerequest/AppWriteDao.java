package com.mpx.infra_manager_java.dao.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：申請單草稿寫入 IM_APP 主檔與 6 張子表（S6 回合二 b-1）。每個方法單句或逐列單句；
//           交易由 AppDraftService 的 @Transactional 決定。CLOB 欄位以 Types.CLOB 綁定（走 setClob），
//           避免長字串被當 VARCHAR／LONG 綁定而撞 ORA-01461／ORA-24816。
//           deleteChildren 給回合二 b-2 編輯草稿「子表整批刪除重建」用。
//           2026-10-07 回合二 b-2：加 updateApp（條件含版本、DRAFT、申請人，任一不符回 0 列）與 findLockState
// ============================================================

import java.sql.Timestamp;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Repository;

import com.mpx.common.db.DbClient;
import com.mpx.infra_manager_java.config.DbSchema;
import com.mpx.infra_manager_java.model.changerequest.AppDraft;
import com.mpx.infra_manager_java.model.changerequest.AppLockRow;

@Repository
public class AppWriteDao {

	/** 子表的刪除順序（無互相參照，順序只為好讀） */
	static final List<String> CHILD_TABLES = List.of("IM_APP_CATG_MAP", "IM_APP_CATG_OTHER", "IM_APP_REASON_MAP",
			"IM_APP_SCOPE_MAP", "IM_APP_EQUIP", "IM_APP_PLAN_STEP");

	private final DbClient dbClient;
	private final DbSchema schema;
	private final String itflowDb;

	public AppWriteDao(DbClient dbClient, DbSchema schema, @Value("${db.connect.itflow}") String itflowDb) {
		this.dbClient = dbClient;
		this.schema = schema;
		this.itflowDb = itflowDb;
	}

	/** 新增草稿主檔：狀態 DRAFT、來源 ONLINE、版本 1、ROW_VER_NO 0 */
	public void insertApp(String appId, String flowId, String applyUserId, Timestamp applyDate, AppDraft d) {
		String sql = "INSERT INTO " + schema.table("IM_APP")
				+ " (APP_ID, APP_TITLE, PRIO_CODE, FLOW_ID, APPLY_USER_ID, APP_STATUS_CODE, SOURCE_CODE, CURR_VER_NO,"
				+ " ROW_VER_NO, APPLY_DATE, APPLY_DEPT_NAME, APPLY_TEL, APPLY_EMAIL, IS_SELF_EXEC, IS_SUP_EXEC,"
				+ " WORK_MODE_CODE, REMOTE_METHOD, SUP_NAME, SUP_CNTCT, SUP_TEL, SUP_HEAD_CNT, WORK_SUBJ, OTHER_REASON,"
				+ " SCHED_START_DATE, SCHED_END_DATE, EST_HOUR_QTY, OMIT_REASON, STATUS, CREATE_DATE, CREATE_BY,"
				+ " IMPACT_DESC, WORK_DETAIL, RISK_DESC, ROLL_BACK_PLAN)"
				+ " VALUES (:appId, :title, :prioCode, :flowId, :applyUserId, 'DRAFT', 'ONLINE', 1,"
				+ " 0, :applyDate, :deptName, :tel, :email, :selfExec, :supExec,"
				+ " :workMode, :remoteMethod, :supName, :supContact, :supTel, :headCount, :workSubject, :otherReason,"
				+ " :schedStart, :schedEnd, :estHours, :omitReason, 1, SYSDATE, :applyUserId,"
				+ " :impactDesc, :workDetail, :riskDesc, :rollbackPlan)";
		Map<String, Object> p = fields(d);
		p.put("appId", appId);
		p.put("flowId", flowId);
		p.put("applyUserId", applyUserId);
		p.put("applyDate", applyDate);
		dbClient.update(itflowDb, sql, p);
	}

	/** 主檔中由表單決定的欄位（insertApp／updateApp 共用） */
	private static Map<String, Object> fields(AppDraft d) {
		Map<String, Object> p = new HashMap<>();
		p.put("title", d.title());
		p.put("prioCode", d.prioCode());
		p.put("deptName", d.applyDeptName());
		p.put("tel", d.applyTel());
		p.put("email", d.applyEmail());
		p.put("selfExec", d.selfExec() ? 1 : 0);
		p.put("supExec", d.supplierExec() ? 1 : 0);
		p.put("workMode", d.workModeCode());
		p.put("remoteMethod", d.remoteMethod());
		p.put("supName", d.supName());
		p.put("supContact", d.supContact());
		p.put("supTel", d.supTel());
		p.put("headCount", d.supHeadCount());
		p.put("workSubject", d.workSubject());
		p.put("otherReason", d.otherReason());
		p.put("schedStart", d.schedStart());
		p.put("schedEnd", d.schedEnd());
		p.put("estHours", d.estHours());
		p.put("omitReason", d.omitReason());
		p.put("impactDesc", clob(d.impactDesc()));
		p.put("workDetail", clob(d.workDetail()));
		p.put("riskDesc", clob(d.riskDesc()));
		p.put("rollbackPlan", clob(d.rollbackPlan()));
		return p;
	}

	/**
	 * 更新草稿主檔並把 ROW_VER_NO +1；只有「版本相符、仍是 DRAFT、申請人是 by、未刪除」才會更新。
	 * 回影響筆數（0 表示上述任一條件不符，由呼叫端用 findLockState 判斷原因）
	 */
	public int updateApp(String appId, long rowVerNo, String flowId, String by, AppDraft d) {
		String sql = "UPDATE " + schema.table("IM_APP") + " SET APP_TITLE = :title, PRIO_CODE = :prioCode,"
				+ " FLOW_ID = :flowId, APPLY_DEPT_NAME = :deptName, APPLY_TEL = :tel, APPLY_EMAIL = :email,"
				+ " IS_SELF_EXEC = :selfExec, IS_SUP_EXEC = :supExec, WORK_MODE_CODE = :workMode,"
				+ " REMOTE_METHOD = :remoteMethod, SUP_NAME = :supName, SUP_CNTCT = :supContact, SUP_TEL = :supTel,"
				+ " SUP_HEAD_CNT = :headCount, WORK_SUBJ = :workSubject, OTHER_REASON = :otherReason,"
				+ " SCHED_START_DATE = :schedStart, SCHED_END_DATE = :schedEnd, EST_HOUR_QTY = :estHours,"
				+ " OMIT_REASON = :omitReason, ROW_VER_NO = ROW_VER_NO + 1, UPDATE_DATE = SYSDATE, UPDATE_BY = :by,"
				+ " IMPACT_DESC = :impactDesc, WORK_DETAIL = :workDetail, RISK_DESC = :riskDesc,"
				+ " ROLL_BACK_PLAN = :rollbackPlan"
				+ " WHERE APP_ID = :appId AND ROW_VER_NO = :rowVerNo AND APP_STATUS_CODE = 'DRAFT'"
				+ " AND APPLY_USER_ID = :by AND STATUS = 1";
		Map<String, Object> p = fields(d);
		p.put("appId", appId);
		p.put("rowVerNo", rowVerNo);
		p.put("flowId", flowId);
		p.put("by", by);
		return dbClient.update(itflowDb, sql, p);
	}

	/** 編輯失敗時判斷原因；單不存在或已刪除回 null */
	public AppLockRow findLockState(String appId) {
		String sql = "SELECT APP_STATUS_CODE, APPLY_USER_ID, ROW_VER_NO FROM " + schema.table("IM_APP")
				+ " WHERE APP_ID = :appId AND STATUS = 1";
		List<AppLockRow> rows = dbClient.query(itflowDb, sql, Map.of("appId", appId), AppLockRow.class);
		return rows.isEmpty() ? null : rows.get(0);
	}

	/** 寫入 6 張子表；SEQ_NO 依清單順序 1 起 */
	public void insertChildren(String appId, String by, AppDraft d) {
		insertOptionMap("IM_APP_CATG_MAP", appId, by, d.categoryItemIds());
		insertOptionMap("IM_APP_REASON_MAP", appId, by, d.reasonIds());
		insertOptionMap("IM_APP_SCOPE_MAP", appId, by, d.scopeIds());
		for (AppDraft.CategoryOther o : d.categoryOthers()) {
			String sql = "INSERT INTO " + schema.table("IM_APP_CATG_OTHER")
					+ " (APP_ID, FORM_OPTION_ID, OTHER_TEXT, STATUS, CREATE_DATE, CREATE_BY)"
					+ " VALUES (:appId, :optionId, :text, 1, SYSDATE, :by)";
			dbClient.update(itflowDb, sql,
					Map.of("appId", appId, "optionId", o.formOptionId(), "text", o.text(), "by", by));
		}
		List<AppDraft.Equipment> equipments = d.equipments();
		for (int i = 0; i < equipments.size(); i++) {
			AppDraft.Equipment e = equipments.get(i);
			String sql = "INSERT INTO " + schema.table("IM_APP_EQUIP")
					+ " (APP_ID, SEQ_NO, EQUIP_NAME, ASSET_NO, MODEL_NO, SERIAL_NO, PURP_DESC, MGMT_IP,"
					+ " STATUS, CREATE_DATE, CREATE_BY)"
					+ " VALUES (:appId, :seqNo, :name, :assetNo, :modelNo, :serialNo, :purpose, :mgmtIp,"
					+ " 1, SYSDATE, :by)";
			Map<String, Object> p = new HashMap<>();
			p.put("appId", appId);
			p.put("seqNo", i + 1);
			p.put("name", e.name());
			p.put("assetNo", e.assetNo());
			p.put("modelNo", e.modelNo());
			p.put("serialNo", e.serialNo());
			p.put("purpose", e.purpose());
			p.put("mgmtIp", e.mgmtIp());
			p.put("by", by);
			dbClient.update(itflowDb, sql, p);
		}
		List<String> steps = d.planSteps();
		for (int i = 0; i < steps.size(); i++) {
			String sql = "INSERT INTO " + schema.table("IM_APP_PLAN_STEP")
					+ " (APP_ID, SEQ_NO, STEP_TEXT, STATUS, CREATE_DATE, CREATE_BY)"
					+ " VALUES (:appId, :seqNo, :text, 1, SYSDATE, :by)";
			dbClient.update(itflowDb, sql,
					Map.of("appId", appId, "seqNo", i + 1, "text", steps.get(i), "by", by));
		}
	}

	/** 刪除該單 6 張子表的全部列（編輯草稿時整批重建用） */
	public void deleteChildren(String appId) {
		for (String table : CHILD_TABLES) {
			dbClient.update(itflowDb, "DELETE FROM " + schema.table(table) + " WHERE APP_ID = :appId",
					Map.of("appId", appId));
		}
	}

	private void insertOptionMap(String table, String appId, String by, List<Long> optionIds) {
		String sql = "INSERT INTO " + schema.table(table)
				+ " (APP_ID, FORM_OPTION_ID, STATUS, CREATE_DATE, CREATE_BY) VALUES (:appId, :optionId, 1, SYSDATE, :by)";
		for (Long id : optionIds) {
			dbClient.update(itflowDb, sql, Map.of("appId", appId, "optionId", id, "by", by));
		}
	}

	private static SqlParameterValue clob(String value) {
		return new SqlParameterValue(Types.CLOB, value);
	}
}
