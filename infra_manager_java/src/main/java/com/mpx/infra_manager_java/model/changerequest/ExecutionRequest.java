package com.mpx.infra_manager_java.model.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：填寫執行紀錄的請求本文（S10 R1，施工計畫 ①）。PUT /api/apps/{id}/execution：
//           rowVerNo 樂觀鎖（必填）；checklist 只帶要更新的檢核項（沒帶到的列維持原值）；
//           resultCode 有值＝完成並送治理審查，空＝暫存（停在執行中）。日期收 yyyy-MM-dd HH:mm 或 T 分隔
// ============================================================

import java.util.List;

public record ExecutionRequest(Long rowVerNo, List<CheckItem> checklist, String actualStart, String actualEnd,
		String resultCode, Boolean exception, String exceptionDesc, Boolean followUp, String followUpDesc,
		String memo) {

	/** 一個檢核項：userId（系統使用者工號）與 executorDesc（自由文字）至多擇一 */
	public record CheckItem(Integer seqNo, Boolean done, String doneAt, String userId, String executorDesc) {
	}
}
