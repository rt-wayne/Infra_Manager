package com.mpx.infra_manager_java;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-05
// 變更說明: 新增：整合測試（S1，裁示 ②A）。*IT 只由 mvnw verify（maven-failsafe）執行，mvnw clean package 不跑；
//           連真實公司測試 Oracle（連線資訊由連線資訊 API 提供，不在 repo）；host.properties 的 API 位址為空時整個略過。
//           本測試只讀（SELECT 1 FROM DUAL），不寫入、不需清理。
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import com.mpx.infra_manager_java.model.HealthStatus;
import com.mpx.infra_manager_java.service.HealthService;

@SpringBootTest
class HealthIT {

	@Autowired
	private HealthService healthService;

	@Value("${db.connect.api.domain.path:}")
	private String apiUrl;

	@Test
	void 真實DB連線健康檢查為UP() {
		assumeTrue(apiUrl != null && !apiUrl.isBlank(), "host.properties 未設定連線資訊 API，略過整合測試");

		HealthStatus s = healthService.check();

		assertThat(s.getStatus()).isEqualTo("UP");
		assertThat(s.getDb()).isEqualTo("UP");
	}
}
