package com.mpx.infra_manager_java.dao.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：AppDao 純函式的單元測試（S4，不連 DB）。鎖定 LIKE 樣式：轉大寫、前後加 %、
//           使用者輸入的 %／_／\ 一律以 \ 跳脫（SQL 端 ESCAPE '\'），避免關鍵字變成萬用字元
//           S4 審查修正（Claude Opus 5.5，2026-10-06）：鎖定 findOptions 的類別「其他」補充是從 IM_APP_CATG_OTHER 出發的
//           獨立一段（AppDaoIT 不依賴種子資料，驗不到撈不撈得到）
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.mpx.common.db.DbClient;
import com.mpx.infra_manager_java.config.DbSchema;
import com.mpx.infra_manager_java.model.changerequest.OptionRow;

class AppDaoTest {

	@Test
	void 類別其他補充從CATG_OTHER表獨立取回_不與子項勾選表JOIN() {
		DbClient db = mock(DbClient.class);
		new AppDao(db, new DbSchema("ITFLOW"), "itflow").findOptions("IM20261006-902");

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(db).query(eq("itflow"), sql.capture(), anyMap(), eq(OptionRow.class));
		assertThat(sql.getValue())
				.contains("FROM ITFLOW.IM_APP_CATG_OTHER OT JOIN ITFLOW.IM_FORM_OPTION O ON O.FORM_OPTION_ID = OT.FORM_OPTION_ID")
				.contains("OT.APP_ID = :appId AND OT.STATUS = 1")
				.doesNotContain("OT.FORM_OPTION_ID = M.FORM_OPTION_ID");
	}

	@Test
	void like樣式轉大寫並前後加百分比() {
		assertThat(AppDao.likePattern("sw-core")).isEqualTo("%SW-CORE%");
		assertThat(AppDao.likePattern("交換器")).isEqualTo("%交換器%");
	}

	@Test
	void like萬用字元與跳脫字元本身被跳脫() {
		assertThat(AppDao.likePattern("100%")).isEqualTo("%100\\%%");
		assertThat(AppDao.likePattern("a_b")).isEqualTo("%A\\_B%");
		assertThat(AppDao.likePattern("c:\\dir")).isEqualTo("%C:\\\\DIR%");
		assertThat(AppDao.likePattern("%_\\")).isEqualTo("%\\%\\_\\\\%");
	}
}
