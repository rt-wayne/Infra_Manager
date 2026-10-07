package com.mpx.infra_manager_java.service.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：執行紀錄的檢核與正規化（S10 R1，施工計畫 ③④⑥）。純函式，不碰 DB；分兩級：
//           暫存（resultCode 空）只檢格式——日期格式、兩個時間都有時結束不早於開始、文字長度（說明與備註 2000、執行人 200）、
//           結果代碼若有值須在啟用清單內、檢核項序號必填不重複、執行人工號與文字描述至多擇一；
//           送治理審查（resultCode 有值）另檢必填：實際開始／結束時間，勾有異常時異常說明、勾需後續追蹤時追蹤說明，
//           一次列出全部缺漏（400「送治理審查前請先補齊：…」，同送審 AppSubmitValidator）。不要求檢核項全勾（③）。
//           done=false 的檢核項丟掉完成時間（修舊系統未完成卻留時間）；沒勾異常／追蹤時對應說明存 null
// ============================================================

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mpx.infra_manager_java.model.changerequest.ExecutionDraft;
import com.mpx.infra_manager_java.model.changerequest.ExecutionRequest;
import com.mpx.infra_manager_java.util.TextLength;
import com.mpx.infra_manager_java.web.ApiBadRequestException;

public final class ExecutionValidator {

	public static final String MSG_SUBMIT_PREFIX = "送治理審查前請先補齊：";
	public static final String MSG_BAD_SEQ = "檢核項序號不正確";
	public static final String MSG_EXECUTOR_BOTH = "檢核項執行人請擇一填寫系統使用者或文字描述";
	public static final String MSG_END_BEFORE_START = "實際結束時間不得早於實際開始時間";
	public static final String MSG_BAD_RESULT = "執行結果不正確";
	/** EXEC_USER_DESC 是 VARCHAR2(200 CHAR) */
	static final int EXECUTOR_DESC_MAX = 200;

	private ExecutionValidator() {
	}

	/**
	 * @param resultCodes 啟用中的 EXEC_RESULT 選項代碼
	 */
	public static ExecutionDraft validate(ExecutionRequest req, Set<String> resultCodes) {
		List<ExecutionDraft.CheckItem> checklist = checklist(req.checklist());
		Timestamp start = AppDraftValidator.dateTime(req.actualStart(), "實際開始時間");
		Timestamp end = AppDraftValidator.dateTime(req.actualEnd(), "實際結束時間");
		if (start != null && end != null && end.before(start)) {
			throw new ApiBadRequestException(MSG_END_BEFORE_START);
		}
		String resultCode = req.resultCode() == null || req.resultCode().isBlank() ? null : req.resultCode().strip();
		if (resultCode != null && !resultCodes.contains(resultCode)) {
			throw new ApiBadRequestException(MSG_BAD_RESULT);
		}
		boolean exception = Boolean.TRUE.equals(req.exception());
		boolean followUp = Boolean.TRUE.equals(req.followUp());
		String exceptionDesc = AppDraftValidator.longText("exceptionDesc", "異常說明", req.exceptionDesc(),
				TextLength.LIMIT_SHORT);
		String followUpDesc = AppDraftValidator.longText("followUpDesc", "後續追蹤說明", req.followUpDesc(),
				TextLength.LIMIT_SHORT);
		String memo = AppDraftValidator.longText("memo", "執行備註", req.memo(), TextLength.LIMIT_SHORT);

		ExecutionDraft draft = new ExecutionDraft(checklist, start, end, resultCode, exception,
				exception ? exceptionDesc : null, followUp, followUp ? followUpDesc : null, memo);
		if (draft.submit()) {
			List<String> m = missing(draft);
			if (!m.isEmpty()) {
				throw new ApiBadRequestException(MSG_SUBMIT_PREFIX + String.join("、", m));
			}
		}
		return draft;
	}

	/** 送治理審查的缺漏欄位中文名稱（依畫面順序）；空清單表示可以送 */
	static List<String> missing(ExecutionDraft d) {
		List<String> m = new ArrayList<>();
		if (d.actualStart() == null) {
			m.add("實際開始時間");
		}
		if (d.actualEnd() == null) {
			m.add("實際結束時間");
		}
		if (d.exception() && d.exceptionDesc() == null) {
			m.add("異常說明");
		}
		if (d.followUp() && d.followUpDesc() == null) {
			m.add("後續追蹤說明");
		}
		return m;
	}

	private static List<ExecutionDraft.CheckItem> checklist(List<ExecutionRequest.CheckItem> items) {
		if (items == null) {
			return List.of();
		}
		List<ExecutionDraft.CheckItem> out = new ArrayList<>();
		Set<Integer> seen = new HashSet<>();
		for (ExecutionRequest.CheckItem item : items) {
			if (item == null || item.seqNo() == null || !seen.add(item.seqNo())) {
				throw new ApiBadRequestException(MSG_BAD_SEQ);
			}
			boolean done = Boolean.TRUE.equals(item.done());
			Timestamp doneAt = AppDraftValidator.dateTime(item.doneAt(), "檢核項完成時間");
			String userId = AppDraftValidator.text("userId", "檢核項執行人", item.userId(), Integer.MAX_VALUE);
			String desc = AppDraftValidator.text("executorDesc", "檢核項執行人", item.executorDesc(),
					EXECUTOR_DESC_MAX);
			if (userId != null && desc != null) {
				throw new ApiBadRequestException(MSG_EXECUTOR_BOTH);
			}
			out.add(new ExecutionDraft.CheckItem(item.seqNo(), done, done ? doneAt : null, userId, desc));
		}
		return out;
	}
}
