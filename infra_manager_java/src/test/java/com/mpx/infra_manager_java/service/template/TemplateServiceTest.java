package com.mpx.infra_manager_java.service.template;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：範本服務的單元測試（S5 R1）。鎖定：
//           新增——ID 格式 tpl_<毫秒>_<4 hex>、建立者為登入者、FORM_JSON 經正規化（去空白、去空白列、不含申請人與預定起訖）、
//           標題可空；名稱必填與長度、缺 form、優先等級錯誤 → 400 且不寫入。
//           修改／刪除——建立者與 admin 可以、其他人 403 且不寫入（第 43 項裁示 A）；找不到或 ID 格式不對 404；
//           UPDATE 0 列（期間被刪）404。檢視——form 解析、canEdit、FORM_JSON 壞掉時 form 為 null；
//           列表——updatedAt 沒改過時用建立時間
//           2026-10-07 R3：套用——任何登入者都累計；範本不存在或 ID 格式不對時不丟例外（不擋建單）
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import com.mpx.infra_manager_java.dao.changerequest.FormOptionDao;
import com.mpx.infra_manager_java.dao.template.TemplateDao;
import com.mpx.infra_manager_java.model.auth.AuthUser;
import com.mpx.infra_manager_java.model.changerequest.AppDraftRequest;
import com.mpx.infra_manager_java.model.changerequest.FormOptionRow;
import com.mpx.infra_manager_java.model.template.TemplateDetail;
import com.mpx.infra_manager_java.model.template.TemplateForm;
import com.mpx.infra_manager_java.model.template.TemplateListItem;
import com.mpx.infra_manager_java.model.template.TemplateRequest;
import com.mpx.infra_manager_java.model.template.TemplateRow;
import com.mpx.infra_manager_java.util.TextTooLongException;
import com.mpx.infra_manager_java.web.ApiBadRequestException;
import com.mpx.infra_manager_java.web.ApiNotFoundException;

class TemplateServiceTest {

	private static final AuthUser OWNER = new AuthUser("T0001", "wayne", "王小明", List.of("infra"), false);
	private static final AuthUser OTHER = new AuthUser("T0002", "amy", "林小美", List.of("infra"), false);
	private static final AuthUser ADMIN = new AuthUser("A0001", "admin", "管理員", List.of("admin"), false);

	private final TemplateDao templateDao = mock(TemplateDao.class);
	private final FormOptionDao formOptionDao = mock(FormOptionDao.class);
	private final TemplateService service = new TemplateService(templateDao, formOptionDao);

	private static FormOptionRow option(long id, String group) {
		FormOptionRow o = new FormOptionRow();
		o.setFormOptionId(id);
		o.setGroupCode(group);
		return o;
	}

	private static TemplateForm form(String title, String prioCode) {
		return new TemplateForm(title, prioCode, null, null, null, null, null, null, null, null, null, null, null,
				null, null, null, null, null, null, null, null);
	}

	private static TemplateRow row(String ownerUserId) {
		TemplateRow r = new TemplateRow();
		r.setTmplId("tpl_firewall_upgrade");
		r.setTmplName("防火牆韌體升級");
		r.setOwnerUserId(ownerUserId);
		r.setOwnerName("王小明");
		r.setUseCnt(7);
		r.setCreateDate(Timestamp.valueOf("2026-05-01 09:30:00"));
		r.setFormJson("{\"prioCode\":\"P3\",\"workSubject\":\"韌體升級\",\"planSteps\":[\"備份設定檔\"],"
				+ "\"schedule\":{\"estHours\":4},\"legacyField\":1}");
		return r;
	}

	@Test
	void 新增_產生ID並以登入者為建立者_內容經正規化() {
		when(formOptionDao.findActive()).thenReturn(List.of(option(11L, "REASON")));
		TemplateForm f = new TemplateForm(null, "P2", null, null, null, null,
				new AppDraftRequest.Supplier("  ", null, null, null), "  韌體升級  ", null, null, null, null, null, null,
				List.of(11L, 11L), null, null,
				Arrays.asList(new AppDraftRequest.Equipment(" ", null, null, null, null, null),
						new AppDraftRequest.Equipment("FW-01", null, null, null, null, null)),
				Arrays.asList("備份設定檔", "  ", "升級"), new TemplateForm.Schedule(new BigDecimal("4")), null);

		String id = service.create(new TemplateRequest("  防火牆韌體升級  ", f), OWNER);

		assertThat(id).matches("^tpl_\\d{13}_[0-9a-f]{4}$");
		ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
		verify(templateDao).insert(eq(id), eq("防火牆韌體升級"), json.capture(), eq("T0001"));
		assertThat(json.getValue())
				.contains("\"prioCode\":\"P2\"", "\"workSubject\":\"韌體升級\"", "\"reasonIds\":[11]",
						"\"planSteps\":[\"備份設定檔\",\"升級\"]", "\"estHours\":4", "\"name\":\"FW-01\"")
				.contains("\"supplier\":null", "\"title\":null")
				.doesNotContain("applicant", "\"start\"", "\"end\"", "rowVerNo");
	}

	@Test
	void 新增_名稱空白或缺form或優先等級錯誤_400且不寫入() {
		when(formOptionDao.findActive()).thenReturn(List.of());

		assertThatThrownBy(() -> service.create(new TemplateRequest("   ", form(null, "P3")), OWNER))
				.isInstanceOf(ApiBadRequestException.class).hasMessage(TemplateService.MSG_NO_NAME);
		assertThatThrownBy(() -> service.create(new TemplateRequest("範本", null), OWNER))
				.isInstanceOf(ApiBadRequestException.class).hasMessage(TemplateService.MSG_NO_FORM);
		assertThatThrownBy(() -> service.create(new TemplateRequest("範本", form(null, "P9")), OWNER))
				.isInstanceOf(ApiBadRequestException.class).hasMessage("請選擇優先等級");
		assertThatThrownBy(() -> service.create(null, OWNER)).isInstanceOf(ApiBadRequestException.class);
		verify(templateDao, never()).insert(anyString(), anyString(), anyString(), anyString());
	}

	@Test
	void 新增_名稱超過100字_TextTooLongException() {
		when(formOptionDao.findActive()).thenReturn(List.of());
		String name = "名".repeat(TemplateService.MAX_NAME + 1);

		assertThatThrownBy(() -> service.create(new TemplateRequest(name, form(null, "P3")), OWNER))
				.isInstanceOf(TextTooLongException.class);
		verify(templateDao, never()).insert(anyString(), anyString(), anyString(), anyString());
	}

	@Test
	void 新增_選項群組不對_400() {
		when(formOptionDao.findActive()).thenReturn(List.of(option(11L, "REASON")));
		TemplateForm f = new TemplateForm(null, "P3", null, null, null, null, null, null, null, null, null, null,
				List.of(11L), null, null, null, null, null, null, null, null);

		assertThatThrownBy(() -> service.create(new TemplateRequest("範本", f), OWNER))
				.isInstanceOf(ApiBadRequestException.class);
	}

	@Test
	void 修改_建立者與admin可以() {
		when(formOptionDao.findActive()).thenReturn(List.of());
		when(templateDao.findById("tpl_firewall_upgrade")).thenReturn(row("T0001"));
		when(templateDao.update(eq("tpl_firewall_upgrade"), anyString(), anyString(), anyString())).thenReturn(1);

		service.update("tpl_firewall_upgrade", new TemplateRequest("新名稱", form("標題", "P3")), OWNER);
		service.update("tpl_firewall_upgrade", new TemplateRequest("新名稱", form("標題", "P3")), ADMIN);

		verify(templateDao).update(eq("tpl_firewall_upgrade"), eq("新名稱"), anyString(), eq("T0001"));
		verify(templateDao).update(eq("tpl_firewall_upgrade"), eq("新名稱"), anyString(), eq("A0001"));
	}

	@Test
	void 修改_非建立者403且不寫入() {
		when(templateDao.findById("tpl_firewall_upgrade")).thenReturn(row("T0001"));

		assertThatThrownBy(
				() -> service.update("tpl_firewall_upgrade", new TemplateRequest("新名稱", form(null, "P3")), OTHER))
				.isInstanceOf(AccessDeniedException.class).hasMessage(TemplateService.MSG_FORBIDDEN);
		verify(templateDao, never()).update(anyString(), anyString(), anyString(), anyString());
		verifyNoInteractions(formOptionDao);
	}

	@Test
	void 修改_找不到或期間被刪或ID格式不對_404() {
		when(formOptionDao.findActive()).thenReturn(List.of());
		TemplateRequest req = new TemplateRequest("新名稱", form(null, "P3"));

		assertThatThrownBy(() -> service.update("tpl_none", req, OWNER)).isInstanceOf(ApiNotFoundException.class);

		when(templateDao.findById("tpl_firewall_upgrade")).thenReturn(row("T0001"));
		when(templateDao.update(any(), any(), any(), any())).thenReturn(0);
		assertThatThrownBy(() -> service.update("tpl_firewall_upgrade", req, OWNER))
				.isInstanceOf(ApiNotFoundException.class).hasMessage(TemplateService.MSG_NOT_FOUND);

		assertThatThrownBy(() -> service.update("../x", req, OWNER)).isInstanceOf(ApiNotFoundException.class);
		verify(templateDao, never()).findById("../x");
	}

	@Test
	void 刪除_建立者軟刪除_非建立者403_找不到404() {
		when(templateDao.findById("tpl_firewall_upgrade")).thenReturn(row("T0001"));
		when(templateDao.softDelete("tpl_firewall_upgrade", "T0001")).thenReturn(1);

		service.delete("tpl_firewall_upgrade", OWNER);
		verify(templateDao).softDelete("tpl_firewall_upgrade", "T0001");

		assertThatThrownBy(() -> service.delete("tpl_firewall_upgrade", OTHER))
				.isInstanceOf(AccessDeniedException.class);
		verify(templateDao, never()).softDelete("tpl_firewall_upgrade", "T0002");

		assertThatThrownBy(() -> service.delete("tpl_none", OWNER)).isInstanceOf(ApiNotFoundException.class);
	}

	@Test
	void 刪除_期間已被刪_404() {
		when(templateDao.findById("tpl_firewall_upgrade")).thenReturn(row("T0001"));
		when(templateDao.softDelete("tpl_firewall_upgrade", "A0001")).thenReturn(0);

		assertThatThrownBy(() -> service.delete("tpl_firewall_upgrade", ADMIN))
				.isInstanceOf(ApiNotFoundException.class);
	}

	@Test
	void 檢視_解析form並帶canEdit_忽略未知欄位() {
		when(templateDao.findById("tpl_firewall_upgrade")).thenReturn(row("T0001"));

		TemplateDetail mine = service.get("tpl_firewall_upgrade", OWNER);
		assertThat(mine.form().prioCode()).isEqualTo("P3");
		assertThat(mine.form().planSteps()).containsExactly("備份設定檔");
		assertThat(mine.form().schedule().estHours()).isEqualByComparingTo("4");
		assertThat(mine.canEdit()).isTrue();
		assertThat(mine.useCnt()).isEqualTo(7);
		assertThat(mine.createdAt()).isEqualTo("2026-05-01 09:30");
		assertThat(mine.updatedAt()).isEqualTo("2026-05-01 09:30");

		assertThat(service.get("tpl_firewall_upgrade", OTHER).canEdit()).isFalse();
		assertThat(service.get("tpl_firewall_upgrade", ADMIN).canEdit()).isTrue();
	}

	@Test
	void 檢視_FORM_JSON壞掉時form為null_其餘照常() {
		TemplateRow r = row("T0001");
		r.setFormJson("{not json");
		when(templateDao.findById("tpl_firewall_upgrade")).thenReturn(r);

		TemplateDetail d = service.get("tpl_firewall_upgrade", OWNER);
		assertThat(d.form()).isNull();
		assertThat(d.tmplName()).isEqualTo("防火牆韌體升級");
	}

	@Test
	void 列表_帶出各欄位與canEdit() {
		TemplateRow r = row("T0001");
		r.setFormJson(null);
		r.setPrioCode("P3");
		r.setPrioName("一般");
		r.setUpdateDate(Timestamp.valueOf("2026-09-18 16:12:00"));
		r.setLastUseDate(Timestamp.valueOf("2026-09-18 16:12:36"));
		r.setLastUseUserName("蕭建弘");
		TemplateRow noCnt = row("T0009");
		noCnt.setUseCnt(null);
		when(templateDao.findActive()).thenReturn(List.of(r, noCnt));

		List<TemplateListItem> items = service.list(OWNER);

		assertThat(items).hasSize(2);
		TemplateListItem first = items.get(0);
		assertThat(first.prioName()).isEqualTo("一般");
		assertThat(first.updatedAt()).isEqualTo("2026-09-18 16:12");
		assertThat(first.lastUsedAt()).isEqualTo("2026-09-18 16:12");
		assertThat(first.lastUsedByName()).isEqualTo("蕭建弘");
		assertThat(first.canEdit()).isTrue();
		assertThat(items.get(1).canEdit()).isFalse();
		assertThat(items.get(1).useCnt()).isZero();
	}

	@Test
	void 套用_任何登入者都累計_範本不存在或ID格式不對時不丟例外() {
		when(templateDao.recordUse("tpl_firewall_upgrade", "T0002")).thenReturn(1);
		service.recordUse("tpl_firewall_upgrade", OTHER);
		verify(templateDao).recordUse("tpl_firewall_upgrade", "T0002");

		when(templateDao.recordUse("tpl_gone", "T0002")).thenReturn(0);
		service.recordUse("tpl_gone", OTHER);

		service.recordUse("../etc", OTHER);
		service.recordUse("", OTHER);
		verify(templateDao, never()).recordUse(eq("../etc"), anyString());
		verify(templateDao, never()).recordUse(eq(""), anyString());
	}
}
