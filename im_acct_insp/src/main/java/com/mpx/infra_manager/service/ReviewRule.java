package com.mpx.infra_manager.service;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：審查條件與審查日期寫入判斷（spec v2.0 §4.7；取代 ReviewBasisRule，M14 不再存審查依據碼）
//           審查條件（符合任一）：資產分類 IS_REVIEW_TRIGGER＝1、類別 DOOR、IS_PRIV＝1（不寫死分類碼）；
//           只在第一次回報時判斷：新增→新內容符合即寫；修改／重新啟用→新內容符合且（舊內容不符合或 LAST_REVIEW_DATE 為空）才寫；
//           停用、刪除不動。寫入值 LAST＝EFFECT_DATE 的日期、NEXT＝＋180 天
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// ============================================================

import java.time.LocalDate;

import com.mpx.infra_manager.common.AcctConst;
import com.mpx.infra_manager.model.AssetClass;

public final class ReviewRule {

	private ReviewRule() {
	}

	/** 審查條件共用函式（帳號主表與申請內容共用） */
	public static boolean matches(AssetClass assetClass, String catgId, Integer isPriv) {
		return (assetClass != null && Boolean.TRUE.equals(assetClass.getReviewTrigger()))
				|| AcctConst.CATG_DOOR.equals(catgId) || Integer.valueOf(1).equals(isPriv);
	}

	/**
	 * 第一次回報時是否寫入審查日期。
	 *
	 * @param reqTypeCode   申請類型
	 * @param newMatches    新內容是否符合審查條件
	 * @param oldMatches    主表舊內容是否符合（新增時忽略）
	 * @param oldLastReview 主表舊的 LAST_REVIEW_DATE（新增時忽略）
	 */
	public static boolean shouldWrite(String reqTypeCode, boolean newMatches, boolean oldMatches, LocalDate oldLastReview) {
		if (!newMatches) {
			return false;
		}
		return switch (reqTypeCode) {
		case AcctConst.REQ_ADD -> true;
		case AcctConst.REQ_MODIFY, AcctConst.REQ_ENABLE -> !oldMatches || oldLastReview == null;
		default -> false;
		};
	}

	public static LocalDate nextReview(LocalDate last) {
		return last.plusDays(AcctConst.REVIEW_CYCLE_DAYS);
	}
}
