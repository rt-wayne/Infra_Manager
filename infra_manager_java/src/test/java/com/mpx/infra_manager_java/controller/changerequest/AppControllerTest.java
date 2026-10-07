package com.mpx.infra_manager_java.controller.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：申請單唯讀 API 的 MockMvc 測試（S4）。@WebMvcTest 同時載入 AuthController（測試要走真的登入流程
//           取得 session，切片裡沒有它 /api/auth/login 會 404）、SecurityConfig 與真的 AuthService，
//           AppQueryService／AttachmentService／UserDao 以 @MockitoBean 取代。
//           鎖定：三個端點未登入 401；列表參數（status／mine／from／to／page）正確轉成 AppListQuery 且回 JSON；
//           日期格式錯誤 400「請求格式錯誤」；服務丟 400／404 時帶原訊息；下載回 Content-Disposition（UTF-8 檔名）、
//           Content-Type、Content-Length、nosniff 與位元組內容；attachId 非數字 400
//           2026-10-07 S6 回合一-2（Claude Opus 5.5）：下載端點的 HEAD（前端下載前先確認）：存在 200 帶 Content-Length、不存在 404、未登入 401
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.mpx.infra_manager_java.config.SecurityConfig;
import com.mpx.infra_manager_java.controller.auth.AuthController;
import com.mpx.infra_manager_java.dao.auth.UserDao;
import com.mpx.infra_manager_java.model.auth.AuthUser;
import com.mpx.infra_manager_java.model.auth.UserRow;
import com.mpx.infra_manager_java.model.changerequest.AppListItem;
import com.mpx.infra_manager_java.model.changerequest.AppListQuery;
import com.mpx.infra_manager_java.model.changerequest.AppListResponse;
import com.mpx.infra_manager_java.model.changerequest.AttachmentFile;
import com.mpx.infra_manager_java.service.auth.AuthService;
import com.mpx.infra_manager_java.service.changerequest.AppQueryService;
import com.mpx.infra_manager_java.service.changerequest.AttachmentService;
import com.mpx.infra_manager_java.web.ApiBadRequestException;
import com.mpx.infra_manager_java.web.ApiNotFoundException;

import jakarta.servlet.http.Cookie;

@WebMvcTest(controllers = { AppController.class, AuthController.class })
@Import({ SecurityConfig.class, AuthService.class })
class AppControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PasswordEncoder encoder;

	@MockitoBean
	private UserDao userDao;

	@MockitoBean
	private AppQueryService appQueryService;

	@MockitoBean
	private AttachmentService attachmentService;

	private MockHttpSession login() throws Exception {
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
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private static AppListResponse sampleList() {
		AppListItem item = new AppListItem("IM20261006-902", "儲存設備擴充硬碟", "P3", "P3 一般", "#2563eb",
				"SAN-01 新增硬碟", "資訊處 Infra 組", "王申請", 1, "IN_REVIEW", "ONLINE", "Infra 主管", "2 人待簽", true,
				"2026-10-04 09:30");
		return new AppListResponse(List.of(item), 1, 1, AppListQuery.PAGE_SIZE, 1);
	}

	@Test
	void 三個端點未登入都401() throws Exception {
		String body = "{\"message\":\"" + SecurityConfig.MSG_NOT_LOGGED_IN + "\"}";
		mockMvc.perform(get("/api/apps")).andExpect(status().isUnauthorized()).andExpect(content().json(body));
		mockMvc.perform(get("/api/apps/IM20261006-902")).andExpect(status().isUnauthorized())
				.andExpect(content().json(body));
		mockMvc.perform(get("/api/apps/IM20261006-902/attachments/1")).andExpect(status().isUnauthorized())
				.andExpect(content().json(body));
	}

	@Test
	void 列表參數轉成查詢條件並回JSON() throws Exception {
		MockHttpSession session = login();
		ArgumentCaptor<AppListQuery> captor = ArgumentCaptor.forClass(AppListQuery.class);
		when(appQueryService.list(captor.capture(), any(AuthUser.class))).thenReturn(sampleList());

		mockMvc.perform(get("/api/apps").session(session).param("status", "IN_REVIEW").param("mine", "true")
				.param("q", " 交換器 ").param("from", "2026-09-01").param("to", "2026-10-06").param("page", "2"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.total").value(1))
				.andExpect(jsonPath("$.size").value(20))
				.andExpect(jsonPath("$.mineCount").value(1))
				.andExpect(jsonPath("$.items[0].appId").value("IM20261006-902"))
				.andExpect(jsonPath("$.items[0].currentApprover").value("2 人待簽"))
				.andExpect(jsonPath("$.items[0].mine").value(true))
				.andExpect(jsonPath("$.items[0].createdAt").value("2026-10-04 09:30"));

		AppListQuery q = captor.getValue();
		assertThat(q.status()).isEqualTo("IN_REVIEW");
		assertThat(q.mine()).isTrue();
		assertThat(q.q()).isEqualTo(" 交換器 ");
		assertThat(q.from()).isEqualTo(LocalDate.of(2026, 9, 1));
		assertThat(q.to()).isEqualTo(LocalDate.of(2026, 10, 6));
		assertThat(q.page()).isEqualTo(2);
	}

	@Test
	void 列表不帶參數時mine為false_page為1() throws Exception {
		MockHttpSession session = login();
		ArgumentCaptor<AppListQuery> captor = ArgumentCaptor.forClass(AppListQuery.class);
		when(appQueryService.list(captor.capture(), any(AuthUser.class))).thenReturn(sampleList());

		mockMvc.perform(get("/api/apps").session(session)).andExpect(status().isOk());

		AppListQuery q = captor.getValue();
		assertThat(q.mine()).isFalse();
		assertThat(q.page()).isEqualTo(1);
		assertThat(q.status()).isNull();
		assertThat(q.from()).isNull();
	}

	@Test
	void 日期格式錯誤或page非數字回400請求格式錯誤() throws Exception {
		MockHttpSession session = login();

		mockMvc.perform(get("/api/apps").session(session).param("from", "2026/09/01"))
				.andExpect(status().isBadRequest())
				.andExpect(content().json("{\"message\":\"請求格式錯誤\"}"));
		mockMvc.perform(get("/api/apps").session(session).param("page", "abc"))
				.andExpect(status().isBadRequest())
				.andExpect(content().json("{\"message\":\"請求格式錯誤\"}"));
	}

	@Test
	void 服務丟400帶原訊息() throws Exception {
		MockHttpSession session = login();
		when(appQueryService.list(any(), any())).thenThrow(new ApiBadRequestException(AppQueryService.MSG_BAD_STATUS));

		mockMvc.perform(get("/api/apps").session(session).param("status", "X"))
				.andExpect(status().isBadRequest())
				.andExpect(content().json("{\"message\":\"" + AppQueryService.MSG_BAD_STATUS + "\"}"));
	}

	@Test
	void 檢視查無回404帶訊息() throws Exception {
		MockHttpSession session = login();
		when(appQueryService.detail(eq("IM20261006-999"), any(AuthUser.class)))
				.thenThrow(new ApiNotFoundException(AppQueryService.MSG_APP_NOT_FOUND));

		mockMvc.perform(get("/api/apps/IM20261006-999").session(session))
				.andExpect(status().isNotFound())
				.andExpect(content().json("{\"message\":\"" + AppQueryService.MSG_APP_NOT_FOUND + "\"}"));
	}

	@Test
	void 下載回檔名MIME長度nosniff與內容() throws Exception {
		MockHttpSession session = login();
		byte[] bytes = "%PDF-1.4 sample".getBytes(StandardCharsets.UTF_8);
		when(attachmentService.open("IM20261006-907", 5L))
				.thenReturn(new AttachmentFile(new ByteArrayResource(bytes), "故障 PSU 照片.pdf", "application/pdf",
						bytes.length));

		MvcResult result = mockMvc.perform(get("/api/apps/IM20261006-907/attachments/5").session(session))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
				.andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, bytes.length))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andReturn();

		String disposition = result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION);
		assertThat(disposition).startsWith("attachment").contains("filename*=UTF-8''").contains("PSU");
		assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(bytes);
	}

	@Test
	void 下載_MIME壞掉退回octet_stream_檔案不在404() throws Exception {
		MockHttpSession session = login();
		byte[] bytes = { 1, 2 };
		when(attachmentService.open("IM20261006-907", 6L))
				.thenReturn(new AttachmentFile(new ByteArrayResource(bytes), "x.bin", "not a mime", bytes.length));
		when(attachmentService.open("IM20261006-907", 7L))
				.thenThrow(new ApiNotFoundException(AttachmentService.MSG_FILE_MISSING));

		mockMvc.perform(get("/api/apps/IM20261006-907/attachments/6").session(session))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE));
		mockMvc.perform(get("/api/apps/IM20261006-907/attachments/7").session(session))
				.andExpect(status().isNotFound())
				.andExpect(content().json("{\"message\":\"" + AttachmentService.MSG_FILE_MISSING + "\"}"));
	}

	@Test
	void 下載的HEAD_存在回200帶長度_不存在回404_未登入401() throws Exception {
		MockHttpSession session = login();
		byte[] bytes = { 1, 2, 3 };
		when(attachmentService.open("IM20261006-907", 8L))
				.thenReturn(new AttachmentFile(new ByteArrayResource(bytes), "a.pdf", "application/pdf", bytes.length));
		when(attachmentService.open("IM20261006-907", 9L))
				.thenThrow(new ApiNotFoundException(AttachmentService.MSG_FILE_MISSING));

		// HEAD 的本文由 Tomcat 丟棄，MockMvc 不模擬這段，所以只驗狀態碼與長度
		mockMvc.perform(head("/api/apps/IM20261006-907/attachments/8").session(session))
				.andExpect(status().isOk())
				.andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, bytes.length));
		mockMvc.perform(head("/api/apps/IM20261006-907/attachments/9").session(session))
				.andExpect(status().isNotFound());
		mockMvc.perform(head("/api/apps/IM20261006-907/attachments/8"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void attachId非數字回400() throws Exception {
		MockHttpSession session = login();

		mockMvc.perform(get("/api/apps/IM20261006-907/attachments/abc").session(session))
				.andExpect(status().isBadRequest())
				.andExpect(content().json("{\"message\":\"請求格式錯誤\"}"));
	}
}
