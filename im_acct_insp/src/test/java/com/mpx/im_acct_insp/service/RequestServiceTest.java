package com.mpx.im_acct_insp.service;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：RequestService 單元測試（mock DbClient，不連真 DB）
//           狀態機（4 關 → EXEC → VERIFY → APPROVED、複驗退回回 EXEC、退件與重新送出）、權限 403、在途 409、
//           併發 0 筆 409、送出驗證順序、門禁卡卡號、展延、修改清異常標記、寫入順序（狀態列先、歷史最後）
//           變更單 #1：UTF-8 byte 長度 400、資產分類碼 IS_SHOW＝0 拒絕、門禁卡清 assetClassId、展延審查日期起算、
//           新增重新送出主表狀態條件
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0 重寫（spec_im_acct_insp_v2.0.md §4.2～§4.8、§5；驗收 T04～T12、T14、T22、T23）：
//           7 關狀態機與 VERSION_NO；權限先驗 403 → 狀態／版本 409；未帶版本 400；關卡 6 第一次回報寫主表與審查日期、
//           複驗退回後再次回報不重寫；佐證必填 E12、門禁卡號 E13；送出驗證順序 E01～E06、E11；在途 E07（先查與 ORA-00001）；
//           前提 E09；退件 E10、只有原申請人可重送；IS_SELF_ACTION（自行核准／同人兼任）；切換身分寫 ACT_BY／ACT_AS_*；
//           交易回復時清除附件；通知在交易提交後
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 變更單 #1（L2）：申請單位欄寬 E11 排在 E03、E06 之後
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-10
// 變更說明: 門禁卡回報執行完畢時申請單 ACCT_NAME 同步寫卡號（第一次與複驗退回換卡號）；非門禁卡新增不寫卡號
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-10
// 變更說明: 送出／重新送出的唯一鍵衝突：只有 IND_IM_ACCT_REQ_04 回 E07（含索引名在 cause 的情形），其他原樣往上拋；
//           新增類型主表主鍵衝突；在途判斷不被前綴相同索引名與 SQL hint 誤中
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-10
// 變更說明: 回報執行完畢驗證順序同前端：403 → 附件格式 E14 → E13 → E12 → E11 → 缺版本號 E02 → E08（每步同時帶後面的錯誤）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-10
// 變更說明: 送出驗證順序同前端：403 → 申請類型 → 404 → 附件（空的 .exe 公文 E14）→ E01 → E02 → E09 → E07；
//           重新送出：403 → 404 → 附件 → E01 → E02 → 缺版本號 → E08 → E09 → E07（每步同時帶後面的錯誤）
// ============================================================

import static com.mpx.im_acct_insp.support.TestFixture.ALAN;
import static com.mpx.im_acct_insp.support.TestFixture.ALEX;
import static com.mpx.im_acct_insp.support.TestFixture.CATHY;
import static com.mpx.im_acct_insp.support.TestFixture.GARY;
import static com.mpx.im_acct_insp.support.TestFixture.HANK;
import static com.mpx.im_acct_insp.support.TestFixture.KEVIN;
import static com.mpx.im_acct_insp.support.TestFixture.MICHAEL;
import static com.mpx.im_acct_insp.support.TestFixture.MOLLY;
import static com.mpx.im_acct_insp.support.TestFixture.SAGE;
import static com.mpx.im_acct_insp.support.TestFixture.TERRY;
import static com.mpx.im_acct_insp.support.TestFixture.TESTER;
import static com.mpx.im_acct_insp.support.TestFixture.TODAY;
import static com.mpx.im_acct_insp.support.TestFixture.id;
import static com.mpx.im_acct_insp.support.TestFixture.idWith;
import static com.mpx.im_acct_insp.support.TestFixture.impersonate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.mpx.im_acct_insp.common.AcctConst;
import com.mpx.im_acct_insp.common.AcctMsg;
import com.mpx.im_acct_insp.common.ApiException;
import com.mpx.im_acct_insp.common.Identity;
import com.mpx.im_acct_insp.model.Acct;
import com.mpx.im_acct_insp.model.AcctHist;
import com.mpx.im_acct_insp.model.AcctReq;
import com.mpx.im_acct_insp.model.AcctReqDetail;
import com.mpx.im_acct_insp.model.ReqActionResult;
import com.mpx.im_acct_insp.model.request.AcctReqForm;
import com.mpx.im_acct_insp.model.request.ExecDoneForm;
import com.mpx.im_acct_insp.model.request.ReasonForm;
import com.mpx.im_acct_insp.model.request.VersionForm;
import com.mpx.im_acct_insp.support.TestFixture;

class RequestServiceTest {

	static final String FIRST_ID = "AR20261007-001";
	static final MultipartFile FILE = new MockMultipartFile("f", "x.pdf", "application/pdf", new byte[] { 0x25, 0x50, 0x44, 0x46 });
	/** Oracle 19c 唯一鍵衝突訊息：同帳號在途索引／其他唯一鍵 */
	static final String DUP_IN_FLIGHT = "ORA-00001: unique constraint (RD_USER.IND_IM_ACCT_REQ_04) violated";
	static final String DUP_OTHER = "ORA-00001: unique constraint (RD_USER.PK_IM_ACCT_ATTACH) violated";

	TestFixture fx;
	RequestService svc;

	@BeforeEach
	void setUp() {
		fx = new TestFixture();
		svc = fx.requestService;
	}

	// ------------------------------------------------------------
	// 狀態機
	// ------------------------------------------------------------

	@Test
	void 新增_七關依序_複驗退回不回復主表_再次回報不重寫_複驗通過() {
		String reqId = svc.submit(id(KEVIN), dbForm(), FILE).getReqId();
		assertThat(reqId).isEqualTo(FIRST_ID);
		assertState(AcctConst.RS_REVIEW, 2, 0);
		assertThat(fx.req.getAcctId()).isEqualTo(FIRST_ID);
		assertThat(fx.req.getApplyBy()).isEqualTo(KEVIN);
		verify(fx.acctDao).insertOnSubmit(any(), any(), eq(KEVIN));
		verify(fx.attachmentService).store(eq(FIRST_ID), eq(AcctConst.ATTACH_DOC), any(), any(), any(), eq(KEVIN));
		verify(fx.notificationService).requestEvent(AcctConst.EV_SUBMIT, FIRST_ID, KEVIN);
		fx.acct(FIRST_ID, "DB", AcctConst.AS_REVIEW);

		approve(ALEX);
		assertState(AcctConst.RS_REVIEW, 3, 1);
		approve(TERRY);
		approve(CATHY);
		assertState(AcctConst.RS_REVIEW, 5, 3);
		approve(GARY);
		assertState(AcctConst.RS_EXEC, 6, 4);
		verify(fx.notificationService, times(3)).requestEvent(eq(AcctConst.EV_NEXT_STAGE), eq(FIRST_ID), anyString());
		verify(fx.notificationService).requestEvent(eq(AcctConst.EV_TO_EXEC), eq(FIRST_ID), anyString());

		ReqActionResult r = svc.execDone(id(MICHAEL), reqId, exec(4, null), FILE);
		assertThat(r.getVersionNo()).isEqualTo(5);
		assertState(AcctConst.RS_VERIFY, 7, 5);
		assertThat(fx.req.getExecBy()).isEqualTo(MICHAEL);
		assertThat(fx.req.getEffectDate()).isEqualTo(TestFixture.NOW);
		verify(fx.acctDao).applyAdd(any(), eq("db_user01"), any(), eq(MICHAEL));
		// H01 符合審查條件：LAST＝生效日、NEXT＝＋180
		verify(fx.acctDao).updateReviewDates(FIRST_ID, TODAY, TODAY.plusDays(180), TestFixture.NOW, MICHAEL);
		verify(fx.attachDao).deactivate(reqId, AcctConst.ATTACH_PROOF, TestFixture.NOW, MICHAEL);
		verify(fx.notificationService).requestEvent(AcctConst.EV_EXEC_DONE, FIRST_ID, MICHAEL);

		svc.verifyReject(id(MOLLY), reqId, reason(5, "權限開太大"));
		assertState(AcctConst.RS_EXEC, 6, 6);
		assertThat(fx.req.getVerifyRejectCnt()).isEqualTo(1);
		verify(fx.notificationService).requestEvent(AcctConst.EV_VERIFY_REJECT, FIRST_ID, MOLLY);

		svc.execDone(id(MICHAEL), reqId, exec(6, null), FILE);
		assertState(AcctConst.RS_VERIFY, 7, 7);
		// 複驗退回後再次回報：主表與審查日期不重寫、EFFECT_DATE 不變
		verify(fx.acctDao, times(1)).applyAdd(any(), any(), any(), any());
		verify(fx.acctDao, times(1)).updateReviewDates(any(), any(), any(), any(), any());

		svc.verifyOk(id(CATHY), reqId, version(7));
		assertState(AcctConst.RS_APPROVED, null, 8);
		assertThat(fx.req.getVerifyBy()).isEqualTo(CATHY);
		assertThat(fx.histActions()).containsExactly("SUBMIT", "APPROVE", "APPROVE", "APPROVE", "APPROVE", "EXEC_DONE",
				"VERIFY_REJECT", "EXEC_DONE", "VERIFY_OK");
		assertThat(fx.hists.get(0).getStageId()).isEqualTo(1);
		assertThat(fx.hists.get(0).getResultDescr()).contains("申請原因說明").contains("公文.pdf");
		assertThat(fx.txm.rollbacks).isZero();
		assertThat(fx.txm.commits).isEqualTo(9);
		// 複驗通過不寄信（C02）
		verify(fx.notificationService, never()).requestEvent(eq("VERIFY_OK"), anyString(), anyString());
	}

	@Test
	void 越權_無關卡權限403_非目前關卡403_狀態不變() {
		submitAdd();
		assertStatus(() -> svc.approve(id(SAGE), FIRST_ID, version(0)), HttpStatus.FORBIDDEN, AcctMsg.P01);
		approve(ALEX);
		// 關卡 3 時 Alex（只有 APPR_G2）核准
		assertStatus(() -> svc.approve(id(ALEX), FIRST_ID, version(1)), HttpStatus.FORBIDDEN, AcctMsg.P01);
		assertState(AcctConst.RS_REVIEW, 3, 1);
	}

	@Test
	void 同關併發_同版本兩次_第二次409_只有一筆核准() {
		submitAdd();
		svc.approve(id(ALEX), FIRST_ID, version(0));
		assertStatus(() -> svc.approve(id(ALAN), FIRST_ID, version(0)), HttpStatus.CONFLICT, AcctMsg.E08);
		assertThat(fx.histActions()).containsExactly("SUBMIT", "APPROVE");
	}

	@Test
	void 帶條件UPDATE影響0筆_409且交易回復() {
		submitAdd();
		when(fx.acctReqDao.approve(anyString(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
				anyString(), org.mockito.ArgumentMatchers.anyInt(), any(), any())).thenReturn(0);
		assertStatus(() -> svc.approve(id(ALEX), FIRST_ID, version(0)), HttpStatus.CONFLICT, AcctMsg.E08);
		assertThat(fx.txm.rollbacks).isEqualTo(1);
	}

	@Test
	void 未帶版本號_400_E02() {
		submitAdd();
		assertStatus(() -> svc.approve(id(ALEX), FIRST_ID, null), HttpStatus.BAD_REQUEST, AcctMsg.E02_PREFIX + "版本號");
	}

	@Test
	void 名單變更_角色停用即無權限() {
		submitAdd();
		approve(ALEX);
		// Terry 的 INFRA_MGR STATUS＝0：只剩 GENERAL 權限
		Identity terryNow = idWith(TERRY, Set.copyOf(TestFixture.ROLE_PERMS.get("GENERAL")), List.of("DB"));
		assertStatus(() -> svc.approve(terryNow, FIRST_ID, version(1)), HttpStatus.FORBIDDEN, AcctMsg.P01);
		svc.approve(id(TestFixture.WENSEN), FIRST_ID, version(1));
		assertState(AcctConst.RS_REVIEW, 4, 2);
	}

	// ------------------------------------------------------------
	// 關卡 6
	// ------------------------------------------------------------

	@Test
	void 回報執行_非該類別執行人403_佐證必填E12_門禁卡號必填E13() {
		toExec(dbForm());
		assertStatus(() -> svc.execDone(id(SAGE), FIRST_ID, exec(4, null), FILE), HttpStatus.FORBIDDEN, AcctMsg.P01);
		assertStatus(() -> svc.execDone(id(MICHAEL), FIRST_ID, exec(4, null), null), HttpStatus.BAD_REQUEST, AcctMsg.E12);
		assertState(AcctConst.RS_EXEC, 6, 4);

		fx = new TestFixture();
		svc = fx.requestService;
		toExec(doorForm());
		assertStatus(() -> svc.execDone(id(ALEX), FIRST_ID, exec(4, " "), FILE), HttpStatus.BAD_REQUEST, AcctMsg.E13);
		svc.execDone(id(ALEX), FIRST_ID, exec(4, "CARD-001"), FILE);
		assertThat(fx.req.getCardNo()).isEqualTo("CARD-001");
		verify(fx.acctDao).applyAdd(any(), eq("CARD-001"), any(), eq(ALEX));
		// 申請單 ACCT_NAME 同句寫入卡號（複驗通知與待辦標題才會顯示卡號）
		verify(fx.acctReqDao).execDone(eq(FIRST_ID), eq(4), any(), eq(true), eq("CARD-001"), any(), any(), any());
		// 門禁卡強制符合審查條件
		verify(fx.acctDao).updateReviewDates(FIRST_ID, TODAY, TODAY.plusDays(180), TestFixture.NOW, ALEX);
	}

	@Test
	void 回報執行_驗證順序同前端_格式_E13_E12_E11_缺版本_E08() {
		toExec(doorForm());
		MultipartFile exe = new MockMultipartFile("f", "x.exe", "application/octet-stream", new byte[] { 0x25, 0x50, 0x44, 0x46 });
		// AttachmentService 為 mock：以格式錯誤代表附件檢查失敗（檢查本身的順序見 AttachmentServiceTest）
		when(fx.attachmentService.check(exe)).thenThrow(ApiException.badRequest(AcctMsg.E14));
		// 每一步都同時帶著後面的錯誤（版本 3 已過期），只能回當步的錯誤
		// MICHAEL 為 DB 執行人、非門禁卡執行人：帶著所有 400 錯誤仍先回 403
		assertStatus(() -> svc.execDone(id(MICHAEL), FIRST_ID, exec(3, null), exe), HttpStatus.FORBIDDEN, AcctMsg.P01);
		assertStatus(() -> svc.execDone(id(ALEX), FIRST_ID, exec(3, null), exe), HttpStatus.BAD_REQUEST, AcctMsg.E14);
		assertStatus(() -> svc.execDone(id(ALEX), FIRST_ID, exec(3, null), null), HttpStatus.BAD_REQUEST, AcctMsg.E13);
		// 卡號超過 50 bytes 且缺佐證：先回 E12
		assertStatus(() -> svc.execDone(id(ALEX), FIRST_ID, exec(3, "C".repeat(51)), null), HttpStatus.BAD_REQUEST, AcctMsg.E12);
		// 卡號超過 50 bytes 且缺版本號：先回 E11
		ExecDoneForm tooLongNoVersion = exec(4, "C".repeat(51));
		tooLongNoVersion.setVersionNo(null);
		assertStatus(() -> svc.execDone(id(ALEX), FIRST_ID, tooLongNoVersion, FILE), HttpStatus.BAD_REQUEST,
				AcctMsg.E11_PREFIX + "門禁卡號");
		assertStatus(() -> svc.execDone(id(ALEX), FIRST_ID, exec(3, "C".repeat(51)), FILE), HttpStatus.BAD_REQUEST,
				AcctMsg.E11_PREFIX + "門禁卡號");
		ExecDoneForm noVersion = exec(4, "CARD-001");
		noVersion.setVersionNo(null);
		assertStatus(() -> svc.execDone(id(ALEX), FIRST_ID, noVersion, FILE), HttpStatus.BAD_REQUEST,
				AcctMsg.E02_PREFIX + RequestService.LABEL_VERSION);
		assertStatus(() -> svc.execDone(id(ALEX), FIRST_ID, exec(3, "CARD-001"), FILE), HttpStatus.CONFLICT, AcctMsg.E08);
		assertState(AcctConst.RS_EXEC, 6, 4);
	}

	@Test
	void 門禁卡_複驗退回後換卡號_主表帳號名稱同步() {
		toExec(doorForm());
		svc.execDone(id(ALEX), FIRST_ID, exec(4, "CARD-001"), FILE);
		svc.verifyReject(id(MOLLY), FIRST_ID, reason(5, "卡號錯"));
		svc.execDone(id(ALAN), FIRST_ID, exec(6, "CARD-002"), FILE);
		// 第二次回報同樣以新卡號覆寫申請單卡號與帳號名稱
		verify(fx.acctReqDao).execDone(eq(FIRST_ID), eq(6), any(), eq(true), eq("CARD-002"), any(), any(), any());
		verify(fx.acctDao).updateAcctName(FIRST_ID, "CARD-002", TestFixture.NOW, ALAN);
		verify(fx.acctDao, times(1)).applyAdd(any(), any(), any(), any());
		assertThat(fx.req.getAcctName()).isEqualTo("CARD-002");
	}

	@Test
	void 修改_第一次回報寫主表_新內容符合且舊不符合才寫審查日期() {
		Acct a = fx.acct("AR20261001-001", "HOST", AcctConst.AS_APPROVED);
		AcctReqForm f = modifyForm(a.getAcctId());
		f.setAssetClassId("H01");
		svc.submit(id(KEVIN), f, null);
		toExecFromReview();
		svc.execDone(id(ALEX), FIRST_ID, exec(4, null), FILE);
		// 非門禁卡新增：不覆寫申請單卡號與帳號名稱
		verify(fx.acctReqDao).execDone(eq(FIRST_ID), eq(4), any(), eq(false), isNull(), any(), any(), any());
		ArgumentCaptor<AcctReq> cap = ArgumentCaptor.forClass(AcctReq.class);
		verify(fx.acctDao).applyModify(cap.capture(), eq(true), any(), eq(ALEX));
		assertThat(cap.getValue().getAssetClassId()).isEqualTo("H01");
		// 鎖定欄位取自主表
		assertThat(cap.getValue().getAcctName()).isEqualTo(a.getAcctName());
		verify(fx.acctDao).updateReviewDates(eq(a.getAcctId()), eq(TODAY), eq(TODAY.plusDays(180)), any(), any());
	}

	@Test
	void 修改_舊內容已符合且已有審查日_不重寫() {
		Acct a = fx.acct("AR20261001-001", "HOST", AcctConst.AS_APPROVED);
		a.setAssetClassId("H01");
		a.setLastReviewDate(LocalDateTime.of(2026, 9, 1, 0, 0));
		AcctReqForm f = modifyForm(a.getAcctId());
		f.setAssetClassId("H05");
		svc.submit(id(KEVIN), f, null);
		toExecFromReview();
		svc.execDone(id(ALEX), FIRST_ID, exec(4, null), FILE);
		verify(fx.acctDao, never()).updateReviewDates(any(), any(), any(), any(), any());
	}

	@Test
	void 停用與刪除_主表改狀態_內容複製主表() {
		Acct a = fx.acct("AR20261001-001", "HOST", AcctConst.AS_EXPIRED);
		AcctReqForm f = reasonOnlyForm(AcctConst.REQ_DISABLE, a.getAcctId());
		svc.submit(id(KEVIN), f, null);
		assertThat(fx.req.getHolderName()).isEqualTo(a.getHolderName());
		assertThat(fx.req.getApplyReason()).isEqualTo("不再使用");
		toExecFromReview();
		svc.execDone(id(ALEX), FIRST_ID, exec(4, null), FILE);
		verify(fx.acctDao).applyState(a.getAcctId(), AcctConst.AS_DISABLED, "不再使用", TestFixture.NOW, ALEX);
		verify(fx.acctDao, never()).updateReviewDates(any(), any(), any(), any(), any());
	}

	@Test
	void revive_永久或到期日不早於今天才改回已核准() {
		AcctReq r = new AcctReq();
		r.setExpiryTypeCode("PERM");
		assertThat(RequestService.revive(r, TODAY)).isTrue();
		r.setExpiryTypeCode("DATE");
		r.setExpiryDate(TODAY.atStartOfDay());
		assertThat(RequestService.revive(r, TODAY)).isTrue();
		r.setExpiryDate(TODAY.minusDays(1).atStartOfDay());
		assertThat(RequestService.revive(r, TODAY)).isFalse();
	}

	// ------------------------------------------------------------
	// 送出驗證（spec §4.3）
	// ------------------------------------------------------------

	@Test
	void 送出驗證順序_E01先於E02() {
		AcctReqForm f = new AcctReqForm();
		f.setReqTypeCode("ADD");
		assertStatus(() -> svc.submit(id(KEVIN), f, null), HttpStatus.BAD_REQUEST, AcctMsg.E01);
		f.setIsUniqueConf(1);
		assertStatus(() -> svc.submit(id(KEVIN), f, null), HttpStatus.BAD_REQUEST,
				"請填寫必填欄位：申請單位、帳號類別、帳號名稱、所屬系統、使用人、申請原因、有效期限類型");
	}

	@Test
	void 送出驗證順序同前端_403_類型_404_附件_E01_內容_E09_E07() {
		Acct a = fx.acct("AR20261001-001", "HOST", AcctConst.AS_EXPIRED);
		when(fx.acctReqDao.countInFlight(a.getAcctId(), null)).thenReturn(1L);
		// 空的 .exe 公文：AttachmentService 為 mock，以丟 E14 代表附件檢查失敗（空檔副檔名不符回 E14 見 AttachmentServiceTest）
		MultipartFile emptyExe = new MockMultipartFile("docFile", "x.exe", "application/octet-stream", new byte[0]);
		when(fx.attachmentService.check(emptyExe)).thenThrow(ApiException.badRequest(AcctMsg.E14));
		// 每一步都同時帶著後面的錯誤（未勾唯一性聲明、缺申請單位、帳號已逾期 E09、同帳號在途 E07），只能回當步的錯誤
		AcctReqForm f = modifyForm("AR20991231-999");
		f.setIsUniqueConf(null);
		f.setApplyDept(null);
		Identity noApply = idWith(KEVIN, Set.of(), List.of());
		assertStatus(() -> svc.submit(noApply, f, emptyExe), HttpStatus.FORBIDDEN, AcctMsg.P01);
		f.setReqTypeCode(null);
		assertStatus(() -> svc.submit(id(KEVIN), f, emptyExe), HttpStatus.BAD_REQUEST, AcctMsg.E02_PREFIX + "申請類型");
		// 新增帶帳號、異動缺帳號：同樣先於附件與 E01
		f.setReqTypeCode("ADD");
		assertStatus(() -> svc.submit(id(KEVIN), f, emptyExe), HttpStatus.BAD_REQUEST, AcctMsg.ADD_WITH_ACCT);
		f.setReqTypeCode("MODIFY");
		f.setAcctId(null);
		assertStatus(() -> svc.submit(id(KEVIN), f, emptyExe), HttpStatus.BAD_REQUEST, AcctMsg.E02_PREFIX + "帳號");
		f.setAcctId("AR20991231-999");
		assertStatus(() -> svc.submit(id(KEVIN), f, emptyExe), HttpStatus.NOT_FOUND, AcctMsg.N01);
		f.setAcctId(a.getAcctId());
		assertStatus(() -> svc.submit(id(KEVIN), f, emptyExe), HttpStatus.BAD_REQUEST, AcctMsg.E14);
		assertStatus(() -> svc.submit(id(KEVIN), f, null), HttpStatus.BAD_REQUEST, AcctMsg.E01);
		f.setIsUniqueConf(1);
		assertStatus(() -> svc.submit(id(KEVIN), f, null), HttpStatus.BAD_REQUEST, AcctMsg.E02_PREFIX + "申請單位");
		f.setApplyDept("資訊治理組");
		assertStatus(() -> svc.submit(id(KEVIN), f, null), HttpStatus.CONFLICT, AcctMsg.E09);
		a.setAcctStateCode(AcctConst.AS_APPROVED);
		assertStatus(() -> svc.submit(id(KEVIN), f, null), HttpStatus.CONFLICT, AcctMsg.E07);
		verify(fx.acctReqDao, never()).insert(any(), any(), any());
	}

	@Test
	void 送出驗證_E03_E04_E05_E06_E11() {
		AcctReqForm f = dbForm();
		f.setExpiryDate(null);
		assertStatus(() -> svc.submit(id(KEVIN), f, null), HttpStatus.BAD_REQUEST, AcctMsg.E03);
		f.setExpiryDate(TODAY.minusDays(1).toString());
		assertStatus(() -> svc.submit(id(KEVIN), f, null), HttpStatus.BAD_REQUEST, AcctMsg.E04);
		f.setExpiryDate(TODAY.toString());
		f.setCatgId("VENDOR");
		assertStatus(() -> svc.submit(id(KEVIN), f, null), HttpStatus.BAD_REQUEST, AcctMsg.E05);
		f.setCatgId("DB");
		f.setAssetClassId("H07");
		assertStatus(() -> svc.submit(id(KEVIN), f, null), HttpStatus.BAD_REQUEST, AcctMsg.E06);
		f.setAssetClassId("H01");
		f.setApplyDept("單".repeat(51));
		assertStatus(() -> svc.submit(id(KEVIN), f, null), HttpStatus.BAD_REQUEST, AcctMsg.E11_PREFIX + "申請單位");
		f.setApplyDept("單".repeat(50));
		f.setHolderEmail(null);
		svc.submit(id(KEVIN), f, null);
	}

	@Test
	void 送出驗證_申請單位欄寬E11排在E03與E06之後() {
		AcctReqForm f = dbForm();
		f.setApplyDept("單".repeat(51));
		f.setExpiryDate(null);
		assertStatus(() -> svc.submit(id(KEVIN), f, null), HttpStatus.BAD_REQUEST, AcctMsg.E03);
		f.setExpiryDate(TODAY.toString());
		f.setAssetClassId("H07");
		assertStatus(() -> svc.submit(id(KEVIN), f, null), HttpStatus.BAD_REQUEST, AcctMsg.E06);
		f.setAssetClassId("H01");
		assertStatus(() -> svc.submit(id(KEVIN), f, null), HttpStatus.BAD_REQUEST, AcctMsg.E11_PREFIX + "申請單位");
	}

	@Test
	void 門禁卡新增_後端強制值() {
		AcctReqForm f = doorForm();
		f.setSysName("亂填");
		f.setAcctName("亂填");
		f.setAssetClassId("H01");
		f.setAccessLevelCode("ADMIN");
		f.setHolderCntct("#9999");
		svc.submit(id(KEVIN), f, null);
		assertThat(fx.req.getAcctName()).isNull();
		assertThat(fx.req.getSysName()).isEqualTo(AcctConst.DOOR_SYS_NAME);
		assertThat(fx.req.getAssetClassId()).isNull();
		assertThat(fx.req.getAccessLevelCode()).isEqualTo("DOOR");
		assertThat(fx.req.getHolderCntct()).isNull();
		assertThat(fx.req.getHolderEmail()).isEqualTo("holder@example.com");
	}

	@Test
	void 新增不可帶帳號_異動必帶帳號() {
		AcctReqForm f = dbForm();
		f.setAcctId("AR20261001-001");
		assertStatus(() -> svc.submit(id(KEVIN), f, null), HttpStatus.BAD_REQUEST, AcctMsg.ADD_WITH_ACCT);
		AcctReqForm m = modifyForm(null);
		assertStatus(() -> svc.submit(id(KEVIN), m, null), HttpStatus.BAD_REQUEST, AcctMsg.E02_PREFIX + "帳號");
		AcctReqForm n = modifyForm("AR20991231-999");
		assertStatus(() -> svc.submit(id(KEVIN), n, null), HttpStatus.NOT_FOUND, AcctMsg.N01);
	}

	@Test
	void 前提不符409_E09_已逾期不可修改與重新啟用() {
		Acct a = fx.acct("AR20261001-001", "HOST", AcctConst.AS_EXPIRED);
		assertStatus(() -> svc.submit(id(KEVIN), modifyForm(a.getAcctId()), null), HttpStatus.CONFLICT, AcctMsg.E09);
		AcctReqForm en = modifyForm(a.getAcctId());
		en.setReqTypeCode(AcctConst.REQ_ENABLE);
		assertStatus(() -> svc.submit(id(KEVIN), en, null), HttpStatus.CONFLICT, AcctMsg.E09);
		a.setAcctStateCode(AcctConst.AS_DELETED);
		assertStatus(() -> svc.submit(id(KEVIN), reasonOnlyForm(AcctConst.REQ_DELETE, a.getAcctId()), null),
				HttpStatus.CONFLICT, AcctMsg.E09);
	}

	@Test
	void 在途409_E07_先查與唯一索引ORA00001() {
		Acct a = fx.acct("AR20261001-001", "HOST", AcctConst.AS_APPROVED);
		when(fx.acctReqDao.countInFlight(a.getAcctId(), null)).thenReturn(1L);
		assertStatus(() -> svc.submit(id(KEVIN), modifyForm(a.getAcctId()), null), HttpStatus.CONFLICT, AcctMsg.E07);

		when(fx.acctReqDao.countInFlight(a.getAcctId(), null)).thenReturn(0L);
		doThrow(new DuplicateKeyException(DUP_IN_FLIGHT)).when(fx.acctReqDao).insert(any(), any(), any());
		assertStatus(() -> svc.submit(id(KEVIN), modifyForm(a.getAcctId()), FILE), HttpStatus.CONFLICT, AcctMsg.E07);
		assertThat(fx.txm.rollbacks).isEqualTo(1);
		verify(fx.attachmentService).cleanup(any());
	}

	@Test
	void 送出_非在途索引的唯一鍵衝突_不回E07_原樣往上拋() {
		Acct a = fx.acct("AR20261001-001", "HOST", AcctConst.AS_APPROVED);
		DuplicateKeyException other = new DuplicateKeyException(DUP_OTHER);
		doThrow(other).when(fx.acctReqDao).insert(any(), any(), any());
		assertRethrown(() -> svc.submit(id(KEVIN), modifyForm(a.getAcctId()), FILE), other);
		assertThat(fx.txm.rollbacks).isEqualTo(1);
		verify(fx.attachmentService).cleanup(any());
		verify(fx.notificationService, never()).requestEvent(any(), any(), any());
	}

	@Test
	void 送出新增_主表主鍵衝突_不回E07_原樣往上拋() {
		DuplicateKeyException other = new DuplicateKeyException("ORA-00001: unique constraint (RD_USER.PK_IM_ACCT) violated");
		doThrow(other).when(fx.acctDao).insertOnSubmit(any(), any(), any());
		assertRethrown(() -> svc.submit(id(KEVIN), dbForm(), FILE), other);
		assertThat(fx.txm.rollbacks).isEqualTo(1);
		verify(fx.attachmentService).cleanup(any());
		verify(fx.notificationService, never()).requestEvent(any(), any(), any());
	}

	@Test
	void 在途判斷_只認括號內的完整索引名() {
		// 外層只有 SQL 摘要、索引名在 cause（Spring 實際形式之一）
		assertThat(RequestService.isInFlightDup(new DuplicateKeyException("PreparedStatementCallback",
				new SQLIntegrityConstraintViolationException(DUP_IN_FLIGHT)))).isTrue();
		// Oracle 23ai 格式：後面多帶表與欄位
		assertThat(RequestService.isInFlightDup(new DuplicateKeyException(
				"ORA-00001: unique constraint (RD_USER.IND_IM_ACCT_REQ_04) violated on table RD_USER.IM_ACCT_REQ columns (ACCT_ID)")))
				.isTrue();
		// 繁中 NLS 訊息：括號為半形（2026-10-10 於測試 DB 以 SQLERRM(-1) 實測範本「(.)」三字元為 U+0028／U+002E／U+0029）
		assertThat(RequestService.isInFlightDup(new DuplicateKeyException(
				"ORA-00001: 違反必須為唯一的限制條件 (RD_USER.IND_IM_ACCT_REQ_04)"))).isTrue();
		assertThat(RequestService.isInFlightDup(new DuplicateKeyException(null))).isFalse();
		assertThat(RequestService.isInFlightDup(new DuplicateKeyException(DUP_OTHER))).isFalse();
		// 前綴相同的其他索引名
		assertThat(RequestService.isInFlightDup(new DuplicateKeyException(
				"ORA-00001: unique constraint (RD_USER.IND_IM_ACCT_REQ_040) violated"))).isFalse();
		// SQL 原文的 hint 提到索引名、實際衝突的是別的主鍵
		assertThat(RequestService.isInFlightDup(new DuplicateKeyException(
				"SQL [INSERT /*+ INDEX(R IND_IM_ACCT_REQ_04) */ INTO IM_ACCT_REQ ...]; ORA-00001: unique constraint (RD_USER.PK_IM_ACCT_REQ) violated")))
				.isFalse();
	}

	@Test
	void 重新送出_唯一鍵衝突_在途索引回E07_其他原樣往上拋() {
		submitAdd();
		approve(ALEX);
		svc.reject(id(TERRY), FIRST_ID, reason(1, "用途不明"));
		AcctReqForm f = dbForm();
		f.setVersionNo(2);
		f.setApplyReason("補充說明後重送");
		// 實際的 Spring 例外：外層訊息是 SQL 摘要，索引名在 cause（SQLException）的訊息裡
		doThrow(new DuplicateKeyException("PreparedStatementCallback", new SQLIntegrityConstraintViolationException(DUP_IN_FLIGHT)))
				.when(fx.acctReqDao).resubmit(any(), anyInt(), any(), any(), any());
		assertStatus(() -> svc.resubmit(id(KEVIN), FIRST_ID, f, FILE), HttpStatus.CONFLICT, AcctMsg.E07);

		DuplicateKeyException other = new DuplicateKeyException(DUP_OTHER);
		doThrow(other).when(fx.acctReqDao).resubmit(any(), anyInt(), any(), any(), any());
		assertRethrown(() -> svc.resubmit(id(KEVIN), FIRST_ID, f, FILE), other);
		assertThat(fx.txm.rollbacks).isEqualTo(2);
		verify(fx.attachmentService, times(2)).cleanup(any());
		verify(fx.notificationService, never()).requestEvent(eq(AcctConst.EV_RESUB), any(), any());
	}

	private static void assertRethrown(Runnable call, RuntimeException expected) {
		try {
			call.run();
			fail("應丟例外");
		} catch (RuntimeException e) {
			assertThat(e).isSameAs(expected);
		}
	}

	@Test
	void 交易內失敗_整筆回復並清除附件_不寄信() {
		doThrow(new IllegalStateException("db down")).when(fx.acctHistDao).insert(any());
		try {
			svc.submit(id(KEVIN), dbForm(), FILE);
			fail("應丟例外");
		} catch (IllegalStateException expected) {
			// ok
		}
		assertThat(fx.txm.rollbacks).isEqualTo(1);
		verify(fx.attachmentService).cleanup(any());
		verify(fx.notificationService, never()).requestEvent(any(), any(), any());
	}

	// ------------------------------------------------------------
	// 退件與重送
	// ------------------------------------------------------------

	@Test
	void 重新送出驗證順序同前端_403_404_附件_E01_內容_缺版本_E08_E09_E07() {
		Acct a = fx.acct("AR20261001-001", "HOST", AcctConst.AS_APPROVED);
		svc.submit(id(KEVIN), modifyForm(a.getAcctId()), null);
		approve(ALEX);
		svc.reject(id(TERRY), FIRST_ID, reason(1, "用途不明"));
		MultipartFile exe = new MockMultipartFile("docFile", "x.exe", "application/octet-stream", new byte[] { 0x25, 0x50, 0x44, 0x46 });
		when(fx.attachmentService.check(exe)).thenThrow(ApiException.badRequest(AcctMsg.E14));
		// 每一步都同時帶著後面的錯誤（未勾唯一性聲明、缺申請單位、缺版本號），只能回當步的錯誤
		AcctReqForm f = modifyForm(a.getAcctId());
		f.setIsUniqueConf(null);
		f.setApplyDept(null);
		f.setVersionNo(null);
		fx.accts.remove(a.getAcctId());
		assertStatus(() -> svc.resubmit(id(SAGE), FIRST_ID, f, exe), HttpStatus.FORBIDDEN, AcctMsg.P01);
		assertStatus(() -> svc.resubmit(id(KEVIN), FIRST_ID, f, exe), HttpStatus.NOT_FOUND, AcctMsg.N01);
		fx.accts.put(a.getAcctId(), a);
		assertStatus(() -> svc.resubmit(id(KEVIN), FIRST_ID, f, exe), HttpStatus.BAD_REQUEST, AcctMsg.E14);
		assertStatus(() -> svc.resubmit(id(KEVIN), FIRST_ID, f, null), HttpStatus.BAD_REQUEST, AcctMsg.E01);
		f.setIsUniqueConf(1);
		assertStatus(() -> svc.resubmit(id(KEVIN), FIRST_ID, f, null), HttpStatus.BAD_REQUEST, AcctMsg.E02_PREFIX + "申請單位");
		// 版本過期且缺申請單位：內容錯誤先於 409 E08
		f.setVersionNo(1);
		assertStatus(() -> svc.resubmit(id(KEVIN), FIRST_ID, f, null), HttpStatus.BAD_REQUEST, AcctMsg.E02_PREFIX + "申請單位");
		f.setVersionNo(null);
		f.setApplyDept("資訊治理組");
		assertStatus(() -> svc.resubmit(id(KEVIN), FIRST_ID, f, null), HttpStatus.BAD_REQUEST,
				AcctMsg.E02_PREFIX + RequestService.LABEL_VERSION);
		// 版本過期且帳號已逾期：先回 409 E08
		f.setVersionNo(1);
		a.setAcctStateCode(AcctConst.AS_EXPIRED);
		assertStatus(() -> svc.resubmit(id(KEVIN), FIRST_ID, f, null), HttpStatus.CONFLICT, AcctMsg.E08);
		// 帳號已逾期且同帳號在途：先回 E09
		f.setVersionNo(2);
		when(fx.acctReqDao.countInFlight(a.getAcctId(), FIRST_ID)).thenReturn(1L);
		assertStatus(() -> svc.resubmit(id(KEVIN), FIRST_ID, f, null), HttpStatus.CONFLICT, AcctMsg.E09);
		a.setAcctStateCode(AcctConst.AS_APPROVED);
		assertStatus(() -> svc.resubmit(id(KEVIN), FIRST_ID, f, null), HttpStatus.CONFLICT, AcctMsg.E07);
		assertThat(fx.req.getReqStateCode()).isEqualTo(AcctConst.RS_REJECTED);
		assertThat(fx.req.getVersionNo()).isEqualTo(2);
	}

	@Test
	void 退件原因必填E10_退件後只有原申請人可重送() {
		submitAdd();
		approve(ALEX);
		assertStatus(() -> svc.reject(id(TERRY), FIRST_ID, reason(1, " ")), HttpStatus.BAD_REQUEST, AcctMsg.E10_PREFIX + "原因");
		ReqActionResult r = svc.reject(id(TERRY), FIRST_ID, reason(1, "用途不明"));
		assertThat(r.getReqStateCode()).isEqualTo(AcctConst.RS_REJECTED);
		assertThat(fx.req.getRejectCnt()).isEqualTo(1);
		assertThat(fx.req.getRejectStageId()).isEqualTo(3);
		assertThat(fx.req.getRejectBy()).isEqualTo(TERRY);
		verify(fx.acctDao).markAddRejected(FIRST_ID, TestFixture.NOW, TERRY);
		verify(fx.notificationService).requestEvent(AcctConst.EV_REJECT, FIRST_ID, TERRY);

		AcctReqForm f = dbForm();
		f.setVersionNo(2);
		f.setApplyReason("補充說明後重送");
		assertStatus(() -> svc.resubmit(id(SAGE), FIRST_ID, f, null), HttpStatus.FORBIDDEN, AcctMsg.P01);
		ReqActionResult rr = svc.resubmit(id(KEVIN), FIRST_ID, f, FILE);
		assertThat(rr.getReqStateCode()).isEqualTo(AcctConst.RS_REVIEW);
		assertThat(rr.getCurrStageId()).isEqualTo(2);
		assertThat(fx.req.getResubCnt()).isEqualTo(1);
		verify(fx.acctDao).resubmitAdd(any(), any(), eq(KEVIN));
		verify(fx.attachDao).deactivate(FIRST_ID, AcctConst.ATTACH_DOC, TestFixture.NOW, KEVIN);
		assertThat(fx.histActions()).endsWith("REJECT", "RESUB");
		verify(fx.notificationService).requestEvent(AcctConst.EV_RESUB, FIRST_ID, KEVIN);
	}

	// ------------------------------------------------------------
	// 歷史：IS_SELF_ACTION、切換身分
	// ------------------------------------------------------------

	@Test
	void 自行核准_申請人自己核准關卡4() {
		submitAdd();
		approve(ALEX);
		approve(TERRY);
		svc.approve(id(KEVIN), FIRST_ID, version(2));
		AcctHist h = fx.hists.get(3);
		assertThat(h.getIsSelfAction()).isEqualTo(1);
		h.setReqApplyBy(KEVIN);
		assertThat(RequestService.selfLabel(h)).isEqualTo(AcctConst.SELF_LABEL_APPLICANT);
		assertThat(fx.hists.get(1).getIsSelfAction()).isZero();
	}

	@Test
	void 同人兼任_關卡3核准後又執行關卡6() {
		toExec(dbForm());
		svc.execDone(id(TERRY), FIRST_ID, exec(4, null), FILE);
		AcctHist h = fx.hists.get(fx.hists.size() - 1);
		assertThat(h.getActionCode()).isEqualTo("EXEC_DONE");
		assertThat(h.getIsSelfAction()).isEqualTo(1);
		h.setReqApplyBy(KEVIN);
		assertThat(RequestService.selfLabel(h)).isEqualTo(AcctConst.SELF_LABEL_SAME_PERSON);
	}

	@Test
	void selfAction_只判斷五種動作_以有效身分比對_同關不算() {
		AcctHist prior = new AcctHist();
		prior.setActionCode("APPROVE");
		prior.setStageId(3);
		prior.setActBy(HANK);
		prior.setActAsId(TERRY);
		assertThat(RequestService.selfAction("SUBMIT", KEVIN, KEVIN, 1, List.of())).isZero();
		assertThat(RequestService.selfAction("APPROVE", KEVIN, KEVIN, 4, List.of())).isEqualTo(1);
		assertThat(RequestService.selfAction("EXEC_DONE", TERRY, KEVIN, 6, List.of(prior))).isEqualTo(1);
		assertThat(RequestService.selfAction("EXEC_DONE", HANK, KEVIN, 6, List.of(prior))).isZero();
		assertThat(RequestService.selfAction("REJECT", TERRY, KEVIN, 3, List.of(prior))).isZero();
	}

	@Test
	void 切換身分核准_ACT_BY為實際操作人_ACT_AS為代理身分_業務欄位記有效身分() {
		toStage5();
		svc.approve(impersonate(HANK, GARY), FIRST_ID, version(3));
		AcctHist h = fx.hists.get(fx.hists.size() - 1);
		assertThat(h.getActBy()).isEqualTo(HANK);
		assertThat(h.getActByName()).isEqualTo("Hank (黃志翰)");
		assertThat(h.getActAsId()).isEqualTo(GARY);
		assertThat(h.getActAsName()).isEqualTo("Gary (許永成)");
		assertThat(h.getCreateBy()).isEqualTo(HANK);
		assertState(AcctConst.RS_EXEC, 6, 4);
	}

	// ------------------------------------------------------------
	// 明細與動作
	// ------------------------------------------------------------

	@Test
	void 明細_他人403_可做動作依目前關卡() {
		submitAdd();
		assertStatus(() -> svc.detail(id(TESTER), FIRST_ID), HttpStatus.FORBIDDEN, AcctMsg.P01);
		AcctReqDetail d = svc.detail(id(ALEX), FIRST_ID);
		assertThat(d.getActions()).containsExactly(RequestService.DO_APPROVE, RequestService.DO_REJECT);
		assertThat(svc.detail(id(KEVIN), FIRST_ID).getActions()).isEmpty();
		// 曾在此單操作過的人（Alex 核准後）仍可看
		approve(ALEX);
		assertThat(svc.detail(id(ALEX), FIRST_ID).getActions()).isEmpty();
	}

	@Test
	void 我的申請單_需ACC_SELF_SERVICE_狀態碼檢查() {
		Identity none = idWith(TESTER, Set.of(), List.of());
		assertStatus(() -> svc.listMine(none, null), HttpStatus.FORBIDDEN, AcctMsg.P01);
		assertStatus(() -> svc.listMine(id(TESTER), "VOID"), HttpStatus.BAD_REQUEST, AcctMsg.BAD_CODE_PREFIX + "狀態");
	}

	// ------------------------------------------------------------
	// 輔助
	// ------------------------------------------------------------

	void submitAdd() {
		svc.submit(id(KEVIN), dbForm(), null);
		fx.acct(FIRST_ID, fx.req.getCatgId(), AcctConst.AS_REVIEW);
	}

	void toStage5() {
		submitAdd();
		approve(ALEX);
		approve(TERRY);
		approve(CATHY);
	}

	void toExec(AcctReqForm f) {
		svc.submit(id(KEVIN), f, null);
		fx.acct(FIRST_ID, f.getCatgId(), AcctConst.AS_REVIEW);
		toExecFromReview();
	}

	void toExecFromReview() {
		approve(ALEX);
		approve(TERRY);
		approve(CATHY);
		approve(GARY);
	}

	void approve(String userId) {
		svc.approve(id(userId), FIRST_ID, version(fx.req.getVersionNo()));
	}

	void assertState(String state, Integer stage, int version) {
		assertThat(fx.req.getReqStateCode()).isEqualTo(state);
		assertThat(fx.req.getCurrStageId()).isEqualTo(stage);
		assertThat(fx.req.getVersionNo()).isEqualTo(version);
	}

	static VersionForm version(Integer v) {
		VersionForm f = new VersionForm();
		f.setVersionNo(v);
		return f;
	}

	static ReasonForm reason(int v, String reason) {
		ReasonForm f = new ReasonForm();
		f.setVersionNo(v);
		f.setReason(reason);
		return f;
	}

	static ExecDoneForm exec(int v, String cardNo) {
		ExecDoneForm f = new ExecDoneForm();
		f.setVersionNo(v);
		f.setCardNo(cardNo);
		f.setExecDescr("已建立");
		return f;
	}

	static AcctReqForm dbForm() {
		AcctReqForm f = new AcctReqForm();
		f.setReqTypeCode("ADD");
		f.setApplyDept("資訊治理組");
		f.setCatgId("DB");
		f.setAcctName("db_user01");
		f.setSysName("財務 Oracle");
		f.setAssetClassId("H01");
		f.setIsPriv(1);
		f.setHolderName("Kevin");
		f.setAccessLevelCode("ADMIN");
		f.setApplyReason("申請原因說明");
		f.setExpiryTypeCode("DATE");
		f.setExpiryDate(TODAY.plusDays(60).toString());
		f.setIsUniqueConf(1);
		return f;
	}

	static AcctReqForm doorForm() {
		AcctReqForm f = new AcctReqForm();
		f.setReqTypeCode("ADD");
		f.setApplyDept("資訊治理組");
		f.setCatgId("DOOR");
		f.setDoorScopeCode("FULL");
		f.setHolderName("Kevin");
		f.setHolderEmail("holder@example.com");
		f.setApplyReason("機房巡檢");
		f.setExpiryTypeCode("PERM");
		f.setIsUniqueConf(1);
		return f;
	}

	static AcctReqForm modifyForm(String acctId) {
		AcctReqForm f = new AcctReqForm();
		f.setReqTypeCode("MODIFY");
		f.setAcctId(acctId);
		f.setApplyDept("資訊治理組");
		f.setAcctName("想改名");
		f.setAssetClassId("H02");
		f.setIsPriv(0);
		f.setHolderName("新使用人");
		f.setAccessLevelCode("RO");
		f.setApplyReason("調整");
		f.setExpiryTypeCode("PERM");
		f.setIsUniqueConf(1);
		return f;
	}

	static AcctReqForm reasonOnlyForm(String type, String acctId) {
		AcctReqForm f = new AcctReqForm();
		f.setReqTypeCode(type);
		f.setAcctId(acctId);
		f.setApplyDept("資訊治理組");
		f.setApplyReason("不再使用");
		f.setIsUniqueConf(1);
		return f;
	}

	static void assertStatus(Runnable action, HttpStatus status, String message) {
		try {
			action.run();
			fail("應丟 ApiException " + status);
		} catch (ApiException ex) {
			assertThat(ex.getStatus()).isEqualTo(status);
			if (message != null) {
				assertThat(ex.getMessage()).isEqualTo(message);
			}
		}
	}
}
