package com.mpx.infra_manager_java.dao.sysparam;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-06
// 變更說明: 新增：SysParamDao 單元測試（S6 回合二 a，不連 DB）。鎖定表名走 schema 前綴、只取啟用列、
//           參數名以具名參數傳入、依最後異動時間新到舊排序
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.mpx.common.db.DbClient;
import com.mpx.infra_manager_java.config.DbSchema;
import com.mpx.infra_manager_java.model.sysparam.SysParamRow;

class SysParamDaoTest {

	@Test
	@SuppressWarnings("unchecked")
	void 依參數名查啟用值並以最新異動排前() {
		DbClient db = mock(DbClient.class);
		SysParamRow row = new SysParamRow();
		row.setParamValue("20");
		when(db.query(eq("itflow"), anyString(), anyMap(), eq(SysParamRow.class))).thenReturn(List.of(row));

		List<String> values = new SysParamDao(db, new DbSchema("ITFLOW"), "itflow").findValues("UPLOAD_MAX_MB");

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
		verify(db).query(eq("itflow"), sql.capture(), params.capture(), eq(SysParamRow.class));
		assertThat(sql.getValue())
				.contains("FROM ITFLOW.SYS_PARAM")
				.contains("PARAM_NAME = :name AND STATUS = 1")
				.contains("ORDER BY NVL(UPDATE_DATE, CREATE_DATE) DESC")
				.doesNotContain("UPLOAD_MAX_MB");
		assertThat(params.getValue()).containsEntry("name", "UPLOAD_MAX_MB");
		assertThat(values).containsExactly("20");
	}
}
