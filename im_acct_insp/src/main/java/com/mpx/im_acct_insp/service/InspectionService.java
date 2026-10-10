package com.mpx.im_acct_insp.service;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：機房巡檢（spec §3.8、§4.4）
//           新增巡檢須涵蓋全部啟用項目（不可缺、不可重複、不可有未知項目），EXCPT 必填異常說明；
//           EXCPT_CNT＝異常數，0 筆異常為 NORMAL 否則 EXCPT；巡檢單先寫、明細後寫（無交易，失敗記 error log 回 500）
//           標記已處理只接受 EXCPT 且未處理的項目，全部處理完轉 FIXED；巡檢相關 API 皆需 INSP 角色（項目清單只需身分）
//           變更單 #1：列表關鍵字以 AccountService.escapeLike 跳脫 \ % _ 後才傳給 DAO
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（spec_im_acct_insp_v2.0.md §4.14、§5、§6.6）：
//           權限改權限碼：項目清單 INSP_VIEW 或 INSP_CREATE、填寫 INSP_CREATE、查詢與明細 INSP_VIEW、標記處理 INSP_RESOLVE；
//           結果只 NORMAL／EXCPT；結果須剛好涵蓋所有啟用項目（否則 400 E18）、EXCPT 必填異常說明（E10）；
//           巡檢時間預設現在、可調整、不可晚於現在；取號 IN → INSERT 巡檢單 → INSERT 全部結果（含 ITEM_NAME_SNAP、SECT_NAME_SNAP）同一交易；
//           INSP_BY／INSP_BY_NAME＝實際使用者、切換中 ACT_AS_*＝代理身分；整體狀態即時計算（InspDao.INSP_SUMMARY）；
//           標記處理只接受 EXCPT 且未處理（否則 409 E19）、處理方式必填（E10），寫 FIX_BY_NAME、FIX_ACT_AS_*；回 {inspId, inspStateCode}
// ============================================================

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.mpx.im_acct_insp.common.AcctConst;
import com.mpx.im_acct_insp.common.AcctMsg;
import com.mpx.im_acct_insp.common.ApiException;
import com.mpx.im_acct_insp.common.DocSeqService;
import com.mpx.im_acct_insp.common.Identity;
import com.mpx.im_acct_insp.common.Texts;
import com.mpx.im_acct_insp.dao.InspDao;
import com.mpx.im_acct_insp.model.Insp;
import com.mpx.im_acct_insp.model.InspDetail;
import com.mpx.im_acct_insp.model.InspFixResult;
import com.mpx.im_acct_insp.model.InspIdResult;
import com.mpx.im_acct_insp.model.InspItem;
import com.mpx.im_acct_insp.model.InspResult;
import com.mpx.im_acct_insp.model.request.FixForm;
import com.mpx.im_acct_insp.model.request.InspForm;
import com.mpx.im_acct_insp.model.request.InspResultForm;

@Service
public class InspectionService {

	private static final Logger log = LoggerFactory.getLogger(InspectionService.class);

	private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
	private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
			.withResolverStyle(ResolverStyle.STRICT);

	private final PermissionService perm;
	private final DocSeqService docSeqService;
	private final InspDao inspDao;
	private final TransactionTemplate tx;
	private final Clock clock;

	public InspectionService(PermissionService perm, DocSeqService docSeqService, InspDao inspDao, TransactionTemplate tx,
			Clock clock) {
		this.perm = perm;
		this.docSeqService = docSeqService;
		this.inspDao = inspDao;
		this.tx = tx;
		this.clock = clock;
	}

	public List<InspItem> items(Identity id) {
		perm.requireAny(id, AcctConst.PERM_INSP_VIEW, AcctConst.PERM_INSP_CREATE);
		return inspDao.findItems();
	}

	public InspIdResult create(Identity id, InspForm form) {
		perm.require(id, AcctConst.PERM_INSP_CREATE);
		InspForm f = form == null ? new InspForm() : form;
		LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
		LocalDateTime inspDate = parseInspDate(f.getInspDate(), now);
		if (inspDate.isAfter(now)) {
			throw ApiException.badRequest(AcctMsg.INSP_DATE_FUTURE);
		}
		List<InspItem> items = inspDao.findItems();
		List<Object[]> rows = checkResults(items, f.getResults());
		String inspId;
		// 交易：取號 → INSERT 巡檢單 → INSERT 全部結果（spec §5）
		inspId = tx.execute(s -> {
			String newId = docSeqService.nextId(AcctConst.DOC_IN, id.getRealUserId());
			if (inspDao.insertInsp(newId, inspDate, id.getRealUserId(), id.getRealUserName(), id.actAsId(), id.actAsName(),
					now) != 1) {
				throw ApiException.internal("巡檢單寫入未完成");
			}
			for (Object[] r : rows) {
				inspDao.insertResult(newId, (InspItem) r[0], (String) r[1], (String) r[2], now, id.getRealUserId());
			}
			return newId;
		});
		log.info("巡檢送出 inspId={}", inspId);
		InspIdResult result = new InspIdResult();
		result.setInspId(inspId);
		return result;
	}

	/**
	 * 結果須剛好涵蓋所有啟用項目、每項 NORMAL 或 EXCPT（否則 400 E18）；EXCPT 必填異常說明（E10）。
	 * 回 [項目, 結果碼, 異常說明] 依項目排序
	 */
	static List<Object[]> checkResults(List<InspItem> items, List<InspResultForm> results) {
		Map<String, InspResultForm> byId = new LinkedHashMap<>();
		if (results != null) {
			for (InspResultForm r : results) {
				String itemId = r == null ? null : Texts.trimToNull(r.getInspItemId());
				if (itemId == null || byId.put(itemId, r) != null) {
					throw ApiException.badRequest(AcctMsg.E18);
				}
			}
		}
		if (items.isEmpty() || byId.size() != items.size()) {
			throw ApiException.badRequest(AcctMsg.E18);
		}
		List<Object[]> rows = new ArrayList<>();
		for (InspItem it : items) {
			InspResultForm r = byId.get(it.getInspItemId());
			String code = r == null ? null : Texts.trimToNull(r.getResultCode());
			if (code == null || !AcctConst.RESULT_CODES.contains(code)) {
				throw ApiException.badRequest(AcctMsg.E18);
			}
			String descr = null;
			if (AcctConst.RESULT_EXCPT.equals(code)) {
				descr = Texts.trimToNull(r.getExcptDescr());
				if (descr == null) {
					throw AcctMsg.fillIn(AcctMsg.WHAT_EXCPT);
				}
				Texts.maxChars(AcctMsg.WHAT_EXCPT, descr, 500);
			}
			rows.add(new Object[] { it, code, descr });
		}
		return rows;
	}

	public List<Insp> list(Identity id, String keyword, String stateCode, String dateFrom, String dateTo) {
		perm.require(id, AcctConst.PERM_INSP_VIEW);
		String state = Texts.trimToNull(stateCode);
		if (state != null && !AcctConst.INSP_STATES.contains(state)) {
			throw AcctMsg.badCode("狀態");
		}
		return inspDao.findList(Texts.escapeLike(Texts.trimToNull(keyword)), state, parseDate(dateFrom), parseDate(dateTo));
	}

	public InspDetail detail(Identity id, String inspId) {
		perm.require(id, AcctConst.PERM_INSP_VIEW);
		return load(inspId);
	}

	public InspFixResult fix(Identity id, String inspId, String itemId, FixForm form) {
		perm.require(id, AcctConst.PERM_INSP_RESOLVE);
		InspDetail d = load(inspId);
		InspResult target = d.getResults().stream().filter(r -> r.getInspItemId().equals(itemId)).findFirst().orElse(null);
		if (target == null) {
			throw AcctMsg.notFound();
		}
		if (!AcctConst.RESULT_EXCPT.equals(target.getResultCode()) || !Integer.valueOf(0).equals(target.getIsFixed())) {
			throw ApiException.conflict(AcctMsg.E19);
		}
		String fixDescr = form == null ? null : Texts.trimToNull(form.getFixDescr());
		if (fixDescr == null) {
			throw AcctMsg.fillIn(AcctMsg.WHAT_FIX);
		}
		Texts.maxChars(AcctMsg.WHAT_FIX, fixDescr, 500);
		LocalDateTime now = LocalDateTime.now(clock);
		// 單句帶條件 UPDATE（RESULT_CODE＝EXCPT AND IS_FIXED＝0），不需另開交易
		if (inspDao.fix(inspId, itemId, fixDescr, id.getRealUserId(), id.getRealUserName(), id.actAsId(), id.actAsName(),
				now) == 0) {
			throw ApiException.conflict(AcctMsg.E19);
		}
		log.info("巡檢異常標記已處理 inspId={} itemId={}", inspId, itemId);
		InspFixResult r = new InspFixResult();
		r.setInspId(inspId);
		InspDetail after = inspDao.findById(inspId);
		r.setInspStateCode(after == null ? null : after.getInspStateCode());
		return r;
	}

	private InspDetail load(String inspId) {
		InspDetail d = inspId == null ? null : inspDao.findById(inspId);
		if (d == null) {
			throw AcctMsg.notFound();
		}
		d.setResults(inspDao.findResults(inspId));
		return d;
	}

	/** 空值＝現在；接受 yyyy-MM-dd HH:mm:ss、yyyy-MM-dd HH:mm（秒補 0），日期與時間間可為 T */
	static LocalDateTime parseInspDate(String s, LocalDateTime now) {
		String v = Texts.trimToNull(s);
		if (v == null) {
			return now;
		}
		String norm = v.replace('T', ' ');
		if (norm.length() == 16) {
			norm = norm + ":00";
		}
		if (norm.length() > 19) {
			norm = norm.substring(0, 19);
		}
		try {
			return LocalDateTime.parse(norm, DATE_TIME_FMT);
		} catch (DateTimeParseException ex) {
			throw ApiException.badRequest(AcctMsg.BAD_DATE_TIME);
		}
	}

	private static LocalDate parseDate(String s) {
		String v = Texts.trimToNull(s);
		if (v == null) {
			return null;
		}
		try {
			return LocalDate.parse(v, DATE_FMT);
		} catch (DateTimeParseException ex) {
			throw ApiException.badRequest(AcctMsg.BAD_DATE);
		}
	}
}
