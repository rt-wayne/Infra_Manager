package com.mpx.infra_manager_java.controller.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-07
// 變更說明: 新增：POST /api/apps/{id}/submit、/recall、/decisions 的 MockMvc 測試（S7 收尾回合）。比照 AppDraftControllerTest
//           走真的登入流程。鎖定：未登入 401；缺 CSRF 403；成功回 200 {appId, rowVerNo(新)} 且服務收到本文與登入者；
//           服務丟出的 403／404／409／400 原樣對應狀態碼與訊息；本文不是 JSON 回 400
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.springframework.test.web.servlet.ResultActions;

import com.mpx.infra_manager_java.config.SecurityConfig;
import com.mpx.infra_manager_java.controller.auth.AuthController;
import com.mpx.infra_manager_java.dao.auth.UserDao;
import com.mpx.infra_manager_java.model.auth.AuthUser;
import com.mpx.infra_manager_java.model.auth.UserRow;
import com.mpx.infra_manager_java.model.changerequest.DecisionRequest;
import com.mpx.infra_manager_java.model.changerequest.FlowActionRequest;
import com.mpx.infra_manager_java.service.auth.AuthService;
import com.mpx.infra_manager_java.service.changerequest.AppFlowService;
import com.mpx.infra_manager_java.service.changerequest.DecisionPolicy;
import com.mpx.infra_manager_java.web.ApiBadRequestException;
import com.mpx.infra_manager_java.web.ApiConflictException;
import com.mpx.infra_manager_java.web.ApiNotFoundException;

import jakarta.servlet.http.Cookie;

@WebMvcTest(controllers = { AppFlowController.class, AuthController.class })
@Import({ SecurityConfig.class, AuthService.class })
class AppFlowControllerTest {

	private static final String APP = "IM20261007-001";
	private static final String SUBMIT_BODY = "{\"rowVerNo\":3}";
	private static final String RECALL_BODY = "{\"rowVerNo\":5,\"reason\":\"資料要補\"}";
	private static final String DECIDE_BODY = "{\"rowVerNo\":7,\"decision\":\"APPROVE\",\"memo\":\"\"}";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PasswordEncoder encoder;

	@MockitoBean
	private UserDao userDao;

	@MockitoBean
	private AppFlowService appFlowService;

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
		when(userDao.findActiveRoleIds("E0001")).thenReturn(List.of("idc_admin"));

		MvcResult me = mockMvc.perform(get("/api/auth/me")).andExpect(status().isOk()).andReturn();
		Cookie xsrf = me.getResponse().getCookie(SecurityConfig.XSRF_COOKIE);
		MvcResult result = mockMvc.perform(post("/api/auth/login").cookie(xsrf)
				.header(SecurityConfig.XSRF_HEADER, xsrf.getValue()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"loginId\":\"wayne\",\"password\":\"secret\"}")).andExpect(status().isOk()).andReturn();
		MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
		Cookie rotated = result.getResponse().getCookie(SecurityConfig.XSRF_COOKIE);
		return new Session(session, rotated != null && !rotated.getValue().isEmpty() ? rotated : xsrf);
	}

	/** 已登入、帶 CSRF 的 POST */
	private ResultActions postJson(Session s, String path, String body) throws Exception {
		return mockMvc.perform(post(path).session(s.session()).cookie(s.xsrf())
				.header(SecurityConfig.XSRF_HEADER, s.xsrf().getValue()).contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	@Test
	void 未登入401() throws Exception {
		MvcResult me = mockMvc.perform(get("/api/auth/me")).andReturn();
		Cookie xsrf = me.getResponse().getCookie(SecurityConfig.XSRF_COOKIE);
		for (String action : List.of("submit", "recall", "decisions")) {
			mockMvc.perform(post("/api/apps/" + APP + "/" + action).cookie(xsrf)
					.header(SecurityConfig.XSRF_HEADER, xsrf.getValue()).contentType(MediaType.APPLICATION_JSON)
					.content(SUBMIT_BODY)).andExpect(status().isUnauthorized());
		}
		verifyNoInteractions(appFlowService);
	}

	@Test
	void 缺CSRF回403() throws Exception {
		Session s = login();
		for (String action : List.of("submit", "recall", "decisions")) {
			mockMvc.perform(post("/api/apps/" + APP + "/" + action).session(s.session())
					.contentType(MediaType.APPLICATION_JSON).content(SUBMIT_BODY)).andExpect(status().isForbidden());
		}
		verifyNoInteractions(appFlowService);
	}

	@Test
	void 送審回200帶新版本號且服務收到登入者() throws Exception {
		Session s = login();
		when(appFlowService.submit(eq(APP), any(), any())).thenReturn(4L);
		postJson(s, "/api/apps/" + APP + "/submit", SUBMIT_BODY).andExpect(status().isOk())
				.andExpect(content().json("{\"appId\":\"" + APP + "\",\"rowVerNo\":4}"));
		ArgumentCaptor<FlowActionRequest> req = ArgumentCaptor.forClass(FlowActionRequest.class);
		ArgumentCaptor<AuthUser> user = ArgumentCaptor.forClass(AuthUser.class);
		verify(appFlowService).submit(eq(APP), req.capture(), user.capture());
		assertThat(req.getValue().rowVerNo()).isEqualTo(3L);
		assertThat(req.getValue().reason()).isNull();
		assertThat(user.getValue().userId()).isEqualTo("E0001");
		assertThat(user.getValue().roles()).containsExactly("idc_admin");
	}

	@Test
	void 撤回回200且原因原樣傳給服務() throws Exception {
		Session s = login();
		when(appFlowService.recall(eq(APP), any(), any())).thenReturn(6L);
		postJson(s, "/api/apps/" + APP + "/recall", RECALL_BODY).andExpect(status().isOk())
				.andExpect(content().json("{\"appId\":\"" + APP + "\",\"rowVerNo\":6}"));
		ArgumentCaptor<FlowActionRequest> req = ArgumentCaptor.forClass(FlowActionRequest.class);
		verify(appFlowService).recall(eq(APP), req.capture(), any());
		assertThat(req.getValue().rowVerNo()).isEqualTo(5L);
		assertThat(req.getValue().reason()).isEqualTo("資料要補");
	}

	@Test
	void 簽核回200且決定與意見原樣傳給服務() throws Exception {
		Session s = login();
		when(appFlowService.decide(eq(APP), any(), any())).thenReturn(8L);
		postJson(s, "/api/apps/" + APP + "/decisions", DECIDE_BODY).andExpect(status().isOk())
				.andExpect(content().json("{\"appId\":\"" + APP + "\",\"rowVerNo\":8}"));
		ArgumentCaptor<DecisionRequest> req = ArgumentCaptor.forClass(DecisionRequest.class);
		ArgumentCaptor<AuthUser> user = ArgumentCaptor.forClass(AuthUser.class);
		verify(appFlowService).decide(eq(APP), req.capture(), user.capture());
		assertThat(req.getValue().rowVerNo()).isEqualTo(7L);
		assertThat(req.getValue().decision()).isEqualTo("APPROVE");
		assertThat(req.getValue().memo()).isEmpty();
		assertThat(user.getValue().userId()).isEqualTo("E0001");
	}

	@Test
	void 服務例外對應狀態碼與訊息() throws Exception {
		Session s = login();
		when(appFlowService.submit(eq("A"), any(), any())).thenThrow(new AccessDeniedException(AppFlowService.MSG_SUBMIT_FORBIDDEN));
		when(appFlowService.submit(eq("B"), any(), any())).thenThrow(new ApiNotFoundException("找不到申請單"));
		when(appFlowService.submit(eq("C"), any(), any())).thenThrow(new ApiConflictException(AppFlowService.MSG_STALE));
		when(appFlowService.submit(eq("D"), any(), any()))
				.thenThrow(new ApiBadRequestException("送審前請先補齊：申請單位、聯絡電話"));
		when(appFlowService.recall(eq("E"), any(), any())).thenThrow(new ApiConflictException(AppFlowService.MSG_RECALL_DECIDED));
		when(appFlowService.decide(eq("F"), any(), any())).thenThrow(new ApiConflictException(AppFlowService.MSG_DECIDE_STALE));
		when(appFlowService.decide(eq("G"), any(), any())).thenThrow(new ApiBadRequestException(DecisionPolicy.MSG_REJECT_NEEDS_MEMO));

		// 服務層 403 的自訂訊息會被 handler 換成通用文字（第 100 項 N1，衝刺期不改），這裡鎖定目前行為
		postJson(s, "/api/apps/A/submit", SUBMIT_BODY).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value(SecurityConfig.MSG_FORBIDDEN));
		postJson(s, "/api/apps/B/submit", SUBMIT_BODY).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("找不到申請單"));
		postJson(s, "/api/apps/C/submit", SUBMIT_BODY).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(AppFlowService.MSG_STALE));
		postJson(s, "/api/apps/D/submit", SUBMIT_BODY).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("送審前請先補齊：申請單位、聯絡電話"));
		postJson(s, "/api/apps/E/recall", RECALL_BODY).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(AppFlowService.MSG_RECALL_DECIDED));
		postJson(s, "/api/apps/F/decisions", DECIDE_BODY).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(AppFlowService.MSG_DECIDE_STALE));
		postJson(s, "/api/apps/G/decisions", DECIDE_BODY).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(DecisionPolicy.MSG_REJECT_NEEDS_MEMO));
	}

	@Test
	void 本文不是JSON回400() throws Exception {
		Session s = login();
		postJson(s, "/api/apps/" + APP + "/decisions", "{not json").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("請求格式錯誤"));
		verifyNoInteractions(appFlowService);
	}
}
