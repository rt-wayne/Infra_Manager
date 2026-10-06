package com.mpx.infra_manager_java.dao.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：S4 三個 DAO 對真實測試 Oracle 的整合測試（只由 mvnw verify 執行；host.properties 的連線資訊 API
//           位址為空時整個略過）。目的是確認 SQL 本身在 Oracle 上能跑（欄名、別名、OFFSET／FETCH、ESCAPE、UNION ALL），
//           不依賴任何種子資料：查不存在的單號與使用者，只驗「不丟例外」與空結果；流程定義展開只驗 V1 內建的 full 流程存在
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import com.mpx.infra_manager_java.model.changerequest.AppListQuery;

@SpringBootTest
class AppDaoIT {

	private static final String NOBODY = "NOBODY_S4_IT";
	private static final String NO_APP = "IM00000000-000";

	@Autowired
	private AppDao appDao;

	@Autowired
	private ApprovalDao approvalDao;

	@Autowired
	private AttachDao attachDao;

	@Value("${db.connect.api.domain.path:}")
	private String apiUrl;

	@BeforeEach
	void requireDb() {
		assumeTrue(apiUrl != null && !apiUrl.isBlank(), "host.properties 未設定連線資訊 API，略過整合測試");
	}

	@Test
	void 列表三種查詢在Oracle上可執行() {
		AppListQuery all = new AppListQuery("IN_REVIEW", "P3", "ONLINE", true, "50%_\\x", LocalDate.now().minusDays(90),
				LocalDate.now(), 2);

		assertThat(appDao.findList(all, NOBODY)).isNotNull();
		assertThat(appDao.countList(all, NOBODY)).isGreaterThanOrEqualTo(0);
		assertThat(appDao.findList(AppListQuery.none(), NOBODY)).isNotNull();
		assertThat(appDao.countMine(NOBODY)).isZero();
	}

	@Test
	void 檢視各子查詢對不存在的單號回空() {
		assertThat(appDao.findById(NO_APP)).isEmpty();
		assertThat(appDao.findOptions(NO_APP)).isEmpty();
		assertThat(appDao.findEquipments(NO_APP)).isEmpty();
		assertThat(appDao.findPlanSteps(NO_APP)).isEmpty();
		assertThat(appDao.findCheckList(NO_APP, 1)).isEmpty();
		assertThat(appDao.findExec(NO_APP, 1)).isEmpty();
		assertThat(appDao.findVersions(NO_APP)).isEmpty();
		assertThat(appDao.findEvents(NO_APP)).isEmpty();
		assertThat(attachDao.findByApp(NO_APP)).isEmpty();
		assertThat(approvalDao.findCurrent(NO_APP, 1)).isEmpty();
		assertThat(approvalDao.findSteps(-1L)).isEmpty();
		assertThat(approvalDao.findCandidates(-1L)).isEmpty();
	}

	@Test
	void 流程定義展開_內建full流程有關卡且狀態WAITING() {
		assertThat(approvalDao.findFlowSteps("full")).isNotEmpty()
				.allSatisfy(s -> {
					assertThat(s.getApprStepId()).isNull();
					assertThat(s.getStepStatusCode()).isEqualTo("WAITING");
					assertThat(s.getStepName()).isNotBlank();
				});
		assertThat(approvalDao.findFlowSteps("no_such_flow")).isEmpty();
	}
}
