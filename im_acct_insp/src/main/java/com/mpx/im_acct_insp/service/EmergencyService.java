package com.mpx.im_acct_insp.service;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: 新增：緊急存取送出與查詢（BACKLOG 第 46 項 S2；SA §5.11）
//           送出需 EMG_APPLY：必填（E10）→ 欄寬（帳號或系統 200、緊急事由 500；E11）→ 預估時長整數 1～24 → 類別須存在（STATUS＝1，
//           不看 IS_ENABLED；否則 400「欄位內容不正確：帳號類別」）；同一交易取號 EA → INSERT，SUBMIT_DATE＝現在（到秒）、
//           DUE_DATE＝＋24 小時、APPLY_BY＝有效身分、CREATE_BY＝實際操作人；交易提交後寄 EMG_SUBMIT 給所有 EMG_DECIDE 持有人
//           列表與明細登入即可：有 EMG_VIEW_ALL 看全部，否則只看 APPLY_BY＝有效身分的單；明細查無或無權看一律 404（裁示①A）；
//           狀態篩選值不在五種計算狀態回 400；日期篩選用 Texts.parseDate
// ============================================================

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.mpx.im_acct_insp.common.AcctConst;
import com.mpx.im_acct_insp.common.AcctMsg;
import com.mpx.im_acct_insp.common.DocSeqService;
import com.mpx.im_acct_insp.common.Identity;
import com.mpx.im_acct_insp.common.Texts;
import com.mpx.im_acct_insp.dao.EmgDao;
import com.mpx.im_acct_insp.model.Emg;
import com.mpx.im_acct_insp.model.EmgDetail;
import com.mpx.im_acct_insp.model.EmgIdResult;
import com.mpx.im_acct_insp.model.request.EmgForm;

@Service
public class EmergencyService {

	private static final Logger log = LoggerFactory.getLogger(EmergencyService.class);

	static final int TARGET_MAX = 200;
	static final int REASON_MAX = 500;
	static final String LABEL_STATE = "狀態";

	private final PermissionService perm;
	private final CodeService codeService;
	private final DocSeqService docSeqService;
	private final EmgDao emgDao;
	private final NotificationService notificationService;
	private final TransactionTemplate tx;
	private final Clock clock;

	public EmergencyService(PermissionService perm, CodeService codeService, DocSeqService docSeqService, EmgDao emgDao,
			NotificationService notificationService, TransactionTemplate tx, Clock clock) {
		this.perm = perm;
		this.codeService = codeService;
		this.docSeqService = docSeqService;
		this.emgDao = emgDao;
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
		if (state != null && !AcctConst.EMG_STATES.contains(state)) {
			throw AcctMsg.badCode(LABEL_STATE);
		}
		String applyBy = perm.can(id, AcctConst.PERM_EMG_VIEW_ALL) ? null : id.getUserId();
		return emgDao.findList(applyBy, Texts.escapeLike(Texts.trimToNull(keyword)), state, Texts.parseDate(dateFrom),
				Texts.parseDate(dateTo), LocalDateTime.now(clock));
	}

	/** 明細（登入即可）：查無或無權看一律 404 */
	public EmgDetail detail(Identity id, String emgId) {
		EmgDetail d = emgId == null ? null : emgDao.findById(emgId, LocalDateTime.now(clock));
		if (d == null || !canView(id, d)) {
			throw AcctMsg.notFound();
		}
		d.setAffectAccts(emgDao.findAffects(emgId));
		return d;
	}

	/** 有 EMG_VIEW_ALL、或申請人就是有效身分 */
	boolean canView(Identity id, Emg e) {
		return perm.can(id, AcctConst.PERM_EMG_VIEW_ALL) || id.getUserId().equals(e.getApplyBy());
	}
}
