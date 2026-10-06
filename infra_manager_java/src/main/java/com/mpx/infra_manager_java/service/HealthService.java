package com.mpx.infra_manager_java.service;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-05
// 變更說明: 新增：健康檢查（S1）。DB 失敗不往外丟，回 db=DOWN 並只 log 例外類別名（不記訊息、不記連線資訊）；
//           HTTP 一律 200，讓前端能分辨「後端連不上」與「後端活著但 DB 連不上」
//           2026-10-06 code review：結果快取 5 秒。DB 掛掉時每次 ping 最多等 Hikari connectionTimeout 10 秒、
//           池未建好時每次都會打連線資訊 API；首頁一開就呼叫、殼 jar 又對外轉發，必須節流。
//           2026-10-06 複審（④A）：快取時間戳改在 probe 完成後才取（之前在前面取，慢失敗 10 秒存進去就已過期、永遠不命中）；
//           快取過期時只讓拿到 tryLock 的那一條去查，其他請求回舊值（沒有舊值才等它查完），避免 N 條執行緒各等 10 秒。
// ============================================================

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.mpx.infra_manager_java.dao.HealthDao;
import com.mpx.infra_manager_java.model.HealthStatus;

@Service
public class HealthService {

	private static final Logger log = LoggerFactory.getLogger(HealthService.class);
	private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");
	private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	/** 快取秒數（毫秒） */
	static final long DEFAULT_CACHE_MILLIS = 5_000L;

	private final HealthDao healthDao;
	private final long cacheMillis;
	private final AtomicReference<Cached> cache = new AtomicReference<>();
	private final ReentrantLock probeLock = new ReentrantLock();

	/** Spring 用這個建構子（有兩個建構子時必須指定）；另一個只給測試調快取秒數 */
	@Autowired
	public HealthService(HealthDao healthDao) {
		this(healthDao, DEFAULT_CACHE_MILLIS);
	}

	HealthService(HealthDao healthDao, long cacheMillis) {
		this.healthDao = healthDao;
		this.cacheMillis = cacheMillis;
	}

	public HealthStatus check() {
		Cached c = cache.get();
		if (isFresh(c)) {
			return c.status;
		}
		if (probeLock.tryLock()) {
			try {
				return probeAndStore();
			} finally {
				probeLock.unlock();
			}
		}
		// 別條執行緒正在查：有舊值就直接回舊值，沒有舊值（第一次）才等它查完
		if (c != null) {
			return c.status;
		}
		probeLock.lock();
		try {
			Cached stored = cache.get();
			return stored != null ? stored.status : probeAndStore();
		} finally {
			probeLock.unlock();
		}
	}

	private boolean isFresh(Cached c) {
		return c != null && System.currentTimeMillis() - c.at < cacheMillis;
	}

	/** 只在持有 probeLock 時呼叫；時間戳在 probe 完成後才取，慢失敗也能命中快取 */
	private HealthStatus probeAndStore() {
		Cached again = cache.get();
		if (isFresh(again)) {
			return again.status;
		}
		HealthStatus fresh = probe();
		cache.set(new Cached(System.currentTimeMillis(), fresh));
		return fresh;
	}

	private HealthStatus probe() {
		String db;
		try {
			db = healthDao.ping() ? "UP" : "DOWN";
		} catch (RuntimeException e) {
			log.warn("DB 健康檢查失敗 {}", e.getClass().getSimpleName());
			db = "DOWN";
		}
		return new HealthStatus("UP", db, LocalDateTime.now(TAIPEI).format(FMT));
	}

	private record Cached(long at, HealthStatus status) {
	}
}
