package com.mpx.infra_manager_java.service.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：AppDeleteService 單元測試（S9 R2）。DAO 全 mock、權限用真的 AppPermissionService（純運算）。
//           鎖定：鎖外驗證（單號格式 404、缺 rowVerNo 400、confirmId 不符 400、原因空白 400、超過 500 字 400）都不碰 DB；
//           取鎖 0 列分流 404／409；鎖內權限 null → 403 且不寫入；申請人刪草稿走 APPLICANT_PRE_REVIEW、沒有簽核實例不關簽核；
//           admin 刪審核中的單走 ADMIN 並把實例與未結關卡改 CANCELLED；申請人刪已退件 403；事件 DELETE 帶 trim 後的原因
// ============================================================

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.access.AccessDeniedException;

import com.mpx.infra_manager_java.dao.changerequest.AppDao;
import com.mpx.infra_manager_java.dao.changerequest.AppWriteDao;
import com.mpx.infra_manager_java.dao.changerequest.ApprovalDao;
import com.mpx.infra_manager_java.dao.changerequest.ApprovalWriteDao;
import com.mpx.infra_manager_java.model.auth.AuthUser;
import com.mpx.infra_manager_java.model.changerequest.AppLockRow;
import com.mpx.infra_manager_java.model.changerequest.AppRow;
import com.mpx.infra_manager_java.model.changerequest.ApprRow;
import com.mpx.infra_manager_java.model.changerequest.ApprStepRow;
import com.mpx.infra_manager_java.model.changerequest.DeleteRequest;
import com.mpx.infra_manager_java.util.TextTooLongException;
import com.mpx.infra_manager_java.web.ApiBadRequestException;
import com.mpx.infra_manager_java.web.ApiConflictException;
import com.mpx.infra_manager_java.web.ApiNotFoundException;

class AppDeleteServiceTest {

	private static final String APP = "IM20261007-001";
	private static final AuthUser APPLICANT = new AuthUser("E0001", "wayne", "申請人", List.of("infra"), false);
	private static final AuthUser ADMIN = new AuthUser("A0001", "admin", "管理員", List.of("admin"), false);

	private AppDao appDao;
	private AppWriteDao appWriteDao;
	private ApprovalDao approvalDao;
	private ApprovalWriteDao approvalWriteDao;
	private AppDeleteService service;

	@BeforeEach
	void setUp() {
		appDao = mock(AppDao.class);
		appWriteDao = mock(AppWriteDao.class);
		approvalDao = mock(ApprovalDao.class);
		approvalWriteDao = mock(ApprovalWriteDao.class);
		service = new AppDeleteService(appDao, appWriteDao, approvalDao, approvalWriteDao, new AppPermissionService());
	}

	private static DeleteRequest req(Long rowVerNo, String confirmId, String reason) {
		return new DeleteRequest(rowVerNo, confirmId, reason);
	}

	private static AppRow app(String status, String applicant) {
		AppRow a = new AppRow();
		a.setAppId(APP);
		a.setAppStatusCode(status);
		a.setApplyUserId(applicant);
		a.setCurrVerNo(1);
		return a;
	}

	private static ApprRow appr(long id, String status) {
		ApprRow r = new ApprRow();
		r.setApprId(id);
		r.setApprStatusCode(status);
		return r;
	}

	private static ApprStepRow step(long id, int seq, String status) {
		ApprStepRow s = new ApprStepRow();
		s.setApprStepId(id);
		s.setSeqNo(seq);
		s.setStepStatusCode(status);
		return s;
	}

	/** 取鎖成功、主檔現況 status、目前實例 appr（可 null）與關卡 steps */
	private void locked(String status, ApprRow appr, List<ApprStepRow> steps) {
		when(appWriteDao.lockForUpdate(APP, 3L, APPLICANT.userId())).thenReturn(1);
		when(appWriteDao.lockForUpdate(APP, 3L, ADMIN.userId())).thenReturn(1);
		when(appDao.findById(APP)).thenReturn(Optional.of(app(status, APPLICANT.userId())));
		when(approvalDao.findCurrent(APP, 1)).thenReturn(Optional.ofNullable(appr));
		if (appr != null) {
			when(approvalDao.findSteps(appr.getApprId())).thenReturn(steps);
			when(approvalDao.findCandidates(appr.getApprId())).thenReturn(List.of());
		}
		when(appDao.findExec(APP, 1)).thenReturn(Optional.empty());
	}

	@Test
	void 鎖外驗證失敗都不碰DB() {
		assertThatThrownBy(() -> service.delete("bad id", req(3L, "bad id", "x"), APPLICANT))
				.isInstanceOf(ApiNotFoundException.class);
		assertThatThrownBy(() -> service.delete(APP, null, APPLICANT)).isInstanceOf(ApiBadRequestException.class)
				.hasMessage(AppDraftService.MSG_NO_VERSION);
		assertThatThrownBy(() -> service.delete(APP, req(null, APP, "x"), APPLICANT))
				.isInstanceOf(ApiBadRequestException.class).hasMessage(AppDraftService.MSG_NO_VERSION);
		for (String wrong : new String[] { null, "", "IM20261007-002", APP.toLowerCase() }) {
			assertThatThrownBy(() -> service.delete(APP, req(3L, wrong, "x"), APPLICANT)).as("confirmId=%s", wrong)
					.isInstanceOf(ApiBadRequestException.class).hasMessage(AppDeleteService.MSG_CONFIRM_MISMATCH);
		}
		for (String blank : new String[] { null, "", "   ", "\n\t" }) {
			assertThatThrownBy(() -> service.delete(APP, req(3L, APP, blank), APPLICANT))
					.isInstanceOf(ApiBadRequestException.class).hasMessage(AppDeleteService.MSG_REASON_REQUIRED);
		}
		assertThatThrownBy(() -> service.delete(APP, req(3L, APP, "字".repeat(501)), APPLICANT))
				.isInstanceOf(TextTooLongException.class);
		verifyNoInteractions(appDao, appWriteDao, approvalDao, approvalWriteDao);
	}

	@Test
	void 取鎖0列_不存在404_版本不符409() {
		when(appWriteDao.lockForUpdate(anyString(), anyLong(), anyString())).thenReturn(0);
		when(appWriteDao.findLockState(APP)).thenReturn(null);
		assertThatThrownBy(() -> service.delete(APP, req(3L, APP, "重複"), APPLICANT))
				.isInstanceOf(ApiNotFoundException.class).hasMessage(AppQueryService.MSG_APP_NOT_FOUND);

		when(appWriteDao.findLockState(APP)).thenReturn(new AppLockRow());
		assertThatThrownBy(() -> service.delete(APP, req(3L, APP, "重複"), APPLICANT))
				.isInstanceOf(ApiConflictException.class).hasMessage(AppFlowService.MSG_STALE);
		verify(appWriteDao, never()).deleteApp(anyString(), anyString(), anyString(), anyString());
	}

	@Test
	void 申請人刪草稿_APPLICANT模式_無實例不關簽核_原因trim後寫入() {
		locked("DRAFT", null, List.of());
		service.delete(APP, req(3L, " " + APP + " ", "  建錯單  "), APPLICANT);

		InOrder order = inOrder(appWriteDao, approvalWriteDao);
		order.verify(appWriteDao).lockForUpdate(APP, 3L, APPLICANT.userId());
		order.verify(appWriteDao).deleteApp(APP, APPLICANT.userId(), "建錯單", AppPermissionService.DELETE_MODE_APPLICANT);
		order.verify(approvalWriteDao).insertEvent(APP, 1, AppDeleteService.EVENT_DELETE, APPLICANT.userId(), "建錯單");
		verify(approvalWriteDao, never()).closeAppr(anyLong(), anyString(), anyString());
		verify(approvalWriteDao, never()).closeOpenSteps(anyLong(), anyString(), anyString());
	}

	@Test
	void admin刪審核中_ADMIN模式_實例與未結關卡改CANCELLED() {
		ApprRow pending = appr(77L, "PENDING");
		locked("IN_REVIEW", pending, List.of(step(1L, 1, "APPROVED"), step(2L, 2, "PENDING")));
		when(approvalWriteDao.findPendingAppr(APP, 1)).thenReturn(pending);

		service.delete(APP, req(3L, APP, "廠商取消"), ADMIN);

		InOrder order = inOrder(appWriteDao, approvalWriteDao);
		order.verify(appWriteDao).deleteApp(APP, ADMIN.userId(), "廠商取消", AppPermissionService.DELETE_MODE_ADMIN);
		order.verify(approvalWriteDao).closeOpenSteps(77L, "CANCELLED", ADMIN.userId());
		order.verify(approvalWriteDao).closeAppr(77L, "CANCELLED", ADMIN.userId());
		order.verify(approvalWriteDao).insertEvent(APP, 1, AppDeleteService.EVENT_DELETE, ADMIN.userId(), "廠商取消");
	}

	@Test
	void admin刪已核准200() {
		locked("APPROVED", appr(5L, "APPROVED"), List.of(step(1L, 1, "APPROVED")));
		service.delete(APP, req(3L, APP, "作廢"), ADMIN);
		verify(appWriteDao).deleteApp(APP, ADMIN.userId(), "作廢", AppPermissionService.DELETE_MODE_ADMIN);
		verify(approvalWriteDao, never()).closeAppr(anyLong(), anyString(), anyString());
	}

	@Test
	void 申請人刪已有人簽的審核中或已退件或已核准_403不寫入() {
		ApprRow appr = appr(9L, "PENDING");
		locked("IN_REVIEW", appr, List.of(step(1L, 1, "APPROVED"), step(2L, 2, "PENDING")));
		assertThatThrownBy(() -> service.delete(APP, req(3L, APP, "不要了"), APPLICANT))
				.isInstanceOf(AccessDeniedException.class).hasMessage(AppDeleteService.MSG_DELETE_FORBIDDEN);

		locked("REJECTED", appr(9L, "REJECTED"), List.of(step(1L, 1, "REJECTED")));
		assertThatThrownBy(() -> service.delete(APP, req(3L, APP, "不要了"), APPLICANT))
				.isInstanceOf(AccessDeniedException.class);

		locked("APPROVED", appr(9L, "APPROVED"), List.of(step(1L, 1, "APPROVED")));
		assertThatThrownBy(() -> service.delete(APP, req(3L, APP, "不要了"), APPLICANT))
				.isInstanceOf(AccessDeniedException.class);

		verify(appWriteDao, never()).deleteApp(anyString(), anyString(), anyString(), anyString());
		verify(approvalWriteDao, never()).insertEvent(anyString(), eq(1), anyString(), anyString(), anyString());
	}

	@Test
	void 非申請人非admin刪草稿403() {
		locked("DRAFT", null, List.of());
		AuthUser other = new AuthUser("E0002", "other", "別人", List.of("infra"), false);
		when(appWriteDao.lockForUpdate(APP, 3L, other.userId())).thenReturn(1);
		assertThatThrownBy(() -> service.delete(APP, req(3L, APP, "x"), other)).isInstanceOf(AccessDeniedException.class);
		verify(appWriteDao, never()).deleteApp(anyString(), anyString(), anyString(), anyString());
	}

	@Test
	void 申請人刪尚無人簽的審核中_APPLICANT模式並取消簽核() {
		ApprRow pending = appr(12L, "PENDING");
		locked("IN_REVIEW", pending, List.of(step(1L, 1, "PENDING"), step(2L, 2, "WAITING")));
		when(approvalWriteDao.findPendingAppr(APP, 1)).thenReturn(pending);
		service.delete(APP, req(3L, APP, "送錯"), APPLICANT);
		verify(appWriteDao).deleteApp(APP, APPLICANT.userId(), "送錯", AppPermissionService.DELETE_MODE_APPLICANT);
		verify(approvalWriteDao).closeOpenSteps(12L, "CANCELLED", APPLICANT.userId());
		verify(approvalWriteDao).closeAppr(12L, "CANCELLED", APPLICANT.userId());
	}
}
