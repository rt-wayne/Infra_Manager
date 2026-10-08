package com.mpx.infra_manager_java.service.mail;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-08
// 變更說明: 新增：把一封信寫進 IM_MAIL_OUTBOX（S8 R1）。
//           刻意不掛 @Transactional：enqueue 在呼叫端的業務交易內執行，與業務一起 commit／rollback；
//           若本方法有自己的交易代理，呼叫端 catch 住例外時外層交易仍會被標成 rollback-only。
//           處理：收件人去空白、不分大小寫去重、格式不對的略過、cc／bcc 不重複 to；主旨去 CR／LF（防標頭注入）、截 500 字；
//           im.mail.override-to 有值時全部改寄到該地址、主旨加「[測試改寄]」、原收件人記進 META_JSON。
//           一個收件人都沒有時不寫入、回 false、只記 info（事件類型＋單號）。
//           log 不記 email 地址（視同個資），只記 outbox 相關的事件類型、單號、收件人數
// ============================================================

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.mpx.infra_manager_java.config.MailProperties;
import com.mpx.infra_manager_java.dao.mail.MailOutboxDao;
import com.mpx.infra_manager_java.model.mail.MailMessage;

import tools.jackson.databind.json.JsonMapper;

@Service
public class MailOutboxService {

	/** MAIL_SUBJ VARCHAR2(500 CHAR) */
	static final int SUBJECT_MAX = 500;
	static final String OVERRIDE_PREFIX = "[測試改寄] ";
	static final String EMPTY_SUBJECT = "(無主旨)";
	/** 寬鬆的 email 形狀檢查：不含空白、角括號、逗號、分號、引號，有 @ 與一個點 */
	private static final Pattern EMAIL = Pattern.compile("^[^\\s@<>,;\"]+@[^\\s@<>,;\"]+\\.[^\\s@<>,;\"]+$");
	private static final Pattern SUBJECT_BREAKS = Pattern.compile("[\\r\\n\\t\\u0000]+");

	private static final Logger log = LoggerFactory.getLogger(MailOutboxService.class);

	/** 專屬 JsonMapper：存進 DB 的格式不受全域 ObjectMapper 設定影響 */
	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private final MailOutboxDao outboxDao;
	private final MailProperties properties;

	public MailOutboxService(MailOutboxDao outboxDao, MailProperties properties) {
		this.outboxDao = outboxDao;
		this.properties = properties;
	}

	/**
	 * 寫入一封待寄信。回 true＝已寫入；false＝沒有任何有效收件人、未寫入。
	 * 收件人與主旨先正規化；INSERT 失敗的例外原樣往外拋（跟業務一起 rollback）
	 */
	public boolean enqueue(MailMessage message) {
		if (message == null) {
			throw new IllegalArgumentException("message 不得為 null");
		}
		if (message.htmlBody() == null || message.htmlBody().isBlank()) {
			throw new IllegalArgumentException("信件內容不得為空");
		}
		Map<String, Object> meta = new LinkedHashMap<>();
		if (message.meta() != null) {
			meta.putAll(message.meta());
		}
		String event = String.valueOf(meta.getOrDefault("event", "-"));
		String appId = String.valueOf(meta.getOrDefault("appId", "-"));

		List<String> to = normalize(message.to(), Set.of());
		List<String> cc = normalize(message.cc(), lower(to));
		Set<String> seen = lower(to);
		seen.addAll(lower(cc));
		List<String> bcc = normalize(message.bcc(), seen);
		if (to.isEmpty() && cc.isEmpty() && bcc.isEmpty()) {
			log.info("信件沒有有效收件人、不寫入 outbox：event={} appId={}", event, appId);
			return false;
		}

		String subject = normalizeSubject(message.subject());
		if (properties.hasOverrideTo()) {
			meta.put("originalTo", to);
			meta.put("originalCc", cc);
			meta.put("originalBcc", bcc);
			to = List.of(properties.getOverrideTo());
			cc = List.of();
			bcc = List.of();
			subject = truncate(OVERRIDE_PREFIX + subject, SUBJECT_MAX);
		}

		String createdBy = message.createdBy() == null || message.createdBy().isBlank() ? MailOutboxDao.SYSTEM_USER
				: message.createdBy().trim();
		outboxDao.insert(json(to), cc.isEmpty() ? null : json(cc), bcc.isEmpty() ? null : json(bcc), subject,
				message.htmlBody(), meta.isEmpty() ? null : json(meta), createdBy);
		log.info("信件已寫入 outbox：event={} appId={} to={} cc={} bcc={}", event, appId, to.size(), cc.size(),
				bcc.size());
		return true;
	}

	/** 去空白、略過格式不對與已出現（不分大小寫）的地址；保留原大小寫與順序 */
	static List<String> normalize(List<String> raw, Set<String> alreadyLower) {
		List<String> out = new ArrayList<>();
		Set<String> seen = new TreeSet<>(alreadyLower);
		if (raw == null) {
			return out;
		}
		for (String r : raw) {
			if (r == null) {
				continue;
			}
			String addr = r.trim();
			if (addr.isEmpty() || !EMAIL.matcher(addr).matches()) {
				continue;
			}
			if (seen.add(addr.toLowerCase(Locale.ROOT))) {
				out.add(addr);
			}
		}
		return out;
	}

	/** 主旨：CR／LF／Tab 換成空白（防郵件標頭注入）、去頭尾空白、空的補預設、截到 500 字 */
	static String normalizeSubject(String subject) {
		String s = subject == null ? "" : SUBJECT_BREAKS.matcher(subject).replaceAll(" ").trim();
		if (s.isEmpty()) {
			s = EMPTY_SUBJECT;
		}
		return truncate(s, SUBJECT_MAX);
	}

	private static Set<String> lower(List<String> addrs) {
		Set<String> s = new TreeSet<>();
		for (String a : addrs) {
			s.add(a.toLowerCase(Locale.ROOT));
		}
		return s;
	}

	private static String truncate(String s, int max) {
		return s.length() <= max ? s : s.substring(0, max);
	}

	private static String json(Object value) {
		return MAPPER.writeValueAsString(value);
	}
}
