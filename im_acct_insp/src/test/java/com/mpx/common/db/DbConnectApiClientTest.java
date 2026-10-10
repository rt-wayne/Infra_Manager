package com.mpx.common.db;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-05
// 變更說明: 新增：DbConnectApiClient 單元測試（規格 TC-B01～B04、B08，§7.1 情境 2～8）
//           以 MockRestServiceServer 模擬 API，不連真 API / Oracle；所有連線值皆為佔位字串
//           審查修正：TC-B03 改參數化 9 組；TC-B04 補 dbType null／全空白；TC-B08 同時檢查 url／user／password；
//           補非標準 HTTP 碼與 URL 格式錯兩案
//           v3.4：TC-B04 改為 MSSQL（含小寫、前後空白）通過、MYSQL 丟例外且訊息列出支援清單
//           jdk25 階段 2：壞 JSON 的 cause 改為 Jackson 3 的 JacksonException；新增 TC-B09（損壞處緊接 password 值，cause 鏈不含 password）
//           審查修正：解析失敗的 cause 改為只含類別名的 RuntimeException；補 password 未加引號的壞 JSON 一案
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withRawStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class DbConnectApiClientTest {

	static final String API_URL = "http://api.example.invalid/dbConnect/api/v1/connection-info";
	static final String ALIAS = "test_alias";
	static final String FAKE_PASSWORD = "FAKE_PWD_PLACEHOLDER";
	static final String FAKE_USER = "FAKE_USER_PLACEHOLDER";
	static final String FAKE_URL = "jdbc:oracle:thin:@db.example.invalid:1521/FAKESVC";

	private RestTemplate restTemplate;
	private MockRestServiceServer server;
	private DbConnectApiClient client;

	@BeforeEach
	void setUp() {
		restTemplate = new RestTemplate();
		server = MockRestServiceServer.bindTo(restTemplate).build();
		client = new DbConnectApiClient(restTemplate, API_URL);
	}

	static String successJson(String alias, String dbType, String jdbcUrl, String username, String password) {
		return "{\"code\":\"0000\",\"message\":\"成功\",\"extraTop\":\"x\",\"data\":{"
				+ "\"alias\":" + quote(alias)
				+ ",\"dbType\":" + quote(dbType)
				+ ",\"jdbcUrl\":" + quote(jdbcUrl)
				+ ",\"username\":" + quote(username)
				+ ",\"password\":" + quote(password)
				+ ",\"maxPoolSize\":5,\"queryTimeoutSeconds\":30,\"extraField\":123}}";
	}

	static String successJson(String alias) {
		return successJson(alias, "ORACLE", FAKE_URL, FAKE_USER, FAKE_PASSWORD);
	}

	private static String quote(String s) {
		return s == null ? "null" : "\"" + s + "\"";
	}

	private void expectResponse(String json) {
		server.expect(requestTo(API_URL))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
	}

	private void assertNoSecret(Throwable e) {
		for (Throwable t = e; t != null; t = t.getCause()) {
			assertThat(String.valueOf(t.getMessage())).doesNotContain(FAKE_PASSWORD, FAKE_USER, FAKE_URL);
		}
	}

	// TC-B01
	@Test
	void success_parsesAllFields_andIgnoresUnknownFields() {
		server.expect(requestTo(API_URL))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
				.andExpect(content().json("{\"alias\":\"" + ALIAS + "\"}"))
				.andRespond(withSuccess(successJson(ALIAS), MediaType.APPLICATION_JSON));

		DbConnectionInfo info = client.fetch(ALIAS);

		assertThat(info.getAlias()).isEqualTo(ALIAS);
		assertThat(info.getDbType()).isEqualTo("ORACLE");
		assertThat(info.getJdbcUrl()).isEqualTo(FAKE_URL);
		assertThat(info.getUsername()).isEqualTo(FAKE_USER);
		assertThat(info.getPassword()).isEqualTo(FAKE_PASSWORD);
		assertThat(info.getMaxPoolSize()).isEqualTo(5);
		assertThat(info.getQueryTimeoutSeconds()).isEqualTo(30);
		server.verify();
	}

	// TC-B02
	@Test
	void codeNotSuccess_throwsWithAliasCodeMessage() {
		expectResponse("{\"code\":\"9001\",\"message\":\"查無別名\",\"data\":null}");

		assertThatThrownBy(() -> client.fetch(ALIAS))
				.isInstanceOf(DbConnectException.class)
				.hasMessageContaining(ALIAS)
				.hasMessageContaining("9001")
				.hasMessageContaining("查無別名");
	}

	// TC-B03
	@Test
	void dataNull_throwsWithAlias() {
		expectResponse("{\"code\":\"0000\",\"message\":\"成功\",\"data\":null}");

		assertThatThrownBy(() -> client.fetch(ALIAS))
				.isInstanceOf(DbConnectException.class)
				.hasMessageContaining(ALIAS)
				.hasMessageContaining("0000");
	}

	/** TC-B03：jdbcUrl / username / password × null / 空字串 / 全空白 共 9 組 */
	static Stream<Arguments> missingFieldCases() {
		List<Arguments> cases = new ArrayList<>();
		for (String field : new String[] { "jdbcUrl", "username", "password" }) {
			for (String bad : new String[] { null, "", "   " }) {
				cases.add(Arguments.of(field, bad));
			}
		}
		return cases.stream();
	}

	// TC-B03
	@ParameterizedTest(name = "[{index}] {0}={1}")
	@MethodSource("missingFieldCases")
	void requiredFieldMissing_throwsWithAlias(String field, String bad) {
		String jdbcUrl = "jdbcUrl".equals(field) ? bad : FAKE_URL;
		String username = "username".equals(field) ? bad : FAKE_USER;
		String password = "password".equals(field) ? bad : FAKE_PASSWORD;
		expectResponse(successJson(ALIAS, "ORACLE", jdbcUrl, username, password));

		assertThatThrownBy(() -> client.fetch(ALIAS))
				.isInstanceOf(DbConnectException.class)
				.hasMessageContaining(ALIAS)
				.hasMessageContaining(field)
				.satisfies(this::assertNoSecret);
	}

	// TC-B04：dbType 為 null 或全空白
	@ParameterizedTest(name = "[{index}] dbType={0}")
	@NullSource
	@ValueSource(strings = { "   " })
	void dbTypeNullOrBlank_throws(String dbType) {
		expectResponse(successJson(ALIAS, dbType, FAKE_URL, FAKE_USER, FAKE_PASSWORD));

		assertThatThrownBy(() -> client.fetch(ALIAS))
				.isInstanceOf(DbConnectException.class)
				.hasMessageContaining(ALIAS)
				.satisfies(this::assertNoSecret);
	}

	// TC-B04：不支援的 dbType，訊息列出支援清單
	@Test
	void dbTypeUnsupported_throws() {
		expectResponse(successJson(ALIAS, "MYSQL", FAKE_URL, FAKE_USER, FAKE_PASSWORD));

		assertThatThrownBy(() -> client.fetch(ALIAS))
				.isInstanceOf(DbConnectException.class)
				.hasMessageContaining(ALIAS)
				.hasMessageContaining("dbType 不支援：MYSQL（支援：ORACLE、MSSQL）")
				.satisfies(this::assertNoSecret);
	}

	// TC-B04：MSSQL 忽略大小寫與前後空白
	@ParameterizedTest(name = "[{index}] dbType={0}")
	@ValueSource(strings = { "MSSQL", "mssql", " MsSql " })
	void dbTypeMssql_passes(String dbType) {
		expectResponse(successJson(ALIAS, dbType, FAKE_URL, FAKE_USER, FAKE_PASSWORD));

		DbConnectionInfo info = client.fetch(ALIAS);

		assertThat(info.getDbType()).isEqualTo(dbType);
		assertThat(DbConnectionManager.driverClassName(info.getDbType()))
				.isEqualTo("com.microsoft.sqlserver.jdbc.SQLServerDriver");
	}

	// TC-B04
	@Test
	void dbTypeLowerCaseOracle_passes() {
		expectResponse(successJson(ALIAS, "oracle", FAKE_URL, FAKE_USER, FAKE_PASSWORD));

		DbConnectionInfo info = client.fetch(ALIAS);

		assertThat(info.getDbType()).isEqualTo("oracle");
	}

	// §7.1 情境 2
	@Test
	void apiUrlEmpty_throwsWithoutCallingApi() {
		DbConnectApiClient emptyUrlClient = new DbConnectApiClient(restTemplate, "  ");

		assertThatThrownBy(() -> emptyUrlClient.fetch(ALIAS))
				.isInstanceOf(DbConnectException.class)
				.hasMessageContaining(ALIAS)
				.hasMessageContaining("db.connect.api.domain.path");
		server.verify();
	}

	// §7.1 情境 4：非 2xx，訊息帶 HTTP 狀態，且不夾帶回應本文
	@Test
	void httpNon2xx_throwsWithStatus() {
		server.expect(requestTo(API_URL))
				.andRespond(withServerError().body("SECRET_BODY_" + FAKE_PASSWORD).contentType(MediaType.TEXT_PLAIN));

		assertThatThrownBy(() -> client.fetch(ALIAS))
				.isInstanceOf(DbConnectException.class)
				.hasMessageContaining(ALIAS)
				.hasMessageContaining("500")
				.satisfies(this::assertNoSecret);
	}

	@Test
	void http404_throwsWithStatus() {
		server.expect(requestTo(API_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThatThrownBy(() -> client.fetch(ALIAS))
				.isInstanceOf(DbConnectException.class)
				.hasMessageContaining("404");
	}

	// 非標準 HTTP 碼（600）：用 getStatusCodeValue 判斷，丟 DbConnectException 而非 IllegalArgumentException
	@Test
	void httpNonStandardStatus_throwsWithStatus() {
		server.expect(requestTo(API_URL))
				.andRespond(withRawStatus(600).body("SECRET_BODY_" + FAKE_PASSWORD).contentType(MediaType.TEXT_PLAIN));

		assertThatThrownBy(() -> client.fetch(ALIAS))
				.isInstanceOf(DbConnectException.class)
				.hasMessageContaining(ALIAS)
				.hasMessageContaining("600")
				.satisfies(this::assertNoSecret);
	}

	// db.connect.api.domain.path 格式錯誤（漏寫 http://）：URI 非絕對路徑，送出前即丟 IllegalArgumentException，包成 DbConnectException；不會實際連線
	@Test
	void apiUrlMalformed_throwsDbConnectException() {
		DbConnectApiClient badUrlClient = new DbConnectApiClient(new RestTemplate(), "api.example.invalid/dbConnect");

		assertThatThrownBy(() -> badUrlClient.fetch(ALIAS))
				.isInstanceOf(DbConnectException.class)
				.hasMessageContaining(ALIAS)
				.hasCauseInstanceOf(IllegalArgumentException.class);
	}

	// 回應不是 JSON：丟 DbConnectException 並掛 cause，cause 鏈不含密碼
	@Test
	void malformedJson_throwsWithCause() {
		expectResponse("{\"code\":\"0000\",\"data\":{\"password\":\"" + FAKE_PASSWORD + "\",");

		assertThatThrownBy(() -> client.fetch(ALIAS))
				.isInstanceOf(DbConnectException.class)
				.hasMessageContaining(ALIAS)
				// cause 只含原例外的類別名（不帶原訊息、不帶原 cause；審查修正）
				.satisfies(e -> assertSanitizedCause(e))
				.satisfies(this::assertNoSecret);
	}

	/** 解析失敗的 cause：RuntimeException，訊息只有 Jackson 例外的類別名，且沒有再往下的 cause */
	private static void assertSanitizedCause(Throwable e) {
		Throwable cause = e.getCause();
		assertThat(cause).isExactlyInstanceOf(RuntimeException.class);
		assertThat(cause.getMessage()).startsWith("tools.jackson.");
		assertThat(cause.getCause()).isNull();
	}

	// 審查修正：password 值沒加引號的壞 JSON（Jackson 會把未識別的 token 寫進訊息），cause 鏈仍不含該值
	@Test
	void malformedJsonUnquotedPassword_noSecretInCauseChain() {
		String secret = "FAKE_PASSWORD_X";
		expectResponse("{\"code\":\"0000\",\"message\":\"ok\",\"data\":{\"alias\":\"" + ALIAS
				+ "\",\"dbType\":\"ORACLE\",\"jdbcUrl\":\"x\",\"username\":\"u\",\"password\":" + secret
				+ ",\"maxPoolSize\":5}}");

		assertThatThrownBy(() -> client.fetch(ALIAS))
				.isInstanceOf(DbConnectException.class)
				.hasMessageContaining(ALIAS)
				.satisfies(e -> assertSanitizedCause(e))
				.satisfies(e -> {
					for (Throwable t = e; t != null; t = t.getCause()) {
						assertThat(String.valueOf(t.getMessage())).doesNotContain(secret);
						assertThat(String.valueOf(t)).doesNotContain(secret);
					}
				});
	}

	// TC-B09：JSON 損壞處緊接在 password 欄位值之後，例外訊息與整條 cause 鏈都不含該 password
	@Test
	void malformedJsonRightAfterPassword_noSecretInCauseChain() {
		expectResponse("{\"code\":\"0000\",\"message\":\"ok\",\"data\":{\"alias\":\"" + ALIAS
				+ "\",\"dbType\":\"ORACLE\",\"jdbcUrl\":\"x\",\"username\":\"u\",\"password\":\"" + FAKE_PASSWORD
				+ "\"x,\"maxPoolSize\":5}}");

		assertThatThrownBy(() -> client.fetch(ALIAS))
				.isInstanceOf(DbConnectException.class)
				.hasMessageContaining(ALIAS)
				.satisfies(e -> assertSanitizedCause(e))
				.satisfies(this::assertNoSecret)
				.satisfies(e -> {
					for (Throwable t = e; t != null; t = t.getCause()) {
						assertThat(String.valueOf(t)).doesNotContain(FAKE_PASSWORD);
					}
				});
	}

	// TC-B08
	@Test
	void toString_masksPassword() {
		DbConnectionInfo info = new DbConnectionInfo();
		info.setAlias(ALIAS);
		info.setDbType("ORACLE");
		info.setJdbcUrl(FAKE_URL);
		info.setUsername(FAKE_USER);
		info.setPassword(FAKE_PASSWORD);

		String text = info.toString();

		assertThat(text).doesNotContain(FAKE_PASSWORD, FAKE_URL, FAKE_USER).contains(ALIAS);
		assertThat(text).doesNotContain(String.valueOf(FAKE_PASSWORD.length()));
	}
}
