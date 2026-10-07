package com.mpx.infra_manager_java.service.template;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：範本 CRUD 對真實測試 Oracle 的整合測試（S5 R1；只由 mvnw verify 執行；連線資訊 API 為空、
//           測試帳號 T0001 不存在、或沒有啟用中的優先等級選項時略過）。
//           鎖定：新增後列表看得到且優先等級名稱／顏色由 JSON_VALUE 帶出、建立者姓名、使用次數 0、canEdit 依身分；
//           單筆 FORM_JSON 往返後內容一致（CLOB 綁定）；建立者修改後名稱與內容更新；他人修改 403 且不寫入；
//           admin 可刪；刪除後列表消失、單筆 404、再刪 404，DB 列 STATUS=0 且 UPDATE_BY 為刪除者。
//           測試結束硬刪本測試建的範本列（只限本測試回傳的 TMPL_ID）
//           2026-10-07 R3：套用兩次後次數 2、最後套用人為套用者、UPDATE_DATE 不動；已刪除的範本套用不累計
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;

import com.mpx.common.db.DbClient;
import com.mpx.infra_manager_java.config.DbSchema;
import com.mpx.infra_manager_java.dao.changerequest.FormOptionDao;
import com.mpx.infra_manager_java.model.DualRow;
import com.mpx.infra_manager_java.model.auth.AuthUser;
import com.mpx.infra_manager_java.model.changerequest.AppDraftRequest;
import com.mpx.infra_manager_java.model.changerequest.FormOptionRow;
import com.mpx.infra_manager_java.model.template.TemplateDetail;
import com.mpx.infra_manager_java.model.template.TemplateForm;
import com.mpx.infra_manager_java.model.template.TemplateListItem;
import com.mpx.infra_manager_java.model.template.TemplateRequest;
import com.mpx.infra_manager_java.web.ApiNotFoundException;

@SpringBootTest
class TemplateServiceIT {

	private static final String OWNER_ID = "T0001";
	private static final AuthUser OWNER = new AuthUser(OWNER_ID, "wayne", "整合測試建立者", List.of("infra"), false);
	private static final AuthUser OTHER = new AuthUser("S5IT_OTHER", "other", "他人", List.of("infra"), false);
	private static final AuthUser ADMIN = new AuthUser("S5IT_ADMIN", "admin", "管理員", List.of("admin"), false);

	@Autowired
	private TemplateService templateService;

	@Autowired
	private FormOptionDao formOptionDao;

	@Autowired
	private DbClient dbClient;

	@Autowired
	private DbSchema schema;

	@Value("${db.connect.api.domain.path:}")
	private String apiUrl;

	@Value("${db.connect.itflow}")
	private String itflowDb;

	private final List<String> created = new ArrayList<>();
	private FormOptionRow prio;

	@BeforeEach
	void setUp() {
		assumeTrue(apiUrl != null && !apiUrl.isBlank(), "host.properties 未設定連線資訊 API，略過整合測試");
		assumeTrue(exists("SELECT COUNT(*) AS OK FROM " + schema.table("IM_USER") + " WHERE USER_ID = :u",
				Map.of("u", OWNER_ID)), "測試帳號不存在，略過");
		prio = formOptionDao.findActive().stream().filter(o -> "PRIO".equals(o.getGroupCode())).findFirst()
				.orElse(null);
		assumeTrue(prio != null, "沒有啟用中的優先等級選項，略過");
	}

	@AfterEach
	void cleanUp() {
		for (String id : created) {
			dbClient.update(itflowDb, "DELETE FROM " + schema.table("IM_TMPL") + " WHERE TMPL_ID = :id",
					Map.of("id", id));
		}
	}

	private TemplateForm form(String subject) {
		return new TemplateForm("S5IT 標題", prio.getOptionCode(), null, true, "REMOTE", "VPN",
				new AppDraftRequest.Supplier("某廠商", null, null, 2), subject, "影響說明\n第二行", null, null, null,
				null, null, null, null, null,
				List.of(new AppDraftRequest.Equipment("FW-01", null, null, null, null, "10.0.0.1")),
				List.of("備份設定檔", "升級韌體"), new TemplateForm.Schedule(new BigDecimal("2.5")), null);
	}

	private String create(String name, String subject) {
		String id = templateService.create(new TemplateRequest(name, form(subject)), OWNER);
		created.add(id);
		return id;
	}

	@Test
	void 新增後列表與單筆看得到_FORM_JSON往返一致() {
		String id = create("S5IT 防火牆升級", "韌體升級");

		TemplateListItem item = templateService.list(OTHER).stream().filter(t -> t.tmplId().equals(id)).findFirst()
				.orElseThrow();
		assertThat(item.tmplName()).isEqualTo("S5IT 防火牆升級");
		assertThat(item.prioCode()).isEqualTo(prio.getOptionCode());
		assertThat(item.prioName()).isEqualTo(prio.getOptionName());
		assertThat(item.prioColor()).isEqualTo(prio.getColorCode());
		assertThat(item.ownerName()).isNotBlank();
		assertThat(item.useCnt()).isZero();
		assertThat(item.lastUsedAt()).isNull();
		assertThat(item.updatedAt()).isNotBlank();
		assertThat(item.canEdit()).isFalse();

		TemplateDetail d = templateService.get(id, OWNER);
		assertThat(d.canEdit()).isTrue();
		TemplateForm f = d.form();
		assertThat(f.title()).isEqualTo("S5IT 標題");
		assertThat(f.prioCode()).isEqualTo(prio.getOptionCode());
		assertThat(f.selfExec()).isTrue();
		assertThat(f.supplierExec()).isTrue();
		assertThat(f.workModeCode()).isEqualTo("REMOTE");
		assertThat(f.remoteMethod()).isEqualTo("VPN");
		assertThat(f.supplier()).isEqualTo(new AppDraftRequest.Supplier("某廠商", null, null, 2));
		assertThat(f.workSubject()).isEqualTo("韌體升級");
		assertThat(f.impactDesc()).isEqualTo("影響說明\n第二行");
		assertThat(f.categoryItemIds()).isEmpty();
		assertThat(f.equipments())
				.containsExactly(new AppDraftRequest.Equipment("FW-01", null, null, null, null, "10.0.0.1"));
		assertThat(f.planSteps()).containsExactly("備份設定檔", "升級韌體");
		assertThat(f.schedule().estHours()).isEqualByComparingTo("2.5");
		assertThat(f.location()).isNull();
		assertThat(templateService.get(id, ADMIN).canEdit()).isTrue();
	}

	@Test
	void 建立者可改_他人403且不寫入() {
		String id = create("S5IT 原名", "原內容");

		assertThatThrownBy(() -> templateService.update(id, new TemplateRequest("S5IT 他人改", form("他人內容")), OTHER))
				.isInstanceOf(AccessDeniedException.class);
		assertThat(templateService.get(id, OWNER).tmplName()).isEqualTo("S5IT 原名");

		templateService.update(id, new TemplateRequest("S5IT 新名", form("新內容")), OWNER);
		TemplateDetail d = templateService.get(id, OWNER);
		assertThat(d.tmplName()).isEqualTo("S5IT 新名");
		assertThat(d.form().workSubject()).isEqualTo("新內容");
	}

	@Test
	void admin刪除後消失_再刪404_DB為軟刪除() {
		String id = create("S5IT 待刪", "內容");

		assertThatThrownBy(() -> templateService.delete(id, OTHER)).isInstanceOf(AccessDeniedException.class);
		templateService.delete(id, ADMIN);

		assertThat(templateService.list(OWNER)).noneMatch(t -> t.tmplId().equals(id));
		assertThatThrownBy(() -> templateService.get(id, OWNER)).isInstanceOf(ApiNotFoundException.class);
		assertThatThrownBy(() -> templateService.delete(id, ADMIN)).isInstanceOf(ApiNotFoundException.class);
		assertThat(exists("SELECT COUNT(*) AS OK FROM " + schema.table("IM_TMPL")
				+ " WHERE TMPL_ID = :id AND STATUS = 0 AND UPDATE_BY = :u", Map.of("id", id, "u", ADMIN.userId())))
				.isTrue();
	}

	@Test
	void 套用兩次_次數與最後套用人更新_不動更新時間() {
		String id = create("S5IT 套用", "內容");

		templateService.recordUse(id, OWNER);
		templateService.recordUse(id, OWNER);

		TemplateDetail d = templateService.get(id, OTHER);
		assertThat(d.useCnt()).isEqualTo(2);
		assertThat(d.lastUsedAt()).isNotBlank();
		assertThat(d.lastUsedByName()).isEqualTo(d.ownerName());
		assertThat(exists("SELECT COUNT(*) AS OK FROM " + schema.table("IM_TMPL")
				+ " WHERE TMPL_ID = :id AND UPDATE_DATE IS NULL AND LAST_USE_USER_ID = :u",
				Map.of("id", id, "u", OWNER_ID))).isTrue();

		templateService.delete(id, OWNER);
		templateService.recordUse(id, OWNER);
		assertThat(exists("SELECT COUNT(*) AS OK FROM " + schema.table("IM_TMPL")
				+ " WHERE TMPL_ID = :id AND USE_CNT = 2", Map.of("id", id))).isTrue();
	}

	private boolean exists(String sql, Map<String, Object> params) {
		List<DualRow> rows = dbClient.query(itflowDb, sql, params, DualRow.class);
		return !rows.isEmpty() && rows.get(0).getOk() != null && rows.get(0).getOk() > 0;
	}
}
