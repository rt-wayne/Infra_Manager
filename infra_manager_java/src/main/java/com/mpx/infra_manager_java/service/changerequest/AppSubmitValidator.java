package com.mpx.infra_manager_java.service.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-07
// 變更說明: 新增：送審前的必填檢核（S7 R1，施工計畫 ②A）。清單照舊表單 new.ejs 的 * 欄位：申請單位、聯絡電話、Email、
//           作業主題；至少勾一個執行人員；處理方式 REMOTE 時連線方式必填；勾委外廠商時廠商三欄必填。
//           設備位置不檢核（S6 ⑦A：S13 前不得要求位置必填）。一次列出全部缺漏，400「送審前請先補齊：…」
// ============================================================

import java.util.ArrayList;
import java.util.List;

import com.mpx.infra_manager_java.model.changerequest.AppRow;
import com.mpx.infra_manager_java.web.ApiBadRequestException;

public final class AppSubmitValidator {

	public static final String MSG_PREFIX = "送審前請先補齊：";

	private AppSubmitValidator() {
	}

	/** 缺漏欄位的中文名稱清單（依畫面順序）；空清單表示可以送審 */
	public static List<String> missing(AppRow app) {
		List<String> m = new ArrayList<>();
		if (blank(app.getApplyDeptName())) {
			m.add("申請單位");
		}
		if (blank(app.getApplyTel())) {
			m.add("聯絡電話");
		}
		if (blank(app.getApplyEmail())) {
			m.add("Email");
		}
		boolean selfExec = Integer.valueOf(1).equals(app.getIsSelfExec());
		boolean supExec = Integer.valueOf(1).equals(app.getIsSupExec());
		if (!selfExec && !supExec) {
			m.add("執行人員");
		}
		if ("REMOTE".equals(app.getWorkModeCode()) && blank(app.getRemoteMethod())) {
			m.add("連線方式");
		}
		if (supExec) {
			if (blank(app.getSupName())) {
				m.add("執行廠商");
			}
			if (blank(app.getSupCntct())) {
				m.add("廠商聯絡人");
			}
			if (blank(app.getSupTel())) {
				m.add("廠商電話");
			}
		}
		if (blank(app.getWorkSubj())) {
			m.add("作業主題");
		}
		return m;
	}

	/** 有缺漏就丟 ApiBadRequestException（→ 400），訊息一次列出全部缺漏 */
	public static void validate(AppRow app) {
		List<String> m = missing(app);
		if (!m.isEmpty()) {
			throw new ApiBadRequestException(MSG_PREFIX + String.join("、", m));
		}
	}

	private static boolean blank(String v) {
		return v == null || v.isBlank();
	}
}
