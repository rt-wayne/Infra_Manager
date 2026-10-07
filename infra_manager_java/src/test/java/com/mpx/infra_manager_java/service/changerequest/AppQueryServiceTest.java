package com.mpx.infra_manager_java.service.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：申請單列表／檢視服務的單元測試（S4，DAO 以 Mockito 取代、不連 DB）。
//           鎖定：篩選值正規化（預設 90 天只在 from／to 都沒給時、無效代碼 400 帶訊息、q 去空白與 100 字上限、
//           from>to 400、page<1 視為 1）、列表項目對應（單一候選人顯示姓名、多人顯示「N 人待簽」、無關卡為 null）、
//           檢視 404（格式不符不打 DB、查無）、沒有簽核實例時用流程定義展開、候選人姓名掛到對應關卡
//           S9 R2（Claude Opus 5.5，2026-10-07）：歷次簽核排除目前實例、依實例分組保留順序
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mpx.infra_manager_java.dao.changerequest.AppDao;
import com.mpx.infra_manager_java.dao.changerequest.ApprovalDao;
import com.mpx.infra_manager_java.dao.changerequest.AttachDao;
import com.mpx.infra_manager_java.model.auth.AuthUser;
import com.mpx.infra_manager_java.model.changerequest.AppDetail;
import com.mpx.infra_manager_java.model.changerequest.AppListItem;
import com.mpx.infra_manager_java.model.changerequest.AppListQuery;
import com.mpx.infra_manager_java.model.changerequest.AppListResponse;
import com.mpx.infra_manager_java.model.changerequest.AppRow;
import com.mpx.infra_manager_java.model.changerequest.ApprHistoryRow;
import com.mpx.infra_manager_java.model.changerequest.ApprRow;
import com.mpx.infra_manager_java.model.changerequest.ApprStepRow;
import com.mpx.infra_manager_java.model.changerequest.CandRow;
import com.mpx.infra_manager_java.model.changerequest.OptionRow;
import com.mpx.infra_manager_java.util.TaiwanTime;
import com.mpx.infra_manager_java.web.ApiBadRequestException;
import com.mpx.infra_manager_java.web.ApiNotFoundException;

@ExtendWith(MockitoExtension.class)
class AppQueryServiceTest {

	@Mock
	private AppDao appDao;
	@Mock
	private ApprovalDao approvalDao;
	@Mock
	private AttachDao attachDao;

	private static final AuthUser ME = new AuthUser("E0001", "wayne", "測試人員", List.of("infra"), false);

	private AppQueryService service() {
		return new AppQueryService(appDao, approvalDao, attachDao, new AppPermissionService());
	}

	private static AppListQuery raw(String status, String q, LocalDate from, LocalDate to, int page) {
		return new AppListQuery(status, null, null, false, q, from, to, page);
	}

	// ---------- normalize ----------

	@Test
	void from與to都沒給時預設最近90天_只給其中一個就不補() {
		AppListQuery both = AppQueryService.normalize(raw(null, null, null, null, 1));
		assertThat(both.from()).isEqualTo(TaiwanTime.today().minusDays(AppQueryService.DEFAULT_RANGE_DAYS));
		assertThat(both.to()).isNull();

		LocalDate d = LocalDate.of(2026, 9, 1);
		assertThat(AppQueryService.normalize(raw(null, null, null, d, 1)).from()).isNull();
		assertThat(AppQueryService.normalize(raw(null, null, d, null, 1)).to()).isNull();
	}

	@Test
	void 無效的狀態分級來源回400帶訊息_空白視為不篩() {
		assertThatThrownBy(() -> AppQueryService.normalize(raw("WHATEVER", null, null, null, 1)))
				.isInstanceOf(ApiBadRequestException.class).hasMessage(AppQueryService.MSG_BAD_STATUS);
		assertThatThrownBy(() -> AppQueryService
				.normalize(new AppListQuery(null, "P9", null, false, null, null, null, 1)))
				.isInstanceOf(ApiBadRequestException.class).hasMessage(AppQueryService.MSG_BAD_PRIORITY);
		assertThatThrownBy(() -> AppQueryService
				.normalize(new AppListQuery(null, null, "PAPER", false, null, null, null, 1)))
				.isInstanceOf(ApiBadRequestException.class).hasMessage(AppQueryService.MSG_BAD_SOURCE);

		AppListQuery blank = AppQueryService.normalize(new AppListQuery(" ", "", null, true, null, null, null, 1));
		assertThat(blank.status()).isNull();
		assertThat(blank.priority()).isNull();
		assertThat(blank.mine()).isTrue();
		assertThat(AppQueryService.normalize(raw("IN_REVIEW", null, null, null, 1)).status()).isEqualTo("IN_REVIEW");
	}

	@Test
	void 關鍵字去空白_全空白視為沒給_超過100字回400() {
		assertThat(AppQueryService.normalize(raw(null, "  交換器 ", null, null, 1)).q()).isEqualTo("交換器");
		assertThat(AppQueryService.normalize(raw(null, "   ", null, null, 1)).q()).isNull();
		assertThat(AppQueryService.normalize(raw(null, "x".repeat(AppQueryService.Q_MAX), null, null, 1)).q())
				.hasSize(AppQueryService.Q_MAX);
		assertThatThrownBy(() -> AppQueryService.normalize(raw(null, "x".repeat(AppQueryService.Q_MAX + 1), null, null, 1)))
				.isInstanceOf(ApiBadRequestException.class).hasMessage(AppQueryService.MSG_Q_TOO_LONG);
	}

	@Test
	void 起日晚於迄日回400_同一天可以() {
		LocalDate d = LocalDate.of(2026, 10, 6);
		assertThatThrownBy(() -> AppQueryService.normalize(raw(null, null, d, d.minusDays(1), 1)))
				.isInstanceOf(ApiBadRequestException.class).hasMessage(AppQueryService.MSG_BAD_RANGE);
		AppListQuery same = AppQueryService.normalize(raw(null, null, d, d, 1));
		assertThat(same.from()).isEqualTo(d);
		assertThat(same.to()).isEqualTo(d);
	}

	@Test
	void page小於1視為1() {
		assertThat(AppQueryService.normalize(raw(null, null, null, null, 0)).page()).isEqualTo(1);
		assertThat(AppQueryService.normalize(raw(null, null, null, null, -5)).page()).isEqualTo(1);
		assertThat(AppQueryService.normalize(raw(null, null, null, null, 3)).page()).isEqualTo(3);
		assertThat(AppQueryService.normalize(raw(null, null, null, null, 3)).offset()).isEqualTo(40);
	}

	// ---------- toListItem ----------

	private static AppRow listRow(String stepName, Integer candCnt, String candName, Integer mine) {
		AppRow r = new AppRow();
		r.setAppId("IM20261006-902");
		r.setAppTitle("儲存設備擴充硬碟");
		r.setPrioCode("P3");
		r.setPrioName("P3 一般");
		r.setPrioColor("#2563eb");
		r.setWorkSubj("SAN-01 新增硬碟");
		r.setApplyDeptName("資訊處 Infra 組");
		r.setApplyUserName("王申請");
		r.setCurrVerNo(1);
		r.setAppStatusCode("IN_REVIEW");
		r.setSourceCode("ONLINE");
		r.setCreateDate(Timestamp.valueOf("2026-10-04 09:30:15"));
		r.setCurrStepName(stepName);
		r.setCurrStepCandCnt(candCnt);
		r.setCurrStepCandName(candName);
		r.setMineFlag(mine);
		return r;
	}

	@Test
	void 列表項目_單一候選人顯示姓名_多人顯示N人待簽_無關卡為null() {
		AppListItem one = AppQueryService.toListItem(listRow("Infra 主管", 1, "陳主管", 1));
		assertThat(one.appId()).isEqualTo("IM20261006-902");
		assertThat(one.currentStep()).isEqualTo("Infra 主管");
		assertThat(one.currentApprover()).isEqualTo("陳主管");
		assertThat(one.mine()).isTrue();
		assertThat(one.createdAt()).isEqualTo("2026-10-04 09:30");
		assertThat(one.prioColor()).isEqualTo("#2563eb");

		assertThat(AppQueryService.toListItem(listRow("Infra 主管", 2, "陳主管", 0)).currentApprover())
				.isEqualTo("2 人待簽");
		assertThat(AppQueryService.toListItem(listRow("Infra 主管", 0, null, 0)).currentApprover()).isNull();

		AppListItem none = AppQueryService.toListItem(listRow(null, 3, "x", null));
		assertThat(none.currentStep()).isNull();
		assertThat(none.currentApprover()).isNull();
		assertThat(none.mine()).isFalse();
	}

	@Test
	void list組合總數與待我簽核數() {
		when(appDao.findList(any(), eq("E0001"))).thenReturn(List.of(listRow("機房管理員", 1, "林機房", 0)));
		when(appDao.countList(any(), eq("E0001"))).thenReturn(41L);
		when(appDao.countMine("E0001")).thenReturn(2L);

		AppListResponse res = service().list(raw(null, null, null, null, 2), ME);

		assertThat(res.items()).hasSize(1);
		assertThat(res.total()).isEqualTo(41);
		assertThat(res.page()).isEqualTo(2);
		assertThat(res.size()).isEqualTo(AppListQuery.PAGE_SIZE);
		assertThat(res.mineCount()).isEqualTo(2);
	}

	// ---------- detail ----------

	@Test
	void 單號格式不符直接404不打DB_查無也404() {
		assertThatThrownBy(() -> service().detail("im 001;drop", ME)).isInstanceOf(ApiNotFoundException.class)
				.hasMessage(AppQueryService.MSG_APP_NOT_FOUND);
		assertThatThrownBy(() -> service().detail("x".repeat(21).toUpperCase(), ME))
				.isInstanceOf(ApiNotFoundException.class);
		verify(appDao, never()).findById(anyString());

		when(appDao.findById("IM20261006-999")).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service().detail("IM20261006-999", ME)).isInstanceOf(ApiNotFoundException.class)
				.hasMessage(AppQueryService.MSG_APP_NOT_FOUND);
	}

	private static AppRow detailRow() {
		AppRow a = listRow(null, null, null, null);
		a.setFlowId("full");
		a.setFlowName("完整流程");
		a.setApplyUserId("S4U001");
		a.setApplyDate(Timestamp.valueOf("2026-10-05 00:00:00"));
		a.setIsSelfExec(1);
		a.setIsSupExec(0);
		a.setSchedStartDate(Timestamp.valueOf("2026-10-15 09:00:00"));
		a.setUnitRange("10-14U");
		return a;
	}

	private static ApprStepRow step(Long id, int seq, String name, String status) {
		ApprStepRow s = new ApprStepRow();
		s.setApprStepId(id);
		s.setSeqNo(seq);
		s.setStepName(name);
		s.setStepStatusCode(status);
		return s;
	}

	@Test
	void 沒有簽核實例時用流程定義展開_簽核實例欄位為null() {
		AppRow app = detailRow();
		app.setAppStatusCode("DRAFT");
		when(appDao.findById("IM20261006-902")).thenReturn(Optional.of(app));
		when(approvalDao.findCurrent("IM20261006-902", 1)).thenReturn(Optional.empty());
		when(approvalDao.findFlowSteps("full")).thenReturn(List.of(step(null, 1, "機房管理員", "WAITING"),
				step(null, 2, "Infra 主管", "WAITING")));
		when(appDao.findExec("IM20261006-902", 1)).thenReturn(Optional.empty());

		AppDetail d = service().detail("IM20261006-902", ME);

		assertThat(d.approval().apprId()).isNull();
		assertThat(d.approval().statusCode()).isNull();
		assertThat(d.approval().steps()).extracting(AppDetail.Step::stepName).containsExactly("機房管理員", "Infra 主管");
		assertThat(d.approval().steps()).allSatisfy(s -> assertThat(s.candidateNames()).isEmpty());
		assertThat(d.execution()).isNull();
		assertThat(d.applyDate()).isEqualTo("2026-10-05");
		assertThat(d.schedule().start()).isEqualTo("2026-10-15 09:00");
		assertThat(d.schedule().end()).isNull();
		assertThat(d.location().uRange()).isEqualTo("10-14U");
		assertThat(d.selfExec()).isTrue();
		assertThat(d.supplierExec()).isFalse();
		assertThat(d.permissions().canDecide()).isFalse();
		verify(approvalDao, never()).findSteps(any(Long.class));
	}

	@Test
	void 有簽核實例時候選人姓名掛到對應關卡_選項依群組分流() {
		AppRow app = detailRow();
		when(appDao.findById("IM20261006-902")).thenReturn(Optional.of(app));
		ApprRow appr = new ApprRow();
		appr.setApprId(77L);
		appr.setApprStatusCode("PENDING");
		appr.setStartDate(Timestamp.valueOf("2026-10-04 10:00:00"));
		when(approvalDao.findCurrent("IM20261006-902", 1)).thenReturn(Optional.of(appr));
		when(approvalDao.findSteps(77L)).thenReturn(List.of(step(1L, 1, "機房管理員", "APPROVED"),
				step(2L, 2, "Infra 主管", "PENDING")));
		CandRow c1 = new CandRow();
		c1.setApprStepId(2L);
		c1.setUserId("S4U003");
		c1.setUserName("陳主管");
		CandRow c2 = new CandRow();
		c2.setApprStepId(2L);
		c2.setUserId("E0001");
		when(approvalDao.findCandidates(77L)).thenReturn(List.of(c1, c2));
		when(appDao.findExec("IM20261006-902", 1)).thenReturn(Optional.empty());

		OptionRow catg = new OptionRow();
		catg.setGroupCode("CATG_ITEM");
		catg.setOptionCode("server_storage_01");
		catg.setUpOptionCode("server_storage");
		OptionRow reason = new OptionRow();
		reason.setGroupCode("REASON");
		reason.setOptionCode("capacity");
		OptionRow scope = new OptionRow();
		scope.setGroupCode("SCOPE");
		scope.setOptionCode("none");
		when(appDao.findOptions("IM20261006-902")).thenReturn(List.of(catg, reason, scope));

		AppDetail d = service().detail("IM20261006-902", ME);

		assertThat(d.approval().apprId()).isEqualTo(77L);
		assertThat(d.approval().startedAt()).isEqualTo("2026-10-04 10:00");
		assertThat(d.approval().steps().get(0).candidateNames()).isEmpty();
		// 沒有姓名的候選人退回顯示工號
		assertThat(d.approval().steps().get(1).candidateNames()).containsExactly("陳主管", "E0001");
		assertThat(d.categories()).extracting(AppDetail.Option::code).containsExactly("server_storage_01");
		assertThat(d.categories().get(0).upCode()).isEqualTo("server_storage");
		assertThat(d.reasons()).extracting(AppDetail.Option::code).containsExactly("capacity");
		assertThat(d.scopes()).extracting(AppDetail.Option::code).containsExactly("none");
		// 我是第 2 關候選人 → 可簽核；不是申請人 → 不能撤回
		assertThat(d.permissions().canDecide()).isTrue();
		assertThat(d.permissions().canRecall()).isFalse();
		// 歷次簽核排除目前實例
		verify(approvalDao).findHistory("IM20261006-902", 77L);
		assertThat(d.approvalHistory()).isEmpty();
	}

	private static ApprHistoryRow hist(long apprId, int verNo, String apprStatus, int seq, String stepStatus,
			String userName, String memo) {
		ApprHistoryRow r = new ApprHistoryRow();
		r.setApprId(apprId);
		r.setDocVerNo(verNo);
		r.setApprStatusCode(apprStatus);
		r.setApprStartDate(Timestamp.valueOf("2026-10-01 09:00:00"));
		r.setApprCloseDate(Timestamp.valueOf("2026-10-02 15:30:00"));
		r.setApprStepId(apprId * 10 + seq);
		r.setSeqNo(seq);
		r.setStepName("第" + seq + "關");
		r.setStepStatusCode(stepStatus);
		r.setIsNotifyOnly(0);
		r.setUserName(userName);
		r.setMemo(memo);
		return r;
	}

	@Test
	void 歷次簽核依實例分組_保留順序_候選人一律空() {
		List<AppDetail.PastApproval> h = AppQueryService.pastApprovals(List.of(
				hist(5L, 1, "RECALLED", 1, "CANCELLED", null, null),
				hist(5L, 1, "RECALLED", 2, "CANCELLED", null, null),
				hist(8L, 1, "REJECTED", 1, "APPROVED", "王一", null),
				hist(8L, 1, "REJECTED", 2, "REJECTED", "李二", "資料不全"),
				hist(8L, 1, "REJECTED", 3, "SKIPPED", null, null)));

		assertThat(h).extracting(AppDetail.PastApproval::apprId).containsExactly(5L, 8L);
		assertThat(h.get(0).statusCode()).isEqualTo("RECALLED");
		assertThat(h.get(0).verNo()).isEqualTo(1);
		assertThat(h.get(0).startedAt()).isEqualTo("2026-10-01 09:00");
		assertThat(h.get(0).closedAt()).isEqualTo("2026-10-02 15:30");
		assertThat(h.get(0).steps()).hasSize(2);
		AppDetail.PastApproval rejected = h.get(1);
		assertThat(rejected.steps()).extracting(AppDetail.Step::statusCode).containsExactly("APPROVED", "REJECTED", "SKIPPED");
		assertThat(rejected.steps().get(1).deciderName()).isEqualTo("李二");
		assertThat(rejected.steps().get(1).memo()).isEqualTo("資料不全");
		assertThat(rejected.steps()).allSatisfy(s -> assertThat(s.candidateNames()).isEmpty());
		assertThat(AppQueryService.pastApprovals(List.of())).isEmpty();
	}
}
