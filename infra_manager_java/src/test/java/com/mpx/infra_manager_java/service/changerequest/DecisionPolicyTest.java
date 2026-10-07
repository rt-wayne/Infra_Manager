package com.mpx.infra_manager_java.service.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-07
// 變更說明: 新增：簽核決定純函式的單元測試（S7 R2）。鎖定：決定值只收 APPROVE／REJECT；同意空白填「同意」、
//           退件空白 400、超過 2000 字丟 TextTooLongException、CRLF 正規化；目前關卡取序號最小的 PENDING
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.mpx.infra_manager_java.model.changerequest.ApprStepRow;
import com.mpx.infra_manager_java.util.TextTooLongException;
import com.mpx.infra_manager_java.web.ApiBadRequestException;

class DecisionPolicyTest {

	private static ApprStepRow step(Long id, int seq, String status) {
		ApprStepRow s = new ApprStepRow();
		s.setApprStepId(id);
		s.setSeqNo(seq);
		s.setStepStatusCode(status);
		return s;
	}

	@Test
	void 決定值只收同意與退件() {
		assertThat(DecisionPolicy.requireDecision("APPROVE")).isEqualTo("APPROVE");
		assertThat(DecisionPolicy.requireDecision("REJECT")).isEqualTo("REJECT");
		assertThatThrownBy(() -> DecisionPolicy.requireDecision("approve")).isInstanceOf(ApiBadRequestException.class);
		assertThatThrownBy(() -> DecisionPolicy.requireDecision(null)).isInstanceOf(ApiBadRequestException.class);
		assertThat(DecisionPolicy.stepStatus("APPROVE")).isEqualTo("APPROVED");
		assertThat(DecisionPolicy.stepStatus("REJECT")).isEqualTo("REJECTED");
	}

	@Test
	void 意見正規化() {
		assertThat(DecisionPolicy.normalizeMemo("APPROVE", null)).isEqualTo("同意");
		assertThat(DecisionPolicy.normalizeMemo("APPROVE", "  ")).isEqualTo("同意");
		assertThat(DecisionPolicy.normalizeMemo("APPROVE", " 可以\r\n進行 ")).isEqualTo("可以\n進行");
		assertThatThrownBy(() -> DecisionPolicy.normalizeMemo("REJECT", " ")).isInstanceOf(ApiBadRequestException.class)
				.hasMessage(DecisionPolicy.MSG_REJECT_NEEDS_MEMO);
		assertThat(DecisionPolicy.normalizeMemo("REJECT", "缺附件")).isEqualTo("缺附件");
		assertThatThrownBy(() -> DecisionPolicy.normalizeMemo("APPROVE", "字".repeat(2001)))
				.isInstanceOf(TextTooLongException.class);
	}

	@Test
	void 目前關卡是序號最小的PENDING_無實例列或沒有PENDING回null() {
		List<ApprStepRow> steps = List.of(step(3L, 3, "WAITING"), step(2L, 2, "PENDING"), step(1L, 1, "APPROVED"));
		assertThat(DecisionPolicy.currentStep(steps).getApprStepId()).isEqualTo(2L);
		assertThat(DecisionPolicy.currentStep(List.of(step(1L, 1, "APPROVED"), step(2L, 2, "WAITING")))).isNull();
		assertThat(DecisionPolicy.currentStep(List.of(step(null, 1, "PENDING")))).isNull();
		assertThat(DecisionPolicy.currentStep(List.of())).isNull();
	}
}
