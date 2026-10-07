package com.mpx.infra_manager_java.service.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：AppExecutionService 單元測試（S10 R1）。DAO 全 mock。鎖定：
//           鎖外：單號格式 404、缺 rowVerNo 400、單不存在 404、非 idc_admin 非申請人 403——都不取鎖；
//           執行人工號未啟用 400 不取鎖；取鎖 0 列分流 404／409；鎖內狀態不是 APPROVED／IN_EXECUTION 409、
//           鎖內申請人已變更 403；第一次儲存依 CHECK_LIST 選項順序展開、已展開不重展；未知序號 400；
//           完成沒填時間補現在時間、未完成傳 null；暫存不寫結案人、狀態 APPROVED → IN_EXECUTION；
//           已是 IN_EXECUTION 的暫存不改狀態；送審寫結案人並改 PENDING_REVIEW；回傳 rowVerNo + 1
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.security.access.AccessDeniedException;

import com.mpx.infra_manager_java.dao.auth.UserDao;
import com.mpx.infra_manager_java.dao.changerequest.AppDao;
import com.mpx.infra_manager_java.dao.changerequest.AppWriteDao;
import com.mpx.infra_manager_java.dao.changerequest.ExecWriteDao;
import com.mpx.infra_manager_java.dao.changerequest.FormOptionDao;
import com.mpx.infra_manager_java.model.auth.AuthUser;
import com.mpx.infra_manager_java.model.changerequest.AppLockRow;
import com.mpx.infra_manager_java.model.changerequest.AppRow;
import com.mpx.infra_manager_java.model.changerequest.ExecutionDraft;
import com.mpx.infra_manager_java.model.changerequest.ExecutionRequest;
import com.mpx.infra_manager_java.model.changerequest.FormOptionRow;
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
	private UserDao userDao;
	private AppExecutionService service;

	@BeforeEach
	void setUp() {
		appDao = mock(AppDao.class);
		appWriteDao = mock(AppWriteDao.class);
		execWriteDao = mock(ExecWriteDao.class);
		formOptionDao = mock(FormOptionDao.class);
		userDao = mock(UserDao.class);
		service = new AppExecutionService(appDao, appWriteDao, execWriteDao, formOptionDao, userDao);
		List<FormOptionRow> options = new ArrayList<>();
		options.add(option(31, "CHECK_LIST", "check_in"));
		options.add(option(32, "CHECK_LIST", "pre_state"));
		options.add(option(33, "CHECK_LIST", "backup"));
		options.add(option(41, "EXEC_RESULT", "DONE"));
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

	/** 取鎖成功、鎖內主檔現況 status、該版次已展開的序號 */
	private void locked(String status, List<Integer> seqNos) {
		when(appWriteDao.lockForUpdate(eq(APP), eq(3L), anyString())).thenReturn(1);
		when(appDao.findById(APP)).thenReturn(Optional.of(app(status, APPLICANT.userId())));
		when(execWriteDao.findCheckSeqNos(APP, 2)).thenReturn(seqNos);
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
		when(userDao.isActive("X9999")).thenReturn(false);
		assertThatThrownBy(() -> service.save(APP,
				draft(List.of(new ExecutionRequest.CheckItem(1, true, null, "X9999", null))), APPLICANT))
				.isInstanceOf(ApiBadRequestException.class).hasMessage(AppExecutionService.MSG_BAD_EXECUTOR);
		when(appWriteDao.findLockState("IM20261007-404")).thenReturn(null);
		assertThatThrownBy(() -> service.save("IM20261007-404", draft(List.of()), IDC))
				.isInstanceOf(ApiNotFoundException.class);
		verify(appWriteDao, never()).lockForUpdate(anyString(), anyLong(), anyString());
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
	}

	@Test
	void 第一次暫存展開檢核表_補現在時間_改IN_EXECUTION_不寫結案人() {
		locked("APPROVED", List.of());
		when(userDao.isActive("E0001")).thenReturn(true);
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
	void 執行中再暫存不改狀態() {
		locked("IN_EXECUTION", List.of(1, 2, 3));
		AppExecutionService.SaveResult r = service.save(APP, draft(List.of()), IDC);
		assertThat(r.statusCode()).isEqualTo("IN_EXECUTION");
		verify(execWriteDao, never()).insertCheckItem(anyString(), anyInt(), anyInt(), anyLong(), anyString());
		verify(execWriteDao).upsertExec(eq(APP), eq(2), any(), isNull(), eq(IDC.userId()));
		verify(appWriteDao, never()).updateStatus(anyString(), anyString(), anyString());
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
		verify(appWriteDao).updateStatus(APP, "PENDING_REVIEW", IDC.userId());
	}

	@Test
	void 結果代碼只認EXEC_RESULT群組() {
		ExecutionRequest wrongGroup = new ExecutionRequest(3L, List.of(), "2026-10-07 09:00", "2026-10-07 10:00",
				"P1", false, null, false, null, null);
		assertThatThrownBy(() -> service.save(APP, wrongGroup, APPLICANT)).isInstanceOf(ApiBadRequestException.class)
				.hasMessage(ExecutionValidator.MSG_BAD_RESULT);
	}
}
