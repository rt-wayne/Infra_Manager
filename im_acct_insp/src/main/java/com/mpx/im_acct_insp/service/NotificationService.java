package com.mpx.im_acct_insp.service;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：Email 通知（spec v2.0 §4.12；到期通知 §4.11）
//           收件人在寄送當下計算、只含 STATUS＝1、同一封信同一人去重：關卡權限（取自 IM_ACCT_APPR_STAGE.PERM_ID）、
//           該類別執行人（IM_EXEC_MAP）、申請人／該次執行人、角色成員（變更單 #2 起角色為程式常數）。每位收件人寫一筆 IM_ACCT_NOTIFY_LOG：無 Email → SKIP、寄送例外 → FAIL；
//           業務動作的通知由呼叫端在交易提交後呼叫，本類別吞下所有例外（寄送或寫紀錄失敗不影響業務結果與 API 回應）。
//           log 只記事件、參照與收件人數，不記 Email 與本文
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 變更單 #1（L3）：sendAll 逐筆記收件人工號與結果（不記 Email、不記本文）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 變更單 #2：移除 properties key im.acct.insp.mail.expiry.roles、im.acct.insp.mail.job-fail.roles 與 parseRoles；
//           收件角色依 SA 7.3 改為程式常數（到期／逾期／每週彙總 → AcctConst.MAIL_ROLE_EXPIRY＝GOV，
//           排程失敗 → AcctConst.MAIL_ROLE_JOB_FAIL＝SYS_ADMIN），成員於寄信當下以 PermDao.findUsersInRoles 查
//           IM_ACCT_ROLE_USER（STATUS＝1）＋IM_ACCT_USER（STATUS＝1）；無 Email 者照 §4.12 記 SKIP
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-11
// 變更說明: BACKLOG 第 25 項：sendAll 加寄送名單（MailAllowList）——有 Email 但不在名單者不寄、記 SKIP，
//           ERR_MSG「不在寄送名單」；無 Email 仍先判斷、照舊記 SKIP 且 ERR_MSG 留空。兩種 SKIP 都不存 Email
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-11
// 變更說明: BACKLOG 第 27 項：本文由純文字改為 HTML（MailHtml 組版面、跳脫所有填入值），欄位內容不變。
//           信末「前往系統」連結依事件：待審核／待執行／待複驗／複驗退回 → 待辦簽核，退件 → 申請單列表，
//           到期／逾期／每週彙總／排程失敗 → 首頁（部分收件人沒有帳號列表權限）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-11
// 變更說明: BACKLOG 第 7 項：jobFail 的 runId 可為空（連執行序號都沒取到）→ 參照鍵寫 0、信中執行序號「（未取得）」；
//           結果碼為 SUCCESS（排程做完、只有結束紀錄寫失敗）→ 主旨「【排程失敗】{代碼} 結束紀錄寫入失敗」
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S2：建構子加 EmgDao；新增緊急存取事件 emgEvent（交易提交後呼叫）——
//           EMG_SUBMIT 寄給所有 EMG_DECIDE 持有人，主旨含補核准期限，本文列單號、類別、帳號或系統、申請人、事由、
//           預估時長、送出時間、期限，連結「緊急存取」；參照 EMG／單號。EMG_RETURN 待 S4、EMG_OVERDUE 待 S7 補
// ============================================================

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.mpx.im_acct_insp.common.AcctConst;
import com.mpx.im_acct_insp.common.Texts;
import com.mpx.im_acct_insp.dao.AcctReqDao;
import com.mpx.im_acct_insp.dao.EmgDao;
import com.mpx.im_acct_insp.dao.NotifyLogDao;
import com.mpx.im_acct_insp.dao.PermDao;
import com.mpx.im_acct_insp.model.Acct;
import com.mpx.im_acct_insp.model.AcctReq;
import com.mpx.im_acct_insp.model.ApprStage;
import com.mpx.im_acct_insp.model.Emg;
import com.mpx.im_acct_insp.model.Recipient;

@Service
public class NotificationService {

	private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
	static final DateTimeFormatter YMD = DateTimeFormatter.BASIC_ISO_DATE;
	static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	static final int ERR_MSG_MAX = 500;
	/** J02 0 筆時的本文（spec §4.15 U13） */
	static final String U13 = "目前沒有已逾期未處理的帳號";
	/** 不在寄送名單的 SKIP 寫入 ERR_MSG 的文字 */
	static final String NOT_IN_ALLOW_LIST = "不在寄送名單";

	private final PermDao permDao;
	private final AcctReqDao acctReqDao;
	private final EmgDao emgDao;
	private final CodeService codeService;
	private final NotifyLogDao notifyLogDao;
	private final MailSender mailSender;
	private final MailAllowList allowList;
	private final MailHtml html;
	private final Clock clock;

	public NotificationService(PermDao permDao, AcctReqDao acctReqDao, EmgDao emgDao, CodeService codeService,
			NotifyLogDao notifyLogDao, MailSender mailSender, MailAllowList allowList, MailHtml html, Clock clock) {
		this.permDao = permDao;
		this.acctReqDao = acctReqDao;
		this.emgDao = emgDao;
		this.codeService = codeService;
		this.notifyLogDao = notifyLogDao;
		this.mailSender = mailSender;
		this.allowList = allowList;
		this.html = html;
		this.clock = clock;
	}

	/** 帳號類通知參照鍵 ACCT_ID_到期日yyyymmdd（與 AcctDao.ACCT_REF_EXPR 同口徑） */
	public static String acctRefId(String acctId, LocalDate expiryDate) {
		return acctId + "_" + (expiryDate == null ? "" : expiryDate.format(YMD));
	}

	// ------------------------------------------------------------
	// 申請單事件（交易提交後呼叫）
	// ------------------------------------------------------------

	/**
	 * @param event    SUBMIT／RESUB／NEXT_STAGE／TO_EXEC／REJECT／EXEC_DONE／VERIFY_REJECT
	 * @param reqId    申請單號（重新讀取提交後的內容）
	 * @param createBy 寫通知紀錄的 CREATE_BY（實際操作人）
	 */
	public void requestEvent(String event, String reqId, String createBy) {
		try {
			AcctReq r = acctReqDao.findById(reqId);
			if (r == null) {
				return;
			}
			Map<Integer, ApprStage> stages = codeService.stageMap();
			String id = r.getReqId();
			List<Recipient> rcpts;
			String subject;
			switch (event) {
			case AcctConst.EV_SUBMIT -> {
				rcpts = withPerm(CodeService.stagePermId(stages, AcctConst.STAGE_FIRST_APPR));
				subject = "【帳號申請】" + id + " 待您審核（" + CodeService.stageName(stages, AcctConst.STAGE_FIRST_APPR) + "）";
			}
			case AcctConst.EV_RESUB -> {
				rcpts = withPerm(CodeService.stagePermId(stages, AcctConst.STAGE_FIRST_APPR));
				subject = "【帳號申請】" + id + " 已重新送出，待您審核";
			}
			case AcctConst.EV_NEXT_STAGE -> {
				rcpts = withPerm(CodeService.stagePermId(stages, r.getCurrStageId()));
				subject = "【帳號申請】" + id + " 待您審核（" + CodeService.stageName(stages, r.getCurrStageId()) + "）";
			}
			case AcctConst.EV_TO_EXEC -> {
				rcpts = permDao.findExecUsers(r.getCatgId());
				subject = "【帳號申請】" + id + " 待您執行";
			}
			case AcctConst.EV_REJECT -> {
				rcpts = single(r.getApplyBy());
				subject = "【帳號申請】" + id + " 已退件";
			}
			case AcctConst.EV_EXEC_DONE -> {
				rcpts = withPerm(CodeService.stagePermId(stages, AcctConst.STAGE_VERIFY));
				subject = "【帳號申請】" + id + " 待您複驗";
			}
			case AcctConst.EV_VERIFY_REJECT -> {
				rcpts = single(r.getExecBy());
				subject = "【帳號申請】" + id + " 複驗退回，請重新處理";
			}
			default -> throw new IllegalArgumentException("未知的通知事件 " + event);
			}
			String path = AcctConst.EV_REJECT.equals(event) ? MailHtml.PATH_REQUESTS : MailHtml.PATH_TODO;
			sendAll(event, AcctConst.REF_REQ, id, rcpts, subject, html.page(subject, requestBody(event, r, stages), path),
					createBy);
		} catch (RuntimeException e) {
			log.error("通知失敗 event={} reqId={} 例外={}", event, reqId, e.getClass().getSimpleName());
		}
	}

	/** 申請單類本文的兩欄表（HTML 片段，值已跳脫） */
	static String requestBody(String event, AcctReq r, Map<Integer, ApprStage> stages) {
		List<String[]> rows = new ArrayList<>();
		rows.add(new String[] { "單號", r.getReqId() });
		rows.add(new String[] { "申請類型", AcctConst.reqTypeName(r.getReqTypeCode()) });
		rows.add(new String[] { "帳號類別", nvl(r.getCatgName(), r.getCatgId()) });
		rows.add(new String[] { "帳號名稱", acctNameText(r.getCatgId(), r.getAcctName()) });
		rows.add(new String[] { "申請人", nvl(r.getApplyByName(), r.getApplyBy()) });
		rows.add(new String[] { "目前關卡",
				r.getCurrStageId() == null ? "—" : CodeService.stageName(stages, r.getCurrStageId()) });
		if (AcctConst.EV_REJECT.equals(event)) {
			rows.add(new String[] { "退件原因", nvl(r.getRejectReason(), "") });
		}
		if (AcctConst.EV_VERIFY_REJECT.equals(event)) {
			rows.add(new String[] { "退回原因", nvl(r.getVerifyRejectReason(), "") });
		}
		return MailHtml.table(rows);
	}

	// ------------------------------------------------------------
	// 緊急存取事件（交易提交後呼叫；BACKLOG 第 46 項）
	// ------------------------------------------------------------

	/**
	 * @param event    EMG_SUBMIT（EMG_RETURN 待 S4、EMG_OVERDUE 待 S7）
	 * @param emgId    緊急存取單號（重新讀取提交後的內容）
	 * @param createBy 寫通知紀錄的 CREATE_BY（實際操作人）
	 */
	public void emgEvent(String event, String emgId, String createBy) {
		try {
			Emg e = emgDao.findById(emgId, LocalDateTime.now(clock));
			if (e == null) {
				return;
			}
			List<Recipient> rcpts;
			String subject;
			switch (event) {
			case AcctConst.EV_EMG_SUBMIT -> {
				rcpts = withPerm(AcctConst.PERM_EMG_DECIDE);
				subject = "【緊急存取】" + emgId + " 已送出，待您補核准（期限 " + dateTimeText(e.getDueDate()) + "）";
			}
			default -> throw new IllegalArgumentException("未知的通知事件 " + event);
			}
			sendAll(event, AcctConst.REF_EMG, emgId, rcpts, subject, html.page(subject, emgBody(e), MailHtml.PATH_EMG), createBy);
		} catch (RuntimeException ex) {
			log.error("通知失敗 event={} emgId={} 例外={}", event, emgId, ex.getClass().getSimpleName());
		}
	}

	/** 緊急存取本文的兩欄表（HTML 片段，值已跳脫） */
	static String emgBody(Emg e) {
		List<String[]> rows = new ArrayList<>();
		rows.add(new String[] { "單號", e.getEmgId() });
		rows.add(new String[] { "帳號類別", nvl(e.getCatgName(), e.getCatgId()) });
		rows.add(new String[] { "帳號或系統", nvl(e.getTargetDescr(), "") });
		rows.add(new String[] { "申請人", nvl(e.getApplyByName(), e.getApplyBy()) });
		rows.add(new String[] { "緊急事由", nvl(e.getApplyReason(), "") });
		rows.add(new String[] { "預估時長", e.getExpectHourQty() == null ? "" : e.getExpectHourQty() + " 小時" });
		rows.add(new String[] { "送出時間", dateTimeText(e.getSubmitDate()) });
		rows.add(new String[] { "補核准期限", dateTimeText(e.getDueDate()) });
		return MailHtml.table(rows);
	}

	private static String dateTimeText(LocalDateTime t) {
		return t == null ? "" : t.format(DATE_TIME);
	}

	// ------------------------------------------------------------
	// 到期（J01／J02）、排程失敗
	// ------------------------------------------------------------

	/**
	 * EXPIRY_7D／EXPIRED：原新增申請人（空則略過）、該類別執行人、到期角色成員。
	 *
	 * @return 寫入的通知紀錄筆數（0＝沒有收件人或寫入失敗）
	 */
	public int expiryEvent(String event, Acct a, LocalDate today) {
		try {
			List<Recipient> rcpts = new ArrayList<>();
			if (a.getApplyBy() != null) {
				rcpts.addAll(single(a.getApplyBy()));
			}
			rcpts.addAll(permDao.findExecUsers(a.getCatgId()));
			rcpts.addAll(inRole(AcctConst.MAIL_ROLE_EXPIRY));
			LocalDate exp = a.getExpiryDate() == null ? null : a.getExpiryDate().toLocalDate();
			String name = acctNameText(a.getCatgId(), a.getAcctName());
			String expText = exp == null ? "" : exp.format(DATE);
			String subject = AcctConst.EV_EXPIRED.equals(event) ? "【帳號到期】" + name + " 已逾期"
					: "【帳號到期】" + name + " 將於 " + expText + " 到期";
			List<String[]> rows = new ArrayList<>();
			rows.add(new String[] { "單號", a.getAcctId() });
			rows.add(new String[] { "帳號類別", nvl(a.getCatgName(), a.getCatgId()) });
			rows.add(new String[] { "帳號名稱", name });
			rows.add(new String[] { "使用人", nvl(a.getHolderName(), "") });
			rows.add(new String[] { "到期日", expText });
			if (exp != null && AcctConst.EV_EXPIRED.equals(event)) {
				rows.add(new String[] { "逾期天數", String.valueOf(ChronoUnit.DAYS.between(exp, today)) });
			}
			return sendAll(event, AcctConst.REF_ACCT, acctRefId(a.getAcctId(), exp), rcpts, subject,
					html.page(subject, MailHtml.table(rows), MailHtml.PATH_HOME), AcctConst.SYSTEM_USER);
		} catch (RuntimeException e) {
			log.error("通知失敗 event={} acctId={} 例外={}", event, a.getAcctId(), e.getClass().getSimpleName());
			return 0;
		}
	}

	/** WEEKLY_SUMMARY：到期角色成員，每人一封；內文以區塊組成（第二階段加入逾期未審區塊不改結構） */
	public int weeklySummary(String weekKey, List<Acct> expired, LocalDate today) {
		try {
			List<String> blocks = new ArrayList<>();
			blocks.add(expiredBlock(expired, today));
			String subject = "【帳號到期】本週已逾期未處理帳號彙總（" + expired.size() + " 筆）";
			return sendAll(AcctConst.EV_WEEKLY_SUMMARY, AcctConst.REF_WEEK, weekKey, inRole(AcctConst.MAIL_ROLE_EXPIRY), subject,
					html.page(subject, String.join("", blocks), MailHtml.PATH_HOME), AcctConst.SYSTEM_USER);
		} catch (RuntimeException e) {
			log.error("通知失敗 event={} week={} 例外={}", AcctConst.EV_WEEKLY_SUMMARY, weekKey, e.getClass().getSimpleName());
			return 0;
		}
	}

	/** 已逾期區塊（HTML 片段）：區塊標題＋多欄表；0 筆顯示 U13 */
	static String expiredBlock(List<Acct> expired, LocalDate today) {
		String head = MailHtml.heading("■ 已逾期未處理帳號");
		if (expired.isEmpty()) {
			return head + MailHtml.para(U13);
		}
		List<List<String>> rows = new ArrayList<>();
		for (Acct a : expired) {
			LocalDate exp = a.getExpiryDate() == null ? null : a.getExpiryDate().toLocalDate();
			rows.add(List.of(nvl(a.getAcctId(), ""), nvl(nvl(a.getCatgName(), a.getCatgId()), ""),
					acctNameText(a.getCatgId(), a.getAcctName()), nvl(a.getHolderName(), ""),
					exp == null ? "" : exp.format(DATE), exp == null ? "" : String.valueOf(ChronoUnit.DAYS.between(exp, today))));
		}
		return head + MailHtml.rowsTable(List.of("單號", "類別", "帳號名稱", "使用人", "到期日", "逾期天數"), rows);
	}

	/**
	 * 排程失敗信。runId 為空＝連執行序號都沒取到（參照鍵寫 0）；
	 * resultCode 為 SUCCESS＝排程本身做完、只有結束紀錄寫入失敗
	 */
	public void jobFail(String jobCode, Long runId, String resultCode, String errMsg) {
		try {
			String what = AcctConst.JR_SUCCESS.equals(resultCode) ? "結束紀錄寫入失敗"
					: AcctConst.JR_PARTIAL.equals(resultCode) ? "執行部分失敗" : "執行失敗";
			String subject = "【排程失敗】" + jobCode + " " + what;
			String body = html.page(subject, MailHtml.table(List.of(new String[] { "排程代碼", jobCode },
					new String[] { "執行序號", runId == null ? "（未取得）" : String.valueOf(runId) }, new String[] { "結果", resultCode },
					new String[] { "錯誤摘要", nvl(errMsg, "") })), MailHtml.PATH_HOME);
			sendAll(AcctConst.EV_JOB_FAIL, AcctConst.REF_JOB, runId == null ? "0" : String.valueOf(runId),
					inRole(AcctConst.MAIL_ROLE_JOB_FAIL), subject, body, AcctConst.SYSTEM_USER);
		} catch (RuntimeException e) {
			log.error("通知失敗 event={} runId={} 例外={}", AcctConst.EV_JOB_FAIL, runId, e.getClass().getSimpleName());
		}
	}

	// ------------------------------------------------------------
	// 共用
	// ------------------------------------------------------------

	/** 去重後逐人寄送並寫通知紀錄；回寫入的紀錄筆數 */
	int sendAll(String event, String refType, String refId, List<Recipient> rcpts, String subject, String body,
			String createBy) {
		Map<String, Recipient> unique = new LinkedHashMap<>();
		for (Recipient r : rcpts) {
			if (r != null && r.getUserId() != null) {
				unique.putIfAbsent(r.getUserId(), r);
			}
		}
		int written = 0;
		int ok = 0;
		int skip = 0;
		int fail = 0;
		for (Recipient r : unique.values()) {
			String email = Texts.trimToNull(r.getEmail());
			String result;
			String err = null;
			if (email == null) {
				result = AcctConst.NR_SKIP;
				skip++;
			} else if (!allowList.allows(r.getUserId())) {
				result = AcctConst.NR_SKIP;
				err = NOT_IN_ALLOW_LIST;
				skip++;
			} else {
				try {
					mailSender.send(email, subject, body);
					result = AcctConst.NR_SUCCESS;
					ok++;
				} catch (RuntimeException e) {
					result = AcctConst.NR_FAIL;
					err = Texts.truncate(e.getClass().getSimpleName(), ERR_MSG_MAX);
					fail++;
				}
			}
			log.info("通知寄送 event={} ref={} 收件人工號={} 結果={}", event, refId, r.getUserId(), result);
			try {
				notifyLogDao.insert(event, refType, refId, r.getUserId(), AcctConst.NR_SKIP.equals(result) ? null : email,
						result, err, LocalDateTime.now(clock), createBy);
				written++;
			} catch (RuntimeException e) {
				log.error("通知紀錄寫入失敗 event={} ref={} 例外={}", event, refId, e.getClass().getSimpleName());
			}
		}
		log.info("通知 event={} ref={} 收件人數={} 成功={} 略過={} 失敗={}", event, refId, unique.size(), ok, skip, fail);
		return written;
	}

	private List<Recipient> withPerm(String permId) {
		return permId == null ? List.of() : permDao.findUsersWithPerm(permId);
	}

	/** 角色成員：寄信當下查 IM_ACCT_ROLE_USER（STATUS＝1）＋IM_ACCT_USER（STATUS＝1），不快取 */
	private List<Recipient> inRole(String roleId) {
		return permDao.findUsersInRoles(List.of(roleId));
	}

	private List<Recipient> single(String userId) {
		if (userId == null) {
			return List.of();
		}
		Recipient r = permDao.findActiveUser(userId);
		return r == null ? List.of() : List.of(r);
	}

	/** 門禁卡尚無卡號時顯示「（門禁卡，待發卡）」 */
	static String acctNameText(String catgId, String acctName) {
		if (acctName == null || acctName.isBlank()) {
			return AcctConst.CATG_DOOR.equals(catgId) ? AcctConst.DOOR_NO_CARD_NAME : "";
		}
		return acctName;
	}

	private static String nvl(String a, String b) {
		return a == null ? b : a;
	}
}
