package com.mpx.infra_manager_java.service.sysparam;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-06
// 變更說明: 新增：SysParamService 單元測試（S6 回合二 a）。鎖定：正常值照用；未設定、空白、非數字、零或負數、
//           不在清單內一律退回預設；UPLOAD_MAX_MB 大於 50 以 50 為準；多列時取第一列（DAO 已排序）
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mpx.infra_manager_java.dao.sysparam.SysParamDao;

class SysParamServiceTest {

	private SysParamDao dao;
	private SysParamService service;

	@BeforeEach
	void setUp() {
		dao = mock(SysParamDao.class);
		service = new SysParamService(dao);
	}

	private void given(String name, String... values) {
		when(dao.findValues(name)).thenReturn(Arrays.asList(values));
	}

	@Test
	void 上傳大小照設定值_可調低() {
		given(SysParamService.UPLOAD_MAX_MB, "20");
		assertThat(service.uploadMaxMb()).isEqualTo(20);
	}

	@Test
	void 上傳大小超過50以50為準() {
		given(SysParamService.UPLOAD_MAX_MB, "200");
		assertThat(service.uploadMaxMb()).isEqualTo(50);
	}

	@Test
	void 上傳大小未設定或格式不合退回50() {
		when(dao.findValues(SysParamService.UPLOAD_MAX_MB)).thenReturn(List.of());
		assertThat(service.uploadMaxMb()).isEqualTo(50);
		for (String bad : new String[] { "", "  ", "abc", "0", "-5", "1.5" }) {
			given(SysParamService.UPLOAD_MAX_MB, bad);
			assertThat(service.uploadMaxMb()).as(bad).isEqualTo(50);
		}
	}

	@Test
	void 上傳檔數照設定值_未設定或格式不合退回30() {
		given(SysParamService.UPLOAD_MAX_FILES, " 10 ");
		assertThat(service.uploadMaxFiles()).isEqualTo(10);
		given(SysParamService.UPLOAD_MAX_FILES, "x");
		assertThat(service.uploadMaxFiles()).isEqualTo(30);
		when(dao.findValues(SysParamService.UPLOAD_MAX_FILES)).thenReturn(List.of());
		assertThat(service.uploadMaxFiles()).isEqualTo(30);
	}

	@Test
	void 多列時取第一列() {
		given(SysParamService.UPLOAD_MAX_FILES, "12", "30");
		assertThat(service.uploadMaxFiles()).isEqualTo(12);
	}

	@Test
	void 流程政策只認兩個值_其餘退回full_only() {
		given(SysParamService.FLOW_POLICY, "by_priority");
		assertThat(service.flowPolicy()).isEqualTo("by_priority");
		given(SysParamService.FLOW_POLICY, "full_only");
		assertThat(service.flowPolicy()).isEqualTo("full_only");
		given(SysParamService.FLOW_POLICY, "BY_PRIORITY");
		assertThat(service.flowPolicy()).isEqualTo("full_only");
		when(dao.findValues(SysParamService.FLOW_POLICY)).thenReturn(List.of());
		assertThat(service.flowPolicy()).isEqualTo("full_only");
	}
}
