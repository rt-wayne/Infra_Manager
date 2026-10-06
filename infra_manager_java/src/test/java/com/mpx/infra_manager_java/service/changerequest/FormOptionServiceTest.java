package com.mpx.infra_manager_java.service.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-06
// 變更說明: 新增：FormOptionService／FormOptionDao 單元測試（S6 回合二 a）。鎖定：DAO 只取啟用列並依群組排序、
//           欄位逐一轉成回應、上傳限制取自系統參數
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.mpx.common.db.DbClient;
import com.mpx.infra_manager_java.config.DbSchema;
import com.mpx.infra_manager_java.dao.changerequest.FormOptionDao;
import com.mpx.infra_manager_java.model.changerequest.FormOptionRow;
import com.mpx.infra_manager_java.model.changerequest.FormOptionsResponse;
import com.mpx.infra_manager_java.model.changerequest.FormOptionsResponse.FormOption;
import com.mpx.infra_manager_java.service.sysparam.SysParamService;

class FormOptionServiceTest {

	@Test
	void DAO只取啟用列並依群組排序() {
		DbClient db = mock(DbClient.class);
		new FormOptionDao(db, new DbSchema("ITFLOW"), "itflow").findActive();

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(db).query(eq("itflow"), sql.capture(), anyMap(), eq(FormOptionRow.class));
		assertThat(sql.getValue())
				.contains("FROM ITFLOW.IM_FORM_OPTION")
				.contains("WHERE STATUS = 1")
				.contains("ORDER BY GROUP_CODE, SORT_NO, FORM_OPTION_ID");
	}

	@Test
	void 欄位逐一轉成回應並帶上傳限制() {
		FormOptionDao dao = mock(FormOptionDao.class);
		SysParamService sysParam = mock(SysParamService.class);
		FormOptionRow row = new FormOptionRow();
		row.setFormOptionId(11L);
		row.setGroupCode("CATG_ITEM");
		row.setOptionCode("RACK_IN");
		row.setUpFormOptionId(3L);
		row.setOptionName("設備上架");
		row.setColorCode("#2563eb");
		row.setOptionDesc("說明");
		row.setTimeLimitDesc("時限");
		row.setPrioFlowDesc("流程");
		row.setSampleDesc("範例");
		row.setFlowId("F1");
		row.setSortNo(2);
		when(dao.findActive()).thenReturn(List.of(row));
		when(sysParam.uploadMaxMb()).thenReturn(20);
		when(sysParam.uploadMaxFiles()).thenReturn(10);

		FormOptionsResponse res = new FormOptionService(dao, sysParam).load();

		assertThat(res.options()).containsExactly(new FormOption(11L, "CATG_ITEM", "RACK_IN", "設備上架", 3L,
				"#2563eb", "說明", "時限", "流程", "範例", "F1", 2));
		assertThat(res.upload().maxMb()).isEqualTo(20);
		assertThat(res.upload().maxFiles()).isEqualTo(10);
	}
}
