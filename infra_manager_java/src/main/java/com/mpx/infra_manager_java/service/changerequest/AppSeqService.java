package com.mpx.infra_manager_java.service.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：申請單取號（S6 回合二 b-1，施工計畫回合二 b）。同一交易內：UPDATE LAST_NO+1 → 0 列則 INSERT 1 →
//           INSERT 撞唯一鍵（別人同時建了當天第一張）就再 UPDATE 一次 → SELECT 取回號碼。
//           計數只增不減：刪單不回收號碼，所以同日刪單再建不會撞號。超過 999 自然變 4 位數（施工計畫待確認第 7 題）。
//           從 AppDraftService 呼叫時併入建單交易，計數列會鎖到建單 commit 為止，所以建單交易內不得放慢動作
// ============================================================

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mpx.infra_manager_java.dao.changerequest.AppSeqDao;
import com.mpx.infra_manager_java.util.TaiwanTime;

@Service
public class AppSeqService {

	public static final String PREFIX_ONLINE = "IM";

	private static final DateTimeFormatter YMD = DateTimeFormatter.BASIC_ISO_DATE;

	private final AppSeqDao appSeqDao;

	public AppSeqService(AppSeqDao appSeqDao) {
		this.appSeqDao = appSeqDao;
	}

	/** 取當天下一個序號（1 起） */
	@Transactional
	public int nextNo(String prefix, LocalDate date, String by) {
		Timestamp seqDate = TaiwanTime.startOf(date);
		if (appSeqDao.increment(prefix, seqDate, by) == 0) {
			try {
				appSeqDao.insertFirst(prefix, seqDate, by);
			} catch (DuplicateKeyException e) {
				if (appSeqDao.increment(prefix, seqDate, by) == 0) {
					throw new IllegalStateException("申請單計數列不存在或已停用");
				}
			}
		}
		Integer no = appSeqDao.findLastNo(prefix, seqDate);
		if (no == null) {
			throw new IllegalStateException("申請單計數列不存在或已停用");
		}
		return no;
	}

	/** 線上申請單號：IMyyyyMMdd-三碼序號 */
	public static String onlineAppId(LocalDate date, int no) {
		return PREFIX_ONLINE + date.format(YMD) + "-" + String.format("%03d", no);
	}
}
