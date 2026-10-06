package com.mpx.infra_manager_java.service.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-06
// 變更說明: 新增：表單選項＋上傳限制（S6 回合二 a，BACKLOG 第 6 項 B1）。申請表單據此長出優先等級、類別、
//           細項（以 upFormOptionId 掛在類別下）、原因、範圍、檢查清單
// ============================================================

import org.springframework.stereotype.Service;

import com.mpx.infra_manager_java.dao.changerequest.FormOptionDao;
import com.mpx.infra_manager_java.model.changerequest.FormOptionRow;
import com.mpx.infra_manager_java.model.changerequest.FormOptionsResponse;
import com.mpx.infra_manager_java.model.changerequest.FormOptionsResponse.FormOption;
import com.mpx.infra_manager_java.model.changerequest.FormOptionsResponse.UploadLimits;
import com.mpx.infra_manager_java.service.sysparam.SysParamService;

@Service
public class FormOptionService {

	private final FormOptionDao formOptionDao;
	private final SysParamService sysParamService;

	public FormOptionService(FormOptionDao formOptionDao, SysParamService sysParamService) {
		this.formOptionDao = formOptionDao;
		this.sysParamService = sysParamService;
	}

	public FormOptionsResponse load() {
		var options = formOptionDao.findActive().stream().map(FormOptionService::toOption).toList();
		var upload = new UploadLimits(sysParamService.uploadMaxMb(), sysParamService.uploadMaxFiles());
		return new FormOptionsResponse(options, upload);
	}

	private static FormOption toOption(FormOptionRow r) {
		return new FormOption(r.getFormOptionId(), r.getGroupCode(), r.getOptionCode(), r.getOptionName(),
				r.getUpFormOptionId(), r.getColorCode(), r.getOptionDesc(), r.getTimeLimitDesc(),
				r.getPrioFlowDesc(), r.getSampleDesc(), r.getFlowId(), r.getSortNo());
	}
}
