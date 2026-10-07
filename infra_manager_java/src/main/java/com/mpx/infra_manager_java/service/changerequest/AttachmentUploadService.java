package com.mpx.infra_manager_java.service.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：草稿附件上傳（S6 回合三，裁示 ②B 一次一檔、⑧A 副檔名白名單、⑥A 上傳時 FOR UPDATE 鎖主檔）。
//           順序：檢查單號、檔名、副檔名、大小（不碰 DB）→ 不鎖先查一次狀態與檔數（明顯不能傳就不搬檔）→
//           複製到 <root>/.tmp/<uuid>.part、邊寫邊算 SHA-256（慢動作放交易外，不長時間鎖住主檔列）→
//           交易內：FOR UPDATE 鎖主檔、再檢查一次（DRAFT、申請人、檔數）、寫 IM_ATTACH、搬到 <root>/<APP_ID>/<uuid>.<ext>。
//           任何一步失敗（含 commit 失敗）都刪掉暫存檔與已搬好的檔；交易沒成功就不留實體檔。
//           原始檔名：去掉路徑、控制字元與格式字元（含 RTL 覆寫等看不見的字元）、頭尾空白，超過 255 字時保留副檔名截斷。
//           MIME 由副檔名決定（AttachmentTypes），不信任瀏覽器給的 Content-Type。
//           log 只記單號、ATTACH_ID、例外類別，不記檔名與路徑
// ============================================================

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.mpx.infra_manager_java.dao.changerequest.AttachDao;
import com.mpx.infra_manager_java.model.auth.AuthUser;
import com.mpx.infra_manager_java.model.changerequest.AppDetail;
import com.mpx.infra_manager_java.model.changerequest.AppLockRow;
import com.mpx.infra_manager_java.model.changerequest.AttachRow;
import com.mpx.infra_manager_java.service.sysparam.SysParamService;
import com.mpx.infra_manager_java.util.TaiwanTime;
import com.mpx.infra_manager_java.web.ApiBadRequestException;
import com.mpx.infra_manager_java.web.ApiConflictException;
import com.mpx.infra_manager_java.web.ApiNotFoundException;

@Service
public class AttachmentUploadService {

	public static final String MSG_NO_FILE = "請選擇要上傳的檔案";
	public static final String MSG_EMPTY_FILE = "檔案是空的，請確認後再上傳";
	public static final String MSG_TYPE_NOT_ALLOWED = "不支援的檔案類型";
	public static final String MSG_NOT_DRAFT = "申請單已不是草稿，無法上傳附件，請重新載入頁面";
	public static final String MSG_NOT_APPLICANT = "只有申請人可以上傳附件";

	/** 暫存子目錄（與正式檔同一個根目錄，搬移才會是同一磁碟內的改名） */
	static final String TMP_DIR = ".tmp";
	/** ORIG_FILE_NAME 欄寬（CHAR 語意，以 code point 計） */
	static final int MAX_NAME_LENGTH = 255;
	static final long MB = 1024L * 1024;

	private static final Pattern APP_ID = Pattern.compile("^[A-Z0-9-]{1,20}$");
	private static final Logger log = LoggerFactory.getLogger(AttachmentUploadService.class);

	private final AttachDao attachDao;
	private final SysParamService sysParamService;
	private final TransactionTemplate transactionTemplate;
	private final String attachRoot;

	public AttachmentUploadService(AttachDao attachDao, SysParamService sysParamService,
			PlatformTransactionManager transactionManager, @Value("${im.attach.root:}") String attachRoot) {
		this.attachDao = attachDao;
		this.sysParamService = sysParamService;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.attachRoot = attachRoot;
	}

	public AppDetail.Attachment upload(String appId, MultipartFile file, AuthUser user) {
		if (appId == null || !APP_ID.matcher(appId).matches()) {
			throw new ApiNotFoundException(AppQueryService.MSG_APP_NOT_FOUND);
		}
		if (file == null) {
			throw new ApiBadRequestException(MSG_NO_FILE);
		}
		String origName = cleanFileName(file.getOriginalFilename());
		if (!AttachmentTypes.isAllowed(origName)) {
			throw new ApiBadRequestException(MSG_TYPE_NOT_ALLOWED);
		}
		int maxMb = sysParamService.uploadMaxMb();
		long maxBytes = maxMb * MB;
		if (file.getSize() > maxBytes) {
			throw new ApiBadRequestException(tooLargeMessage(maxMb));
		}
		if (file.getSize() <= 0) {
			throw new ApiBadRequestException(MSG_EMPTY_FILE);
		}
		int maxFiles = sysParamService.uploadMaxFiles();
		check(appId, attachDao.findAppState(appId), user, maxFiles);

		Path root = root();
		String ext = AttachmentTypes.extension(origName);
		String storeName = UUID.randomUUID() + "." + ext;
		String filePath = appId + "/" + storeName;
		Path target = root.resolve(appId).resolve(storeName).normalize();
		if (!target.startsWith(root)) {
			// 單號已限定 [A-Z0-9-]、檔名由 UUID 產生，正常不會發生；保留這道檢查與下載端一致
			throw new IllegalStateException("附件路徑逸出根目錄");
		}
		Path temp = root.resolve(TMP_DIR).resolve(UUID.randomUUID() + ".part");

		boolean committed = false;
		try {
			Stored stored = copyToTemp(file, temp, maxBytes, maxMb);
			AttachRow row = transactionTemplate.execute(status -> {
				check(appId, attachDao.lockApp(appId), user, maxFiles);
				AttachRow inserted = attachDao.insertAppFile(appId, origName, storeName, filePath, stored.bytes(),
						AttachmentTypes.mimeFor(storeName), stored.sha256(), user.userId());
				move(temp, target);
				return inserted;
			});
			committed = true;
			log.info("附件上傳 appId={} attachId={} bytes={} user={}", appId, row.getAttachId(), stored.bytes(),
					user.userId());
			return new AppDetail.Attachment(row.getAttachId(), row.getOwnerType(), row.getOwnerId(),
					row.getOrigFileName(), row.getFileByteQty(), row.getMimeType(),
					TaiwanTime.formatDateTime(row.getCreateDate()));
		} finally {
			deleteQuietly(temp, appId);
			if (!committed) {
				deleteQuietly(target, appId);
			}
		}
	}

	/** DRAFT、申請人、檔數；state 為 null 表示單不存在或已刪除 */
	private void check(String appId, AppLockRow state, AuthUser user, int maxFiles) {
		if (state == null) {
			throw new ApiNotFoundException(AppQueryService.MSG_APP_NOT_FOUND);
		}
		if (!user.userId().equals(state.getApplyUserId())) {
			throw new AccessDeniedException(MSG_NOT_APPLICANT);
		}
		if (!"DRAFT".equals(state.getAppStatusCode())) {
			throw new ApiConflictException(MSG_NOT_DRAFT);
		}
		if (attachDao.countAppFiles(appId) >= maxFiles) {
			throw new ApiBadRequestException(tooManyMessage(maxFiles));
		}
	}

	private Path root() {
		if (attachRoot == null || attachRoot.isBlank()) {
			log.error("附件根目錄 im.attach.root 未設定，無法上傳");
			throw new IllegalStateException("im.attach.root not configured");
		}
		return Paths.get(attachRoot).toAbsolutePath().normalize();
	}

	private record Stored(long bytes, String sha256) {
	}

	/** 複製到暫存檔並算 SHA-256；實際讀到的長度超過上限（宣告的 size 與內容不符）也擋 */
	private static Stored copyToTemp(MultipartFile file, Path temp, long maxBytes, int maxMb) {
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("JVM 不支援 SHA-256", e);
		}
		long total = 0;
		try {
			Files.createDirectories(temp.getParent());
			try (InputStream in = file.getInputStream();
					OutputStream out = new DigestOutputStream(Files.newOutputStream(temp), digest)) {
				byte[] buf = new byte[64 * 1024];
				int n;
				while ((n = in.read(buf)) > 0) {
					total += n;
					if (total > maxBytes) {
						throw new ApiBadRequestException(tooLargeMessage(maxMb));
					}
					out.write(buf, 0, n);
				}
			}
		} catch (IOException e) {
			throw new IllegalStateException("附件寫入暫存檔失敗", e);
		}
		if (total == 0) {
			throw new ApiBadRequestException(MSG_EMPTY_FILE);
		}
		return new Stored(total, HexFormat.of().formatHex(digest.digest()));
	}

	private static void move(Path temp, Path target) {
		try {
			Files.createDirectories(target.getParent());
			try {
				Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, target);
			}
		} catch (IOException e) {
			throw new IllegalStateException("附件搬移到正式目錄失敗", e);
		}
	}

	private static void deleteQuietly(Path path, String appId) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException e) {
			log.warn("附件殘留檔刪除失敗，需人工清理 appId={}: {}", appId, e.getClass().getSimpleName());
		}
	}

	/**
	 * 原始檔名清理：只留最後一段（瀏覽器可能帶 C:\fakepath\ 之類的路徑）、去掉控制字元與格式字元
	 * （含 U+202E 這類會讓檔名顯示成另一個副檔名的字元）、孤立的代理字元、頭尾空白；
	 * 超過 {@value #MAX_NAME_LENGTH} 個字時截斷主檔名、保留副檔名
	 */
	static String cleanFileName(String raw) {
		if (raw == null) {
			return "";
		}
		int cut = Math.max(raw.lastIndexOf('/'), raw.lastIndexOf('\\'));
		StringBuilder sb = new StringBuilder();
		raw.substring(cut + 1).codePoints().filter(cp -> {
			int type = Character.getType(cp);
			return type != Character.CONTROL && type != Character.FORMAT && type != Character.SURROGATE;
		}).forEach(sb::appendCodePoint);
		String name = sb.toString().strip();
		if (name.codePointCount(0, name.length()) <= MAX_NAME_LENGTH) {
			return name;
		}
		String ext = AttachmentTypes.extension(name);
		String suffix = ext == null ? "" : "." + ext;
		String base = ext == null ? name : name.substring(0, name.length() - suffix.length());
		int keep = MAX_NAME_LENGTH - suffix.codePointCount(0, suffix.length());
		if (keep <= 0) {
			// 副檔名本身就超長，必定不在白名單；照長度截斷即可，之後會被白名單擋下
			return name.substring(0, name.offsetByCodePoints(0, MAX_NAME_LENGTH));
		}
		return base.substring(0, base.offsetByCodePoints(0, keep)).strip() + suffix;
	}

	static String tooLargeMessage(int maxMb) {
		return "檔案超過 " + maxMb + " MB 上限";
	}

	static String tooManyMessage(int maxFiles) {
		return "附件已達 " + maxFiles + " 個上限";
	}
}
