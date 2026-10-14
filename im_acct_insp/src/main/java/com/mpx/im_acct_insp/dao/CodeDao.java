package com.mpx.im_acct_insp.dao;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：代碼表查詢（IM_ACCT_CATG、IM_ASSET_CLASS、IM_ACCT_APPR_STAGE，皆 STATUS＝1、依 SORT_SEQ）
//           與某帳號類別的執行人名單（IM_EXEC_MAP，第 4 關核准轉交執行的歷史文字用）
//           測試第 1 輪修正：IM_USER 改 schema 限定名稱（引用 AcctConst.EXISTING_USER_TABLE）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（spec_im_acct_insp_v2.0.md §6.3；Table v0.2 7.9、7.11、7.12）：類別加 IS_ENABLED、IS_SUGGEST_EXPIRY、SORT_SEQ；
//           資產分類改 IS_REVIEW_TRIGGER、IS_SELECTABLE（旗標以 Boolean 對應）；關卡 ROLE_ID 換 PERM_ID；
//           刪執行人名單查詢（不再 JOIN 既有模組使用者表；執行人改由 PermDao 查 IM_ACCT_USER）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-14
// 變更說明: DB 別名 key 由 db.connect.itflow 改名為 itflow.db.alias（使用者改名）
// ============================================================

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.mpx.common.db.DbClient;
import com.mpx.im_acct_insp.model.ApprStage;
import com.mpx.im_acct_insp.model.AssetClass;
import com.mpx.im_acct_insp.model.Category;

@Repository
public class CodeDao {

	public static final String FIND_CATEGORIES = """
			SELECT CATG_ID, CATG_NAME, CATG_DESCR, IS_ENABLED AS ENABLED, IS_SUGGEST_EXPIRY AS SUGGEST_EXPIRY, SORT_SEQ
			  FROM IM_ACCT_CATG
			 WHERE STATUS = 1
			 ORDER BY SORT_SEQ, CATG_ID
			""";

	public static final String FIND_ASSET_CLASSES = """
			SELECT ASSET_CLASS_ID, ASSET_TYPE_CODE, ASSET_CLASS_NAME, IS_REVIEW_TRIGGER AS REVIEW_TRIGGER,
			       IS_SELECTABLE AS SELECTABLE, CTRL_DESCR, SORT_SEQ
			  FROM IM_ASSET_CLASS
			 WHERE STATUS = 1
			 ORDER BY SORT_SEQ, ASSET_CLASS_ID
			""";

	public static final String FIND_STAGES = """
			SELECT STAGE_ID, STAGE_NAME, STAGE_TYPE_CODE, PERM_ID
			  FROM IM_ACCT_APPR_STAGE
			 WHERE STATUS = 1
			 ORDER BY STAGE_ID
			""";

	private final DbClient dbClient;
	private final String itflowDb;

	public CodeDao(DbClient dbClient, @Value("${itflow.db.alias}") String itflowDb) {
		this.dbClient = dbClient;
		this.itflowDb = itflowDb;
	}

	public List<Category> findCategories() {
		return dbClient.query(itflowDb, FIND_CATEGORIES, null, Category.class);
	}

	public List<AssetClass> findAssetClasses() {
		return dbClient.query(itflowDb, FIND_ASSET_CLASSES, null, AssetClass.class);
	}

	public List<ApprStage> findStages() {
		return dbClient.query(itflowDb, FIND_STAGES, null, ApprStage.class);
	}
}
