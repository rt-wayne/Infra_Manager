package com.mpx.infra_manager.model;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：GET /codes 回傳（spec §4.1）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（spec_im_acct_insp_v2.0.md §6.3）：欄位不變，元素型別依新欄位改寫
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// ============================================================

import java.util.List;

public class Codes {

	private List<Category> categories;
	private List<AssetClass> assetClasses;
	private List<ApprStage> stages;

	public List<Category> getCategories() {
		return categories;
	}

	public void setCategories(List<Category> categories) {
		this.categories = categories;
	}

	public List<AssetClass> getAssetClasses() {
		return assetClasses;
	}

	public void setAssetClasses(List<AssetClass> assetClasses) {
		this.assetClasses = assetClasses;
	}

	public List<ApprStage> getStages() {
		return stages;
	}

	public void setStages(List<ApprStage> stages) {
		this.stages = stages;
	}
}
