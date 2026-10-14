package com.mpx.im_acct_insp.dao;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：首頁卡片、到期提醒、待辦簽核查詢（spec §3.7、§4.1）
//           帳號總數／特權不含 DELETED／VOID；待審核＝REVIEW＋CONFIRM（原型 stat-acc-pending：非已核准／已停用／已退件）；
//           30 天內到期＝APPROVED、DATE、到期日－今天 ≤ 30（含已過期）；本月異常＝本月巡檢中 IS_FIXED＝0 的 EXCPT 項目數
//           待辦依動作分三支查詢，關卡／類別清單為空時 Service 不呼叫（避免 IN () 語法錯）
//           變更單 #1：待辦查詢加回 CATG_ID、IS_PRIV（前端以 catgId＝DOOR 判斷門禁卡）
//           測試第 1 輪修正：IM_USER 改 schema 限定名稱（引用 AcctConst.EXISTING_USER_TABLE）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（spec_im_acct_insp_v2.0.md §4.13）：六張卡片口徑改寫——帳號總數＝非 DELETED、待審核＝帳號狀態 REVIEW、
//           30 天內到期＝到期提醒範圍（AcctDao.EXPIRING_COND，含已逾期）、特權＝IS_PRIV＝1 且非 DELETED、
//           本月巡檢次數、本月異常（EXCPT 且 IS_FIXED＝0）；今天與月份界線一律綁定參數（不用 SYSDATE）；
//           鈴鐺清單改用到期提醒範圍並附 catgId、acctStateCode、applyBy（逐筆相關性由 Service 判斷）；
//           待辦查詢移到 AcctReqDao（帶 versionNo、複驗退回資訊）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S8：卡片加兩張——緊急存取待補核准（EmgDao.UNDECIDED_COND，含逾期）、
//           緊急存取異常待追蹤（EmgDao.RETURNED_COND）；數字看全部（不依登入者過濾）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-14
// 變更說明: DB 別名 key 由 db.connect.itflow 改名為 itflow.db.alias（使用者改名）
// ============================================================

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.mpx.common.db.DbClient;
import com.mpx.im_acct_insp.model.ExpiringAcct;
import com.mpx.im_acct_insp.model.HomeStats;

@Repository
public class HomeDao {

	/** 首頁卡片（共用計算函式，第二階段統計頁可沿用）：帳號四張、巡檢兩張、緊急存取兩張 */
	public static final String FIND_STATS = """
			SELECT (SELECT COUNT(*) FROM IM_ACCT A WHERE A.ACCT_STATE_CODE <> 'DELETED') AS ACCT_TOTAL,
			       (SELECT COUNT(*) FROM IM_ACCT A WHERE A.ACCT_STATE_CODE = 'REVIEW') AS ACCT_REVIEWING,
			       (SELECT COUNT(*) FROM IM_ACCT A WHERE
			""" + AcctDao.EXPIRING_COND + """
			       ) AS ACCT_EXPIRING,
			       (SELECT COUNT(*) FROM IM_ACCT A WHERE A.IS_PRIV = 1 AND A.ACCT_STATE_CODE <> 'DELETED') AS ACCT_PRIV,
			       (SELECT COUNT(*) FROM IM_INSP I
			         WHERE I.STATUS = 1 AND I.INSP_DATE >= :monthStart AND I.INSP_DATE < :nextMonthStart) AS INSP_THIS_MONTH,
			       (SELECT COUNT(*) FROM IM_INSP_RESULT R JOIN IM_INSP I ON I.INSP_ID = R.INSP_ID
			         WHERE I.STATUS = 1 AND R.RESULT_CODE = 'EXCPT' AND R.IS_FIXED = 0
			           AND I.INSP_DATE >= :monthStart AND I.INSP_DATE < :nextMonthStart) AS INSP_EXCPT_THIS_MONTH,
			       (SELECT COUNT(*) FROM IM_EMG_ACCESS E WHERE
			""" + EmgDao.UNDECIDED_COND + """
			       ) AS EMG_UNDECIDED,
			       (SELECT COUNT(*) FROM IM_EMG_ACCESS E WHERE
			""" + EmgDao.RETURNED_COND + """
			       ) AS EMG_RETURNED
			  FROM DUAL
			""";

	/** 到期提醒範圍內的帳號，依到期日由近到遠 */
	public static final String FIND_EXPIRING = """
			SELECT A.ACCT_ID, A.ACCT_NAME, A.CATG_ID, C.CATG_NAME, A.ACCT_STATE_CODE, A.EXPIRY_DATE, A.APPLY_BY
			  FROM IM_ACCT A
			  LEFT JOIN IM_ACCT_CATG C ON C.CATG_ID = A.CATG_ID
			 WHERE
			""" + AcctDao.EXPIRING_COND + """
			 ORDER BY A.EXPIRY_DATE, A.ACCT_ID
			""";

	private final DbClient dbClient;
	private final String itflowDb;

	public HomeDao(DbClient dbClient, @Value("${itflow.db.alias}") String itflowDb) {
		this.dbClient = dbClient;
		this.itflowDb = itflowDb;
	}

	public HomeStats findStats(LocalDate today) {
		LocalDate monthStart = today.withDayOfMonth(1);
		Map<String, Object> params = new HashMap<>();
		params.put("today", DaoSupport.ts(today));
		params.put("monthStart", DaoSupport.ts(monthStart));
		params.put("nextMonthStart", DaoSupport.ts(monthStart.plusMonths(1)));
		List<HomeStats> rows = dbClient.query(itflowDb, FIND_STATS, params, HomeStats.class);
		return rows.isEmpty() ? new HomeStats() : rows.get(0);
	}

	public List<ExpiringAcct> findExpiring(LocalDate today) {
		Map<String, Object> params = new HashMap<>();
		params.put("today", DaoSupport.ts(today));
		return dbClient.query(itflowDb, FIND_EXPIRING, params, ExpiringAcct.class);
	}
}
