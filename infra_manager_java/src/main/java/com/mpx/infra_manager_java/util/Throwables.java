package com.mpx.infra_manager_java.util;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：例外摘要（S2 回合二）。原為 web.ApiExceptionHandler 的 package-private 方法，
//           匯入器（非 web）也要用同一種「只記類別名與 ORA 碼、不記訊息」的寫法，移到 util 共用
// ============================================================

import java.sql.SQLException;

public final class Throwables {

	private Throwables() {
	}

	/** 例外類別名 + 鏈中第一個 SQLException 的錯誤碼；不帶訊息（訊息可能含主機、SQL 參數值） */
	public static String summarize(Throwable e) {
		StringBuilder sb = new StringBuilder(e.getClass().getSimpleName());
		int depth = 0;
		for (Throwable t = e.getCause(); t != null && depth < 32; t = t.getCause(), depth++) {
			if (t instanceof SQLException sqlEx && sqlEx.getErrorCode() > 0) {
				sb.append(" / ").append(t.getClass().getSimpleName()).append(" code=").append(sqlEx.getErrorCode());
				break;
			}
			if (t.getCause() == t) {
				break;
			}
		}
		return sb.toString();
	}
}
