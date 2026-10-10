package com.mpx.im_acct_insp.service;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：帳號申請單狀態機（spec §3.2～§3.6、§4.3）
//           REVIEW(1～4) → EXEC(5) → VERIFY(6) → APPROVED；REVIEW 可退件 → REJECTED，原申請人重新送出 → REVIEW(1)；
//           VERIFY 可複驗退回 → EXEC(5)、VERIFY_REJECT_CNT＋1
//           驗權順序：401 身分 → 404 單號 → 403 角色 → 409 狀態 → 400 內容
//           核准／退件：目前關卡不是我的、但我負責的關卡已過（例：同人連按兩次）回 409，否則回 403
//           無交易（spec §3.4）：先以帶條件的 UPDATE 改申請單狀態（影響 0 筆回 409），成功後才寫帳號主表，歷史最後寫；
//           後續步驟失敗記 error log 並回 500，不回滾
//           送出驗證順序（spec §4.3）：isUniqueConf＝1 → 必填 → DATE 時到期日必填，之後才檢查代碼值與帳號前提
//           變更單 #1：APPLY_TEL_NO／APPLY_EMAIL／HOLDER_EMAIL／CARD_NO 改以 UTF-8 byte 長度檢查；資產分類碼 IS_SHOW＝0 回 400；
//           門禁卡強制 assetClassId＝null；展延新帳號審查日期照抄舊帳號，舊帳號未起算且新內容符合時自今天起算
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0 重寫（spec_im_acct_insp_v2.0.md §4.2～§4.10、§5、§6.5）：
//           單號 AR；送出即建主表列（新增類型，REVIEW）；7 關：關卡 2～5 依 IM_ACCT_APPR_STAGE.PERM_ID 核准／退件，
//           關卡 6 該類別執行人回報執行完畢（佐證必填 E12、門禁卡新增卡號必填 E13），關卡 7 VERIFY_G7 複驗通過／退回；
//           主表寫入由複驗通過搬到關卡 6 第一次回報（各類型欄位依 §4.6），同交易寫審查日期（§4.7）；複驗退回不回復主表；
//           樂觀鎖：狀態轉移帶 VERSION_NO，0 筆 → 409 E08；同帳號在途先查、競態由 IND_IM_ACCT_REQ_04 擋（ORA-00001 → 409 E07）；
//           驗權順序：403（無任何相關權限）→ 400 E02（未帶 versionNo）→ 409（狀態／版本不符）→ 403（無該關權限）→ 400 內容；
//           交易邊界（§5）以 TransactionTemplate 包住多句寫入，附件先寫檔、交易失敗刪檔；通知在交易提交後寄出；
//           歷史寫 ACT_BY＝實際操作人、ACT_AS_*＝代理身分、IS_SELF_ACTION（§4.8）；業務欄位記有效身分；
//           刪展延、作廢、清審查異常標記；送出驗證依 §4.3（類型別必填欄位、E04～E06、欄寬 E11）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 變更單 #1（L2）：申請單位欄寬檢查（E11）移到 E03～E06 之後，與其他欄寬檢查同組（E11 與 E03 同時成立時回 E03）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 變更單 #2：僅修正檔頭 v2.0 驗權順序摘要，補「400 E02（未帶 versionNo）」（與 spec §4.4 定案一致；程式未改）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-10
// 變更說明: 送出／重新送出的 DuplicateKeyException 只在違反 IND_IM_ACCT_REQ_04（同帳號在途）時回 409 E07；
//           其他唯一鍵衝突記 error log（含交易內取到的單號）後原樣往上拋（500），不再誤報在途；
//           判斷依 ORA-00001 訊息中的「.IND_IM_ACCT_REQ_04)」
// ============================================================

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.mpx.im_acct_insp.common.AcctConst;
import com.mpx.im_acct_insp.common.AcctMsg;
import com.mpx.im_acct_insp.common.ApiException;
import com.mpx.im_acct_insp.common.DocSeqService;
import com.mpx.im_acct_insp.common.Identity;
import com.mpx.im_acct_insp.common.Texts;
import com.mpx.im_acct_insp.dao.AcctDao;
import com.mpx.im_acct_insp.dao.AcctHistDao;
import com.mpx.im_acct_insp.dao.AcctReqDao;
import com.mpx.im_acct_insp.dao.AttachDao;
import com.mpx.im_acct_insp.model.Acct;
import com.mpx.im_acct_insp.model.AcctHist;
import com.mpx.im_acct_insp.model.AcctReq;
import com.mpx.im_acct_insp.model.AcctReqDetail;
import com.mpx.im_acct_insp.model.ApprStage;
import com.mpx.im_acct_insp.model.AssetClass;
import com.mpx.im_acct_insp.model.Category;
import com.mpx.im_acct_insp.model.ReqActionResult;
import com.mpx.im_acct_insp.model.ReqIdResult;
import com.mpx.im_acct_insp.model.ReqListItem;
import com.mpx.im_acct_insp.model.request.AcctReqForm;
import com.mpx.im_acct_insp.model.request.ExecDoneForm;
import com.mpx.im_acct_insp.model.request.ReasonForm;
import com.mpx.im_acct_insp.model.request.VersionForm;

@Service
public class RequestService {

	private static final Logger log = LoggerFactory.getLogger(RequestService.class);

	// 明細「我此刻可做」的動作（spec §6.5）
	public static final String DO_APPROVE = "APPROVE";
	public static final String DO_REJECT = "REJECT";
	public static final String DO_EXEC_DONE = "EXEC_DONE";
	public static final String DO_VERIFY_OK = "VERIFY_OK";
	public static final String DO_VERIFY_REJECT = "VERIFY_REJECT";
	public static final String DO_RESUBMIT = "RESUBMIT";

	static final int RESULT_DESCR_MAX = 1000;
	static final String LABEL_VERSION = "版本號";
	/** 同帳號一筆在途的唯一函式索引；ORA-00001 訊息含此名稱才是在途衝突 */
	static final String IDX_IN_FLIGHT = "IND_IM_ACCT_REQ_04";

	private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("uuuu-MM-dd")
			.withResolverStyle(ResolverStyle.STRICT);

	private final PermissionService perm;
	private final CodeService codeService;
	private final DocSeqService docSeqService;
	private final AcctReqDao acctReqDao;
	private final AcctDao acctDao;
	private final AcctHistDao acctHistDao;
	private final AttachDao attachDao;
	private final AttachmentService attachmentService;
	private final NotificationService notificationService;
	private final TransactionTemplate tx;
	private final Clock clock;

	public RequestService(PermissionService perm, CodeService codeService, DocSeqService docSeqService, AcctReqDao acctReqDao,
			AcctDao acctDao, AcctHistDao acctHistDao, AttachDao attachDao, AttachmentService attachmentService,
			NotificationService notificationService, TransactionTemplate tx, Clock clock) {
		this.perm = perm;
		this.codeService = codeService;
		this.docSeqService = docSeqService;
		this.acctReqDao = acctReqDao;
		this.acctDao = acctDao;
		this.acctHistDao = acctHistDao;
		this.attachDao = attachDao;
		this.attachmentService = attachmentService;
		this.notificationService = notificationService;
		this.tx = tx;
		this.clock = clock;
	}

	// ------------------------------------------------------------
	// 查詢
	// ------------------------------------------------------------

	/** GET /requests：我的申請單（APPLY_BY＝我） */
	public List<ReqListItem> listMine(Identity id, String stateCode) {
		perm.require(id, AcctConst.PERM_ACC_SELF_SERVICE);
		String state = Texts.trimToNull(stateCode);
		if (state != null && !AcctConst.REQ_STATES.contains(state)) {
			throw AcctMsg.badCode("狀態");
		}
		List<ReqListItem> rows = acctReqDao.findMine(id.getUserId(), state);
		for (ReqListItem r : rows) {
			r.setCanResubmit(AcctConst.RS_REJECTED.equals(r.getReqStateCode()) && id.getUserId().equals(r.getApplyBy()));
		}
		return rows;
	}

	/** GET /requests/{id}：可看申請單者（spec §2.4） */
	public AcctReqDetail detail(Identity id, String reqId) {
		AcctReqDetail r = load(reqId);
		Map<Integer, ApprStage> stages = codeService.stageMap();
		if (!perm.canViewRequest(id, r, stages)) {
			throw AcctMsg.forbidden();
		}
		r.setAttachments(attachDao.findActiveByReq(r.getReqId()));
		r.setHistory(withLabels(acctHistDao.findByReq(r.getReqId())));
		r.setActions(actions(id, r, stages));
		return r;
	}

	/** 我此刻可做的動作 */
	List<String> actions(Identity id, AcctReq r, Map<Integer, ApprStage> stages) {
		List<String> list = new ArrayList<>();
		String state = r.getReqStateCode();
		if (AcctConst.RS_REJECTED.equals(state)) {
			if (id.getUserId().equals(r.getApplyBy()) && perm.can(id, AcctConst.PERM_ACC_APPLY)) {
				list.add(DO_RESUBMIT);
			}
		} else if (perm.canHandleNow(id, r, stages)) {
			if (AcctConst.RS_REVIEW.equals(state)) {
				list.add(DO_APPROVE);
				list.add(DO_REJECT);
			} else if (AcctConst.RS_EXEC.equals(state)) {
				list.add(DO_EXEC_DONE);
			} else if (AcctConst.RS_VERIFY.equals(state)) {
				list.add(DO_VERIFY_OK);
				list.add(DO_VERIFY_REJECT);
			}
		}
		return list;
	}

	// ------------------------------------------------------------
	// 關卡 1：送出／重新送出
	// ------------------------------------------------------------

	public ReqIdResult submit(Identity id, AcctReqForm form, MultipartFile docFile) {
		perm.require(id, AcctConst.PERM_ACC_APPLY);
		AcctReqForm f = form == null ? new AcctReqForm() : form;
		if (!Integer.valueOf(1).equals(f.getIsUniqueConf())) {
			throw ApiException.badRequest(AcctMsg.E01);
		}
		String reqType = Texts.trimToNull(f.getReqTypeCode());
		if (reqType == null) {
			throw AcctMsg.required("申請類型");
		}
		if (!AcctConst.REQ_TYPES.contains(reqType)) {
			throw AcctMsg.badCode("申請類型");
		}
		boolean isAdd = AcctConst.REQ_ADD.equals(reqType);
		String acctId = Texts.trimToNull(f.getAcctId());
		Acct target = null;
		if (isAdd) {
			if (acctId != null) {
				throw ApiException.badRequest(AcctMsg.ADD_WITH_ACCT);
			}
		} else {
			if (acctId == null) {
				throw AcctMsg.required("帳號");
			}
			target = acctDao.findById(acctId);
			if (target == null) {
				throw AcctMsg.notFound();
			}
		}
		AcctReq c = buildContent(f, reqType, target);
		AttachmentService.PendingFile doc = attachmentService.check(docFile);
		if (!isAdd) {
			checkPrereq(reqType, target);
			if (acctReqDao.countInFlight(acctId, null) > 0) {
				throw ApiException.conflict(AcctMsg.E07);
			}
		}
		c.setReqTypeCode(reqType);
		c.setApplyBy(id.getUserId());
		c.setAcctId(acctId);
		LocalDateTime now = LocalDateTime.now(clock);
		List<Path> written = new ArrayList<>();
		String reqId;
		try {
			// 交易：取號 → INSERT 申請單 → 新增類型 INSERT 主表 → INSERT 公文附件列 → INSERT 歷史 SUBMIT（spec §5）
			reqId = tx.execute(s -> {
				String newId = docSeqService.nextId(AcctConst.DOC_AR, id.getRealUserId());
				c.setReqId(newId);
				if (isAdd) {
					c.setAcctId(newId);
				}
				must(acctReqDao.insert(c, now, id.getRealUserId()), newId, "建立申請單");
				if (isAdd) {
					must(acctDao.insertOnSubmit(c, now, id.getRealUserId()), newId, "建立帳號主表");
				}
				if (doc != null) {
					attachmentService.store(newId, AcctConst.ATTACH_DOC, doc, written, now, id.getRealUserId());
				}
				acctHistDao.insert(hist(id, c, AcctConst.ACT_SUBMIT, AcctConst.STAGE_SUBMIT,
						"送出申請；原因：" + c.getApplyReason() + attachText(doc), 0, now));
				return newId;
			});
		} catch (DuplicateKeyException e) {
			attachmentService.cleanup(written);
			if (isInFlightDup(e)) {
				throw ApiException.conflict(AcctMsg.E07);
			}
			// 非同帳號在途的唯一鍵衝突是系統問題，不得誤報 E07；堆疊由 GlobalExceptionHandler 記錄
			// reqId 記交易內取到的單號（新增類型 acctId 為 null，主鍵重複時最需要這個號；取號前就失敗則為 null）
			log.error("送出申請單發生非在途索引的唯一鍵衝突 reqId={} acctId={} reqType={}", c.getReqId(), acctId, reqType);
			throw e;
		} catch (RuntimeException e) {
			attachmentService.cleanup(written);
			throw e;
		}
		log.info("申請單送出 reqId={} reqType={}", reqId, reqType);
		notificationService.requestEvent(AcctConst.EV_SUBMIT, reqId, id.getRealUserId());
		ReqIdResult result = new ReqIdResult();
		result.setReqId(reqId);
		return result;
	}

	public ReqActionResult resubmit(Identity id, String reqId, AcctReqForm form, MultipartFile docFile) {
		AcctReqDetail r = load(reqId);
		if (!id.getUserId().equals(r.getApplyBy()) || !perm.can(id, AcctConst.PERM_ACC_APPLY)) {
			throw AcctMsg.forbidden();
		}
		AcctReqForm f = form == null ? new AcctReqForm() : form;
		Integer v = requireVersion(f.getVersionNo());
		if (!AcctConst.RS_REJECTED.equals(r.getReqStateCode()) || !v.equals(r.getVersionNo())) {
			throw AcctMsg.concurrent();
		}
		if (!Integer.valueOf(1).equals(f.getIsUniqueConf())) {
			throw ApiException.badRequest(AcctMsg.E01);
		}
		String reqType = r.getReqTypeCode();
		boolean isAdd = AcctConst.REQ_ADD.equals(reqType);
		Acct target = null;
		if (!isAdd) {
			target = acctDao.findById(r.getAcctId());
			if (target == null) {
				throw AcctMsg.notFound();
			}
		}
		AcctReq c = buildContent(f, reqType, target);
		AttachmentService.PendingFile doc = attachmentService.check(docFile);
		if (!isAdd) {
			checkPrereq(reqType, target);
			if (acctReqDao.countInFlight(r.getAcctId(), r.getReqId()) > 0) {
				throw ApiException.conflict(AcctMsg.E07);
			}
		}
		c.setReqId(r.getReqId());
		c.setAcctId(r.getAcctId());
		c.setReqTypeCode(reqType);
		c.setApplyBy(r.getApplyBy());
		LocalDateTime now = LocalDateTime.now(clock);
		List<Path> written = new ArrayList<>();
		try {
			// 交易：帶條件 UPDATE 申請單 → 新增類型 UPDATE 主表 → 舊公文 STATUS＝0、INSERT 新公文 → INSERT 歷史 RESUB（spec §5）
			tx.executeWithoutResult(s -> {
				if (acctReqDao.resubmit(c, v, id.getUserId(), now, id.getRealUserId()) != 1) {
					throw AcctMsg.concurrent();
				}
				if (isAdd) {
					must(acctDao.resubmitAdd(c, now, id.getRealUserId()), reqId, "帳號主表改回審核中");
				}
				if (doc != null) {
					attachDao.deactivate(reqId, AcctConst.ATTACH_DOC, now, id.getRealUserId());
					attachmentService.store(reqId, AcctConst.ATTACH_DOC, doc, written, now, id.getRealUserId());
				}
				acctHistDao.insert(hist(id, c, AcctConst.ACT_RESUB, AcctConst.STAGE_SUBMIT,
						"重新送出；原因：" + c.getApplyReason() + attachText(doc), 0, now));
			});
		} catch (DuplicateKeyException e) {
			attachmentService.cleanup(written);
			if (isInFlightDup(e)) {
				throw ApiException.conflict(AcctMsg.E07);
			}
			log.error("重新送出申請單發生非在途索引的唯一鍵衝突 reqId={}", reqId);
			throw e;
		} catch (RuntimeException e) {
			attachmentService.cleanup(written);
			throw e;
		}
		log.info("申請單重新送出 reqId={}", reqId);
		notificationService.requestEvent(AcctConst.EV_RESUB, reqId, id.getRealUserId());
		return result(reqId, AcctConst.RS_REVIEW, AcctConst.STAGE_FIRST_APPR, v + 1);
	}

	// ------------------------------------------------------------
	// 關卡 2～5：核准／退件
	// ------------------------------------------------------------

	public ReqActionResult approve(Identity id, String reqId, VersionForm form) {
		AcctReqDetail r = load(reqId);
		Map<Integer, ApprStage> stages = codeService.stageMap();
		int cur = checkApprover(id, r, stages, form == null ? null : form.getVersionNo());
		int v = r.getVersionNo();
		boolean last = cur == AcctConst.STAGE_LAST_APPR;
		String newState = last ? AcctConst.RS_EXEC : AcctConst.RS_REVIEW;
		int newStage = last ? AcctConst.STAGE_EXEC : cur + 1;
		LocalDateTime now = LocalDateTime.now(clock);
		// 交易：帶條件 UPDATE 申請單 → INSERT 歷史 APPROVE（spec §5）
		tx.executeWithoutResult(s -> {
			if (acctReqDao.approve(reqId, v, cur, newState, newStage, now, id.getRealUserId()) != 1) {
				throw AcctMsg.concurrent();
			}
			int self = selfAction(AcctConst.ACT_APPROVE, id.getUserId(), r.getApplyBy(), cur,
					acctHistDao.findSelfCheckActs(reqId));
			acctHistDao.insert(hist(id, r, AcctConst.ACT_APPROVE, cur, "「" + CodeService.stageName(stages, cur) + "」核准",
					self, now));
		});
		log.info("申請單核准 reqId={} stage={}", reqId, cur);
		notificationService.requestEvent(last ? AcctConst.EV_TO_EXEC : AcctConst.EV_NEXT_STAGE, reqId, id.getRealUserId());
		return result(reqId, newState, newStage, v + 1);
	}

	public ReqActionResult reject(Identity id, String reqId, ReasonForm form) {
		AcctReqDetail r = load(reqId);
		Map<Integer, ApprStage> stages = codeService.stageMap();
		int cur = checkApprover(id, r, stages, form == null ? null : form.getVersionNo());
		String reason = requireReason(form == null ? null : form.getReason());
		int v = r.getVersionNo();
		boolean isAdd = AcctConst.REQ_ADD.equals(r.getReqTypeCode());
		LocalDateTime now = LocalDateTime.now(clock);
		// 交易：帶條件 UPDATE 申請單 → 新增類型主表改 REJECTED → INSERT 歷史 REJECT（spec §5）
		tx.executeWithoutResult(s -> {
			if (acctReqDao.reject(reqId, v, cur, reason, id.getUserId(), now, id.getRealUserId()) != 1) {
				throw AcctMsg.concurrent();
			}
			if (isAdd) {
				must(acctDao.markAddRejected(r.getAcctId(), now, id.getRealUserId()), reqId, "帳號主表改已退件");
			}
			int self = selfAction(AcctConst.ACT_REJECT, id.getUserId(), r.getApplyBy(), cur,
					acctHistDao.findSelfCheckActs(reqId));
			acctHistDao.insert(hist(id, r, AcctConst.ACT_REJECT, cur,
					"「" + CodeService.stageName(stages, cur) + "」退件；原因：" + reason, self, now));
		});
		log.info("申請單退件 reqId={} stage={}", reqId, cur);
		notificationService.requestEvent(AcctConst.EV_REJECT, reqId, id.getRealUserId());
		return result(reqId, AcctConst.RS_REJECTED, null, v + 1);
	}

	/**
	 * 核准／退件的驗權與狀態檢查，回目前關卡（spec §4.4：權限先驗再做帶條件 UPDATE）。
	 * 沒有任何關卡 2～5 權限 → 403；未帶 versionNo → 400 E02；不在 REVIEW 或版本不符 → 409 E08；
	 * 沒有目前關卡的權限 → 403
	 */
	int checkApprover(Identity id, AcctReq r, Map<Integer, ApprStage> stages, Integer versionNo) {
		if (!perm.hasAnyApprPerm(id, stages)) {
			throw AcctMsg.forbidden();
		}
		Integer v = requireVersion(versionNo);
		Integer cur = r.getCurrStageId();
		if (!AcctConst.RS_REVIEW.equals(r.getReqStateCode()) || cur == null || cur < AcctConst.STAGE_FIRST_APPR
				|| cur > AcctConst.STAGE_LAST_APPR || !v.equals(r.getVersionNo())) {
			throw AcctMsg.concurrent();
		}
		if (!perm.can(id, CodeService.stagePermId(stages, cur))) {
			throw AcctMsg.forbidden();
		}
		return cur;
	}

	// ------------------------------------------------------------
	// 關卡 6：回報執行完畢（帳號生效）
	// ------------------------------------------------------------

	public ReqActionResult execDone(Identity id, String reqId, ExecDoneForm form, MultipartFile proofFile) {
		AcctReqDetail r = load(reqId);
		if (!perm.isExecutor(id, r.getCatgId())) {
			throw AcctMsg.forbidden();
		}
		ExecDoneForm f = form == null ? new ExecDoneForm() : form;
		Integer v = requireVersion(f.getVersionNo());
		if (!AcctConst.RS_EXEC.equals(r.getReqStateCode()) || !Integer.valueOf(AcctConst.STAGE_EXEC).equals(r.getCurrStageId())
				|| !v.equals(r.getVersionNo())) {
			throw AcctMsg.concurrent();
		}
		AttachmentService.PendingFile proof = attachmentService.check(proofFile);
		if (proof == null) {
			throw ApiException.badRequest(AcctMsg.E12);
		}
		boolean doorAdd = AcctConst.CATG_DOOR.equals(r.getCatgId()) && AcctConst.REQ_ADD.equals(r.getReqTypeCode());
		String cardNo = doorAdd ? Texts.trimToNull(f.getCardNo()) : null;
		if (doorAdd && cardNo == null) {
			throw ApiException.badRequest(AcctMsg.E13);
		}
		Texts.maxBytes("門禁卡號", cardNo, 50);
		String execDescr = Texts.maxChars("執行說明", Texts.trimToNull(f.getExecDescr()), 500);
		boolean first = r.getEffectDate() == null;
		Acct acct = acctDao.findById(r.getAcctId());
		if (acct == null) {
			log.error("帳號主表查無 reqId={} acctId={}", reqId, r.getAcctId());
			throw ApiException.internal("帳號資料不存在，請聯絡系統管理員");
		}
		LocalDateTime now = LocalDateTime.now(clock);
		List<Path> written = new ArrayList<>();
		try {
			// 交易：帶條件 UPDATE 申請單（含 EFFECT_DATE）→ 舊佐證 STATUS＝0、INSERT 新佐證 → 更新主表（§4.6）
			//       → INSERT 歷史 EXEC_DONE → 寫審查日期（§4.7）（spec §5）
			tx.executeWithoutResult(s -> {
				if (acctReqDao.execDone(reqId, v, execDescr, doorAdd, cardNo, id.getUserId(), now, id.getRealUserId()) != 1) {
					throw AcctMsg.concurrent();
				}
				attachDao.deactivate(reqId, AcctConst.ATTACH_PROOF, now, id.getRealUserId());
				attachmentService.store(reqId, AcctConst.ATTACH_PROOF, proof, written, now, id.getRealUserId());
				if (first) {
					applyToAccount(r, acct, cardNo, now, id.getRealUserId());
				} else if (doorAdd && !Objects.equals(cardNo, r.getCardNo())) {
					must(acctDao.updateAcctName(r.getAcctId(), cardNo, now, id.getRealUserId()), reqId, "主表同步門禁卡號");
				}
				int self = selfAction(AcctConst.ACT_EXEC_DONE, id.getUserId(), r.getApplyBy(), AcctConst.STAGE_EXEC,
						acctHistDao.findSelfCheckActs(reqId));
				acctHistDao.insert(hist(id, r, AcctConst.ACT_EXEC_DONE, AcctConst.STAGE_EXEC,
						execText(cardNo, execDescr, proof), self, now));
				if (first) {
					writeReviewDates(r, acct, now, id.getRealUserId());
				}
			});
		} catch (RuntimeException e) {
			attachmentService.cleanup(written);
			throw e;
		}
		log.info("申請單回報執行完畢 reqId={} first={}", reqId, first);
		notificationService.requestEvent(AcctConst.EV_EXEC_DONE, reqId, id.getRealUserId());
		return result(reqId, AcctConst.RS_VERIFY, AcctConst.STAGE_VERIFY, v + 1);
	}

	/** 第一次回報時依類型更新主表（spec §4.6），UPDATE_BY＝實際操作人 */
	private void applyToAccount(AcctReq r, Acct acct, String cardNo, LocalDateTime now, String userId) {
		String reqId = r.getReqId();
		switch (r.getReqTypeCode()) {
		case AcctConst.REQ_ADD -> {
			String name = AcctConst.CATG_DOOR.equals(r.getCatgId()) ? cardNo : r.getAcctName();
			must(acctDao.applyAdd(r, name, now, userId), reqId, "主表寫入新增內容");
		}
		case AcctConst.REQ_MODIFY -> must(acctDao.applyModify(r, revive(r, LocalDate.from(now)), now, userId), reqId,
				"主表覆寫修改內容");
		case AcctConst.REQ_ENABLE -> must(acctDao.applyEnable(r, now, userId), reqId, "主表重新啟用");
		case AcctConst.REQ_DISABLE -> must(acctDao.applyState(acct.getAcctId(), AcctConst.AS_DISABLED, r.getApplyReason(), now,
				userId), reqId, "主表停用");
		case AcctConst.REQ_DELETE -> must(acctDao.applyState(acct.getAcctId(), AcctConst.AS_DELETED, r.getApplyReason(), now,
				userId), reqId, "主表刪除");
		default -> throw new IllegalStateException("未知的申請類型");
		}
	}

	/** 修改：在途期間已被 J01 轉為 EXPIRED 時，新期限為永久或到期日 ≥ 今天即改回 APPROVED */
	static boolean revive(AcctReq r, LocalDate today) {
		if (AcctConst.EXPIRY_PERM.equals(r.getExpiryTypeCode())) {
			return true;
		}
		return r.getExpiryDate() != null && !r.getExpiryDate().toLocalDate().isBefore(today);
	}

	/** 審查日期（spec §4.7）：只在第一次回報判斷 */
	private void writeReviewDates(AcctReq r, Acct acct, LocalDateTime now, String userId) {
		boolean newMatch = ReviewRule.matches(codeService.findAssetClass(r.getAssetClassId()), r.getCatgId(), r.getIsPriv());
		boolean oldMatch = ReviewRule.matches(codeService.findAssetClass(acct.getAssetClassId()), acct.getCatgId(),
				acct.getIsPriv());
		LocalDate oldLast = acct.getLastReviewDate() == null ? null : acct.getLastReviewDate().toLocalDate();
		if (ReviewRule.shouldWrite(r.getReqTypeCode(), newMatch, oldMatch, oldLast)) {
			LocalDate last = LocalDate.from(now);
			must(acctDao.updateReviewDates(r.getAcctId(), last, ReviewRule.nextReview(last), now, userId), r.getReqId(),
					"寫入審查日期");
		}
	}

	// ------------------------------------------------------------
	// 關卡 7：複驗
	// ------------------------------------------------------------

	public ReqActionResult verifyOk(Identity id, String reqId, VersionForm form) {
		AcctReqDetail r = load(reqId);
		int v = checkVerifier(id, r, form == null ? null : form.getVersionNo());
		LocalDateTime now = LocalDateTime.now(clock);
		// 交易：帶條件 UPDATE 申請單（APPROVED、DONE_DATE）→ INSERT 歷史 VERIFY_OK；主表不動（spec §5）
		tx.executeWithoutResult(s -> {
			if (acctReqDao.verifyOk(reqId, v, id.getUserId(), now, id.getRealUserId()) != 1) {
				throw AcctMsg.concurrent();
			}
			int self = selfAction(AcctConst.ACT_VERIFY_OK, id.getUserId(), r.getApplyBy(), AcctConst.STAGE_VERIFY,
					acctHistDao.findSelfCheckActs(reqId));
			acctHistDao.insert(hist(id, r, AcctConst.ACT_VERIFY_OK, AcctConst.STAGE_VERIFY, "複驗通過，結案", self, now));
		});
		log.info("申請單複驗通過 reqId={}", reqId);
		return result(reqId, AcctConst.RS_APPROVED, null, v + 1);
	}

	public ReqActionResult verifyReject(Identity id, String reqId, ReasonForm form) {
		AcctReqDetail r = load(reqId);
		int v = checkVerifier(id, r, form == null ? null : form.getVersionNo());
		String reason = requireReason(form == null ? null : form.getReason());
		LocalDateTime now = LocalDateTime.now(clock);
		// 交易：帶條件 UPDATE 申請單（EXEC、VERIFY_REJECT_CNT＋1）→ INSERT 歷史 VERIFY_REJECT；主表不動（spec §5）
		tx.executeWithoutResult(s -> {
			if (acctReqDao.verifyReject(reqId, v, reason, now, id.getRealUserId()) != 1) {
				throw AcctMsg.concurrent();
			}
			int self = selfAction(AcctConst.ACT_VERIFY_REJECT, id.getUserId(), r.getApplyBy(), AcctConst.STAGE_VERIFY,
					acctHistDao.findSelfCheckActs(reqId));
			acctHistDao.insert(hist(id, r, AcctConst.ACT_VERIFY_REJECT, AcctConst.STAGE_VERIFY,
					"複驗退回，退回執行人重新處理；原因：" + reason, self, now));
		});
		log.info("申請單複驗退回 reqId={}", reqId);
		notificationService.requestEvent(AcctConst.EV_VERIFY_REJECT, reqId, id.getRealUserId());
		return result(reqId, AcctConst.RS_EXEC, AcctConst.STAGE_EXEC, v + 1);
	}

	/** 複驗：無 VERIFY 關卡權限 403 → 未帶版本 400 → 狀態／版本不符 409；回目前版本 */
	private int checkVerifier(Identity id, AcctReq r, Integer versionNo) {
		if (!perm.can(id, CodeService.stagePermId(codeService.stageMap(), AcctConst.STAGE_VERIFY))) {
			throw AcctMsg.forbidden();
		}
		Integer v = requireVersion(versionNo);
		if (!AcctConst.RS_VERIFY.equals(r.getReqStateCode()) || !Integer.valueOf(AcctConst.STAGE_VERIFY).equals(r.getCurrStageId())
				|| !v.equals(r.getVersionNo())) {
			throw AcctMsg.concurrent();
		}
		return v;
	}

	// ------------------------------------------------------------
	// 送出內容驗證與整理（spec §4.3、§4.9）
	// ------------------------------------------------------------

	/**
	 * 依類型驗證並整理申請內容（不含 reqId／acctId／reqTypeCode／applyBy）。順序：E02 必填 → E03 → E04 → E05 → E06 → 代碼值 → E11。
	 * 異動類型的帳號類別、名稱、所屬系統取自主表；停用、刪除只取申請單位與原因，其餘複製主表。
	 */
	AcctReq buildContent(AcctReqForm f, String reqType, Acct target) {
		boolean isAdd = AcctConst.REQ_ADD.equals(reqType);
		boolean reasonOnly = AcctConst.REASON_ONLY_TYPES.contains(reqType);
		String catgId = isAdd ? Texts.trimToNull(f.getCatgId()) : target.getCatgId();
		boolean door = AcctConst.CATG_DOOR.equals(catgId);

		List<String> missing = new ArrayList<>();
		addIfBlank(missing, "申請單位", f.getApplyDept());
		if (reasonOnly) {
			addIfBlank(missing, AcctConst.REQ_DISABLE.equals(reqType) ? "停用原因" : "刪除原因", f.getApplyReason());
		} else {
			if (isAdd) {
				addIfBlank(missing, "帳號類別", catgId);
				if (!door) {
					addIfBlank(missing, "帳號名稱", f.getAcctName());
					addIfBlank(missing, "所屬系統", f.getSysName());
				}
			}
			if (door) {
				addIfBlank(missing, "門禁權限範圍", f.getDoorScopeCode());
			}
			addIfBlank(missing, "使用人", f.getHolderName());
			addIfBlank(missing, "申請原因", f.getApplyReason());
			addIfBlank(missing, "有效期限類型", f.getExpiryTypeCode());
		}
		if (!missing.isEmpty()) {
			throw AcctMsg.required(missing);
		}

		AcctReq c = new AcctReq();
		c.setIsUniqueConf(1);
		if (reasonOnly) {
			c.setApplyDept(Texts.maxChars("申請單位", f.getApplyDept().trim(), 50));
			copyContent(target, c);
			c.setApplyReason(Texts.maxChars(AcctConst.REQ_DISABLE.equals(reqType) ? "停用原因" : "刪除原因",
					f.getApplyReason().trim(), 500));
			return c;
		}

		String expiryType = f.getExpiryTypeCode().trim();
		String expiryText = Texts.trimToNull(f.getExpiryDate());
		boolean dateType = AcctConst.EXPIRY_DATE.equals(expiryType);
		if (dateType && expiryText == null) {
			throw ApiException.badRequest(AcctMsg.E03);
		}
		if (!AcctConst.EXPIRY_TYPES.contains(expiryType)) {
			throw AcctMsg.badCode("有效期限類型");
		}
		LocalDate expiryDate = dateType ? parseDate(expiryText) : null;
		if (expiryDate != null && expiryDate.isBefore(LocalDate.now(clock))) {
			throw ApiException.badRequest(AcctMsg.E04);
		}
		if (isAdd) {
			Category cat = codeService.findCategory(catgId);
			if (cat == null || !Boolean.TRUE.equals(cat.getEnabled())) {
				throw ApiException.badRequest(AcctMsg.E05);
			}
		}
		String assetClassId = door ? null : Texts.trimToNull(f.getAssetClassId());
		if (assetClassId != null) {
			AssetClass ac = codeService.findAssetClass(assetClassId);
			if (ac == null || !Boolean.TRUE.equals(ac.getSelectable())) {
				throw ApiException.badRequest(AcctMsg.E06);
			}
		}
		Integer isPriv = f.getIsPriv() == null ? Integer.valueOf(0) : f.getIsPriv();
		if (isPriv != 0 && isPriv != 1) {
			throw AcctMsg.badCode("特權帳號");
		}
		String doorScope = door ? f.getDoorScopeCode().trim() : null;
		if (door && !AcctConst.DOOR_SCOPES.contains(doorScope)) {
			throw AcctMsg.badCode("門禁權限範圍");
		}
		String accessLevel = door ? AcctConst.DOOR_ACCESS_LEVEL : Texts.trimToNull(f.getAccessLevelCode());
		if (!door && accessLevel != null && !AcctConst.ACCESS_LEVELS.contains(accessLevel)) {
			throw AcctMsg.badCode("權限範圍");
		}

		// 欄寬（E11）一律在 E03～E06 之後檢查（spec §4.3，變更單 #1 L2）
		c.setApplyDept(Texts.maxChars("申請單位", f.getApplyDept().trim(), 50));
		c.setCatgId(catgId);
		if (isAdd) {
			// 門禁卡：帳號名稱送出時為空（關卡 6 填卡號）、所屬系統固定（spec §4.9）
			c.setAcctName(door ? null : Texts.maxChars("帳號名稱", f.getAcctName().trim(), 100));
			c.setSysName(door ? AcctConst.DOOR_SYS_NAME : Texts.maxChars("所屬系統", f.getSysName().trim(), 100));
		} else {
			c.setAcctName(target.getAcctName());
			c.setSysName(target.getSysName());
		}
		c.setDoorScopeCode(doorScope);
		c.setAssetClassId(assetClassId);
		c.setIsPriv(isPriv);
		c.setHolderName(Texts.maxChars("使用人", f.getHolderName().trim(), 50));
		c.setHolderDept(Texts.maxChars("使用人所屬單位", Texts.trimToNull(f.getHolderDept()), 50));
		c.setHolderCntct(door ? null : Texts.maxChars("使用人聯絡方式", Texts.trimToNull(f.getHolderCntct()), 100));
		c.setHolderEmail(door ? Texts.maxBytes("使用人 Email", Texts.trimToNull(f.getHolderEmail()), 100) : null);
		c.setAccessLevelCode(accessLevel);
		c.setAccessTarget(door ? null : Texts.maxChars("存取對象", Texts.trimToNull(f.getAccessTarget()), 200));
		c.setApplyReason(Texts.maxChars("申請原因", f.getApplyReason().trim(), 500));
		c.setExpiryTypeCode(expiryType);
		c.setExpiryDate(expiryDate == null ? null : expiryDate.atStartOfDay());
		return c;
	}

	/** 停用、刪除：內容欄位全部複製主表當下內容（spec §4.3） */
	private static void copyContent(Acct a, AcctReq c) {
		c.setCatgId(a.getCatgId());
		c.setAcctName(a.getAcctName());
		c.setSysName(a.getSysName());
		c.setDoorScopeCode(a.getDoorScopeCode());
		c.setAssetClassId(a.getAssetClassId());
		c.setIsPriv(a.getIsPriv() == null ? Integer.valueOf(0) : a.getIsPriv());
		c.setHolderName(a.getHolderName());
		c.setHolderDept(a.getHolderDept());
		c.setHolderCntct(a.getHolderCntct());
		c.setHolderEmail(a.getHolderEmail());
		c.setAccessLevelCode(a.getAccessLevelCode());
		c.setAccessTarget(a.getAccessTarget());
		c.setExpiryTypeCode(a.getExpiryTypeCode() == null ? AcctConst.EXPIRY_PERM : a.getExpiryTypeCode());
		c.setExpiryDate(a.getExpiryDate());
	}

	/** 申請前提（Table 6.5）不符 → 409 E09 */
	static void checkPrereq(String reqType, Acct target) {
		if (!AccountService.isAllowed(reqType, target.getAcctStateCode())) {
			throw ApiException.conflict(AcctMsg.E09);
		}
	}

	// ------------------------------------------------------------
	// 歷史與 IS_SELF_ACTION（spec §4.8）
	// ------------------------------------------------------------

	/**
	 * IS_SELF_ACTION：只判斷 APPROVE、REJECT、EXEC_DONE、VERIFY_OK、VERIFY_REJECT；以有效身分比對，符合任一為 1：
	 * ① 等於申請人；② 同一申請單已有其他關卡（STAGE_ID 不同）的上述動作且有效身分相同
	 */
	static int selfAction(String action, String effectiveId, String applyBy, int stageId, List<AcctHist> prior) {
		if (!AcctConst.SELF_CHECK_ACTIONS.contains(action) || effectiveId == null) {
			return 0;
		}
		if (effectiveId.equals(applyBy)) {
			return 1;
		}
		for (AcctHist h : prior) {
			if (AcctConst.SELF_CHECK_ACTIONS.contains(h.getActionCode()) && h.getStageId() != null && h.getStageId() != stageId
					&& effectiveId.equals(h.getActAsId() != null ? h.getActAsId() : h.getActBy())) {
				return 1;
			}
		}
		return 0;
	}

	/** 顯示標籤：條件 ① 成立「自行核准」，否則「同人兼任」；非 1 為 null */
	static String selfLabel(AcctHist h) {
		if (!Integer.valueOf(1).equals(h.getIsSelfAction())) {
			return null;
		}
		String eff = h.getActAsId() != null ? h.getActAsId() : h.getActBy();
		return eff != null && eff.equals(h.getReqApplyBy()) ? AcctConst.SELF_LABEL_APPLICANT : AcctConst.SELF_LABEL_SAME_PERSON;
	}

	public static List<AcctHist> withLabels(List<AcctHist> rows) {
		for (AcctHist h : rows) {
			h.setSelfActionLabel(selfLabel(h));
		}
		return rows;
	}

	private static AcctHist hist(Identity id, AcctReq r, String action, Integer stageId, String text, int self,
			LocalDateTime now) {
		AcctHist h = new AcctHist();
		h.setAcctId(r.getAcctId());
		h.setReqId(r.getReqId());
		h.setSourceCode(AcctConst.HIST_SRC_REQ);
		h.setActionCode(action);
		h.setStageId(stageId);
		h.setActBy(id.getRealUserId());
		h.setActByName(id.getRealUserName());
		h.setActAsId(id.actAsId());
		h.setActAsName(id.actAsName());
		h.setActDate(now);
		h.setApplyDept(r.getApplyDept());
		h.setResultDescr(Texts.truncate(text, RESULT_DESCR_MAX));
		h.setIsSelfAction(self);
		h.setCreateBy(id.getRealUserId());
		return h;
	}

	private static String execText(String cardNo, String execDescr, AttachmentService.PendingFile proof) {
		StringBuilder b = new StringBuilder("回報執行完畢");
		if (cardNo != null) {
			b.append("；門禁卡號：").append(cardNo);
		}
		if (execDescr != null) {
			b.append("；執行說明：").append(execDescr);
		}
		b.append("；佐證：").append(proof.fileName());
		return b.toString();
	}

	private static String attachText(AttachmentService.PendingFile doc) {
		return doc == null ? "" : "；公文：" + doc.fileName();
	}

	// ------------------------------------------------------------
	// 共用
	// ------------------------------------------------------------

	private AcctReqDetail load(String reqId) {
		AcctReqDetail r = reqId == null ? null : acctReqDao.findById(reqId);
		if (r == null) {
			throw AcctMsg.notFound();
		}
		return r;
	}

	private static Integer requireVersion(Integer versionNo) {
		if (versionNo == null) {
			throw AcctMsg.required(LABEL_VERSION);
		}
		return versionNo;
	}

	private static String requireReason(String reason) {
		String r = Texts.trimToNull(reason);
		if (r == null) {
			throw AcctMsg.fillIn(AcctMsg.WHAT_REASON);
		}
		return Texts.maxChars(AcctMsg.WHAT_REASON, r, 500);
	}

	/** 交易內的寫入必須剛好 1 筆，否則丟例外讓整筆交易回復 */
	private static void must(int rows, String reqId, String step) {
		if (rows != 1) {
			log.error("{}影響筆數 {}（預期 1），交易回復 reqId={}", step, rows, reqId);
			throw ApiException.internal("資料寫入未完成（單號 " + reqId + "），請聯絡系統管理員");
		}
	}

	/**
	 * 唯一鍵衝突是否來自同帳號在途索引：沿例外鏈找 ORA-00001 訊息中的「(擁有者.索引名)」。
	 * 比對前後帶「.」與「)」，避免前綴相同的索引名（如 _040）或 SQL 原文中的 hint 誤中
	 */
	static boolean isInFlightDup(DuplicateKeyException e) {
		String token = "." + IDX_IN_FLIGHT + ")";
		for (Throwable t = e; t != null; t = t.getCause()) {
			String msg = t.getMessage();
			if (msg != null && msg.contains(token)) {
				return true;
			}
		}
		return false;
	}

	private static ReqActionResult result(String reqId, String state, Integer stage, int versionNo) {
		ReqActionResult res = new ReqActionResult();
		res.setReqId(reqId);
		res.setReqStateCode(state);
		res.setCurrStageId(stage);
		res.setVersionNo(versionNo);
		return res;
	}

	private static void addIfBlank(List<String> missing, String label, String value) {
		if (value == null || value.isBlank()) {
			missing.add(label);
		}
	}

	private static LocalDate parseDate(String s) {
		try {
			return LocalDate.parse(s, DATE_FMT);
		} catch (DateTimeParseException ex) {
			throw ApiException.badRequest(AcctMsg.BAD_DATE);
		}
	}
}
