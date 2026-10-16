package com.mpx.infra_manager.service;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：代碼表（GET /codes）與關卡角色判斷（spec §2、§4.1）
//           關卡 1～4 的負責角色、關卡 6 複驗角色一律取自 IM_ACCT_APPR_STAGE.ROLE_ID，不寫死在程式
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（spec_im_acct_insp_v2.0.md §2.3、§6.3）：關卡改 1～7，處理權限取自 IM_ACCT_APPR_STAGE.PERM_ID（不寫死）；
//           角色判斷移到 PermissionService；新增 findCategory（新增申請檢查 IS_ENABLED）；刪執行人名單文字
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// ============================================================

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.mpx.infra_manager.dao.CodeDao;
import com.mpx.infra_manager.model.ApprStage;
import com.mpx.infra_manager.model.AssetClass;
import com.mpx.infra_manager.model.Category;
import com.mpx.infra_manager.model.Codes;

@Service
public class CodeService {

	private final CodeDao codeDao;

	public CodeService(CodeDao codeDao) {
		this.codeDao = codeDao;
	}

	public Codes getCodes() {
		Codes codes = new Codes();
		codes.setCategories(codeDao.findCategories());
		codes.setAssetClasses(codeDao.findAssetClasses());
		codes.setStages(codeDao.findStages());
		return codes;
	}

	public Map<Integer, ApprStage> stageMap() {
		Map<Integer, ApprStage> map = new LinkedHashMap<>();
		for (ApprStage s : codeDao.findStages()) {
			map.put(s.getStageId(), s);
		}
		return map;
	}

	public static String stageName(Map<Integer, ApprStage> stages, Integer stageId) {
		ApprStage s = stageId == null ? null : stages.get(stageId);
		return s == null ? String.valueOf(stageId) : s.getStageName();
	}

	/** 處理該關所需權限碼；關卡 1、6 或查無回 null */
	public static String stagePermId(Map<Integer, ApprStage> stages, Integer stageId) {
		ApprStage s = stageId == null ? null : stages.get(stageId);
		return s == null ? null : s.getPermId();
	}

	/** STATUS＝1 的類別；查無回 null */
	public Category findCategory(String catgId) {
		if (catgId == null) {
			return null;
		}
		return codeDao.findCategories().stream().filter(c -> catgId.equals(c.getCatgId())).findFirst().orElse(null);
	}

	/** STATUS＝1 的資產分類（含不可選者）；空值或查無回 null */
	public AssetClass findAssetClass(String assetClassId) {
		if (assetClassId == null) {
			return null;
		}
		return codeDao.findAssetClasses().stream().filter(a -> assetClassId.equals(a.getAssetClassId())).findFirst()
				.orElse(null);
	}
}
