package com.mpx.infra_manager_java.service.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：取號對真實測試 Oracle 的整合測試（S6 回合二 b-1，只由 mvnw verify 執行；連線資訊 API 位址為空時略過）。
//           用 2099 年的隨機日期，不碰真實日期的計數列；驗證：連續取號遞增；兩個執行緒同時取當天第一號，
//           一個走 INSERT、一個撞唯一鍵改 UPDATE，結果是 {1, 2} 不重複。測試結束刪除本測試建的計數列
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import com.mpx.common.db.DbClient;
import com.mpx.infra_manager_java.config.DbSchema;
import com.mpx.infra_manager_java.util.TaiwanTime;

@SpringBootTest
class AppSeqIT {

	private static final String BY = "S6_SEQ_IT";

	@Autowired
	private AppSeqService appSeqService;

	@Autowired
	private DbClient dbClient;

	@Autowired
	private DbSchema schema;

	@Value("${db.connect.api.domain.path:}")
	private String apiUrl;

	@Value("${db.connect.itflow}")
	private String itflowDb;

	private LocalDate day;

	@BeforeEach
	void setUp() {
		assumeTrue(apiUrl != null && !apiUrl.isBlank(), "host.properties 未設定連線資訊 API，略過整合測試");
		day = LocalDate.of(2099, 1, 1).plusDays(ThreadLocalRandom.current().nextInt(365));
		cleanUp();
	}

	@AfterEach
	void cleanUp() {
		if (day != null) {
			dbClient.update(itflowDb, "DELETE FROM " + schema.table("IM_APP_SEQ")
					+ " WHERE SEQ_PREFIX = 'IM' AND SEQ_DATE = :d AND CREATE_BY = :by",
					Map.of("d", TaiwanTime.startOf(day), "by", BY));
		}
	}

	@Test
	void 連續取號遞增() {
		assertThat(appSeqService.nextNo("IM", day, BY)).isEqualTo(1);
		assertThat(appSeqService.nextNo("IM", day, BY)).isEqualTo(2);
		assertThat(appSeqService.nextNo("IM", day, BY)).isEqualTo(3);
	}

	@Test
	void 兩個執行緒同時取第一號不重複() throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<Integer> a = pool.submit(() -> {
				start.await();
				return appSeqService.nextNo("IM", day, BY);
			});
			Future<Integer> b = pool.submit(() -> {
				start.await();
				return appSeqService.nextNo("IM", day, BY);
			});
			start.countDown();
			List<Integer> got = List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
			assertThat(got).containsExactlyInAnyOrder(1, 2);
		} finally {
			pool.shutdownNow();
		}
	}
}
