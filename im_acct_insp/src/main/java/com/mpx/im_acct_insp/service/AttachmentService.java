package com.mpx.im_acct_insp.service;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：申請單附件上傳與驗權下載（spec v2.0 §4.10、§6.5）
//           檢查：空檔視同未上傳；單檔 ≤ 10 MB（否則 413 E15）；副檔名（不分大小寫）限 .pdf .doc .docx .jpg .png，
//           且檔頭須相符（pdf %PDF、doc OLE2、docx zip、jpg FF D8 FF、png 89 50 4E 47），否則 400 E14；
//           存放：根目錄 im.acct.insp.attach.dir（未設定時上傳／下載回 500 並記 log，啟動時記 WARN），
//           實體路徑 REQ_ID/uuid.副檔名（不用原檔名當路徑），DB 存相對路徑、原檔名、大小、MIME（依副檔名）；
//           寫入順序：呼叫端在交易內先寫檔再寫 DB，交易失敗時以 cleanup 刪除剛寫的檔（盡力而為，失敗記 log）；
//           下載：須「可看申請單」（否則 403），回原始位元組、Content-Type＝MIME、Content-Disposition attachment（RFC 5987 檔名）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 變更單 #3：啟動時檢查 im.acct.insp.attach.dir，未設定或不是絕對路徑（例 properties 單反斜線被吃掉成 D:homeattach）
//           即記 ERROR 說明寫法並丟 IllegalStateException 使啟動失敗（不印設定值）；取代原本只記 WARN；目錄不存在仍於寫檔時自動建立
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-10
// 變更說明: 檢查順序對齊前端選檔檢查：副檔名 E14 → 大小 E15 → 空檔視同未上傳 → 檔名欄寬 → 檔頭 E14
//           （原為空檔 → 大小 → 副檔名）；空檔但副檔名不符改回 E14，沒選檔（無檔名的空 part）仍視同未上傳。
//           超過 multipart 上限（預設 10 MB）的檔案在進入本方法前即由框架回 413 E15，此處的大小檢查僅在上限調高時生效
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 1：設定鍵改為 infra.manager.attach.dir（原 im.acct.insp.attach.dir），
//           錯誤訊息範例路徑改為 D:/home/infra_manager/attach（專案改名 infra_manager）
// ============================================================

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.mpx.im_acct_insp.common.AcctConst;
import com.mpx.im_acct_insp.common.AcctMsg;
import com.mpx.im_acct_insp.common.ApiException;
import com.mpx.im_acct_insp.common.Identity;
import com.mpx.im_acct_insp.common.Texts;
import com.mpx.im_acct_insp.dao.AcctReqDao;
import com.mpx.im_acct_insp.dao.AttachDao;
import com.mpx.im_acct_insp.model.AcctAttach;
import com.mpx.im_acct_insp.model.AcctReq;

import jakarta.annotation.PostConstruct;

@Service
public class AttachmentService {

	private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);

	static final String KEY_ATTACH_DIR = "infra.manager.attach.dir";
	static final int FILE_NAME_MAX = 255;
	static final Map<String, String> MIME = Map.of(
			"pdf", "application/pdf",
			"doc", "application/msword",
			"docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
			"jpg", "image/jpeg",
			"png", "image/png");

	/** 通過檢查、待寫入的附件 */
	public record PendingFile(String fileName, String ext, String mimeType, byte[] bytes) {
	}

	private final AttachDao attachDao;
	private final AcctReqDao acctReqDao;
	private final PermissionService permissionService;
	private final CodeService codeService;
	private final String attachDir;

	public AttachmentService(AttachDao attachDao, AcctReqDao acctReqDao, PermissionService permissionService,
			CodeService codeService, @Value("${" + KEY_ATTACH_DIR + ":}") String attachDir) {
		this.attachDao = attachDao;
		this.acctReqDao = acctReqDao;
		this.permissionService = permissionService;
		this.codeService = codeService;
		this.attachDir = attachDir == null ? "" : attachDir.trim();
	}

	/** 寫法錯誤時的 ERROR log／例外訊息（不含設定值） */
	static final String MSG_ATTACH_DIR_INVALID = KEY_ATTACH_DIR
			+ " 必須是絕對路徑，Windows 請用 / 或 \\\\，例：D:/home/infra_manager/attach";

	@PostConstruct
	void validateAttachDir() {
		if (!isAbsolutePath(attachDir)) {
			log.error(MSG_ATTACH_DIR_INVALID);
			throw new IllegalStateException(MSG_ATTACH_DIR_INVALID);
		}
	}

	/** 非空且解析後為絕對路徑 */
	static boolean isAbsolutePath(String value) {
		if (value == null || value.isBlank()) {
			return false;
		}
		try {
			return Paths.get(value.trim()).isAbsolute();
		} catch (InvalidPathException e) {
			return false;
		}
	}

	// ------------------------------------------------------------
	// 上傳
	// ------------------------------------------------------------

	/**
	 * 檢查上傳檔；未上傳（null 或無檔名的空 part）或副檔名合法的空檔回 null，副檔名不符的空檔回 E14。
	 * 順序同前端選檔檢查（utils/attach.ts）：副檔名 E14 → 大小 E15 → 空檔視同未上傳 → 檔名欄寬 → 檔頭 E14
	 */
	public PendingFile check(MultipartFile file) {
		if (file == null) {
			return null;
		}
		String name = baseName(file.getOriginalFilename());
		if (file.isEmpty() && name.isEmpty()) {
			// 沒選檔（空的 part、無檔名）
			return null;
		}
		String ext = extension(name);
		if (ext == null || !MIME.containsKey(ext)) {
			throw ApiException.badRequest(AcctMsg.E14);
		}
		if (file.getSize() > AcctConst.ATTACH_MAX_BYTES) {
			throw new ApiException(org.springframework.http.HttpStatus.CONTENT_TOO_LARGE, AcctMsg.E15);
		}
		if (file.isEmpty()) {
			return null;
		}
		Texts.maxChars("附件檔名", name, FILE_NAME_MAX);
		byte[] bytes;
		try {
			bytes = file.getBytes();
		} catch (IOException e) {
			log.error("讀取上傳附件失敗 例外={}", e.getClass().getSimpleName());
			throw ApiException.internal("附件讀取失敗，請重新上傳");
		}
		if (bytes.length > AcctConst.ATTACH_MAX_BYTES) {
			throw new ApiException(org.springframework.http.HttpStatus.CONTENT_TOO_LARGE, AcctMsg.E15);
		}
		if (!magicOk(ext, bytes)) {
			throw ApiException.badRequest(AcctMsg.E14);
		}
		root();
		return new PendingFile(name, ext, MIME.get(ext), bytes);
	}

	/** 寫檔並寫 IM_ACCT_ATTACH 一列（須在交易內呼叫）；寫入的實體檔加入 written 供失敗時清除 */
	public void store(String reqId, String attachTypeCode, PendingFile p, List<Path> written, LocalDateTime now, String userId) {
		Path root = root();
		Path dir = root.resolve(reqId).normalize();
		if (!dir.startsWith(root)) {
			throw ApiException.internal("附件路徑不正確");
		}
		String stored = UUID.randomUUID() + "." + p.ext();
		Path target = dir.resolve(stored);
		try {
			Files.createDirectories(dir);
			Files.write(target, p.bytes(), StandardOpenOption.CREATE_NEW);
		} catch (IOException e) {
			log.error("附件寫檔失敗 reqId={} 例外={}", reqId, e.getClass().getSimpleName());
			throw ApiException.internal("附件儲存失敗，請稍後再試");
		}
		written.add(target);
		String rel = reqId + "/" + stored;
		if (attachDao.insert(reqId, attachTypeCode, p.fileName(), rel, p.bytes().length, p.mimeType(), now, userId) != 1) {
			throw ApiException.internal("附件資料寫入未完成");
		}
	}

	/** 交易回復時刪除剛寫的檔（盡力而為） */
	public void cleanup(List<Path> written) {
		for (Path p : written) {
			try {
				Files.deleteIfExists(p);
			} catch (IOException | RuntimeException e) {
				log.warn("交易回復後刪除附件失敗 file={} 例外={}", p.getFileName(), e.getClass().getSimpleName());
			}
		}
		written.clear();
	}

	// ------------------------------------------------------------
	// 下載
	// ------------------------------------------------------------

	public ResponseEntity<byte[]> download(Identity id, long attachId) {
		AcctAttach a = attachDao.findById(attachId);
		if (a == null) {
			throw AcctMsg.notFound();
		}
		AcctReq r = acctReqDao.findById(a.getReqId());
		if (r == null) {
			throw AcctMsg.notFound();
		}
		if (!permissionService.canViewRequest(id, r, codeService.stageMap())) {
			throw AcctMsg.forbidden();
		}
		Path root = root();
		Path file = root.resolve(a.getFilePath()).normalize();
		if (!file.startsWith(root) || !Files.isRegularFile(file)) {
			log.error("附件檔案不存在 attachId={}", attachId);
			throw ApiException.internal("附件檔案不存在，請聯絡系統管理員");
		}
		byte[] bytes;
		try {
			bytes = Files.readAllBytes(file);
		} catch (IOException e) {
			log.error("附件讀檔失敗 attachId={} 例外={}", attachId, e.getClass().getSimpleName());
			throw ApiException.internal("附件讀取失敗，請稍後再試");
		}
		HttpHeaders h = new HttpHeaders();
		h.setContentType(MediaType.parseMediaType(a.getMimeType()));
		h.setContentDisposition(ContentDisposition.attachment().filename(a.getFileName(), StandardCharsets.UTF_8).build());
		return ResponseEntity.ok().headers(h).body(bytes);
	}

	// ------------------------------------------------------------
	// 共用
	// ------------------------------------------------------------

	Path root() {
		if (attachDir.isEmpty()) {
			log.error("未設定附件根目錄（{}）", KEY_ATTACH_DIR);
			throw ApiException.internal("附件存放位置未設定，請聯絡系統管理員");
		}
		return Paths.get(attachDir).toAbsolutePath().normalize();
	}

	/** 去掉瀏覽器可能帶的路徑，只留檔名 */
	static String baseName(String original) {
		if (original == null) {
			return "";
		}
		String s = original.replace('\\', '/');
		int i = s.lastIndexOf('/');
		return (i >= 0 ? s.substring(i + 1) : s).trim();
	}

	/** 小寫副檔名；沒有回 null */
	static String extension(String name) {
		int i = name.lastIndexOf('.');
		return i < 0 || i == name.length() - 1 ? null : name.substring(i + 1).toLowerCase(Locale.ROOT);
	}

	/** 檔頭檢查 */
	static boolean magicOk(String ext, byte[] b) {
		return switch (ext) {
		case "pdf" -> startsWith(b, 0x25, 0x50, 0x44, 0x46);
		case "doc" -> startsWith(b, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1);
		case "docx" -> startsWith(b, 0x50, 0x4B, 0x03, 0x04);
		case "jpg" -> startsWith(b, 0xFF, 0xD8, 0xFF);
		case "png" -> startsWith(b, 0x89, 0x50, 0x4E, 0x47);
		default -> false;
		};
	}

	private static boolean startsWith(byte[] b, int... sig) {
		if (b == null || b.length < sig.length) {
			return false;
		}
		for (int i = 0; i < sig.length; i++) {
			if ((b[i] & 0xFF) != sig[i]) {
				return false;
			}
		}
		return true;
	}
}
