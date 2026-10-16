package com.mpx.common.web;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-06
// 變更說明: 新增：RestTemplateConfig 輕量測試（規格 v1.4 D-51，取代原轉發層的測試）
//           只建 RestTemplateConfig 與 RestTemplateBuilder，不啟動 web 環境、不讀 host.properties、不連外
//           驗：RestTemplate bean 存在且只有一個；connect 5 秒、read 120 秒
//           jdk25 階段 7 第 1 輪審查修正（2026-10-06）：逾時測試改用 Mockito mock RestTemplateBuilder（RETURNS_SELF）
//           驗 connectTimeout／readTimeout 的呼叫參數，不再反射讀 JdkClientHttpRequestFactory 私有欄位
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.boot.restclient.autoconfigure.RestTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestTemplate;

class RestTemplateConfigTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(RestTemplateAutoConfiguration.class))
			.withUserConfiguration(RestTemplateConfig.class);

	@Test
	void restTemplateBeanExists() {
		runner.run(ctx -> assertThat(ctx).hasSingleBean(RestTemplate.class));
	}

	@Test
	void timeoutsAreConnect5sRead120s() {
		RestTemplateBuilder builder = mock(RestTemplateBuilder.class, RETURNS_SELF);
		RestTemplate built = new RestTemplate();
		when(builder.build()).thenReturn(built);

		assertThat(new RestTemplateConfig().restTemplate(builder)).isSameAs(built);
		verify(builder).connectTimeout(Duration.ofSeconds(5));
		verify(builder).readTimeout(Duration.ofSeconds(120));
	}
}
