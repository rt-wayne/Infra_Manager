package com.mpx.infra_manager_java.service.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：取號邏輯的單元測試（S6 回合二 b-1）。鎖定：已有計數列直接 +1；當天第一張 INSERT；
//           INSERT 撞唯一鍵改 UPDATE；計數列停用時丟例外；單號格式（三碼補零、超過 999 變四碼）。
//           併發行為由 AppSeqIT 對真 DB 驗證
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import com.mpx.infra_manager_java.dao.changerequest.AppSeqDao;

class AppSeqServiceTest {

	private static final LocalDate DAY = LocalDate.of(2026, 10, 7);
	private static final Timestamp SEQ_DATE = Timestamp.valueOf(LocalDateTime.of(2026, 10, 7, 0, 0));

	private final AppSeqDao dao = mock(AppSeqDao.class);
	private final AppSeqService service = new AppSeqService(dao);

	@Test
	void 已有計數列直接加一() {
		when(dao.increment("IM", SEQ_DATE, "T0001")).thenReturn(1);
		when(dao.findLastNo("IM", SEQ_DATE)).thenReturn(5);
		assertThat(service.nextNo("IM", DAY, "T0001")).isEqualTo(5);
		verify(dao, never()).insertFirst(any(), any(), any());
	}

	@Test
	void 當天第一張建計數列() {
		when(dao.increment("IM", SEQ_DATE, "T0001")).thenReturn(0);
		when(dao.findLastNo("IM", SEQ_DATE)).thenReturn(1);
		assertThat(service.nextNo("IM", DAY, "T0001")).isEqualTo(1);
		verify(dao).insertFirst("IM", SEQ_DATE, "T0001");
	}

	@Test
	void 同時建第一張撞唯一鍵就改加一() {
		when(dao.increment("IM", SEQ_DATE, "T0001")).thenReturn(0, 1);
		when(dao.insertFirst("IM", SEQ_DATE, "T0001")).thenThrow(new DuplicateKeyException("UK"));
		when(dao.findLastNo("IM", SEQ_DATE)).thenReturn(2);
		assertThat(service.nextNo("IM", DAY, "T0001")).isEqualTo(2);
		verify(dao, times(2)).increment(eq("IM"), eq(SEQ_DATE), eq("T0001"));
	}

	@Test
	void 計數列停用時丟例外() {
		when(dao.increment("IM", SEQ_DATE, "T0001")).thenReturn(0, 0);
		when(dao.insertFirst("IM", SEQ_DATE, "T0001")).thenThrow(new DuplicateKeyException("UK"));
		assertThatThrownBy(() -> service.nextNo("IM", DAY, "T0001")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void 單號格式() {
		assertThat(AppSeqService.onlineAppId(DAY, 7)).isEqualTo("IM20261007-007");
		assertThat(AppSeqService.onlineAppId(DAY, 999)).isEqualTo("IM20261007-999");
		assertThat(AppSeqService.onlineAppId(DAY, 1000)).isEqualTo("IM20261007-1000");
	}
}
