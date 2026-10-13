package com.mpx.im_acct_insp.service;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增（v2.0 重寫，取代舊版同名測試）：帳號、首頁、待辦單元測試（spec v2.0 §2.4、§4.2、§4.5、§4.13、§6.3、§6.4；
//           驗收 T06⑤、T19、T24、T25）：申請前提含 EXPIRED、在途時 allowedReqTypes 為空；/accounts 需 ACC_LIST_FULL、
//           stateCounts 不套狀態條件；lookup 需完整名稱；lookup/{id} 名稱不符且不可看 → 404；明細他人 403；
//           鈴鐺逐筆相關、daysLeft 可為負；待辦依權限碼與執行人對照、帶 versionNo 與標題；審查條件共用函式
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S8：FIND_STATS 含緊急存取兩張卡的共用計數條件、不依登入者過濾
// ============================================================

import static com.mpx.im_acct_insp.service.RequestServiceTest.assertStatus;
import static com.mpx.im_acct_insp.support.TestFixture.ALEX;
import static com.mpx.im_acct_insp.support.TestFixture.GARY;
import static com.mpx.im_acct_insp.support.TestFixture.KEVIN;
import static com.mpx.im_acct_insp.support.TestFixture.MICHAEL;
import static com.mpx.im_acct_insp.support.TestFixture.MOLLY;
import static com.mpx.im_acct_insp.support.TestFixture.TERRY;
import static com.mpx.im_acct_insp.support.TestFixture.TESTER;
import static com.mpx.im_acct_insp.support.TestFixture.TODAY;
import static com.mpx.im_acct_insp.support.TestFixture.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import com.mpx.im_acct_insp.common.AcctConst;
import com.mpx.im_acct_insp.common.AcctMsg;
import com.mpx.im_acct_insp.dao.AcctDao;
import com.mpx.im_acct_insp.model.Acct;
import com.mpx.im_acct_insp.model.AcctListResult;
import com.mpx.im_acct_insp.model.AcctLookupBrief;
import com.mpx.im_acct_insp.model.AssetClass;
import com.mpx.im_acct_insp.model.CodeCountRow;
import com.mpx.im_acct_insp.model.ExpiringAcct;
import com.mpx.im_acct_insp.model.TodoItem;
import com.mpx.im_acct_insp.support.TestFixture;

class AccountAndHomeServiceTest {

	TestFixture fx;

	@BeforeEach
	void setUp() {
		fx = new TestFixture();
	}

	// ------------------------------------------------------------
	// 申請前提（Table 6.5）
	// ------------------------------------------------------------

	@Test
	void 申請前提_含已逾期_在途時不可異動() {
		assertThat(AccountService.allowedReqTypes("APPROVED", false)).containsExactly("MODIFY", "DISABLE", "DELETE");
		assertThat(AccountService.allowedReqTypes("EXPIRED", false)).containsExactly("DISABLE", "DELETE");
		assertThat(AccountService.allowedReqTypes("DISABLED", false)).containsExactly("ENABLE", "DELETE");
		assertThat(AccountService.allowedReqTypes("DELETED", false)).isEmpty();
		assertThat(AccountService.allowedReqTypes("REVIEW", false)).isEmpty();
		assertThat(AccountService.allowedReqTypes("APPROVED", true)).isEmpty();
		assertThat(AccountService.isAllowed("ADD", "APPROVED")).isFalse();
	}

	// ------------------------------------------------------------
	// 帳號
	// ------------------------------------------------------------

	@Test
	void 帳號列表_需ACC_LIST_FULL_計數與篩選() {
		assertStatus(() -> fx.accountService.list(id(TESTER), null, null, null, null, null), HttpStatus.FORBIDDEN, AcctMsg.P01);
		Acct a = fx.acct("AR20261001-001", "DB", AcctConst.AS_APPROVED);
		when(fx.acctDao.findList(any())).thenReturn(new ArrayList<>(List.of(a)));
		when(fx.acctDao.countByState(any())).thenReturn(List.of(cc("APPROVED", 3), cc("DELETED", 1)));
		when(fx.acctDao.countByCatg(any())).thenReturn(List.of(cc("DB", 2)));
		when(fx.acctDao.countExpiring(TODAY)).thenReturn(4L);
		AcctListResult r = fx.accountService.list(id(GARY), " db_% ", null, null, null, "1");
		assertThat(r.getItems().get(0).getAllowedReqTypes()).containsExactly("MODIFY", "DISABLE", "DELETE");
		assertThat(r.getStateCounts()).containsEntry("APPROVED", 3L).containsEntry("DELETED", 1L);
		assertThat(r.getCatgCounts()).containsEntry("DB", 2L);
		assertThat(r.getExpiringCount()).isEqualTo(4L);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, Object>> p = ArgumentCaptor.forClass(Map.class);
		verify(fx.acctDao).findList(p.capture());
		assertThat(p.getValue()).containsEntry("keyword", "db\\_\\%").containsEntry("expiring", 1);
		assertStatus(() -> fx.accountService.list(id(GARY), null, null, "VOID", null, null), HttpStatus.BAD_REQUEST,
				AcctMsg.BAD_CODE_PREFIX + "狀態");
	}

	@Test
	void 帳號列表SQL_計數排除自身篩選_預設不含已刪除_到期條件共用() {
		assertThat(AcctDao.COUNT_BY_STATE).doesNotContain(":stateCode").contains(":catgId").contains(":expiring");
		assertThat(AcctDao.COUNT_BY_CATG).doesNotContain(":catgId").contains(":stateCode");
		assertThat(AcctDao.FIND_LIST).contains("A.ACCT_STATE_CODE <> 'DELETED'").contains(AcctDao.EXPIRING_COND);
		assertThat(AcctDao.COUNT_EXPIRING).contains(AcctDao.EXPIRING_COND).doesNotContain(":keyword");
		assertThat(AcctDao.EXPIRING_COND).contains("A.EXPIRY_DATE <= :today + 30").contains("A.ACCT_STATE_CODE = 'EXPIRED'");
		assertThat(com.mpx.im_acct_insp.dao.HomeDao.FIND_STATS).contains(AcctDao.EXPIRING_COND)
				// S8：緊急存取兩張卡共用計數條件；數字看全部，不依登入者過濾
				.contains(com.mpx.im_acct_insp.dao.EmgDao.UNDECIDED_COND).contains("AS EMG_UNDECIDED")
				.contains(com.mpx.im_acct_insp.dao.EmgDao.RETURNED_COND).contains("AS EMG_RETURNED")
				.doesNotContain(":userId").doesNotContain(":applyBy");
		assertThat(com.mpx.im_acct_insp.dao.HomeDao.FIND_EXPIRING).contains(AcctDao.EXPIRING_COND);
	}

	@Test
	void lookup_需完整帳號名稱_精確比對() {
		assertStatus(() -> fx.accountService.lookup(id(TESTER), " "), HttpStatus.BAD_REQUEST, AcctMsg.E02_PREFIX + "帳號名稱");
		AcctLookupBrief b = new AcctLookupBrief();
		b.setAcctStateCode("APPROVED");
		b.setInFlight(true);
		when(fx.acctDao.findLookup("db_user01")).thenReturn(List.of(b));
		assertThat(fx.accountService.lookup(id(TESTER), " db_user01 ")).singleElement()
				.satisfies(x -> assertThat(x.getAllowedReqTypes()).isEmpty());
		assertThat(AcctDao.FIND_LOOKUP).contains("A.ACCT_NAME = :acctName").doesNotContain("LIKE")
				.contains("('APPROVED', 'EXPIRED', 'DISABLED')");
	}

	@Test
	void lookupById_名稱相符或可看_否則404_狀態限制() {
		Acct a = fx.acct("AR20261001-001", "DB", AcctConst.AS_APPROVED);
		a.setApplyBy(MOLLY);
		assertStatus(() -> fx.accountService.lookupById(id(TESTER), a.getAcctId(), null), HttpStatus.NOT_FOUND, AcctMsg.N01);
		assertStatus(() -> fx.accountService.lookupById(id(TESTER), a.getAcctId(), "acct_x"), HttpStatus.NOT_FOUND, AcctMsg.N01);
		assertThat(fx.accountService.lookupById(id(TESTER), a.getAcctId(), a.getAcctName()).getAcctId()).isEqualTo(a.getAcctId());
		assertThat(fx.accountService.lookupById(id(MOLLY), a.getAcctId(), null).getAllowedReqTypes()).contains("MODIFY");
		a.setAcctStateCode(AcctConst.AS_REVIEW);
		assertStatus(() -> fx.accountService.lookupById(id(MOLLY), a.getAcctId(), a.getAcctName()), HttpStatus.NOT_FOUND,
				AcctMsg.N01);
	}

	@Test
	void 帳號明細與歷史_他人403_歷史帶標籤() {
		Acct a = fx.acct("AR20261001-001", "DB", AcctConst.AS_APPROVED);
		assertStatus(() -> fx.accountService.detail(id(TESTER), a.getAcctId()), HttpStatus.FORBIDDEN, AcctMsg.P01);
		assertStatus(() -> fx.accountService.detail(id(TESTER), "AR20991231-999"), HttpStatus.NOT_FOUND, AcctMsg.N01);
		com.mpx.im_acct_insp.model.AcctHist h = new com.mpx.im_acct_insp.model.AcctHist();
		h.setIsSelfAction(1);
		h.setActBy(KEVIN);
		h.setReqApplyBy(KEVIN);
		when(fx.acctHistDao.findByAcct(a.getAcctId())).thenReturn(List.of(h));
		assertThat(fx.accountService.history(id(KEVIN), a.getAcctId()).get(0).getSelfActionLabel())
				.isEqualTo(AcctConst.SELF_LABEL_APPLICANT);
	}

	// ------------------------------------------------------------
	// 首頁、鈴鐺、待辦
	// ------------------------------------------------------------

	@Test
	void 鈴鐺_逐筆相關_無相關者不顯示_剩餘天數可為負() {
		ExpiringAcct a = exp("A", "DB", KEVIN, TODAY.plusDays(10), "APPROVED");
		ExpiringAcct b = exp("B", "HOST", MOLLY, TODAY.minusDays(3), "EXPIRED");
		when(fx.homeDao.findExpiring(TODAY)).thenAnswer(inv -> List.of(copy(a), copy(b)));
		assertThat(fx.homeService.expiring(id(GARY))).extracting(ExpiringAcct::getAcctId).containsExactly("A", "B");
		assertThat(fx.homeService.expiring(id(MICHAEL))).extracting(ExpiringAcct::getAcctId).containsExactly("A");
		assertThat(fx.homeService.expiring(id(TESTER))).isEmpty();
		assertThat(fx.homeService.expiring(id(GARY)).get(1).getDaysLeft()).isEqualTo(-3L);
	}

	@Test
	void 待辦_依關卡權限與執行人對照_帶標題() {
		TodoItem t = new TodoItem();
		t.setReqId("AR20261007-001");
		t.setReqTypeCode("ADD");
		t.setCatgId("DOOR");
		t.setCatgName("機房門禁卡");
		t.setSubmitDate(LocalDateTime.of(2026, 10, 7, 9, 0));
		when(fx.acctReqDao.findTodoApprove(List.of(2))).thenReturn(List.of(t));
		List<TodoItem> alex = fx.homeService.todos(id(ALEX));
		assertThat(alex).singleElement().satisfies(x -> {
			assertThat(x.getActionKind()).isEqualTo("APPROVE");
			assertThat(x.getTitle()).isEqualTo("新增 機房門禁卡：（門禁卡，待發卡）");
		});
		verify(fx.acctReqDao).findTodoExec(List.of("HOST", "APP", "SVC", "NET", "CERT", "DOOR"));
		verify(fx.acctReqDao, never()).findTodoVerify();

		fx.homeService.todos(id(TESTER));
		verify(fx.acctReqDao, never()).findTodoApprove(List.of());
		fx.homeService.todos(id(KEVIN));
		verify(fx.acctReqDao).findTodoApprove(List.of(4));
		verify(fx.acctReqDao).findTodoVerify();
		fx.homeService.todos(id(TERRY));
		verify(fx.acctReqDao).findTodoApprove(List.of(3));
		verify(fx.acctReqDao).findTodoExec(List.of("DB"));
		verify(fx.acctReqDao, never()).findTodoExec(List.of());
	}

	@Test
	void 審查條件共用函式_分類觸發_門禁_特權() {
		AssetClass h01 = TestFixture.assetClasses().get(0);
		AssetClass h02 = TestFixture.assetClasses().get(1);
		assertThat(ReviewRule.matches(h01, "DB", 0)).isTrue();
		assertThat(ReviewRule.matches(h02, "DOOR", 0)).isTrue();
		assertThat(ReviewRule.matches(h02, "DB", 1)).isTrue();
		assertThat(ReviewRule.matches(null, "DB", 0)).isFalse();
		assertThat(ReviewRule.shouldWrite("ADD", true, false, null)).isTrue();
		assertThat(ReviewRule.shouldWrite("MODIFY", true, false, TODAY)).isTrue();
		assertThat(ReviewRule.shouldWrite("MODIFY", true, true, null)).isTrue();
		assertThat(ReviewRule.shouldWrite("ENABLE", true, true, TODAY)).isFalse();
		assertThat(ReviewRule.shouldWrite("DISABLE", true, false, null)).isFalse();
		assertThat(ReviewRule.shouldWrite("ADD", false, false, null)).isFalse();
		assertThat(ReviewRule.nextReview(TODAY)).isEqualTo(TODAY.plusDays(180));
	}

	static ExpiringAcct exp(String id, String catg, String applyBy, java.time.LocalDate date, String state) {
		ExpiringAcct e = new ExpiringAcct();
		e.setAcctId(id);
		e.setCatgId(catg);
		e.setApplyBy(applyBy);
		e.setExpiryDate(date.atStartOfDay());
		e.setAcctStateCode(state);
		return e;
	}

	static ExpiringAcct copy(ExpiringAcct s) {
		ExpiringAcct e = exp(s.getAcctId(), s.getCatgId(), s.getApplyBy(), s.getExpiryDate().toLocalDate(), s.getAcctStateCode());
		return e;
	}

	static CodeCountRow cc(String code, long n) {
		CodeCountRow r = new CodeCountRow();
		r.setCode(code);
		r.setCnt(n);
		return r;
	}
}
