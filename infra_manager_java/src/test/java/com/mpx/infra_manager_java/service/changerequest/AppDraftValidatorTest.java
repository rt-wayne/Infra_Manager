package com.mpx.infra_manager_java.service.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：草稿檢查與正規化的單元測試（S6 回合二 b-1）。鎖定：標題與優先等級必填；預設值；空白轉 null；
//           超長回正確 field／max／actual（含 code point、UTF-8 byte、CLOB 兩級）；CRLF 不多算；
//           選項群組錯、未啟用、重複的處理；空白設備列與步驟略過；設備缺名稱、時間順序、工時格式
//           2026-10-07 S5 R1：加範本模式（requireTitle=false）標題可空、優先等級仍必填
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mpx.infra_manager_java.model.changerequest.AppDraft;
import com.mpx.infra_manager_java.model.changerequest.AppDraftRequest;
import com.mpx.infra_manager_java.model.changerequest.FormOptionRow;
import com.mpx.infra_manager_java.util.TextLength;
import com.mpx.infra_manager_java.util.TextTooLongException;
import com.mpx.infra_manager_java.web.ApiBadRequestException;

class AppDraftValidatorTest {

	private static final List<FormOptionRow> OPTIONS = List.of(option(1L, "CATG"), option(11L, "CATG_ITEM"),
			option(12L, "CATG_ITEM"), option(21L, "REASON"), option(31L, "SCOPE"));

	private static FormOptionRow option(Long id, String group) {
		FormOptionRow o = new FormOptionRow();
		o.setFormOptionId(id);
		o.setGroupCode(group);
		return o;
	}

	private static Builder base() {
		return new Builder();
	}

	/** 只填必要欄位的請求，測試再逐欄覆寫 */
	private static final class Builder {
		String title = "  更換核心交換器  ";
		String prioCode = "P3";
		AppDraftRequest.Applicant applicant;
		Boolean selfExec;
		Boolean supplierExec;
		String workModeCode;
		AppDraftRequest.Supplier supplier;
		String impactDesc;
		String workDetail;
		List<Long> categoryItemIds;
		List<AppDraftRequest.CategoryOther> categoryOthers;
		List<Long> reasonIds;
		List<Long> scopeIds;
		List<AppDraftRequest.Equipment> equipments;
		List<String> planSteps;
		AppDraftRequest.Schedule schedule;
		AppDraftRequest.Location location;

		AppDraftRequest build() {
			return new AppDraftRequest(title, prioCode, applicant, selfExec, supplierExec, workModeCode, null,
					supplier, null, impactDesc, workDetail, null, null, categoryItemIds, categoryOthers, reasonIds,
					null, scopeIds, equipments, planSteps, schedule, location, null);
		}

		AppDraft validate() {
			return AppDraftValidator.validate(build(), OPTIONS);
		}
	}

	private static TextTooLongException tooLong(Builder b) {
		try {
			b.validate();
		} catch (TextTooLongException e) {
			return e;
		}
		throw new AssertionError("應丟 TextTooLongException");
	}

	@Test
	void 只填標題與優先等級可通過且帶預設值() {
		AppDraft d = base().validate();
		assertThat(d.title()).isEqualTo("更換核心交換器");
		assertThat(d.prioCode()).isEqualTo("P3");
		assertThat(d.selfExec()).isTrue();
		assertThat(d.supplierExec()).isFalse();
		assertThat(d.workModeCode()).isEqualTo("ONSITE");
		assertThat(d.applyEmail()).isNull();
		assertThat(d.categoryItemIds()).isEmpty();
		assertThat(d.equipments()).isEmpty();
		assertThat(d.planSteps()).isEmpty();
		assertThat(d.schedStart()).isNull();
	}

	@Test
	void 標題空白或缺優先等級回400() {
		Builder b = base();
		b.title = "   ";
		assertThatThrownBy(b::validate).isInstanceOf(ApiBadRequestException.class).hasMessage("請填寫標題");
		Builder c = base();
		c.prioCode = "P9";
		assertThatThrownBy(c::validate).isInstanceOf(ApiBadRequestException.class).hasMessage("請選擇優先等級");
		Builder n = base();
		n.prioCode = null;
		assertThatThrownBy(n::validate).isInstanceOf(ApiBadRequestException.class);
		assertThatThrownBy(() -> AppDraftValidator.validate(null, OPTIONS)).isInstanceOf(ApiBadRequestException.class);
	}

	@Test
	void 範本模式標題可空但優先等級仍必填() {
		Builder b = base();
		b.title = "   ";
		assertThat(AppDraftValidator.validate(b.build(), OPTIONS, false).title()).isNull();
		Builder n = base();
		n.title = null;
		n.prioCode = null;
		assertThatThrownBy(() -> AppDraftValidator.validate(n.build(), OPTIONS, false))
				.isInstanceOf(ApiBadRequestException.class).hasMessage("請選擇優先等級");
	}

	@Test
	void 標題超過200字回field與字數_以codepoint計() {
		Builder ok = base();
		ok.title = "😀".repeat(200);
		assertThat(ok.validate().title()).hasSize(400);
		Builder b = base();
		b.title = "字".repeat(201);
		TextTooLongException e = tooLong(b);
		assertThat(e.getField()).isEqualTo("title");
		assertThat(e.getMax()).isEqualTo(200);
		assertThat(e.getActual()).isEqualTo(201);
	}

	@Test
	void 電子郵件以UTF8位元組計() {
		Builder ok = base();
		ok.applicant = new AppDraftRequest.Applicant(" 資訊處 ", "", "a".repeat(255));
		AppDraft d = ok.validate();
		assertThat(d.applyDeptName()).isEqualTo("資訊處");
		assertThat(d.applyTel()).isNull();
		Builder b = base();
		b.applicant = new AppDraftRequest.Applicant(null, null, "信".repeat(86));
		TextTooLongException e = tooLong(b);
		assertThat(e.getField()).isEqualTo("applicant.email");
		assertThat(e.getMax()).isEqualTo(255);
		assertThat(e.getActual()).isEqualTo(258);
	}

	@Test
	void 長文欄位兩級上限且CRLF不多算() {
		Builder ok = base();
		ok.impactDesc = "a\r\n".repeat(1000);
		assertThat(ok.validate().impactDesc()).isEqualTo("a\n".repeat(1000));
		Builder b = base();
		b.impactDesc = "字".repeat(TextLength.LIMIT_SHORT + 1);
		assertThat(tooLong(b).getField()).isEqualTo("impactDesc");
		Builder longOk = base();
		longOk.workDetail = "字".repeat(TextLength.LIMIT_LONG);
		assertThat(longOk.validate().workDetail()).hasSize(TextLength.LIMIT_LONG);
		Builder longBad = base();
		longBad.workDetail = "字".repeat(TextLength.LIMIT_LONG + 1);
		assertThat(tooLong(longBad).getMax()).isEqualTo(TextLength.LIMIT_LONG);
		Builder blank = base();
		blank.workDetail = " \n ";
		assertThat(blank.validate().workDetail()).isNull();
	}

	@Test
	void 作業方式只收ONSITE或REMOTE() {
		Builder b = base();
		b.workModeCode = "REMOTE";
		assertThat(b.validate().workModeCode()).isEqualTo("REMOTE");
		Builder bad = base();
		bad.workModeCode = "HOME";
		assertThatThrownBy(bad::validate).isInstanceOf(ApiBadRequestException.class).hasMessage("作業方式不正確");
	}

	@Test
	void 廠商人數範圍() {
		Builder b = base();
		b.supplier = new AppDraftRequest.Supplier("廠商", null, null, -1);
		assertThatThrownBy(b::validate).isInstanceOf(ApiBadRequestException.class);
		Builder big = base();
		big.supplier = new AppDraftRequest.Supplier(null, null, null, 10000);
		assertThatThrownBy(big::validate).isInstanceOf(ApiBadRequestException.class);
		Builder ok = base();
		ok.supplier = new AppDraftRequest.Supplier("廠商", "聯絡人", "02", 3);
		assertThat(ok.validate().supHeadCount()).isEqualTo(3);
	}

	@Test
	void 選項必須是啟用中且群組正確_重複只留一筆() {
		Builder ok = base();
		ok.categoryItemIds = List.of(11L, 12L, 11L);
		ok.reasonIds = List.of(21L);
		ok.scopeIds = List.of(31L);
		AppDraft d = ok.validate();
		assertThat(d.categoryItemIds()).containsExactly(11L, 12L);
		assertThat(d.reasonIds()).containsExactly(21L);
		assertThat(d.scopeIds()).containsExactly(31L);

		Builder wrongGroup = base();
		wrongGroup.reasonIds = List.of(31L);
		assertThatThrownBy(wrongGroup::validate).isInstanceOf(ApiBadRequestException.class);
		Builder inactive = base();
		inactive.scopeIds = List.of(999L);
		assertThatThrownBy(inactive::validate).isInstanceOf(ApiBadRequestException.class);
		Builder nullId = base();
		nullId.categoryItemIds = Arrays.asList(11L, null);
		assertThatThrownBy(nullId::validate).isInstanceOf(ApiBadRequestException.class);
	}

	@Test
	void 類別其他_須為CATG_空白略過_重複擋下() {
		Builder ok = base();
		ok.categoryOthers = List.of(new AppDraftRequest.CategoryOther(1L, " 自備機櫃 "));
		assertThat(ok.validate().categoryOthers()).containsExactly(new AppDraft.CategoryOther(1L, "自備機櫃"));
		Builder blank = base();
		blank.categoryOthers = List.of(new AppDraftRequest.CategoryOther(1L, "  "));
		assertThat(blank.validate().categoryOthers()).isEmpty();
		Builder item = base();
		item.categoryOthers = List.of(new AppDraftRequest.CategoryOther(11L, "x"));
		assertThatThrownBy(item::validate).isInstanceOf(ApiBadRequestException.class);
		Builder dup = base();
		dup.categoryOthers = List.of(new AppDraftRequest.CategoryOther(1L, "a"),
				new AppDraftRequest.CategoryOther(1L, "b"));
		assertThatThrownBy(dup::validate).isInstanceOf(ApiBadRequestException.class);
		Builder tooLong = base();
		tooLong.categoryOthers = List.of(new AppDraftRequest.CategoryOther(1L, "字".repeat(501)));
		assertThat(tooLong(tooLong).getField()).isEqualTo("categoryOthers[0].text");
	}

	@Test
	void 設備列_全空白略過_缺名稱擋下_超長帶索引() {
		Builder b = base();
		List<AppDraftRequest.Equipment> rows = new ArrayList<>();
		rows.add(new AppDraftRequest.Equipment(" ", "", null, null, null, null));
		rows.add(null);
		rows.add(new AppDraftRequest.Equipment("SW-01", "A001", null, null, null, " 10.0.0.1 "));
		b.equipments = rows;
		assertThat(b.validate().equipments())
				.containsExactly(new AppDraft.Equipment("SW-01", "A001", null, null, null, "10.0.0.1"));

		Builder noName = base();
		noName.equipments = List.of(new AppDraftRequest.Equipment(null, "A001", null, null, null, null));
		assertThatThrownBy(noName::validate).isInstanceOf(ApiBadRequestException.class)
				.hasMessage("第 1 筆設備請填寫設備名稱");

		Builder longIp = base();
		longIp.equipments = List.of(new AppDraftRequest.Equipment("x", null, null, null, null, null),
				new AppDraftRequest.Equipment("y", null, null, null, null, "1".repeat(46)));
		assertThat(tooLong(longIp).getField()).isEqualTo("equipments[1].mgmtIp");

		Builder tooMany = base();
		tooMany.equipments = Collections.nCopies(AppDraftValidator.MAX_ROWS + 1,
				new AppDraftRequest.Equipment("x", null, null, null, null, null));
		assertThatThrownBy(tooMany::validate).isInstanceOf(ApiBadRequestException.class);
	}

	@Test
	void 作業步驟_空白略過_超長帶索引() {
		Builder b = base();
		b.planSteps = Arrays.asList("關機", " ", null, "  更換  ");
		assertThat(b.validate().planSteps()).containsExactly("關機", "更換");
		Builder bad = base();
		bad.planSteps = List.of("a", "字".repeat(1001));
		assertThat(tooLong(bad).getField()).isEqualTo("planSteps[1]");
	}

	@Test
	void 時程_兩種格式_順序與工時檢查() {
		Builder b = base();
		b.schedule = new AppDraftRequest.Schedule("2026-10-08 09:00", "2026-10-08T18:30", new BigDecimal("9.5"));
		AppDraft d = b.validate();
		assertThat(d.schedStart()).isEqualTo(Timestamp.valueOf(LocalDateTime.of(2026, 10, 8, 9, 0)));
		assertThat(d.schedEnd()).isEqualTo(Timestamp.valueOf(LocalDateTime.of(2026, 10, 8, 18, 30)));
		assertThat(d.estHours()).isEqualByComparingTo("9.5");

		Builder same = base();
		same.schedule = new AppDraftRequest.Schedule("2026-10-08 09:00", "2026-10-08 09:00", null);
		assertThat(same.validate().schedEnd()).isNotNull();

		Builder reversed = base();
		reversed.schedule = new AppDraftRequest.Schedule("2026-10-09 09:00", "2026-10-08 09:00", null);
		assertThatThrownBy(reversed::validate).isInstanceOf(ApiBadRequestException.class);
		Builder badFormat = base();
		badFormat.schedule = new AppDraftRequest.Schedule("2026/10/08", null, null);
		assertThatThrownBy(badFormat::validate).isInstanceOf(ApiBadRequestException.class)
				.hasMessage("預定開始時間格式不正確");
		Builder scale = base();
		scale.schedule = new AppDraftRequest.Schedule(null, null, new BigDecimal("1.255"));
		assertThatThrownBy(scale::validate).isInstanceOf(ApiBadRequestException.class);
		Builder negative = base();
		negative.schedule = new AppDraftRequest.Schedule(null, null, new BigDecimal("-1"));
		assertThatThrownBy(negative::validate).isInstanceOf(ApiBadRequestException.class);
		Builder trailingZero = base();
		trailingZero.schedule = new AppDraftRequest.Schedule(null, null, new BigDecimal("2.500"));
		assertThat(trailingZero.validate().estHours()).isEqualByComparingTo("2.5");
	}

	@Test
	void 不適用原因長度() {
		Builder b = base();
		b.location = new AppDraftRequest.Location("字".repeat(501));
		assertThat(tooLong(b).getField()).isEqualTo("location.omitReason");
		Builder ok = base();
		ok.location = new AppDraftRequest.Location(" 無實體設備 ");
		assertThat(ok.validate().omitReason()).isEqualTo("無實體設備");
	}
}
