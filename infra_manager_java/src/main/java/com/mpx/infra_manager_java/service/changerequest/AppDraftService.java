package com.mpx.infra_manager_java.service.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：建立申請單草稿（S6 回合二 b-1）。順序：讀選項與流程政策 → 檢查（交易外的慢動作都在取號前做完）→
//           同一交易內取號、寫主檔與子表。申請人鎖定登入者、APPLY_DATE 為台灣今天（施工計畫待確認第 6 題）。
//           流程：full_only → full；by_priority → 該優先等級 PRIO 選項的 FLOW_ID，沒設定則退回 full。
//           建草稿不寫 IM_APP_EVENT（事件碼沒有「建立」，與舊系統一致）
// ============================================================

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mpx.infra_manager_java.dao.changerequest.AppWriteDao;
import com.mpx.infra_manager_java.dao.changerequest.FormOptionDao;
import com.mpx.infra_manager_java.model.auth.AuthUser;
import com.mpx.infra_manager_java.model.changerequest.AppDraft;
import com.mpx.infra_manager_java.model.changerequest.AppDraftRequest;
import com.mpx.infra_manager_java.model.changerequest.FormOptionRow;
import com.mpx.infra_manager_java.service.sysparam.SysParamService;
import com.mpx.infra_manager_java.util.TaiwanTime;

@Service
public class AppDraftService {

	public static final String FLOW_FULL = "full";

	private final FormOptionDao formOptionDao;
	private final SysParamService sysParamService;
	private final AppSeqService appSeqService;
	private final AppWriteDao appWriteDao;

	public AppDraftService(FormOptionDao formOptionDao, SysParamService sysParamService, AppSeqService appSeqService,
			AppWriteDao appWriteDao) {
		this.formOptionDao = formOptionDao;
		this.sysParamService = sysParamService;
		this.appSeqService = appSeqService;
		this.appWriteDao = appWriteDao;
	}

	/** 建草稿，回新單號 */
	@Transactional
	public String create(AppDraftRequest request, AuthUser user) {
		List<FormOptionRow> options = formOptionDao.findActive();
		AppDraft draft = AppDraftValidator.validate(request, options);
		String flowId = flowFor(draft.prioCode(), sysParamService.flowPolicy(), options);

		LocalDate today = TaiwanTime.today();
		String appId = AppSeqService.onlineAppId(today,
				appSeqService.nextNo(AppSeqService.PREFIX_ONLINE, today, user.userId()));
		appWriteDao.insertApp(appId, flowId, user.userId(), TaiwanTime.startOf(today), draft);
		appWriteDao.insertChildren(appId, user.userId(), draft);
		return appId;
	}

	static String flowFor(String prioCode, String policy, List<FormOptionRow> options) {
		if (!SysParamService.POLICY_BY_PRIORITY.equals(policy)) {
			return FLOW_FULL;
		}
		return options.stream()
				.filter(o -> "PRIO".equals(o.getGroupCode()) && prioCode.equals(o.getOptionCode()))
				.map(FormOptionRow::getFlowId)
				.filter(f -> f != null && !f.isBlank())
				.findFirst()
				.orElse(FLOW_FULL);
	}
}
