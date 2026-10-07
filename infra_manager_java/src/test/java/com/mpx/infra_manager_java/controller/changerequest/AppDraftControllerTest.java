package com.mpx.infra_manager_java.controller.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：POST /api/apps 的 MockMvc 測試（S6 回合二 b-1）。比照 AppControllerTest 走真的登入流程。
//           鎖定：未登入 401；缺 CSRF 403；登入後 201 {appId, rowVerNo:0} 且服務收到登入者；
//           超長回 400 {message, field, max, actual}；本文不是 JSON 回 400
//           2026-10-07 回合二 b-2：加 PUT /api/apps/{id}——200 {appId, rowVerNo(新)}、缺 CSRF 403、
//           服務丟出的 403／404／409／400 原樣對應狀態碼與訊息
//           2026-10-07 回合三：加 POST /api/apps/{id}/attachments——201 回附件資訊、CSRF 放表單參數不算（只認 header，B5）、
//           不是 multipart 415、缺 file part 400、服務例外對應 409／400
//           2026-10-07 S5 R3：POST 的 query 參數 templateId 原樣轉給服務；沒帶時傳 null
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.multipart.MultipartFile;

import com.mpx.infra_manager_java.config.SecurityConfig;
import com.mpx.infra_manager_java.controller.auth.AuthController;
import com.mpx.infra_manager_java.dao.auth.UserDao;
import com.mpx.infra_manager_java.model.auth.AuthUser;
import com.mpx.infra_manager_java.model.auth.UserRow;
import com.mpx.infra_manager_java.model.changerequest.AppDetail;
import com.mpx.infra_manager_java.model.changerequest.AppDraftRequest;
import com.mpx.infra_manager_java.service.auth.AuthService;
import com.mpx.infra_manager_java.service.changerequest.AppDraftService;
import com.mpx.infra_manager_java.service.changerequest.AttachmentUploadService;
import com.mpx.infra_manager_java.util.TextTooLongException;
import com.mpx.infra_manager_java.web.ApiBadRequestException;
import com.mpx.infra_manager_java.web.ApiConflictException;
import com.mpx.infra_manager_java.web.ApiNotFoundException;

import jakarta.servlet.http.Cookie;

@WebMvcTest(controllers = { AppDraftController.class, AuthController.class })
@Import({ SecurityConfig.class, AuthService.class })
class AppDraftControllerTest {

	private static final String BODY = "{\"title\":\"更換核心交換器\",\"prioCode\":\"P3\"}";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PasswordEncoder encoder;

	@MockitoBean
	private UserDao userDao;

	@MockitoBean
	private AppDraftService appDraftService;

	@MockitoBean
	private AttachmentUploadService attachmentUploadService;

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

	@Test
	void 未登入401() throws Exception {
		MvcResult me = mockMvc.perform(get("/api/auth/me")).andReturn();
		Cookie xsrf = me.getResponse().getCookie(SecurityConfig.XSRF_COOKIE);
		mockMvc.perform(post("/api/apps").cookie(xsrf).header(SecurityConfig.XSRF_HEADER, xsrf.getValue())
				.contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
		verifyNoInteractions(appDraftService);
	}

	@Test
	void 缺CSRF回403() throws Exception {
		Session s = login();
		mockMvc.perform(post("/api/apps").session(s.session()).contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isForbidden());
		verifyNoInteractions(appDraftService);
	}

	@Test
	void 登入後建草稿回201() throws Exception {
		Session s = login();
		when(appDraftService.create(any(), any(), any())).thenReturn("IM20261007-001");
		mockMvc.perform(post("/api/apps").session(s.session()).cookie(s.xsrf())
				.header(SecurityConfig.XSRF_HEADER, s.xsrf().getValue()).contentType(MediaType.APPLICATION_JSON)
				.content(BODY)).andExpect(status().isCreated())
				.andExpect(content().json("{\"appId\":\"IM20261007-001\",\"rowVerNo\":0}"));
		ArgumentCaptor<AppDraftRequest> req = ArgumentCaptor.forClass(AppDraftRequest.class);
		ArgumentCaptor<AuthUser> user = ArgumentCaptor.forClass(AuthUser.class);
		verify(appDraftService).create(req.capture(), user.capture(), isNull());
		assertThat(req.getValue().title()).isEqualTo("更換核心交換器");
		assertThat(user.getValue().userId()).isEqualTo("E0001");
	}

	@Test
	void 帶templateId時轉給服務() throws Exception {
		Session s = login();
		when(appDraftService.create(any(), any(), any())).thenReturn("IM20261007-002");
		mockMvc.perform(post("/api/apps").param("templateId", "tpl_firewall_upgrade").session(s.session())
				.cookie(s.xsrf()).header(SecurityConfig.XSRF_HEADER, s.xsrf().getValue())
				.contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isCreated());
		verify(appDraftService).create(any(), any(), eq("tpl_firewall_upgrade"));
	}

	@Test
	void 超長回400帶欄位資訊() throws Exception {
		Session s = login();
		when(appDraftService.create(any(), any(), any())).thenThrow(new TextTooLongException("title", "標題", 200, 201));
		mockMvc.perform(post("/api/apps").session(s.session()).cookie(s.xsrf())
				.header(SecurityConfig.XSRF_HEADER, s.xsrf().getValue()).contentType(MediaType.APPLICATION_JSON)
				.content(BODY)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("title"))
				.andExpect(jsonPath("$.max").value(200)).andExpect(jsonPath("$.actual").value(201))
				.andExpect(jsonPath("$.message").exists());
	}

	private static final String EDIT_BODY = "{\"title\":\"改標題\",\"prioCode\":\"P2\",\"rowVerNo\":3}";

	private ResultActions putDraft(Session s, String id) throws Exception {
		return mockMvc.perform(put("/api/apps/" + id).session(s.session()).cookie(s.xsrf())
				.header(SecurityConfig.XSRF_HEADER, s.xsrf().getValue()).contentType(MediaType.APPLICATION_JSON)
				.content(EDIT_BODY));
	}

	@Test
	void 編輯草稿回200帶新版本號() throws Exception {
		Session s = login();
		when(appDraftService.update(eq("IM20261007-001"), any(), any())).thenReturn(4L);
		putDraft(s, "IM20261007-001").andExpect(status().isOk())
				.andExpect(content().json("{\"appId\":\"IM20261007-001\",\"rowVerNo\":4}"));
		ArgumentCaptor<AppDraftRequest> req = ArgumentCaptor.forClass(AppDraftRequest.class);
		ArgumentCaptor<AuthUser> user = ArgumentCaptor.forClass(AuthUser.class);
		verify(appDraftService).update(eq("IM20261007-001"), req.capture(), user.capture());
		assertThat(req.getValue().rowVerNo()).isEqualTo(3L);
		assertThat(user.getValue().userId()).isEqualTo("E0001");
	}

	@Test
	void 編輯_缺CSRF回403() throws Exception {
		Session s = login();
		mockMvc.perform(put("/api/apps/IM20261007-001").session(s.session()).contentType(MediaType.APPLICATION_JSON)
				.content(EDIT_BODY)).andExpect(status().isForbidden());
		verifyNoInteractions(appDraftService);
	}

	@Test
	void 編輯_服務例外對應狀態碼() throws Exception {
		Session s = login();
		when(appDraftService.update(eq("A"), any(), any())).thenThrow(new AccessDeniedException("x"));
		when(appDraftService.update(eq("B"), any(), any())).thenThrow(new ApiNotFoundException("找不到申請單"));
		when(appDraftService.update(eq("C"), any(), any()))
				.thenThrow(new ApiConflictException(AppDraftService.MSG_STALE));
		when(appDraftService.update(eq("D"), any(), any()))
				.thenThrow(new ApiBadRequestException(AppDraftService.MSG_NO_VERSION));
		putDraft(s, "A").andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value(SecurityConfig.MSG_FORBIDDEN));
		putDraft(s, "B").andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("找不到申請單"));
		putDraft(s, "C").andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(AppDraftService.MSG_STALE));
		putDraft(s, "D").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(AppDraftService.MSG_NO_VERSION));
	}

	@Test
	void 本文不是JSON回400() throws Exception {
		Session s = login();
		mockMvc.perform(post("/api/apps").session(s.session()).cookie(s.xsrf())
				.header(SecurityConfig.XSRF_HEADER, s.xsrf().getValue()).contentType(MediaType.APPLICATION_JSON)
				.content("{not json")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("請求格式錯誤"));
		verifyNoInteractions(appDraftService);
	}

	private static final String UPLOAD_URL = "/api/apps/IM20261007-001/attachments";

	private static MockMultipartFile part() {
		return new MockMultipartFile("file", "報價單.pdf", "application/pdf", new byte[] { 1, 2, 3 });
	}

	@Test
	void 上傳成功回201帶附件資訊() throws Exception {
		Session s = login();
		when(attachmentUploadService.upload(eq("IM20261007-001"), any(), any())).thenReturn(new AppDetail.Attachment(
				101L, "APP", "IM20261007-001", "報價單.pdf", 3L, "application/pdf", "2026-10-07 10:20"));
		mockMvc.perform(multipart(UPLOAD_URL).file(part()).session(s.session()).cookie(s.xsrf())
				.header(SecurityConfig.XSRF_HEADER, s.xsrf().getValue())).andExpect(status().isCreated())
				.andExpect(content().json("{\"attachId\":101,\"ownerType\":\"APP\",\"fileName\":\"報價單.pdf\","
						+ "\"byteQty\":3,\"mimeType\":\"application/pdf\",\"uploadedAt\":\"2026-10-07 10:20\"}"));
		ArgumentCaptor<MultipartFile> file = ArgumentCaptor.forClass(MultipartFile.class);
		ArgumentCaptor<AuthUser> user = ArgumentCaptor.forClass(AuthUser.class);
		verify(attachmentUploadService).upload(eq("IM20261007-001"), file.capture(), user.capture());
		assertThat(file.getValue().getOriginalFilename()).isEqualTo("報價單.pdf");
		assertThat(user.getValue().userId()).isEqualTo("E0001");
	}

	@Test
	void 上傳_CSRF只認header_放在表單參數也回403() throws Exception {
		Session s = login();
		mockMvc.perform(multipart(UPLOAD_URL).file(part()).session(s.session()).cookie(s.xsrf())
				.param("_csrf", s.xsrf().getValue())).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value(SecurityConfig.MSG_CSRF));
		verifyNoInteractions(attachmentUploadService);
	}

	@Test
	void 上傳_不是multipart回415_缺part回400() throws Exception {
		Session s = login();
		mockMvc.perform(post(UPLOAD_URL).session(s.session()).cookie(s.xsrf())
				.header(SecurityConfig.XSRF_HEADER, s.xsrf().getValue()).contentType(MediaType.APPLICATION_JSON)
				.content("{}")).andExpect(status().isUnsupportedMediaType());
		mockMvc.perform(multipart(UPLOAD_URL).file(new MockMultipartFile("other", "a.pdf", null, new byte[] { 1 }))
				.session(s.session()).cookie(s.xsrf()).header(SecurityConfig.XSRF_HEADER, s.xsrf().getValue()))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(attachmentUploadService);
	}

	@Test
	void 上傳_服務例外對應狀態碼() throws Exception {
		Session s = login();
		when(attachmentUploadService.upload(any(), any(), any()))
				.thenThrow(new ApiConflictException(AttachmentUploadService.MSG_NOT_DRAFT))
				.thenThrow(new ApiBadRequestException(AttachmentUploadService.MSG_TYPE_NOT_ALLOWED));
		mockMvc.perform(multipart(UPLOAD_URL).file(part()).session(s.session()).cookie(s.xsrf())
				.header(SecurityConfig.XSRF_HEADER, s.xsrf().getValue())).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(AttachmentUploadService.MSG_NOT_DRAFT));
		mockMvc.perform(multipart(UPLOAD_URL).file(part()).session(s.session()).cookie(s.xsrf())
				.header(SecurityConfig.XSRF_HEADER, s.xsrf().getValue())).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(AttachmentUploadService.MSG_TYPE_NOT_ALLOWED));
	}
}
