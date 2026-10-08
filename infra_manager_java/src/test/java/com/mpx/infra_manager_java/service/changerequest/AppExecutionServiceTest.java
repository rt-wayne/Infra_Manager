package com.mpx.infra_manager_java.service.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：AppExecutionService 單元測試（S10 R1）。DAO 全 mock。鎖定：
//           鎖外：單號格式 404、缺 rowVerNo 400、單不存在 404、非 idc_admin 非申請人 403——都不取鎖；
//           執行人工號未啟用 400 不取鎖；取鎖 0 列分流 404／409；鎖內狀態不是 APPROVED／IN_EXECUTION 409、
//           鎖內申請人已變更 403；第一次儲存依 CHECK_LIST 選項順序展開、已展開不重展；未知序號 400；
//           完成沒填時間補現在時間、未完成傳 null；暫存不寫結案人、狀態 APPROVED → IN_EXECUTION；
//           已是 IN_EXECUTION 的暫存不改狀態；送審寫結案人並改 PENDING_REVIEW；回傳 rowVerNo + 1。
//           2026-10-07 S10 R2：加執行端退回（鎖外 403／意見必填與超長不取鎖、0 列分流、鎖內狀態不對 409、
//           成功改 REJECTED 寫 EXEC_REJECT 且不動執行資料）與治理審查（非 governance 403、決定值／意見檢核不取鎖、
//           0 列分流 404／409 狀態／409 版本、PASS → EXECUTED 空白意見存 null、RETURN → REJECTED、申請人兼治理不擋）
//           2026-10-07 S10 結案 review ①B：拿掉「執行人工號未啟用 400」（不再查 UserDao）；改鎖「執行人只收本人或該列原值，
//           別人工號 400 且一列都不更新、不寫執行結果」
//           2026-10-08 S8 R3：建構子多 MailNotifier（mock）；鎖定信件事件 7～10——送治理審查在改狀態後寄待審核（帶執行結果
//           名稱）、暫存不寄；執行端退回寄退件「執行階段」、治理退回寄退件「資訊治理審核」（帶版次、退件人姓名、去空白後意見）；
//           治理通過寄執行結果已通過；所有失敗路徑一封都不寄
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.security.access.AccessDeniedException;

import com.mpx.infra_manager_java.dao.changerequest.AppDao;
import com.mpx.infra_manager_java.dao.changerequest.AppWriteDao;
import com.mpx.infra_manager_java.dao.changerequest.ApprovalWriteDao;
import com.mpx.infra_manager_java.dao.changerequest.ExecWriteDao;
import com.mpx.infra_manager_java.dao.changerequest.FormOptionDao;
import com.mpx.infra_manager_java.model.auth.AuthUser;
import com.mpx.infra_manager_java.model.changerequest.AppLockRow;
import com.mpx.infra_manager_java.model.changerequest.AppRow;
import com.mpx.infra_manager_java.model.changerequest.ExecRejectRequest;
import com.mpx.infra_manager_java.model.changerequest.ExecutionDraft;
import com.mpx.infra_manager_java.model.changerequest.ExecutionRequest;
import com.mpx.infra_manager_java.model.changerequest.FormOptionRow;
import com.mpx.infra_manager_java.model.changerequest.GovernanceReviewRequest;
import com.mpx.infra_manager_java.service.mail.MailNotifier;
import com.mpx.infra_manager_java.util.TextTooLongException;
import com.mpx.infra_manager_java.web.ApiBadRequestException;
import com.mpx.infra_manager_java.web.ApiConflictException;
import com.mpx.infra_manager_java.web.ApiNotFoundException;

class AppExecutionServiceTest {

	private static final String APP = "IM20261007-001";
	private static final AuthUser APPLICANT = new AuthUser("E0001", "wayne", "申請人", List.of("infra"), false);
	private static final AuthUser IDC = new AuthUser("I0001", "idc", "機房", List.of("idc_admin"), false);
	private static final AuthUser OTHER = new AuthUser("E0002", "other", "路人", List.of("infra", "admin"), false);

	private AppDao appDao;
	private AppWriteDao appWriteDao;
	private ExecWriteDao execWriteDao;
	private FormOptionDao formOptionDao;
	private ApprovalWriteDao approvalWriteDao;
	private MailNotifier mailNotifier;
	private AppExecutionService service;

	@BeforeEach
	void setUp() {
		appDao = mock(AppDao.class);
		appWriteDao = mock(AppWriteDao.class);
		execWriteDao = mock(ExecWriteDao.class);
		formOptionDao = mock(FormOptionDao.class);
		approvalWriteDao = mock(ApprovalWriteDao.class);
		mailNotifier = mock(MailNotifier.class);
		service = new AppExecutionService(appDao, appWriteDao, execWriteDao, formOptionDao, approvalWriteDao,
				mailNotifier);
		List<FormOptionRow> options = new ArrayList<>();
		options.add(option(31, "CHECK_LIST", "check_in"));
		options.add(option(32, "CHECK_LIST", "pre_state"));
		options.add(option(33, "CHECK_LIST", "backup"));
		FormOptionRow done = option(41, "EXEC_RESULT", "DONE");
		done.setOptionName("全部完成");
		options.add(done);
		options.add(option(42, "EXEC_RESULT", "PARTIAL"));
		options.add(option(51, "PRIO", "P1"));
		when(formOptionDao.findActive()).thenReturn(options);
		when(appWriteDao.findLockState(APP)).thenReturn(lockState(APPLICANT.userId()));
	}

	private static FormOptionRow option(long id, String group, String code) {
		FormOptionRow o = new FormOptionRow();
		o.setFormOptionId(id);
		o.setGroupCode(group);
		o.setOptionCode(code);
		return o;
	}

	private static AppLockRow lockState(String applicant) {
		AppLockRow r = new AppLockRow();
		r.setApplyUserId(applicant);
		r.setAppStatusCode("APPROVED");
		return r;
	}

	private static AppRow app(String status, String applicant) {
		AppRow a = new AppRow();
		a.setAppId(APP);
		a.setAppStatusCode(status);
		a.setApplyUserId(applicant);
		a.setCurrVerNo(2);
		return a;
	}

	/** 取鎖成功、鎖內主檔現況 status、該版次已展開的序號（都還沒存執行人） */
	private void locked(String status, List<Integer> seqNos) {
		Map<Integer, String> stored = new LinkedHashMap<>();
		seqNos.forEach(s -> stored.put(s, null));
		locked(status, stored);
	}

	/** 取鎖成功、鎖內主檔現況 status、該版次已展開的檢核項（序號 → 已存執行人工號） */
	private void locked(String status, Map<Integer, String> stored) {
		when(appWriteDao.lockForUpdate(eq(APP), eq(3L), anyString())).thenReturn(1);
		when(appDao.findById(APP)).thenReturn(Optional.of(app(status, APPLICANT.userId())));
		when(execWriteDao.findCheckExecutors(APP, 2)).thenReturn(stored);
	}

	private static ExecutionRequest draft(List<ExecutionRequest.CheckItem> items) {
		return new ExecutionRequest(3L, items, "2026-10-07 09:00", null, null, false, null, false, null, null);
	}

	private static ExecutionRequest submit() {
		return new ExecutionRequest(3L, List.of(), "2026-10-07 09:00", "2026-10-07 10:00", "DONE", false, null, false,
				null, "完成");
	}

	@Test
	void 鎖外失敗都不取鎖() {
		assertThatThrownBy(() -> service.save("bad id", draft(List.of()), APPLICANT))
				.isInstanceOf(ApiNotFoundException.class);
		assertThatThrownBy(() -> service.save(APP, null, APPLICANT)).isInstanceOf(ApiBadRequestException.class)
				.hasMessage(AppDraftService.MSG_NO_VERSION);
		assertThatThrownBy(() -> service.save(APP,
				new ExecutionRequest(null, null, null, null, null, null, null, null, null, null), APPLICANT))
				.isInstanceOf(ApiBadRequestException.class).hasMessage(AppDraftService.MSG_NO_VERSION);
		assertThatThrownBy(() -> service.save(APP, draft(List.of()), OTHER)).as("admin 角色也不行")
				.isInstanceOf(AccessDeniedException.class).hasMessage(AppExecutionService.MSG_EXEC_FORBIDDEN);
		assertThatThrownBy(() -> service.save(APP, submitMissingEnd(), APPLICANT))
				.isInstanceOf(ApiBadRequestException.class)
				.hasMessage(ExecutionValidator.MSG_SUBMIT_PREFIX + "實際結束時間");
		when(appWriteDao.findLockState("IM20261007-404")).thenReturn(null);
		assertThatThrownBy(() -> service.save("IM20261007-404", draft(List.of()), IDC))
				.isInstanceOf(ApiNotFoundException.class);
		verify(appWriteDao, never()).lockForUpdate(anyString(), anyLong(), anyString());
		verifyNoInteractions(mailNotifier);
	}

	private static ExecutionRequest submitMissingEnd() {
		return new ExecutionRequest(3L, List.of(), "2026-10-07 09:00", null, "DONE", false, null, false, null, null);
	}

	@Test
	void 取鎖0列分流404與409() {
		when(appWriteDao.lockForUpdate(APP, 3L, IDC.userId())).thenReturn(0);
		when(appWriteDao.findLockState(APP)).thenReturn(lockState(APPLICANT.userId()), (AppLockRow) null);
		assertThatThrownBy(() -> service.save(APP, draft(List.of()), IDC)).isInstanceOf(ApiNotFoundException.class);

		when(appWriteDao.findLockState(APP)).thenReturn(lockState(APPLICANT.userId()));
		assertThatThrownBy(() -> service.save(APP, draft(List.of()), IDC)).isInstanceOf(ApiConflictException.class)
				.hasMessage(AppFlowService.MSG_STALE);
		verify(execWriteDao, never()).upsertExec(anyString(), anyInt(), any(), any(), anyString());
	}

	@Test
	void 鎖內狀態不對409_申請人已變更403_都不寫入() {
		for (String status : new String[] { "PENDING_REVIEW", "EXECUTED", "REJECTED", "DRAFT", "IN_REVIEW" }) {
			locked(status, List.of(1, 2, 3));
			assertThatThrownBy(() -> service.save(APP, draft(List.of()), APPLICANT)).as(status)
					.isInstanceOf(ApiConflictException.class).hasMessage(AppExecutionService.MSG_EXEC_NOT_EXECUTABLE);
		}
		locked("APPROVED", List.of(1, 2, 3));
		when(appDao.findById(APP)).thenReturn(Optional.of(app("APPROVED", "E9999")));
		assertThatThrownBy(() -> service.save(APP, draft(List.of()), APPLICANT))
				.isInstanceOf(AccessDeniedException.class);
		verify(execWriteDao, never()).upsertExec(anyString(), anyInt(), any(), any(), anyString());
		verify(appWriteDao, never()).updateStatus(anyString(), anyString(), anyString());
		verifyNoInteractions(mailNotifier);
	}

	@Test
	void 第一次暫存展開檢核表_補現在時間_改IN_EXECUTION_不寫結案人() {
		locked("APPROVED", List.of());
		Timestamp before = new Timestamp(System.currentTimeMillis() - 60_000);
		AppExecutionService.SaveResult r = service.save(APP,
				draft(List.of(new ExecutionRequest.CheckItem(1, true, null, "E0001", null),
						new ExecutionRequest.CheckItem(3, true, "2026-10-07 09:30", null, "廠商"),
						new ExecutionRequest.CheckItem(2, false, "2026-10-07 09:40", null, null))),
				APPLICANT);
		assertThat(r.rowVerNo()).isEqualTo(4L);
		assertThat(r.statusCode()).isEqualTo("IN_EXECUTION");

		InOrder order = inOrder(appWriteDao, execWriteDao);
		order.verify(appWriteDao).lockForUpdate(APP, 3L, APPLICANT.userId());
		order.verify(execWriteDao).insertCheckItem(APP, 2, 1, 31L, APPLICANT.userId());
		order.verify(execWriteDao).insertCheckItem(APP, 2, 2, 32L, APPLICANT.userId());
		order.verify(execWriteDao).insertCheckItem(APP, 2, 3, 33L, APPLICANT.userId());

		ArgumentCaptor<ExecutionDraft.CheckItem> items = ArgumentCaptor.forClass(ExecutionDraft.CheckItem.class);
		ArgumentCaptor<Timestamp> doneAts = ArgumentCaptor.forClass(Timestamp.class);
		verify(execWriteDao, times(3)).updateCheckItem(eq(APP), eq(2), items.capture(), doneAts.capture(),
				eq(APPLICANT.userId()));
		assertThat(items.getAllValues()).extracting(ExecutionDraft.CheckItem::seqNo).containsExactly(1, 3, 2);
		assertThat(doneAts.getAllValues().get(0)).as("完成沒填時間補現在").isAfter(before);
		assertThat(doneAts.getAllValues().get(1)).isEqualTo(Timestamp.valueOf("2026-10-07 09:30:00"));
		assertThat(doneAts.getAllValues().get(2)).as("未完成傳 null").isNull();

		verify(execWriteDao).upsertExec(eq(APP), eq(2), any(), isNull(), eq(APPLICANT.userId()));
		verify(appWriteDao).updateStatus(APP, "IN_EXECUTION", APPLICANT.userId());
		verifyNoInteractions(mailNotifier);
	}

	@Test
	void 已展開不重展_未知序號400() {
		locked("IN_EXECUTION", List.of(1, 2, 3));
		assertThatThrownBy(() -> service.save(APP, draft(List.of(new ExecutionRequest.CheckItem(4, true, null, null,
				null))), APPLICANT)).isInstanceOf(ApiBadRequestException.class)
				.hasMessage(ExecutionValidator.MSG_BAD_SEQ);
		verify(execWriteDao, never()).insertCheckItem(anyString(), anyInt(), anyInt(), anyLong(), anyString());
	}

	@Test
	void 執行人只收本人或該列原值_別人工號400且不寫入() {
		Map<Integer, String> stored = new LinkedHashMap<>();
		stored.put(1, "E0002");
		stored.put(2, null);
		stored.put(3, null);
		locked("IN_EXECUTION", stored);
		assertThatThrownBy(() -> service.save(APP,
				draft(List.of(new ExecutionRequest.CheckItem(1, true, null, "E0002", null),
						new ExecutionRequest.CheckItem(2, true, null, "E0002", null))),
				IDC)).as("第 2 列沒存過 E0002，不能填別人").isInstanceOf(ApiBadRequestException.class)
				.hasMessage(AppExecutionService.MSG_BAD_EXECUTOR);
		verify(execWriteDao, never()).updateCheckItem(anyString(), anyInt(), any(), any(), anyString());
		verify(execWriteDao, never()).upsertExec(anyString(), anyInt(), any(), any(), anyString());

		AppExecutionService.SaveResult r = service.save(APP,
				draft(List.of(new ExecutionRequest.CheckItem(1, true, null, "E0002", null),
						new ExecutionRequest.CheckItem(2, true, null, IDC.userId(), null))),
				IDC);
		assertThat(r.statusCode()).as("保留原值＋填自己可以存").isEqualTo("IN_EXECUTION");
		verify(execWriteDao, times(2)).updateCheckItem(eq(APP), eq(2), any(), any(), eq(IDC.userId()));
	}

	@Test
	void 執行中再暫存不改狀態() {
		locked("IN_EXECUTION", List.of(1, 2, 3));
		AppExecutionService.SaveResult r = service.save(APP, draft(List.of()), IDC);
		assertThat(r.statusCode()).isEqualTo("IN_EXECUTION");
		verify(execWriteDao, never()).insertCheckItem(anyString(), anyInt(), anyInt(), anyLong(), anyString());
		verify(execWriteDao).upsertExec(eq(APP), eq(2), any(), isNull(), eq(IDC.userId()));
		verify(appWriteDao, never()).updateStatus(anyString(), anyString(), anyString());
		verifyNoInteractions(mailNotifier);
	}

	@Test
	void 送審寫結案人並改PENDING_REVIEW_機房管理員非申請人也可以() {
		locked("IN_EXECUTION", List.of(1, 2, 3));
		AppExecutionService.SaveResult r = service.save(APP, submit(), IDC);
		assertThat(r.rowVerNo()).isEqualTo(4L);
		assertThat(r.statusCode()).isEqualTo("PENDING_REVIEW");
		ArgumentCaptor<ExecutionDraft> d = ArgumentCaptor.forClass(ExecutionDraft.class);
		verify(execWriteDao).upsertExec(eq(APP), eq(2), d.capture(), eq(IDC.userId()), eq(IDC.userId()));
		assertThat(d.getValue().resultCode()).isEqualTo("DONE");
		assertThat(d.getValue().memo()).isEqualTo("完成");
		InOrder order = inOrder(appWriteDao, mailNotifier);
		order.verify(appWriteDao).updateStatus(APP, "PENDING_REVIEW", IDC.userId());
		order.verify(mailNotifier).govReviewRequest(APP, "全部完成", IDC.userId());
	}

	@Test
	void 送審結果代碼沒有選項名稱時信件用代碼本身() {
		locked("IN_EXECUTION", List.of(1, 2, 3));
		ExecutionRequest partial = new ExecutionRequest(3L, List.of(), "2026-10-07 09:00", "2026-10-07 10:00",
				"PARTIAL", false, null, false, null, null);
		service.save(APP, partial, APPLICANT);
		verify(mailNotifier).govReviewRequest(APP, "PARTIAL", APPLICANT.userId());
	}

	@Test
	void 結果代碼只認EXEC_RESULT群組() {
		ExecutionRequest wrongGroup = new ExecutionRequest(3L, List.of(), "2026-10-07 09:00", "2026-10-07 10:00",
				"P1", false, null, false, null, null);
		assertThatThrownBy(() -> service.save(APP, wrongGroup, APPLICANT)).isInstanceOf(ApiBadRequestException.class)
				.hasMessage(ExecutionValidator.MSG_BAD_RESULT);
	}

	// ---- 執行端退回（⑧） ----

	@Test
	void 退回鎖外失敗都不取鎖_意見必填() {
		assertThatThrownBy(() -> service.reject(APP, new ExecRejectRequest(null, "x"), APPLICANT))
				.isInstanceOf(ApiBadRequestException.class).hasMessage(AppDraftService.MSG_NO_VERSION);
		assertThatThrownBy(() -> service.reject(APP, new ExecRejectRequest(3L, "x"), OTHER))
				.isInstanceOf(AccessDeniedException.class).hasMessage(AppExecutionService.MSG_REJECT_FORBIDDEN);
		for (String blank : new String[] { null, "", "  \n " }) {
			assertThatThrownBy(() -> service.reject(APP, new ExecRejectRequest(3L, blank), APPLICANT)).as("[" + blank + "]")
					.isInstanceOf(ApiBadRequestException.class).hasMessage(AppExecutionService.MSG_REJECT_NEEDS_MEMO);
		}
		assertThatThrownBy(() -> service.reject(APP, new ExecRejectRequest(3L, "字".repeat(2001)), APPLICANT))
				.isInstanceOf(TextTooLongException.class);
		verify(appWriteDao, never()).lockForUpdate(anyString(), anyLong(), anyString());
		verifyNoInteractions(mailNotifier);
	}

	@Test
	void 退回鎖內狀態不對409_成功改REJECTED寫事件不清執行資料() {
		for (String status : new String[] { "PENDING_REVIEW", "EXECUTED", "REJECTED", "IN_REVIEW" }) {
			locked(status, List.of());
			assertThatThrownBy(() -> service.reject(APP, new ExecRejectRequest(3L, "設備未到"), IDC)).as(status)
					.isInstanceOf(ApiConflictException.class).hasMessage(AppExecutionService.MSG_REJECT_NOT_EXECUTABLE);
		}
		verify(approvalWriteDao, never()).insertEvent(anyString(), anyInt(), anyString(), anyString(), any());
		verifyNoInteractions(mailNotifier);

		locked("IN_EXECUTION", List.of(1, 2, 3));
		assertThat(service.reject(APP, new ExecRejectRequest(3L, "  設備未到\n"), IDC)).isEqualTo(4L);
		verify(appWriteDao).updateStatus(APP, "REJECTED", IDC.userId());
		verify(execWriteDao, never()).upsertExec(anyString(), anyInt(), any(), any(), anyString());
		InOrder order = inOrder(approvalWriteDao, mailNotifier);
		order.verify(approvalWriteDao).insertEvent(APP, 2, "EXEC_REJECT", IDC.userId(), "設備未到");
		order.verify(mailNotifier).rejectedAtVersion(APP, 2, MailNotifier.STAGE_EXECUTION, "機房", "設備未到",
				IDC.userId());
	}

	@Test
	void 退回取鎖0列分流404與409() {
		when(appWriteDao.lockForUpdate(APP, 3L, APPLICANT.userId())).thenReturn(0);
		when(appWriteDao.findLockState(APP)).thenReturn(lockState(APPLICANT.userId()), (AppLockRow) null);
		assertThatThrownBy(() -> service.reject(APP, new ExecRejectRequest(3L, "x"), APPLICANT))
				.isInstanceOf(ApiNotFoundException.class);
		when(appWriteDao.findLockState(APP)).thenReturn(lockState(APPLICANT.userId()));
		assertThatThrownBy(() -> service.reject(APP, new ExecRejectRequest(3L, "x"), APPLICANT))
				.isInstanceOf(ApiConflictException.class).hasMessage(AppFlowService.MSG_STALE);
		verifyNoInteractions(mailNotifier);
	}

	// ---- 治理審查（⑨） ----

	private static final AuthUser GOV = new AuthUser("G0001", "gov", "治理", List.of("governance"), false);

	@Test
	void 審查非governance403_決定值與意見檢核_都不取鎖() {
		assertThatThrownBy(() -> service.review(APP, new GovernanceReviewRequest(3L, "PASS", null), IDC))
				.isInstanceOf(AccessDeniedException.class).hasMessage(AppExecutionService.MSG_REVIEW_FORBIDDEN);
		assertThatThrownBy(() -> service.review(APP, new GovernanceReviewRequest(3L, "PASS", null), OTHER))
				.as("admin 也不行").isInstanceOf(AccessDeniedException.class);
		for (String bad : new String[] { null, "pass", "APPROVE", "REJECT" }) {
			assertThatThrownBy(() -> service.review(APP, new GovernanceReviewRequest(3L, bad, "x"), GOV)).as(bad)
					.isInstanceOf(ApiBadRequestException.class).hasMessage(AppExecutionService.MSG_BAD_REVIEW_DECISION);
		}
		assertThatThrownBy(() -> service.review(APP, new GovernanceReviewRequest(3L, "RETURN", " "), GOV))
				.isInstanceOf(ApiBadRequestException.class).hasMessage(AppExecutionService.MSG_REJECT_NEEDS_MEMO);
		assertThatThrownBy(() -> service.review(APP, new GovernanceReviewRequest(3L, "PASS", "字".repeat(2001)), GOV))
				.isInstanceOf(TextTooLongException.class);
		assertThatThrownBy(() -> service.review(APP, new GovernanceReviewRequest(null, "PASS", null), GOV))
				.isInstanceOf(ApiBadRequestException.class).hasMessage(AppDraftService.MSG_NO_VERSION);
		verify(appWriteDao, never()).transition(anyString(), anyLong(), anyString(), anyString(), anyString(),
				anyBoolean());
		verifyNoInteractions(mailNotifier);
	}

	@Test
	void 審查0列分流404_狀態不對409_版本過期409() {
		when(appWriteDao.transition(APP, 3L, "PENDING_REVIEW", "EXECUTED", GOV.userId(), false)).thenReturn(0);
		when(appWriteDao.findLockState(APP)).thenReturn(null);
		assertThatThrownBy(() -> service.review(APP, new GovernanceReviewRequest(3L, "PASS", null), GOV))
				.isInstanceOf(ApiNotFoundException.class);

		AppLockRow executed = lockState(APPLICANT.userId());
		executed.setAppStatusCode("EXECUTED");
		when(appWriteDao.findLockState(APP)).thenReturn(executed);
		assertThatThrownBy(() -> service.review(APP, new GovernanceReviewRequest(3L, "PASS", null), GOV))
				.isInstanceOf(ApiConflictException.class).hasMessage(AppExecutionService.MSG_REVIEW_NOT_PENDING);

		AppLockRow pending = lockState(APPLICANT.userId());
		pending.setAppStatusCode("PENDING_REVIEW");
		when(appWriteDao.findLockState(APP)).thenReturn(pending);
		assertThatThrownBy(() -> service.review(APP, new GovernanceReviewRequest(3L, "PASS", null), GOV))
				.isInstanceOf(ApiConflictException.class).hasMessage(AppFlowService.MSG_STALE);
		verify(approvalWriteDao, never()).insertEvent(anyString(), anyInt(), anyString(), anyString(), any());
		verifyNoInteractions(mailNotifier);
	}

	@Test
	void 審查通過EXECUTED意見選填_退回REJECTED意見必填_申請人兼治理也可以() {
		when(appDao.findById(APP)).thenReturn(Optional.of(app("EXECUTED", APPLICANT.userId())));
		when(appWriteDao.transition(APP, 3L, "PENDING_REVIEW", "EXECUTED", GOV.userId(), false)).thenReturn(1);
		AppExecutionService.SaveResult pass = service.review(APP, new GovernanceReviewRequest(3L, "PASS", "  "), GOV);
		assertThat(pass.rowVerNo()).isEqualTo(4L);
		assertThat(pass.statusCode()).isEqualTo("EXECUTED");
		verify(approvalWriteDao).insertEvent(APP, 2, "GOV_PASS", GOV.userId(), null);
		verify(mailNotifier).govPassed(APP, null, GOV.userId());
		verify(mailNotifier, never()).rejectedAtVersion(anyString(), anyInt(), anyString(), anyString(), any(),
				anyString());

		AuthUser applicantGov = new AuthUser(APPLICANT.userId(), "wayne", "申請人", List.of("governance"), false);
		when(appWriteDao.transition(APP, 5L, "PENDING_REVIEW", "REJECTED", APPLICANT.userId(), false)).thenReturn(1);
		AppExecutionService.SaveResult ret = service.review(APP,
				new GovernanceReviewRequest(5L, "RETURN", " 備份紀錄不完整 "), applicantGov);
		assertThat(ret.rowVerNo()).isEqualTo(6L);
		assertThat(ret.statusCode()).isEqualTo("REJECTED");
		verify(approvalWriteDao).insertEvent(APP, 2, "GOV_RETURN", APPLICANT.userId(), "備份紀錄不完整");
		verify(mailNotifier).rejectedAtVersion(APP, 2, MailNotifier.STAGE_GOVERNANCE, "申請人", "備份紀錄不完整",
				APPLICANT.userId());
		verify(mailNotifier).govPassed(anyString(), any(), anyString()); // 退回不加寄通過信：全程仍只有前面那一封
		verify(appWriteDao, never()).updateStatus(anyString(), anyString(), anyString());
	}
}
