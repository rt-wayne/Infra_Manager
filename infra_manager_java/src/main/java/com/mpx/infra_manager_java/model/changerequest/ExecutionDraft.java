package com.mpx.infra_manager_java.model.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：ExecutionValidator 檢核並正規化後的執行紀錄（S10 R1）。文字已 trim／統一換行、空白轉 null，
//           日期已解析；done=false 的檢核項 doneAt 一律 null；沒勾「有異常」「需後續追蹤」時對應說明一律 null
// ============================================================

import java.sql.Timestamp;
import java.util.List;

public record ExecutionDraft(List<CheckItem> checklist, Timestamp actualStart, Timestamp actualEnd, String resultCode,
		boolean exception, String exceptionDesc, boolean followUp, String followUpDesc, String memo) {

	public ExecutionDraft {
		checklist = checklist == null ? List.of() : List.copyOf(checklist);
	}

	/** 有結果＝完成並送治理審查；沒有＝暫存 */
	public boolean submit() {
		return resultCode != null;
	}

	/** doneAt 為 null 且 done=true 時由服務補伺服器現在時間 */
	public record CheckItem(int seqNo, boolean done, Timestamp doneAt, String userId, String executorDesc) {
	}
}
