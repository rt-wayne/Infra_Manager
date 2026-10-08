package com.mpx.infra_manager_java.dao.mail;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-08
// 變更說明: 新增：IM_MAIL_OUTBOX 的讀寫（S8 R1）。每個方法單句；交易由呼叫端決定——
//           enqueue 的 INSERT 跟著業務交易一起 commit／rollback，worker 則每封信開自己的交易。
//           TO_JSON／CC_JSON／BCC_JSON／HTML_BODY／META_JSON 以 Types.CLOB 綁定。
//           worker 取信分兩步：findDueIds 不鎖、只找候選主鍵；lockPending 逐列 FOR UPDATE SKIP LOCKED
//           （Oracle 不允許 FOR UPDATE 與 FETCH FIRST 同句，ORA-02014）。
//           markFailedTry 的 SET 子句以舊值算 TRY_CNT+1，達上限直接標 FAILED
// ============================================================

import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Repository;

import com.mpx.common.db.DbClient;
import com.mpx.infra_manager_java.config.DbSchema;
import com.mpx.infra_manager_java.model.mail.MailIdRow;
import com.mpx.infra_manager_java.model.mail.MailOutboxRow;

@Repository
public class MailOutboxDao {

	public static final String STATUS_PENDING = "PENDING";
	public static final String STATUS_SENT = "SENT";
	public static final String STATUS_FAILED = "FAILED";
	/** worker 更新列時的 UPDATE_BY（DDL 註解：排程產生者為 SYSTEM） */
	public static final String SYSTEM_USER = "SYSTEM";

	private static final String COLUMNS = "MAIL_OUTBOX_ID, TO_JSON, CC_JSON, BCC_JSON, MAIL_SUBJ, HTML_BODY, MAIL_STATUS_CODE,"
			+ " TRY_CNT, ERROR_TEXT, SMTP_MSG_ID, SEND_DATE, META_JSON, CREATE_DATE, CREATE_BY, UPDATE_DATE, UPDATE_BY";

	private final DbClient dbClient;
	private final DbSchema schema;
	private final String itflowDb;

	public MailOutboxDao(DbClient dbClient, DbSchema schema, @Value("${db.connect.itflow}") String itflowDb) {
		this.dbClient = dbClient;
		this.schema = schema;
		this.itflowDb = itflowDb;
	}

	/** 寫入一封待寄信（PENDING、TRY_CNT 0）；JSON 參數已是序列化好的字串，cc／bcc／meta 可為 null */
	public void insert(String toJson, String ccJson, String bccJson, String subject, String htmlBody, String metaJson,
			String createdBy) {
		String sql = "INSERT INTO " + schema.table("IM_MAIL_OUTBOX")
				+ " (TO_JSON, CC_JSON, BCC_JSON, MAIL_SUBJ, HTML_BODY, MAIL_STATUS_CODE, TRY_CNT, META_JSON, STATUS,"
				+ " CREATE_DATE, CREATE_BY)"
				+ " VALUES (:toJson, :ccJson, :bccJson, :subj, :html, '" + STATUS_PENDING + "', 0, :metaJson, 1,"
				+ " SYSDATE, :createdBy)";
		Map<String, Object> p = new HashMap<>();
		p.put("toJson", clob(toJson));
		p.put("ccJson", clob(ccJson));
		p.put("bccJson", clob(bccJson));
		p.put("subj", subject);
		p.put("html", clob(htmlBody));
		p.put("metaJson", clob(metaJson));
		p.put("createdBy", createdBy);
		dbClient.update(itflowDb, sql, p);
	}

	/**
	 * 到期待寄的候選主鍵（不鎖）：PENDING、未達重試上限、且已過退避時間
	 * （第一次立即寄；之後以上次嘗試時間 + 已嘗試次數 × backoffMinutes 計）。舊的先寄
	 */
	public List<Long> findDueIds(int limit, int maxTry, int backoffMinutes) {
		String sql = "SELECT MAIL_OUTBOX_ID FROM " + schema.table("IM_MAIL_OUTBOX")
				+ " WHERE STATUS = 1 AND MAIL_STATUS_CODE = '" + STATUS_PENDING + "' AND TRY_CNT < :maxTry"
				+ " AND (TRY_CNT = 0 OR NVL(UPDATE_DATE, CREATE_DATE) + (TRY_CNT * :backoff) / 1440 <= SYSDATE)"
				+ " ORDER BY CREATE_DATE, MAIL_OUTBOX_ID FETCH FIRST :limit ROWS ONLY";
		Map<String, Object> p = Map.of("maxTry", maxTry, "backoff", backoffMinutes, "limit", limit);
		return dbClient.query(itflowDb, sql, p, MailIdRow.class).stream().map(MailIdRow::getMailOutboxId).toList();
	}

	/** 鎖住一封仍是 PENDING 的信並讀出全部欄位；已被別的執行緒鎖住或狀態已變則回 null。必須在交易內呼叫 */
	public MailOutboxRow lockPending(long mailOutboxId) {
		String sql = "SELECT " + COLUMNS + " FROM " + schema.table("IM_MAIL_OUTBOX")
				+ " WHERE MAIL_OUTBOX_ID = :id AND STATUS = 1 AND MAIL_STATUS_CODE = '" + STATUS_PENDING + "'"
				+ " FOR UPDATE SKIP LOCKED";
		return first(dbClient.query(itflowDb, sql, Map.of("id", mailOutboxId), MailOutboxRow.class));
	}

	/** 單筆（不鎖、不限狀態）；找不到回 null */
	public MailOutboxRow findById(long mailOutboxId) {
		String sql = "SELECT " + COLUMNS + " FROM " + schema.table("IM_MAIL_OUTBOX")
				+ " WHERE MAIL_OUTBOX_ID = :id AND STATUS = 1";
		return first(dbClient.query(itflowDb, sql, Map.of("id", mailOutboxId), MailOutboxRow.class));
	}

	/** 標成已寄出：TRY_CNT+1、記 Message-ID 與寄出時間；note 為部分收件人無效等備註（可 null）。回更新列數 */
	public int markSent(long mailOutboxId, String smtpMsgId, String note) {
		String sql = "UPDATE " + schema.table("IM_MAIL_OUTBOX")
				+ " SET MAIL_STATUS_CODE = '" + STATUS_SENT + "', TRY_CNT = TRY_CNT + 1, SMTP_MSG_ID = :msgId,"
				+ " SEND_DATE = SYSDATE, ERROR_TEXT = :note, UPDATE_DATE = SYSDATE, UPDATE_BY = :by"
				+ " WHERE MAIL_OUTBOX_ID = :id AND MAIL_STATUS_CODE = '" + STATUS_PENDING + "'";
		Map<String, Object> p = new HashMap<>();
		p.put("id", mailOutboxId);
		p.put("msgId", smtpMsgId);
		p.put("note", note);
		p.put("by", SYSTEM_USER);
		return dbClient.update(itflowDb, sql, p);
	}

	/** 記一次失敗：TRY_CNT+1、存錯誤文字；達 maxTry 標 FAILED、否則維持 PENDING 等下次重試。回更新列數 */
	public int markFailedTry(long mailOutboxId, String errorText, int maxTry) {
		String sql = "UPDATE " + schema.table("IM_MAIL_OUTBOX")
				+ " SET TRY_CNT = TRY_CNT + 1,"
				+ " MAIL_STATUS_CODE = CASE WHEN TRY_CNT + 1 >= :maxTry THEN '" + STATUS_FAILED + "'"
				+ " ELSE '" + STATUS_PENDING + "' END,"
				+ " ERROR_TEXT = :err, UPDATE_DATE = SYSDATE, UPDATE_BY = :by"
				+ " WHERE MAIL_OUTBOX_ID = :id AND MAIL_STATUS_CODE = '" + STATUS_PENDING + "'";
		Map<String, Object> p = new HashMap<>();
		p.put("id", mailOutboxId);
		p.put("maxTry", maxTry);
		p.put("err", errorText);
		p.put("by", SYSTEM_USER);
		return dbClient.update(itflowDb, sql, p);
	}

	private static SqlParameterValue clob(String value) {
		return new SqlParameterValue(Types.CLOB, value);
	}

	private static <T> T first(List<T> rows) {
		return rows.isEmpty() ? null : rows.get(0);
	}
}
