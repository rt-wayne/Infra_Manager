package com.mpx.infra_manager_java.model.mail;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-08
// 變更說明: 新增：要寫進 IM_MAIL_OUTBOX 的一封信（S8 R1）。
//           收件人以 email 字串清單表示（cc／bcc 可空）；meta 是追查用的關聯資訊（事件類型、單號等），
//           由 MailOutboxService 序列化成 META_JSON；createdBy 為觸發寄信的人員工號
// ============================================================

import java.util.List;
import java.util.Map;

public record MailMessage(
		List<String> to,
		List<String> cc,
		List<String> bcc,
		String subject,
		String htmlBody,
		Map<String, Object> meta,
		String createdBy) {

	/** 只有收件人、沒有副本的常見情形 */
	public static MailMessage of(List<String> to, String subject, String htmlBody, Map<String, Object> meta,
			String createdBy) {
		return new MailMessage(to, List.of(), List.of(), subject, htmlBody, meta, createdBy);
	}
}
