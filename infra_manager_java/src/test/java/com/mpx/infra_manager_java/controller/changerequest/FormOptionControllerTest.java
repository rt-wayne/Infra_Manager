package com.mpx.infra_manager_java.controller.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-06
// 變更說明: 新增：GET /api/form-options 的 MockMvc 測試（S6 回合二 a）。登入流程同 AppControllerTest；
//           鎖定未登入 401、登入後回 options 與 upload 的 JSON 結構
// ============================================================

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.mpx.infra_manager_java.config.SecurityConfig;
import com.mpx.infra_manager_java.controller.auth.AuthController;
import com.mpx.infra_manager_java.dao.auth.UserDao;
import com.mpx.infra_manager_java.model.auth.UserRow;
import com.mpx.infra_manager_java.model.changerequest.FormOptionsResponse;
import com.mpx.infra_manager_java.model.changerequest.FormOptionsResponse.FormOption;
import com.mpx.infra_manager_java.model.changerequest.FormOptionsResponse.UploadLimits;
import com.mpx.infra_manager_java.service.auth.AuthService;
import com.mpx.infra_manager_java.service.changerequest.FormOptionService;

import jakarta.servlet.http.Cookie;

@WebMvcTest(controllers = { FormOptionController.class, AuthController.class })
@Import({ SecurityConfig.class, AuthService.class })
class FormOptionControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PasswordEncoder encoder;

	@MockitoBean
	private UserDao userDao;

	@MockitoBean
	private FormOptionService formOptionService;

	private MockHttpSession login() throws Exception {
		UserRow row = new UserRow();
		row.setUserId("E0001");
		row.setLoginId("wayne");
		row.setUserName("測試人員");
		row.setPwdHash(encoder.encode("secret"));
		row.setStatus(1);
		row.setIsDfltPwd(0);
		when(userDao.findByLoginId("wayne")).thenReturn(Optional.of(row));
		when(userDao.findActiveRoleIds("E0001")).thenReturn(List.of("applicant"));

		MvcResult me = mockMvc.perform(get("/api/auth/me")).andExpect(status().isOk()).andReturn();
		Cookie xsrf = me.getResponse().getCookie(SecurityConfig.XSRF_COOKIE);
		MvcResult result = mockMvc.perform(post("/api/auth/login").cookie(xsrf)
				.header(SecurityConfig.XSRF_HEADER, xsrf.getValue()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"loginId\":\"wayne\",\"password\":\"secret\"}")).andExpect(status().isOk()).andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	@Test
	void 未登入401() throws Exception {
		mockMvc.perform(get("/api/form-options")).andExpect(status().isUnauthorized())
				.andExpect(content().json("{\"message\":\"" + SecurityConfig.MSG_NOT_LOGGED_IN + "\"}"));
	}

	@Test
	void 登入後回選項與上傳限制() throws Exception {
		MockHttpSession session = login();
		when(formOptionService.load()).thenReturn(new FormOptionsResponse(
				List.of(new FormOption(1L, "PRIO", "P1", "P1 緊急", null, "#dc2626", null, "4 小時內", null, null,
						"FLOW_FULL", 1)),
				new UploadLimits(50, 30)));

		mockMvc.perform(get("/api/form-options").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.options[0].formOptionId").value(1))
				.andExpect(jsonPath("$.options[0].groupCode").value("PRIO"))
				.andExpect(jsonPath("$.options[0].code").value("P1"))
				.andExpect(jsonPath("$.options[0].name").value("P1 緊急"))
				.andExpect(jsonPath("$.options[0].timeLimitDesc").value("4 小時內"))
				.andExpect(jsonPath("$.upload.maxMb").value(50))
				.andExpect(jsonPath("$.upload.maxFiles").value(30));
	}
}
