package com.mpx.infra_manager_java.service.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：檢視頁動作權限旗標的純單元測試（S4）。鎖定：canDecide 須狀態 IN_REVIEW 且為目前關卡簽核人
//           （候選人優先、無候選人看流程指定人）；canRecall 任一關已決就關閉；canExecute 已有結果就關閉；
//           admin 刪除模式 ADMIN、申請人只在未決且狀態允許時 APPLICANT_PRE_REVIEW；canAiReview／canSubmit 恆 false
//           2026-10-07 S7 R1（Claude Fable 5.1）：canSubmit 改為草稿且（申請人或 admin）才開
//           2026-10-07 S9 結案（Claude Opus 5.5）：補件過（版次大於 1）申請人不能刪、admin 仍可
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.mpx.infra_manager_java.model.auth.AuthUser;
import com.mpx.infra_manager_java.model.changerequest.AppPermissions;
import com.mpx.infra_manager_java.model.changerequest.AppRow;
import com.mpx.infra_manager_java.model.changerequest.ApprRow;
import com.mpx.infra_manager_java.model.changerequest.ApprStepRow;
import com.mpx.infra_manager_java.model.changerequest.CandRow;
import com.mpx.infra_manager_java.model.changerequest.ExecRow;

class AppPermissionServiceTest {

	private final AppPermissionService service = new AppPermissionService();

	private static AppRow app(String status, String applicant) {
		AppRow a = new AppRow();
		a.setAppId("IM20261006-001");
		a.setAppStatusCode(status);
		a.setApplyUserId(applicant);
		return a;
	}

	private static ApprRow appr() {
		ApprRow r = new ApprRow();
		r.setApprId(10L);
		r.setApprStatusCode("PENDING");
		return r;
	}

	private static ApprStepRow step(Long id, int seq, String status, String flowUserId) {
		ApprStepRow s = new ApprStepRow();
		s.setApprStepId(id);
		s.setSeqNo(seq);
		s.setStepStatusCode(status);
		s.setFlowUserId(flowUserId);
		return s;
	}

	private static CandRow cand(long stepId, String userId) {
		CandRow c = new CandRow();
		c.setApprStepId(stepId);
		c.setUserId(userId);
		return c;
	}

	private static AuthUser user(String id, String... roles) {
		return new AuthUser(id, id.toLowerCase(), id, List.of(roles), false);
	}

	@Test
	void 目前關卡候選人可簽核_其他人不行() {
		List<ApprStepRow> steps = List.of(step(1L, 1, "APPROVED", null), step(2L, 2, "PENDING", null),
				step(3L, 3, "WAITING", null));
		List<CandRow> cands = List.of(cand(1L, "U_OLD"), cand(2L, "U_A"), cand(2L, "U_B"));

		assertThat(service.compute(app("IN_REVIEW", "APPLICANT"), appr(), steps, cands, null, user("U_A")).canDecide())
				.isTrue();
		assertThat(service.compute(app("IN_REVIEW", "APPLICANT"), appr(), steps, cands, null, user("U_OLD")).canDecide())
				.isFalse();
		assertThat(service.compute(app("IN_REVIEW", "APPLICANT"), appr(), steps, cands, null, user("U_C")).canDecide())
				.isFalse();
	}

	@Test
	void 無候選人時看流程定義指定簽核人() {
		List<ApprStepRow> steps = List.of(step(1L, 1, "PENDING", "BOSS"));

		assertThat(AppPermissionService.isCurrentApprover(steps, List.of(), "BOSS")).isTrue();
		assertThat(AppPermissionService.isCurrentApprover(steps, List.of(), "OTHER")).isFalse();
	}

	@Test
	void 非簽核中狀態即使是簽核人也不能簽_沒有實例也不能簽() {
		List<ApprStepRow> steps = List.of(step(1L, 1, "PENDING", "BOSS"));

		assertThat(service.compute(app("REJECTED", "APPLICANT"), appr(), steps, List.of(), null, user("BOSS")).canDecide())
				.isFalse();
		assertThat(service.compute(app("IN_REVIEW", "APPLICANT"), null, steps, List.of(), null, user("BOSS")).canDecide())
				.isFalse();
		// 流程定義展開的關卡沒有 apprStepId，不算目前關卡
		assertThat(AppPermissionService.isCurrentApprover(List.of(step(null, 1, "PENDING", "BOSS")), List.of(), "BOSS"))
				.isFalse();
	}

	@Test
	void 申請人撤回與補件() {
		List<ApprStepRow> undecided = List.of(step(1L, 1, "PENDING", null), step(2L, 2, "WAITING", null));
		List<ApprStepRow> decided = List.of(step(1L, 1, "APPROVED", null), step(2L, 2, "PENDING", null));

		AppPermissions p1 = service.compute(app("IN_REVIEW", "ME"), appr(), undecided, List.of(), null, user("ME"));
		assertThat(p1.canRecall()).isTrue();
		assertThat(p1.canResubmit()).isFalse();
		assertThat(p1.canDelete()).isTrue();
		assertThat(p1.deleteMode()).isEqualTo(AppPermissionService.DELETE_MODE_APPLICANT);

		AppPermissions p2 = service.compute(app("IN_REVIEW", "ME"), appr(), decided, List.of(), null, user("ME"));
		assertThat(p2.canRecall()).isFalse();
		assertThat(p2.canDelete()).isFalse();
		assertThat(p2.deleteMode()).isNull();

		AppPermissions p3 = service.compute(app("REJECTED", "ME"), appr(), decided, List.of(), null, user("ME"));
		assertThat(p3.canResubmit()).isTrue();
		assertThat(service.compute(app("REJECTED", "ME"), appr(), decided, List.of(), null, user("OTHER")).canResubmit())
				.isFalse();
	}

	@Test
	void 執行權限_機房管理員或申請人_已有結果就關閉() {
		ExecRow started = new ExecRow();
		ExecRow done = new ExecRow();
		done.setResultCode("DONE");

		assertThat(service.compute(app("APPROVED", "ME"), null, List.of(), List.of(), null, user("X", "idc_admin"))
				.canExecute()).isTrue();
		assertThat(service.compute(app("IN_EXECUTION", "ME"), null, List.of(), List.of(), started, user("ME"))
				.canExecute()).isTrue();
		assertThat(service.compute(app("IN_EXECUTION", "ME"), null, List.of(), List.of(), done, user("ME"))
				.canExecute()).isFalse();
		assertThat(service.compute(app("APPROVED", "ME"), null, List.of(), List.of(), null, user("X", "infra"))
				.canExecute()).isFalse();
		assertThat(service.compute(app("IN_REVIEW", "ME"), null, List.of(), List.of(), null, user("ME")).canExecute())
				.isFalse();
	}

	@Test
	void 治理複驗只有待複驗狀態且governance角色() {
		assertThat(service.compute(app("PENDING_REVIEW", "ME"), null, List.of(), List.of(), null, user("G", "governance"))
				.canReview()).isTrue();
		assertThat(service.compute(app("PENDING_REVIEW", "ME"), null, List.of(), List.of(), null, user("G", "admin"))
				.canReview()).isFalse();
		assertThat(service.compute(app("EXECUTED", "ME"), null, List.of(), List.of(), null, user("G", "governance"))
				.canReview()).isFalse();
	}

	@Test
	void 刪除模式_admin恆ADMIN_申請人在已核准之後不可刪() {
		AppPermissions admin = service.compute(app("EXECUTED", "ME"), null, List.of(), List.of(), null,
				user("A", "admin"));
		assertThat(admin.canDelete()).isTrue();
		assertThat(admin.deleteMode()).isEqualTo(AppPermissionService.DELETE_MODE_ADMIN);

		for (String status : List.of("APPROVED", "IN_EXECUTION", "PENDING_REVIEW", "EXECUTED", "REJECTED")) {
			assertThat(service.compute(app(status, "ME"), null, List.of(), List.of(), null, user("ME")).canDelete())
					.as(status).isFalse();
		}
		assertThat(service.compute(app("DRAFT", "ME"), null, List.of(), List.of(), null, user("ME")).deleteMode())
				.isEqualTo(AppPermissionService.DELETE_MODE_APPLICANT);
		assertThat(service.compute(app("DRAFT", "ME"), null, List.of(), List.of(), null, user("OTHER")).canDelete())
				.isFalse();
	}

	@Test
	void 刪除模式_補件過的單新版還沒人簽_申請人也不能刪_admin仍可() {
		AppRow v2 = app("IN_REVIEW", "ME");
		v2.setCurrVerNo(2);
		List<ApprStepRow> undecided = List.of(step(1L, 1, "PENDING", null), step(2L, 2, "WAITING", null));

		AppPermissions mine = service.compute(v2, appr(), undecided, List.of(), null, user("ME"));
		assertThat(mine.canDelete()).isFalse();
		assertThat(mine.deleteMode()).isNull();
		assertThat(mine.canRecall()).isTrue();
		assertThat(service.compute(v2, appr(), undecided, List.of(), null, user("A", "admin")).deleteMode())
				.isEqualTo(AppPermissionService.DELETE_MODE_ADMIN);
	}

	@Test
	void 草稿編輯只有申請人_AI審查恆關閉_送出開給申請人與admin() {
		AppPermissions p = service.compute(app("DRAFT", "ME"), null, List.of(), List.of(), null, user("ME", "admin"));
		assertThat(p.canEditDraft()).isTrue();
		assertThat(p.canAiReview()).isFalse();
		assertThat(p.canSubmit()).isTrue();
		AppPermissions other = service.compute(app("DRAFT", "ME"), null, List.of(), List.of(), null, user("OTHER"));
		assertThat(other.canEditDraft()).isFalse();
		assertThat(other.canSubmit()).isFalse();
		assertThat(service.compute(app("DRAFT", "ME"), null, List.of(), List.of(), null, user("ADM", "admin"))
				.canSubmit()).isTrue();
		assertThat(service.compute(app("IN_REVIEW", "ME"), null, List.of(), List.of(), null, user("ME")).canSubmit())
				.isFalse();
	}
}
