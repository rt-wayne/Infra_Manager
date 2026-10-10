package com.mpx.common.db;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-04
// 變更說明: 新增：DbClient / DbConnectionManager 單元測試（規格 TC-B05～B07、§6.4）
//           以子類別覆寫 createDataSource 替換建池步驟（回傳不連線的 stub），不連真 Oracle、不新增依賴
//           審查修正：補「池建好後續步驟失敗先關池」與 summarize 取鏈中 ORA 碼兩案
//           v3.4：StubManager 於覆寫的 createDataSource 內呼叫 buildHikariConfig 記下設定（不建池），驗 MSSQL／ORACLE 的 driverClassName
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.twice;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.zaxxer.hikari.HikariConfig;

class DbConnectionManagerTest {

	static final String API_URL = DbConnectApiClientTest.API_URL;
	static final String ALIAS = DbConnectApiClientTest.ALIAS;

	private MockRestServiceServer server;
	private DbConnectApiClient apiClient;
	private StubManager manager;
	private DbClient dbClient;

	/** 建池步驟替換成 stub：記錄建立次數與傳入的 maxPoolSize，可設定前 N 次失敗 */
	static class StubManager extends DbConnectionManager {
		final AtomicInteger createCount = new AtomicInteger();
		final List<StubDataSource> created = new ArrayList<>();
		volatile int failTimes = 0;
		volatile int templateFailTimes = 0;
		volatile int lastMaxPoolSize;
		volatile HikariConfig lastConfig;

		StubManager(DbConnectApiClient apiClient) {
			super(apiClient);
		}

		@Override
		protected NamedParameterJdbcTemplate createJdbcTemplate(DataSource dataSource, int queryTimeout) {
			if (templateFailTimes > 0) {
				templateFailTimes--;
				throw new IllegalStateException("模擬建 template 失敗");
			}
			return super.createJdbcTemplate(dataSource, queryTimeout);
		}

		@Override
		protected DataSource createDataSource(String dbName, DbConnectionInfo info, int maxPoolSize) {
			createCount.incrementAndGet();
			lastMaxPoolSize = maxPoolSize;
			lastConfig = buildHikariConfig(dbName, info, maxPoolSize);
			if (failTimes > 0) {
				failTimes--;
				throw new IllegalStateException("模擬建池失敗");
			}
			StubDataSource ds = new StubDataSource(dbName);
			synchronized (created) {
				created.add(ds);
			}
			return ds;
		}
	}

	/** 不連線的 DataSource，只記錄是否被 close；可設定 close 時丟例外 */
	static class StubDataSource implements DataSource, Closeable {
		final String name;
		volatile boolean closed;
		volatile boolean failOnClose;

		StubDataSource(String name) {
			this.name = name;
		}

		@Override
		public void close() throws IOException {
			closed = true;
			if (failOnClose) {
				throw new IOException("模擬 close 失敗");
			}
		}

		@Override
		public Connection getConnection() throws SQLException {
			throw new SQLException("stub");
		}

		@Override
		public Connection getConnection(String username, String password) throws SQLException {
			throw new SQLException("stub");
		}

		@Override
		public PrintWriter getLogWriter() {
			return null;
		}

		@Override
		public void setLogWriter(PrintWriter out) {
		}

		@Override
		public void setLoginTimeout(int seconds) {
		}

		@Override
		public int getLoginTimeout() {
			return 0;
		}

		@Override
		public Logger getParentLogger() {
			return Logger.getGlobal();
		}

		@Override
		public <T> T unwrap(Class<T> iface) throws SQLException {
			throw new SQLException("stub");
		}

		@Override
		public boolean isWrapperFor(Class<?> iface) {
			return false;
		}
	}

	@BeforeEach
	void setUp() {
		RestTemplate restTemplate = new RestTemplate();
		server = MockRestServiceServer.bindTo(restTemplate).build();
		apiClient = new DbConnectApiClient(restTemplate, API_URL);
		manager = new StubManager(apiClient);
		dbClient = new DbClient(manager);
	}

	private static String successJson(String alias) {
		return DbConnectApiClientTest.successJson(alias);
	}

	// TC-B05
	@Test
	void dbNameNullOrBlank_throwsWithoutCallingApi() {
		assertThatThrownBy(() -> dbClient.query(null, "SELECT 1 FROM DUAL", null, Object.class))
				.isInstanceOf(DbConnectException.class);
		assertThatThrownBy(() -> dbClient.query("", "SELECT 1 FROM DUAL", null, Object.class))
				.isInstanceOf(DbConnectException.class);
		assertThatThrownBy(() -> dbClient.update("   ", "DELETE FROM T", null))
				.isInstanceOf(DbConnectException.class);

		server.verify(); // 沒有任何 expect：有打 API 會失敗
		assertThat(manager.createCount.get()).isZero();
	}

	// TC-B06
	@Test
	void sameDbNameTwice_callsApiOnce() {
		server.expect(once(), requestTo(API_URL)).andRespond(withSuccess(successJson(ALIAS), MediaType.APPLICATION_JSON));

		NamedParameterJdbcTemplate first = manager.getJdbcTemplate(DbClient.checkDbName(ALIAS));
		NamedParameterJdbcTemplate second = manager.getJdbcTemplate(DbClient.checkDbName("  " + ALIAS + "  "));

		assertThat(second).isSameAs(first);
		assertThat(manager.createCount.get()).isEqualTo(1);
		server.verify();
	}

	// TC-B06（並發首次呼叫只建一次池）
	@Test
	void concurrentFirstCalls_createOnce() throws Exception {
		server.expect(once(), requestTo(API_URL)).andRespond(withSuccess(successJson(ALIAS), MediaType.APPLICATION_JSON));

		int threads = 8;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<NamedParameterJdbcTemplate>> futures = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			Callable<NamedParameterJdbcTemplate> task = () -> {
				start.await();
				return manager.getJdbcTemplate(ALIAS);
			};
			futures.add(pool.submit(task));
		}
		start.countDown();
		NamedParameterJdbcTemplate expected = futures.get(0).get(10, TimeUnit.SECONDS);
		for (Future<NamedParameterJdbcTemplate> f : futures) {
			assertThat(f.get(10, TimeUnit.SECONDS)).isSameAs(expected);
		}
		pool.shutdownNow();

		assertThat(manager.createCount.get()).isEqualTo(1);
		server.verify();
	}

	// §6.2：區分大小寫，兩個 key 各打一次 API
	@Test
	void dbNameCaseSensitive_callsApiPerKey() {
		server.expect(once(), requestTo(API_URL))
				.andExpect(content().json("{\"alias\":\"NOVA_X\"}"))
				.andRespond(withSuccess(successJson("NOVA_X"), MediaType.APPLICATION_JSON));
		server.expect(once(), requestTo(API_URL))
				.andExpect(content().json("{\"alias\":\"nova_x\"}"))
				.andRespond(withSuccess(successJson("nova_x"), MediaType.APPLICATION_JSON));

		NamedParameterJdbcTemplate upper = manager.getJdbcTemplate("NOVA_X");
		NamedParameterJdbcTemplate lower = manager.getJdbcTemplate("nova_x");

		assertThat(upper).isNotSameAs(lower);
		server.verify();
	}

	// TC-B07：API 回錯誤後同名再呼叫，會重新打 API
	@Test
	void apiFailedFirst_retriesOnNextCall() {
		server.expect(once(), requestTo(API_URL))
				.andRespond(withSuccess("{\"code\":\"9001\",\"message\":\"查無別名\"}", MediaType.APPLICATION_JSON));
		server.expect(once(), requestTo(API_URL))
				.andRespond(withSuccess(successJson(ALIAS), MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> manager.getJdbcTemplate(ALIAS))
				.isInstanceOf(DbConnectException.class)
				.hasMessageContaining("9001");
		NamedParameterJdbcTemplate template = manager.getJdbcTemplate(ALIAS);

		assertThat(template).isNotNull();
		assertThat(manager.createCount.get()).isEqualTo(1);
		server.verify();
	}

	// §7.1 情境 9：建池失敗丟 DbConnectException、掛 cause、不快取，下次重新打 API
	@Test
	void createPoolFailedFirst_notCached() {
		server.expect(twice(), requestTo(API_URL)).andRespond(withSuccess(successJson(ALIAS), MediaType.APPLICATION_JSON));
		manager.failTimes = 1;

		assertThatThrownBy(() -> manager.getJdbcTemplate(ALIAS))
				.isInstanceOf(DbConnectException.class)
				.hasMessageContaining(ALIAS)
				.hasCauseInstanceOf(IllegalStateException.class);
		assertThat(manager.getJdbcTemplate(ALIAS)).isNotNull();

		assertThat(manager.createCount.get()).isEqualTo(2);
		server.verify();
	}

	// 池已建好但後續步驟失敗：先關池再丟 DbConnectException，不快取
	@Test
	void templateFailedAfterPoolCreated_closesPoolFirst() {
		server.expect(twice(), requestTo(API_URL)).andRespond(withSuccess(successJson(ALIAS), MediaType.APPLICATION_JSON));
		manager.templateFailTimes = 1;

		assertThatThrownBy(() -> manager.getJdbcTemplate(ALIAS))
				.isInstanceOf(DbConnectException.class)
				.hasMessageContaining(ALIAS)
				.hasCauseInstanceOf(IllegalStateException.class);
		assertThat(manager.created).hasSize(1);
		assertThat(manager.created.get(0).closed).isTrue();

		assertThat(manager.getJdbcTemplate(ALIAS)).isNotNull();
		assertThat(manager.created).hasSize(2);
		assertThat(manager.created.get(1).closed).isFalse();
		server.verify();
	}

	// summarize：ORA 碼取自鏈中第一個 errorCode > 0 的 SQLException，底下掛非 SQLException 也拿得到；不帶例外訊息
	@Test
	void summarize_findsOraCodeInMiddleOfChain() {
		Exception vendor = new Exception("host=db.example.invalid");
		SQLException sql = new SQLException("ORA-01017: invalid username/password", "72000", 1017, vendor);
		RuntimeException outer = new RuntimeException("wrapper", sql);

		String summary = DbConnectionManager.summarize(outer, "ORACLE");

		assertThat(summary).isEqualTo("RuntimeException / SQLException ORA-01017");
		assertThat(DbConnectionManager.summarize(sql, "oracle")).isEqualTo("SQLException ORA-01017");
		// dbType 未知時用通用形式，不寫死 ORA-
		assertThat(DbConnectionManager.summarize(sql)).isEqualTo("SQLException code=1017");
		assertThat(DbConnectionManager.summarize(new RuntimeException("x", new IllegalStateException("y"))))
				.isEqualTo("RuntimeException / IllegalStateException");
	}

	// v3.4：非 Oracle（MSSQL）錯誤碼以通用形式 code=xxxx 顯示，不出現 ORA-；不帶例外訊息
	@Test
	void summarize_mssqlCodeIsGeneric() {
		SQLException sql = new SQLException("Login failed for user 'x'. host=db.example.invalid", "S0001", 18456);
		RuntimeException outer = new RuntimeException("wrapper", sql);

		String summary = DbConnectionManager.summarize(outer, "MSSQL");

		assertThat(summary).isEqualTo("RuntimeException / SQLException code=18456");
		assertThat(summary).doesNotContain("ORA-", "Login failed", "example.invalid");
	}

	// v3.4：driverClassName 依 dbType（MSSQL 含小寫、ORACLE），其餘 Hikari 參數相同
	@ParameterizedTest(name = "[{index}] dbType={0}")
	@CsvSource({ "MSSQL,com.microsoft.sqlserver.jdbc.SQLServerDriver", "mssql,com.microsoft.sqlserver.jdbc.SQLServerDriver",
			"ORACLE,oracle.jdbc.OracleDriver" })
	void driverClassName_byDbType(String dbType, String driver) {
		server.expect(once(), requestTo(API_URL)).andRespond(withSuccess(
				DbConnectApiClientTest.successJson(ALIAS, dbType, DbConnectApiClientTest.FAKE_URL, DbConnectApiClientTest.FAKE_USER,
						DbConnectApiClientTest.FAKE_PASSWORD),
				MediaType.APPLICATION_JSON));

		manager.getJdbcTemplate(ALIAS);

		assertThat(manager.lastConfig.getDriverClassName()).isEqualTo(driver);
		assertThat(manager.lastConfig.getPoolName()).isEqualTo("db-" + ALIAS);
		assertThat(manager.lastConfig.getMaximumPoolSize()).isEqualTo(5);
		assertThat(manager.lastConfig.getMinimumIdle()).isEqualTo(1);
		server.verify();
	}

	// §6.3：queryTimeout 與 maxPoolSize 取 API 值；null 用預設
	@Test
	void poolAndTimeoutSettings_fromApiOrDefault() {
		server.expect(once(), requestTo(API_URL)).andRespond(withSuccess(successJson("a1"), MediaType.APPLICATION_JSON));
		server.expect(once(), requestTo(API_URL)).andRespond(withSuccess(
				"{\"code\":\"0000\",\"data\":{\"dbType\":\"ORACLE\",\"jdbcUrl\":\"x\",\"username\":\"x\",\"password\":\"x\","
						+ "\"maxPoolSize\":0,\"queryTimeoutSeconds\":null}}",
				MediaType.APPLICATION_JSON));

		NamedParameterJdbcTemplate fromApi = manager.getJdbcTemplate("a1");
		assertThat(manager.lastMaxPoolSize).isEqualTo(5);
		assertThat(fromApi.getJdbcTemplate().getQueryTimeout()).isEqualTo(30);

		NamedParameterJdbcTemplate defaults = manager.getJdbcTemplate("a2");
		assertThat(manager.lastMaxPoolSize).isEqualTo(10);
		assertThat(defaults.getJdbcTemplate().getQueryTimeout()).isEqualTo(60);
		server.verify();
	}

	// §4.3：destroy 逐一 close，單一池 close 失敗不中斷其他池
	@Test
	void destroy_closesAllPools_evenIfOneFails() {
		server.expect(once(), requestTo(API_URL)).andRespond(withSuccess(successJson("a1"), MediaType.APPLICATION_JSON));
		server.expect(once(), requestTo(API_URL)).andRespond(withSuccess(successJson("a2"), MediaType.APPLICATION_JSON));
		server.expect(once(), requestTo(API_URL)).andRespond(withSuccess(successJson("a3"), MediaType.APPLICATION_JSON));
		manager.getJdbcTemplate("a1");
		manager.getJdbcTemplate("a2");
		manager.getJdbcTemplate("a3");
		manager.created.get(0).failOnClose = true;

		manager.destroy();

		assertThat(manager.created).hasSize(3).allSatisfy(ds -> assertThat(ds.closed).isTrue());
	}
}
