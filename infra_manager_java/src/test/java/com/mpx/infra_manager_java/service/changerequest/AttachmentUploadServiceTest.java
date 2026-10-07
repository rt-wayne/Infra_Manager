package com.mpx.infra_manager_java.service.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：草稿附件上傳服務的單元測試（S6 回合三，用 @TempDir 當附件根目錄、mock 交易管理器）。
//           鎖定：成功時檔案落在 <root>/<APP_ID>/<uuid>.<ext>、sha256 與內容一致、暫存目錄清空、MIME 由副檔名決定；
//           白名單外 400、超過大小 400、空檔 400、超過檔數 400、非 DRAFT 409、非申請人 403、單不存在 404、單號格式錯 404；
//           交易內才發現超過檔數（併發）、DB 寫入失敗、commit 失敗，都不留任何實體檔；
//           檔名清理（去路徑、控制字元、RTL 覆寫字元、超長保留副檔名）
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;

import com.mpx.infra_manager_java.dao.changerequest.AttachDao;
import com.mpx.infra_manager_java.model.auth.AuthUser;
import com.mpx.infra_manager_java.model.changerequest.AppDetail;
import com.mpx.infra_manager_java.model.changerequest.AppLockRow;
import com.mpx.infra_manager_java.model.changerequest.AttachRow;
import com.mpx.infra_manager_java.service.sysparam.SysParamService;
import com.mpx.infra_manager_java.web.ApiBadRequestException;
import com.mpx.infra_manager_java.web.ApiConflictException;
import com.mpx.infra_manager_java.web.ApiNotFoundException;

class AttachmentUploadServiceTest {

	private static final String APP = "IM20261007-001";
	private static final AuthUser USER = new AuthUser("T0001", "wayne", "王小明", List.of("infra"), false);
	private static final byte[] CONTENT = "設備清單內容".getBytes(StandardCharsets.UTF_8);

	@TempDir
	Path root;

	private final AttachDao attachDao = mock(AttachDao.class);
	private final SysParamService sysParamService = mock(SysParamService.class);
	private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);

	@BeforeEach
	void setUp() {
		when(sysParamService.uploadMaxMb()).thenReturn(50);
		when(sysParamService.uploadMaxFiles()).thenReturn(30);
		AppLockRow draft = state("DRAFT", "T0001");
		when(attachDao.findAppState(APP)).thenReturn(draft);
		when(attachDao.lockApp(APP)).thenReturn(draft);
		when(attachDao.countAppFiles(APP)).thenReturn(0);
		when(attachDao.insertAppFile(eq(APP), anyString(), anyString(), anyString(), anyLong(), anyString(),
				anyString(), anyString())).thenAnswer(inv -> {
					AttachRow r = new AttachRow();
					r.setAttachId(101L);
					r.setOwnerType("APP");
					r.setOwnerId(APP);
					r.setOrigFileName(inv.getArgument(1));
					r.setFilePath(inv.getArgument(3));
					r.setFileByteQty(inv.getArgument(4));
					r.setMimeType(inv.getArgument(5));
					r.setCreateDate(Timestamp.valueOf("2026-10-07 10:20:30"));
					return r;
				});
	}

	private static AppLockRow state(String status, String applicant) {
		AppLockRow s = new AppLockRow();
		s.setAppStatusCode(status);
		s.setApplyUserId(applicant);
		s.setRowVerNo(0L);
		return s;
	}

	private AttachmentUploadService service() {
		return new AttachmentUploadService(attachDao, sysParamService, txManager, root.toString());
	}

	private static MockMultipartFile file(String name, byte[] content) {
		return new MockMultipartFile("file", name, "application/x-anything", content);
	}

	/** 根目錄底下所有一般檔案（含暫存目錄） */
	private List<Path> filesUnderRoot() throws IOException {
		try (Stream<Path> s = Files.walk(root)) {
			return s.filter(Files::isRegularFile).toList();
		}
	}

	@Test
	void 成功時落檔路徑_sha256_MIME正確且暫存清空() throws Exception {
		AppDetail.Attachment result = service().upload(APP, file("設備清單.PDF", CONTENT), USER);

		assertThat(result.attachId()).isEqualTo(101L);
		assertThat(result.fileName()).isEqualTo("設備清單.PDF");
		assertThat(result.byteQty()).isEqualTo(CONTENT.length);
		assertThat(result.mimeType()).isEqualTo("application/pdf");
		// 與檢視 API 同一個格式器，精度到分
		assertThat(result.uploadedAt()).isEqualTo("2026-10-07 10:20");

		ArgumentCaptor<String> storeName = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> filePath = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> sha = ArgumentCaptor.forClass(String.class);
		verify(attachDao).insertAppFile(eq(APP), eq("設備清單.PDF"), storeName.capture(), filePath.capture(),
				eq((long) CONTENT.length), eq("application/pdf"), sha.capture(), eq("T0001"));
		assertThat(storeName.getValue()).matches("[0-9a-f-]{36}\\.pdf");
		assertThat(filePath.getValue()).isEqualTo(APP + "/" + storeName.getValue());
		String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(CONTENT));
		assertThat(sha.getValue()).isEqualTo(expected);

		Path stored = root.resolve(APP).resolve(storeName.getValue());
		assertThat(Files.readAllBytes(stored)).isEqualTo(CONTENT);
		assertThat(filesUnderRoot()).containsExactly(stored);
		verify(txManager).commit(any());
	}

	@Test
	void 白名單外400且不碰DB不落檔() throws Exception {
		assertThatThrownBy(() -> service().upload(APP, file("tool.exe", CONTENT), USER))
				.isInstanceOf(ApiBadRequestException.class).hasMessage(AttachmentUploadService.MSG_TYPE_NOT_ALLOWED);
		assertThatThrownBy(() -> service().upload(APP, file("沒有副檔名", CONTENT), USER))
				.isInstanceOf(ApiBadRequestException.class);
		verify(attachDao, never()).findAppState(anyString());
		assertThat(filesUnderRoot()).isEmpty();
	}

	@Test
	void 超過系統參數大小400() throws Exception {
		when(sysParamService.uploadMaxMb()).thenReturn(1);
		byte[] big = new byte[(int) AttachmentUploadService.MB + 1];
		assertThatThrownBy(() -> service().upload(APP, file("a.zip", big), USER))
				.isInstanceOf(ApiBadRequestException.class).hasMessage("檔案超過 1 MB 上限");
		assertThat(filesUnderRoot()).isEmpty();
	}

	@Test
	void 空檔400() {
		assertThatThrownBy(() -> service().upload(APP, file("a.txt", new byte[0]), USER))
				.isInstanceOf(ApiBadRequestException.class).hasMessage(AttachmentUploadService.MSG_EMPTY_FILE);
	}

	@Test
	void 已達檔數上限400且不落檔() throws Exception {
		when(attachDao.countAppFiles(APP)).thenReturn(30);
		assertThatThrownBy(() -> service().upload(APP, file("a.pdf", CONTENT), USER))
				.isInstanceOf(ApiBadRequestException.class).hasMessage("附件已達 30 個上限");
		assertThat(filesUnderRoot()).isEmpty();
		verify(attachDao, never()).lockApp(anyString());
	}

	@Test
	void 交易內鎖住後才發現已達上限_回滾且不留檔() throws Exception {
		// 預查時 29 個，鎖住後別人已傳完第 30 個
		when(attachDao.countAppFiles(APP)).thenReturn(29, 30);
		assertThatThrownBy(() -> service().upload(APP, file("a.pdf", CONTENT), USER))
				.isInstanceOf(ApiBadRequestException.class);
		verify(attachDao, never()).insertAppFile(anyString(), anyString(), anyString(), anyString(), anyLong(),
				anyString(), anyString(), anyString());
		verify(txManager).rollback(any());
		assertThat(filesUnderRoot()).isEmpty();
	}

	@Test
	void 非DRAFT回409() {
		when(attachDao.findAppState(APP)).thenReturn(state("PENDING", "T0001"));
		assertThatThrownBy(() -> service().upload(APP, file("a.pdf", CONTENT), USER))
				.isInstanceOf(ApiConflictException.class).hasMessage(AttachmentUploadService.MSG_NOT_DRAFT);
	}

	@Test
	void 鎖住後狀態已變_回409且不留檔() throws Exception {
		when(attachDao.lockApp(APP)).thenReturn(state("PENDING", "T0001"));
		assertThatThrownBy(() -> service().upload(APP, file("a.pdf", CONTENT), USER))
				.isInstanceOf(ApiConflictException.class);
		assertThat(filesUnderRoot()).isEmpty();
	}

	@Test
	void 非申請人403() {
		when(attachDao.findAppState(APP)).thenReturn(state("DRAFT", "T0002"));
		assertThatThrownBy(() -> service().upload(APP, file("a.pdf", CONTENT), USER))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void 單不存在或單號格式錯404() {
		when(attachDao.findAppState(APP)).thenReturn(null);
		assertThatThrownBy(() -> service().upload(APP, file("a.pdf", CONTENT), USER))
				.isInstanceOf(ApiNotFoundException.class);
		assertThatThrownBy(() -> service().upload("../etc", file("a.pdf", CONTENT), USER))
				.isInstanceOf(ApiNotFoundException.class);
	}

	@Test
	void DB寫入失敗時回滾且不留檔() throws Exception {
		when(attachDao.insertAppFile(anyString(), anyString(), anyString(), anyString(), anyLong(), anyString(),
				anyString(), anyString())).thenThrow(new DataIntegrityViolationException("x"));
		assertThatThrownBy(() -> service().upload(APP, file("a.pdf", CONTENT), USER))
				.isInstanceOf(DataIntegrityViolationException.class);
		verify(txManager).rollback(any());
		assertThat(filesUnderRoot()).isEmpty();
	}

	@Test
	void commit失敗時已搬好的檔也刪掉() throws Exception {
		doThrow(new TransactionSystemException("commit failed")).when(txManager).commit(any());
		assertThatThrownBy(() -> service().upload(APP, file("a.pdf", CONTENT), USER))
				.isInstanceOf(TransactionSystemException.class);
		assertThat(filesUnderRoot()).isEmpty();
	}

	@Test
	void 根目錄未設定是部署錯誤() {
		AttachmentUploadService noRoot = new AttachmentUploadService(attachDao, sysParamService, txManager, "");
		assertThatThrownBy(() -> noRoot.upload(APP, file("a.pdf", CONTENT), USER))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void 檔名清理() {
		assertThat(AttachmentUploadService.cleanFileName("C:\\fakepath\\報價單.xlsx")).isEqualTo("報價單.xlsx");
		assertThat(AttachmentUploadService.cleanFileName("../../a/b.pdf")).isEqualTo("b.pdf");
		assertThat(AttachmentUploadService.cleanFileName("  a\u0000b\r\n.txt  ")).isEqualTo("ab.txt");
		// U+202E 會讓「invoice‮fdp.exe」顯示成「invoiceexe.pdf」；去掉後副檔名就是真的 exe
		assertThat(AttachmentUploadService.cleanFileName("invoice\u202Efdp.exe")).isEqualTo("invoicefdp.exe");
		assertThat(AttachmentUploadService.cleanFileName(null)).isEmpty();

		String longName = "設".repeat(300) + ".docx";
		String cleaned = AttachmentUploadService.cleanFileName(longName);
		assertThat(cleaned.codePointCount(0, cleaned.length())).isEqualTo(AttachmentUploadService.MAX_NAME_LENGTH);
		assertThat(cleaned).endsWith(".docx").startsWith("設設");

		String emoji = "😀".repeat(300) + ".pdf";
		String cleanedEmoji = AttachmentUploadService.cleanFileName(emoji);
		assertThat(cleanedEmoji.codePointCount(0, cleanedEmoji.length())).isEqualTo(255);
		assertThat(cleanedEmoji).endsWith(".pdf");
	}
}
