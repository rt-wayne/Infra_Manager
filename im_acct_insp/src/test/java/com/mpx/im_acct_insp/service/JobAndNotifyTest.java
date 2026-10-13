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
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-11
// 變更說明: BACKLOG 第 25 項：寄送名單——名單外記 SKIP「不在寄送名單」且不存 Email、無 Email 照舊（ERR_MSG 空）、
//           名單留空誰都不寄、寄給所有人忽略名單；既有通知測試改傳「寄給所有人」
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-11
// 變更說明: BACKLOG 第 27 項：NotificationService 建構子加 MailHtml；新增 HTML 本文測試——值（含主旨裡的帳號名稱、
//           原因）一律跳脫、換行轉 <br>、連結依事件（待審核類→待辦簽核、退件→申請單列表、到期／週彙總／排程失敗→首頁）、
//           七種申請單事件逐一驗連結、複驗退回原因跳脫換行；頁尾含發送時間；
//           系統網址留空、非 http(s) 開頭或無主機不放連結，去 #／結尾 index.html／斜線，href 也跳脫；
//           兩欄表與多欄表每個欄位自帶字型與字級
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-11
// 變更說明: BACKLOG 第 7 項：寫結束紀錄失敗（排程成功／部分失敗）仍寄 JOB_FAIL 並註明；寫 RUNNING 或取號失敗不跑、寄信、例外照拋
//           （排程觸發不外拋）；開跑前補記舊 RUNNING 為 FAIL（先查寄過沒再改狀態）、已寄過不重寄、補記失敗照常執行；
//           jobFail 沒有執行序號時參照鍵 0、主旨依結果碼（SUCCESS＝結束紀錄寫入失敗）
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S2：NotificationService 建構子加 EmgDao；新增緊急存取送出通知測試——收件人為 EMG_DECIDE
//           持有人、主旨含補核准期限、本文值跳脫與換行、連結到緊急存取頁、通知紀錄事件 EMG_SUBMIT／參照 EMG、
//           查無單號不寄、未知事件與 DAO 例外不外拋
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S4b-2：新增緊急存取退回通知測試——只寄原申請人、主旨「已退回」、本文含送出信 8 列與
//           處理人（有效身分姓名）／退回原因／回報結果（有異動帶回報說明、無異動、未回報、不明代碼）、信末提醒句、值跳脫、
//           連結到緊急存取頁、通知紀錄事件 EMG_RETURN；申請人無 Email 記 SKIP 不寄
// ============================================================

import static com.mpx.im_acct_insp.service.RequestServiceTest.assertStatus;
import static com.mpx.im_acct_insp.support.TestFixture.GARY;
import static com.mpx.im_acct_insp.support.TestFixture.HANK;
import static com.mpx.im_acct_insp.support.TestFixture.KEVIN;
import static com.mpx.im_acct_insp.support.TestFixture.NOW;
import static com.mpx.im_acct_insp.support.TestFixture.TODAY;
import static com.mpx.im_acct_insp.support.TestFixture.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;

import com.mpx.im_acct_insp.common.AcctConst;
import com.mpx.im_acct_insp.common.AcctMsg;
import com.mpx.im_acct_insp.common.ApiException;
import com.mpx.im_acct_insp.model.Acct;
import com.mpx.im_acct_insp.model.AcctReqDetail;
import com.mpx.im_acct_insp.model.EmgDetail;
import com.mpx.im_acct_insp.model.JobRunResult;
import com.mpx.im_acct_insp.model.Recipient;
import com.mpx.im_acct_insp.support.TestFixture;

class JobAndNotifyTest {

	/** 寄給所有收件人（既有通知測試不受寄送名單影響） */
	static final MailAllowList SEND_ALL = new MailAllowList("", true);
	/** 信末連結的系統網址（尾端斜線會被去掉） */
	static final String SITE = "http://h/ctx/";
	static final String FOOTER = "本信件由「機房與帳號權限管理系統」自動發送，請勿直接回覆。發送時間：2026-10-07 10:30";
	/** 沒有自帶字型與字級的表格欄位（Outlook 表格不繼承外層樣式） */
	static final String CELL_WITHOUT_FONT = "<t[dh](?![^>]*font-family[^>]*font-size)";

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
		verify(fx.notificationService, never()).jobFail(any(), any(), any(), any());
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
	void 結束紀錄寫入失敗_排程成功_照常回結果並寄排程失敗信() {
		doThrow(new IllegalStateException("db")).when(fx.jobRunDao).finish(anyLong(), any(), anyInt(), any(), any(), any());
		JobRunResult r = job.runManual(id(HANK), "J01", null);
		assertThat(r.getRunId()).isEqualTo(77L);
		assertThat(r.getResultCode()).isEqualTo(AcctConst.JR_SUCCESS);
		verify(fx.notificationService).jobFail("J01", 77L, "SUCCESS", "結束紀錄寫入失敗（IllegalStateException）");
	}

	@Test
	void 結束紀錄寫入失敗_排程部分失敗_錯誤摘要兩者都有() {
		Acct a = fx.acct("AR20261001-001", "DB", AcctConst.AS_APPROVED);
		when(fx.acctDao.findToExpire(TODAY)).thenReturn(List.of(a));
		doThrow(new IllegalStateException("lock")).when(fx.acctDao).expire(any(), any(), any(), any());
		doThrow(new IllegalStateException("db")).when(fx.jobRunDao).finish(anyLong(), any(), anyInt(), any(), any(), any());
		assertThat(job.runManual(id(HANK), "J01", null).getResultCode()).isEqualTo(AcctConst.JR_PARTIAL);
		verify(fx.notificationService).jobFail("J01", 77L, "PARTIAL",
				"結束紀錄寫入失敗（IllegalStateException）; AR20261001-001:IllegalStateException");
	}

	@Test
	void 開始紀錄寫入失敗_不執行_寄排程失敗信_例外照拋() {
		doThrow(new IllegalStateException("db")).when(fx.jobRunDao).insertRunning(anyLong(), any(), any(), any(), any(), any());
		assertThatThrownBy(() -> job.runManual(id(HANK), "J01", null)).isInstanceOf(IllegalStateException.class);
		verify(fx.acctDao, never()).findToExpire(any());
		verify(fx.jobRunDao, never()).finish(anyLong(), any(), anyInt(), any(), any(), any());
		verify(fx.notificationService).jobFail("J01", 77L, "FAIL", "開始紀錄寫入失敗，排程未執行（IllegalStateException）");
		// 鎖已釋放：下次可再跑
		doReturn(1).when(fx.jobRunDao).insertRunning(anyLong(), any(), any(), any(), any(), any());
		assertThat(job.runManual(id(HANK), "J01", null).getResultCode()).isEqualTo(AcctConst.JR_SUCCESS);
	}

	@Test
	void 取號失敗_不執行_執行序號為空_排程觸發不外拋() {
		when(fx.jobRunDao.nextId()).thenThrow(new IllegalStateException("seq"));
		job.scheduledJ02();
		verify(fx.jobRunDao, never()).insertRunning(anyLong(), any(), any(), any(), any(), any());
		verify(fx.notifyLogDao, never()).countByEventRef(eq(AcctConst.EV_WEEKLY_SUMMARY), any(), any());
		verify(fx.notificationService).jobFail(eq("J02"), isNull(), eq("FAIL"), eq("開始紀錄寫入失敗，排程未執行（IllegalStateException）"));
	}

	@Test
	void 開始紀錄寫入失敗_排程觸發不外拋() {
		doThrow(new IllegalStateException("db")).when(fx.jobRunDao).insertRunning(anyLong(), any(), any(), any(), any(), any());
		job.scheduledJ01();
		verify(fx.acctDao, never()).findToExpire(any());
		verify(fx.notificationService).jobFail("J01", 77L, "FAIL", "開始紀錄寫入失敗，排程未執行（IllegalStateException）");
	}

	@Test
	void 開跑前補記舊RUNNING為FAIL_已寄過不重寄_已非RUNNING略過() {
		when(fx.jobRunDao.findRunningIds("J01")).thenReturn(List.of(5L, 6L, 7L));
		when(fx.jobRunDao.markStaleFail(eq(5L), any(), any(), any())).thenReturn(1);
		when(fx.jobRunDao.markStaleFail(eq(6L), any(), any(), any())).thenReturn(1);
		when(fx.jobRunDao.markStaleFail(eq(7L), any(), any(), any())).thenReturn(0);
		when(fx.notifyLogDao.countByEventRef(AcctConst.EV_JOB_FAIL, AcctConst.REF_JOB, "6")).thenReturn(1L);
		assertThat(job.runManual(id(HANK), "J01", null).getResultCode()).isEqualTo(AcctConst.JR_SUCCESS);
		InOrder order = inOrder(fx.notifyLogDao, fx.jobRunDao, fx.notificationService);
		order.verify(fx.notifyLogDao).countByEventRef(AcctConst.EV_JOB_FAIL, AcctConst.REF_JOB, "5");
		order.verify(fx.jobRunDao).markStaleFail(eq(5L), eq("上次執行未正常結束（下次開跑時補記）"), any(), eq(HANK));
		order.verify(fx.notificationService).jobFail("J01", 5L, "FAIL", "上次執行未正常結束（下次開跑時補記）");
		order.verify(fx.jobRunDao).insertRunning(eq(77L), eq("J01"), any(), any(), any(), any());
		// 已寄過的仍要改 FAIL，只是不重寄
		verify(fx.jobRunDao).markStaleFail(eq(6L), any(), any(), any());
		verify(fx.notificationService, never()).jobFail(eq("J01"), eq(6L), any(), any());
		verify(fx.notificationService, never()).jobFail(eq("J01"), eq(7L), any(), any());
		verify(fx.jobRunDao, never()).findRunningIds("J02");
	}

	@Test
	void 補記失敗只記log_照常執行() {
		when(fx.jobRunDao.findRunningIds("J01")).thenReturn(List.of(5L, 6L));
		when(fx.jobRunDao.markStaleFail(eq(5L), any(), any(), any())).thenThrow(new IllegalStateException("db"));
		when(fx.jobRunDao.markStaleFail(eq(6L), any(), any(), any())).thenReturn(1);
		assertThat(job.runManual(id(HANK), "J01", null).getResultCode()).isEqualTo(AcctConst.JR_SUCCESS);
		verify(fx.notificationService).jobFail("J01", 6L, "FAIL", "上次執行未正常結束（下次開跑時補記）");

		when(fx.jobRunDao.findRunningIds("J02")).thenThrow(new IllegalStateException("db"));
		assertThat(job.runManual(id(HANK), "J02", null).getResultCode()).isEqualTo(AcctConst.JR_SUCCESS);
		verify(fx.jobRunDao).insertRunning(anyLong(), eq("J02"), any(), any(), any(), any());
	}

	@Test
	void 補記時查寄信紀錄失敗_不改狀態不寄信_照常執行() {
		when(fx.jobRunDao.findRunningIds("J01")).thenReturn(List.of(5L));
		when(fx.notifyLogDao.countByEventRef(AcctConst.EV_JOB_FAIL, AcctConst.REF_JOB, "5"))
				.thenThrow(new IllegalStateException("db"));
		assertThat(job.runManual(id(HANK), "J01", null).getResultCode()).isEqualTo(AcctConst.JR_SUCCESS);
		// 仍停在 RUNNING，下次開跑會重試
		verify(fx.jobRunDao, never()).markStaleFail(anyLong(), any(), any(), any());
		verify(fx.notificationService, never()).jobFail(eq("J01"), eq(5L), any(), any());
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
		NotificationService n = notifier(sender, SEND_ALL);
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
		NotificationService n = notifier(sender, SEND_ALL);
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
		NotificationService n = notifier(sender, SEND_ALL);
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

		// 只有結束紀錄寫入失敗（結果碼 SUCCESS）
		n.jobFail("J01", 78L, AcctConst.JR_SUCCESS, "結束紀錄寫入失敗（X）");
		verify(sender).send(eq("s@example.com"), eq("【排程失敗】J01 結束紀錄寫入失敗"), anyString());
		// 沒取到執行序號：參照鍵 0、信中顯示（未取得）
		ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
		n.jobFail("J02", null, AcctConst.JR_FAIL, "開始紀錄寫入失敗");
		verify(sender).send(eq("s@example.com"), eq("【排程失敗】J02 執行失敗"), body.capture());
		assertThat(body.getValue()).contains("（未取得）");
		verify(fx.notifyLogDao).insert(eq("JOB_FAIL"), eq("JOB"), eq("0"), eq("0000009"), any(), any(), any(), any(), any());

		// 不再依賴 properties：建構子無字串參數
		assertThat(NotificationService.class.getConstructors()).allSatisfy(
				c -> assertThat(c.getParameterTypes()).doesNotContain(String.class));
	}

	@Test
	void 通知_業務事件例外不外拋() {
		NotificationService n = notifier(mock(MailSender.class), SEND_ALL);
		when(fx.acctReqDao.findById("AR")).thenThrow(new IllegalStateException("db"));
		n.requestEvent(AcctConst.EV_SUBMIT, "AR", KEVIN);
		assertThat(fx.txm.rollbacks).isZero();
	}

	@Test
	void 通知_寄送名單外記SKIP不在寄送名單_不存Email_無Email仍照舊記SKIP() {
		MailSender sender = mock(MailSender.class);
		NotificationService n = notifier(sender, new MailAllowList(" 0000001 , ,0000002", false));
		Recipient listed = rcpt("0000001", "a@example.com");
		Recipient listedNoMail = rcpt("0000002", null);
		Recipient outside = rcpt("0000003", "c@example.com");
		Recipient outsideNoMail = rcpt("0000004", "");
		assertThat(n.sendAll("SUBMIT", "REQ", "R1", List.of(listed, listedNoMail, outside, outsideNoMail), "主旨", "本文", KEVIN))
				.isEqualTo(4);
		verify(sender, times(1)).send(any(), any(), any());
		verify(sender).send(eq("a@example.com"), eq("主旨"), eq("本文"));
		verify(fx.notifyLogDao).insert(eq("SUBMIT"), eq("REQ"), eq("R1"), eq("0000001"), eq("a@example.com"), eq("SUCCESS"),
				isNull(), any(), eq(KEVIN));
		verify(fx.notifyLogDao).insert(eq("SUBMIT"), eq("REQ"), eq("R1"), eq("0000002"), isNull(), eq("SKIP"), isNull(), any(),
				eq(KEVIN));
		verify(fx.notifyLogDao).insert(eq("SUBMIT"), eq("REQ"), eq("R1"), eq("0000003"), isNull(), eq("SKIP"), eq("不在寄送名單"),
				any(), eq(KEVIN));
		verify(fx.notifyLogDao).insert(eq("SUBMIT"), eq("REQ"), eq("R1"), eq("0000004"), isNull(), eq("SKIP"), isNull(), any(),
				eq(KEVIN));
	}

	@Test
	void 寄送名單_留空誰都不寄_寄給所有人忽略名單() {
		MailAllowList empty = new MailAllowList("", false);
		assertThat(empty.allows("0000001")).isFalse();
		assertThat(empty.size()).isZero();
		assertThat(new MailAllowList(null, false).allows("0000001")).isFalse();
		MailAllowList listed = new MailAllowList("2410305", false);
		assertThat(listed.allows("2410305")).isTrue();
		assertThat(listed.allows("2410306")).isFalse();
		assertThat(listed.allows(null)).isFalse();
		assertThat(new MailAllowList("", true).allows("0000001")).isTrue();
		assertThat(new MailAllowList("2410305", true).allows("0000001")).isTrue();
		// 全形逗號也當分隔符
		MailAllowList fullWidth = new MailAllowList("2410305，0000001", false);
		assertThat(fullWidth.size()).isEqualTo(2);
		assertThat(fullWidth.allows("2410305")).isTrue();
		assertThat(fullWidth.allows("0000001")).isTrue();
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

	// ------------------------------------------------------------
	// 通知信 HTML（BACKLOG 第 27 項）
	// ------------------------------------------------------------

	@Test
	void HTML_申請單值跳脫_送審連待辦_退件原因換行轉br且連申請單列表() {
		MailSender sender = mock(MailSender.class);
		NotificationService n = notifier(sender, SEND_ALL);
		AcctReqDetail r = htmlReq();
		when(fx.permDao.findUsersWithPerm(anyString())).thenReturn(List.of(rcpt("0000001", "a@example.com")));

		n.requestEvent(AcctConst.EV_SUBMIT, "AR20261011-001", KEVIN);
		ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
		verify(sender).send(eq("a@example.com"), anyString(), body.capture());
		assertThat(body.getValue()).startsWith("<!DOCTYPE html>")
				.contains("HTML測試&lt;b&gt;粗體&lt;/b&gt;")
				.contains("凱文 &amp; &quot;&lt;i&gt;&quot;")
				.doesNotContain("<b>", "<i>")
				.contains("<a href=\"http://h/ctx/#/todo\"", "前往「待辦簽核」", FOOTER);

		r.setReqStateCode("REJECTED");
		r.setCurrStageId(null);
		r.setRejectReason("第一行\r\n<script>x</script>");
		fx.req = r;
		when(fx.permDao.findActiveUser(KEVIN)).thenReturn(rcpt(KEVIN, "k@example.com"));
		n.requestEvent(AcctConst.EV_REJECT, "AR20261011-001", KEVIN);
		ArgumentCaptor<String> rej = ArgumentCaptor.forClass(String.class);
		verify(sender).send(eq("k@example.com"), eq("【帳號申請】AR20261011-001 已退件"), rej.capture());
		assertThat(rej.getValue()).contains("第一行<br>&lt;script&gt;x&lt;/script&gt;")
				.doesNotContain("<script>")
				.contains("<a href=\"http://h/ctx/#/requests\"", "前往「申請單列表」", "退件原因");
	}

	@Test
	void HTML_七種申請單事件連結_退件連申請單列表其餘連待辦_複驗退回原因跳脫換行() {
		AcctReqDetail r = htmlReq();
		r.setCurrStageId(3);
		r.setExecBy("0000004");
		r.setVerifyRejectReason("第一行\n<i>重做</i>");
		Recipient a = rcpt("0000001", "a@example.com");
		when(fx.permDao.findUsersWithPerm(anyString())).thenReturn(List.of(a));
		when(fx.permDao.findExecUsers(any())).thenReturn(List.of(a));
		when(fx.permDao.findActiveUser(anyString())).thenReturn(a);
		Map<String, String> paths = new LinkedHashMap<>();
		paths.put(AcctConst.EV_SUBMIT, "#/todo");
		paths.put(AcctConst.EV_RESUB, "#/todo");
		paths.put(AcctConst.EV_NEXT_STAGE, "#/todo");
		paths.put(AcctConst.EV_TO_EXEC, "#/todo");
		paths.put(AcctConst.EV_EXEC_DONE, "#/todo");
		paths.put(AcctConst.EV_VERIFY_REJECT, "#/todo");
		paths.put(AcctConst.EV_REJECT, "#/requests");

		for (Map.Entry<String, String> e : paths.entrySet()) {
			MailSender sender = mock(MailSender.class);
			notifier(sender, SEND_ALL).requestEvent(e.getKey(), r.getReqId(), KEVIN);
			ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
			verify(sender).send(eq("a@example.com"), anyString(), body.capture());
			assertThat(body.getValue()).as(e.getKey()).contains("<a href=\"http://h/ctx/" + e.getValue() + "\"");
			if (AcctConst.EV_VERIFY_REJECT.equals(e.getKey())) {
				assertThat(body.getValue()).contains("退回原因", "第一行<br>&lt;i&gt;重做&lt;/i&gt;").doesNotContain("<i>");
			}
		}
	}

	@Test
	void HTML_到期信標題跳脫帳號名稱_週彙總多欄表_排程失敗_都連首頁() {
		MailSender sender = mock(MailSender.class);
		NotificationService n = notifier(sender, SEND_ALL);
		Acct a = fx.acct("AR20261001-001", "DB", AcctConst.AS_EXPIRED);
		a.setAcctName("<b>x</b>");
		a.setExpiryDate(LocalDate.of(2026, 10, 6).atStartOfDay());
		when(fx.permDao.findUsersInRoles(any())).thenReturn(List.of(rcpt("0000003", "c@example.com")));
		ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);

		n.expiryEvent(AcctConst.EV_EXPIRED, a, TODAY);
		verify(sender).send(eq("c@example.com"), eq("【帳號到期】<b>x</b> 已逾期"), body.capture());
		assertThat(body.getValue()).contains("<title>【帳號到期】&lt;b&gt;x&lt;/b&gt; 已逾期</title>")
				.contains(">【帳號到期】&lt;b&gt;x&lt;/b&gt; 已逾期</h2>")
				.doesNotContain("<b>")
				.contains("<a href=\"http://h/ctx/#/\"", "前往「機房與帳號權限管理系統」", "逾期天數", FOOTER)
				.doesNotContainPattern(CELL_WITHOUT_FONT);

		n.weeklySummary("20261005", List.of(a), TODAY);
		verify(sender).send(eq("c@example.com"), eq("【帳號到期】本週已逾期未處理帳號彙總（1 筆）"), body.capture());
		assertThat(body.getValue()).contains("■ 已逾期未處理帳號", "<th", ">單號</th>", ">逾期天數</th>",
				">AR20261001-001</td>", ">&lt;b&gt;x&lt;/b&gt;</td>", ">2026-10-06</td>", ">1</td>", "<a href=\"http://h/ctx/#/\"")
				.doesNotContain("<b>")
				.doesNotContainPattern(CELL_WITHOUT_FONT);

		n.jobFail("J01", 77L, AcctConst.JR_FAIL, "Illegal<State>");
		verify(sender).send(eq("c@example.com"), eq("【排程失敗】J01 執行失敗"), body.capture());
		assertThat(body.getValue()).contains("Illegal&lt;State&gt;", "<a href=\"http://h/ctx/#/\"", FOOTER)
				.doesNotContainPattern(CELL_WITHOUT_FONT);
	}

	@Test
	void HTML_系統網址留空非http開頭或無主機不放連結_去井號indexhtml尾端斜線_href跳脫() {
		assertThat(MailHtml.normalize(null)).isEmpty();
		assertThat(MailHtml.normalize("  ")).isEmpty();
		assertThat(MailHtml.normalize("#/")).isEmpty();
		assertThat(MailHtml.normalize("javascript:alert(1)")).isEmpty();
		assertThat(MailHtml.normalize("ftp://h/x")).isEmpty();
		assertThat(MailHtml.normalize("localhost:8787/ctx")).isEmpty();
		assertThat(MailHtml.normalize("http://")).isEmpty();
		assertThat(MailHtml.normalize("http:///ctx")).isEmpty();
		assertThat(MailHtml.normalize("http://h/\"onmouseover=x")).isEmpty();
		assertThat(MailHtml.normalize("http://h/my app/")).isEmpty();
		assertThat(MailHtml.normalize(" https://h/ctx// ")).isEqualTo("https://h/ctx");
		assertThat(MailHtml.normalize("HTTP://H/ctx")).isEqualTo("HTTP://H/ctx");
		// 從瀏覽器網址列整串貼上
		assertThat(MailHtml.normalize("http://h:8787/im_acct_insp_web/#/")).isEqualTo("http://h:8787/im_acct_insp_web");
		assertThat(MailHtml.normalize("http://h/ctx/#/requests?x=1")).isEqualTo("http://h/ctx");
		assertThat(MailHtml.normalize("http://h/ctx/index.html")).isEqualTo("http://h/ctx");
		assertThat(MailHtml.normalize("http://h/ctx/INDEX.HTML#/todo")).isEqualTo("http://h/ctx");
		assertThat(MailHtml.normalize("http://h/ctx/ #/")).isEqualTo("http://h/ctx");
		assertThat(MailHtml.normalize("http://h/ctx/index.html/")).isEqualTo("http://h/ctx");
		// 主機名剛好叫 index.html 時不去掉
		assertThat(MailHtml.normalize("http://index.html")).isEqualTo("http://index.html");
		// 主機名含底線仍接受
		assertThat(MailHtml.normalize("http://my_host:8787/ctx/")).isEqualTo("http://my_host:8787/ctx");

		for (String site : new String[] { "", "javascript:alert(1)", "http://" }) {
			assertThat(new MailHtml(site, fx.clock).page("t", "", MailHtml.PATH_TODO)).doesNotContain("<a ").contains(FOOTER);
		}
		assertThat(new MailHtml(SITE, fx.clock).page("t", "", null)).doesNotContain("<a ");
		assertThat(new MailHtml("http://h/R&D/", fx.clock).page("t", "", MailHtml.PATH_HOME))
				.contains("href=\"http://h/R&amp;D/#/\"");
		assertThat(MailHtml.para("a\rb\nc")).isEqualTo("<p>a<br>b<br>c</p>");
		assertThat(MailHtml.esc(null)).isEmpty();
	}

	// ------------------------------------------------------------
	// 緊急存取通知（BACKLOG 第 46 項 S2）
	// ------------------------------------------------------------

	@Test
	void 緊急存取_送出通知寄給補核准權限持有人_主旨含期限_本文值跳脫連緊急存取_查無不寄_未知事件不外拋() {
		MailSender sender = mock(MailSender.class);
		NotificationService n = notifier(sender, SEND_ALL);
		EmgDetail e = new EmgDetail();
		e.setEmgId("EA20261007-001");
		e.setCatgId("DB");
		e.setCatgName("資料庫");
		e.setTargetDescr("prod-db <root>");
		e.setApplyBy(KEVIN);
		e.setApplyByName("凱文 & 測試");
		e.setApplyReason("第一行\n<i>斜體</i>");
		e.setExpectHourQty(4);
		e.setSubmitDate(NOW);
		e.setDueDate(NOW.plusHours(24));
		when(fx.emgDao.findById(eq("EA20261007-001"), any())).thenReturn(e);
		when(fx.permDao.findUsersWithPerm(AcctConst.PERM_EMG_DECIDE))
				.thenReturn(List.of(rcpt("0000004", "t@example.com"), rcpt("0000005", null)));

		n.emgEvent(AcctConst.EV_EMG_SUBMIT, "EA20261007-001", KEVIN);
		ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
		verify(sender).send(eq("t@example.com"),
				eq("【緊急存取】EA20261007-001 已送出，待您補核准（期限 2026-10-08 10:30）"), body.capture());
		assertThat(body.getValue()).startsWith("<!DOCTYPE html>")
				.contains("資料庫", "prod-db &lt;root&gt;", "凱文 &amp; 測試", "第一行<br>&lt;i&gt;斜體&lt;/i&gt;", "4 小時",
						"2026-10-07 10:30", "2026-10-08 10:30")
				.doesNotContain("<root>", "<i>")
				.contains("<a href=\"http://h/ctx/#/emergency\"", "前往「緊急存取」", FOOTER);
		// 通知紀錄：事件 EMG_SUBMIT、參照類型 EMG、CREATE_BY 為實際操作人；無 Email 記 SKIP
		verify(fx.notifyLogDao).insert(eq("EMG_SUBMIT"), eq("EMG"), eq("EA20261007-001"), eq("0000004"), eq("t@example.com"),
				eq("SUCCESS"), isNull(), any(), eq(KEVIN));
		verify(fx.notifyLogDao).insert(eq("EMG_SUBMIT"), eq("EMG"), eq("EA20261007-001"), eq("0000005"), isNull(), eq("SKIP"),
				isNull(), any(), eq(KEVIN));
		verify(fx.permDao).findUsersWithPerm("EMG_DECIDE");

		// 查無單號：不寄、不記
		n.emgEvent(AcctConst.EV_EMG_SUBMIT, "EA-none", KEVIN);
		// 未知事件：記 log、不外拋、不寄
		n.emgEvent("EMG_WHAT", "EA20261007-001", KEVIN);
		// DAO 例外：不外拋
		doThrow(new IllegalStateException("db")).when(fx.emgDao).findById(eq("EA-err"), any());
		n.emgEvent(AcctConst.EV_EMG_SUBMIT, "EA-err", KEVIN);
		verify(sender, times(1)).send(any(), any(), any());
		verify(fx.notifyLogDao, times(2)).insert(any(), any(), any(), any(), any(), any(), any(), any(), any());
	}

	@Test
	void 緊急存取_退回通知只寄申請人_主旨已退回_本文含處理人退回原因回報結果與提醒_值跳脫_紀錄事件EMG_RETURN() {
		MailSender sender = mock(MailSender.class);
		NotificationService n = notifier(sender, SEND_ALL);
		EmgDetail e = returnedEmg("EA20261007-002");
		e.setReportTypeCode(AcctConst.EMG_REPORT_MODIFIED);
		e.setReportDescr("改了 <root> 密碼\n並新增帳號");
		when(fx.emgDao.findById(eq("EA20261007-002"), any())).thenReturn(e);
		when(fx.permDao.findActiveUser(KEVIN)).thenReturn(rcpt(KEVIN, "k@example.com"));

		n.emgEvent(AcctConst.EV_EMG_RETURN, "EA20261007-002", HANK);
		ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
		verify(sender).send(eq("k@example.com"), eq("【緊急存取】EA20261007-002 已退回"), body.capture());
		assertThat(body.getValue()).startsWith("<!DOCTYPE html>")
				// 送出信 8 列照舊
				.contains("資料庫", "prod-db &lt;root&gt;", "凱文 &amp; 測試", "第一行<br>&lt;i&gt;斜體&lt;/i&gt;", "4 小時",
						"2026-10-07 10:30", "2026-10-08 10:30", "補核准期限")
				// 處理人顯示有效身分姓名（代理時為被代理主管），不是工號
				.contains("處理人", "許 &lt;永成&gt;").doesNotContain("0000010")
				.contains("退回原因", "事由不符 &amp; 需補件")
				.contains("回報結果", "有異動：改了 &lt;root&gt; 密碼<br>並新增帳號")
				.contains("<p>後續由主管異常結案，若有帳號異動請告知處理主管</p>")
				.doesNotContain("<root>")
				.contains("<a href=\"http://h/ctx/#/emergency\"", "前往「緊急存取」", FOOTER);
		// 只查申請人、不查補核准權限持有人；通知紀錄事件 EMG_RETURN、參照 EMG、CREATE_BY 為實際操作人
		verify(fx.permDao).findActiveUser(KEVIN);
		verify(fx.permDao, never()).findUsersWithPerm(any());
		verify(fx.notifyLogDao).insert(eq("EMG_RETURN"), eq("EMG"), eq("EA20261007-002"), eq(KEVIN), eq("k@example.com"),
				eq("SUCCESS"), isNull(), any(), eq(HANK));
		verify(fx.notifyLogDao, times(1)).insert(any(), any(), any(), any(), any(), any(), any(), any(), any());
	}

	@Test
	void 緊急存取_退回通知_回報結果三種文字與不明代碼_處理人無姓名顯示工號_申請人無Email記SKIP不寄() {
		// 無異動
		EmgDetail none = returnedEmg("EA20261007-003");
		none.setReportTypeCode(AcctConst.EMG_REPORT_NONE);
		none.setReportDescr("有填說明也不顯示");
		assertThat(NotificationService.reportResultText(none)).isEqualTo("無異動");
		// 有異動但說明空白
		none.setReportTypeCode(AcctConst.EMG_REPORT_MODIFIED);
		none.setReportDescr("  ");
		assertThat(NotificationService.reportResultText(none)).isEqualTo("有異動");
		// 代碼被手動改壞：不得寫成「無異動」
		none.setReportTypeCode("WHAT");
		assertThat(NotificationService.reportResultText(none)).isEqualTo("（不明：WHAT）");
		// 未回報即退回
		EmgDetail unreported = returnedEmg("EA20261007-004");
		assertThat(NotificationService.reportResultText(unreported)).isEqualTo("未回報");
		// 處理人沒有姓名快照時退回工號；退回原因空白顯示空字串、不出現 null
		unreported.setDecideByName(null);
		unreported.setReturnReason(null);
		assertThat(NotificationService.emgReturnBody(unreported)).contains("0000010", "未回報").doesNotContain("null");

		// 申請人無 Email：記 SKIP、不寄
		MailSender sender = mock(MailSender.class);
		NotificationService n = notifier(sender, SEND_ALL);
		when(fx.emgDao.findById(eq("EA20261007-004"), any())).thenReturn(unreported);
		when(fx.permDao.findActiveUser(KEVIN)).thenReturn(rcpt(KEVIN, null));
		n.emgEvent(AcctConst.EV_EMG_RETURN, "EA20261007-004", HANK);
		verify(sender, never()).send(any(), any(), any());
		verify(fx.notifyLogDao).insert(eq("EMG_RETURN"), eq("EMG"), eq("EA20261007-004"), eq(KEVIN), isNull(), eq("SKIP"),
				isNull(), any(), eq(HANK));
	}

	/** 已退回的緊急存取單（處理人 Gary、姓名與原因含 HTML 特殊字元；回報欄位由各測試自行設定） */
	static EmgDetail returnedEmg(String emgId) {
		EmgDetail e = new EmgDetail();
		e.setEmgId(emgId);
		e.setCatgId("DB");
		e.setCatgName("資料庫");
		e.setTargetDescr("prod-db <root>");
		e.setApplyBy(KEVIN);
		e.setApplyByName("凱文 & 測試");
		e.setApplyReason("第一行\n<i>斜體</i>");
		e.setExpectHourQty(4);
		e.setSubmitDate(NOW);
		e.setDueDate(NOW.plusHours(24));
		e.setDecideCode(AcctConst.EMG_DECIDE_RETURN);
		e.setDecideBy(GARY);
		e.setDecideByName("許 <永成>");
		e.setDecideDate(NOW.plusHours(2));
		e.setReturnReason("事由不符 & 需補件");
		return e;
	}

	/** 帳號名稱與申請人姓名含 HTML 特殊字元的申請單（目前關卡＝第一關），並放進模擬的申請單表 */
	AcctReqDetail htmlReq() {
		AcctReqDetail r = new AcctReqDetail();
		r.setReqId("AR20261011-001");
		r.setReqTypeCode(AcctConst.REQ_ADD);
		r.setCatgId("DB");
		r.setCatgName("資料庫");
		r.setAcctName("HTML測試<b>粗體</b>");
		r.setApplyBy(KEVIN);
		r.setApplyByName("凱文 & \"<i>\"");
		r.setCurrStageId(AcctConst.STAGE_FIRST_APPR);
		fx.req = r;
		return r;
	}

	NotificationService notifier(MailSender sender, MailAllowList allowList) {
		return new NotificationService(fx.permDao, fx.acctReqDao, fx.emgDao, fx.codeService, fx.notifyLogDao, sender, allowList,
				new MailHtml(SITE, fx.clock), fx.clock);
	}

	static Recipient rcpt(String id, String email) {
		Recipient r = new Recipient();
		r.setUserId(id);
		r.setEmail(email);
		return r;
	}
}
