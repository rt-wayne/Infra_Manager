package com.mpx.infra_manager_java.model.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：附件下載用的檔案描述（S4）：已確認位於附件根目錄下的 Resource、原始檔名、MIME 與長度
// ============================================================

import org.springframework.core.io.Resource;

public record AttachmentFile(Resource resource, String fileName, String mimeType, long length) {
}
