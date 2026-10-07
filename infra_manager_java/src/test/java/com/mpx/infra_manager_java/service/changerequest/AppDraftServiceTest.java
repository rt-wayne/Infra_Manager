package com.mpx.infra_manager_java.service.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：建草稿服務的單元測試（S6 回合二 b-1）。鎖定：流程政策（full_only → full；by_priority → PRIO 選項
//           FLOW_ID，沒設定退回 full）；申請人與建立者為登入者、單號用台灣今天；檢查失敗時不取號也不寫入
//           2026-10-07 回合二 b-2：加編輯草稿——成功時主檔→刪子表→重建子表的順序與新版本號；缺版本號 400、
//           單號格式不對 404；更新 0 列時依現況分 404／403／409（非草稿、版本不符），且不動子表
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.security.access.AccessDeniedException;

import com.mpx.infra_manager_java.dao.changerequest.AppWriteDao;
import com.mpx.infra_manager_java.dao.changerequest.FormOptionDao;
import com.mpx.infra_manager_java.model.auth.AuthUser;
import com.mpx.infra_manager_java.model.changerequest.AppDraft;
import com.mpx.infra_manager_java.model.changerequest.AppDraftRequest;
import com.mpx.infra_manager_java.model.changerequest.AppLockRow;
import com.mpx.infra_manager_java.model.changerequest.FormOptionRow;
import com.mpx.infra_manager_java.service.sysparam.SysParamService;
import com.mpx.infra_manager_java.util.TaiwanTime;
import com.mpx.infra_manager_java.web.ApiBadRequestException;
import com.mpx.infra_manager_java.web.ApiConflictException;
import com.mpx.infra_manager_java.web.ApiNotFoundException;

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

	private static AppDraftRequest edit(String title, String prioCode, Long rowVerNo) {
		return new AppDraftRequest(title, prioCode, null, null, null, null, null, null, null, null, null, null, null,
				null, null, null, null, null, null, null, null, null, rowVerNo);
	}

	private static AppLockRow lock(String status, String applicant) {
		AppLockRow r = new AppLockRow();
		r.setAppStatusCode(status);
		r.setApplyUserId(applicant);
		r.setRowVerNo(9L);
		return r;
	}

	private void stubOptions() {
		when(formOptionDao.findActive()).thenReturn(OPTIONS);
		when(sysParamService.flowPolicy()).thenReturn(SysParamService.POLICY_BY_PRIORITY);
	}

	@Test
	void 編輯草稿_更新主檔後子表刪除重建並回新版本() {
		stubOptions();
		when(appWriteDao.updateApp(eq("IM20261007-001"), eq(3L), eq("p1_emergency"), eq("T0001"), any()))
				.thenReturn(1);

		long ver = service.update("IM20261007-001", edit("改標題", "P1", 3L), USER);

		assertThat(ver).isEqualTo(4L);
		InOrder order = inOrder(appWriteDao);
		order.verify(appWriteDao).updateApp(eq("IM20261007-001"), eq(3L), eq("p1_emergency"), eq("T0001"), any());
		order.verify(appWriteDao).deleteChildren("IM20261007-001");
		order.verify(appWriteDao).insertChildren(eq("IM20261007-001"), eq("T0001"), any());
	}

	@Test
	void 編輯_缺版本號400且不寫入() {
		assertThatThrownBy(() -> service.update("IM20261007-001", edit("x", "P1", null), USER))
				.isInstanceOf(ApiBadRequestException.class).hasMessage(AppDraftService.MSG_NO_VERSION);
		verifyNoInteractions(appWriteDao);
	}

	@Test
	void 編輯_單號格式不對404且不查資料庫() {
		assertThatThrownBy(() -> service.update("im'; --", edit("x", "P1", 0L), USER))
				.isInstanceOf(ApiNotFoundException.class);
		verifyNoInteractions(formOptionDao, appWriteDao);
	}

	@Test
	void 編輯_檢查失敗不寫入() {
		when(formOptionDao.findActive()).thenReturn(OPTIONS);
		assertThatThrownBy(() -> service.update("IM20261007-001", edit(" ", "P1", 0L), USER))
				.isInstanceOf(ApiBadRequestException.class);
		verifyNoInteractions(appWriteDao);
	}

	@Test
	void 編輯_0列時依現況判斷404_403_409() {
		stubOptions();
		when(appWriteDao.updateApp(anyString(), anyLong(), anyString(), anyString(), any())).thenReturn(0);

		when(appWriteDao.findLockState("IM20261007-001")).thenReturn(null);
		assertThatThrownBy(() -> service.update("IM20261007-001", edit("x", "P1", 0L), USER))
				.isInstanceOf(ApiNotFoundException.class);

		when(appWriteDao.findLockState("IM20261007-001")).thenReturn(lock("DRAFT", "T0002"));
		assertThatThrownBy(() -> service.update("IM20261007-001", edit("x", "P1", 0L), USER))
				.isInstanceOf(AccessDeniedException.class);

		when(appWriteDao.findLockState("IM20261007-001")).thenReturn(lock("PENDING", "T0001"));
		assertThatThrownBy(() -> service.update("IM20261007-001", edit("x", "P1", 0L), USER))
				.isInstanceOf(ApiConflictException.class).hasMessage(AppDraftService.MSG_NOT_DRAFT);

		when(appWriteDao.findLockState("IM20261007-001")).thenReturn(lock("DRAFT", "T0001"));
		assertThatThrownBy(() -> service.update("IM20261007-001", edit("x", "P1", 0L), USER))
				.isInstanceOf(ApiConflictException.class).hasMessage(AppDraftService.MSG_STALE);

		verify(appWriteDao, never()).deleteChildren(anyString());
		verify(appWriteDao, never()).insertChildren(anyString(), anyString(), any());
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
