package com.mpx.infra_manager_java.service.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：附件副檔名白名單與 MIME 對照的單元測試（S6 回合一-2）。鎖定：⑧A 清單 17 種都在、不分大小寫、
//           取最後一個點、沒有副檔名／點在結尾／隱藏檔／點在資料夾名裡都視為沒有副檔名、白名單外回 octet-stream
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AttachmentTypesTest {

	@Test
	void 裁示八A清單都允許() {
		for (String ext : new String[] { "jpg", "jpeg", "png", "gif", "bmp", "webp", "pdf", "doc", "docx", "xls", "xlsx",
				"ppt", "pptx", "txt", "csv", "zip", "msg" }) {
			assertThat(AttachmentTypes.isAllowed("a." + ext)).as(ext).isTrue();
			assertThat(AttachmentTypes.mimeFor("a." + ext)).as(ext).isNotEqualTo(AttachmentTypes.OCTET_STREAM);
		}
	}

	@Test
	void 副檔名不分大小寫_取最後一個點() {
		assertThat(AttachmentTypes.mimeFor("報價單.v2.PDF")).isEqualTo("application/pdf");
		assertThat(AttachmentTypes.mimeFor("IM1/uuid.Docx"))
				.isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
	}

	@Test
	void 白名單外與沒有副檔名都不允許_回octet_stream() {
		for (String name : new String[] { "a.exe", "a.html", "a.svg", "a.pdf.exe", "noext", "a.", ".pdf", "dir.pdf/file",
				"dir.pdf\\file", "", null }) {
			assertThat(AttachmentTypes.isAllowed(name)).as(String.valueOf(name)).isFalse();
			assertThat(AttachmentTypes.mimeFor(name)).as(String.valueOf(name)).isEqualTo(AttachmentTypes.OCTET_STREAM);
		}
	}
}
