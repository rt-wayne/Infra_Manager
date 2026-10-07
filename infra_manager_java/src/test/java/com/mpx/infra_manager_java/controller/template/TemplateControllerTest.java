package com.mpx.infra_manager_java.controller.template;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：/api/templates 的 MockMvc 測試（S5 R1）。比照 AppDraftControllerTest 走真的登入流程。
//           鎖定：未登入 401；列表與單筆 200 且 JSON 欄位名（tmplId、tmplName、canEdit、form.prioCode）；
//           POST 201 {tmplId} 且服務收到登入者；PUT 200；DELETE 204；寫入端點缺 CSRF 403 且不呼叫服務；
//           服務丟出的 403／404／400 對應狀態碼；本文不是 JSON 400
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.mpx.infra_manager_java.config.SecurityConfig;
import com.mpx.infra_manager_java.controller.auth.AuthController;
import com.mpx.infra_manager_java.dao.auth.UserDao;
import com.mpx.infra_manager_java.model.auth.AuthUser;
import com.mpx.infra_manager_java.model.auth.UserRow;
import com.mpx.infra_manager_java.model.template.TemplateDetail;
import com.mpx.infra_manager_java.model.template.TemplateForm;
import com.mpx.infra_manager_java.model.template.TemplateListItem;
import com.mpx.infra_manager_java.model.template.TemplateRequest;
import com.mpx.infra_manager_java.service.auth.AuthService;
import com.mpx.infra_manager_java.service.template.TemplateService;
import com.mpx.infra_manager_java.web.ApiBadRequestException;
import com.mpx.infra_manager_java.web.ApiNotFoundException;

import jakarta.servlet.http.Cookie;

@WebMvcTest(controllers = { TemplateController.class, AuthController.class })
@Import({ SecurityConfig.class, AuthService.class })
class TemplateControllerTest {

	private static final String BODY = "{\"tmplName\":\"防火牆韌體升級\",\"form\":{\"prioCode\":\"P3\"}}";
	private static final String ID = "tpl_firewall_upgrade";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PasswordEncoder encoder;

	@MockitoBean
	private UserDao userDao;

	@MockitoBean
	private TemplateService templateService;

	private record Session(MockHttpSession session, Cookie xsrf) {
	}

	private Session login() throws Exception {
		UserRow row = new UserRow();
		row.setUserId("E0001");
		row.setLoginId("wayne");
		row.setUserName("測試人員");
		row.setPwdHash(encoder.encode("secret"));
		row.setStatus(1);
		row.setIsDfltPwd(0);
		when(userDao.findByLoginId("wayne")).thenReturn(Optional.of(row));
		when(userDao.findActiveRoleIds("E0001")).thenReturn(List.of("infra"));

		MvcResult me = mockMvc.perform(get("/api/auth/me")).andExpect(status().isOk()).andReturn();
		Cookie xsrf = me.getResponse().getCookie(SecurityConfig.XSRF_COOKIE);
		MvcResult result = mockMvc.perform(post("/api/auth/login").cookie(xsrf)
				.header(SecurityConfig.XSRF_HEADER, xsrf.getValue()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"loginId\":\"wayne\",\"password\":\"secret\"}")).andExpect(status().isOk()).andReturn();
		MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
		Cookie rotated = result.getResponse().getCookie(SecurityConfig.XSRF_COOKIE);
		return new Session(session, rotated != null && !rotated.getValue().isEmpty() ? rotated : xsrf);
	}

	private static TemplateForm form() {
		return new TemplateForm(null, "P3", null, null, null, null, null, "韌體升級", null, null, null, null, null,
				null, null, null, null, null, List.of("備份設定檔"), null, null);
	}

	@Test
	void 未登入_401() throws Exception {
		mockMvc.perform(get("/api/templates")).andExpect(status().isUnauthorized());
		verifyNoInteractions(templateService);
	}

	@Test
	void 列表與單筆_200() throws Exception {
		Session s = login();
		when(templateService.list(any())).thenReturn(List.of(new TemplateListItem(ID, "防火牆韌體升級", "P3", "一般",
				"#999", "王小明", 7, "2026-09-18 16:12", "蕭建弘", "2026-05-01 09:30", false)));
		when(templateService.get(eq(ID), any())).thenReturn(new TemplateDetail(ID, "防火牆韌體升級", form(), "王小明", 7,
				null, null, "2026-05-01 09:30", "2026-05-01 09:30", true));

		mockMvc.perform(get("/api/templates").session(s.session())).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].tmplId").value(ID)).andExpect(jsonPath("$[0].tmplName").value("防火牆韌體升級"))
				.andExpect(jsonPath("$[0].useCnt").value(7)).andExpect(jsonPath("$[0].canEdit").value(false));
		mockMvc.perform(get("/api/templates/" + ID).session(s.session())).andExpect(status().isOk())
				.andExpect(jsonPath("$.form.prioCode").value("P3"))
				.andExpect(jsonPath("$.form.planSteps[0]").value("備份設定檔"))
				.andExpect(jsonPath("$.canEdit").value(true));
	}

	@Test
	void 新增_201回tmplId且服務收到登入者() throws Exception {
		Session s = login();
		when(templateService.create(any(), any())).thenReturn("tpl_1790000000000_ab12");

		mockMvc.perform(post("/api/templates").session(s.session()).cookie(s.xsrf())
				.header(SecurityConfig.XSRF_HEADER, s.xsrf().getValue()).contentType(MediaType.APPLICATION_JSON)
				.content(BODY)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.tmplId").value("tpl_1790000000000_ab12"));

		ArgumentCaptor<TemplateRequest> req = ArgumentCaptor.forClass(TemplateRequest.class);
		ArgumentCaptor<AuthUser> user = ArgumentCaptor.forClass(AuthUser.class);
		verify(templateService).create(req.capture(), user.capture());
		assertThat(req.getValue().tmplName()).isEqualTo("防火牆韌體升級");
		assertThat(req.getValue().form().prioCode()).isEqualTo("P3");
		assertThat(user.getValue().userId()).isEqualTo("E0001");
	}

	@Test
	void 寫入端點缺CSRF_403且不呼叫服務() throws Exception {
		Session s = login();

		mockMvc.perform(post("/api/templates").session(s.session()).contentType(MediaType.APPLICATION_JSON)
				.content(BODY)).andExpect(status().isForbidden());
		mockMvc.perform(put("/api/templates/" + ID).session(s.session()).contentType(MediaType.APPLICATION_JSON)
				.content(BODY)).andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/templates/" + ID).session(s.session())).andExpect(status().isForbidden());
		verifyNoInteractions(templateService);
	}

	@Test
	void 修改200_刪除204() throws Exception {
		Session s = login();

		mockMvc.perform(put("/api/templates/" + ID).session(s.session()).cookie(s.xsrf())
				.header(SecurityConfig.XSRF_HEADER, s.xsrf().getValue()).contentType(MediaType.APPLICATION_JSON)
				.content(BODY)).andExpect(status().isOk()).andExpect(jsonPath("$.tmplId").value(ID));
		mockMvc.perform(delete("/api/templates/" + ID).session(s.session()).cookie(s.xsrf())
				.header(SecurityConfig.XSRF_HEADER, s.xsrf().getValue())).andExpect(status().isNoContent());

		verify(templateService).update(eq(ID), any(), any());
		verify(templateService).delete(eq(ID), any());
	}

	@Test
	void 服務例外對應403_404_400() throws Exception {
		Session s = login();
		doThrow(new AccessDeniedException(TemplateService.MSG_FORBIDDEN)).when(templateService).delete(eq(ID), any());
		when(templateService.get(eq("tpl_none"), any())).thenThrow(new ApiNotFoundException(TemplateService.MSG_NOT_FOUND));
		when(templateService.create(any(), any())).thenThrow(new ApiBadRequestException(TemplateService.MSG_NO_NAME));

		mockMvc.perform(delete("/api/templates/" + ID).session(s.session()).cookie(s.xsrf())
				.header(SecurityConfig.XSRF_HEADER, s.xsrf().getValue())).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/templates/tpl_none").session(s.session())).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value(TemplateService.MSG_NOT_FOUND));
		mockMvc.perform(post("/api/templates").session(s.session()).cookie(s.xsrf())
				.header(SecurityConfig.XSRF_HEADER, s.xsrf().getValue()).contentType(MediaType.APPLICATION_JSON)
				.content(BODY)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(TemplateService.MSG_NO_NAME));
	}

	@Test
	void 本文不是JSON_400() throws Exception {
		Session s = login();

		mockMvc.perform(post("/api/templates").session(s.session()).cookie(s.xsrf())
				.header(SecurityConfig.XSRF_HEADER, s.xsrf().getValue()).contentType(MediaType.APPLICATION_JSON)
				.content("{not json")).andExpect(status().isBadRequest());
		verifyNoInteractions(templateService);
	}
}
