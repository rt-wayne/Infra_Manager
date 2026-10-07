package com.mpx.infra_manager_java.service.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：附件下載服務的單元測試（S4，用 @TempDir 當附件根目錄）。鎖定：附件不屬於該單 404「找不到附件」、
//           FILE_PATH 逸出根目錄 404（不洩漏存在與否）、索引有但檔案不在 404「附件檔案不存在」、根目錄未設定 500 類例外、
//           正常情況回原始檔名／MIME／長度（長度以 DB 為準、MIME 空白退回 octet-stream）
//           2026-10-07 S6 回合一-2：長度改以實體檔為準（DB 值不同仍以實際為準）、MIME 改由副檔名決定（不照 DB、白名單外 octet-stream）
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mpx.infra_manager_java.dao.changerequest.AttachDao;
import com.mpx.infra_manager_java.model.changerequest.AppRow;
import com.mpx.infra_manager_java.model.changerequest.AttachRow;
import com.mpx.infra_manager_java.model.changerequest.AttachmentFile;
import com.mpx.infra_manager_java.web.ApiNotFoundException;

class AttachmentServiceTest {

	private static final String APP = "IM20261006-907";

	@TempDir
	Path root;

	private final AppQueryService appQueryService = mock(AppQueryService.class);
	private final AttachDao attachDao = mock(AttachDao.class);

	@BeforeEach
	void setUp() {
		when(appQueryService.findApp(APP)).thenReturn(new AppRow());
	}

	private static AttachRow row(long id, String filePath, Long bytes, String mime, String name) {
		AttachRow r = new AttachRow();
		r.setAttachId(id);
		r.setOwnerType("APP");
		r.setOwnerId(APP);
		r.setFilePath(filePath);
		r.setFileByteQty(bytes);
		r.setMimeType(mime);
		r.setOrigFileName(name);
		return r;
	}

	private AttachmentService service() {
		return new AttachmentService(appQueryService, attachDao, root.toString());
	}

	@Test
	void 正常下載回原始檔名_副檔名決定的MIME_實際檔案長度() throws IOException {
		Path file = root.resolve("s4/IM20261006-907/a1.pdf");
		Files.createDirectories(file.getParent());
		Files.writeString(file, "hello", StandardCharsets.UTF_8);
		when(attachDao.findByApp(APP)).thenReturn(List.of(row(1L, "s4/IM20261006-907/a1.pdf", 5L, "application/pdf",
				"報價單.pdf")));

		AttachmentFile f = service().open(APP, 1L);

		assertThat(f.fileName()).isEqualTo("報價單.pdf");
		assertThat(f.mimeType()).isEqualTo("application/pdf");
		assertThat(f.length()).isEqualTo(5L);
		assertThat(f.resource().getContentAsString(StandardCharsets.UTF_8)).isEqualTo("hello");
	}

	@Test
	void DB記錄的長度與實際不同時以實際檔案為準() throws IOException {
		Files.writeString(root.resolve("b.pdf"), "hello", StandardCharsets.UTF_8);
		when(attachDao.findByApp(APP)).thenReturn(List.of(row(6L, "b.pdf", 999L, "application/pdf", "b.pdf")));

		assertThat(service().open(APP, 6L).length()).isEqualTo(5L);
	}

	@Test
	void MIME不照DB值_白名單外副檔名回octet_stream() throws IOException {
		Files.writeString(root.resolve("x.html"), "<script>", StandardCharsets.UTF_8);
		Files.writeString(root.resolve("Y.PNG"), "png", StandardCharsets.UTF_8);
		when(attachDao.findByApp(APP)).thenReturn(List.of(row(7L, "x.html", 8L, "text/html", "x.html"),
				row(8L, "Y.PNG", 3L, "text/html", "y.png")));

		assertThat(service().open(APP, 7L).mimeType()).isEqualTo("application/octet-stream");
		assertThat(service().open(APP, 8L).mimeType()).isEqualTo("image/png");
	}

	@Test
	void DB沒記長度與MIME時退回實際檔案長度與octet_stream() throws IOException {
		Path file = root.resolve("x.bin");
		Files.write(file, new byte[] { 1, 2, 3 });
		when(attachDao.findByApp(APP)).thenReturn(List.of(row(2L, "x.bin", null, " ", null)));

		AttachmentFile f = service().open(APP, 2L);

		assertThat(f.length()).isEqualTo(3L);
		assertThat(f.mimeType()).isEqualTo("application/octet-stream");
		assertThat(f.fileName()).isEqualTo("attachment");
	}

	@Test
	void 附件不屬於該申請單回404() {
		when(attachDao.findByApp(APP)).thenReturn(List.of(row(1L, "a.pdf", 1L, "application/pdf", "a.pdf")));

		assertThatThrownBy(() -> service().open(APP, 2L)).isInstanceOf(ApiNotFoundException.class)
				.hasMessage(AttachmentService.MSG_ATTACH_NOT_FOUND);
	}

	@Test
	void 申請單不存在時由findApp丟404_不查附件() {
		when(appQueryService.findApp("IM20261006-000"))
				.thenThrow(new ApiNotFoundException(AppQueryService.MSG_APP_NOT_FOUND));

		assertThatThrownBy(() -> service().open("IM20261006-000", 1L)).isInstanceOf(ApiNotFoundException.class)
				.hasMessage(AppQueryService.MSG_APP_NOT_FOUND);
	}

	@Test
	void 路徑逸出根目錄回404找不到附件() throws IOException {
		Path outside = root.resolveSibling(root.getFileName() + "-outside.txt");
		Files.writeString(outside, "secret", StandardCharsets.UTF_8);
		try {
			when(attachDao.findByApp(APP))
					.thenReturn(List.of(row(3L, "../" + outside.getFileName(), 6L, "text/plain", "x.txt")));

			assertThatThrownBy(() -> service().open(APP, 3L)).isInstanceOf(ApiNotFoundException.class)
					.hasMessage(AttachmentService.MSG_ATTACH_NOT_FOUND);
		} finally {
			Files.deleteIfExists(outside);
		}
	}

	@Test
	void 索引存在但檔案不在回404附件檔案不存在() {
		when(attachDao.findByApp(APP)).thenReturn(List.of(row(4L, "missing/none.pdf", 1L, "application/pdf", "n.pdf")));

		assertThatThrownBy(() -> service().open(APP, 4L)).isInstanceOf(ApiNotFoundException.class)
				.hasMessage(AttachmentService.MSG_FILE_MISSING);
	}

	@Test
	void 根目錄未設定是部署錯誤_丟IllegalState不是404() {
		when(attachDao.findByApp(APP)).thenReturn(List.of(row(5L, "a.pdf", 1L, "application/pdf", "a.pdf")));
		AttachmentService blank = new AttachmentService(appQueryService, attachDao, " ");

		assertThatThrownBy(() -> blank.open(APP, 5L)).isInstanceOf(IllegalStateException.class);
	}
}
