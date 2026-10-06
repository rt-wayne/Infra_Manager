package com.mpx.infra_manager_java.dao;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-05
// 變更說明: 新增：DB 健康檢查 DAO（S1）；對本系統資料庫（別名 key db.connect.itflow）執行 SELECT 1 FROM DUAL
// ============================================================

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.mpx.common.db.DbClient;
import com.mpx.infra_manager_java.model.DualRow;

@Repository
public class HealthDao {

	private final DbClient dbClient;

	@Value("${db.connect.itflow}")
	private String itflowDb;

	public HealthDao(DbClient dbClient) {
		this.dbClient = dbClient;
	}

	/** 連得上且查得到 1 才算 UP；連線或 SQL 失敗由呼叫端接例外 */
	public boolean ping() {
		List<DualRow> rows = dbClient.query(itflowDb, "SELECT 1 AS OK FROM DUAL", null, DualRow.class);
		return !rows.isEmpty() && Integer.valueOf(1).equals(rows.get(0).getOk());
	}
}
