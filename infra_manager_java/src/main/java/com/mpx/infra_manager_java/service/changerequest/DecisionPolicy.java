package com.mpx.infra_manager_java.service.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-07
// 變更說明: 新增：簽核決定的純函式（S7 R2），不碰 DB：決定值檢核、意見正規化（退件必填、同意空白填「同意」、≤ 2000 字）、
//           目前關卡（序號最小的 PENDING）、是否為該關簽核人（沿用 AppPermissionService.isCurrentApprover：
//           候選人優先、無候選人看流程指定人）。放在 changerequest 套件以共用 package-private 的 isCurrentApprover
// ============================================================

import java.util.Comparator;
import java.util.List;

import com.mpx.infra_manager_java.model.changerequest.ApprStepRow;
import com.mpx.infra_manager_java.model.changerequest.CandRow;
import com.mpx.infra_manager_java.util.TextLength;
import com.mpx.infra_manager_java.web.ApiBadRequestException;

public final class DecisionPolicy {

	public static final String APPROVE = "APPROVE";
	public static final String REJECT = "REJECT";
	public static final String DEFAULT_APPROVE_MEMO = "同意";
	public static final String MSG_BAD_DECISION = "簽核決定只能是同意或退件";
	public static final String MSG_REJECT_NEEDS_MEMO = "退件請填寫意見";

	private DecisionPolicy() {
	}

	/** decision 不是 APPROVE／REJECT 丟 400 */
	public static String requireDecision(String decision) {
		if (!APPROVE.equals(decision) && !REJECT.equals(decision)) {
			throw new ApiBadRequestException(MSG_BAD_DECISION);
		}
		return decision;
	}

	/** 意見正規化：超過 2000 字 400；退件空白 400；同意空白填「同意」 */
	public static String normalizeMemo(String decision, String memo) {
		String n = TextLength.check("memo", "簽核意見", memo, TextLength.LIMIT_SHORT);
		boolean blank = n == null || n.isBlank();
		if (REJECT.equals(decision) && blank) {
			throw new ApiBadRequestException(MSG_REJECT_NEEDS_MEMO);
		}
		return blank ? DEFAULT_APPROVE_MEMO : n.strip();
	}

	/** 目前關卡＝序號最小且有實例列的 PENDING 關卡；沒有回 null */
	public static ApprStepRow currentStep(List<ApprStepRow> steps) {
		return steps.stream().filter(s -> "PENDING".equals(s.getStepStatusCode())).filter(s -> s.getApprStepId() != null)
				.min(Comparator.comparing(ApprStepRow::getSeqNo, Comparator.nullsLast(Comparator.naturalOrder())))
				.orElse(null);
	}

	/** 是否為目前關卡的簽核人（候選人優先，無候選人看流程指定人） */
	public static boolean isApprover(List<ApprStepRow> steps, List<CandRow> cands, String userId) {
		return AppPermissionService.isCurrentApprover(steps, cands, userId);
	}

	/** 決定對應的關卡狀態 */
	public static String stepStatus(String decision) {
		return APPROVE.equals(decision) ? "APPROVED" : "REJECTED";
	}
}
