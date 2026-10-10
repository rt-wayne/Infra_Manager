package com.mpx.im_acct_insp.service;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：權限判斷單一入口（spec v2.0 §2.3、§2.4、§4.5）
//           can(身分, 權限碼)：有效身分的權限集合（含 GENERAL，由 IdentityProvider 每次請求自 DB 載入）；
//           需要資料的判斷：是否為某類別執行人、此刻能否處理某申請單目前關卡、可看申請單、可看帳號、到期提醒相關性。
//           關卡權限取自 IM_ACCT_APPR_STAGE.PERM_ID；程式不判斷角色名稱或人名；Controller 不寫權限邏輯。
//           無權限丟 403 P01（狀態或版本不符的 409 由各業務 Service 判斷）
// ============================================================

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.mpx.im_acct_insp.common.AcctConst;
import com.mpx.im_acct_insp.common.AcctMsg;
import com.mpx.im_acct_insp.common.Identity;
import com.mpx.im_acct_insp.dao.AcctHistDao;
import com.mpx.im_acct_insp.dao.AcctReqDao;
import com.mpx.im_acct_insp.dao.PermDao;
import com.mpx.im_acct_insp.model.Acct;
import com.mpx.im_acct_insp.model.AcctReq;
import com.mpx.im_acct_insp.model.ApprStage;

@Service
public class PermissionService {

	/** 某使用者當下的權限集合、角色、執行人類別 */
	public record Grants(Set<String> permissions, List<String> roleIds, List<String> execCatgIds) {
	}

	private final PermDao permDao;
	private final AcctReqDao acctReqDao;
	private final AcctHistDao acctHistDao;

	public PermissionService(PermDao permDao, AcctReqDao acctReqDao, AcctHistDao acctHistDao) {
		this.permDao = permDao;
		this.acctReqDao = acctReqDao;
		this.acctHistDao = acctHistDao;
	}

	/** 自 DB 讀取當下的權限（spec §2.3：每次請求依當下名單判斷，不跨請求快取） */
	public Grants load(String userId) {
		return new Grants(new HashSet<>(permDao.findPerms(userId)), permDao.findRoleIds(userId),
				permDao.findExecCatgs(userId));
	}

	// ------------------------------------------------------------
	// 權限碼
	// ------------------------------------------------------------

	public boolean can(Identity id, String permId) {
		return id != null && permId != null && id.getPermissions().contains(permId);
	}

	public void require(Identity id, String permId) {
		if (!can(id, permId)) {
			throw AcctMsg.forbidden();
		}
	}

	public void requireAny(Identity id, String... permIds) {
		for (String p : permIds) {
			if (can(id, p)) {
				return;
			}
		}
		throw AcctMsg.forbidden();
	}

	/** 實際使用者是否有權限（SYS_IMPERSONATE：切換中失去此權限也能切回或改切別人，spec §2.5） */
	public boolean realCan(Identity id, String permId) {
		if (!id.isImpersonating()) {
			return can(id, permId);
		}
		return permDao.findPerms(id.getRealUserId()).contains(permId);
	}

	public boolean isExecutor(Identity id, String catgId) {
		return id != null && catgId != null && id.getExecCatgIds().contains(catgId);
	}

	// ------------------------------------------------------------
	// 申請單關卡（spec §4.5）
	// ------------------------------------------------------------

	/** 我是否有關卡 2～5 任一關的核准權限 */
	public boolean hasAnyApprPerm(Identity id, Map<Integer, ApprStage> stages) {
		for (int s = AcctConst.STAGE_FIRST_APPR; s <= AcctConst.STAGE_LAST_APPR; s++) {
			if (can(id, CodeService.stagePermId(stages, s))) {
				return true;
			}
		}
		return false;
	}

	/** 「此刻能處理」：申請單在該關且符合處理者條件 */
	public boolean canHandleNow(Identity id, AcctReq r, Map<Integer, ApprStage> stages) {
		String state = r.getReqStateCode();
		Integer stage = r.getCurrStageId();
		if (stage == null) {
			return false;
		}
		if (AcctConst.RS_REVIEW.equals(state) && stage >= AcctConst.STAGE_FIRST_APPR && stage <= AcctConst.STAGE_LAST_APPR) {
			return can(id, CodeService.stagePermId(stages, stage));
		}
		if (AcctConst.RS_EXEC.equals(state) && stage == AcctConst.STAGE_EXEC) {
			return isExecutor(id, r.getCatgId());
		}
		if (AcctConst.RS_VERIFY.equals(state) && stage == AcctConst.STAGE_VERIFY) {
			return can(id, CodeService.stagePermId(stages, AcctConst.STAGE_VERIFY));
		}
		return false;
	}

	// ------------------------------------------------------------
	// 資料範圍（spec §2.4）
	// ------------------------------------------------------------

	/** 可看申請單：申請人、ACC_LIST_FULL、在途且此刻能處理、曾在此單操作過（ACT_BY 或 ACT_AS_ID） */
	public boolean canViewRequest(Identity id, AcctReq r, Map<Integer, ApprStage> stages) {
		if (id.getUserId().equals(r.getApplyBy()) || can(id, AcctConst.PERM_ACC_LIST_FULL)) {
			return true;
		}
		if (AcctConst.RS_IN_FLIGHT.contains(r.getReqStateCode()) && canHandleNow(id, r, stages)) {
			return true;
		}
		return acctHistDao.countReqActor(r.getReqId(), id.getUserId()) > 0;
	}

	/**
	 * 可看帳號：ACC_LIST_FULL、原新增申請人、曾對該帳號送過申請、可看該帳號的任一張申請單（此刻能處理在途單或曾操作過）、
	 * 在到期提醒範圍且有 ACC_EXPIRY_ALL 或是該類別執行人
	 */
	public boolean canViewAccount(Identity id, Acct a, Map<Integer, ApprStage> stages, LocalDate today) {
		if (can(id, AcctConst.PERM_ACC_LIST_FULL) || id.getUserId().equals(a.getApplyBy())) {
			return true;
		}
		if (inExpiringRange(a.getAcctStateCode(), a.getExpiryTypeCode(), a.getExpiryDate() == null ? null
				: a.getExpiryDate().toLocalDate(), today) && expiringRelatedByRole(id, a.getCatgId())) {
			return true;
		}
		if (acctReqDao.countAcctInvolved(a.getAcctId(), id.getUserId()) > 0) {
			return true;
		}
		AcctReq inFlight = acctReqDao.findInFlightByAcct(a.getAcctId());
		return inFlight != null && canHandleNow(id, inFlight, stages);
	}

	/** 鈴鐺逐筆相關（spec §4.13）：ACC_EXPIRY_ALL、原新增申請人、該類別執行人 */
	public boolean expiringRelated(Identity id, String applyBy, String catgId) {
		return id.getUserId().equals(applyBy) || expiringRelatedByRole(id, catgId);
	}

	private boolean expiringRelatedByRole(Identity id, String catgId) {
		return can(id, AcctConst.PERM_ACC_EXPIRY_ALL) || isExecutor(id, catgId);
	}

	/** 到期提醒範圍（與 AcctDao.EXPIRING_COND 同口徑）：APPROVED＋DATE＋到期日 ≤ today＋30，或 EXPIRED */
	public static boolean inExpiringRange(String acctStateCode, String expiryTypeCode, LocalDate expiryDate, LocalDate today) {
		if (AcctConst.AS_EXPIRED.equals(acctStateCode)) {
			return true;
		}
		return AcctConst.AS_APPROVED.equals(acctStateCode) && AcctConst.EXPIRY_DATE.equals(expiryTypeCode) && expiryDate != null
				&& !expiryDate.isAfter(today.plusDays(AcctConst.EXPIRY_WARN_DAYS));
	}
}
