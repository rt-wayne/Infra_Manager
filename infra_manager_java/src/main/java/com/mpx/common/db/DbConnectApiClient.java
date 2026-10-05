package com.mpx.common.db;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-05
// 變更說明: 新增：呼叫 DB 連線資訊 API，取得並檢核 DbConnectionInfo（規格 §5、§7.1 情境 2～8）
//           RestTemplate 在類別內自建、不註冊成 bean，避免與使用者專案既有 RestTemplate bean 撞名（規格 §4.3）
//           connect 5 秒 / read 10 秒，不重試（規格 §5.3、D-07）
//           回應以 byte[] 收下再自行用 Jackson 解析：
//             - 關閉 INCLUDE_SOURCE_IN_LOCATION，解析失敗時例外訊息不夾帶回應原文（回應含明文密碼）
//             - 非 2xx 不讓 RestTemplate 丟 HttpStatusCodeException（其訊息會夾帶回應本文），改自行判斷狀態碼
//           log 只記 alias、dbType、code、message（規格 §9）
//           驗證 header / https 屬假設（規格 §13-1），日後要加只動本類別
//           v3.4：dbType 支援 ORACLE 與 MSSQL（忽略大小寫、trim），支援清單取自 DbConnectionManager.DRIVERS
//           審查修正：RestTemplateBuilder 改自建、不注入；狀態碼改用 getStatusCodeValue 判 200～299；
//           IllegalArgumentException（非標準 HTTP 碼、URL 格式錯）包成 DbConnectException，log 只記類別名
//           API URL 改讀 host.properties 的 db.connect.api.domain.path（由 rt-api.domain、db.connect.api.port、db.connect.api.path 組合）
//           jdk25 階段 2（規格 D-35、D-36）：Jackson 3（tools.jackson）——JsonMapper.builder 建 mapper，維持忽略未知欄位，
//                 關閉 StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION（Jackson 2 JsonParser.Feature 的等價選項）；
//                 Jackson 3 例外為 unchecked 的 JacksonException，catch 改接它；RestTemplateBuilder 搬到 org.springframework.boot.restclient，
//                 逾時改 connectTimeout／readTimeout；狀態碼改 getStatusCode().value()（Framework 7 的 HttpStatusCode 接受非標準碼，不再丟例外）；
//                 ResponseErrorHandler.handleError 改覆寫 (URI, HttpMethod, ClientHttpResponse) 版本。行為不變
//           審查修正：回應 JSON 解析失敗時不再掛原 JacksonException，改掛只含類別名的 RuntimeException（避免 token 帶出 password）
// ============================================================

import java.net.URI;
import java.time.Duration;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Component
@PropertySource("classpath:config/host.properties")
public class DbConnectApiClient {

	Logger devTemplateDb = LoggerFactory.getLogger("devTemplate.db");

	static final String SUCCESS_CODE = "0000";

	private final RestTemplate restTemplate;
	private final String apiUrl;
	private final ObjectMapper objectMapper;

	/**
	 * RestTemplateBuilder 自建、不注入 Spring 的 builder：避免吃到使用者專案註冊的
	 * RestTemplateCustomizer / interceptor（例如把回應全文印進 log，回應含明文密碼）。
	 */
	@Autowired
	public DbConnectApiClient(@Value("${db.connect.api.domain.path:}") String apiUrl) {
		this(new RestTemplateBuilder()
				.connectTimeout(Duration.ofSeconds(5))
				.readTimeout(Duration.ofSeconds(10))
				.build(), apiUrl);
	}

	/** 單元測試用：傳入已綁定 MockRestServiceServer 的 RestTemplate */
	DbConnectApiClient(RestTemplate restTemplate, String apiUrl) {
		this.restTemplate = restTemplate;
		this.restTemplate.setErrorHandler(new PassThroughErrorHandler());
		this.apiUrl = apiUrl == null ? "" : apiUrl.trim();
		// 解析失敗時例外訊息不夾帶回應原文（回應含明文密碼；TC-B09）
		this.objectMapper = JsonMapper.builder()
				.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
				.disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
				.build();
	}

	/**
	 * 依 alias 打 API 取得連線資訊並完成檢核（code、data、必要欄位、dbType）。
	 * 任一項不符丟 DbConnectException。
	 */
	public DbConnectionInfo fetch(String alias) {
		if (apiUrl.isEmpty()) {
			throw new DbConnectException(alias, "host.properties 未設定 db.connect.api.domain.path");
		}

		DbConnectApiResponse response = callApi(alias);
		String code = response.getCode();
		String message = response.getMessage();
		devTemplateDb.info("連線資訊 API 回應 alias={} code={} message={}", alias, code, message);

		if (!SUCCESS_CODE.equals(code)) {
			throw new DbConnectException(alias, "API 回應失敗 code=" + code + " message=" + message);
		}
		DbConnectionInfo info = response.getData();
		if (info == null) {
			throw new DbConnectException(alias, "API 回應 data 為空 code=" + code + " message=" + message);
		}
		requireText(alias, "jdbcUrl", info.getJdbcUrl());
		requireText(alias, "username", info.getUsername());
		requireText(alias, "password", info.getPassword());
		if (DbConnectionManager.driverClassName(info.getDbType()) == null) {
			throw new DbConnectException(alias,
					"dbType 不支援：" + info.getDbType() + "（支援：" + DbConnectionManager.supportedDbTypes() + "）");
		}
		return info;
	}

	private DbConnectApiResponse callApi(String alias) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

		byte[] body;
		try {
			// 以 UTF-8 byte[] 送出，避免 StringHttpMessageConverter 預設 ISO-8859-1 編碼
			byte[] requestBody = objectMapper.writeValueAsBytes(Collections.singletonMap("alias", alias));
			ResponseEntity<byte[]> entity = restTemplate.exchange(apiUrl, HttpMethod.POST,
					new HttpEntity<>(requestBody, headers), byte[].class);
			// 取數值判斷 2xx（非標準 HTTP 碼也照樣得到數值）
			int status = entity.getStatusCode().value();
			if (status < 200 || status > 299) {
				devTemplateDb.info("連線資訊 API 呼叫失敗 alias={} HTTP {}", alias, status);
				throw new DbConnectException(alias, "API 回應 HTTP " + status);
			}
			body = entity.getBody();
		} catch (JacksonException e) {
			throw new DbConnectException(alias, "組 API 請求內容失敗", e);
		} catch (RestClientException e) {
			devTemplateDb.info("連線資訊 API 呼叫失敗 alias={} 原因={}", alias, e.getClass().getSimpleName());
			throw new DbConnectException(alias, "API 連線失敗或逾時", e);
		} catch (IllegalArgumentException e) {
			// db.connect.api.domain.path 格式錯誤等；log 只記類別名
			devTemplateDb.info("連線資訊 API 呼叫失敗 alias={} 原因={}", alias, e.getClass().getSimpleName());
			throw new DbConnectException(alias, "API 呼叫失敗（" + e.getClass().getSimpleName() + "），請檢查 db.connect.api.domain.path", e);
		}

		if (body == null || body.length == 0) {
			throw new DbConnectException(alias, "API 回應內容為空");
		}
		try {
			DbConnectApiResponse response = objectMapper.readValue(body, DbConnectApiResponse.class);
			if (response == null) {
				throw new DbConnectException(alias, "API 回應內容為空");
			}
			return response;
		} catch (JacksonException e) {
			// 不掛原例外：Jackson 的訊息可能帶出損壞處附近的 token（例如未加引號的 password），
			// 改掛只含例外類別名的 RuntimeException（不帶原訊息、不帶原 cause）
			throw new DbConnectException(alias, "API 回應格式無法解析", new RuntimeException(e.getClass().getName()));
		}
	}

	private static void requireText(String alias, String field, String value) {
		if (value == null || value.trim().isEmpty()) {
			throw new DbConnectException(alias, "API 回應缺少 " + field);
		}
	}

	/** 不丟例外，交由 callApi 自行判斷狀態碼（避免 HttpStatusCodeException 訊息夾帶回應本文） */
	private static class PassThroughErrorHandler implements ResponseErrorHandler {
		@Override
		public boolean hasError(ClientHttpResponse response) {
			return false;
		}

		@Override
		public void handleError(URI url, HttpMethod method, ClientHttpResponse response) {
			// hasError 恆為 false，不會進來
		}
	}
}
