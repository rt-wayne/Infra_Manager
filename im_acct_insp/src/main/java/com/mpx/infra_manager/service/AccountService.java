package com.mpx.infra_manager.service;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：帳號查詢（spec §4.2）與申請類型前提（spec §3.3）
//           列表需 ACCT_LIST；明細／歷史：ACCT_LIST、該帳號 APPLY_BY 本人、或曾對該帳號送過申請者；
//           lookup 回帳號狀態允許的 allowedReqTypes（不考慮在途，在途另以 inFlight 表示）
//           變更單 #1：新增 lookupById（任何有身分者、限 APPROVED／DISABLED，否則 404）；lookup 清單回精簡欄位；
//           LIKE 關鍵字以 escapeLike 跳脫 \ % _（SQL 端 ESCAPE '\'）；曾申請者含對其 ORIG_ACCT_ID 送過申請者（DAO 條件）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（spec_im_acct_insp_v2.0.md §2.4、§4.2、§4.13、§6.4；Table v0.2 6.5）：
//           權限改權限碼——/accounts 需 ACC_LIST_FULL（回 items＋stateCounts＋catgCounts＋expiringCount，可篩 expiring＝1）；
//           /accounts/mine、/lookup 需 ACC_SELF_SERVICE；/lookup 改 acctName 完整精確比對（必填，去前後空白、區分大小寫），
//           狀態 APPROVED／EXPIRED／DISABLED；/lookup/{id} 需 ACC_APPLY，且 acctName 精確相符或我可看該帳號，否則 404；
//           /accounts/{id}、/history 依 PermissionService.canViewAccount（不符 403）；
//           申請前提依 Table 6.5（加 EXPIRED、刪 EXTEND），allowedReqTypes 只在無在途時有值；
//           歷史附「自行核准／同人兼任」標籤；trimToNull、escapeLike 搬到 common.Texts
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// ============================================================

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import com.mpx.infra_manager.common.AcctConst;
import com.mpx.infra_manager.common.AcctMsg;
import com.mpx.infra_manager.common.Identity;
import com.mpx.infra_manager.common.Texts;
import com.mpx.infra_manager.dao.AcctDao;
import com.mpx.infra_manager.dao.AcctHistDao;
import com.mpx.infra_manager.model.Acct;
import com.mpx.infra_manager.model.AcctHist;
import com.mpx.infra_manager.model.AcctListResult;
import com.mpx.infra_manager.model.AcctLookup;
import com.mpx.infra_manager.model.AcctLookupBrief;
import com.mpx.infra_manager.model.CodeCountRow;

@Service
public class AccountService {

	private final PermissionService perm;
	private final CodeService codeService;
	private final AcctDao acctDao;
	private final AcctHistDao acctHistDao;
	private final Clock clock;

	public AccountService(PermissionService perm, CodeService codeService, AcctDao acctDao, AcctHistDao acctHistDao,
			Clock clock) {
		this.perm = perm;
		this.codeService = codeService;
		this.acctDao = acctDao;
		this.acctHistDao = acctHistDao;
		this.clock = clock;
	}

	/** GET /accounts（ACC_LIST_FULL） */
	public AcctListResult list(Identity id, String keyword, String catgId, String stateCode, String assetClassId,
			String expiring) {
		perm.require(id, AcctConst.PERM_ACC_LIST_FULL);
		String state = Texts.trimToNull(stateCode);
		if (state != null && !AcctConst.ACCT_STATES.contains(state)) {
			throw AcctMsg.badCode("狀態");
		}
		LocalDate today = LocalDate.now(clock);
		Map<String, Object> p = AcctDao.listParams(Texts.escapeLike(Texts.trimToNull(keyword)), Texts.trimToNull(catgId), state,
				Texts.trimToNull(assetClassId), "1".equals(Texts.trimToNull(expiring)), today);
		List<Acct> items = acctDao.findList(p);
		for (Acct a : items) {
			a.setAllowedReqTypes(allowedReqTypes(a.getAcctStateCode(), Boolean.TRUE.equals(a.getInFlight())));
		}
		AcctListResult r = new AcctListResult();
		r.setItems(items);
		r.setStateCounts(toMap(acctDao.countByState(p)));
		r.setCatgCounts(toMap(acctDao.countByCatg(p)));
		r.setExpiringCount(acctDao.countExpiring(today));
		return r;
	}

	/** GET /accounts/mine：我曾申請新增的帳號（狀態不是 REJECTED、DELETED） */
	public List<Acct> mine(Identity id) {
		perm.require(id, AcctConst.PERM_ACC_SELF_SERVICE);
		List<Acct> rows = acctDao.findMine(id.getUserId());
		for (Acct a : rows) {
			a.setAllowedReqTypes(allowedReqTypes(a.getAcctStateCode(), Boolean.TRUE.equals(a.getInFlight())));
		}
		return rows;
	}

	/** GET /accounts/lookup?acctName：完整精確比對，不提供模糊查詢或瀏覽 */
	public List<AcctLookupBrief> lookup(Identity id, String acctName) {
		perm.require(id, AcctConst.PERM_ACC_SELF_SERVICE);
		String name = Texts.trimToNull(acctName);
		if (name == null) {
			throw AcctMsg.required("帳號名稱");
		}
		List<AcctLookupBrief> rows = acctDao.findLookup(name);
		for (AcctLookupBrief a : rows) {
			a.setAllowedReqTypes(allowedReqTypes(a.getAcctStateCode(), Boolean.TRUE.equals(a.getInFlight())));
		}
		return rows;
	}

	/** GET /accounts/lookup/{id}：須 acctName 精確相符或我可看該帳號；狀態限 APPROVED／EXPIRED／DISABLED；否則 404 */
	public AcctLookup lookupById(Identity id, String acctId, String acctName) {
		perm.require(id, AcctConst.PERM_ACC_APPLY);
		String aid = Texts.trimToNull(acctId);
		Acct a = aid == null ? null : acctDao.findById(aid);
		if (a == null || !List.of(AcctConst.AS_APPROVED, AcctConst.AS_EXPIRED, AcctConst.AS_DISABLED).contains(a.getAcctStateCode())) {
			throw AcctMsg.notFound();
		}
		String name = Texts.trimToNull(acctName);
		boolean nameOk = name != null && name.equals(a.getAcctName());
		if (!nameOk && !perm.canViewAccount(id, a, codeService.stageMap(), LocalDate.now(clock))) {
			throw AcctMsg.notFound();
		}
		AcctLookup out = new AcctLookup();
		BeanUtils.copyProperties(a, out);
		out.setAllowedReqTypes(allowedReqTypes(a.getAcctStateCode(), Boolean.TRUE.equals(a.getInFlight())));
		return out;
	}

	/** GET /accounts/{id}：可看帳號者（spec §2.4），否則 403 */
	public Acct detail(Identity id, String acctId) {
		Acct a = acctId == null ? null : acctDao.findById(acctId);
		if (a == null) {
			throw AcctMsg.notFound();
		}
		if (!perm.canViewAccount(id, a, codeService.stageMap(), LocalDate.now(clock))) {
			throw AcctMsg.forbidden();
		}
		a.setAllowedReqTypes(allowedReqTypes(a.getAcctStateCode(), Boolean.TRUE.equals(a.getInFlight())));
		return a;
	}

	public List<AcctHist> history(Identity id, String acctId) {
		detail(id, acctId);
		return RequestService.withLabels(acctHistDao.findByAcct(acctId));
	}

	// ------------------------------------------------------------
	// 申請前提（Table 6.5）
	// ------------------------------------------------------------

	/** 依帳號狀態可提出的異動類型；有在途時為空（spec §6.4） */
	public static List<String> allowedReqTypes(String acctStateCode, boolean inFlight) {
		List<String> result = new ArrayList<>();
		if (inFlight) {
			return result;
		}
		for (String t : AcctConst.CHANGE_REQ_TYPES) {
			if (isAllowed(t, acctStateCode)) {
				result.add(t);
			}
		}
		return result;
	}

	public static boolean isAllowed(String reqTypeCode, String acctStateCode) {
		List<String> pre = AcctConst.REQ_PREREQ.get(reqTypeCode);
		return pre != null && pre.contains(acctStateCode);
	}

	private static Map<String, Long> toMap(List<CodeCountRow> rows) {
		Map<String, Long> m = new LinkedHashMap<>();
		for (CodeCountRow r : rows) {
			if (r.getCode() != null) {
				m.put(r.getCode(), r.getCnt() == null ? 0L : r.getCnt());
			}
		}
		return m;
	}
}
