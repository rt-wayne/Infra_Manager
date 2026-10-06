package com.mpx.infra_manager_java.dao.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：AppDao 純函式的單元測試（S4，不連 DB）。鎖定 LIKE 樣式：轉大寫、前後加 %、
//           使用者輸入的 %／_／\ 一律以 \ 跳脫（SQL 端 ESCAPE '\'），避免關鍵字變成萬用字元
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AppDaoTest {

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
