package com.mpx.infra_manager.service;

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
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S4b-2：emgEvent 加 EMG_RETURN（S4-⑪）——只寄原申請人，主旨「已退回」，本文＝送出信 8 列＋
//           處理人（有效身分姓名）、退回原因、回報結果（未回報／無異動／有異動：回報說明；代碼不明時標示不明），信末提醒後續由主管異常結案；
//           連結仍為「緊急存取」。寄失敗沿用 sendAll（退回照常成功、FAIL 紀錄，S4-⑫）。emgRows 抽出共用列清單
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S7（S7-⑨）：新增 emgOverdue（J03 呼叫）——以排程基準時間點重讀該單，已處理（DECIDE_CODE 非空）
//           或不存在則回 0 不寄；寄給所有 EMG_DECIDE 持有人，主旨「已逾期未補核准（期限 …）」，本文＝送出信 8 列＋回報結果＋
//           提醒「補核准須申請人先回報；逾期後仍可補核准或退回」；參照 EMG／單號、CREATE_BY＝SYSTEM；
//           回寫入筆數（0＝已處理、不存在、無收件人或紀錄全部寫入失敗；SMTP 寄送失敗寫 FAIL 紀錄、仍計入）
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S7 回合 2：sendAll 改回 SendResult（寫入筆數＋成功／略過／失敗人數），既有呼叫端取 written()；
//           emgOverdue 改回 OverdueResult 四值讓 J03 區分——SKIPPED（寄前重讀發現已處理或不存在：不寄、不計、不算失敗）、
//           SENT（有寫入紀錄且不是全部寄送失敗）、ALL_FAILED（有寫入紀錄但沒有人寄成功且至少一人 SMTP 失敗：算已寄不重寄、J03 記 PARTIAL）、
//           NOT_WRITTEN（一筆紀錄都沒寫入：無收件人、紀錄全部寫入失敗或例外；J03 記 PARTIAL 下次再試）；
//           jobFail 改回 boolean＝SendResult.reached（至少一人成功或全部略過），J03 以此決定要不要記「今天已寄」
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
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

import com.mpx.infra_manager.common.AcctConst;
import com.mpx.infra_manager.common.Texts;
import com.mpx.infra_manager.dao.AcctReqDao;
import com.mpx.infra_manager.dao.EmgDao;
import com.mpx.infra_manager.dao.NotifyLogDao;
import com.mpx.infra_manager.dao.PermDao;
import com.mpx.infra_manager.model.Acct;
import com.mpx.infra_manager.model.AcctReq;
import com.mpx.infra_manager.model.ApprStage;
import com.mpx.infra_manager.model.Emg;
import com.mpx.infra_manager.model.Recipient;

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

	/**
	 * sendAll 的結果（收件人去重後）：written＝寫入的通知紀錄筆數（含 SKIP／FAIL），ok／skip／fail＝各結果人數。
	 * 寫紀錄失敗的人仍計入 ok／skip／fail（結果已決定，只是沒寫進 DB），所以 written 可能小於三者之和
	 */
	record SendResult(int written, int ok, int skip, int fail) {
		/**
		 * 這封信「算寄到了」：至少一人寄成功，或沒有人失敗且至少一人略過（無 Email／名單外，S7-④ 把 SKIP 當已寄）。
		 * 沒有收件人、或沒人成功且有人 SMTP 失敗 → false
		 */
		boolean reached() {
			return ok > 0 || (fail == 0 && skip > 0);
		}
	}

	/** emgOverdue 的結果；J03 據此決定 AFFECTED_CNT 與 PARTIAL（S7-⑧⑩、回合 2 裁示 (a)(b)） */
	public enum OverdueResult {
		/** 寄前重讀發現已處理（補核准或退回）或不存在：不寄、不計、不算失敗；下次掃描不會再列出這張單 */
		SKIPPED,
		/** 至少寫入一筆紀錄，且不是「全部寄送失敗」：算已寄（含收件人全部 SKIP 的情況，S7-④） */
		SENT,
		/** 有寫入紀錄、沒有任何人寄成功、且至少一人 SMTP 失敗：紀錄已在、算已寄不重寄，但 J03 記 PARTIAL 讓人知道沒人收到 */
		ALL_FAILED,
		/** 一筆紀錄都沒寫入（沒有收件人、紀錄全部寫入失敗、或途中例外）：J03 記 PARTIAL，該單下次掃描會再列出 */
		NOT_WRITTEN
	}

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
	 * @param event    EMG_SUBMIT／EMG_RETURN（EMG_OVERDUE 由排程走 emgOverdue）
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
			String body;
			switch (event) {
			case AcctConst.EV_EMG_SUBMIT -> {
				rcpts = withPerm(AcctConst.PERM_EMG_DECIDE);
				subject = "【緊急存取】" + emgId + " 已送出，待您補核准（期限 " + dateTimeText(e.getDueDate()) + "）";
				body = emgBody(e);
			}
			case AcctConst.EV_EMG_RETURN -> {
				rcpts = single(e.getApplyBy());
				subject = "【緊急存取】" + emgId + " 已退回";
				body = emgReturnBody(e);
			}
			default -> throw new IllegalArgumentException("未知的通知事件 " + event);
			}
			sendAll(event, AcctConst.REF_EMG, emgId, rcpts, subject, html.page(subject, body, MailHtml.PATH_EMG), createBy);
		} catch (RuntimeException ex) {
			log.error("通知失敗 event={} emgId={} 例外={}", event, emgId, ex.getClass().getSimpleName());
		}
	}

	/**
	 * EMG_OVERDUE（J03 逾期通知，S7-⑨）：以排程基準時間點 base 重讀該單；不存在或主管已處理（補核准或退回）則不寄、回 SKIPPED。
	 * 收件人＝所有 EMG_DECIDE 持有人；參照 EMG／單號、CREATE_BY＝SYSTEM。不外拋：途中例外回 NOT_WRITTEN。
	 *
	 * @return 四種結果的意義見 {@link OverdueResult}；SMTP 寄失敗寫 FAIL 紀錄、算已寄（S7-④），只有沒半個人寄成功才回 ALL_FAILED
	 */
	public OverdueResult emgOverdue(String emgId, LocalDateTime base) {
		try {
			Emg e = emgDao.findById(emgId, base);
			if (e == null || e.getDecideCode() != null) {
				log.info("逾期通知略過（已處理或不存在）emgId={}", emgId);
				return OverdueResult.SKIPPED;
			}
			String subject = "【緊急存取】" + emgId + " 已逾期未補核准（期限 " + dateTimeText(e.getDueDate()) + "）";
			SendResult s = sendAll(AcctConst.EV_EMG_OVERDUE, AcctConst.REF_EMG, emgId, withPerm(AcctConst.PERM_EMG_DECIDE), subject,
					html.page(subject, emgOverdueBody(e), MailHtml.PATH_EMG), AcctConst.SYSTEM_USER);
			if (s.written() == 0) {
				return OverdueResult.NOT_WRITTEN;
			}
			// 「全部失敗」＝沒有人寄成功、且至少一人是 SMTP 失敗；全是 SKIP（無 Email／名單外）照 S7-④ 當已寄
			return s.ok() == 0 && s.fail() > 0 ? OverdueResult.ALL_FAILED : OverdueResult.SENT;
		} catch (RuntimeException ex) {
			log.error("通知失敗 event={} emgId={} 例外={}", AcctConst.EV_EMG_OVERDUE, emgId, ex.getClass().getSimpleName());
			return OverdueResult.NOT_WRITTEN;
		}
	}

	/** 逾期信本文：送出信 8 列＋回報結果，信末提醒處理方式（S7-⑨） */
	static String emgOverdueBody(Emg e) {
		List<String[]> rows = emgRows(e);
		rows.add(new String[] { "回報結果", reportResultText(e) });
		return MailHtml.table(rows) + MailHtml.para(EMG_OVERDUE_FOOTER);
	}

	/** 逾期信信末提醒 */
	static final String EMG_OVERDUE_FOOTER = "補核准須申請人先回報；逾期後仍可補核准或退回";

	/** 緊急存取送出信本文的兩欄表（HTML 片段，值已跳脫） */
	static String emgBody(Emg e) {
		return MailHtml.table(emgRows(e));
	}

	/** 退回信本文：送出信 8 列＋處理人、退回原因、回報結果，信末提醒後續流程（S4-⑪） */
	static String emgReturnBody(Emg e) {
		List<String[]> rows = emgRows(e);
		rows.add(new String[] { "處理人", nvl(e.getDecideByName(), nvl(e.getDecideBy(), "")) });
		rows.add(new String[] { "退回原因", nvl(e.getReturnReason(), "") });
		rows.add(new String[] { "回報結果", reportResultText(e) });
		return MailHtml.table(rows) + MailHtml.para(EMG_RETURN_FOOTER);
	}

	/** 退回信信末提醒 */
	static final String EMG_RETURN_FOOTER = "後續由主管異常結案，若有帳號異動請告知處理主管";

	/**
	 * 回報結果：未回報／無異動／有異動：{回報說明}（說明空白時只寫「有異動」）。
	 * 代碼不是 NONE／MODIFIED（DB 無 CHECK 限制，資料被手動改壞）時寫「（不明：{代碼}）」，不得對申請人說成「無異動」
	 */
	static String reportResultText(Emg e) {
		String type = e.getReportTypeCode();
		if (type == null) {
			return "未回報";
		}
		if (AcctConst.EMG_REPORT_MODIFIED.equals(type)) {
			String descr = Texts.trimToNull(e.getReportDescr());
			return descr == null ? "有異動" : "有異動：" + descr;
		}
		if (AcctConst.EMG_REPORT_NONE.equals(type)) {
			return "無異動";
		}
		return "（不明：" + type + "）";
	}

	/** 送出信的 8 列（送出信與退回信共用；回傳可再追加的清單） */
	private static List<String[]> emgRows(Emg e) {
		List<String[]> rows = new ArrayList<>();
		rows.add(new String[] { "單號", e.getEmgId() });
		rows.add(new String[] { "帳號類別", nvl(e.getCatgName(), e.getCatgId()) });
		rows.add(new String[] { "帳號或系統", nvl(e.getTargetDescr(), "") });
		rows.add(new String[] { "申請人", nvl(e.getApplyByName(), e.getApplyBy()) });
		rows.add(new String[] { "緊急事由", nvl(e.getApplyReason(), "") });
		rows.add(new String[] { "預估時長", e.getExpectHourQty() == null ? "" : e.getExpectHourQty() + " 小時" });
		rows.add(new String[] { "送出時間", dateTimeText(e.getSubmitDate()) });
		rows.add(new String[] { "補核准期限", dateTimeText(e.getDueDate()) });
		return rows;
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
					html.page(subject, MailHtml.table(rows), MailHtml.PATH_HOME), AcctConst.SYSTEM_USER).written();
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
					html.page(subject, String.join("", blocks), MailHtml.PATH_HOME), AcctConst.SYSTEM_USER).written();
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
	 * resultCode 為 SUCCESS＝排程本身做完、只有結束紀錄寫入失敗。
	 * 回「算寄到了沒」（SendResult.reached）：J03 的「同一種失敗當天一封」只在 true 時記下，SMTP 故障期間沒寄到的不占名額（回合 2 審查 ⑧B）；
	 * 查收件人或組信途中例外回 false
	 */
	public boolean jobFail(String jobCode, Long runId, String resultCode, String errMsg) {
		try {
			String what = AcctConst.JR_SUCCESS.equals(resultCode) ? "結束紀錄寫入失敗"
					: AcctConst.JR_PARTIAL.equals(resultCode) ? "執行部分失敗" : "執行失敗";
			String subject = "【排程失敗】" + jobCode + " " + what;
			String body = html.page(subject, MailHtml.table(List.of(new String[] { "排程代碼", jobCode },
					new String[] { "執行序號", runId == null ? "（未取得）" : String.valueOf(runId) }, new String[] { "結果", resultCode },
					new String[] { "錯誤摘要", nvl(errMsg, "") })), MailHtml.PATH_HOME);
			return sendAll(AcctConst.EV_JOB_FAIL, AcctConst.REF_JOB, runId == null ? "0" : String.valueOf(runId),
					inRole(AcctConst.MAIL_ROLE_JOB_FAIL), subject, body, AcctConst.SYSTEM_USER).reached();
		} catch (RuntimeException e) {
			log.error("通知失敗 event={} runId={} 例外={}", AcctConst.EV_JOB_FAIL, runId, e.getClass().getSimpleName());
			return false;
		}
	}

	// ------------------------------------------------------------
	// 共用
	// ------------------------------------------------------------

	/** 去重後逐人寄送並寫通知紀錄；回寫入的紀錄筆數與成功／略過／失敗人數（SendResult） */
	SendResult sendAll(String event, String refType, String refId, List<Recipient> rcpts, String subject, String body,
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
		return new SendResult(written, ok, skip, fail);
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
