package com.mpx.infra_manager_java.service.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：建草稿服務的單元測試（S6 回合二 b-1）。鎖定：流程政策（full_only → full；by_priority → PRIO 選項
//           FLOW_ID，沒設定退回 full）；申請人與建立者為登入者、單號用台灣今天；檢查失敗時不取號也不寫入
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.mpx.infra_manager_java.dao.changerequest.AppWriteDao;
import com.mpx.infra_manager_java.dao.changerequest.FormOptionDao;
import com.mpx.infra_manager_java.model.auth.AuthUser;
import com.mpx.infra_manager_java.model.changerequest.AppDraft;
import com.mpx.infra_manager_java.model.changerequest.AppDraftRequest;
import com.mpx.infra_manager_java.model.changerequest.FormOptionRow;
import com.mpx.infra_manager_java.service.sysparam.SysParamService;
import com.mpx.infra_manager_java.util.TaiwanTime;
import com.mpx.infra_manager_java.web.ApiBadRequestException;

class AppDraftServiceTest {

	private static final AuthUser USER = new AuthUser("T0001", "wayne", "王小明", List.of("infra"), false);

	private final FormOptionDao formOptionDao = mock(FormOptionDao.class);
	private final SysParamService sysParamService = mock(SysParamService.class);
	private final AppSeqService appSeqService = mock(AppSeqService.class);
	private final AppWriteDao appWriteDao = mock(AppWriteDao.class);
	private final AppDraftService service = new AppDraftService(formOptionDao, sysParamService, appSeqService,
			appWriteDao);

	private static FormOptionRow prio(String code, String flowId) {
		FormOptionRow o = new FormOptionRow();
		o.setFormOptionId((long) code.charAt(1));
		o.setGroupCode("PRIO");
		o.setOptionCode(code);
		o.setFlowId(flowId);
		return o;
	}

	private static final List<FormOptionRow> OPTIONS = List.of(prio("P1", "p1_emergency"), prio("P2", "p2_high"),
			prio("P3", null));

	private static AppDraftRequest request(String title, String prioCode) {
		return new AppDraftRequest(title, prioCode, null, null, null, null, null, null, null, null, null, null, null,
				null, null, null, null, null, null, null, null, null, null);
	}

	@Test
	void 建草稿_取號並以登入者寫入主檔與子表() {
		when(formOptionDao.findActive()).thenReturn(OPTIONS);
		when(sysParamService.flowPolicy()).thenReturn(SysParamService.POLICY_FULL_ONLY);
		when(appSeqService.nextNo(eq("IM"), any(), eq("T0001"))).thenReturn(12);

		String appId = service.create(request("新單", "P1"), USER);

		String expected = AppSeqService.onlineAppId(TaiwanTime.today(), 12);
		assertThat(appId).isEqualTo(expected);
		verify(appSeqService).nextNo("IM", TaiwanTime.today(), "T0001");
		ArgumentCaptor<AppDraft> draft = ArgumentCaptor.forClass(AppDraft.class);
		verify(appWriteDao).insertApp(eq(expected), eq("full"), eq("T0001"),
				eq(TaiwanTime.startOf(TaiwanTime.today())), draft.capture());
		assertThat(draft.getValue().title()).isEqualTo("新單");
		verify(appWriteDao).insertChildren(expected, "T0001", draft.getValue());
	}

	@Test
	void 檢查失敗不取號也不寫入() {
		when(formOptionDao.findActive()).thenReturn(OPTIONS);
		assertThatThrownBy(() -> service.create(request(" ", "P1"), USER)).isInstanceOf(ApiBadRequestException.class);
		verifyNoInteractions(appSeqService, appWriteDao);
	}

	@Test
	void 流程政策() {
		assertThat(AppDraftService.flowFor("P1", SysParamService.POLICY_FULL_ONLY, OPTIONS)).isEqualTo("full");
		assertThat(AppDraftService.flowFor("P1", SysParamService.POLICY_BY_PRIORITY, OPTIONS))
				.isEqualTo("p1_emergency");
		assertThat(AppDraftService.flowFor("P2", SysParamService.POLICY_BY_PRIORITY, OPTIONS)).isEqualTo("p2_high");
		assertThat(AppDraftService.flowFor("P3", SysParamService.POLICY_BY_PRIORITY, OPTIONS)).isEqualTo("full");
		assertThat(AppDraftService.flowFor("P4", SysParamService.POLICY_BY_PRIORITY, OPTIONS)).isEqualTo("full");
	}

	@Test
	void by_priority時寫入對應流程() {
		when(formOptionDao.findActive()).thenReturn(OPTIONS);
		when(sysParamService.flowPolicy()).thenReturn(SysParamService.POLICY_BY_PRIORITY);
		when(appSeqService.nextNo(anyString(), any(), anyString())).thenReturn(1);
		service.create(request("急件", "P2"), USER);
		verify(appWriteDao).insertApp(anyString(), eq("p2_high"), eq("T0001"), any(), any());
	}
}
