package com.mpx.infra_manager.service;

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
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S4b-1：主管補核准／退回測試——未回報不得補核准（400）、補核准不標旗標、回報異動每帳號一列 EMG_MODIFY
//           歷史（只取 REPORT 列；ACT_BY 實際操作人、ACT_AS 代理身分、RESULT_DESCR 內容）、退回＋異動逐帳號標旗標且提交後才寄信、
//           沒有異動或未回報不寫歷史不標旗標、退回原因必填／500 字／版本號必填、404／403、兩位主管並發與回報並發皆 409、
//           自核（處理人即申請人、含切換成申請人）IS_SELF_DECIDE＝1 且歷史標籤「自行核准」、逾期後仍可處理；
//           審查補強：標旗標 0 筆→500 回滾、交易中途例外整筆回滾且不寄信
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S6：異常結案測試——沒有異動只寫主表、有異動寫子表 CLOSE 列＋EMG_CLOSE_MODIFY 歷史＋旗標
//           （原因格式、截斷、REPORT 列不動）、驗證順序（結案說明兩種都必填）、帳號不符同一句、404／403、
//           未退回／已補核准／已結案／版本不符／UPDATE 0 筆皆 409、旗標 0 筆 500 回滾、自核標籤「自行結案」、不寄信
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S8：列表篩選 UNDECIDED 合法並原樣轉交 DAO（SQL 斷言在 SqlConstantScanTest）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// ============================================================

import static com.mpx.infra_manager.service.RequestServiceTest.assertStatus;
import static com.mpx.infra_manager.support.TestFixture.GARY;
import static com.mpx.infra_manager.support.TestFixture.HANK;
import static com.mpx.infra_manager.support.TestFixture.KEVIN;
import static com.mpx.infra_manager.support.TestFixture.MICHAEL;
import static com.mpx.infra_manager.support.TestFixture.MOLLY;
import static com.mpx.infra_manager.support.TestFixture.NAMES;
import static com.mpx.infra_manager.support.TestFixture.NOW;
import static com.mpx.infra_manager.support.TestFixture.TERRY;
import static com.mpx.infra_manager.support.TestFixture.TODAY;
import static com.mpx.infra_manager.support.TestFixture.id;
import static com.mpx.infra_manager.support.TestFixture.idWith;
import static com.mpx.infra_manager.support.TestFixture.impersonate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;

import com.mpx.infra_manager.common.AcctConst;
import com.mpx.infra_manager.common.AcctMsg;
import com.mpx.infra_manager.common.Identity;
import com.mpx.infra_manager.model.AcctHist;
import com.mpx.infra_manager.model.Emg;
import com.mpx.infra_manager.model.EmgActionResult;
import com.mpx.infra_manager.model.EmgAffectAcct;
import com.mpx.infra_manager.model.EmgDetail;
import com.mpx.infra_manager.model.request.EmgCloseForm;
import com.mpx.infra_manager.model.request.EmgForm;
import com.mpx.infra_manager.model.request.EmgReportForm;
import com.mpx.infra_manager.model.request.ReasonForm;
import com.mpx.infra_manager.model.request.VersionForm;
import com.mpx.infra_manager.support.TestFixture;

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
	void 列表_篩選UNDECIDED合法_原樣轉交DAO() {
		svc.list(id(TERRY), null, "UNDECIDED", null, null);
		verify(fx.emgDao).findList(isNull(), isNull(), eq("UNDECIDED"), isNull(), isNull(), eq(NOW));
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

	// ------------------------------------------------------------
	// 主管處理（S4b-1）：補核准 approve、退回 returnEmg
	// ------------------------------------------------------------

	@Test
	void 補核准_回報沒有異動_寫主表_不寫歷史不標旗標_不寄信_逾期後仍可() {
		EmgDetail d = emg(MICHAEL, 1);
		d.setDueDate(NOW.minusHours(3));
		reported(d, AcctConst.EMG_REPORT_NONE, null);
		EmgActionResult r = svc.approve(id(TERRY), EMG_ID, version(1));
		assertThat(r.getEmgId()).isEqualTo(EMG_ID);
		assertThat(r.getVersionNo()).isEqualTo(2);
		verify(fx.emgDao).decide(EMG_ID, 1, AcctConst.EMG_DECIDE_APPROVE, TERRY, null, 0, NOW, TERRY);
		// 「沒有異動」連子表都不用讀
		verify(fx.emgDao, never()).findAffects(any());
		assertThat(fx.hists).isEmpty();
		verify(fx.acctDao, never()).flagReview(any(), any(), any(), any(), any(), any());
		verify(fx.notificationService, never()).emgEvent(anyString(), anyString(), anyString());
		assertThat(fx.txm.commits).isEqualTo(1);
	}

	@Test
	void 補核准_回報異動_每帳號一列EMG_MODIFY歷史_只取REPORT列_不標旗標_切換身分時ACT_BY實際操作人() {
		EmgDetail d = emg(MICHAEL, 1);
		reported(d, AcctConst.EMG_REPORT_MODIFIED, "改了密碼", affectRow("A1", "重設密碼"), affectRow("A2", null),
				closeRow("A9"));
		svc.approve(impersonate(HANK, TERRY), EMG_ID, version(1));
		verify(fx.emgDao).decide(EMG_ID, 1, AcctConst.EMG_DECIDE_APPROVE, TERRY, null, 0, NOW, HANK);

		assertThat(fx.hists).extracting(AcctHist::getAcctId).containsExactly("A1", "A2");
		AcctHist h = fx.hists.get(0);
		assertThat(h.getEmgId()).isEqualTo(EMG_ID);
		assertThat(h.getReqId()).isNull();
		assertThat(h.getStageId()).isNull();
		assertThat(h.getApplyDept()).isNull();
		assertThat(h.getSourceCode()).isEqualTo(AcctConst.HIST_SRC_EMG);
		assertThat(h.getActionCode()).isEqualTo(AcctConst.ACT_EMG_MODIFY);
		assertThat(h.getActBy()).isEqualTo(HANK);
		assertThat(h.getActByName()).isEqualTo(NAMES.get(HANK));
		assertThat(h.getActAsId()).isEqualTo(TERRY);
		assertThat(h.getActAsName()).isEqualTo(NAMES.get(TERRY));
		assertThat(h.getActDate()).isEqualTo(NOW);
		assertThat(h.getIsSelfAction()).isZero();
		assertThat(h.getCreateBy()).isEqualTo(HANK);
		assertThat(h.getResultDescr()).isEqualTo("緊急存取 " + EMG_ID + " 已補核准；申請人 " + NAMES.get(MICHAEL)
				+ " 於 2026-10-07 09:00:00 回報異動：改了密碼；本帳號：重設密碼");
		// 沒有本帳號說明就不加那一段
		assertThat(fx.hists.get(1).getResultDescr()).endsWith("回報異動：改了密碼");

		verify(fx.acctDao, never()).flagReview(any(), any(), any(), any(), any(), any());
		verify(fx.notificationService, never()).emgEvent(anyString(), anyString(), anyString());
		assertThat(fx.txm.commits).isEqualTo(1);
	}

	@Test
	void 補核准_申請人尚未回報_400_不寫() {
		emg(MICHAEL, 1);
		assertStatus(() -> svc.approve(id(TERRY), EMG_ID, version(1)), HttpStatus.BAD_REQUEST, AcctMsg.EMG_APPROVE_NO_REPORT);
		assertNothingDecided();
	}

	@Test
	void 退回_回報異動_每帳號歷史並標旗標_RESULT_DESCR含退回原因_提交後才寄EMG_RETURN給申請人() {
		EmgDetail d = emg(MICHAEL, 1);
		reported(d, AcctConst.EMG_REPORT_MODIFIED, "改了密碼", affectRow("A1", "重設密碼"), affectRow("A2", null));
		// 寄信時交易必須已提交（S4-⑪）
		doAnswer(inv -> {
			assertThat(fx.txm.commits).isEqualTo(1);
			return null;
		}).when(fx.notificationService).emgEvent(anyString(), anyString(), anyString());

		EmgActionResult r = svc.returnEmg(id(MOLLY), EMG_ID, reasonForm(1, " 未事先通知 "));
		assertThat(r.getVersionNo()).isEqualTo(2);
		verify(fx.emgDao).decide(EMG_ID, 1, AcctConst.EMG_DECIDE_RETURN, MOLLY, "未事先通知", 0, NOW, MOLLY);
		assertThat(fx.hists).extracting(AcctHist::getAcctId).containsExactly("A1", "A2");
		assertThat(fx.hists.get(0).getResultDescr()).isEqualTo("緊急存取 " + EMG_ID + " 已退回（未事先通知）；申請人 "
				+ NAMES.get(MICHAEL) + " 於 2026-10-07 09:00:00 回報異動：改了密碼；本帳號：重設密碼");
		String flag = EMG_ID + " 已退回：未事先通知";
		verify(fx.acctDao).flagReview("A1", flag, AcctConst.FLAG_SRC_EMG, MOLLY, NOW, MOLLY);
		verify(fx.acctDao).flagReview("A2", flag, AcctConst.FLAG_SRC_EMG, MOLLY, NOW, MOLLY);
		verify(fx.notificationService).emgEvent(AcctConst.EV_EMG_RETURN, EMG_ID, MOLLY);
		assertThat(fx.txm.commits).isEqualTo(1);
	}

	@Test
	void 退回_旗標原因超過500字截斷_歷史說明超過1000字截斷() {
		EmgDetail d = emg(MICHAEL, 1);
		reported(d, AcctConst.EMG_REPORT_MODIFIED, "說".repeat(500), affectRow("A1", "改".repeat(500)));
		String reason = "因".repeat(500);
		svc.returnEmg(id(TERRY), EMG_ID, reasonForm(1, reason));
		ArgumentCaptor<String> flag = ArgumentCaptor.forClass(String.class);
		verify(fx.acctDao).flagReview(eq("A1"), flag.capture(), eq(AcctConst.FLAG_SRC_EMG), eq(TERRY), eq(NOW), eq(TERRY));
		assertThat(flag.getValue()).hasSize(500).startsWith(EMG_ID + " 已退回：因因");
		assertThat(fx.hists.get(0).getResultDescr()).hasSize(1000).startsWith("緊急存取 " + EMG_ID + " 已退回（因因");
	}

	@Test
	void 退回_沒有異動或未回報_不寫歷史不標旗標_仍寄信_逾期後仍可() {
		EmgDetail d = emg(MICHAEL, 0);
		d.setDueDate(NOW.minusDays(2));
		svc.returnEmg(id(GARY), EMG_ID, reasonForm(0, "逾期未回報"));
		reported(d, AcctConst.EMG_REPORT_NONE, "沒動到帳號");
		svc.returnEmg(id(GARY), EMG_ID, reasonForm(0, "事由不符"));

		verify(fx.emgDao).decide(EMG_ID, 0, AcctConst.EMG_DECIDE_RETURN, GARY, "逾期未回報", 0, NOW, GARY);
		verify(fx.emgDao).decide(EMG_ID, 0, AcctConst.EMG_DECIDE_RETURN, GARY, "事由不符", 0, NOW, GARY);
		verify(fx.emgDao, never()).findAffects(any());
		assertThat(fx.hists).isEmpty();
		verify(fx.acctDao, never()).flagReview(any(), any(), any(), any(), any(), any());
		verify(fx.notificationService, times(2)).emgEvent(AcctConst.EV_EMG_RETURN, EMG_ID, GARY);
		assertThat(fx.txm.commits).isEqualTo(2);
	}

	@Test
	void 處理驗證_退回原因必填與500字_版本號必填_不寫() {
		emg(MICHAEL, 1);
		String what = AcctMsg.WHAT_EMG_RETURN_REASON;
		assertStatus(() -> svc.returnEmg(id(TERRY), EMG_ID, null), HttpStatus.BAD_REQUEST, AcctMsg.E10_PREFIX + what);
		assertStatus(() -> svc.returnEmg(id(TERRY), EMG_ID, reasonForm(1, " ")), HttpStatus.BAD_REQUEST, AcctMsg.E10_PREFIX + what);
		assertStatus(() -> svc.returnEmg(id(TERRY), EMG_ID, reasonForm(1, "x".repeat(501))), HttpStatus.BAD_REQUEST,
				AcctMsg.E11_PREFIX + what);
		assertStatus(() -> svc.returnEmg(id(TERRY), EMG_ID, reasonForm(null, "原因")), HttpStatus.BAD_REQUEST,
				AcctMsg.E02_PREFIX + "版本號");
		assertStatus(() -> svc.approve(id(TERRY), EMG_ID, null), HttpStatus.BAD_REQUEST, AcctMsg.E02_PREFIX + "版本號");
		assertStatus(() -> svc.approve(id(TERRY), EMG_ID, version(null)), HttpStatus.BAD_REQUEST, AcctMsg.E02_PREFIX + "版本號");
		assertNothingDecided();

		// 500 字整為上限內
		svc.returnEmg(id(TERRY), EMG_ID, reasonForm(1, "x".repeat(500)));
		verify(fx.emgDao).decide(EMG_ID, 1, AcctConst.EMG_DECIDE_RETURN, TERRY, "x".repeat(500), 0, NOW, TERRY);
	}

	@Test
	void 處理權限_查無或無權看404_無EMG_DECIDE者403_申請人本人沒權限也403() {
		emg(MICHAEL, 1);
		assertStatus(() -> svc.approve(id(KEVIN), EMG_ID, version(1)), HttpStatus.NOT_FOUND, AcctMsg.N01);
		assertStatus(() -> svc.returnEmg(id(TERRY), "EA20261007-999", reasonForm(1, "x")), HttpStatus.NOT_FOUND, AcctMsg.N01);
		// 看得到（EMG_VIEW_ALL）但不能處理
		assertStatus(() -> svc.approve(idWith(TERRY, Set.of(AcctConst.PERM_EMG_VIEW_ALL), List.of()), EMG_ID, version(1)),
				HttpStatus.FORBIDDEN, AcctMsg.P01);
		// 申請人看得到自己的單，沒 EMG_DECIDE 一樣不能處理
		assertStatus(() -> svc.returnEmg(id(MICHAEL), EMG_ID, reasonForm(1, "x")), HttpStatus.FORBIDDEN, AcctMsg.P01);
		assertNothingDecided();
	}

	@Test
	void 並發_兩位主管先後處理_已處理或版本不符409_UPDATE零筆409回滾不寫歷史不標旗標不寄信() {
		EmgDetail d = emg(MICHAEL, 1);
		reported(d, AcctConst.EMG_REPORT_MODIFIED, "說明", affectRow("A1", null));
		// 讀到已處理
		d.setDecideCode(AcctConst.EMG_DECIDE_APPROVE);
		assertStatus(() -> svc.returnEmg(id(MOLLY), EMG_ID, reasonForm(1, "x")), HttpStatus.CONFLICT, AcctMsg.E08);
		// 版本落後
		d.setDecideCode(null);
		assertStatus(() -> svc.approve(id(MOLLY), EMG_ID, version(0)), HttpStatus.CONFLICT, AcctMsg.E08);
		assertNothingDecided();

		// 讀取時沒人動、寫入時被另一位主管搶先 → UPDATE 0 筆 → 409，整筆回滾
		when(fx.emgDao.decide(any(), anyInt(), any(), any(), any(), anyInt(), any(), any())).thenReturn(0);
		assertStatus(() -> svc.returnEmg(id(MOLLY), EMG_ID, reasonForm(1, "x")), HttpStatus.CONFLICT, AcctMsg.E08);
		assertThat(fx.hists).isEmpty();
		verify(fx.acctDao, never()).flagReview(any(), any(), any(), any(), any(), any());
		verify(fx.notificationService, never()).emgEvent(anyString(), anyString(), anyString());
		assertThat(fx.txm.commits).isZero();
		assertThat(fx.txm.rollbacks).isEqualTo(1);
	}

	@Test
	void 退回_標旗標零筆_500整筆回滾_不寄信() {
		EmgDetail d = emg(MICHAEL, 1);
		reported(d, AcctConst.EMG_REPORT_MODIFIED, "說明", affectRow("A1", null), affectRow("A2", null));
		// 第二個帳號已不存在 → UPDATE 0 筆
		when(fx.acctDao.flagReview(eq("A2"), any(), any(), any(), any(), any())).thenReturn(0);
		assertStatus(() -> svc.returnEmg(id(TERRY), EMG_ID, reasonForm(1, "x")), HttpStatus.INTERNAL_SERVER_ERROR,
				"資料寫入未完成（單號 " + EMG_ID + "），請聯絡系統管理員");
		assertThat(fx.txm.commits).isZero();
		assertThat(fx.txm.rollbacks).isEqualTo(1);
		verify(fx.notificationService, never()).emgEvent(anyString(), anyString(), anyString());
	}

	@Test
	void 退回_交易中途例外_整筆回滾_例外往外丟_不寄信() {
		EmgDetail d = emg(MICHAEL, 1);
		reported(d, AcctConst.EMG_REPORT_MODIFIED, "說明", affectRow("A1", null), affectRow("A2", null));
		when(fx.acctDao.flagReview(eq("A2"), any(), any(), any(), any(), any())).thenThrow(new IllegalStateException("DB 斷線"));
		assertThatThrownBy(() -> svc.returnEmg(id(TERRY), EMG_ID, reasonForm(1, "x"))).isInstanceOf(IllegalStateException.class)
				.hasMessage("DB 斷線");
		assertThat(fx.txm.commits).isZero();
		assertThat(fx.txm.rollbacks).isEqualTo(1);
		verify(fx.notificationService, never()).emgEvent(anyString(), anyString(), anyString());
	}

	@Test
	void 並發_主管畫面停在未回報_申請人先回報_版本已變_409而非400() {
		// DB 現況：已回報、版本 2；主管畫面還是版本 1
		EmgDetail d = emg(MICHAEL, 2);
		reported(d, AcctConst.EMG_REPORT_NONE, null);
		assertStatus(() -> svc.approve(id(TERRY), EMG_ID, version(1)), HttpStatus.CONFLICT, AcctMsg.E08);
		assertStatus(() -> svc.returnEmg(id(TERRY), EMG_ID, reasonForm(1, "x")), HttpStatus.CONFLICT, AcctMsg.E08);
		assertNothingDecided();
	}

	@Test
	void 自核_處理人即申請人_IS_SELF_DECIDE與歷史IS_SELF_ACTION為1_標籤自行核准_切換成申請人也算() {
		EmgDetail d = emg(MICHAEL, 1);
		reported(d, AcctConst.EMG_REPORT_MODIFIED, "說明", affectRow("A1", null));
		svc.approve(idWith(MICHAEL, Set.of(AcctConst.PERM_EMG_DECIDE), List.of()), EMG_ID, version(1));
		verify(fx.emgDao).decide(EMG_ID, 1, AcctConst.EMG_DECIDE_APPROVE, MICHAEL, null, 1, NOW, MICHAEL);
		AcctHist h = fx.hists.get(0);
		assertThat(h.getIsSelfAction()).isEqualTo(1);
		// 查詢時 REQ_APPLY_BY 由 COALESCE(R.APPLY_BY, EM.APPLY_BY) 帶出緊急存取申請人
		h.setReqApplyBy(MICHAEL);
		assertThat(RequestService.withLabels(List.of(h)).get(0).getSelfActionLabel()).isEqualTo(AcctConst.SELF_LABEL_APPLICANT);

		// HANK 切換成申請人 MICHAEL 處理：有效身分是申請人 → 自核；ACT_BY＝HANK、ACT_AS＝MICHAEL
		fx.hists.clear();
		Identity asApplicant = new Identity("hash-" + HANK, HANK, NAMES.get(HANK), MICHAEL, NAMES.get(MICHAEL), true,
				Set.of(AcctConst.PERM_EMG_DECIDE), List.of(), List.of(), null, null, null);
		svc.returnEmg(asApplicant, EMG_ID, reasonForm(1, "自己退回"));
		verify(fx.emgDao).decide(EMG_ID, 1, AcctConst.EMG_DECIDE_RETURN, MICHAEL, "自己退回", 1, NOW, HANK);
		AcctHist h2 = fx.hists.get(0);
		assertThat(h2.getActBy()).isEqualTo(HANK);
		assertThat(h2.getActAsId()).isEqualTo(MICHAEL);
		assertThat(h2.getIsSelfAction()).isEqualTo(1);
		h2.setReqApplyBy(MICHAEL);
		assertThat(RequestService.selfLabel(h2)).isEqualTo(AcctConst.SELF_LABEL_APPLICANT);

		// 非申請人處理：不是自核、沒有標籤
		fx.hists.clear();
		svc.approve(id(TERRY), EMG_ID, version(1));
		AcctHist h3 = fx.hists.get(0);
		assertThat(h3.getIsSelfAction()).isZero();
		h3.setReqApplyBy(MICHAEL);
		assertThat(RequestService.selfLabel(h3)).isNull();
	}

	// ------------------------------------------------------------
	// 異常結案（S6）：close
	// ------------------------------------------------------------

	@Test
	void 結案_沒有異動_寫主表_不寫子表歷史旗標_不寄信_回傳新版本() {
		EmgDetail d = returned(emg(MICHAEL, 2));
		d.setDueDate(NOW.minusDays(1));
		EmgActionResult r = svc.close(id(TERRY), EMG_ID, closeForm(AcctConst.EMG_CLOSE_NO_CHANGE, " 查無異動 ", 2));
		assertThat(r.getEmgId()).isEqualTo(EMG_ID);
		assertThat(r.getVersionNo()).isEqualTo(3);
		verify(fx.emgDao).close(EMG_ID, 2, AcctConst.EMG_CLOSE_NO_CHANGE, "查無異動", TERRY, 0, NOW, TERRY);
		verify(fx.emgDao, never()).insertAffect(any(), any(), any(), any(), any(), any(), any());
		assertThat(fx.hists).isEmpty();
		// 既有旗標不清（S10 才處理）、也不新標
		verify(fx.acctDao, never()).flagReview(any(), any(), any(), any(), any(), any());
		verify(fx.notificationService, never()).emgEvent(anyString(), anyString(), anyString());
		assertThat(fx.txm.commits).isEqualTo(1);
	}

	@Test
	void 結案_有異動_子表CLOSE列_EMG_CLOSE_MODIFY歷史_標旗標_切換身分時ACT_BY實際操作人_REPORT列不動() {
		returned(emg(MICHAEL, 2));
		fx.acct("A1", "DB", AcctConst.AS_APPROVED);
		fx.acct("A2", "DB", AcctConst.AS_DISABLED);
		EmgActionResult r = svc.close(impersonate(HANK, TERRY), EMG_ID, closeForm(AcctConst.EMG_CLOSE_CHANGED, " 稽核發現改權限 ", 2,
				affect(" A1 ", " acct_A1 ", " 加了 DBA "), affect("A2", "acct_A2", " ")));
		assertThat(r.getVersionNo()).isEqualTo(3);

		InOrder order = inOrder(fx.emgDao);
		order.verify(fx.emgDao).close(EMG_ID, 2, AcctConst.EMG_CLOSE_CHANGED, "稽核發現改權限", TERRY, 0, NOW, HANK);
		order.verify(fx.emgDao).insertAffect(EMG_ID, AcctConst.AFFECT_CLOSE, "A1", "加了 DBA", TERRY, NOW, HANK);
		order.verify(fx.emgDao).insertAffect(EMG_ID, AcctConst.AFFECT_CLOSE, "A2", null, TERRY, NOW, HANK);
		// 申請人回報的 REPORT 列不改不刪：結案只新增 CLOSE 列
		verify(fx.emgDao, never()).insertAffect(any(), eq(AcctConst.AFFECT_REPORT), any(), any(), any(), any(), any());

		assertThat(fx.hists).extracting(AcctHist::getAcctId).containsExactly("A1", "A2");
		AcctHist h = fx.hists.get(0);
		assertThat(h.getEmgId()).isEqualTo(EMG_ID);
		assertThat(h.getReqId()).isNull();
		assertThat(h.getSourceCode()).isEqualTo(AcctConst.HIST_SRC_EMG);
		assertThat(h.getActionCode()).isEqualTo(AcctConst.ACT_EMG_CLOSE_MODIFY);
		assertThat(h.getActBy()).isEqualTo(HANK);
		assertThat(h.getActByName()).isEqualTo(NAMES.get(HANK));
		assertThat(h.getActAsId()).isEqualTo(TERRY);
		assertThat(h.getActAsName()).isEqualTo(NAMES.get(TERRY));
		assertThat(h.getActDate()).isEqualTo(NOW);
		assertThat(h.getIsSelfAction()).isZero();
		assertThat(h.getCreateBy()).isEqualTo(HANK);
		assertThat(h.getResultDescr()).isEqualTo("緊急存取 " + EMG_ID + " 異常結案：有帳號異動；結案人 " + NAMES.get(TERRY)
				+ "；結案說明：稽核發現改權限；本帳號：加了 DBA");
		assertThat(fx.hists.get(1).getResultDescr()).endsWith("結案說明：稽核發現改權限");

		// S6-②：旗標原因「{單號} 異常結案發現異動：{結案說明}」；FLAG_BY 有效身分、UPDATE_BY 實際操作人
		String flag = EMG_ID + " 異常結案發現異動：稽核發現改權限";
		verify(fx.acctDao).flagReview("A1", flag, AcctConst.FLAG_SRC_EMG, TERRY, NOW, HANK);
		verify(fx.acctDao).flagReview("A2", flag, AcctConst.FLAG_SRC_EMG, TERRY, NOW, HANK);
		verify(fx.notificationService, never()).emgEvent(anyString(), anyString(), anyString());
		assertThat(fx.txm.commits).isEqualTo(1);
	}

	@Test
	void 結案_上限20個帳號可送_旗標原因超過500字截斷_歷史說明超過1000字截斷() {
		returned(emg(MICHAEL, 2));
		EmgReportForm.AffectAcct[] list = new EmgReportForm.AffectAcct[AcctConst.EMG_AFFECT_MAX];
		for (int i = 0; i < list.length; i++) {
			fx.acct("A" + i, "DB", AcctConst.AS_APPROVED);
			list[i] = affect("A" + i, "acct_A" + i, "改".repeat(500));
		}
		svc.close(id(TERRY), EMG_ID, closeForm(AcctConst.EMG_CLOSE_CHANGED, "因".repeat(500), 2, list));
		verify(fx.emgDao, times(AcctConst.EMG_AFFECT_MAX)).insertAffect(any(), any(), any(), any(), any(), any(), any());
		ArgumentCaptor<String> flag = ArgumentCaptor.forClass(String.class);
		verify(fx.acctDao).flagReview(eq("A0"), flag.capture(), eq(AcctConst.FLAG_SRC_EMG), eq(TERRY), eq(NOW), eq(TERRY));
		assertThat(flag.getValue()).hasSize(500).startsWith(EMG_ID + " 異常結案發現異動：因因");
		assertThat(fx.hists.get(0).getResultDescr()).hasSize(1000).startsWith("緊急存取 " + EMG_ID + " 異常結案：有帳號異動；");
	}

	@Test
	void 結案驗證_結案發現_受影響帳號數量與重複_結案說明兩種都必填與欄寬_版本號必填_查帳號前擋下() {
		returned(emg(MICHAEL, 2));
		fx.acct("A1", "DB", AcctConst.AS_APPROVED);
		String res = AcctMsg.WHAT_EMG_CLOSE_RESULT;
		String aff = AcctMsg.WHAT_EMG_AFFECT;
		String descr = AcctMsg.WHAT_EMG_CLOSE_DESCR;
		String none = AcctConst.EMG_CLOSE_NO_CHANGE;
		String chg = AcctConst.EMG_CLOSE_CHANGED;
		assertCloseBad(null, AcctMsg.E10_PREFIX + res);
		assertCloseBad(closeForm(" ", "說明", 2), AcctMsg.E10_PREFIX + res);
		// 回報的值域不通用
		assertCloseBad(closeForm(AcctConst.EMG_REPORT_MODIFIED, "說明", 2), AcctMsg.BAD_CODE_PREFIX + res);
		assertCloseBad(closeForm(chg, "說明", 2), AcctMsg.E10_PREFIX + aff);
		assertCloseBad(closeForm(none, "說明", 2, affect("A1", "acct_A1", null)), AcctMsg.BAD_CODE_PREFIX + aff);
		EmgReportForm.AffectAcct[] over = new EmgReportForm.AffectAcct[AcctConst.EMG_AFFECT_MAX + 1];
		for (int i = 0; i < over.length; i++) {
			fx.acct("A" + i, "DB", AcctConst.AS_APPROVED);
			over[i] = affect("A" + i, "acct_A" + i, null);
		}
		assertCloseBad(closeForm(chg, "說明", 2, over), AcctMsg.BAD_CODE_PREFIX + aff);
		assertCloseBad(closeForm(chg, "說明", 2, affect("A1", "acct_A1", null), affect(" A1", "acct_A1", null)),
				AcctMsg.BAD_CODE_PREFIX + aff);
		assertCloseBad(closeForm(chg, "說明", 2, (EmgReportForm.AffectAcct) null), AcctMsg.BAD_CODE_PREFIX + aff);
		assertCloseBad(closeForm(chg, "說明", 2, affect(" ", "acct_A1", null)), AcctMsg.BAD_CODE_PREFIX + aff);
		assertCloseBad(closeForm(chg, "說明", 2, affect("A1", " ", null)), AcctMsg.BAD_CODE_PREFIX + aff);
		// S6-①：結案說明兩種結果都必填
		assertCloseBad(closeForm(none, " ", 2), AcctMsg.E10_PREFIX + descr);
		assertCloseBad(closeForm(none, null, 2), AcctMsg.E10_PREFIX + descr);
		assertCloseBad(closeForm(chg, "", 2, affect("A1", "acct_A1", null)), AcctMsg.E10_PREFIX + descr);
		assertCloseBad(closeForm(none, "x".repeat(501), 2), AcctMsg.E11_PREFIX + descr);
		assertCloseBad(closeForm(chg, "說明", 2, affect("A1", "acct_A1", "x".repeat(501))),
				AcctMsg.E11_PREFIX + AcctMsg.WHAT_EMG_AFFECT_DESCR);
		assertCloseBad(closeForm(none, "說明", null), AcctMsg.E02_PREFIX + "版本號");
		assertNothingClosed();
		verify(fx.acctDao, never()).findById(any());

		// 500 字整為上限內
		svc.close(id(TERRY), EMG_ID, closeForm(none, "x".repeat(500), 2));
		verify(fx.emgDao).close(EMG_ID, 2, none, "x".repeat(500), TERRY, 0, NOW, TERRY);
	}

	@Test
	void 結案驗證_帳號單號與名稱須同時相符_狀態限三種_不符一律同一句() {
		returned(emg(MICHAEL, 2));
		fx.acct("A1", "DB", AcctConst.AS_APPROVED);
		fx.acct("R1", "DB", AcctConst.AS_REVIEW);
		String msg = AcctMsg.BAD_CODE_PREFIX + AcctMsg.WHAT_EMG_AFFECT;
		String chg = AcctConst.EMG_CLOSE_CHANGED;
		assertCloseBad(closeForm(chg, "說明", 2, affect("NOPE", "acct_NOPE", null)), msg);
		assertCloseBad(closeForm(chg, "說明", 2, affect("A1", "acct_a1", null)), msg);
		assertCloseBad(closeForm(chg, "說明", 2, affect("R1", "acct_R1", null)), msg);
		assertCloseBad(closeForm(chg, "說明", 2, affect("A1", "acct_A1", null), affect("R1", "acct_R1", null)), msg);
		// 順序：帳號格式 400 先於結案說明 E10；帳號查無 400 先於版本號 E02
		assertCloseBad(closeForm(chg, " ", 2, affect(" ", "acct_A1", null)), msg);
		assertCloseBad(closeForm(chg, "說明", null, affect("NOPE", "acct_NOPE", null)), msg);
		assertNothingClosed();
	}

	@Test
	void 結案_帳號照單號排序寫入_與退回同一上鎖順序_已逾期帳號可收() {
		returned(emg(MICHAEL, 2));
		fx.acct("A1", "DB", AcctConst.AS_EXPIRED);
		fx.acct("A2", "DB", AcctConst.AS_APPROVED);
		svc.close(id(TERRY), EMG_ID, closeForm(AcctConst.EMG_CLOSE_CHANGED, "說明", 2, affect("A2", "acct_A2", null),
				affect("A1", "acct_A1", null)));
		InOrder order = inOrder(fx.emgDao, fx.acctDao);
		order.verify(fx.emgDao).insertAffect(EMG_ID, AcctConst.AFFECT_CLOSE, "A1", null, TERRY, NOW, TERRY);
		order.verify(fx.acctDao).flagReview(eq("A1"), any(), any(), any(), any(), any());
		order.verify(fx.emgDao).insertAffect(EMG_ID, AcctConst.AFFECT_CLOSE, "A2", null, TERRY, NOW, TERRY);
		order.verify(fx.acctDao).flagReview(eq("A2"), any(), any(), any(), any(), any());
		assertThat(fx.hists).extracting(AcctHist::getAcctId).containsExactly("A1", "A2");
	}

	@Test
	void 結案權限_查無或無權看404_無EMG_DECIDE者403_申請人本人沒權限也403() {
		returned(emg(MICHAEL, 2));
		EmgCloseForm ok = closeForm(AcctConst.EMG_CLOSE_NO_CHANGE, "說明", 2);
		assertStatus(() -> svc.close(id(KEVIN), EMG_ID, ok), HttpStatus.NOT_FOUND, AcctMsg.N01);
		assertStatus(() -> svc.close(id(TERRY), "EA20261007-999", ok), HttpStatus.NOT_FOUND, AcctMsg.N01);
		assertStatus(() -> svc.close(id(TERRY), null, ok), HttpStatus.NOT_FOUND, AcctMsg.N01);
		assertStatus(() -> svc.close(idWith(TERRY, Set.of(AcctConst.PERM_EMG_VIEW_ALL), List.of()), EMG_ID, ok),
				HttpStatus.FORBIDDEN, AcctMsg.P01);
		assertStatus(() -> svc.close(id(MICHAEL), EMG_ID, ok), HttpStatus.FORBIDDEN, AcctMsg.P01);
		// 權限在欄位驗證前：沒權限者送空表單也是 403
		assertStatus(() -> svc.close(id(MICHAEL), EMG_ID, null), HttpStatus.FORBIDDEN, AcctMsg.P01);
		assertNothingClosed();
	}

	@Test
	void 結案並發_未處理_已補核准_已結案_版本不符409_UPDATE零筆409回滾不寫子表歷史旗標() {
		EmgDetail d = emg(MICHAEL, 2);
		fx.acct("A1", "DB", AcctConst.AS_APPROVED);
		EmgCloseForm ok = closeForm(AcctConst.EMG_CLOSE_NO_CHANGE, "說明", 2);
		// D13：只有已退回的單能結案
		assertStatus(() -> svc.close(id(TERRY), EMG_ID, ok), HttpStatus.CONFLICT, AcctMsg.E08);
		d.setDecideCode(AcctConst.EMG_DECIDE_APPROVE);
		assertStatus(() -> svc.close(id(TERRY), EMG_ID, ok), HttpStatus.CONFLICT, AcctMsg.E08);
		returned(d);
		d.setCloseDate(NOW.minusMinutes(5));
		assertStatus(() -> svc.close(id(TERRY), EMG_ID, ok), HttpStatus.CONFLICT, AcctMsg.E08);
		d.setCloseDate(null);
		assertStatus(() -> svc.close(id(TERRY), EMG_ID, closeForm(AcctConst.EMG_CLOSE_NO_CHANGE, "說明", 1)),
				HttpStatus.CONFLICT, AcctMsg.E08);
		assertNothingClosed();

		// 讀取時沒人動、寫入時被另一位主管搶先 → UPDATE 0 筆 → 409，整筆回滾
		when(fx.emgDao.close(any(), anyInt(), any(), any(), any(), anyInt(), any(), any())).thenReturn(0);
		assertStatus(() -> svc.close(id(TERRY), EMG_ID,
				closeForm(AcctConst.EMG_CLOSE_CHANGED, "說明", 2, affect("A1", "acct_A1", null))), HttpStatus.CONFLICT,
				AcctMsg.E08);
		verify(fx.emgDao, never()).insertAffect(any(), any(), any(), any(), any(), any(), any());
		assertThat(fx.hists).isEmpty();
		verify(fx.acctDao, never()).flagReview(any(), any(), any(), any(), any(), any());
		assertThat(fx.txm.commits).isZero();
		assertThat(fx.txm.rollbacks).isEqualTo(1);
	}

	@Test
	void 結案_標旗標零筆_500整筆回滾() {
		returned(emg(MICHAEL, 2));
		fx.acct("A1", "DB", AcctConst.AS_APPROVED);
		fx.acct("A2", "DB", AcctConst.AS_APPROVED);
		when(fx.acctDao.flagReview(eq("A2"), any(), any(), any(), any(), any())).thenReturn(0);
		assertStatus(() -> svc.close(id(TERRY), EMG_ID, closeForm(AcctConst.EMG_CLOSE_CHANGED, "說明", 2,
				affect("A1", "acct_A1", null), affect("A2", "acct_A2", null))), HttpStatus.INTERNAL_SERVER_ERROR,
				"資料寫入未完成（單號 " + EMG_ID + "），請聯絡系統管理員");
		assertThat(fx.txm.commits).isZero();
		assertThat(fx.txm.rollbacks).isEqualTo(1);
	}

	@Test
	void 自核_結案人即申請人_IS_SELF_CLOSE與歷史IS_SELF_ACTION為1_標籤自行結案_切換成申請人也算() {
		returned(emg(MICHAEL, 2));
		fx.acct("A1", "DB", AcctConst.AS_APPROVED);
		EmgCloseForm f = closeForm(AcctConst.EMG_CLOSE_CHANGED, "說明", 2, affect("A1", "acct_A1", null));
		svc.close(idWith(MICHAEL, Set.of(AcctConst.PERM_EMG_DECIDE), List.of()), EMG_ID, f);
		verify(fx.emgDao).close(EMG_ID, 2, AcctConst.EMG_CLOSE_CHANGED, "說明", MICHAEL, 1, NOW, MICHAEL);
		AcctHist h = fx.hists.get(0);
		assertThat(h.getIsSelfAction()).isEqualTo(1);
		h.setReqApplyBy(MICHAEL);
		// S6-③：結案的自核標籤是「自行結案」，不是「自行核准」
		assertThat(RequestService.selfLabel(h)).isEqualTo(AcctConst.SELF_LABEL_CLOSE);

		// HANK 切換成申請人 MICHAEL 結案：有效身分是申請人 → 自核
		fx.hists.clear();
		Identity asApplicant = new Identity("hash-" + HANK, HANK, NAMES.get(HANK), MICHAEL, NAMES.get(MICHAEL), true,
				Set.of(AcctConst.PERM_EMG_DECIDE), List.of(), List.of(), null, null, null);
		svc.close(asApplicant, EMG_ID, f);
		verify(fx.emgDao).close(EMG_ID, 2, AcctConst.EMG_CLOSE_CHANGED, "說明", MICHAEL, 1, NOW, HANK);
		AcctHist h2 = fx.hists.get(0);
		assertThat(h2.getActBy()).isEqualTo(HANK);
		assertThat(h2.getActAsId()).isEqualTo(MICHAEL);
		assertThat(h2.getIsSelfAction()).isEqualTo(1);
		h2.setReqApplyBy(MICHAEL);
		assertThat(RequestService.selfLabel(h2)).isEqualTo(AcctConst.SELF_LABEL_CLOSE);

		// 非申請人結案：不是自核、沒有標籤
		fx.hists.clear();
		svc.close(id(TERRY), EMG_ID, f);
		AcctHist h3 = fx.hists.get(0);
		assertThat(h3.getIsSelfAction()).isZero();
		h3.setReqApplyBy(MICHAEL);
		assertThat(RequestService.selfLabel(h3)).isNull();
	}

	/** 已回報異動並被退回、尚未結案；close() 預設寫入 1 筆 */
	EmgDetail returned(EmgDetail d) {
		reported(d, AcctConst.EMG_REPORT_MODIFIED, "改了密碼", affectRow("A1", "重設密碼"));
		d.setDecideCode(AcctConst.EMG_DECIDE_RETURN);
		when(fx.emgDao.close(any(), anyInt(), any(), any(), any(), anyInt(), any(), any())).thenReturn(1);
		return d;
	}

	void assertCloseBad(EmgCloseForm f, String msg) {
		assertStatus(() -> svc.close(id(TERRY), EMG_ID, f), HttpStatus.BAD_REQUEST, msg);
	}

	void assertNothingClosed() {
		verify(fx.emgDao, never()).close(any(), anyInt(), any(), any(), any(), anyInt(), any(), any());
		verify(fx.emgDao, never()).insertAffect(any(), any(), any(), any(), any(), any(), any());
		assertThat(fx.hists).isEmpty();
		verify(fx.acctDao, never()).flagReview(any(), any(), any(), any(), any(), any());
		assertThat(fx.txm.commits).isZero();
	}

	static EmgCloseForm closeForm(String result, String descr, Integer versionNo, EmgReportForm.AffectAcct... accts) {
		EmgCloseForm f = new EmgCloseForm();
		f.setCloseResultCode(result);
		f.setCloseDescr(descr);
		f.setVersionNo(versionNo);
		f.setAffectAccts(accts.length == 0 ? null : java.util.Arrays.asList(accts));
		return f;
	}

	/** 待回報、未處理的單；report()／decide() 預設寫入 1 筆 */
	EmgDetail emg(String applyBy, int versionNo) {
		EmgDetail d = new EmgDetail();
		d.setEmgId(EMG_ID);
		d.setApplyBy(applyBy);
		d.setApplyByName(NAMES.get(applyBy));
		d.setVersionNo(versionNo);
		d.setDueDate(NOW.plusHours(20));
		when(fx.emgDao.findById(EMG_ID, NOW)).thenReturn(d);
		when(fx.emgDao.report(any(), anyInt(), any(), any(), any(), any(), any())).thenReturn(1);
		when(fx.emgDao.insertAffect(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
		when(fx.emgDao.decide(any(), anyInt(), any(), any(), any(), anyInt(), any(), any())).thenReturn(1);
		return d;
	}

	/** 申請人已於 09:00 回報；子表列由 findAffects 回傳 */
	void reported(EmgDetail d, String type, String descr, EmgAffectAcct... rows) {
		d.setReportTypeCode(type);
		d.setReportDescr(descr);
		d.setReportDate(NOW.minusMinutes(90));
		d.setReportActBy(d.getApplyBy());
		when(fx.emgDao.findAffects(EMG_ID)).thenReturn(List.of(rows));
	}

	static EmgAffectAcct affectRow(String acctId, String descr) {
		EmgAffectAcct a = new EmgAffectAcct();
		a.setEmgId(EMG_ID);
		a.setAffectTypeCode(AcctConst.AFFECT_REPORT);
		a.setAcctId(acctId);
		a.setAffectDescr(descr);
		return a;
	}

	/** 異常結案登記的列（S6 才會有）；處理時不得拿來寫歷史 */
	static EmgAffectAcct closeRow(String acctId) {
		EmgAffectAcct a = affectRow(acctId, null);
		a.setAffectTypeCode(AcctConst.AFFECT_CLOSE);
		return a;
	}

	void assertNothingDecided() {
		verify(fx.emgDao, never()).decide(any(), anyInt(), any(), any(), any(), anyInt(), any(), any());
		assertThat(fx.hists).isEmpty();
		verify(fx.acctDao, never()).flagReview(any(), any(), any(), any(), any(), any());
		verify(fx.notificationService, never()).emgEvent(anyString(), anyString(), anyString());
		assertThat(fx.txm.commits).isZero();
	}

	static VersionForm version(Integer versionNo) {
		VersionForm f = new VersionForm();
		f.setVersionNo(versionNo);
		return f;
	}

	static ReasonForm reasonForm(Integer versionNo, String reason) {
		ReasonForm f = new ReasonForm();
		f.setVersionNo(versionNo);
		f.setReason(reason);
		return f;
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
