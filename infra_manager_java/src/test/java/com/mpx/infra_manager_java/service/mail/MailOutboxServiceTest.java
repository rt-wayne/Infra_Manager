package com.mpx.infra_manager_java.service.mail;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-08
// 變更說明: 新增：enqueue 的單元測試（S8 R1）。鎖定：
//           收件人去空白、不分大小寫去重、格式不對的略過、cc 不重複 to；主旨 CR／LF 換空白、截 500 字、空主旨補預設；
//           沒有有效收件人時不寫入、回 false；override-to 有值時全部改寄、主旨加前綴、原收件人進 META_JSON；
//           cc／meta 空時存 null；CREATE_BY 空時用 SYSTEM；DAO 例外原樣往外拋；內容空時 400 類例外且不寫入
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import com.mpx.infra_manager_java.config.MailProperties;
import com.mpx.infra_manager_java.dao.mail.MailOutboxDao;
import com.mpx.infra_manager_java.model.mail.MailMessage;

class MailOutboxServiceTest {

	private final MailOutboxDao dao = mock(MailOutboxDao.class);
	private final MailProperties properties = new MailProperties();
	private final MailOutboxService service = new MailOutboxService(dao, properties);

	private static MailMessage msg(List<String> to, List<String> cc, String subject) {
		return new MailMessage(to, cc, List.of(), subject, "<p>內容</p>", Map.of("event", "step-pending", "appId", "IM2026100001"),
				"T0001");
	}

	@Test
	void 收件人去空白去重且略過格式不對的() {
		List<String> to = Arrays.asList(" a@pxmart.com.tw ", "A@PXMART.COM.TW", "", null, "not-an-email", "b@pxmart.com.tw");
		List<String> cc = List.of("b@pxmart.com.tw", "c@pxmart.com.tw");

		boolean ok = service.enqueue(msg(to, cc, "主旨"));

		assertThat(ok).isTrue();
		ArgumentCaptor<String> toJson = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> ccJson = ArgumentCaptor.forClass(String.class);
		verify(dao).insert(toJson.capture(), ccJson.capture(), any(), anyString(), anyString(), any(), anyString());
		assertThat(toJson.getValue()).isEqualTo("[\"a@pxmart.com.tw\",\"b@pxmart.com.tw\"]");
		assertThat(ccJson.getValue()).isEqualTo("[\"c@pxmart.com.tw\"]");
	}

	@Test
	void 主旨去換行並截到五百字() {
		String longSubject = "x".repeat(600);
		service.enqueue(msg(List.of("a@pxmart.com.tw"), List.of(), "  第一行\r\nBcc: evil@x.com\t第二行  "));
		service.enqueue(msg(List.of("a@pxmart.com.tw"), List.of(), longSubject));
		service.enqueue(msg(List.of("a@pxmart.com.tw"), List.of(), "   "));

		ArgumentCaptor<String> subj = ArgumentCaptor.forClass(String.class);
		verify(dao, times(3)).insert(anyString(), any(), any(), subj.capture(), anyString(), any(),
				anyString());
		assertThat(subj.getAllValues().get(0)).isEqualTo("第一行 Bcc: evil@x.com 第二行");
		assertThat(subj.getAllValues().get(1)).hasSize(500);
		assertThat(subj.getAllValues().get(2)).isEqualTo(MailOutboxService.EMPTY_SUBJECT);
	}

	@Test
	void 沒有有效收件人時不寫入() {
		boolean ok = service.enqueue(msg(Arrays.asList("", "bad", null), List.of(), "主旨"));

		assertThat(ok).isFalse();
		verifyNoInteractions(dao);
	}

	@Test
	void override_to有值時全部改寄並保留原收件人到meta() {
		properties.setOverrideTo("ciliao@pxmart.com.tw");

		service.enqueue(msg(List.of("a@pxmart.com.tw"), List.of("c@pxmart.com.tw"), "主旨"));

		ArgumentCaptor<String> toJson = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> ccJson = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> subj = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> meta = ArgumentCaptor.forClass(String.class);
		verify(dao).insert(toJson.capture(), ccJson.capture(), any(), subj.capture(), anyString(), meta.capture(),
				anyString());
		assertThat(toJson.getValue()).isEqualTo("[\"ciliao@pxmart.com.tw\"]");
		assertThat(ccJson.getValue()).isNull();
		assertThat(subj.getValue()).isEqualTo(MailOutboxService.OVERRIDE_PREFIX + "主旨");
		assertThat(meta.getValue()).contains("\"originalTo\":[\"a@pxmart.com.tw\"]")
				.contains("\"originalCc\":[\"c@pxmart.com.tw\"]").contains("\"event\":\"step-pending\"");
	}

	@Test
	void cc與meta空時存null且建立者空時用SYSTEM() {
		service.enqueue(new MailMessage(List.of("a@pxmart.com.tw"), null, null, "主旨", "<p>x</p>", null, " "));

		ArgumentCaptor<String> ccJson = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> meta = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> by = ArgumentCaptor.forClass(String.class);
		verify(dao).insert(anyString(), ccJson.capture(), any(), anyString(), anyString(), meta.capture(), by.capture());
		assertThat(ccJson.getValue()).isNull();
		assertThat(meta.getValue()).isNull();
		assertThat(by.getValue()).isEqualTo(MailOutboxDao.SYSTEM_USER);
	}

	@Test
	void DAO例外原樣往外拋() {
		doThrow(new DataIntegrityViolationException("ORA-02290")).when(dao).insert(anyString(), any(), any(), anyString(),
				anyString(), any(), anyString());

		assertThatThrownBy(() -> service.enqueue(msg(List.of("a@pxmart.com.tw"), List.of(), "主旨")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 內容空時拒絕且不寫入() {
		assertThatThrownBy(() -> service.enqueue(
				new MailMessage(List.of("a@pxmart.com.tw"), List.of(), List.of(), "主旨", " ", null, "T0001")))
				.isInstanceOf(IllegalArgumentException.class);
		verifyNoInteractions(dao);
	}
}
