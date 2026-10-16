package com.mpx.im_acct_insp.service;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-11
// 變更說明: 新增：通知信寄送名單（BACKLOG 第 25 項）。im.acct.insp.mail.allow-list 為逗號分隔的工號（半形「,」與全形「，」都認）；
//           留空＝誰都不寄；im.acct.insp.mail.send-to-all＝true 時忽略名單、寄給所有收件人。
//           名單外的收件人由 NotificationService 記 SKIP（ERR_MSG「不在寄送名單」）。
//           測試環境的 DB 有同事的真實 Email，預設值刻意是「誰都不寄」
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 1：設定鍵改為 infra.manager.mail.allow-list、infra.manager.mail.send-to-all（專案改名 infra_manager）
// ============================================================

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MailAllowList {

	private final Set<String> userIds;
	private final boolean sendToAll;

	public MailAllowList(@Value("${infra.manager.mail.allow-list:}") String allowList,
			@Value("${infra.manager.mail.send-to-all:false}") boolean sendToAll) {
		this.userIds = allowList == null ? Set.of()
				: Arrays.stream(allowList.split("[,，]")).map(String::trim).filter(s -> !s.isEmpty())
						.collect(Collectors.toUnmodifiableSet());
		this.sendToAll = sendToAll;
	}

	/** 這位收件人可不可以寄 */
	public boolean allows(String userId) {
		return sendToAll || (userId != null && userIds.contains(userId));
	}

	public boolean isSendToAll() {
		return sendToAll;
	}

	public int size() {
		return userIds.size();
	}
}
