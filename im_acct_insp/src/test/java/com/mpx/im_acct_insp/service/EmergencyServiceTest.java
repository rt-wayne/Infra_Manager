package com.mpx.im_acct_insp.service;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: 新增：緊急存取送出與查詢單元測試（BACKLOG 第 46 項 S2；SA §5.11）：
//           EMG_APPLY 權限、必填 E10、欄寬 E11、預估時長 1～24 與類別不存在 BAD_CODE、取號 EA 與 DUE_DATE＝＋24 小時、
//           有效身分（APPLY_BY）與實際操作人（CREATE_BY／取號）、通知在交易提交後呼叫、列表資料範圍（EMG_VIEW_ALL 不限／否則本人）、
//           狀態篩選非法 400、關鍵字跳脫、明細查無或無權看一律 404
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
import static com.mpx.im_acct_insp.support.TestFixture.impersonate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import com.mpx.im_acct_insp.model.EmgDetail;
import com.mpx.im_acct_insp.model.request.EmgForm;
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

	static EmgForm form(String catgId, String target, String reason, Integer hours) {
		EmgForm f = new EmgForm();
		f.setCatgId(catgId);
		f.setTargetDescr(target);
		f.setApplyReason(reason);
		f.setExpectHourQty(hours);
		return f;
	}
}
