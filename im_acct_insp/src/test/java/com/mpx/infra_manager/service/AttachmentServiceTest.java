package com.mpx.infra_manager.service;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：附件單元測試（spec v2.0 §4.10；驗收 T03、T21）：副檔名白名單（不分大小寫）與檔頭比對（.txt 改名 .pdf 擋下）、
//           超過 10 MB 413 E15、空檔視同未上傳、根目錄未設定 500；寫檔路徑 REQ_ID/uuid.副檔名、DB 存相對路徑與原檔名、
//           交易回復時清除；下載須可看申請單（否則 403）、Content-Disposition 以 RFC 5987 編碼原檔名
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 變更單 #3：啟動檢查根目錄——空值、相對路徑、單反斜線被吃掉後的 D:homeattach 皆丟 IllegalStateException（訊息不含設定值），絕對路徑通過
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-10
// 變更說明: 補檢查順序測試（同前端選檔）：超過 10 MB 的 .exe 回 E14、空的 .exe 回 E14、無檔名的空 part 視同未上傳；
//           檔名超過 255 字時空檔視同未上傳、超過 10 MB 回 E15，皆先於檔名欄寬 E11
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 1：設定鍵 infra.manager.attach.dir 與範例路徑 D:/home/infra_manager/attach（專案改名 infra_manager）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// ============================================================

import static com.mpx.infra_manager.service.RequestServiceTest.assertStatus;
import static com.mpx.infra_manager.support.TestFixture.KEVIN;
import static com.mpx.infra_manager.support.TestFixture.NOW;
import static com.mpx.infra_manager.support.TestFixture.TESTER;
import static com.mpx.infra_manager.support.TestFixture.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import com.mpx.infra_manager.common.AcctConst;
import com.mpx.infra_manager.common.AcctMsg;
import com.mpx.infra_manager.model.AcctAttach;
import com.mpx.infra_manager.model.AcctReqDetail;
import com.mpx.infra_manager.support.TestFixture;

class AttachmentServiceTest {

	static final byte[] PDF = { 0x25, 0x50, 0x44, 0x46, 0x2D, 0x31 };
	static final byte[] PNG = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A };

	@TempDir
	Path root;

	TestFixture fx;
	AttachmentService svc;

	@BeforeEach
	void setUp() {
		fx = new TestFixture();
		svc = new AttachmentService(fx.attachDao, fx.acctReqDao, fx.permissionService, fx.codeService, root.toString());
		when(fx.attachDao.insert(anyString(), anyString(), anyString(), anyString(), anyLong(), anyString(),
				org.mockito.ArgumentMatchers.any(), anyString())).thenReturn(1);
	}

	@Test
	void 格式檢查_副檔名不分大小寫_檔頭須相符() {
		assertThat(svc.check(file("Proof.PNG", PNG)).mimeType()).isEqualTo("image/png");
		assertThat(svc.check(file("C:\\fake\\公文.pdf", PDF)).fileName()).isEqualTo("公文.pdf");
		assertStatus(() -> svc.check(file("a.exe", PDF)), HttpStatus.BAD_REQUEST, AcctMsg.E14);
		assertStatus(() -> svc.check(file("note.pdf", "hello".getBytes(StandardCharsets.UTF_8))), HttpStatus.BAD_REQUEST,
				AcctMsg.E14);
		assertStatus(() -> svc.check(file("noext", PDF)), HttpStatus.BAD_REQUEST, AcctMsg.E14);
		assertThat(AttachmentService.magicOk("doc", new byte[] { (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1,
				(byte) 0xB1, 0x1A, (byte) 0xE1 })).isTrue();
		assertThat(AttachmentService.magicOk("docx", new byte[] { 0x50, 0x4B, 0x03, 0x04 })).isTrue();
		assertThat(AttachmentService.magicOk("jpg", new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF })).isTrue();
		assertThat(AttachmentService.magicOk("jpg", new byte[] { (byte) 0xFF, (byte) 0xD8 })).isFalse();
	}

	@Test
	void 大小_超過10MB_413_空檔視同未上傳() {
		byte[] big = new byte[(int) AcctConst.ATTACH_MAX_BYTES + 1];
		System.arraycopy(PDF, 0, big, 0, PDF.length);
		assertStatus(() -> svc.check(file("big.pdf", big)), HttpStatus.CONTENT_TOO_LARGE, AcctMsg.E15);
		byte[] max = new byte[(int) AcctConst.ATTACH_MAX_BYTES];
		System.arraycopy(PDF, 0, max, 0, PDF.length);
		assertThat(svc.check(file("max.pdf", max))).isNotNull();
		assertThat(svc.check(file("empty.pdf", new byte[0]))).isNull();
		assertThat(svc.check(null)).isNull();
	}

	@Test
	void 檢查順序同前端_副檔名先於大小與空檔() {
		// 超過 10 MB 且副檔名不符：先回 E14（前端 attach.ts 同序）
		byte[] big = new byte[(int) AcctConst.ATTACH_MAX_BYTES + 1];
		assertStatus(() -> svc.check(file("big.exe", big)), HttpStatus.BAD_REQUEST, AcctMsg.E14);
		// 空檔但副檔名不符：E14，不視同未上傳
		assertStatus(() -> svc.check(file("empty.exe", new byte[0])), HttpStatus.BAD_REQUEST, AcctMsg.E14);
		// 沒選檔（無檔名的空 part）仍視同未上傳
		assertThat(svc.check(file("", new byte[0]))).isNull();
		// 檔名超過 255 字：空檔先視同未上傳、超過 10 MB 先回 E15，都不回檔名欄寬 E11
		String longName = "n".repeat(AttachmentService.FILE_NAME_MAX) + ".pdf";
		assertThat(svc.check(file(longName, new byte[0]))).isNull();
		assertStatus(() -> svc.check(file(longName, big)), HttpStatus.CONTENT_TOO_LARGE, AcctMsg.E15);
		assertStatus(() -> svc.check(file(longName, PDF)), HttpStatus.BAD_REQUEST, AcctMsg.E11_PREFIX + "附件檔名");
	}

	@Test
	void 啟動檢查_空值_相對路徑_單反斜線被吃掉_啟動失敗() {
		for (String bad : new String[] { "", " ", "attach", "home/infra_manager/attach", "D:homeinfra_managerattach" }) {
			AttachmentService s = new AttachmentService(fx.attachDao, fx.acctReqDao, fx.permissionService, fx.codeService, bad);
			assertThatThrownBy(s::validateAttachDir).as("值=[%s]", bad).isInstanceOf(IllegalStateException.class)
					.hasMessage(AttachmentService.MSG_ATTACH_DIR_INVALID);
		}
		AttachmentService nul = new AttachmentService(fx.attachDao, fx.acctReqDao, fx.permissionService, fx.codeService, null);
		assertThatThrownBy(nul::validateAttachDir).isInstanceOf(IllegalStateException.class);
		assertThat(AttachmentService.MSG_ATTACH_DIR_INVALID).contains("infra.manager.attach.dir", "絕對路徑", "D:/home/infra_manager/attach");
	}

	@Test
	void 啟動檢查_絕對路徑通過_目錄不存在寫檔時自動建立() throws Exception {
		svc.validateAttachDir();
		Path notYet = root.resolve("not_yet").resolve("attach");
		AttachmentService s = new AttachmentService(fx.attachDao, fx.acctReqDao, fx.permissionService, fx.codeService,
				notYet.toString().replace('\\', '/'));
		s.validateAttachDir();
		List<Path> written = new ArrayList<>();
		s.store("AR20261007-009", "DOC", s.check(file("a.pdf", PDF)), written, NOW, KEVIN);
		assertThat(written.get(0)).startsWith(notYet.toAbsolutePath().normalize());
		assertThat(Files.exists(written.get(0))).isTrue();
	}

	@Test
	void 根目錄未設定_有檔案時500() {
		AttachmentService none = new AttachmentService(fx.attachDao, fx.acctReqDao, fx.permissionService, fx.codeService, " ");
		assertStatus(() -> none.check(file("a.pdf", PDF)), HttpStatus.INTERNAL_SERVER_ERROR, null);
		assertThat(none.check(null)).isNull();
	}

	@Test
	void 寫檔_REQ_ID下uuid檔名_DB存相對路徑_回復時清除() throws Exception {
		List<Path> written = new ArrayList<>();
		svc.store("AR20261007-001", "DOC", svc.check(file("公文.pdf", PDF)), written, NOW, KEVIN);
		assertThat(written).hasSize(1);
		Path f = written.get(0);
		assertThat(f.getParent()).isEqualTo(root.toAbsolutePath().normalize().resolve("AR20261007-001"));
		assertThat(f.getFileName().toString()).matches("[0-9a-f-]{36}\\.pdf");
		assertThat(Files.readAllBytes(f)).isEqualTo(PDF);
		ArgumentCaptor<String> rel = ArgumentCaptor.forClass(String.class);
		verify(fx.attachDao).insert(eq("AR20261007-001"), eq("DOC"), eq("公文.pdf"), rel.capture(), eq(6L),
				eq("application/pdf"), eq(NOW), eq(KEVIN));
		assertThat(rel.getValue()).isEqualTo("AR20261007-001/" + f.getFileName());
		svc.cleanup(written);
		assertThat(Files.exists(f)).isFalse();
		assertThat(written).isEmpty();
	}

	@Test
	void 下載_須可看申請單_回原始位元組與RFC5987檔名() throws Exception {
		Path dir = Files.createDirectories(root.resolve("AR20261007-001"));
		Files.write(dir.resolve("u.pdf"), PDF);
		AcctAttach a = new AcctAttach();
		a.setAttachId(5L);
		a.setReqId("AR20261007-001");
		a.setFileName("公文 1.pdf");
		a.setFilePath("AR20261007-001/u.pdf");
		a.setMimeType("application/pdf");
		when(fx.attachDao.findById(5L)).thenReturn(a);
		AcctReqDetail r = PermissionServiceTest.req("REVIEW", 3, "DB");
		r.setReqId("AR20261007-001");
		when(fx.acctReqDao.findById("AR20261007-001")).thenReturn(r);

		assertStatus(() -> svc.download(id(TESTER), 5L), HttpStatus.FORBIDDEN, AcctMsg.P01);
		ResponseEntity<byte[]> res = svc.download(id(KEVIN), 5L);
		assertThat(res.getBody()).isEqualTo(PDF);
		assertThat(res.getHeaders().getContentType().toString()).isEqualTo("application/pdf");
		assertThat(res.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment")
				.contains("filename*=UTF-8''%E5%85%AC%E6%96%87%201.pdf");
		assertStatus(() -> svc.download(id(KEVIN), 6L), HttpStatus.NOT_FOUND, AcctMsg.N01);

		a.setFilePath("../outside.pdf");
		assertStatus(() -> svc.download(id(KEVIN), 5L), HttpStatus.INTERNAL_SERVER_ERROR, null);
	}

	static MockMultipartFile file(String name, byte[] bytes) {
		return new MockMultipartFile("docFile", name, "application/octet-stream", bytes);
	}
}
