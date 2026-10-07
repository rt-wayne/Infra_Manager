package com.mpx.infra_manager_java.dao.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：申請單編號計數器 IM_APP_SEQ 的讀寫（S6 回合二 b-1）。
//           每個方法單句；交易由 AppSeqService 的 @Transactional 決定。UPDATE 會鎖住當天計數列直到交易結束，
//           同一天同時建單的請求在此排隊，所以號碼不會重複
// ============================================================

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.mpx.common.db.DbClient;
import com.mpx.infra_manager_java.config.DbSchema;
import com.mpx.infra_manager_java.model.changerequest.CountRow;

@Repository
public class AppSeqDao {

	private final DbClient dbClient;
	private final DbSchema schema;
	private final String itflowDb;

	public AppSeqDao(DbClient dbClient, DbSchema schema, @Value("${db.connect.itflow}") String itflowDb) {
		this.dbClient = dbClient;
		this.schema = schema;
		this.itflowDb = itflowDb;
	}

	/** 當天計數 +1；回影響筆數（0 表示當天還沒有計數列） */
	public int increment(String prefix, Timestamp seqDate, String by) {
		String sql = "UPDATE " + schema.table("IM_APP_SEQ")
				+ " SET LAST_NO = LAST_NO + 1, UPDATE_DATE = SYSDATE, UPDATE_BY = :by"
				+ " WHERE SEQ_PREFIX = :prefix AND SEQ_DATE = :seqDate AND STATUS = 1";
		return dbClient.update(itflowDb, sql, Map.of("prefix", prefix, "seqDate", seqDate, "by", by));
	}

	/** 建當天計數列，LAST_NO 直接為 1；別的交易搶先建好時丟 DuplicateKeyException */
	public int insertFirst(String prefix, Timestamp seqDate, String by) {
		String sql = "INSERT INTO " + schema.table("IM_APP_SEQ")
				+ " (SEQ_PREFIX, SEQ_DATE, LAST_NO, STATUS, CREATE_DATE, CREATE_BY)"
				+ " VALUES (:prefix, :seqDate, 1, 1, SYSDATE, :by)";
		return dbClient.update(itflowDb, sql, Map.of("prefix", prefix, "seqDate", seqDate, "by", by));
	}

	/** 當天目前的最後序號；沒有計數列回 null */
	public Integer findLastNo(String prefix, Timestamp seqDate) {
		String sql = "SELECT LAST_NO AS CNT FROM " + schema.table("IM_APP_SEQ")
				+ " WHERE SEQ_PREFIX = :prefix AND SEQ_DATE = :seqDate AND STATUS = 1";
		List<CountRow> rows = dbClient.query(itflowDb, sql, Map.of("prefix", prefix, "seqDate", seqDate),
				CountRow.class);
		return rows.isEmpty() || rows.get(0).getCnt() == null ? null : rows.get(0).getCnt().intValue();
	}
}
