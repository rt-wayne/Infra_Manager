package com.mpx.infra_manager_java.controller.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：PUT /api/apps/{id}/execution 的 MockMvc 測試（S10 R1）。比照 AppFlowControllerTest 走真的登入流程。
//           鎖定：未登入 401；缺 CSRF 403；成功回 200 {appId, rowVerNo, statusCode} 且本文（含巢狀檢核項）與登入者原樣傳給服務；
//           服務丟出的 400／403／404／409 對應狀態碼；沒帶本文或不是 JSON 400。
//           2026-10-07 S10 R2：加 POST execution/reject 與 governance-review 的 401／CSRF 403／200 本文傳遞與例外對應
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.mpx.infra_manager_java.model.changerequest.ExecRejectRequest;
import com.mpx.infra_manager_java.model.changerequest.ExecutionRequest;
import com.mpx.infra_manager_java.model.changerequest.GovernanceReviewRequest;
import com.mpx.infra_manager_java.service.auth.AuthService;
import com.mpx.infra_manager_java.service.changerequest.AppExecutionService;
import com.mpx.infra_manager_java.service.changerequest.AppFlowService;
import com.mpx.infra_manager_java.service.changerequest.ExecutionValidator;
import com.mpx.infra_manager_java.web.ApiBadRequestException;
import com.mpx.infra_manager_java.web.ApiConflictException;
import com.mpx.infra_manager_java.web.ApiNotFoundException;

import jakarta.servlet.http.Cookie;

@WebMvcTest(controllers = { AppExecutionController.class, AuthController.class })
@Import({ SecurityConfig.class, AuthService.class })
class AppExecutionControllerTest {

	private static final String APP = "IM20261007-001";
	private static final String BODY = "{\"rowVerNo\":3,\"checklist\":[{\"seqNo\":1,\"done\":true,\"doneAt\":null,"
			+ "\"userId\":\"E0001\"},{\"seqNo\":5,\"done\":false,\"executorDesc\":\"廠商\"}],"
			+ "\"actualStart\":\"2026-10-07 09:00\",\"actualEnd\":\"2026-10-07T10:00\",\"resultCode\":\"DONE\","
			+ "\"exception\":true,\"exceptionDesc\":\"UPS 告警\",\"followUp\":false,\"memo\":\"完成\"}";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PasswordEncoder encoder;

	@MockitoBean
	private UserDao userDao;

	@MockitoBean
	private AppExecutionService appExecutionService;

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

	/** 已登入、帶 CSRF 的 PUT */
	private ResultActions putJson(Session s, String path, String body) throws Exception {
		return mockMvc.perform(put(path).session(s.session()).cookie(s.xsrf())
				.header(SecurityConfig.XSRF_HEADER, s.xsrf().getValue()).contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	@Test
	void 未登入401_缺CSRF403() throws Exception {
		MvcResult me = mockMvc.perform(get("/api/auth/me")).andReturn();
		Cookie xsrf = me.getResponse().getCookie(SecurityConfig.XSRF_COOKIE);
		mockMvc.perform(put("/api/apps/" + APP + "/execution").cookie(xsrf)
				.header(SecurityConfig.XSRF_HEADER, xsrf.getValue()).contentType(MediaType.APPLICATION_JSON)
				.content(BODY)).andExpect(status().isUnauthorized());

		Session s = login();
		mockMvc.perform(put("/api/apps/" + APP + "/execution").session(s.session())
				.contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
		verifyNoInteractions(appExecutionService);
	}

	@Test
	void 成功回200_本文含巢狀檢核項原樣傳給服務() throws Exception {
		Session s = login();
		when(appExecutionService.save(eq(APP), any(), any()))
				.thenReturn(new AppExecutionService.SaveResult(4L, "PENDING_REVIEW"));
		putJson(s, "/api/apps/" + APP + "/execution", BODY).andExpect(status().isOk())
				.andExpect(content().json("{\"appId\":\"" + APP + "\",\"rowVerNo\":4,\"statusCode\":\"PENDING_REVIEW\"}"));

		ArgumentCaptor<ExecutionRequest> req = ArgumentCaptor.forClass(ExecutionRequest.class);
		ArgumentCaptor<AuthUser> user = ArgumentCaptor.forClass(AuthUser.class);
		verify(appExecutionService).save(eq(APP), req.capture(), user.capture());
		ExecutionRequest r = req.getValue();
		assertThat(r.rowVerNo()).isEqualTo(3L);
		assertThat(r.checklist()).containsExactly(new ExecutionRequest.CheckItem(1, true, null, "E0001", null),
				new ExecutionRequest.CheckItem(5, false, null, null, "廠商"));
		assertThat(r.actualStart()).isEqualTo("2026-10-07 09:00");
		assertThat(r.actualEnd()).isEqualTo("2026-10-07T10:00");
		assertThat(r.resultCode()).isEqualTo("DONE");
		assertThat(r.exception()).isTrue();
		assertThat(r.exceptionDesc()).isEqualTo("UPS 告警");
		assertThat(r.followUp()).isFalse();
		assertThat(r.followUpDesc()).isNull();
		assertThat(r.memo()).isEqualTo("完成");
		assertThat(user.getValue().userId()).isEqualTo("E0001");
		assertThat(user.getValue().roles()).containsExactly("idc_admin");
	}

	@Test
	void 服務例外對應狀態碼_缺本文或非JSON400() throws Exception {
		Session s = login();
		when(appExecutionService.save(eq("A"), any(), any()))
				.thenThrow(new AccessDeniedException(AppExecutionService.MSG_EXEC_FORBIDDEN));
		when(appExecutionService.save(eq("B"), any(), any())).thenThrow(new ApiNotFoundException("找不到申請單"));
		when(appExecutionService.save(eq("C"), any(), any()))
				.thenThrow(new ApiConflictException(AppExecutionService.MSG_EXEC_NOT_EXECUTABLE));
		when(appExecutionService.save(eq("D"), any(), any()))
				.thenThrow(new ApiBadRequestException(ExecutionValidator.MSG_SUBMIT_PREFIX + "實際結束時間"));
		when(appExecutionService.save(eq("E"), any(), any())).thenThrow(new ApiConflictException(AppFlowService.MSG_STALE));

		putJson(s, "/api/apps/A/execution", BODY).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value(SecurityConfig.MSG_FORBIDDEN));
		putJson(s, "/api/apps/B/execution", BODY).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("找不到申請單"));
		putJson(s, "/api/apps/C/execution", BODY).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(AppExecutionService.MSG_EXEC_NOT_EXECUTABLE));
		putJson(s, "/api/apps/D/execution", BODY).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(ExecutionValidator.MSG_SUBMIT_PREFIX + "實際結束時間"));
		putJson(s, "/api/apps/E/execution", BODY).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(AppFlowService.MSG_STALE));

		mockMvc.perform(put("/api/apps/Z/execution").session(s.session()).cookie(s.xsrf())
				.header(SecurityConfig.XSRF_HEADER, s.xsrf().getValue())).andExpect(status().isBadRequest());
		putJson(s, "/api/apps/Z/execution", "{not json").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("請求格式錯誤"));
		verify(appExecutionService, org.mockito.Mockito.never()).save(eq("Z"), any(), any());
	}

	// ---- 執行端退回與治理審查（S10 R2） ----

	private ResultActions postJson(Session s, String path, String body) throws Exception {
		return mockMvc.perform(post(path).session(s.session()).cookie(s.xsrf())
				.header(SecurityConfig.XSRF_HEADER, s.xsrf().getValue()).contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	@Test
	void 退回與審查_未登入401_缺CSRF403() throws Exception {
		MvcResult me = mockMvc.perform(get("/api/auth/me")).andReturn();
		Cookie xsrf = me.getResponse().getCookie(SecurityConfig.XSRF_COOKIE);
		for (String path : new String[] { "/execution/reject", "/governance-review" }) {
			mockMvc.perform(post("/api/apps/" + APP + path).cookie(xsrf).header(SecurityConfig.XSRF_HEADER, xsrf.getValue())
					.contentType(MediaType.APPLICATION_JSON).content("{\"rowVerNo\":3}")).andExpect(status().isUnauthorized());
		}
		Session s = login();
		for (String path : new String[] { "/execution/reject", "/governance-review" }) {
			mockMvc.perform(post("/api/apps/" + APP + path).session(s.session()).contentType(MediaType.APPLICATION_JSON)
					.content("{\"rowVerNo\":3}")).andExpect(status().isForbidden());
		}
		verifyNoInteractions(appExecutionService);
	}

	@Test
	void 退回成功回200_本文傳給服務() throws Exception {
		Session s = login();
		when(appExecutionService.reject(eq(APP), any(), any())).thenReturn(4L);
		postJson(s, "/api/apps/" + APP + "/execution/reject", "{\"rowVerNo\":3,\"memo\":\"設備未到\"}")
				.andExpect(status().isOk()).andExpect(content().json("{\"appId\":\"" + APP + "\",\"rowVerNo\":4}"));
		ArgumentCaptor<ExecRejectRequest> req = ArgumentCaptor.forClass(ExecRejectRequest.class);
		verify(appExecutionService).reject(eq(APP), req.capture(), any());
		assertThat(req.getValue()).isEqualTo(new ExecRejectRequest(3L, "設備未到"));

		when(appExecutionService.reject(eq("C"), any(), any()))
				.thenThrow(new ApiConflictException(AppExecutionService.MSG_REJECT_NOT_EXECUTABLE));
		postJson(s, "/api/apps/C/execution/reject", "{\"rowVerNo\":3,\"memo\":\"x\"}").andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(AppExecutionService.MSG_REJECT_NOT_EXECUTABLE));
	}

	@Test
	void 審查成功回200含狀態_例外對應狀態碼() throws Exception {
		Session s = login();
		when(appExecutionService.review(eq(APP), any(), any()))
				.thenReturn(new AppExecutionService.SaveResult(4L, "EXECUTED"));
		postJson(s, "/api/apps/" + APP + "/governance-review", "{\"rowVerNo\":3,\"decision\":\"PASS\",\"memo\":null}")
				.andExpect(status().isOk())
				.andExpect(content().json("{\"appId\":\"" + APP + "\",\"rowVerNo\":4,\"statusCode\":\"EXECUTED\"}"));
		ArgumentCaptor<GovernanceReviewRequest> req = ArgumentCaptor.forClass(GovernanceReviewRequest.class);
		verify(appExecutionService).review(eq(APP), req.capture(), any());
		assertThat(req.getValue()).isEqualTo(new GovernanceReviewRequest(3L, "PASS", null));

		when(appExecutionService.review(eq("A"), any(), any()))
				.thenThrow(new AccessDeniedException(AppExecutionService.MSG_REVIEW_FORBIDDEN));
		when(appExecutionService.review(eq("D"), any(), any()))
				.thenThrow(new ApiBadRequestException(AppExecutionService.MSG_REJECT_NEEDS_MEMO));
		when(appExecutionService.review(eq("C"), any(), any()))
				.thenThrow(new ApiConflictException(AppExecutionService.MSG_REVIEW_NOT_PENDING));
		String body = "{\"rowVerNo\":3,\"decision\":\"RETURN\"}";
		postJson(s, "/api/apps/A/governance-review", body).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value(SecurityConfig.MSG_FORBIDDEN));
		postJson(s, "/api/apps/D/governance-review", body).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(AppExecutionService.MSG_REJECT_NEEDS_MEMO));
		postJson(s, "/api/apps/C/governance-review", body).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(AppExecutionService.MSG_REVIEW_NOT_PENDING));
	}
}
