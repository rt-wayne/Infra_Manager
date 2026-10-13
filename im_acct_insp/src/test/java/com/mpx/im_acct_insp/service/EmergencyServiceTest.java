package com.mpx.im_acct_insp.service;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: 新增：緊急存取送出與查詢單元測試（BACKLOG 第 46 項 S2；SA §5.11）：
//           EMG_APPLY 權限、必填 E10、欄寬 E11、預估時長 1～24 與類別不存在 BAD_CODE、取號 EA 與 DUE_DATE＝＋24 小時、
//           有效身分（APPLY_BY）與實際操作人（CREATE_BY／取號）、通知在交易提交後呼叫、列表資料範圍（EMG_VIEW_ALL 不限／否則本人）、
//           狀態篩選非法 400、關鍵字跳脫、明細查無或無權看一律 404
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S4a：回報測試——沒有異動／異動（子表 REPORT 列、有效身分與實際操作人）、上限 20、逾期後仍可、
//           404／非本人 403、欄位驗證順序、帳號單號＋名稱＋狀態不符同一句、已回報／已處理／版本不符／UPDATE 0 筆皆 409
// ============================================================

import static com.mpx.im_acct_insp.service.RequestServiceTest.assertStatus;
import static com.mpx.im_acct_insp.support.TestFixture.HANK;
import static com.mpx.im_acct_insp.support.TestFixture.KEVIN;
import static com.mpx.im_acct_insp.support.TestFixture.MICHAEL;
import static com.mpx.im_acct_insp.support.TestFixture.NAMES;
import static com.mpx.im_acct_insp.support.TestFixture.NOW;
import static com.mpx.im_acct_insp.support.TestFixture.TERRY;
import static com.mpx.im_acct_insp.support.TestFixture.TODAY;
import static com.mpx.im_acct_insp.support.TestFixture.id;
import static com.mpx.im_acct_insp.support.TestFixture.idWith;
import static com.mpx.im_acct_insp.support.TestFixture.impersonate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;

import com.mpx.im_acct_insp.common.AcctConst;
import com.mpx.im_acct_insp.common.AcctMsg;
import com.mpx.im_acct_insp.common.Identity;
import com.mpx.im_acct_insp.model.Emg;
import com.mpx.im_acct_insp.model.EmgActionResult;
import com.mpx.im_acct_insp.model.EmgDetail;
import com.mpx.im_acct_insp.model.request.EmgForm;
import com.mpx.im_acct_insp.model.request.EmgReportForm;
import com.mpx.im_acct_insp.support.TestFixture;

class EmergencyServiceTest {

	static final String EMG_ID = "EA20261007-001";

	TestFixture fx;
	EmergencyService svc;

	@BeforeEach
	void setUp() {
		fx = new TestFixture();
		svc = fx.emergencyService;
		when(fx.emgDao.insert(any(), anyString())).thenReturn(1);
	}

	@Test
	void 權限_無EMG_APPLY送出403_列表與明細登入即可() {
		Identity noPerm = new Identity("hash-x", MICHAEL, NAMES.get(MICHAEL), MICHAEL, NAMES.get(MICHAEL), false, Set.of(),
				List.of(), List.of(), null, null, null);
		assertStatus(() -> svc.create(noPerm, form("DB", "prod-db01", "主機當機需緊急登入", 4)), HttpStatus.FORBIDDEN, AcctMsg.P01);
		verify(fx.emgDao, never()).insert(any(), anyString());
		// 列表不需權限：無 EMG_VIEW_ALL 者只是範圍縮到本人
		assertThat(svc.list(noPerm, null, null, null, null)).isEmpty();
		verify(fx.emgDao).findList(eq(MICHAEL), isNull(), isNull(), isNull(), isNull(), eq(NOW));
	}

	@Test
	void 送出_取號EA_DUE_DATE加24小時_APPLY_BY有效身分_CREATE_BY實際操作人_交易提交後通知() {
		// 通知被呼叫的當下交易必須已提交（通知若被搬進 tx.execute 內，這裡的 commits 會是 0）
		doAnswer(inv -> {
			assertThat(fx.txm.commits).as("通知時交易已提交").isEqualTo(1);
			return null;
		}).when(fx.notificationService).emgEvent(anyString(), anyString(), anyString());
		assertThat(svc.create(id(MICHAEL), form("DB", "prod-db01", "主機當機需緊急登入", 4)).getEmgId()).isEqualTo(EMG_ID);

		ArgumentCaptor<Emg> cap = ArgumentCaptor.forClass(Emg.class);
		verify(fx.emgDao).insert(cap.capture(), eq(MICHAEL));
		Emg e = cap.getValue();
		assertThat(e.getEmgId()).isEqualTo(EMG_ID);
		assertThat(e.getCatgId()).isEqualTo("DB");
		assertThat(e.getTargetDescr()).isEqualTo("prod-db01");
		assertThat(e.getApplyBy()).isEqualTo(MICHAEL);
		assertThat(e.getApplyReason()).isEqualTo("主機當機需緊急登入");
		assertThat(e.getExpectHourQty()).isEqualTo(4);
		assertThat(e.getSubmitDate()).isEqualTo(NOW);
		assertThat(e.getDueDate()).isEqualTo(NOW.plusHours(24));
		assertThat(fx.txm.commits).isEqualTo(1);

		// 通知在交易提交後才呼叫（寫入在前、通知在後），帶單號與實際操作人
		InOrder order = inOrder(fx.emgDao, fx.notificationService);
		order.verify(fx.emgDao).insert(any(), anyString());
		order.verify(fx.notificationService).emgEvent(AcctConst.EV_EMG_SUBMIT, EMG_ID, MICHAEL);
	}

	@Test
	void 送出_切換身分_APPLY_BY為代理身分_取號與CREATE_BY為實際操作人() {
		svc.create(impersonate(HANK, KEVIN), form("DB", "prod-db01", "緊急事由", 1));
		ArgumentCaptor<Emg> cap = ArgumentCaptor.forClass(Emg.class);
		verify(fx.emgDao).insert(cap.capture(), eq(HANK));
		assertThat(cap.getValue().getApplyBy()).isEqualTo(KEVIN);
		verify(fx.docSeqDao).insertFirst(eq(AcctConst.DOC_EA), anyString(), eq(HANK), any());
		verify(fx.notificationService).emgEvent(AcctConst.EV_EMG_SUBMIT, EMG_ID, HANK);
	}

	@Test
	void 送出驗證_必填E10_欄寬E11_時長範圍與類別不存在BAD_CODE_body可為空() {
		assertStatus(() -> svc.create(id(MICHAEL), null), HttpStatus.BAD_REQUEST, AcctMsg.E10_PREFIX + AcctMsg.WHAT_EMG_CATG);
		assertStatus(() -> svc.create(id(MICHAEL), form(" ", "x", "y", 1)), HttpStatus.BAD_REQUEST,
				AcctMsg.E10_PREFIX + AcctMsg.WHAT_EMG_CATG);
		assertStatus(() -> svc.create(id(MICHAEL), form("DB", null, "y", 1)), HttpStatus.BAD_REQUEST,
				AcctMsg.E10_PREFIX + AcctMsg.WHAT_EMG_TARGET);
		assertStatus(() -> svc.create(id(MICHAEL), form("DB", "x", "", 1)), HttpStatus.BAD_REQUEST,
				AcctMsg.E10_PREFIX + AcctMsg.WHAT_EMG_REASON);
		assertStatus(() -> svc.create(id(MICHAEL), form("DB", "x", "y", null)), HttpStatus.BAD_REQUEST,
				AcctMsg.E10_PREFIX + AcctMsg.WHAT_EMG_HOURS);
		assertStatus(() -> svc.create(id(MICHAEL), form("DB", "x".repeat(201), "y", 1)), HttpStatus.BAD_REQUEST,
				AcctMsg.E11_PREFIX + AcctMsg.WHAT_EMG_TARGET);
		assertStatus(() -> svc.create(id(MICHAEL), form("DB", "x", "y".repeat(501), 1)), HttpStatus.BAD_REQUEST,
				AcctMsg.E11_PREFIX + AcctMsg.WHAT_EMG_REASON);
		assertStatus(() -> svc.create(id(MICHAEL), form("DB", "x", "y", 0)), HttpStatus.BAD_REQUEST,
				AcctMsg.BAD_CODE_PREFIX + AcctMsg.WHAT_EMG_HOURS);
		assertStatus(() -> svc.create(id(MICHAEL), form("DB", "x", "y", 25)), HttpStatus.BAD_REQUEST,
				AcctMsg.BAD_CODE_PREFIX + AcctMsg.WHAT_EMG_HOURS);
		assertStatus(() -> svc.create(id(MICHAEL), form("ZZZ", "x", "y", 1)), HttpStatus.BAD_REQUEST,
				AcctMsg.BAD_CODE_PREFIX + AcctMsg.WHAT_EMG_CATG);
		verify(fx.emgDao, never()).insert(any(), anyString());
		verify(fx.notificationService, never()).emgEvent(anyString(), anyString(), anyString());
		assertThat(fx.txm.commits).isZero();
	}

	@Test
	void 送出_邊界值_1與24小時皆可_停用類別仍可送() {
		assertThat(svc.create(id(MICHAEL), form("DB", "x", "y", 1)).getEmgId()).isEqualTo(EMG_ID);
		// APP 在測試資料中 IS_ENABLED=false，但類別存在就能送（緊急存取不受「開放申請」開關限制）
		svc.create(id(MICHAEL), form("APP", "x", "y", 24));
		ArgumentCaptor<Emg> cap = ArgumentCaptor.forClass(Emg.class);
		verify(fx.emgDao, org.mockito.Mockito.times(2)).insert(cap.capture(), eq(MICHAEL));
		assertThat(cap.getAllValues()).extracting(Emg::getExpectHourQty).containsExactly(1, 24);
	}

	@Test
	void 列表_有EMG_VIEW_ALL不限申請人_否則限本人_篩選值轉交DAO() {
		svc.list(id(TERRY), " db ", "PENDING", "2026-10-01", "2026-10-07");
		verify(fx.emgDao).findList(isNull(), eq("db"), eq("PENDING"), eq(TODAY.minusDays(6)), eq(TODAY), eq(NOW));

		svc.list(id(MICHAEL), null, " ", null, null);
		verify(fx.emgDao).findList(eq(MICHAEL), isNull(), isNull(), isNull(), isNull(), eq(NOW));
	}

	@Test
	void 列表_狀態非法400_日期格式錯400_關鍵字LIKE跳脫() {
		assertStatus(() -> svc.list(id(TERRY), null, "DONE", null, null), HttpStatus.BAD_REQUEST, AcctMsg.BAD_CODE_PREFIX + "狀態");
		assertStatus(() -> svc.list(id(TERRY), null, null, "2026/10/01", null), HttpStatus.BAD_REQUEST, AcctMsg.BAD_DATE);
		assertStatus(() -> svc.list(id(TERRY), null, null, null, "2026-02-30"), HttpStatus.BAD_REQUEST, AcctMsg.BAD_DATE);
		verify(fx.emgDao, never()).findList(any(), any(), any(), any(), any(), any());

		svc.list(id(TERRY), "50%_a", null, null, null);
		verify(fx.emgDao).findList(isNull(), eq("50\\%\\_a"), isNull(), isNull(), isNull(), eq(NOW));
	}

	@Test
	void 明細_本人或EMG_VIEW_ALL可看_帶受影響帳號_查無或無權看一律404() {
		EmgDetail d = new EmgDetail();
		d.setEmgId(EMG_ID);
		d.setApplyBy(MICHAEL);
		when(fx.emgDao.findById(EMG_ID, NOW)).thenReturn(d);
		when(fx.emgDao.findAffects(EMG_ID)).thenReturn(List.of());

		assertThat(svc.detail(id(MICHAEL), EMG_ID).getAffectAccts()).isEmpty();
		assertThat(svc.detail(id(TERRY), EMG_ID)).isSameAs(d);
		// 切換身分：有效身分是申請人 → 可看
		assertThat(svc.detail(impersonate(HANK, MICHAEL), EMG_ID)).isSameAs(d);

		assertStatus(() -> svc.detail(id(KEVIN), EMG_ID), HttpStatus.NOT_FOUND, AcctMsg.N01);
		assertStatus(() -> svc.detail(id(TERRY), "EA20261007-999"), HttpStatus.NOT_FOUND, AcctMsg.N01);
		assertStatus(() -> svc.detail(id(TERRY), null), HttpStatus.NOT_FOUND, AcctMsg.N01);
	}

	@Test
	void 回報_沒有異動_寫主表不寫子表_不寄信_回傳新版本_逾期後仍可() {
		EmgDetail d = emg(MICHAEL, 3);
		d.setDueDate(NOW.minusHours(1));
		EmgActionResult r = svc.report(id(MICHAEL), EMG_ID, reportForm(AcctConst.EMG_REPORT_NONE, "  ", 3));
		assertThat(r.getEmgId()).isEqualTo(EMG_ID);
		assertThat(r.getVersionNo()).isEqualTo(4);
		// 「沒有異動」說明選填，空白存 null
		verify(fx.emgDao).report(EMG_ID, 3, MICHAEL, AcctConst.EMG_REPORT_NONE, null, NOW, MICHAEL);
		verify(fx.emgDao, never()).insertAffect(any(), any(), any(), any(), any(), any(), any());
		verify(fx.notificationService, never()).emgEvent(anyString(), anyString(), anyString());
		assertThat(fx.txm.commits).isEqualTo(1);
	}

	@Test
	void 回報_異動_子表REPORT列_RECORD_BY有效身分_CREATE_BY實際操作人_同一交易() {
		emg(KEVIN, 0);
		fx.acct("A1", "DB", AcctConst.AS_APPROVED);
		fx.acct("A2", "DB", AcctConst.AS_EXPIRED);
		fx.acct("A3", "DB", AcctConst.AS_DISABLED);
		svc.report(impersonate(HANK, KEVIN), EMG_ID, reportForm(AcctConst.EMG_REPORT_MODIFIED, " 改了密碼 ", 0,
				affect(" A1 ", " acct_A1 ", " 重設密碼 "), affect("A2", "acct_A2", null), affect("A3", "acct_A3", "")));

		InOrder order = inOrder(fx.emgDao);
		order.verify(fx.emgDao).report(EMG_ID, 0, KEVIN, AcctConst.EMG_REPORT_MODIFIED, "改了密碼", NOW, HANK);
		order.verify(fx.emgDao).insertAffect(EMG_ID, AcctConst.AFFECT_REPORT, "A1", "重設密碼", KEVIN, NOW, HANK);
		order.verify(fx.emgDao).insertAffect(EMG_ID, AcctConst.AFFECT_REPORT, "A2", null, KEVIN, NOW, HANK);
		order.verify(fx.emgDao).insertAffect(EMG_ID, AcctConst.AFFECT_REPORT, "A3", null, KEVIN, NOW, HANK);
		assertThat(fx.txm.commits).isEqualTo(1);
	}

	@Test
	void 回報_上限20個帳號可送() {
		emg(MICHAEL, 0);
		EmgReportForm.AffectAcct[] list = new EmgReportForm.AffectAcct[AcctConst.EMG_AFFECT_MAX];
		for (int i = 0; i < list.length; i++) {
			fx.acct("A" + i, "DB", AcctConst.AS_APPROVED);
			list[i] = affect("A" + i, "acct_A" + i, null);
		}
		svc.report(id(MICHAEL), EMG_ID, reportForm(AcctConst.EMG_REPORT_MODIFIED, "說明", 0, list));
		verify(fx.emgDao, org.mockito.Mockito.times(AcctConst.EMG_AFFECT_MAX)).insertAffect(any(), any(), any(), any(), any(),
				any(), any());
	}

	@Test
	void 回報_本人即可_無任何權限仍可回報() {
		// S4-⑬：申請後被拿掉 EMG_APPLY 的人仍須能回報自己的單
		emg(MICHAEL, 0);
		svc.report(idWith(MICHAEL, Set.of(), List.of()), EMG_ID, reportForm(AcctConst.EMG_REPORT_NONE, null, 0));
		verify(fx.emgDao).report(EMG_ID, 0, MICHAEL, AcctConst.EMG_REPORT_NONE, null, NOW, MICHAEL);
	}

	@Test
	void 回報_權限_查無或無權看404_非本人403_EMG_VIEW_ALL者非本人也403() {
		emg(MICHAEL, 0);
		EmgReportForm ok = reportForm(AcctConst.EMG_REPORT_NONE, null, 0);
		assertStatus(() -> svc.report(id(KEVIN), EMG_ID, ok), HttpStatus.NOT_FOUND, AcctMsg.N01);
		assertStatus(() -> svc.report(id(MICHAEL), "EA20261007-999", ok), HttpStatus.NOT_FOUND, AcctMsg.N01);
		assertStatus(() -> svc.report(id(MICHAEL), null, ok), HttpStatus.NOT_FOUND, AcctMsg.N01);
		assertStatus(() -> svc.report(id(TERRY), EMG_ID, ok), HttpStatus.FORBIDDEN, AcctMsg.P01);
		// 實際操作人是申請人、但切換成別人 → 有效身分不是申請人
		assertStatus(() -> svc.report(impersonate(MICHAEL, TERRY), EMG_ID, ok), HttpStatus.FORBIDDEN, AcctMsg.P01);
		assertNothingWritten();
	}

	@Test
	void 回報驗證_回報結果_受影響帳號數量與重複_說明必填與欄寬_版本號必填() {
		emg(MICHAEL, 0);
		fx.acct("A1", "DB", AcctConst.AS_APPROVED);
		String type = AcctMsg.WHAT_EMG_REPORT_TYPE;
		String aff = AcctMsg.WHAT_EMG_AFFECT;
		String none = AcctConst.EMG_REPORT_NONE;
		String mod = AcctConst.EMG_REPORT_MODIFIED;
		assertBad(null, AcctMsg.E10_PREFIX + type);
		assertBad(reportForm(" ", null, 0), AcctMsg.E10_PREFIX + type);
		assertBad(reportForm("CLOSE", null, 0), AcctMsg.BAD_CODE_PREFIX + type);
		assertBad(reportForm(mod, "說明", 0), AcctMsg.E10_PREFIX + aff);
		assertBad(reportForm(none, null, 0, affect("A1", "acct_A1", null)), AcctMsg.BAD_CODE_PREFIX + aff);
		// 21 個都是合法帳號，只剩上限能擋（不符與超量同一句，帳號不合法會讓上限測試假綠）
		EmgReportForm.AffectAcct[] over = new EmgReportForm.AffectAcct[AcctConst.EMG_AFFECT_MAX + 1];
		for (int i = 0; i < over.length; i++) {
			fx.acct("A" + i, "DB", AcctConst.AS_APPROVED);
			over[i] = affect("A" + i, "acct_A" + i, null);
		}
		assertBad(reportForm(mod, "說明", 0, over), AcctMsg.BAD_CODE_PREFIX + aff);
		// 重複以去空白後的單號比對，由 Service 擋、不讓子表主鍵擋成 500
		assertBad(reportForm(mod, "說明", 0, affect("A1", "acct_A1", null), affect(" A1", "acct_A1", null)),
				AcctMsg.BAD_CODE_PREFIX + aff);
		assertBad(reportForm(mod, "說明", 0, (EmgReportForm.AffectAcct) null), AcctMsg.BAD_CODE_PREFIX + aff);
		assertBad(reportForm(mod, "說明", 0, affect(" ", "acct_A1", null)), AcctMsg.BAD_CODE_PREFIX + aff);
		assertBad(reportForm(mod, "說明", 0, affect("A1", "", null)), AcctMsg.BAD_CODE_PREFIX + aff);
		assertBad(reportForm(mod, " ", 0, affect("A1", "acct_A1", null)), AcctMsg.E10_PREFIX + AcctMsg.WHAT_EMG_REPORT_DESCR);
		assertBad(reportForm(none, "x".repeat(501), 0), AcctMsg.E11_PREFIX + AcctMsg.WHAT_EMG_REPORT_DESCR);
		assertBad(reportForm(mod, "說明", 0, affect("A1", "acct_A1", "x".repeat(501))),
				AcctMsg.E11_PREFIX + AcctMsg.WHAT_EMG_AFFECT_DESCR);
		assertBad(reportForm(none, null, null), AcctMsg.E02_PREFIX + "版本號");
		assertNothingWritten();
		// 以上全在查帳號前擋下（S4-③ 上限、單號空白等不得靠「查無帳號」那句湊成同一訊息）
		verify(fx.acctDao, never()).findById(any());

		// 500 字整為上限內
		svc.report(id(MICHAEL), EMG_ID, reportForm(none, "x".repeat(500), 0));
		verify(fx.emgDao).report(EMG_ID, 0, MICHAEL, none, "x".repeat(500), NOW, MICHAEL);
	}

	@Test
	void 回報驗證_帳號單號與名稱須同時相符_狀態限三種_不符一律同一句() {
		emg(MICHAEL, 0);
		fx.acct("A1", "DB", AcctConst.AS_APPROVED);
		fx.acct("R1", "DB", AcctConst.AS_REVIEW);
		fx.acct("J1", "DB", AcctConst.AS_REJECTED);
		String msg = AcctMsg.BAD_CODE_PREFIX + AcctMsg.WHAT_EMG_AFFECT;
		String mod = AcctConst.EMG_REPORT_MODIFIED;
		assertBad(reportForm(mod, "說明", 0, affect("NOPE", "acct_NOPE", null)), msg);
		assertBad(reportForm(mod, "說明", 0, affect("A1", "acct_a1", null)), msg);
		assertBad(reportForm(mod, "說明", 0, affect("A1", "acct_A", null)), msg);
		assertBad(reportForm(mod, "說明", 0, affect("R1", "acct_R1", null)), msg);
		assertBad(reportForm(mod, "說明", 0, affect("J1", "acct_J1", null)), msg);
		// 一筆對、一筆錯 → 整張不寫
		assertBad(reportForm(mod, "說明", 0, affect("A1", "acct_A1", null), affect("R1", "acct_R1", null)), msg);
		assertNothingWritten();
	}

	@Test
	void 回報_已回報_已處理_版本不符_UPDATE零筆_一律409() {
		EmgDetail d = emg(MICHAEL, 2);
		EmgReportForm ok = reportForm(AcctConst.EMG_REPORT_NONE, null, 2);
		assertStatus(() -> svc.report(id(MICHAEL), EMG_ID, reportForm(AcctConst.EMG_REPORT_NONE, null, 1)), HttpStatus.CONFLICT,
				AcctMsg.E08);

		d.setReportTypeCode(AcctConst.EMG_REPORT_NONE);
		assertStatus(() -> svc.report(id(MICHAEL), EMG_ID, ok), HttpStatus.CONFLICT, AcctMsg.E08);
		d.setReportTypeCode(null);
		d.setDecideCode(AcctConst.EMG_DECIDE_RETURN);
		assertStatus(() -> svc.report(id(MICHAEL), EMG_ID, ok), HttpStatus.CONFLICT, AcctMsg.E08);
		assertNothingWritten();

		// 讀取時還沒人動、寫入時被搶先 → UPDATE 0 筆 → 409，子表不寫、交易回滾
		d.setDecideCode(null);
		when(fx.emgDao.report(any(), anyInt(), any(), any(), any(), any(), any())).thenReturn(0);
		fx.acct("A1", "DB", AcctConst.AS_APPROVED);
		assertStatus(() -> svc.report(id(MICHAEL), EMG_ID,
				reportForm(AcctConst.EMG_REPORT_MODIFIED, "說明", 2, affect("A1", "acct_A1", null))), HttpStatus.CONFLICT,
				AcctMsg.E08);
		verify(fx.emgDao, never()).insertAffect(any(), any(), any(), any(), any(), any(), any());
		assertThat(fx.txm.commits).isZero();
		assertThat(fx.txm.rollbacks).isEqualTo(1);
	}

	/** 待回報、未處理的單；report() 預設寫入 1 筆 */
	EmgDetail emg(String applyBy, int versionNo) {
		EmgDetail d = new EmgDetail();
		d.setEmgId(EMG_ID);
		d.setApplyBy(applyBy);
		d.setVersionNo(versionNo);
		d.setDueDate(NOW.plusHours(20));
		when(fx.emgDao.findById(EMG_ID, NOW)).thenReturn(d);
		when(fx.emgDao.report(any(), anyInt(), any(), any(), any(), any(), any())).thenReturn(1);
		when(fx.emgDao.insertAffect(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
		return d;
	}

	void assertBad(EmgReportForm f, String msg) {
		assertStatus(() -> svc.report(id(MICHAEL), EMG_ID, f), HttpStatus.BAD_REQUEST, msg);
	}

	void assertNothingWritten() {
		verify(fx.emgDao, never()).report(any(), anyInt(), any(), any(), any(), any(), any());
		verify(fx.emgDao, never()).insertAffect(any(), any(), any(), any(), any(), any(), any());
		assertThat(fx.txm.commits).isZero();
	}

	static EmgReportForm reportForm(String type, String descr, Integer versionNo, EmgReportForm.AffectAcct... accts) {
		EmgReportForm f = new EmgReportForm();
		f.setReportTypeCode(type);
		f.setReportDescr(descr);
		f.setVersionNo(versionNo);
		f.setAffectAccts(accts.length == 0 ? null : java.util.Arrays.asList(accts));
		return f;
	}

	static EmgReportForm.AffectAcct affect(String acctId, String acctName, String descr) {
		EmgReportForm.AffectAcct a = new EmgReportForm.AffectAcct();
		a.setAcctId(acctId);
		a.setAcctName(acctName);
		a.setAffectDescr(descr);
		return a;
	}

	static EmgForm form(String catgId, String target, String reason, Integer hours) {
		EmgForm f = new EmgForm();
		f.setCatgId(catgId);
		f.setTargetDescr(target);
		f.setApplyReason(reason);
		f.setExpectHourQty(hours);
		return f;
	}
}
