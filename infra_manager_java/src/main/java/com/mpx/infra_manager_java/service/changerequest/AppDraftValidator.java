package com.mpx.infra_manager_java.service.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：草稿請求的檢查與正規化（S6 回合二 b-1，施工計畫 B8）。存草稿只要求標題與優先等級
//           （兩者 DB NOT NULL；其餘必填到 S7 送審才檢核，施工計畫待確認第 3 題）。
//           長度：VARCHAR2(n CHAR) 以 code point 計，APPLY_EMAIL 是 byte 語意、以 UTF-8 byte 計；CLOB 用 TextLength 兩級上限。
//           超長丟 TextTooLongException（400 帶 field／max／actual，field 為 JSON 路徑如 equipments[0].name）；
//           其餘格式錯誤丟 ApiBadRequestException。選項 id 必須是啟用中且群組正確的 IM_FORM_OPTION
//           2026-10-07 S5 R1（Claude Opus 5.5）：加 validate(…, requireTitle) 給範本用（範本標題可空）
// ============================================================

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mpx.infra_manager_java.model.changerequest.AppDraft;
import com.mpx.infra_manager_java.model.changerequest.AppDraftRequest;
import com.mpx.infra_manager_java.model.changerequest.FormOptionRow;
import com.mpx.infra_manager_java.util.TextLength;
import com.mpx.infra_manager_java.util.TextTooLongException;
import com.mpx.infra_manager_java.web.ApiBadRequestException;

public final class AppDraftValidator {

	/** 設備列與作業步驟各自的上限筆數 */
	public static final int MAX_ROWS = 100;
	/** 預估工時上限（NUMBER(6,2)） */
	public static final BigDecimal MAX_EST_HOURS = new BigDecimal("9999.99");

	private static final Set<String> PRIO_CODES = Set.of("P1", "P2", "P3", "P4");
	private static final Set<String> WORK_MODES = Set.of("ONSITE", "REMOTE");
	private static final DateTimeFormatter SPACE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	private static final DateTimeFormatter T = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

	private AppDraftValidator() {
	}

	public static AppDraft validate(AppDraftRequest r, List<FormOptionRow> activeOptions) {
		return validate(r, activeOptions, true);
	}

	/** requireTitle=false 給範本用：範本的標題可空，其餘規則與草稿相同 */
	public static AppDraft validate(AppDraftRequest r, List<FormOptionRow> activeOptions, boolean requireTitle) {
		if (r == null) {
			throw new ApiBadRequestException("請求格式錯誤");
		}
		String title = text("title", "標題", r.title(), 200);
		if (title == null && requireTitle) {
			throw new ApiBadRequestException("請填寫標題");
		}
		if (r.prioCode() == null || !PRIO_CODES.contains(r.prioCode())) {
			throw new ApiBadRequestException("請選擇優先等級");
		}
		String workMode = r.workModeCode() == null || r.workModeCode().isBlank() ? "ONSITE" : r.workModeCode();
		if (!WORK_MODES.contains(workMode)) {
			throw new ApiBadRequestException("作業方式不正確");
		}

		AppDraftRequest.Applicant a = r.applicant();
		AppDraftRequest.Supplier s = r.supplier();
		Integer headCount = s == null ? null : s.headCount();
		if (headCount != null && (headCount < 0 || headCount > 9999)) {
			throw new ApiBadRequestException("廠商進場人數不正確");
		}

		AppDraftRequest.Schedule sc = r.schedule();
		Timestamp start = sc == null ? null : dateTime(sc.start(), "預定開始時間");
		Timestamp end = sc == null ? null : dateTime(sc.end(), "預定結束時間");
		if (start != null && end != null && start.after(end)) {
			throw new ApiBadRequestException("預定結束時間不得早於開始時間");
		}
		BigDecimal estHours = sc == null ? null : sc.estHours();
		if (estHours != null && (estHours.signum() < 0 || estHours.compareTo(MAX_EST_HOURS) > 0
				|| estHours.stripTrailingZeros().scale() > 2)) {
			throw new ApiBadRequestException("預估工時不正確");
		}

		Map<Long, String> groupById = new HashMap<>();
		for (FormOptionRow o : activeOptions) {
			groupById.put(o.getFormOptionId(), o.getGroupCode());
		}

		return new AppDraft(title, r.prioCode(),
				a == null ? null : text("applicant.deptName", "申請部門", a.deptName(), 50),
				a == null ? null : text("applicant.tel", "聯絡電話", a.tel(), 50),
				a == null ? null : bytes("applicant.email", "電子郵件", a.email(), 255),
				r.selfExec() == null || r.selfExec(), Boolean.TRUE.equals(r.supplierExec()), workMode,
				text("remoteMethod", "遠端連線方式", r.remoteMethod(), 200),
				s == null ? null : text("supplier.name", "廠商名稱", s.name(), 200),
				s == null ? null : text("supplier.contact", "廠商聯絡人", s.contact(), 100),
				s == null ? null : text("supplier.tel", "廠商電話", s.tel(), 50), headCount,
				text("workSubject", "作業主旨", r.workSubject(), 200),
				longText("impactDesc", "影響說明", r.impactDesc(), TextLength.LIMIT_SHORT),
				longText("workDetail", "作業內容", r.workDetail(), TextLength.LIMIT_LONG),
				longText("riskDesc", "風險評估", r.riskDesc(), TextLength.LIMIT_LONG),
				longText("rollbackPlan", "回復計畫", r.rollbackPlan(), TextLength.LIMIT_LONG),
				text("otherReason", "其他原因", r.otherReason(), 500), start, end, estHours,
				r.location() == null ? null : text("location.omitReason", "不適用原因", r.location().omitReason(), 500),
				optionIds(r.categoryItemIds(), "CATG_ITEM", groupById),
				categoryOthers(r.categoryOthers(), groupById),
				optionIds(r.reasonIds(), "REASON", groupById),
				optionIds(r.scopeIds(), "SCOPE", groupById),
				equipments(r.equipments()), planSteps(r.planSteps()));
	}

	/** VARCHAR2(n CHAR)：去頭尾空白、空白轉 null、以 code point 檢查 */
	static String text(String field, String label, String value, int max) {
		String v = TextLength.check(field, label, value, Integer.MAX_VALUE);
		v = v == null ? null : v.strip();
		if (v == null || v.isEmpty()) {
			return null;
		}
		return TextLength.check(field, label, v, max);
	}

	/** byte 語意的 VARCHAR2：以 UTF-8 byte 數檢查 */
	static String bytes(String field, String label, String value, int maxBytes) {
		String v = text(field, label, value, Integer.MAX_VALUE);
		if (v != null) {
			int len = v.getBytes(StandardCharsets.UTF_8).length;
			if (len > maxBytes) {
				throw new TextTooLongException(field, label, maxBytes, len);
			}
		}
		return v;
	}

	/** CLOB：只統一換行（不去內文空白），全空白轉 null */
	static String longText(String field, String label, String value, int max) {
		String v = TextLength.check(field, label, value, max);
		return v == null || v.isBlank() ? null : v;
	}

	static Timestamp dateTime(String value, String label) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String v = value.strip();
		try {
			return Timestamp.valueOf(LocalDateTime.parse(v, v.indexOf('T') > 0 ? T : SPACE));
		} catch (DateTimeParseException e) {
			throw new ApiBadRequestException(label + "格式不正確");
		}
	}

	private static List<Long> optionIds(List<Long> ids, String group, Map<Long, String> groupById) {
		if (ids == null) {
			return List.of();
		}
		Set<Long> result = new LinkedHashSet<>();
		for (Long id : ids) {
			if (id == null || !group.equals(groupById.get(id))) {
				throw new ApiBadRequestException("選項不正確，請重新載入頁面");
			}
			result.add(id);
		}
		return List.copyOf(result);
	}

	private static List<AppDraft.CategoryOther> categoryOthers(List<AppDraftRequest.CategoryOther> others,
			Map<Long, String> groupById) {
		if (others == null) {
			return List.of();
		}
		Set<Long> seen = new LinkedHashSet<>();
		List<AppDraft.CategoryOther> result = new ArrayList<>();
		for (int i = 0; i < others.size(); i++) {
			AppDraftRequest.CategoryOther o = others.get(i);
			if (o == null || o.formOptionId() == null || !"CATG".equals(groupById.get(o.formOptionId()))
					|| !seen.add(o.formOptionId())) {
				throw new ApiBadRequestException("選項不正確，請重新載入頁面");
			}
			String t = text("categoryOthers[" + i + "].text", "類別其他說明", o.text(), 500);
			if (t != null) {
				result.add(new AppDraft.CategoryOther(o.formOptionId(), t));
			}
		}
		return List.copyOf(result);
	}

	private static List<AppDraft.Equipment> equipments(List<AppDraftRequest.Equipment> rows) {
		if (rows == null) {
			return List.of();
		}
		if (rows.size() > MAX_ROWS) {
			throw new ApiBadRequestException("設備最多 " + MAX_ROWS + " 筆");
		}
		List<AppDraft.Equipment> result = new ArrayList<>();
		for (int i = 0; i < rows.size(); i++) {
			AppDraftRequest.Equipment e = rows.get(i);
			if (e == null) {
				continue;
			}
			String p = "equipments[" + i + "].";
			AppDraft.Equipment n = new AppDraft.Equipment(text(p + "name", "設備名稱", e.name(), 200),
					text(p + "assetNo", "財產編號", e.assetNo(), 100), text(p + "modelNo", "型號", e.modelNo(), 200),
					text(p + "serialNo", "序號", e.serialNo(), 100), text(p + "purpose", "用途", e.purpose(), 500),
					text(p + "mgmtIp", "管理 IP", e.mgmtIp(), 45));
			boolean empty = n.name() == null && n.assetNo() == null && n.modelNo() == null && n.serialNo() == null
					&& n.purpose() == null && n.mgmtIp() == null;
			if (empty) {
				continue;
			}
			if (n.name() == null) {
				throw new ApiBadRequestException("第 " + (i + 1) + " 筆設備請填寫設備名稱");
			}
			result.add(n);
		}
		return List.copyOf(result);
	}

	private static List<String> planSteps(List<String> steps) {
		if (steps == null) {
			return List.of();
		}
		if (steps.size() > MAX_ROWS) {
			throw new ApiBadRequestException("作業步驟最多 " + MAX_ROWS + " 筆");
		}
		List<String> result = new ArrayList<>();
		for (int i = 0; i < steps.size(); i++) {
			String t = text("planSteps[" + i + "]", "作業步驟", steps.get(i), 1000);
			if (t != null) {
				result.add(t);
			}
		}
		return List.copyOf(result);
	}
}
