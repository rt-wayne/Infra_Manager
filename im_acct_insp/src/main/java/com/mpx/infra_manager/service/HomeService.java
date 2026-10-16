package com.mpx.infra_manager.service;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：首頁卡片、鈴鐺到期清單、待辦簽核（spec §3.7、§4.1）
//           待辦只回登入者此刻能處理的：APPROVE＝REVIEW 且目前關卡的角色是我；EXEC＝EXEC 且類別在我的 IM_EXEC_MAP；
//           VERIFY＝VERIFY 且我具關卡 6 角色；合併後依送出時間排序
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（spec_im_acct_insp_v2.0.md §4.5、§4.13、§6.3）：
//           卡片：所有登入者、只回聚合數字（口徑見 HomeDao.FIND_STATS）；
//           鈴鐺：到期提醒範圍（含 EXPIRED）逐筆判斷相關性（ACC_EXPIRY_ALL、原新增申請人、該類別執行人），daysLeft＝到期日－今天；
//           待辦：APPROVE＝REVIEW 且目前關卡 PERM_ID 在我的權限；EXEC＝EXEC／6 且類別在我的執行人對照；
//           VERIFY＝VERIFY／7 且有關卡 7 權限；回 source、title、versionNo、複驗退回次數與原因（「曾被複驗退回」提示）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// ============================================================

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.mpx.infra_manager.common.AcctConst;
import com.mpx.infra_manager.common.Identity;
import com.mpx.infra_manager.dao.AcctReqDao;
import com.mpx.infra_manager.dao.HomeDao;
import com.mpx.infra_manager.model.ApprStage;
import com.mpx.infra_manager.model.ExpiringAcct;
import com.mpx.infra_manager.model.HomeStats;
import com.mpx.infra_manager.model.TodoItem;

@Service
public class HomeService {

	public static final String KIND_APPROVE = "APPROVE";
	public static final String KIND_EXEC = "EXEC";
	public static final String KIND_VERIFY = "VERIFY";

	private final PermissionService perm;
	private final CodeService codeService;
	private final HomeDao homeDao;
	private final AcctReqDao acctReqDao;
	private final Clock clock;

	public HomeService(PermissionService perm, CodeService codeService, HomeDao homeDao, AcctReqDao acctReqDao, Clock clock) {
		this.perm = perm;
		this.codeService = codeService;
		this.homeDao = homeDao;
		this.acctReqDao = acctReqDao;
		this.clock = clock;
	}

	public HomeStats stats(Identity id) {
		return homeDao.findStats(LocalDate.now(clock));
	}

	public List<ExpiringAcct> expiring(Identity id) {
		LocalDate today = LocalDate.now(clock);
		List<ExpiringAcct> result = new ArrayList<>();
		for (ExpiringAcct a : homeDao.findExpiring(today)) {
			if (perm.expiringRelated(id, a.getApplyBy(), a.getCatgId())) {
				a.setDaysLeft(a.getExpiryDate() == null ? null : ChronoUnit.DAYS.between(today, a.getExpiryDate().toLocalDate()));
				result.add(a);
			}
		}
		return result;
	}

	public List<TodoItem> todos(Identity id) {
		Map<Integer, ApprStage> stages = codeService.stageMap();
		List<TodoItem> result = new ArrayList<>();
		List<Integer> myStages = new ArrayList<>();
		for (int s = AcctConst.STAGE_FIRST_APPR; s <= AcctConst.STAGE_LAST_APPR; s++) {
			if (perm.can(id, CodeService.stagePermId(stages, s))) {
				myStages.add(s);
			}
		}
		if (!myStages.isEmpty()) {
			addAll(result, acctReqDao.findTodoApprove(myStages), KIND_APPROVE);
		}
		if (!id.getExecCatgIds().isEmpty()) {
			addAll(result, acctReqDao.findTodoExec(id.getExecCatgIds()), KIND_EXEC);
		}
		if (perm.can(id, CodeService.stagePermId(stages, AcctConst.STAGE_VERIFY))) {
			addAll(result, acctReqDao.findTodoVerify(), KIND_VERIFY);
		}
		result.sort(Comparator.comparing(TodoItem::getSubmitDate, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(TodoItem::getReqId, Comparator.nullsLast(Comparator.naturalOrder())));
		return result;
	}

	private static void addAll(List<TodoItem> target, List<TodoItem> rows, String kind) {
		for (TodoItem t : rows) {
			t.setActionKind(kind);
			t.setTitle(title(t));
			target.add(t);
		}
	}

	/** 待辦標題：「{申請類型} {帳號類別}：{帳號名稱}」；門禁卡尚無卡號顯示「（門禁卡，待發卡）」 */
	static String title(TodoItem t) {
		String name = NotificationService.acctNameText(t.getCatgId(), t.getAcctName());
		return AcctConst.reqTypeName(t.getReqTypeCode()) + " " + (t.getCatgName() == null ? t.getCatgId() : t.getCatgName())
				+ (name.isEmpty() ? "" : "：" + name);
	}
}
