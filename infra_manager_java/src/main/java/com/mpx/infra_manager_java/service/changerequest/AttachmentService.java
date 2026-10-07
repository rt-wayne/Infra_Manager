package com.mpx.infra_manager_java.service.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：附件下載（S4，只做後端端點；殼 jar 串流透傳到 S6 一起處理，見 BACKLOG 第 79 項）。
//           授權規則：附件必須屬於該申請單（AttachDao.findByApp），單或附件不存在一律 404、不洩漏存在與否；
//           實體路徑＝im.attach.root ＋ IM_ATTACH.FILE_PATH，normalize 後必須仍在根目錄下（DB 層 CHECK 已擋 ..，
//           這裡再擋一次）；根目錄沒設定是部署錯誤 → 500，log 只寫原因不寫路徑
//           2026-10-07 S6 回合一-2：長度改用實體檔 Files.size（DB 值不同時寫 warn，原本以 DB 為準會截斷或卡住下載，第 95 項 N5）；
//                MIME 改由實體檔副檔名查白名單（AttachmentTypes），不再照 DB 值回，白名單外 octet-stream（第 95 項 N6）
// ============================================================

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.stereotype.Service;

import com.mpx.infra_manager_java.dao.changerequest.AttachDao;
import com.mpx.infra_manager_java.model.changerequest.AttachRow;
import com.mpx.infra_manager_java.model.changerequest.AttachmentFile;
import com.mpx.infra_manager_java.web.ApiNotFoundException;

@Service
public class AttachmentService {

	public static final String MSG_ATTACH_NOT_FOUND = "找不到附件";
	public static final String MSG_FILE_MISSING = "附件檔案不存在";

	private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);

	private final AppQueryService appQueryService;
	private final AttachDao attachDao;
	private final String attachRoot;

	public AttachmentService(AppQueryService appQueryService, AttachDao attachDao,
			@Value("${im.attach.root:}") String attachRoot) {
		this.appQueryService = appQueryService;
		this.attachDao = attachDao;
		this.attachRoot = attachRoot;
	}

	public AttachmentFile open(String appId, long attachId) {
		appQueryService.findApp(appId);
		AttachRow row = attachDao.findByApp(appId).stream().filter(a -> a.getAttachId() != null && a.getAttachId() == attachId)
				.findFirst().orElseThrow(() -> new ApiNotFoundException(MSG_ATTACH_NOT_FOUND));

		if (attachRoot == null || attachRoot.isBlank()) {
			log.error("附件根目錄 im.attach.root 未設定，無法提供下載");
			throw new IllegalStateException("im.attach.root not configured");
		}
		Path root = Paths.get(attachRoot).toAbsolutePath().normalize();
		Path file = root.resolve(row.getFilePath()).normalize();
		if (!file.startsWith(root)) {
			log.warn("附件路徑逸出根目錄，attachId={}", attachId);
			throw new ApiNotFoundException(MSG_ATTACH_NOT_FOUND);
		}
		if (!Files.isRegularFile(file)) {
			log.warn("附件檔案不存在，attachId={}", attachId);
			throw new ApiNotFoundException(MSG_FILE_MISSING);
		}
		long length;
		try {
			length = Files.size(file);
		} catch (IOException e) {
			log.warn("附件檔案讀不到大小，attachId={}: {}", attachId, e.getClass().getSimpleName());
			throw new ApiNotFoundException(MSG_FILE_MISSING);
		}
		if (row.getFileByteQty() != null && row.getFileByteQty() != length) {
			log.warn("附件實際大小與 DB 記錄不同，attachId={} db={} actual={}", attachId, row.getFileByteQty(), length);
		}
		String mime = AttachmentTypes.mimeFor(row.getFilePath());
		String name = row.getOrigFileName() == null || row.getOrigFileName().isBlank() ? "attachment" : row.getOrigFileName();
		return new AttachmentFile(new PathResource(file), name, mime, length);
	}
}
