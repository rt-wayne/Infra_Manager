package com.mpx.infra_manager_java.service;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-05
// 變更說明: 新增：HealthService 單元測試（S1，裁示 ②A：mock DbClient、不連真 DB）
//           2026-10-06：加快取測試；既有測試改用快取 0 毫秒的建構子
//           2026-10-06 複審（④A）：加「慢查詢結束後仍命中快取」「同時進來只有一條去查」「查詢中其他請求拿舊值」
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.mpx.common.db.DbClient;
import com.mpx.common.db.DbConnectException;
import com.mpx.infra_manager_java.dao.HealthDao;
import com.mpx.infra_manager_java.model.DualRow;
import com.mpx.infra_manager_java.model.HealthStatus;

class HealthServiceTest {

	private DbClient dbClient;
	private HealthDao dao;
	private HealthService service;

	@BeforeEach
	void setUp() {
		dbClient = mock(DbClient.class);
		dao = new HealthDao(dbClient);
		ReflectionTestUtils.setField(dao, "itflowDb", "test-alias");
		service = new HealthService(dao, 0L);
	}

	@Test
	void 快取期間內不重複查DB() {
		DualRow row = new DualRow();
		row.setOk(1);
		when(dbClient.query(anyString(), anyString(), any(), eq(DualRow.class))).thenReturn(List.of(row));
		HealthService cached = new HealthService(dao, 60_000L);

		HealthStatus first = cached.check();
		HealthStatus second = cached.check();

		assertThat(second).isSameAs(first);
		verify(dbClient, times(1)).query(anyString(), anyString(), any(), eq(DualRow.class));
	}

	@Test
	void 慢查詢結束後仍在快取期間內() {
		DualRow row = new DualRow();
		row.setOk(1);
		when(dbClient.query(anyString(), anyString(), any(), eq(DualRow.class))).thenAnswer(inv -> {
			Thread.sleep(150);
			return List.of(row);
		});
		HealthService cached = new HealthService(dao, 100L);

		cached.check();
		cached.check();

		verify(dbClient, times(1)).query(anyString(), anyString(), any(), eq(DualRow.class));
	}

	@Test
	void 沒有快取時同時進來的請求只有一條去查DB() throws Exception {
		DualRow row = new DualRow();
		row.setOk(1);
		when(dbClient.query(anyString(), anyString(), any(), eq(DualRow.class))).thenAnswer(inv -> {
			Thread.sleep(200);
			return List.of(row);
		});
		HealthService cached = new HealthService(dao, 60_000L);
		int threads = 4;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<HealthStatus>> futures = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				futures.add(pool.submit(() -> {
					start.await();
					return cached.check();
				}));
			}
			start.countDown();
			for (Future<HealthStatus> f : futures) {
				assertThat(f.get(5, TimeUnit.SECONDS).getDb()).isEqualTo("UP");
			}
		} finally {
			pool.shutdownNow();
		}

		verify(dbClient, times(1)).query(anyString(), anyString(), any(), eq(DualRow.class));
	}

	@Test
	void 快取過期且有人正在查時其他請求直接拿舊值() throws Exception {
		DualRow row = new DualRow();
		row.setOk(1);
		CountDownLatch probing = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		when(dbClient.query(anyString(), anyString(), any(), eq(DualRow.class)))
				.thenReturn(List.of(row))
				.thenAnswer(inv -> {
					probing.countDown();
					release.await(5, TimeUnit.SECONDS);
					return Collections.emptyList();
				});
		HealthService cached = new HealthService(dao, 50L);
		HealthStatus old = cached.check();
		Thread.sleep(80);
		ExecutorService pool = Executors.newSingleThreadExecutor();
		try {
			Future<HealthStatus> slow = pool.submit(cached::check);
			assertThat(probing.await(5, TimeUnit.SECONDS)).isTrue();

			HealthStatus meanwhile = cached.check();

			assertThat(meanwhile).isSameAs(old);
			release.countDown();
			assertThat(slow.get(5, TimeUnit.SECONDS).getDb()).isEqualTo("DOWN");
		} finally {
			pool.shutdownNow();
		}
		verify(dbClient, times(2)).query(anyString(), anyString(), any(), eq(DualRow.class));
	}

	@Test
	void 查到1回UP() {
		DualRow row = new DualRow();
		row.setOk(1);
		when(dbClient.query(eq("test-alias"), anyString(), any(), eq(DualRow.class))).thenReturn(List.of(row));

		HealthStatus s = service.check();

		assertThat(s.getStatus()).isEqualTo("UP");
		assertThat(s.getDb()).isEqualTo("UP");
		assertThat(s.getTime()).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
	}

	@Test
	void 查無資料回DOWN() {
		when(dbClient.query(anyString(), anyString(), any(), eq(DualRow.class))).thenReturn(Collections.emptyList());

		assertThat(service.check().getDb()).isEqualTo("DOWN");
	}

	@Test
	void 連線失敗不往外丟並回DOWN() {
		when(dbClient.query(anyString(), anyString(), any(), eq(DualRow.class)))
				.thenThrow(new DbConnectException("test-alias", "API 位址未設定"));

		HealthStatus s = service.check();

		assertThat(s.getStatus()).isEqualTo("UP");
		assertThat(s.getDb()).isEqualTo("DOWN");
	}
}
