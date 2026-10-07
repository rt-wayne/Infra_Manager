package com.mpx.infra_manager_java.service.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：ExecutionValidator 單元測試（S10 R1，施工計畫 ③④⑥）。鎖定：暫存只檢格式（全空也可以存）；
//           兩種日期分隔都收、格式錯 400；結束早於開始 400、相等可以；結果代碼不在清單 400、空白視為暫存；
//           文字超長 400（說明與備註 2000、執行人 200）；檢核項序號缺或重複 400、執行人兩欄都給 400、done=false 丟時間；
//           沒勾異常／追蹤時說明存 null；送審缺漏一次列全且依畫面順序；送審不要求檢核項全勾
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.mpx.infra_manager_java.model.changerequest.ExecutionDraft;
import com.mpx.infra_manager_java.model.changerequest.ExecutionRequest;
import com.mpx.infra_manager_java.util.TextTooLongException;
import com.mpx.infra_manager_java.web.ApiBadRequestException;

class ExecutionValidatorTest {

	private static final Set<String> RESULTS = Set.of("DONE", "DONE_ADJ", "PARTIAL", "NOT_DONE", "CANCEL");

	private static ExecutionRequest req(List<ExecutionRequest.CheckItem> items, String start, String end, String result,
			Boolean exception, String exceptionDesc, Boolean followUp, String followUpDesc, String memo) {
		return new ExecutionRequest(1L, items, start, end, result, exception, exceptionDesc, followUp, followUpDesc,
				memo);
	}

	private static ExecutionRequest times(String start, String end, String result) {
		return req(null, start, end, result, null, null, null, null, null);
	}

	private static ExecutionRequest.CheckItem item(Integer seq, Boolean done, String at, String userId, String desc) {
		return new ExecutionRequest.CheckItem(seq, done, at, userId, desc);
	}

	@Test
	void 暫存全空也可以存() {
		ExecutionDraft d = ExecutionValidator.validate(times(null, null, null), RESULTS);
		assertThat(d.submit()).isFalse();
		assertThat(d.checklist()).isEmpty();
		assertThat(d.actualStart()).isNull();
		assertThat(d.exception()).isFalse();
		assertThat(d.followUp()).isFalse();
		assertThat(ExecutionValidator.validate(times(null, null, "   "), RESULTS).submit()).as("結果空白視為暫存").isFalse();
	}

	@Test
	void 日期兩種分隔都收_格式錯400_結束早於開始400() {
		ExecutionDraft d = ExecutionValidator.validate(times("2026-10-07 09:00", "2026-10-07T09:00", null), RESULTS);
		assertThat(d.actualStart()).isEqualTo(Timestamp.valueOf("2026-10-07 09:00:00"));
		assertThat(d.actualEnd()).as("相等可以").isEqualTo(d.actualStart());

		assertThatThrownBy(() -> ExecutionValidator.validate(times("2026/10/07 09:00", null, null), RESULTS))
				.isInstanceOf(ApiBadRequestException.class).hasMessage("實際開始時間格式不正確");
		assertThatThrownBy(() -> ExecutionValidator.validate(times(null, "明天", null), RESULTS))
				.isInstanceOf(ApiBadRequestException.class).hasMessage("實際結束時間格式不正確");
		assertThatThrownBy(() -> ExecutionValidator.validate(times("2026-10-07 09:00", "2026-10-07 08:59", null), RESULTS))
				.isInstanceOf(ApiBadRequestException.class).hasMessage(ExecutionValidator.MSG_END_BEFORE_START);
	}

	@Test
	void 結果不在清單400() {
		for (String bad : new String[] { "done", "OK", "EXECUTED" }) {
			assertThatThrownBy(() -> ExecutionValidator.validate(times("2026-10-07 09:00", "2026-10-07 10:00", bad),
					RESULTS)).as(bad).isInstanceOf(ApiBadRequestException.class).hasMessage(ExecutionValidator.MSG_BAD_RESULT);
		}
		assertThat(ExecutionValidator.validate(times("2026-10-07 09:00", "2026-10-07 10:00", " PARTIAL "), RESULTS)
				.resultCode()).isEqualTo("PARTIAL");
	}

	@Test
	void 文字超長400() {
		String over = "字".repeat(2001);
		assertThatThrownBy(() -> ExecutionValidator.validate(req(null, null, null, null, true, over, null, null, null),
				RESULTS)).isInstanceOf(TextTooLongException.class);
		assertThatThrownBy(() -> ExecutionValidator.validate(req(null, null, null, null, null, null, true, over, null),
				RESULTS)).isInstanceOf(TextTooLongException.class);
		assertThatThrownBy(() -> ExecutionValidator.validate(req(null, null, null, null, null, null, null, null, over),
				RESULTS)).isInstanceOf(TextTooLongException.class);
		assertThatThrownBy(() -> ExecutionValidator.validate(
				req(List.of(item(1, true, null, null, "人".repeat(201))), null, null, null, null, null, null, null, null),
				RESULTS)).isInstanceOf(TextTooLongException.class);
		ExecutionDraft ok = ExecutionValidator.validate(
				req(List.of(item(1, true, null, null, "人".repeat(200))), null, null, null, null, null, null, null,
						"字".repeat(2000)),
				RESULTS);
		assertThat(ok.memo()).hasSize(2000);
	}

	@Test
	void 檢核項序號缺或重複400_執行人兩欄都給400() {
		assertThatThrownBy(() -> ExecutionValidator.validate(
				req(List.of(item(null, true, null, null, null)), null, null, null, null, null, null, null, null), RESULTS))
				.isInstanceOf(ApiBadRequestException.class).hasMessage(ExecutionValidator.MSG_BAD_SEQ);
		assertThatThrownBy(() -> ExecutionValidator.validate(
				req(List.of(item(1, true, null, null, null), item(1, false, null, null, null)), null, null, null, null,
						null, null, null, null),
				RESULTS)).isInstanceOf(ApiBadRequestException.class).hasMessage(ExecutionValidator.MSG_BAD_SEQ);
		assertThatThrownBy(() -> ExecutionValidator.validate(
				req(Arrays.asList((ExecutionRequest.CheckItem) null), null, null, null, null, null, null, null, null),
				RESULTS)).isInstanceOf(ApiBadRequestException.class).hasMessage(ExecutionValidator.MSG_BAD_SEQ);
		assertThatThrownBy(() -> ExecutionValidator.validate(
				req(List.of(item(1, true, null, "T0001", "廠商")), null, null, null, null, null, null, null, null),
				RESULTS)).isInstanceOf(ApiBadRequestException.class).hasMessage(ExecutionValidator.MSG_EXECUTOR_BOTH);
		assertThatThrownBy(() -> ExecutionValidator.validate(
				req(List.of(item(1, true, "昨天", null, null)), null, null, null, null, null, null, null, null), RESULTS))
				.isInstanceOf(ApiBadRequestException.class).hasMessage("檢核項完成時間格式不正確");
	}

	@Test
	void 檢核項正規化_未完成丟時間_空白轉null() {
		ExecutionDraft d = ExecutionValidator.validate(req(List.of(item(1, true, "2026-10-07 09:30", " T0001 ", "  "),
				item(2, false, "2026-10-07 09:40", "", " 門市EDP "), item(3, null, null, null, null)), null, null, null,
				null, null, null, null, null), RESULTS);
		assertThat(d.checklist()).containsExactly(
				new ExecutionDraft.CheckItem(1, true, Timestamp.valueOf("2026-10-07 09:30:00"), "T0001", null),
				new ExecutionDraft.CheckItem(2, false, null, null, "門市EDP"),
				new ExecutionDraft.CheckItem(3, false, null, null, null));
	}

	@Test
	void 沒勾異常與追蹤時說明存null_勾了才保留() {
		ExecutionDraft off = ExecutionValidator.validate(
				req(null, null, null, null, false, "有寫但沒勾", null, "也沒勾", "  "), RESULTS);
		assertThat(off.exceptionDesc()).isNull();
		assertThat(off.followUpDesc()).isNull();
		assertThat(off.memo()).as("全空白備註轉 null").isNull();
		ExecutionDraft on = ExecutionValidator.validate(
				req(null, null, null, null, true, "UPS 告警\r\n已排除", true, "下週複查", null), RESULTS);
		assertThat(on.exceptionDesc()).isEqualTo("UPS 告警\n已排除");
		assertThat(on.followUpDesc()).isEqualTo("下週複查");
	}

	@Test
	void 送審缺漏一次列全_依畫面順序() {
		assertThatThrownBy(() -> ExecutionValidator.validate(req(null, null, null, "DONE", true, " ", true, null, null),
				RESULTS)).isInstanceOf(ApiBadRequestException.class)
				.hasMessage(ExecutionValidator.MSG_SUBMIT_PREFIX + "實際開始時間、實際結束時間、異常說明、後續追蹤說明");
		assertThatThrownBy(() -> ExecutionValidator.validate(times("2026-10-07 09:00", null, "CANCEL"), RESULTS))
				.isInstanceOf(ApiBadRequestException.class)
				.hasMessage(ExecutionValidator.MSG_SUBMIT_PREFIX + "實際結束時間");
	}

	@Test
	void 送審不要求檢核項全勾() {
		ExecutionDraft d = ExecutionValidator.validate(req(List.of(item(1, false, null, null, null)), "2026-10-07 09:00",
				"2026-10-07 10:00", "PARTIAL", false, null, false, null, null), RESULTS);
		assertThat(d.submit()).isTrue();
		assertThat(d.resultCode()).isEqualTo("PARTIAL");
	}
}
