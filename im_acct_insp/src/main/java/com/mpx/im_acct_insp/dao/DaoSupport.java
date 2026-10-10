package com.mpx.im_acct_insp.dao;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：DAO 共用小工具（spec v2.0 §3：業務時間由 Clock 產生、以參數綁定寫入，不用 SYSDATE）
//           LocalDateTime／LocalDate → Timestamp（日期為當天 00:00:00）；單筆計數查詢
// ============================================================

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.mpx.im_acct_insp.model.CountRow;

final class DaoSupport {

	private DaoSupport() {
	}

	static Timestamp ts(LocalDateTime v) {
		return v == null ? null : Timestamp.valueOf(v);
	}

	static Timestamp ts(LocalDate v) {
		return v == null ? null : Timestamp.valueOf(v.atStartOfDay());
	}

	static long count(List<CountRow> rows) {
		return rows.isEmpty() || rows.get(0).getCnt() == null ? 0L : rows.get(0).getCnt();
	}

	static <T> T first(List<T> rows) {
		return rows.isEmpty() ? null : rows.get(0);
	}
}
