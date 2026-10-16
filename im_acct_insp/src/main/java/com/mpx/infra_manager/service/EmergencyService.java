package com.mpx.infra_manager.service;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: 新增：緊急存取送出與查詢（BACKLOG 第 46 項 S2；SA §5.11）
//           送出需 EMG_APPLY：必填（E10）→ 欄寬（帳號或系統 200、緊急事由 500；E11）→ 預估時長整數 1～24 → 類別須存在（STATUS＝1，
//           不看 IS_ENABLED；否則 400「欄位內容不正確：帳號類別」）；同一交易取號 EA → INSERT，SUBMIT_DATE＝現在（到秒）、
//           DUE_DATE＝＋24 小時、APPLY_BY＝有效身分、CREATE_BY＝實際操作人；交易提交後寄 EMG_SUBMIT 給所有 EMG_DECIDE 持有人
//           列表與明細登入即可：有 EMG_VIEW_ALL 看全部，否則只看 APPLY_BY＝有效身分的單；明細查無或無權看一律 404（裁示①A）；
//           狀態篩選值不在五種計算狀態回 400；日期篩選用 Texts.parseDate
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S4a：申請人回報 report（本人即可、只一次、主管處理前、逾期後仍可）：「異動」須 1～20 個受影響帳號、
//           回報說明必填，帳號說明選填；「沒有異動」不得帶帳號；帳號以單號＋完整名稱＋三種狀態驗證（S4-⑥，R8）；
//           樂觀鎖帶 versionNo（S4-⑰）；子表 REPORT 列與主表回報同一交易；回報不寄信；明細與回報共用 load（404）
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S4b-1：主管補核准 approve／退回 returnEmg（EMG_DECIDE，S4-⑬；逾期後仍可處理）：
//           共用 decide——404 → 403 → 欄位（退回原因必填 E10、500 字 E11）→ E02 版本號 → 409（已處理、版本不符，S4-⑰）；
//           補核准須先有申請人回報，否則 400（S4-⑦）；DECIDE_BY 有效身分、DECIDE_ACT_BY 實際操作人、IS_SELF_DECIDE＝處理人即申請人；
//           交易內先 UPDATE 主表（0 筆→409 回滾）再讀回報帳號：回報為「異動」才每帳號寫一列 EMG_MODIFY 歷史（S4-⑧⑨⑩），
//           退回時再逐帳號標審查異常旗標（S4-①，同帳號第二次直接覆蓋）；補核准不標旗標；退回提交後才寄 EMG_RETURN 給申請人（S4-⑪）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S6：異常結案 close（EMG_DECIDE，裁示⑥；只限已退回且未結案）：結案發現 NO_CHANGE／CHANGED、
//           結案說明兩種都必填（S6-①）；CHANGED 帳號規則同回報（1～20、不重複、單號＋名稱＋狀態，S4-③⑥），
//           每帳號寫子表 CLOSE 列、EMG_CLOSE_MODIFY 歷史（自行結案標記，S6-③）並標旗標（原因「{單號} 異常結案發現異動：…」，S6-②）；
//           IS_SELF_CLOSE＝結案人即申請人（㉖）；不寄信。回報與結案共用帳號檢查 pickAffects／verifyAffects（驗證順序不變）；
//           結案帳號照單號排序後才寫入，與退回（findAffects 依 ACCT_ID 排序）同一上鎖順序，避免兩者並發時死結
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S8：列表狀態篩選改依 AcctConst.EMG_STATE_FILTERS（多收 UNDECIDED＝待補核准＋逾期，首頁卡點進來用）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// ============================================================

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.mpx.infra_manager.common.AcctConst;
import com.mpx.infra_manager.common.AcctMsg;
import com.mpx.infra_manager.common.ApiException;
import com.mpx.infra_manager.common.DocSeqService;
import com.mpx.infra_manager.common.Identity;
import com.mpx.infra_manager.common.Texts;
import com.mpx.infra_manager.dao.AcctDao;
import com.mpx.infra_manager.dao.AcctHistDao;
import com.mpx.infra_manager.dao.EmgDao;
import com.mpx.infra_manager.model.Acct;
import com.mpx.infra_manager.model.AcctHist;
import com.mpx.infra_manager.model.Emg;
import com.mpx.infra_manager.model.EmgActionResult;
import com.mpx.infra_manager.model.EmgAffectAcct;
import com.mpx.infra_manager.model.EmgDetail;
import com.mpx.infra_manager.model.EmgIdResult;
import com.mpx.infra_manager.model.request.EmgCloseForm;
import com.mpx.infra_manager.model.request.EmgForm;
import com.mpx.infra_manager.model.request.EmgReportForm;
import com.mpx.infra_manager.model.request.ReasonForm;
import com.mpx.infra_manager.model.request.VersionForm;

@Service
public class EmergencyService {

	private static final Logger log = LoggerFactory.getLogger(EmergencyService.class);

	static final int TARGET_MAX = 200;
	static final int REASON_MAX = 500;
	static final String LABEL_STATE = "狀態";
	static final int REPORT_DESCR_MAX = 500;
	static final int AFFECT_DESCR_MAX = 500;
	static final String LABEL_VERSION = "版本號";
	static final List<String> REPORT_TYPES = List.of(AcctConst.EMG_REPORT_NONE, AcctConst.EMG_REPORT_MODIFIED);
	/** 受影響帳號可選的帳號狀態（D29＋㉑；與 AcctDao.FIND_LOOKUP 同範圍） */
	static final List<String> AFFECT_STATES = List.of(AcctConst.AS_APPROVED, AcctConst.AS_EXPIRED, AcctConst.AS_DISABLED);
	static final int RETURN_REASON_MAX = 500;
	/** IM_ACCT.REVIEW_FLAG_REASON 欄寬 */
	static final int FLAG_REASON_MAX = 500;
	/** IM_ACCT_HIST.RESULT_DESCR 欄寬（與 RequestService 同） */
	static final int RESULT_DESCR_MAX = 1000;
	static final String LABEL_APPROVED = "已補核准";
	static final String LABEL_RETURNED = "已退回";
	static final List<String> CLOSE_RESULTS = List.of(AcctConst.EMG_CLOSE_NO_CHANGE, AcctConst.EMG_CLOSE_CHANGED);
	static final int CLOSE_DESCR_MAX = 500;
	/** 結案標旗標的原因前綴（S6-②） */
	static final String LABEL_CLOSE_CHANGED = "異常結案發現異動";
	private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private final PermissionService perm;
	private final CodeService codeService;
	private final DocSeqService docSeqService;
	private final EmgDao emgDao;
	private final AcctDao acctDao;
	private final AcctHistDao acctHistDao;
	private final NotificationService notificationService;
	private final TransactionTemplate tx;
	private final Clock clock;

	public EmergencyService(PermissionService perm, CodeService codeService, DocSeqService docSeqService, EmgDao emgDao,
			AcctDao acctDao, AcctHistDao acctHistDao, NotificationService notificationService, TransactionTemplate tx,
			Clock clock) {
		this.perm = perm;
		this.codeService = codeService;
		this.docSeqService = docSeqService;
		this.emgDao = emgDao;
		this.acctDao = acctDao;
		this.acctHistDao = acctHistDao;
		this.notificationService = notificationService;
		this.tx = tx;
		this.clock = clock;
	}

	/** 送出緊急存取（EMG_APPLY）；回 {emgId} */
	public EmgIdResult create(Identity id, EmgForm form) {
		perm.require(id, AcctConst.PERM_EMG_APPLY);
		EmgForm f = form == null ? new EmgForm() : form;
		String catgId = Texts.trimToNull(f.getCatgId());
		if (catgId == null) {
			throw AcctMsg.fillIn(AcctMsg.WHAT_EMG_CATG);
		}
		String target = Texts.trimToNull(f.getTargetDescr());
		if (target == null) {
			throw AcctMsg.fillIn(AcctMsg.WHAT_EMG_TARGET);
		}
		String reason = Texts.trimToNull(f.getApplyReason());
		if (reason == null) {
			throw AcctMsg.fillIn(AcctMsg.WHAT_EMG_REASON);
		}
		Integer hours = f.getExpectHourQty();
		if (hours == null) {
			throw AcctMsg.fillIn(AcctMsg.WHAT_EMG_HOURS);
		}
		Texts.maxChars(AcctMsg.WHAT_EMG_TARGET, target, TARGET_MAX);
		Texts.maxChars(AcctMsg.WHAT_EMG_REASON, reason, REASON_MAX);
		if (hours < AcctConst.EMG_HOUR_MIN || hours > AcctConst.EMG_HOUR_MAX) {
			throw AcctMsg.badCode(AcctMsg.WHAT_EMG_HOURS);
		}
		// 類別只要存在（STATUS＝1）即可；IS_ENABLED 是「開放帳號申請」的開關，緊急存取不受限
		if (codeService.findCategory(catgId) == null) {
			throw AcctMsg.badCode(AcctMsg.WHAT_EMG_CATG);
		}

		LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
		Emg e = new Emg();
		e.setCatgId(catgId);
		e.setTargetDescr(target);
		e.setApplyBy(id.getUserId());
		e.setApplyReason(reason);
		e.setExpectHourQty(hours);
		e.setSubmitDate(now);
		e.setDueDate(now.plusHours(AcctConst.EMG_DUE_HOURS));

		String emgId = tx.execute(s -> {
			String n = docSeqService.nextId(AcctConst.DOC_EA, id.getRealUserId());
			e.setEmgId(n);
			emgDao.insert(e, id.getRealUserId());
			return n;
		});
		log.info("緊急存取送出 emgId={} applyBy={} realUser={}", emgId, id.getUserId(), id.getRealUserId());
		notificationService.emgEvent(AcctConst.EV_EMG_SUBMIT, emgId, id.getRealUserId());

		EmgIdResult r = new EmgIdResult();
		r.setEmgId(emgId);
		return r;
	}

	/** 列表（登入即可）：無 EMG_VIEW_ALL 只看自己申請的 */
	public List<Emg> list(Identity id, String keyword, String stateCode, String dateFrom, String dateTo) {
		String state = Texts.trimToNull(stateCode);
		if (state != null && !AcctConst.EMG_STATE_FILTERS.contains(state)) {
			throw AcctMsg.badCode(LABEL_STATE);
		}
		String applyBy = perm.can(id, AcctConst.PERM_EMG_VIEW_ALL) ? null : id.getUserId();
		return emgDao.findList(applyBy, Texts.escapeLike(Texts.trimToNull(keyword)), state, Texts.parseDate(dateFrom),
				Texts.parseDate(dateTo), LocalDateTime.now(clock));
	}

	/** 明細（登入即可）：查無或無權看一律 404 */
	public EmgDetail detail(Identity id, String emgId) {
		EmgDetail d = load(id, emgId);
		d.setAffectAccts(emgDao.findAffects(emgId));
		return d;
	}

	/**
	 * 申請人回報（本人即可，不需 EMG_APPLY，S4-⑬）；只能一次、主管處理前，逾期後仍可回報。
	 * 驗證順序（S4-⑭）：404 → 403 非本人 → E10 回報結果 → 值域 400 → E10 受影響帳號 → 400（「沒有異動」帶帳號、超過上限、
	 * 單號或名稱空白、重複）→ E10 回報說明 → E11 → 帳號驗證 400 → E02 版本號 → 409 E08（已回報、已處理、版本不符）→ 交易
	 */
	public EmgActionResult report(Identity id, String emgId, EmgReportForm form) {
		EmgDetail d = load(id, emgId);
		if (!id.getUserId().equals(d.getApplyBy())) {
			throw AcctMsg.forbidden();
		}
		EmgReportForm f = form == null ? new EmgReportForm() : form;
		String type = Texts.trimToNull(f.getReportTypeCode());
		if (type == null) {
			throw AcctMsg.fillIn(AcctMsg.WHAT_EMG_REPORT_TYPE);
		}
		if (!REPORT_TYPES.contains(type)) {
			throw AcctMsg.badCode(AcctMsg.WHAT_EMG_REPORT_TYPE);
		}
		boolean modified = AcctConst.EMG_REPORT_MODIFIED.equals(type);
		List<EmgReportForm.AffectAcct> picked = pickAffects(f.getAffectAccts(), modified);
		// 「異動」時整體說明必填、各帳號說明選填；「沒有異動」皆選填（S4-②）
		String descr = Texts.trimToNull(f.getReportDescr());
		if (modified && descr == null) {
			throw AcctMsg.fillIn(AcctMsg.WHAT_EMG_REPORT_DESCR);
		}
		Texts.maxChars(AcctMsg.WHAT_EMG_REPORT_DESCR, descr, REPORT_DESCR_MAX);
		verifyAffects(picked);
		Integer v = requireVersion(f.getVersionNo());
		if (d.getReportTypeCode() != null || d.getDecideCode() != null || !v.equals(d.getVersionNo())) {
			throw AcctMsg.concurrent();
		}

		LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
		tx.executeWithoutResult(s -> {
			if (emgDao.report(emgId, v, id.getUserId(), type, descr, now, id.getRealUserId()) != 1) {
				throw AcctMsg.concurrent();
			}
			for (EmgReportForm.AffectAcct p : picked) {
				emgDao.insertAffect(emgId, AcctConst.AFFECT_REPORT, p.getAcctId(), p.getAffectDescr(), id.getUserId(), now,
						id.getRealUserId());
			}
		});
		log.info("緊急存取回報 emgId={} type={} accts={} realUser={}", emgId, type, picked.size(), id.getRealUserId());
		return result(emgId, v + 1);
	}

	/**
	 * 主管補核准（EMG_DECIDE）；逾期後仍可。須先有申請人回報（S4-⑦）：沒有回報只能退回。
	 * 順序：404 → 403 → E02 版本號 → 409（已處理、版本不符）→ 400 尚未回報 → 交易。
	 * 回報為「異動」時每個受影響帳號寫一列 EMG_MODIFY 歷史；補核准代表異動已獲追認，不標審查異常旗標
	 */
	public EmgActionResult approve(Identity id, String emgId, VersionForm form) {
		EmgDetail d = load(id, emgId);
		perm.require(id, AcctConst.PERM_EMG_DECIDE);
		Integer v = requireVersion(form == null ? null : form.getVersionNo());
		checkUndecided(d, v);
		if (d.getReportTypeCode() == null) {
			throw ApiException.badRequest(AcctMsg.EMG_APPROVE_NO_REPORT);
		}
		return decide(id, d, AcctConst.EMG_DECIDE_APPROVE, null, v);
	}

	/**
	 * 主管退回（EMG_DECIDE）；逾期後仍可、不要求先回報。順序：404 → 403 → E10 退回原因 → E11（500 字）→ E02 版本號 → 409 → 交易。
	 * 回報為「異動」時每帳號寫 EMG_MODIFY 歷史並標審查異常旗標（S4-①）；「沒有異動」或未回報不寫歷史、不標旗標。
	 * 交易提交後寄 EMG_RETURN 給申請人（S4-⑪）
	 */
	public EmgActionResult returnEmg(Identity id, String emgId, ReasonForm form) {
		EmgDetail d = load(id, emgId);
		perm.require(id, AcctConst.PERM_EMG_DECIDE);
		String reason = Texts.trimToNull(form == null ? null : form.getReason());
		if (reason == null) {
			throw AcctMsg.fillIn(AcctMsg.WHAT_EMG_RETURN_REASON);
		}
		Texts.maxChars(AcctMsg.WHAT_EMG_RETURN_REASON, reason, RETURN_REASON_MAX);
		Integer v = requireVersion(form.getVersionNo());
		checkUndecided(d, v);
		return decide(id, d, AcctConst.EMG_DECIDE_RETURN, reason, v);
	}

	/** 預檢：已處理或版本不符 → 409（真正的防線是 UPDATE_DECIDE 的 WHERE） */
	private static void checkUndecided(EmgDetail d, Integer v) {
		if (d.getDecideCode() != null || !v.equals(d.getVersionNo())) {
			throw AcctMsg.concurrent();
		}
	}

	/**
	 * 補核准與退回共用：同一交易內 UPDATE 主表（0 筆→409 回滾）→ 回報為「異動」才讀 REPORT 列，逐帳號寫歷史、退回再標旗標。
	 * 主表更新成功即表示讀取後沒人動過這張單（回報也會遞增版本），所以回報內容可直接用 d 的值
	 */
	private EmgActionResult decide(Identity id, EmgDetail d, String decideCode, String returnReason, int v) {
		String emgId = d.getEmgId();
		boolean returned = AcctConst.EMG_DECIDE_RETURN.equals(decideCode);
		// 裁示⑤：處理人（有效身分）就是申請人
		int self = id.getUserId().equals(d.getApplyBy()) ? 1 : 0;
		LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
		String flagReason = returned ? Texts.truncate(emgId + " " + LABEL_RETURNED + "：" + returnReason, FLAG_REASON_MAX) : null;

		int written = tx.execute(s -> {
			if (emgDao.decide(emgId, v, decideCode, id.getUserId(), returnReason, self, now, id.getRealUserId()) != 1) {
				throw AcctMsg.concurrent();
			}
			if (!AcctConst.EMG_REPORT_MODIFIED.equals(d.getReportTypeCode())) {
				return 0;
			}
			int n = 0;
			for (EmgAffectAcct a : emgDao.findAffects(emgId)) {
				if (!AcctConst.AFFECT_REPORT.equals(a.getAffectTypeCode())) {
					continue;
				}
				acctHistDao.insert(emgHist(id, d, a, decideCode, returnReason, self, now));
				if (returned) {
					// 帳號不存在會更新 0 筆：不能讓單已退回、旗標卻沒標（稽核欄位不得靜默不一致），整筆回滾
					int rows = acctDao.flagReview(a.getAcctId(), flagReason, AcctConst.FLAG_SRC_EMG, id.getUserId(), now,
							id.getRealUserId());
					if (rows != 1) {
						log.error("標審查異常旗標影響筆數 {}（預期 1），交易回復 emgId={} acctId={}", rows, emgId, a.getAcctId());
						throw ApiException.internal("資料寫入未完成（單號 " + emgId + "），請聯絡系統管理員");
					}
				}
				n++;
			}
			return n;
		});
		log.info("緊急存取處理 emgId={} decide={} self={} hist={} realUser={}", emgId, decideCode, self, written, id.getRealUserId());
		if (returned) {
			notificationService.emgEvent(AcctConst.EV_EMG_RETURN, emgId, id.getRealUserId());
		}
		return result(emgId, v + 1);
	}

	/**
	 * EMG_MODIFY 歷史列（S4-⑧⑨⑩）：SOURCE_CODE＝EMG、EMG_ID＝單號、REQ_ID／STAGE_ID／APPLY_DEPT 空；
	 * ACT_BY＝實際操作人、ACT_AS＝代理身分、IS_SELF_ACTION＝處理人即申請人；
	 * RESULT_DESCR 寫單號、處理結果（退回含原因）、申請人、回報時間、整體說明與本帳號說明，超過 1000 字截斷
	 */
	private static AcctHist emgHist(Identity id, EmgDetail d, EmgAffectAcct a, String decideCode, String returnReason, int self,
			LocalDateTime now) {
		StringBuilder sb = new StringBuilder();
		sb.append("緊急存取 ").append(d.getEmgId()).append(' ');
		if (AcctConst.EMG_DECIDE_RETURN.equals(decideCode)) {
			sb.append(LABEL_RETURNED).append("（").append(returnReason).append("）");
		} else {
			sb.append(LABEL_APPROVED);
		}
		sb.append("；申請人 ").append(d.getApplyByName() != null ? d.getApplyByName() : d.getApplyBy());
		if (d.getReportDate() != null) {
			sb.append(" 於 ").append(DATE_TIME_FMT.format(d.getReportDate()));
		}
		// 回報「異動」時說明必填（S4a），這裡只防資料被手動改壞時寫出字面 null（歷史只增不改）
		sb.append(" 回報異動：").append(Objects.toString(d.getReportDescr(), ""));
		if (a.getAffectDescr() != null) {
			sb.append("；本帳號：").append(a.getAffectDescr());
		}

		AcctHist h = new AcctHist();
		h.setAcctId(a.getAcctId());
		h.setEmgId(d.getEmgId());
		h.setSourceCode(AcctConst.HIST_SRC_EMG);
		h.setActionCode(AcctConst.ACT_EMG_MODIFY);
		h.setActBy(id.getRealUserId());
		h.setActByName(id.getRealUserName());
		h.setActAsId(id.actAsId());
		h.setActAsName(id.actAsName());
		h.setActDate(now);
		h.setResultDescr(Texts.truncate(sb.toString(), RESULT_DESCR_MAX));
		h.setIsSelfAction(self);
		h.setCreateBy(id.getRealUserId());
		return h;
	}

	/**
	 * 異常結案（EMG_DECIDE 任一人，裁示⑥）；只有已退回且尚未結案的單可結案（D13）。
	 * 順序：404 → 403 → E10 結案發現 → 值域 400 → E10 受影響帳號 → 400（「沒有異動」帶帳號、超過上限、單號或名稱空白、重複）
	 * → E10 結案說明（兩種結果都必填，S6-①）→ E11 → 帳號驗證 400 → E02 版本號 → 409 E08（未退回、已結案、版本不符）→ 交易。
	 * 「有異動」時每帳號寫子表 CLOSE 列、EMG_CLOSE_MODIFY 歷史並標審查異常旗標（S6-②）；申請人回報的 REPORT 列不動。
	 * 「沒有異動」只更新主表，既有旗標不清（清除依 D42，S10）。結案不寄信（裁示⑨）
	 */
	public EmgActionResult close(Identity id, String emgId, EmgCloseForm form) {
		EmgDetail d = load(id, emgId);
		perm.require(id, AcctConst.PERM_EMG_DECIDE);
		EmgCloseForm f = form == null ? new EmgCloseForm() : form;
		String closeCode = Texts.trimToNull(f.getCloseResultCode());
		if (closeCode == null) {
			throw AcctMsg.fillIn(AcctMsg.WHAT_EMG_CLOSE_RESULT);
		}
		if (!CLOSE_RESULTS.contains(closeCode)) {
			throw AcctMsg.badCode(AcctMsg.WHAT_EMG_CLOSE_RESULT);
		}
		boolean changed = AcctConst.EMG_CLOSE_CHANGED.equals(closeCode);
		List<EmgReportForm.AffectAcct> picked = pickAffects(f.getAffectAccts(), changed);
		String descr = Texts.trimToNull(f.getCloseDescr());
		if (descr == null) {
			throw AcctMsg.fillIn(AcctMsg.WHAT_EMG_CLOSE_DESCR);
		}
		Texts.maxChars(AcctMsg.WHAT_EMG_CLOSE_DESCR, descr, CLOSE_DESCR_MAX);
		verifyAffects(picked);
		Integer v = requireVersion(f.getVersionNo());
		if (!AcctConst.EMG_DECIDE_RETURN.equals(d.getDecideCode()) || d.getCloseDate() != null || !v.equals(d.getVersionNo())) {
			throw AcctMsg.concurrent();
		}

		// 與退回（findAffects 依 ACCT_ID 排序）同一順序鎖 IM_ACCT 列，兩者並發時才不會互等成死結
		picked.sort(Comparator.comparing(EmgReportForm.AffectAcct::getAcctId));
		// 裁示㉖：結案人（有效身分）就是申請人
		int self = id.getUserId().equals(d.getApplyBy()) ? 1 : 0;
		LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
		String flagReason = Texts.truncate(emgId + " " + LABEL_CLOSE_CHANGED + "：" + descr, FLAG_REASON_MAX);
		tx.executeWithoutResult(s -> {
			if (emgDao.close(emgId, v, closeCode, descr, id.getUserId(), self, now, id.getRealUserId()) != 1) {
				throw AcctMsg.concurrent();
			}
			for (EmgReportForm.AffectAcct p : picked) {
				emgDao.insertAffect(emgId, AcctConst.AFFECT_CLOSE, p.getAcctId(), p.getAffectDescr(), id.getUserId(), now,
						id.getRealUserId());
				acctHistDao.insert(closeHist(id, emgId, p, descr, self, now));
				// 同 decide：旗標沒標上就整筆回滾，不讓結案登記與旗標不一致
				int rows = acctDao.flagReview(p.getAcctId(), flagReason, AcctConst.FLAG_SRC_EMG, id.getUserId(), now,
						id.getRealUserId());
				if (rows != 1) {
					log.error("標審查異常旗標影響筆數 {}（預期 1），交易回復 emgId={} acctId={}", rows, emgId, p.getAcctId());
					throw ApiException.internal("資料寫入未完成（單號 " + emgId + "），請聯絡系統管理員");
				}
			}
		});
		log.info("緊急存取異常結案 emgId={} result={} self={} accts={} realUser={}", emgId, closeCode, self, picked.size(),
				id.getRealUserId());
		return result(emgId, v + 1);
	}

	/**
	 * EMG_CLOSE_MODIFY 歷史列：欄位寫法同 EMG_MODIFY（S4-⑧）；IS_SELF_ACTION＝結案人即申請人（標籤「自行結案」，S6-③）；
	 * RESULT_DESCR 寫單號、結案發現、結案人（有效身分）、結案說明與本帳號說明，超過 1000 字截斷
	 */
	private static AcctHist closeHist(Identity id, String emgId, EmgReportForm.AffectAcct p, String closeDescr, int self,
			LocalDateTime now) {
		StringBuilder sb = new StringBuilder();
		sb.append("緊急存取 ").append(emgId).append(" 異常結案：有帳號異動；結案人 ")
				.append(id.getUserName() != null ? id.getUserName() : id.getUserId())
				.append("；結案說明：").append(closeDescr);
		if (p.getAffectDescr() != null) {
			sb.append("；本帳號：").append(p.getAffectDescr());
		}

		AcctHist h = new AcctHist();
		h.setAcctId(p.getAcctId());
		h.setEmgId(emgId);
		h.setSourceCode(AcctConst.HIST_SRC_EMG);
		h.setActionCode(AcctConst.ACT_EMG_CLOSE_MODIFY);
		h.setActBy(id.getRealUserId());
		h.setActByName(id.getRealUserName());
		h.setActAsId(id.actAsId());
		h.setActAsName(id.actAsName());
		h.setActDate(now);
		h.setResultDescr(Texts.truncate(sb.toString(), RESULT_DESCR_MAX));
		h.setIsSelfAction(self);
		h.setCreateBy(id.getRealUserId());
		return h;
	}

	/**
	 * 受影響帳號的格式檢查（回報與結案共用，S4-②③）：required＝「異動」時至少 1 個，否則不得帶帳號；上限 20；
	 * 單號或名稱空白、同帳號重複一律 400（不讓子表複合主鍵擋成 500）。回傳去頭尾空白後的新清單
	 */
	private static List<EmgReportForm.AffectAcct> pickAffects(List<EmgReportForm.AffectAcct> input, boolean required) {
		List<EmgReportForm.AffectAcct> accts = input == null ? List.of() : input;
		if (required && accts.isEmpty()) {
			throw AcctMsg.fillIn(AcctMsg.WHAT_EMG_AFFECT);
		}
		if (!required && !accts.isEmpty()) {
			throw AcctMsg.badCode(AcctMsg.WHAT_EMG_AFFECT);
		}
		// 上限 20，查帳號前先擋
		if (accts.size() > AcctConst.EMG_AFFECT_MAX) {
			throw AcctMsg.badCode(AcctMsg.WHAT_EMG_AFFECT);
		}
		List<EmgReportForm.AffectAcct> picked = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (EmgReportForm.AffectAcct a : accts) {
			String acctId = a == null ? null : Texts.trimToNull(a.getAcctId());
			String acctName = a == null ? null : Texts.trimToNull(a.getAcctName());
			if (acctId == null || acctName == null || !seen.add(acctId)) {
				throw AcctMsg.badCode(AcctMsg.WHAT_EMG_AFFECT);
			}
			EmgReportForm.AffectAcct p = new EmgReportForm.AffectAcct();
			p.setAcctId(acctId);
			p.setAcctName(acctName);
			p.setAffectDescr(Texts.trimToNull(a.getAffectDescr()));
			picked.add(p);
		}
		return picked;
	}

	/**
	 * 各帳號說明 E11（500 字）→ S4-⑥（R8）：單號＋完整名稱同時相符且狀態屬 D29 三種，與選取器 /accounts/lookup 同範圍；
	 * 不符一律同一句、不指出哪一筆或為何不符，避免以連號單號列舉帳號名稱
	 */
	private void verifyAffects(List<EmgReportForm.AffectAcct> picked) {
		for (EmgReportForm.AffectAcct p : picked) {
			Texts.maxChars(AcctMsg.WHAT_EMG_AFFECT_DESCR, p.getAffectDescr(), AFFECT_DESCR_MAX);
		}
		for (EmgReportForm.AffectAcct p : picked) {
			Acct a = acctDao.findById(p.getAcctId());
			if (a == null || !p.getAcctName().equals(a.getAcctName()) || !AFFECT_STATES.contains(a.getAcctStateCode())) {
				throw AcctMsg.badCode(AcctMsg.WHAT_EMG_AFFECT);
			}
		}
	}

	/** 查無或無權看一律 404（裁示①A） */
	EmgDetail load(Identity id, String emgId) {
		EmgDetail d = emgId == null ? null : emgDao.findById(emgId, LocalDateTime.now(clock));
		if (d == null || !canView(id, d)) {
			throw AcctMsg.notFound();
		}
		return d;
	}

	private static Integer requireVersion(Integer versionNo) {
		if (versionNo == null) {
			throw AcctMsg.required(LABEL_VERSION);
		}
		return versionNo;
	}

	private static EmgActionResult result(String emgId, int versionNo) {
		EmgActionResult r = new EmgActionResult();
		r.setEmgId(emgId);
		r.setVersionNo(versionNo);
		return r;
	}

	/** 有 EMG_VIEW_ALL、或申請人就是有效身分 */
	boolean canView(Identity id, Emg e) {
		return perm.can(id, AcctConst.PERM_EMG_VIEW_ALL) || id.getUserId().equals(e.getApplyBy());
	}
}
