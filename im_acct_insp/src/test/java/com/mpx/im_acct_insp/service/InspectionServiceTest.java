package com.mpx.im_acct_insp.service;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：機房巡檢單元測試（spec §3.8、§4.4）：INSP 權限、須涵蓋全部項目、異常必填說明、異常數與狀態、
//           巡檢時間解析、標記已處理（只接受未處理的異常）與轉 FIXED
//           變更單 #1：列表關鍵字 LIKE 跳脫
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0 重寫（spec_im_acct_insp_v2.0.md §4.14、§6.6；驗收 T20）：權限碼 INSP_VIEW／INSP_CREATE／INSP_RESOLVE；
//           結果須剛好涵蓋啟用項目且只 NORMAL／EXCPT（E18）、異常說明必填（E10）；巡檢時間不可晚於現在；
//           巡檢單與結果同一交易、寫快照與代理身分；標記處理 409 E19、處理方式必填 E10、寫處理人快照與代理身分；
//           整體狀態 SQL 與列表／明細共用
// ============================================================

import static com.mpx.im_acct_insp.service.RequestServiceTest.assertStatus;
import static com.mpx.im_acct_insp.support.TestFixture.HANK;
import static com.mpx.im_acct_insp.support.TestFixture.KEVIN;
import static com.mpx.im_acct_insp.support.TestFixture.MICHAEL;
import static com.mpx.im_acct_insp.support.TestFixture.NOW;
import static com.mpx.im_acct_insp.support.TestFixture.id;
import static com.mpx.im_acct_insp.support.TestFixture.impersonate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.mpx.im_acct_insp.common.AcctMsg;
import com.mpx.im_acct_insp.dao.InspDao;
import com.mpx.im_acct_insp.model.InspDetail;
import com.mpx.im_acct_insp.model.InspItem;
import com.mpx.im_acct_insp.model.InspResult;
import com.mpx.im_acct_insp.model.request.FixForm;
import com.mpx.im_acct_insp.model.request.InspForm;
import com.mpx.im_acct_insp.model.request.InspResultForm;
import com.mpx.im_acct_insp.support.TestFixture;

class InspectionServiceTest {

	TestFixture fx;
	InspectionService svc;

	@BeforeEach
	void setUp() {
		fx = new TestFixture();
		svc = fx.inspectionService;
		when(fx.inspDao.findItems()).thenReturn(List.of(item("A01", "甲、機房環境設施", "溫度"), item("B01", "乙、資訊設備狀態", "燈號")));
		when(fx.inspDao.insertInsp(anyString(), any(), anyString(), anyString(), any(), any(), any())).thenReturn(1);
		when(fx.inspDao.insertResult(anyString(), any(), anyString(), any(), any(), anyString())).thenReturn(1);
	}

	@Test
	void 權限_無INSP權限403() {
		assertStatus(() -> svc.list(id(MICHAEL), null, null, null, null), HttpStatus.FORBIDDEN, AcctMsg.P01);
		assertStatus(() -> svc.items(id(MICHAEL)), HttpStatus.FORBIDDEN, AcctMsg.P01);
		assertStatus(() -> svc.create(id(MICHAEL), form(r("A01", "NORMAL", null), r("B01", "NORMAL", null))),
				HttpStatus.FORBIDDEN, AcctMsg.P01);
		assertStatus(() -> svc.fix(id(MICHAEL), "IN20261007-001", "A01", fix("x")), HttpStatus.FORBIDDEN, AcctMsg.P01);
	}

	@Test
	void 送出_取號IN_同一交易寫巡檢單與全部結果_快照() {
		InspForm f = form(r("A01", "EXCPT", "溫度偏高"), r("B01", "NORMAL", null));
		f.setInspDate("2026-10-07 09:15:00");
		assertThat(svc.create(id(KEVIN), f).getInspId()).isEqualTo("IN20261007-001");
		verify(fx.inspDao).insertInsp("IN20261007-001", LocalDateTime.of(2026, 10, 7, 9, 15), KEVIN, "Kevin (李凱鈞)", null,
				null, NOW);
		verify(fx.inspDao, times(2)).insertResult(eq("IN20261007-001"), any(), anyString(), any(), eq(NOW), eq(KEVIN));
		assertThat(fx.txm.commits).isEqualTo(1);
	}

	@Test
	void 送出_切換身分_INSP_BY為實際使用者_ACT_AS為代理身分() {
		svc.create(impersonate(HANK, KEVIN), form(r("A01", "NORMAL", null), r("B01", "NORMAL", null)));
		verify(fx.inspDao).insertInsp(anyString(), any(), eq(HANK), eq("Hank (黃志翰)"), eq(KEVIN), eq("Kevin (李凱鈞)"), eq(NOW));
	}

	@Test
	void 送出驗證_缺項重複未知NA皆E18_異常說明必填E10_不可晚於現在() {
		assertStatus(() -> svc.create(id(KEVIN), form(r("A01", "NORMAL", null))), HttpStatus.BAD_REQUEST, AcctMsg.E18);
		assertStatus(() -> svc.create(id(KEVIN), form(r("A01", "NORMAL", null), r("A01", "NORMAL", null))),
				HttpStatus.BAD_REQUEST, AcctMsg.E18);
		assertStatus(() -> svc.create(id(KEVIN), form(r("A01", "NORMAL", null), r("Z99", "NORMAL", null))),
				HttpStatus.BAD_REQUEST, AcctMsg.E18);
		assertStatus(() -> svc.create(id(KEVIN), form(r("A01", "NA", null), r("B01", "NORMAL", null))),
				HttpStatus.BAD_REQUEST, AcctMsg.E18);
		assertStatus(() -> svc.create(id(KEVIN), form(r("A01", "EXCPT", " "), r("B01", "NORMAL", null))),
				HttpStatus.BAD_REQUEST, AcctMsg.E10_PREFIX + "異常說明");
		InspForm f = form(r("A01", "NORMAL", null), r("B01", "NORMAL", null));
		f.setInspDate("2026-10-07T10:31");
		assertStatus(() -> svc.create(id(KEVIN), f), HttpStatus.BAD_REQUEST, AcctMsg.INSP_DATE_FUTURE);
		verify(fx.inspDao, never()).insertInsp(anyString(), any(), anyString(), anyString(), any(), any(), any());
	}

	@Test
	void 巡檢時間解析_秒可省_T分隔_空值為現在() {
		assertThat(InspectionService.parseInspDate(null, NOW)).isEqualTo(NOW);
		assertThat(InspectionService.parseInspDate("2026-10-07T08:00", NOW)).isEqualTo(LocalDateTime.of(2026, 10, 7, 8, 0));
		assertThat(InspectionService.parseInspDate("2026-10-07 08:00:30", NOW)).isEqualTo(LocalDateTime.of(2026, 10, 7, 8, 0, 30));
		assertStatus(() -> InspectionService.parseInspDate("2026/10/07", NOW), HttpStatus.BAD_REQUEST, AcctMsg.BAD_DATE_TIME);
	}

	@Test
	void 標記處理_非異常或已處理409E19_處理方式必填E10_寫處理人快照() {
		InspDetail d = detail(result("A01", "EXCPT", 0), result("B01", "NORMAL", 0), result("A02", "EXCPT", 1));
		when(fx.inspDao.findById("IN20261007-001")).thenReturn(d);
		when(fx.inspDao.findResults("IN20261007-001")).thenReturn(d.getResults());
		assertStatus(() -> svc.fix(id(KEVIN), "IN20261007-001", "B01", fix("x")), HttpStatus.CONFLICT, AcctMsg.E19);
		assertStatus(() -> svc.fix(id(KEVIN), "IN20261007-001", "A02", fix("x")), HttpStatus.CONFLICT, AcctMsg.E19);
		assertStatus(() -> svc.fix(id(KEVIN), "IN20261007-001", "A01", fix(" ")), HttpStatus.BAD_REQUEST,
				AcctMsg.E10_PREFIX + "處理方式與結果");
		assertStatus(() -> svc.fix(id(KEVIN), "IN20261007-001", "Z99", fix("x")), HttpStatus.NOT_FOUND, AcctMsg.N01);

		when(fx.inspDao.fix(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any(), any())).thenReturn(1);
		assertThat(svc.fix(impersonate(HANK, KEVIN), "IN20261007-001", "A01", fix("已調整空調")).getInspId())
				.isEqualTo("IN20261007-001");
		verify(fx.inspDao).fix("IN20261007-001", "A01", "已調整空調", HANK, "Hank (黃志翰)", KEVIN, "Kevin (李凱鈞)", NOW);
	}

	@Test
	void 標記處理_條件UPDATE影響0筆_409E19() {
		InspDetail d = detail(result("A01", "EXCPT", 0));
		when(fx.inspDao.findById("IN20261007-001")).thenReturn(d);
		when(fx.inspDao.findResults("IN20261007-001")).thenReturn(d.getResults());
		when(fx.inspDao.fix(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any(), any())).thenReturn(0);
		assertStatus(() -> svc.fix(id(KEVIN), "IN20261007-001", "A01", fix("x")), HttpStatus.CONFLICT, AcctMsg.E19);
	}

	@Test
	void 列表_狀態碼檢查_關鍵字跳脫_日期含頭尾() {
		assertStatus(() -> svc.list(id(KEVIN), null, "NA", null, null), HttpStatus.BAD_REQUEST, AcctMsg.BAD_CODE_PREFIX + "狀態");
		svc.list(id(KEVIN), " 50%_ ", "FIXED", "2026-09-07", "2026-10-07");
		verify(fx.inspDao).findList("50\\%\\_", "FIXED", LocalDate.of(2026, 9, 7), LocalDate.of(2026, 10, 7));
	}

	@Test
	void 整體狀態SQL_列表與明細共用同一段() {
		assertThat(InspDao.FIND_LIST).startsWith(InspDao.INSP_SUMMARY);
		assertThat(InspDao.FIND_BY_ID).startsWith(InspDao.INSP_SUMMARY);
		assertThat(InspDao.INSP_SUMMARY).contains("THEN 'NORMAL'").contains("IS_FIXED = 0 THEN 1 ELSE 0 END) > 0 THEN 'EXCPT'")
				.contains("ELSE 'FIXED'");
		assertThat(InspDao.FIND_LIST).contains("X.INSP_STATE_CODE = :stateCode").contains("X.INSP_BY_NAME")
				.contains("X.ACT_AS_NAME");
	}

	// ------------------------------------------------------------

	static InspItem item(String id, String sect, String name) {
		InspItem i = new InspItem();
		i.setInspItemId(id);
		i.setSectName(sect);
		i.setItemName(name);
		return i;
	}

	static InspResultForm r(String id, String code, String descr) {
		InspResultForm f = new InspResultForm();
		f.setInspItemId(id);
		f.setResultCode(code);
		f.setExcptDescr(descr);
		return f;
	}

	static InspForm form(InspResultForm... rs) {
		InspForm f = new InspForm();
		f.setResults(new ArrayList<>(List.of(rs)));
		return f;
	}

	static FixForm fix(String s) {
		FixForm f = new FixForm();
		f.setFixDescr(s);
		return f;
	}

	static InspResult result(String id, String code, int fixed) {
		InspResult r = new InspResult();
		r.setInspItemId(id);
		r.setResultCode(code);
		r.setIsFixed(fixed);
		return r;
	}

	static InspDetail detail(InspResult... rs) {
		InspDetail d = new InspDetail();
		d.setInspId("IN20261007-001");
		d.setInspStateCode("EXCPT");
		d.setResults(new ArrayList<>(List.of(rs)));
		return d;
	}
}
