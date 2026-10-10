package com.mpx.im_acct_insp.service;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：排程與通知單元測試（spec v2.0 §4.11、§4.12、§6.7；驗收 T16～T18）
//           J01：每筆帳號一個交易、影響 1 筆才寫 EXPIRE（SYS、系統排程）、單筆失敗 PARTIAL 並寄 JOB_FAIL、
//           同基準日重跑（查詢回空）不重複轉換不重寄；J02：週鍵＝所在週週一、本週已寄 → SUCCESS／0 筆不寄；
//           手動觸發需 SYS_JOB_RUN、未知代碼 404、allow-base-date＝false 帶 baseDate 400 E17、執行中 409 E16、
//           JOB_RUN 寫 RUN_BY 與 BASE_DATE；通知：收件人去重、無 Email SKIP、寄送例外 FAIL、角色清單解析、參照鍵格式
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 變更單 #2：NotificationService 不再接收角色 properties（移除角色清單解析測試）；
//           到期、每週彙總、排程失敗的角色收件人改為 mock PermDao.findUsersInRoles（GOV／SYS_ADMIN 常數）驗證
// ============================================================

import static com.mpx.im_acct_insp.service.RequestServiceTest.assertStatus;
import static com.mpx.im_acct_insp.support.TestFixture.HANK;
import static com.mpx.im_acct_insp.support.TestFixture.KEVIN;
import static com.mpx.im_acct_insp.support.TestFixture.TODAY;
import static com.mpx.im_acct_insp.support.TestFixture.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.mpx.im_acct_insp.common.AcctConst;
import com.mpx.im_acct_insp.common.AcctMsg;
import com.mpx.im_acct_insp.common.ApiException;
import com.mpx.im_acct_insp.model.Acct;
import com.mpx.im_acct_insp.model.JobRunResult;
import com.mpx.im_acct_insp.model.Recipient;
import com.mpx.im_acct_insp.support.TestFixture;

class JobAndNotifyTest {

	TestFixture fx;
	JobService job;

	@BeforeEach
	void setUp() {
		fx = new TestFixture();
		job = fx.jobService(true);
		when(fx.jobRunDao.nextId()).thenReturn(77L);
	}

	// ------------------------------------------------------------
	// 排程
	// ------------------------------------------------------------

	@Test
	void J01_轉逾期_影響1筆才寫歷史_寄轉逾期與7天信_計數() {
		Acct b = fx.acct("AR20261001-001", "DB", AcctConst.AS_APPROVED);
		b.setExpiryTypeCode("DATE");
		b.setExpiryDate(TODAY.minusDays(1).atStartOfDay());
		Acct raced = fx.acct("AR20261001-002", "DB", AcctConst.AS_APPROVED);
		when(fx.acctDao.findToExpire(TODAY)).thenReturn(List.of(b, raced));
		when(fx.acctDao.expire(eq(b.getAcctId()), eq(TODAY), any(), eq(AcctConst.SYSTEM_USER))).thenReturn(1);
		when(fx.acctDao.expire(eq(raced.getAcctId()), eq(TODAY), any(), any())).thenReturn(0);
		when(fx.acctDao.findExpiredUnnotified(AcctConst.EV_EXPIRED)).thenReturn(List.of(b));
		Acct d = fx.acct("AR20261001-003", "HOST", AcctConst.AS_APPROVED);
		when(fx.acctDao.findExpiry7dUnnotified(AcctConst.EV_EXPIRY_7D, TODAY, TODAY.plusDays(7))).thenReturn(List.of(d));
		when(fx.notificationService.expiryEvent(any(), any(), any())).thenReturn(3);

		JobRunResult r = job.runManual(id(HANK), "J01", null);
		assertThat(r.getRunId()).isEqualTo(77L);
		assertThat(r.getResultCode()).isEqualTo(AcctConst.JR_SUCCESS);
		assertThat(r.getAffectedCnt()).isEqualTo(3);
		assertThat(fx.hists).singleElement().satisfies(h -> {
			assertThat(h.getActionCode()).isEqualTo(AcctConst.ACT_EXPIRE);
			assertThat(h.getSourceCode()).isEqualTo(AcctConst.HIST_SRC_SYS);
			assertThat(h.getActBy()).isNull();
			assertThat(h.getActByName()).isEqualTo("系統排程");
			assertThat(h.getResultDescr()).contains("2026-10-06");
		});
		assertThat(fx.txm.commits).isEqualTo(2);
		verify(fx.jobRunDao).insertRunning(eq(77L), eq("J01"), eq(HANK), eq(TODAY), any(), eq(HANK));
		verify(fx.jobRunDao).finish(eq(77L), eq("SUCCESS"), eq(3), isNull(), any(), eq(HANK));
		verify(fx.notificationService, never()).jobFail(any(), anyLong(), any(), any());
	}

	@Test
	void J01_同基準日重跑_查詢已無對象_不重複轉換不重寄() {
		JobRunResult r = job.runManual(id(HANK), "J01", null);
		assertThat(r.getAffectedCnt()).isZero();
		assertThat(fx.hists).isEmpty();
		verify(fx.notificationService, never()).expiryEvent(any(), any(), any());
	}

	@Test
	void J01_單筆失敗_繼續處理_PARTIAL並寄排程失敗信() {
		Acct a = fx.acct("AR20261001-001", "DB", AcctConst.AS_APPROVED);
		Acct b = fx.acct("AR20261001-002", "DB", AcctConst.AS_APPROVED);
		when(fx.acctDao.findToExpire(TODAY)).thenReturn(List.of(a, b));
		doThrow(new IllegalStateException("lock")).when(fx.acctDao).expire(eq(a.getAcctId()), any(), any(), any());
		when(fx.acctDao.expire(eq(b.getAcctId()), any(), any(), any())).thenReturn(1);
		JobRunResult r = job.runManual(id(HANK), "J01", null);
		assertThat(r.getResultCode()).isEqualTo(AcctConst.JR_PARTIAL);
		assertThat(r.getAffectedCnt()).isEqualTo(1);
		assertThat(fx.txm.rollbacks).isEqualTo(1);
		verify(fx.notificationService).jobFail(eq("J01"), eq(77L), eq("PARTIAL"), anyString());
	}

	@Test
	void J01_整體查詢失敗_FAIL() {
		when(fx.acctDao.findToExpire(TODAY)).thenThrow(new IllegalStateException("db down"));
		assertThat(job.runManual(id(HANK), "J01", null).getResultCode()).isEqualTo(AcctConst.JR_FAIL);
		verify(fx.notificationService).jobFail(eq("J01"), eq(77L), eq("FAIL"), eq("IllegalStateException"));
	}

	@Test
	void J02_週鍵為週一_本週已寄不再寄_否則列出已逾期() {
		assertThat(JobService.weekKey(LocalDate.of(2026, 10, 7))).isEqualTo("20261005");
		assertThat(JobService.weekKey(LocalDate.of(2026, 10, 5))).isEqualTo("20261005");
		assertThat(JobService.weekKey(LocalDate.of(2026, 10, 11))).isEqualTo("20261005");

		when(fx.notifyLogDao.countByEventRef(AcctConst.EV_WEEKLY_SUMMARY, AcctConst.REF_WEEK, "20261005")).thenReturn(2L);
		JobRunResult sent = job.runManual(id(HANK), "J02", null);
		assertThat(sent.getResultCode()).isEqualTo("SUCCESS");
		assertThat(sent.getAffectedCnt()).isZero();
		verify(fx.notificationService, never()).weeklySummary(any(), any(), any());

		when(fx.notifyLogDao.countByEventRef(AcctConst.EV_WEEKLY_SUMMARY, AcctConst.REF_WEEK, "20261005")).thenReturn(0L);
		Acct e = fx.acct("AR20261001-001", "DB", AcctConst.AS_EXPIRED);
		when(fx.acctDao.findExpiredAll()).thenReturn(List.of(e));
		assertThat(job.runManual(id(HANK), "J02", null).getAffectedCnt()).isEqualTo(1);
		verify(fx.notificationService).weeklySummary("20261005", List.of(e), TODAY);
	}

	@Test
	void 手動觸發_權限_未知代碼_基準日開關() {
		assertStatus(() -> job.runManual(id(KEVIN), "J01", null), HttpStatus.FORBIDDEN, AcctMsg.P01);
		assertStatus(() -> job.runManual(id(HANK), "J03", null), HttpStatus.NOT_FOUND, AcctMsg.N01);
		JobService noBase = fx.jobService(false);
		assertStatus(() -> noBase.runManual(id(HANK), "J01", "2026-10-09"), HttpStatus.BAD_REQUEST, AcctMsg.E17);
		assertStatus(() -> job.runManual(id(HANK), "J01", "2026/10/09"), HttpStatus.BAD_REQUEST, AcctMsg.BAD_DATE);
		job.runManual(id(HANK), "J01", "2026-10-09");
		verify(fx.acctDao).findToExpire(LocalDate.of(2026, 10, 9));
		verify(fx.jobRunDao).insertRunning(anyLong(), eq("J01"), eq(HANK), eq(LocalDate.of(2026, 10, 9)), any(), any());
	}

	@Test
	void 同排程不可同時跑_409E16() throws Exception {
		CountDownLatch inside = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		when(fx.acctDao.findToExpire(TODAY)).thenAnswer(inv -> {
			inside.countDown();
			release.await();
			return List.of();
		});
		AtomicReference<Throwable> err = new AtomicReference<>();
		Thread t = new Thread(() -> {
			try {
				job.runManual(id(HANK), "J01", null);
			} catch (Throwable e) {
				err.set(e);
			}
		});
		t.start();
		inside.await();
		assertStatus(() -> job.runManual(id(HANK), "J01", null), HttpStatus.CONFLICT, AcctMsg.E16);
		// 不同排程不互鎖
		job.runManual(id(HANK), "J02", null);
		release.countDown();
		t.join();
		assertThat(err.get()).isNull();
		job.runManual(id(HANK), "J01", null);
	}

	@Test
	void 排程觸發_關閉時不執行_由排程觸發RUN_BY為空() {
		JobService off = new JobService(fx.acctDao, fx.acctHistDao, fx.jobRunDao, fx.notifyLogDao, fx.notificationService,
				fx.permissionService, fx.tx, fx.clock, false, false);
		off.scheduledJ01();
		verify(fx.jobRunDao, never()).nextId();
		job.scheduledJ02();
		verify(fx.jobRunDao).insertRunning(eq(77L), eq("J02"), isNull(), eq(TODAY), any(), eq(AcctConst.SYSTEM_USER));
	}

	// ------------------------------------------------------------
	// 通知
	// ------------------------------------------------------------

	@Test
	void 通知_收件人去重_無Email略過_寄送例外記FAIL_紀錄失敗不外拋() {
		MailSender sender = mock(MailSender.class);
		NotificationService n = new NotificationService(fx.permDao, fx.acctReqDao, fx.codeService, fx.notifyLogDao, sender,
				fx.clock);
		Recipient a = rcpt("0000001", "a@example.com");
		Recipient dup = rcpt("0000001", "a@example.com");
		Recipient noMail = rcpt("0000002", " ");
		Recipient bad = rcpt("0000003", "bad@example.com");
		doThrow(new IllegalStateException("smtp")).when(sender).send(eq("bad@example.com"), anyString(), anyString());
		int written = n.sendAll("SUBMIT", "REQ", "AR20261007-001", List.of(a, dup, noMail, bad), "主旨", "本文", KEVIN);
		assertThat(written).isEqualTo(3);
		verify(sender, times(1)).send(eq("a@example.com"), any(), any());
		verify(fx.notifyLogDao).insert(eq("SUBMIT"), eq("REQ"), eq("AR20261007-001"), eq("0000001"), eq("a@example.com"),
				eq("SUCCESS"), isNull(), any(), eq(KEVIN));
		verify(fx.notifyLogDao).insert(eq("SUBMIT"), eq("REQ"), eq("AR20261007-001"), eq("0000002"), isNull(), eq("SKIP"),
				isNull(), any(), eq(KEVIN));
		verify(fx.notifyLogDao).insert(eq("SUBMIT"), eq("REQ"), eq("AR20261007-001"), eq("0000003"), eq("bad@example.com"),
				eq("FAIL"), eq("IllegalStateException"), any(), eq(KEVIN));

		doThrow(new IllegalStateException("db")).when(fx.notifyLogDao).insert(any(), any(), any(), any(), any(), any(), any(),
				any(), any());
		assertThat(n.sendAll("SUBMIT", "REQ", "X", List.of(a), "s", "b", KEVIN)).isZero();
	}

	@Test
	void 通知_到期收件人含申請人執行人角色成員_參照鍵與主旨() {
		MailSender sender = mock(MailSender.class);
		NotificationService n = new NotificationService(fx.permDao, fx.acctReqDao, fx.codeService, fx.notifyLogDao, sender,
				fx.clock);
		Acct a = fx.acct("AR20261001-001", "DB", AcctConst.AS_EXPIRED);
		a.setExpiryDate(LocalDate.of(2026, 10, 6).atStartOfDay());
		when(fx.permDao.findActiveUser(KEVIN)).thenReturn(rcpt(KEVIN, "k@example.com"));
		when(fx.permDao.findExecUsers("DB")).thenReturn(List.of(rcpt("0000004", "t@example.com"), rcpt(KEVIN, "k@example.com")));
		when(fx.permDao.findUsersInRoles(List.of(AcctConst.MAIL_ROLE_EXPIRY))).thenReturn(List.of(rcpt("0000003", "c@example.com")));
		assertThat(n.expiryEvent(AcctConst.EV_EXPIRED, a, TODAY)).isEqualTo(3);
		verify(fx.permDao).findUsersInRoles(List.of("GOV"));
		verify(sender).send(eq("k@example.com"), eq("【帳號到期】acct_AR20261001-001 已逾期"), any());
		verify(fx.notifyLogDao, times(3)).insert(eq("EXPIRED"), eq("ACCT"), eq("AR20261001-001_20261006"), any(), any(), any(),
				any(), any(), eq(AcctConst.SYSTEM_USER));
		assertThat(NotificationService.acctRefId("X", LocalDate.of(2026, 1, 2))).isEqualTo("X_20260102");
		assertThat(NotificationService.acctNameText("DOOR", null)).isEqualTo("（門禁卡，待發卡）");
		assertThat(NotificationService.expiredBlock(List.of(), TODAY)).contains("目前沒有已逾期未處理的帳號");
	}

	@Test
	void 通知_每週彙總與排程失敗收件人為角色成員_角色碼為常數() {
		MailSender sender = mock(MailSender.class);
		NotificationService n = new NotificationService(fx.permDao, fx.acctReqDao, fx.codeService, fx.notifyLogDao, sender,
				fx.clock);
		when(fx.permDao.findUsersInRoles(List.of("GOV")))
				.thenReturn(List.of(rcpt("0000003", "c@example.com"), rcpt("0000005", null)));
		when(fx.permDao.findUsersInRoles(List.of("SYS_ADMIN"))).thenReturn(List.of(rcpt("0000009", "s@example.com")));

		assertThat(n.weeklySummary("20261005", List.of(), TODAY)).isEqualTo(2);
		verify(sender).send(eq("c@example.com"), eq("【帳號到期】本週已逾期未處理帳號彙總（0 筆）"), anyString());
		verify(fx.notifyLogDao).insert(eq("WEEKLY_SUMMARY"), eq("WEEK"), eq("20261005"), eq("0000005"), isNull(), eq("SKIP"),
				isNull(), any(), eq(AcctConst.SYSTEM_USER));

		n.jobFail("J01", 77L, AcctConst.JR_PARTIAL, "x");
		verify(sender).send(eq("s@example.com"), eq("【排程失敗】J01 執行部分失敗"), anyString());
		verify(fx.notifyLogDao).insert(eq("JOB_FAIL"), eq("JOB"), eq("77"), eq("0000009"), eq("s@example.com"), eq("SUCCESS"),
				isNull(), any(), eq(AcctConst.SYSTEM_USER));
		verify(fx.permDao).findUsersInRoles(List.of("GOV"));
		verify(fx.permDao).findUsersInRoles(List.of("SYS_ADMIN"));

		// 不再依賴 properties：建構子無字串參數
		assertThat(NotificationService.class.getConstructors()).allSatisfy(
				c -> assertThat(c.getParameterTypes()).doesNotContain(String.class));
	}

	@Test
	void 通知_業務事件例外不外拋() {
		NotificationService n = new NotificationService(fx.permDao, fx.acctReqDao, fx.codeService, fx.notifyLogDao,
				mock(MailSender.class), fx.clock);
		when(fx.acctReqDao.findById("AR")).thenThrow(new IllegalStateException("db"));
		n.requestEvent(AcctConst.EV_SUBMIT, "AR", KEVIN);
		assertThat(fx.txm.rollbacks).isZero();
	}

	@Test
	void 排程時間與開關有預設值() throws Exception {
		var m = JobService.class.getMethod("scheduledJ01");
		var s = m.getAnnotation(org.springframework.scheduling.annotation.Scheduled.class);
		assertThat(s.cron()).isEqualTo("${im.acct.insp.job.j01.cron:0 10 0 * * *}");
		assertThat(s.zone()).isEqualTo("Asia/Taipei");
		var s2 = JobService.class.getMethod("scheduledJ02").getAnnotation(org.springframework.scheduling.annotation.Scheduled.class);
		assertThat(s2.cron()).isEqualTo("${im.acct.insp.job.j02.cron:0 0 8 * * *}");
		assertThat(new ApiException(HttpStatus.CONFLICT, AcctMsg.E16).getStatus()).isEqualTo(HttpStatus.CONFLICT);
	}

	static Recipient rcpt(String id, String email) {
		Recipient r = new Recipient();
		r.setUserId(id);
		r.setEmail(email);
		return r;
	}
}
