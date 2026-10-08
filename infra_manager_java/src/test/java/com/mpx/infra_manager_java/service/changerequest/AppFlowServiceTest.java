package com.mpx.infra_manager_java.service.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-07
// 變更說明: 新增：送審／撤回服務的單元測試（S7 R1）。鎖定：送審第一句是條件式 UPDATE，admin 不帶申請人條件；成功時的
//           順序（重算 FLOW_ID → 建實例 → 查回 → 展開關卡 → 展開候選人並排除申請人 → 事件）與回傳新版本；
//           0 列時依現況分 404／403／409；必填缺漏 400 且不建實例；關卡 0 人／沒關卡 400；
//           撤回只限申請人、已有關卡簽過 409、成功時 CANCELLED／RECALLED／RECALL 事件帶原因
//           2026-10-07 S7 R2：加簽核——同意進下一關或末關結案 APPROVED；退件 SKIPPED＋REJECTED；意見空白填「同意」、
//           退件沒意見 400；非目前關卡簽核人 403；版本不符／被搶簽 409
//           2026-10-07 S9 R1：加補件——鎖是 REJECTED → IN_REVIEW 且只限申請人（admin 403）；成功順序：快照舊版進 IM_APP_VER
//           （APP_VER_NO＝舊版次、FORM_JSON 是退件當時的標題與 schema 1）→ 覆寫主檔 → 子表重建 → 新版建實例 → RESUBMIT 事件帶說明；
//           表單格式錯 400 不碰 DB；必填缺漏 400 不建實例；CLOSE_STATUS_CODE 三分支；附件索引只含舊版結束前上傳的檔
//           2026-10-08 S8 R2：建構子多 MailNotifier（mock）；鎖定信件事件掛點——送審／補件在候選人檢查過後寄待簽核、
//           同意進下一關寄待簽核、末關同意寄核准完成、退件寄退件（帶關卡名、簽核人姓名、意見）、撤回在 closeOpenSteps 之前寄；
//           失敗路徑（400／403／409）一封都不寄
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

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.security.access.AccessDeniedException;

import com.mpx.infra_manager_java.dao.changerequest.AppDao;
import com.mpx.infra_manager_java.dao.changerequest.AppVerDao;
import com.mpx.infra_manager_java.dao.changerequest.AppWriteDao;
import com.mpx.infra_manager_java.dao.changerequest.ApprovalDao;
import com.mpx.infra_manager_java.dao.changerequest.ApprovalWriteDao;
import com.mpx.infra_manager_java.dao.changerequest.AttachDao;
import com.mpx.infra_manager_java.dao.changerequest.FormOptionDao;
import com.mpx.infra_manager_java.model.auth.AuthUser;
import com.mpx.infra_manager_java.model.changerequest.AppDraft;
import com.mpx.infra_manager_java.model.changerequest.AppDraftRequest;
import com.mpx.infra_manager_java.model.changerequest.AppLockRow;
import com.mpx.infra_manager_java.model.changerequest.AppRow;
import com.mpx.infra_manager_java.model.changerequest.AppVersionSnapshot;
import com.mpx.infra_manager_java.model.changerequest.ApprRow;
import com.mpx.infra_manager_java.model.changerequest.ApprStepRow;
import com.mpx.infra_manager_java.model.changerequest.AttachRow;
import com.mpx.infra_manager_java.model.changerequest.CandRow;
import com.mpx.infra_manager_java.model.changerequest.DecisionRequest;
import com.mpx.infra_manager_java.model.changerequest.EventRow;
import com.mpx.infra_manager_java.model.changerequest.FlowActionRequest;
import com.mpx.infra_manager_java.model.changerequest.ResubmitRequest;
import com.mpx.infra_manager_java.service.mail.MailNotifier;
import com.mpx.infra_manager_java.service.sysparam.SysParamService;
import com.mpx.infra_manager_java.util.TextTooLongException;
import com.mpx.infra_manager_java.web.ApiBadRequestException;
import com.mpx.infra_manager_java.web.ApiConflictException;
import com.mpx.infra_manager_java.web.ApiNotFoundException;

import tools.jackson.databind.json.JsonMapper;

class AppFlowServiceTest {

	private static final String APP = "IM20261007001";
	private static final AuthUser USER = new AuthUser("T0001", "wayne", "王小明", List.of("infra"), false);
	private static final AuthUser ADMIN = new AuthUser("A0001", "root", "管理員", List.of("admin"), false);
	private static final AuthUser OTHER = new AuthUser("T0002", "other", "別人", List.of("infra"), false);

	private final AppDao appDao = mock(AppDao.class);
	private final AppWriteDao appWriteDao = mock(AppWriteDao.class);
	private final ApprovalDao approvalDao = mock(ApprovalDao.class);
	private final ApprovalWriteDao approvalWriteDao = mock(ApprovalWriteDao.class);
	private final FormOptionDao formOptionDao = mock(FormOptionDao.class);
	private final SysParamService sysParamService = mock(SysParamService.class);
	private final AppVerDao appVerDao = mock(AppVerDao.class);
	private final AttachDao attachDao = mock(AttachDao.class);
	private final JsonMapper jsonMapper = JsonMapper.builder().build();
	private final MailNotifier mailNotifier = mock(MailNotifier.class);
	private final AppFlowService service = new AppFlowService(appDao, appWriteDao, approvalDao, approvalWriteDao,
			formOptionDao, sysParamService, appVerDao, attachDao, jsonMapper, mailNotifier);

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
		InOrder o = inOrder(appWriteDao, approvalWriteDao, mailNotifier);
		o.verify(appWriteDao).transition(APP, 3L, "DRAFT", "IN_REVIEW", "T0001", true);
		o.verify(appWriteDao).updateFlowId(APP, "full", "T0001");
		o.verify(approvalWriteDao).insertAppr(APP, 1, "full", "T0001");
		o.verify(approvalWriteDao).findPendingAppr(APP, 1);
		o.verify(approvalWriteDao).insertSteps(77L, "full", "T0001");
		o.verify(approvalWriteDao).insertCandidates(77L, "T0001", "T0001");
		o.verify(approvalWriteDao).findOpenStepsWithoutCandidate(77L);
		o.verify(mailNotifier).stepPending(APP, 77L, "T0001");
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
		verifyNoInteractions(mailNotifier);
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
		verifyNoInteractions(mailNotifier);
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
		InOrder o = inOrder(appWriteDao, approvalWriteDao, mailNotifier);
		o.verify(appWriteDao).transition(APP, 3L, "IN_REVIEW", "DRAFT", "T0001", true);
		o.verify(mailNotifier).recalled(APP, 77L, "資料要補\n再送", "T0001");
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

	private static ApprStepRow step(long id, int seq, String status) {
		ApprStepRow s = new ApprStepRow();
		s.setApprStepId(id);
		s.setSeqNo(seq);
		s.setStepName("第 " + seq + " 關");
		s.setStepStatusCode(status);
		return s;
	}

	private static CandRow cand(long stepId, String userId) {
		CandRow c = new CandRow();
		c.setApprStepId(stepId);
		c.setUserId(userId);
		return c;
	}

	private static final AuthUser APPROVER = new AuthUser("S4U002", "idc", "機房", List.of("idc_admin"), false);

	/** 兩關：第 1 關（id 11）PENDING 候選人 S4U002、S4U009；第 2 關（id 12）WAITING */
	private void happyDecideStubs() {
		when(appWriteDao.transition(APP, 3L, "IN_REVIEW", "IN_REVIEW", "S4U002", false)).thenReturn(1);
		AppRow a = app();
		a.setAppStatusCode("IN_REVIEW");
		when(appDao.findById(APP)).thenReturn(Optional.of(a));
		when(approvalWriteDao.findPendingAppr(APP, 1)).thenReturn(appr(77L));
		when(approvalDao.findSteps(77L)).thenReturn(List.of(step(11, 1, "PENDING"), step(12, 2, "WAITING")));
		when(approvalDao.findCandidates(77L)).thenReturn(List.of(cand(11, "S4U002"), cand(11, "S4U009")));
		when(approvalWriteDao.decideStep(eq(11L), anyString(), eq("S4U002"), anyString())).thenReturn(1);
	}

	@Test
	void 簽核_同意有下一關時只進下一關_意見空白填同意_回新版本() {
		happyDecideStubs();
		when(approvalWriteDao.activateNext(77L, "S4U002")).thenReturn(1);

		long newVer = service.decide(APP, new DecisionRequest(3L, "APPROVE", " "), APPROVER);

		assertThat(newVer).isEqualTo(4L);
		InOrder o = inOrder(appWriteDao, approvalWriteDao);
		o.verify(appWriteDao).transition(APP, 3L, "IN_REVIEW", "IN_REVIEW", "S4U002", false);
		o.verify(approvalWriteDao).decideStep(11L, "APPROVED", "S4U002", "同意");
		o.verify(approvalWriteDao).activateNext(77L, "S4U002");
		verify(approvalWriteDao, never()).closeAppr(anyLong(), anyString(), anyString());
		verify(appWriteDao, never()).updateStatus(anyString(), anyString(), anyString());
		verify(mailNotifier).stepPending(APP, 77L, "S4U002");
		verify(mailNotifier, never()).approved(anyString(), anyLong(), anyString());
	}

	@Test
	void 簽核_末關同意時結案APPROVED() {
		happyDecideStubs();
		when(approvalWriteDao.activateNext(77L, "S4U002")).thenReturn(0);

		service.decide(APP, new DecisionRequest(3L, "APPROVE", "OK"), APPROVER);

		verify(approvalWriteDao).decideStep(11L, "APPROVED", "S4U002", "OK");
		verify(approvalWriteDao).closeAppr(77L, "APPROVED", "S4U002");
		verify(appWriteDao).updateStatus(APP, "APPROVED", "S4U002");
		verify(approvalWriteDao, never()).closeOpenSteps(anyLong(), anyString(), anyString());
		InOrder o = inOrder(appWriteDao, mailNotifier);
		o.verify(appWriteDao).updateStatus(APP, "APPROVED", "S4U002");
		o.verify(mailNotifier).approved(APP, 77L, "S4U002");
		verify(mailNotifier, never()).stepPending(anyString(), anyLong(), anyString());
	}

	@Test
	void 簽核_退件時剩餘關卡SKIPPED_結案REJECTED_沒意見400() {
		happyDecideStubs();

		service.decide(APP, new DecisionRequest(3L, "REJECT", "資料不全"), APPROVER);

		InOrder o = inOrder(appWriteDao, approvalWriteDao);
		o.verify(approvalWriteDao).decideStep(11L, "REJECTED", "S4U002", "資料不全");
		o.verify(approvalWriteDao).closeOpenSteps(77L, "SKIPPED", "S4U002");
		o.verify(approvalWriteDao).closeAppr(77L, "REJECTED", "S4U002");
		o.verify(appWriteDao).updateStatus(APP, "REJECTED", "S4U002");
		verify(approvalWriteDao, never()).activateNext(anyLong(), anyString());
		verify(mailNotifier).rejected(APP, 77L, "第 1 關", "機房", "資料不全", "S4U002");

		assertThatThrownBy(() -> service.decide(APP, new DecisionRequest(3L, "REJECT", "  "), APPROVER))
				.isInstanceOf(ApiBadRequestException.class).hasMessage(DecisionPolicy.MSG_REJECT_NEEDS_MEMO);
		assertThatThrownBy(() -> service.decide(APP, new DecisionRequest(3L, "MAYBE", "x"), APPROVER))
				.isInstanceOf(ApiBadRequestException.class).hasMessage(DecisionPolicy.MSG_BAD_DECISION);
	}

	@Test
	void 簽核_非目前關卡候選人403_且鎖內判斷不信任前端() {
		happyDecideStubs();
		when(appWriteDao.transition(APP, 3L, "IN_REVIEW", "IN_REVIEW", "T0002", false)).thenReturn(1);

		assertThatThrownBy(() -> service.decide(APP, new DecisionRequest(3L, "APPROVE", null), OTHER))
				.isInstanceOf(AccessDeniedException.class).hasMessage(AppFlowService.MSG_DECIDE_FORBIDDEN);

		verify(approvalWriteDao, never()).decideStep(anyLong(), anyString(), anyString(), anyString());
		verifyNoInteractions(mailNotifier);
	}

	@Test
	void 簽核_版本不符或關卡已被搶簽409_不在審核中409_不存在404() {
		when(appWriteDao.transition(anyString(), anyLong(), anyString(), anyString(), anyString(), eq(false)))
				.thenReturn(0);
		when(appWriteDao.findLockState(APP)).thenReturn(lock("IN_REVIEW", "T0001"));
		assertThatThrownBy(() -> service.decide(APP, new DecisionRequest(2L, "APPROVE", null), APPROVER))
				.isInstanceOf(ApiConflictException.class).hasMessage(AppFlowService.MSG_DECIDE_STALE);
		when(appWriteDao.findLockState(APP)).thenReturn(lock("APPROVED", "T0001"));
		assertThatThrownBy(() -> service.decide(APP, new DecisionRequest(3L, "APPROVE", null), APPROVER))
				.isInstanceOf(ApiConflictException.class).hasMessage(AppFlowService.MSG_DECIDE_NOT_IN_REVIEW);
		when(appWriteDao.findLockState(APP)).thenReturn(null);
		assertThatThrownBy(() -> service.decide(APP, new DecisionRequest(3L, "APPROVE", null), APPROVER))
				.isInstanceOf(ApiNotFoundException.class);
		verifyNoInteractions(approvalWriteDao);

		happyDecideStubs();
		when(approvalWriteDao.decideStep(eq(11L), anyString(), eq("S4U002"), anyString())).thenReturn(0);
		assertThatThrownBy(() -> service.decide(APP, new DecisionRequest(3L, "APPROVE", null), APPROVER))
				.isInstanceOf(ApiConflictException.class).hasMessage(AppFlowService.MSG_DECIDE_STALE);
		verify(approvalWriteDao, never()).activateNext(anyLong(), anyString());
		verifyNoInteractions(mailNotifier);
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
		verifyNoInteractions(mailNotifier);
	}

	// ---------- 補件（S9 R1） ----------

	private static AppDraftRequest form(String title) {
		return new AppDraftRequest(title, "P3", new AppDraftRequest.Applicant("資訊處", "1234", "it@example.com"), true,
				false, "ONSITE", null, null, "新主旨", null, null, null, null, null, null, null, null, null, null, null,
				null, null, null);
	}

	private static ResubmitRequest resubmit(long rowVerNo, String memo) {
		return new ResubmitRequest(rowVerNo, memo, form("改過的標題"));
	}

	private static EventRow event(int verNo, String code, String memo) {
		EventRow e = new EventRow();
		e.setAppVerNo(verNo);
		e.setEventCode(code);
		e.setMemo(memo);
		return e;
	}

	private static AttachRow attach(long id, String createdAt) {
		AttachRow a = new AttachRow();
		a.setAttachId(id);
		a.setOwnerType("APP");
		a.setOwnerId(APP);
		a.setOrigFileName("f" + id + ".pdf");
		a.setFileByteQty(10L);
		a.setMimeType("application/pdf");
		a.setCreateDate(Timestamp.valueOf(createdAt));
		return a;
	}

	/** 退件中的 v1：主檔 REJECTED、實例 77 REJECTED（第 1 關退件意見「資料不全」）、附件兩個（一個在退件後才傳） */
	private void happyResubmitStubs() {
		when(appWriteDao.transition(APP, 3L, "REJECTED", "IN_REVIEW", "T0001", true)).thenReturn(1);
		AppRow a = app();
		a.setAppStatusCode("REJECTED");
		a.setAppTitle("退件當時的標題");
		when(appDao.findById(APP)).thenReturn(Optional.of(a));
		when(sysParamService.flowPolicy()).thenReturn(SysParamService.POLICY_FULL_ONLY);
		when(formOptionDao.findActive()).thenReturn(List.of());
		ApprRow rejected = appr(77L);
		rejected.setApprStatusCode("REJECTED");
		rejected.setCloseDate(Timestamp.valueOf("2026-10-07 10:00:00"));
		when(approvalDao.findCurrent(APP, 1)).thenReturn(Optional.of(rejected));
		ApprStepRow s1 = step(11, 1, "REJECTED");
		s1.setMemo("資料不全");
		when(approvalDao.findSteps(77L)).thenReturn(List.of(s1, step(12, 2, "SKIPPED")));
		when(appDao.findEvents(APP)).thenReturn(List.of(event(1, "SUBMIT", null)));
		when(attachDao.findByApp(APP))
				.thenReturn(List.of(attach(1, "2026-10-07 09:00:00"), attach(2, "2026-10-07 10:30:00")));
		when(approvalWriteDao.findPendingAppr(APP, 2)).thenReturn(appr(88L));
		when(approvalWriteDao.countOpenSteps(88L)).thenReturn(5L);
		when(approvalWriteDao.findOpenStepsWithoutCandidate(88L)).thenReturn(List.of());
	}

	@Test
	void 補件_成功時依序鎖主檔_快照舊版_覆寫主檔_重建子表_建新版實例_寫事件_回新版本() {
		happyResubmitStubs();

		long newVer = service.resubmit(APP, resubmit(3L, "  已補齊資料 "), USER);

		assertThat(newVer).isEqualTo(4L);
		InOrder o = inOrder(appWriteDao, appVerDao, approvalWriteDao);
		o.verify(appWriteDao).transition(APP, 3L, "REJECTED", "IN_REVIEW", "T0001", true);
		o.verify(appVerDao).insertVersion(eq(APP), eq(1), eq("REJECTED"), eq("資料不全"), anyString(), eq("T0001"));
		o.verify(appWriteDao).updateForResubmit(eq(APP), eq("full"), eq("  已補齊資料 "), eq("T0001"), any(AppDraft.class));
		o.verify(appWriteDao).deleteChildren(APP);
		o.verify(appWriteDao).insertChildren(eq(APP), eq("T0001"), any(AppDraft.class));
		o.verify(approvalWriteDao).insertAppr(APP, 2, "full", "T0001");
		o.verify(approvalWriteDao).insertSteps(88L, "full", "T0001");
		o.verify(approvalWriteDao).insertCandidates(88L, "T0001", "T0001");
		o.verify(approvalWriteDao).insertEvent(APP, 2, "RESUBMIT", "T0001", "  已補齊資料 ");
		verify(mailNotifier).stepPending(APP, 88L, "T0001");
		verify(appWriteDao, never()).updateFlowId(anyString(), anyString(), anyString());

		ArgumentCaptor<AppDraft> draft = ArgumentCaptor.forClass(AppDraft.class);
		verify(appWriteDao).updateForResubmit(eq(APP), eq("full"), anyString(), eq("T0001"), draft.capture());
		assertThat(draft.getValue().title()).isEqualTo("改過的標題");
	}

	@Test
	void 補件_快照是退件當時的內容_schema1_附件只含舊版結束前上傳的檔() {
		happyResubmitStubs();

		service.resubmit(APP, resubmit(3L, null), USER);

		ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
		verify(appVerDao).insertVersion(eq(APP), eq(1), eq("REJECTED"), eq("資料不全"), json.capture(), eq("T0001"));
		AppVersionSnapshot snap = jsonMapper.readValue(json.getValue(), AppVersionSnapshot.class);
		assertThat(snap.snapshotSchema()).isEqualTo(AppVersionSnapshot.SCHEMA);
		assertThat(snap.appId()).isEqualTo(APP);
		assertThat(snap.verNo()).isEqualTo(1);
		assertThat(snap.title()).isEqualTo("退件當時的標題");
		assertThat(snap.applicant().userId()).isEqualTo("T0001");
		assertThat(snap.attachments()).extracting(a -> a.attachId()).containsExactly(1L);
		assertThat(json.getValue()).doesNotContain("statusCode").doesNotContain("permissions");
	}

	@Test
	void 補件_補件說明空白存null_事件MEMO也是null() {
		happyResubmitStubs();

		service.resubmit(APP, resubmit(3L, "   "), USER);

		verify(appWriteDao).updateForResubmit(eq(APP), eq("full"), isNull(), eq("T0001"), any(AppDraft.class));
		verify(approvalWriteDao).insertEvent(APP, 2, "RESUBMIT", "T0001", null);
	}

	@Test
	void 補件_表單格式錯或缺版本號400_單號格式錯404_都不碰DB() {
		assertThatThrownBy(() -> service.resubmit(APP, new ResubmitRequest(null, null, form("x")), USER))
				.isInstanceOf(ApiBadRequestException.class).hasMessage(AppDraftService.MSG_NO_VERSION);
		assertThatThrownBy(() -> service.resubmit("bad id", resubmit(3L, null), USER))
				.isInstanceOf(ApiNotFoundException.class);
		assertThatThrownBy(() -> service.resubmit(APP, new ResubmitRequest(3L, null, form("  ")), USER))
				.isInstanceOf(ApiBadRequestException.class).hasMessage("請填寫標題");
		assertThatThrownBy(() -> service.resubmit(APP, new ResubmitRequest(3L, null, null), USER))
				.isInstanceOf(ApiBadRequestException.class).hasMessage("請求格式錯誤");
		assertThatThrownBy(() -> service.resubmit(APP, resubmit(3L, "x".repeat(2001)), USER))
				.isInstanceOf(TextTooLongException.class);
		verifyNoInteractions(appWriteDao, appVerDao, approvalWriteDao, appDao);
	}

	@Test
	void 補件_更新0列時依現況分流_admin也不能代補() {
		when(appWriteDao.transition(anyString(), anyLong(), anyString(), anyString(), anyString(), eq(true)))
				.thenReturn(0);

		when(appWriteDao.findLockState(APP)).thenReturn(null);
		assertThatThrownBy(() -> service.resubmit(APP, resubmit(3L, null), USER))
				.isInstanceOf(ApiNotFoundException.class);

		when(appWriteDao.findLockState(APP)).thenReturn(lock("REJECTED", "T0001"));
		assertThatThrownBy(() -> service.resubmit(APP, resubmit(3L, null), OTHER))
				.isInstanceOf(AccessDeniedException.class).hasMessage(AppFlowService.MSG_RESUBMIT_FORBIDDEN);
		assertThatThrownBy(() -> service.resubmit(APP, resubmit(3L, null), ADMIN))
				.isInstanceOf(AccessDeniedException.class).hasMessage(AppFlowService.MSG_RESUBMIT_FORBIDDEN);

		when(appWriteDao.findLockState(APP)).thenReturn(lock("IN_REVIEW", "T0001"));
		assertThatThrownBy(() -> service.resubmit(APP, resubmit(3L, null), USER))
				.isInstanceOf(ApiConflictException.class).hasMessage(AppFlowService.MSG_RESUBMIT_NOT_REJECTED);

		when(appWriteDao.findLockState(APP)).thenReturn(lock("REJECTED", "T0001"));
		assertThatThrownBy(() -> service.resubmit(APP, resubmit(2L, null), USER))
				.isInstanceOf(ApiConflictException.class).hasMessage(AppFlowService.MSG_STALE);

		verify(appWriteDao).transition(APP, 3L, "REJECTED", "IN_REVIEW", "A0001", true);
		verifyNoInteractions(appVerDao, approvalWriteDao);
		verify(appWriteDao, never()).updateForResubmit(anyString(), anyString(), any(), anyString(), any());
	}

	@Test
	void 補件_必填缺漏400_已快照但不建實例_整筆靠rollback() {
		happyResubmitStubs();
		AppRow after = app();
		after.setApplyTel(null);
		when(appDao.findById(APP)).thenReturn(Optional.of(after));

		assertThatThrownBy(() -> service.resubmit(APP, resubmit(3L, null), USER))
				.isInstanceOf(ApiBadRequestException.class).hasMessage("送審前請先補齊：聯絡電話");

		verify(appWriteDao).updateForResubmit(eq(APP), eq("full"), isNull(), eq("T0001"), any(AppDraft.class));
		verifyNoInteractions(approvalWriteDao);
	}

	@Test
	void 版次結束方式_依事件推算三分支() {
		ApprStepRow rejectedStep = step(11, 1, "REJECTED");
		rejectedStep.setMemo("簽核退件意見");
		List<ApprStepRow> steps = List.of(rejectedStep, step(12, 2, "SKIPPED"));

		AppFlowService.VersionClose c1 = AppFlowService.closeOf(List.of(event(1, "SUBMIT", null)), steps);
		assertThat(c1.code()).isEqualTo(AppFlowService.CLOSE_REJECTED);
		assertThat(c1.reason()).isEqualTo("簽核退件意見");

		AppFlowService.VersionClose c2 = AppFlowService.closeOf(
				List.of(event(1, "SUBMIT", null), event(1, "GOV_RETURN", "請補風險評估")), steps);
		assertThat(c2.code()).isEqualTo(AppFlowService.CLOSE_GOV_RETURNED);
		assertThat(c2.reason()).isEqualTo("請補風險評估");

		AppFlowService.VersionClose c3 = AppFlowService.closeOf(List.of(event(1, "SUBMIT", null),
				event(1, "EXEC_REJECT", "第一次"), event(1, "EXEC_REJECT", "第二次")), List.of());
		assertThat(c3.code()).isEqualTo(AppFlowService.CLOSE_EXEC_REJECTED);
		assertThat(c3.reason()).isEqualTo("第二次");

		assertThat(AppFlowService.closeOf(List.of(), List.of()).reason()).isNull();
	}
}
