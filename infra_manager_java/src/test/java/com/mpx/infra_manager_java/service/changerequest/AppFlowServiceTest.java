package com.mpx.infra_manager_java.service.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-07
// 變更說明: 新增：送審／撤回服務的單元測試（S7 R1）。鎖定：送審第一句是條件式 UPDATE，admin 不帶申請人條件；成功時的
//           順序（重算 FLOW_ID → 建實例 → 查回 → 展開關卡 → 展開候選人並排除申請人 → 事件）與回傳新版本；
//           0 列時依現況分 404／403／409；必填缺漏 400 且不建實例；關卡 0 人／沒關卡 400；
//           撤回只限申請人、已有關卡簽過 409、成功時 CANCELLED／RECALLED／RECALL 事件帶原因
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.access.AccessDeniedException;

import com.mpx.infra_manager_java.dao.changerequest.AppDao;
import com.mpx.infra_manager_java.dao.changerequest.AppWriteDao;
import com.mpx.infra_manager_java.dao.changerequest.ApprovalWriteDao;
import com.mpx.infra_manager_java.dao.changerequest.FormOptionDao;
import com.mpx.infra_manager_java.model.auth.AuthUser;
import com.mpx.infra_manager_java.model.changerequest.AppLockRow;
import com.mpx.infra_manager_java.model.changerequest.AppRow;
import com.mpx.infra_manager_java.model.changerequest.ApprRow;
import com.mpx.infra_manager_java.model.changerequest.ApprStepRow;
import com.mpx.infra_manager_java.model.changerequest.FlowActionRequest;
import com.mpx.infra_manager_java.service.sysparam.SysParamService;
import com.mpx.infra_manager_java.util.TextTooLongException;
import com.mpx.infra_manager_java.web.ApiBadRequestException;
import com.mpx.infra_manager_java.web.ApiConflictException;
import com.mpx.infra_manager_java.web.ApiNotFoundException;

class AppFlowServiceTest {

	private static final String APP = "IM20261007001";
	private static final AuthUser USER = new AuthUser("T0001", "wayne", "王小明", List.of("infra"), false);
	private static final AuthUser ADMIN = new AuthUser("A0001", "root", "管理員", List.of("admin"), false);
	private static final AuthUser OTHER = new AuthUser("T0002", "other", "別人", List.of("infra"), false);

	private final AppDao appDao = mock(AppDao.class);
	private final AppWriteDao appWriteDao = mock(AppWriteDao.class);
	private final ApprovalWriteDao approvalWriteDao = mock(ApprovalWriteDao.class);
	private final FormOptionDao formOptionDao = mock(FormOptionDao.class);
	private final SysParamService sysParamService = mock(SysParamService.class);
	private final AppFlowService service = new AppFlowService(appDao, appWriteDao, approvalWriteDao, formOptionDao,
			sysParamService);

	private static AppRow app() {
		AppRow a = new AppRow();
		a.setAppId(APP);
		a.setApplyUserId("T0001");
		a.setAppStatusCode("DRAFT");
		a.setCurrVerNo(1);
		a.setRowVerNo(3L);
		a.setPrioCode("P3");
		a.setApplyDeptName("資訊處");
		a.setApplyTel("1234");
		a.setApplyEmail("it@example.com");
		a.setIsSelfExec(1);
		a.setIsSupExec(0);
		a.setWorkModeCode("ONSITE");
		a.setWorkSubj("主旨");
		return a;
	}

	private static AppLockRow lock(String status, String applicant) {
		AppLockRow l = new AppLockRow();
		l.setAppStatusCode(status);
		l.setApplyUserId(applicant);
		l.setRowVerNo(9L);
		return l;
	}

	private static ApprRow appr(long id) {
		ApprRow r = new ApprRow();
		r.setApprId(id);
		r.setApprStatusCode("PENDING");
		return r;
	}

	private void happySubmitStubs() {
		when(appWriteDao.transition(APP, 3L, "DRAFT", "IN_REVIEW", "T0001", true)).thenReturn(1);
		when(appDao.findById(APP)).thenReturn(Optional.of(app()));
		when(sysParamService.flowPolicy()).thenReturn(SysParamService.POLICY_FULL_ONLY);
		when(formOptionDao.findActive()).thenReturn(List.of());
		when(approvalWriteDao.findPendingAppr(APP, 1)).thenReturn(appr(77L));
		when(approvalWriteDao.insertSteps(77L, "full", "T0001")).thenReturn(5);
		when(approvalWriteDao.countOpenSteps(77L)).thenReturn(5L);
		when(approvalWriteDao.insertCandidates(77L, "T0001", "T0001")).thenReturn(6);
		when(approvalWriteDao.findOpenStepsWithoutCandidate(77L)).thenReturn(List.of());
	}

	@Test
	void 送審_申請人成功時依序鎖主檔_重算流程_建實例_展開關卡與候選人_寫事件_回新版本() {
		happySubmitStubs();

		long newVer = service.submit(APP, new FlowActionRequest(3L, null), USER);

		assertThat(newVer).isEqualTo(4L);
		InOrder o = inOrder(appWriteDao, approvalWriteDao);
		o.verify(appWriteDao).transition(APP, 3L, "DRAFT", "IN_REVIEW", "T0001", true);
		o.verify(appWriteDao).updateFlowId(APP, "full", "T0001");
		o.verify(approvalWriteDao).insertAppr(APP, 1, "full", "T0001");
		o.verify(approvalWriteDao).findPendingAppr(APP, 1);
		o.verify(approvalWriteDao).insertSteps(77L, "full", "T0001");
		o.verify(approvalWriteDao).insertCandidates(77L, "T0001", "T0001");
		o.verify(approvalWriteDao).insertEvent(APP, 1, "SUBMIT", "T0001", null);
		verify(appWriteDao, never()).findLockState(anyString());
	}

	@Test
	void 送審_admin代送不帶申請人條件_候選人仍排除申請人() {
		when(appWriteDao.transition(APP, 3L, "DRAFT", "IN_REVIEW", "A0001", false)).thenReturn(1);
		when(appDao.findById(APP)).thenReturn(Optional.of(app()));
		when(sysParamService.flowPolicy()).thenReturn(SysParamService.POLICY_FULL_ONLY);
		when(formOptionDao.findActive()).thenReturn(List.of());
		when(approvalWriteDao.findPendingAppr(APP, 1)).thenReturn(appr(78L));
		when(approvalWriteDao.countOpenSteps(78L)).thenReturn(5L);
		when(approvalWriteDao.findOpenStepsWithoutCandidate(78L)).thenReturn(List.of());

		service.submit(APP, new FlowActionRequest(3L, null), ADMIN);

		verify(appWriteDao).transition(APP, 3L, "DRAFT", "IN_REVIEW", "A0001", false);
		verify(approvalWriteDao).insertCandidates(78L, "T0001", "A0001");
		verify(approvalWriteDao).insertEvent(APP, 1, "SUBMIT", "A0001", null);
	}

	@Test
	void 送審_缺版本號400_單號格式不對404_不碰DB() {
		assertThatThrownBy(() -> service.submit(APP, new FlowActionRequest(null, null), USER))
				.isInstanceOf(ApiBadRequestException.class).hasMessage(AppDraftService.MSG_NO_VERSION);
		assertThatThrownBy(() -> service.submit("bad id", new FlowActionRequest(1L, null), USER))
				.isInstanceOf(ApiNotFoundException.class);
		verifyNoInteractions(appWriteDao, approvalWriteDao, appDao);
	}

	@Test
	void 送審_更新0列時依現況分流() {
		when(appWriteDao.transition(anyString(), anyLong(), anyString(), anyString(), anyString(), eq(true)))
				.thenReturn(0);

		when(appWriteDao.findLockState(APP)).thenReturn(null);
		assertThatThrownBy(() -> service.submit(APP, new FlowActionRequest(3L, null), USER))
				.isInstanceOf(ApiNotFoundException.class);

		when(appWriteDao.findLockState(APP)).thenReturn(lock("DRAFT", "T0001"));
		assertThatThrownBy(() -> service.submit(APP, new FlowActionRequest(3L, null), OTHER))
				.isInstanceOf(AccessDeniedException.class).hasMessage(AppFlowService.MSG_SUBMIT_FORBIDDEN);

		when(appWriteDao.findLockState(APP)).thenReturn(lock("IN_REVIEW", "T0001"));
		assertThatThrownBy(() -> service.submit(APP, new FlowActionRequest(3L, null), USER))
				.isInstanceOf(ApiConflictException.class).hasMessage(AppFlowService.MSG_SUBMIT_NOT_DRAFT);

		when(appWriteDao.findLockState(APP)).thenReturn(lock("DRAFT", "T0001"));
		assertThatThrownBy(() -> service.submit(APP, new FlowActionRequest(2L, null), USER))
				.isInstanceOf(ApiConflictException.class).hasMessage(AppFlowService.MSG_STALE);

		verifyNoInteractions(approvalWriteDao);
	}

	@Test
	void 送審_必填缺漏400_不重算流程也不建實例() {
		when(appWriteDao.transition(APP, 3L, "DRAFT", "IN_REVIEW", "T0001", true)).thenReturn(1);
		AppRow a = app();
		a.setApplyTel(null);
		a.setWorkSubj("");
		when(appDao.findById(APP)).thenReturn(Optional.of(a));

		assertThatThrownBy(() -> service.submit(APP, new FlowActionRequest(3L, null), USER))
				.isInstanceOf(ApiBadRequestException.class).hasMessage("送審前請先補齊：聯絡電話、作業主題");

		verify(appWriteDao, never()).updateFlowId(anyString(), anyString(), anyString());
		verifyNoInteractions(approvalWriteDao);
	}

	@Test
	void 送審_流程沒有關卡400() {
		happySubmitStubs();
		when(approvalWriteDao.countOpenSteps(77L)).thenReturn(0L);

		assertThatThrownBy(() -> service.submit(APP, new FlowActionRequest(3L, null), USER))
				.isInstanceOf(ApiBadRequestException.class).hasMessage(AppFlowService.MSG_NO_STEP);

		verify(approvalWriteDao, never()).insertCandidates(anyLong(), anyString(), anyString());
		verify(approvalWriteDao, never()).insertEvent(anyString(), anyInt(), anyString(), anyString(), any());
	}

	@Test
	void 送審_排除申請人後某關沒人可簽400並指名關卡() {
		happySubmitStubs();
		ApprStepRow s = new ApprStepRow();
		s.setSeqNo(2);
		s.setStepName("部門主管");
		when(approvalWriteDao.findOpenStepsWithoutCandidate(77L)).thenReturn(List.of(s));

		assertThatThrownBy(() -> service.submit(APP, new FlowActionRequest(3L, null), USER))
				.isInstanceOf(ApiBadRequestException.class).hasMessage("第 2 關（部門主管）沒有可簽核的人，請聯絡管理員");

		verify(approvalWriteDao, never()).insertEvent(anyString(), anyInt(), anyString(), anyString(), any());
	}

	@Test
	void 撤回_申請人成功時關卡CANCELLED_實例RECALLED_事件帶原因_回新版本() {
		when(appWriteDao.transition(APP, 3L, "IN_REVIEW", "DRAFT", "T0001", true)).thenReturn(1);
		AppRow a = app();
		a.setAppStatusCode("IN_REVIEW");
		when(appDao.findById(APP)).thenReturn(Optional.of(a));
		when(approvalWriteDao.findPendingAppr(APP, 1)).thenReturn(appr(77L));
		when(approvalWriteDao.countDecidedSteps(77L)).thenReturn(0L);

		long newVer = service.recall(APP, new FlowActionRequest(3L, "資料要補\r\n再送"), USER);

		assertThat(newVer).isEqualTo(4L);
		InOrder o = inOrder(appWriteDao, approvalWriteDao);
		o.verify(appWriteDao).transition(APP, 3L, "IN_REVIEW", "DRAFT", "T0001", true);
		o.verify(approvalWriteDao).closeOpenSteps(77L, "CANCELLED", "T0001");
		o.verify(approvalWriteDao).closeAppr(77L, "RECALLED", "T0001");
		o.verify(approvalWriteDao).insertEvent(APP, 1, "RECALL", "T0001", "資料要補\n再送");
	}

	@Test
	void 撤回_原因空白存null_超過2000字400() {
		when(appWriteDao.transition(APP, 3L, "IN_REVIEW", "DRAFT", "T0001", true)).thenReturn(1);
		AppRow a = app();
		a.setAppStatusCode("IN_REVIEW");
		when(appDao.findById(APP)).thenReturn(Optional.of(a));
		when(approvalWriteDao.findPendingAppr(APP, 1)).thenReturn(appr(77L));

		service.recall(APP, new FlowActionRequest(3L, "   "), USER);
		verify(approvalWriteDao).insertEvent(eq(APP), eq(1), eq("RECALL"), eq("T0001"), isNull());

		assertThatThrownBy(() -> service.recall(APP, new FlowActionRequest(3L, "字".repeat(2001)), USER))
				.isInstanceOf(TextTooLongException.class);
	}

	@Test
	void 撤回_更新0列時依現況分流_admin也不能代撤() {
		when(appWriteDao.transition(anyString(), anyLong(), anyString(), anyString(), anyString(), eq(true)))
				.thenReturn(0);

		when(appWriteDao.findLockState(APP)).thenReturn(null);
		assertThatThrownBy(() -> service.recall(APP, new FlowActionRequest(3L, null), USER))
				.isInstanceOf(ApiNotFoundException.class);

		when(appWriteDao.findLockState(APP)).thenReturn(lock("IN_REVIEW", "T0001"));
		assertThatThrownBy(() -> service.recall(APP, new FlowActionRequest(3L, null), ADMIN))
				.isInstanceOf(AccessDeniedException.class).hasMessage(AppFlowService.MSG_RECALL_FORBIDDEN);

		when(appWriteDao.findLockState(APP)).thenReturn(lock("DRAFT", "T0001"));
		assertThatThrownBy(() -> service.recall(APP, new FlowActionRequest(3L, null), USER))
				.isInstanceOf(ApiConflictException.class).hasMessage(AppFlowService.MSG_RECALL_NOT_IN_REVIEW);

		when(appWriteDao.findLockState(APP)).thenReturn(lock("IN_REVIEW", "T0001"));
		assertThatThrownBy(() -> service.recall(APP, new FlowActionRequest(1L, null), USER))
				.isInstanceOf(ApiConflictException.class).hasMessage(AppFlowService.MSG_STALE);

		verifyNoInteractions(approvalWriteDao);
	}

	@Test
	void 撤回_已有關卡簽核完成409_不動關卡與實例() {
		when(appWriteDao.transition(APP, 3L, "IN_REVIEW", "DRAFT", "T0001", true)).thenReturn(1);
		AppRow a = app();
		a.setAppStatusCode("IN_REVIEW");
		when(appDao.findById(APP)).thenReturn(Optional.of(a));
		when(approvalWriteDao.findPendingAppr(APP, 1)).thenReturn(appr(77L));
		when(approvalWriteDao.countDecidedSteps(77L)).thenReturn(1L);

		assertThatThrownBy(() -> service.recall(APP, new FlowActionRequest(3L, null), USER))
				.isInstanceOf(ApiConflictException.class).hasMessage(AppFlowService.MSG_RECALL_DECIDED);

		verify(approvalWriteDao, never()).closeOpenSteps(anyLong(), anyString(), anyString());
		verify(approvalWriteDao, never()).closeAppr(anyLong(), anyString(), anyString());
	}
}
