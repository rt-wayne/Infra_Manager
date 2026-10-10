package com.mpx.im_acct_insp.common;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：單號取號（spec §3.1）：前綴＋yyyymmdd＋「-」＋3 碼
//           DbClient 無交易，改樂觀 CAS：讀 CURR_SEQ → UPDATE … WHERE CURR_SEQ＝:curr，影響 0 筆重讀重試，最多 10 次；
//           當日第一筆先 INSERT，PK 衝突（他人同時建立）則改走 UPDATE；超過 999 號或重試用盡回 500
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（spec_im_acct_insp_v2.0.md §4.1）：前綴改 AR／IN（AcctConst.DOC_AR）；取號由呼叫端放在送出交易內，
//           交易回復時流水號一併回復；CREATE_DATE／UPDATE_DATE 改由 Clock 產生後綁定（不用 SYSDATE）
// ============================================================

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.mpx.im_acct_insp.dao.DocSeqDao;

@Service
public class DocSeqService {

	private static final Logger log = LoggerFactory.getLogger(DocSeqService.class);

	static final int MAX_TRY = 10;
	static final int MAX_SEQ = 999;
	private static final DateTimeFormatter SEQ_DATE = DateTimeFormatter.BASIC_ISO_DATE;

	private final DocSeqDao docSeqDao;
	private final Clock clock;

	public DocSeqService(DocSeqDao docSeqDao, Clock clock) {
		this.docSeqDao = docSeqDao;
		this.clock = clock;
	}

	public String nextId(String docTypeId, String userId) {
		LocalDateTime now = LocalDateTime.now(clock);
		String seqDate = LocalDate.from(now).format(SEQ_DATE);
		for (int i = 0; i < MAX_TRY; i++) {
			Integer curr = docSeqDao.findCurrSeq(docTypeId, seqDate);
			if (curr == null) {
				try {
					docSeqDao.insertFirst(docTypeId, seqDate, userId, now);
					return format(docTypeId, seqDate, 1);
				} catch (DataIntegrityViolationException dup) {
					// 他人已先建立當日資料，重讀後走 UPDATE
					continue;
				}
			}
			int next = curr + 1;
			if (next > MAX_SEQ) {
				log.error("單號已用完 docTypeId={} seqDate={}", docTypeId, seqDate);
				throw ApiException.internal("今日單號已用完，請聯絡系統管理員");
			}
			if (docSeqDao.updateCas(docTypeId, seqDate, curr, next, userId, now) == 1) {
				return format(docTypeId, seqDate, next);
			}
		}
		log.error("取號重試 {} 次仍失敗 docTypeId={} seqDate={}", MAX_TRY, docTypeId, seqDate);
		throw ApiException.internal("取號失敗，請稍後再試");
	}

	static String format(String docTypeId, String seqDate, int seq) {
		return docTypeId + seqDate + "-" + String.format("%03d", seq);
	}
}
