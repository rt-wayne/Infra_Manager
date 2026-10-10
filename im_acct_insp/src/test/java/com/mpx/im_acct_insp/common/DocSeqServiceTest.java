package com.mpx.im_acct_insp.common;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：取號單元測試（spec §3.1）：當日第一筆 INSERT、INSERT 衝突改 UPDATE、CAS 0 筆重讀重試、重試 10 次用盡、超過 999
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（spec_im_acct_insp_v2.0.md §4.1）：前綴改 AR／IN；改為自帶 mock DbClient（真 DocSeqDao），
//           並檢查 CREATE_DATE／UPDATE_DATE 綁定 Clock 時間（:now，不用 SYSDATE）
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.Invocation;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;

import com.mpx.common.db.DbClient;
import com.mpx.im_acct_insp.dao.DocSeqDao;
import com.mpx.im_acct_insp.model.DocSeq;
import com.mpx.im_acct_insp.support.TestFixture;

class DocSeqServiceTest {

	static final String DB = "itflow_test_alias";

	DbClient db;
	DocSeqService svc;

	@BeforeEach
	void setUp() {
		db = mock(DbClient.class);
		doReturn(1).when(db).update(anyString(), anyString(), anyMap());
		Clock clock = new TestFixture().clock;
		svc = new DocSeqService(new DocSeqDao(db, DB), clock);
	}

	@Test
	void 當日第一筆_INSERT_取001_時間綁定Clock() {
		assertThat(svc.nextId(AcctConst.DOC_AR, "0000001")).isEqualTo("AR20261007-001");
		Map<String, Object> p = updatesOf(DocSeqDao.INSERT_FIRST).get(0);
		assertThat(p).containsEntry("docTypeId", "AR").containsEntry("seqDate", "20261007")
				.containsEntry("now", Timestamp.valueOf(TestFixture.NOW));
		assertThat(updatesOf(DocSeqDao.UPDATE_CAS)).isEmpty();
		assertThat(DocSeqDao.INSERT_FIRST + DocSeqDao.UPDATE_CAS).doesNotContain("SYSDATE");
	}

	@Test
	void INSERT_PK衝突_重讀改走UPDATE() {
		doReturn(List.of(), List.of(seq(1))).when(db).query(eq(DB), eq(DocSeqDao.FIND_CURR_SEQ), anyMap(), eq(DocSeq.class));
		doThrow(new DuplicateKeyException("ORA-00001")).when(db).update(eq(DB), eq(DocSeqDao.INSERT_FIRST), anyMap());
		assertThat(svc.nextId(AcctConst.DOC_IN, "0000001")).isEqualTo("IN20261007-002");
		assertThat(updatesOf(DocSeqDao.UPDATE_CAS).get(0)).containsEntry("currSeq", 1).containsEntry("nextSeq", 2);
	}

	@Test
	void CAS影響0筆_重讀重試() {
		doReturn(List.of(seq(5)), List.of(seq(6)), List.of(seq(7))).when(db)
				.query(eq(DB), eq(DocSeqDao.FIND_CURR_SEQ), anyMap(), eq(DocSeq.class));
		doReturn(0, 0, 1).when(db).update(eq(DB), eq(DocSeqDao.UPDATE_CAS), anyMap());
		assertThat(svc.nextId(AcctConst.DOC_AR, "0000001")).isEqualTo("AR20261007-008");
		List<Map<String, Object>> cas = updatesOf(DocSeqDao.UPDATE_CAS);
		assertThat(cas).hasSize(3);
		assertThat(cas.get(2)).containsEntry("currSeq", 7).containsEntry("nextSeq", 8);
	}

	@Test
	void 重試10次仍失敗_500() {
		doReturn(List.of(seq(3))).when(db).query(eq(DB), eq(DocSeqDao.FIND_CURR_SEQ), anyMap(), eq(DocSeq.class));
		doReturn(0).when(db).update(eq(DB), eq(DocSeqDao.UPDATE_CAS), anyMap());
		assertThatThrownBy(() -> svc.nextId(AcctConst.DOC_AR, "0000001")).isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
		assertThat(updatesOf(DocSeqDao.UPDATE_CAS)).hasSize(DocSeqService.MAX_TRY);
	}

	@Test
	void 超過999_500() {
		doReturn(List.of(seq(999))).when(db).query(eq(DB), eq(DocSeqDao.FIND_CURR_SEQ), anyMap(), eq(DocSeq.class));
		assertThatThrownBy(() -> svc.nextId(AcctConst.DOC_AR, "0000001")).isInstanceOf(ApiException.class);
		assertThat(updatesOf(DocSeqDao.UPDATE_CAS)).isEmpty();
	}

	private List<Map<String, Object>> updatesOf(String sql) {
		return mockingDetails(db).getInvocations().stream().filter(i -> i.getMethod().getName().equals("update"))
				.filter(i -> sql.equals(i.getArgument(1))).map(DocSeqServiceTest::params).toList();
	}

	private static Map<String, Object> params(Invocation i) {
		return i.getArgument(2);
	}

	private static DocSeq seq(int n) {
		DocSeq s = new DocSeq();
		s.setCurrSeq(n);
		return s;
	}
}
