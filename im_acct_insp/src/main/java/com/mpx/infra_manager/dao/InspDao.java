package com.mpx.infra_manager.dao;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：機房巡檢 IM_INSP_ITEM／IM_INSP／IM_INSP_RESULT 存取（spec §3.8、§4.4）
//           標記已處理只更新 RESULT_CODE＝EXCPT 且 IS_FIXED＝0 的項目；全部異常已處理時以單句條件 UPDATE 轉 FIXED
//           變更單 #1：列表關鍵字 LIKE 加 ESCAPE '\'（關鍵字由 Service 跳脫 \ % _）
//           測試第 1 輪修正：IM_USER 改 schema 限定名稱（引用 AcctConst.EXISTING_USER_TABLE）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（spec_im_acct_insp_v2.0.md §4.14、§6.6；Table v0.2 7.18、7.19）：
//           整體狀態不存欄位，列表、明細、篩選共用 INSP_SUMMARY（spec §4.14 的 CASE）；異常數、未處理數同段計算；
//           寫入 INSP_BY_NAME、ACT_AS_ID／ACT_AS_NAME、ITEM_NAME_SNAP／SECT_NAME_SNAP、FIX_BY_NAME、FIX_ACT_AS_*（快照，不 JOIN 使用者表）；
//           關鍵字比對 INSP_ID、INSP_BY_NAME、ACT_AS_NAME；日期區間依 INSP_DATE 含頭尾（:dateFrom ≤ INSP_DATE < :dateToNext）；
//           刪 FIXED 狀態轉換 SQL；時間一律綁定參數
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: INSP_SUMMARY 的狀態欄改用 AcctReqDao 的「"   " + 片段 + " AS 別名\n"」串接寫法（原寫法靠結尾 """ 那行撐住最短縮排，
//           結尾一移就會拼出 ENDAS；同 EmgDao 修正，BACKLOG 第 46 項 S3 實測發現），SQL 只差空白
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-14
// 變更說明: DB 別名 key 由 db.connect.itflow 改名為 itflow.db.alias（使用者改名）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// ============================================================

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.mpx.common.db.DbClient;
import com.mpx.infra_manager.model.Insp;
import com.mpx.infra_manager.model.InspDetail;
import com.mpx.infra_manager.model.InspItem;
import com.mpx.infra_manager.model.InspResult;

@Repository
public class InspDao {

	public static final String FIND_ITEMS = """
			SELECT INSP_ITEM_ID, SECT_CODE, SECT_NAME, ITEM_NAME, ITEM_DESCR, SORT_SEQ
			  FROM IM_INSP_ITEM
			 WHERE STATUS = 1
			 ORDER BY SORT_SEQ, INSP_ITEM_ID
			""";

	public static final String INSERT_INSP = """
			INSERT INTO IM_INSP (INSP_ID, INSP_DATE, INSP_BY, INSP_BY_NAME, ACT_AS_ID, ACT_AS_NAME, SIGN_DATE, CREATE_DATE, CREATE_BY)
			VALUES (:inspId, :inspDate, :inspBy, :inspByName, :actAsId, :actAsName, :now, :now, :inspBy)
			""";

	public static final String INSERT_RESULT = """
			INSERT INTO IM_INSP_RESULT (INSP_ID, INSP_ITEM_ID, ITEM_NAME_SNAP, SECT_NAME_SNAP, RESULT_CODE, EXCPT_DESCR, IS_FIXED,
			       CREATE_DATE, CREATE_BY)
			VALUES (:inspId, :inspItemId, :itemNameSnap, :sectNameSnap, :resultCode, :excptDescr, 0, :now, :userId)
			""";

	/** 整體狀態（spec §4.14）：沒有異常 NORMAL、有未處理異常 EXCPT、否則 FIXED */
	static final String STATE_EXPR = """
			CASE
			         WHEN SUM(CASE WHEN R.RESULT_CODE = 'EXCPT' THEN 1 ELSE 0 END) = 0 THEN 'NORMAL'
			         WHEN SUM(CASE WHEN R.RESULT_CODE = 'EXCPT' AND R.IS_FIXED = 0 THEN 1 ELSE 0 END) > 0 THEN 'EXCPT'
			         ELSE 'FIXED'
			       END""";

	/** 巡檢單＋即時計算的異常數、未處理數、整體狀態；列表、明細、篩選共用 */
	public static final String INSP_SUMMARY = """
			SELECT X.INSP_ID, X.INSP_DATE, X.INSP_BY, X.INSP_BY_NAME, X.ACT_AS_ID, X.ACT_AS_NAME, X.SIGN_DATE,
			       X.EXCPT_CNT, X.UNFIXED_CNT, X.INSP_STATE_CODE
			  FROM (SELECT I.INSP_ID, I.INSP_DATE, I.INSP_BY, I.INSP_BY_NAME, I.ACT_AS_ID, I.ACT_AS_NAME, I.SIGN_DATE,
			               SUM(CASE WHEN R.RESULT_CODE = 'EXCPT' THEN 1 ELSE 0 END) AS EXCPT_CNT,
			               SUM(CASE WHEN R.RESULT_CODE = 'EXCPT' AND R.IS_FIXED = 0 THEN 1 ELSE 0 END) AS UNFIXED_CNT,
			""" + "               " + STATE_EXPR + " AS INSP_STATE_CODE\n" + """
			          FROM IM_INSP I
			          JOIN IM_INSP_RESULT R ON R.INSP_ID = I.INSP_ID AND R.STATUS = 1
			         WHERE I.STATUS = 1
			         GROUP BY I.INSP_ID, I.INSP_DATE, I.INSP_BY, I.INSP_BY_NAME, I.ACT_AS_ID, I.ACT_AS_NAME, I.SIGN_DATE) X
			""";

	public static final String FIND_LIST = INSP_SUMMARY + """
			 WHERE (:stateCode IS NULL OR X.INSP_STATE_CODE = :stateCode)
			   AND (:keyword IS NULL
			        OR UPPER(X.INSP_ID) LIKE '%' || UPPER(:keyword) || '%' ESCAPE '\\'
			        OR UPPER(X.INSP_BY_NAME) LIKE '%' || UPPER(:keyword) || '%' ESCAPE '\\'
			        OR UPPER(X.ACT_AS_NAME) LIKE '%' || UPPER(:keyword) || '%' ESCAPE '\\')
			   AND (:dateFrom IS NULL OR X.INSP_DATE >= :dateFrom)
			   AND (:dateToNext IS NULL OR X.INSP_DATE < :dateToNext)
			 ORDER BY X.INSP_DATE DESC, X.INSP_ID DESC
			""";

	public static final String FIND_BY_ID = INSP_SUMMARY + """
			 WHERE X.INSP_ID = :inspId
			""";

	public static final String FIND_RESULTS = """
			SELECT R.INSP_ID, R.INSP_ITEM_ID, R.SECT_NAME_SNAP, R.ITEM_NAME_SNAP, R.RESULT_CODE, R.EXCPT_DESCR, R.IS_FIXED,
			       R.FIX_DESCR, R.FIX_BY, R.FIX_BY_NAME, R.FIX_ACT_AS_ID, R.FIX_ACT_AS_NAME, R.FIX_DATE
			  FROM IM_INSP_RESULT R
			  LEFT JOIN IM_INSP_ITEM T ON T.INSP_ITEM_ID = R.INSP_ITEM_ID
			 WHERE R.INSP_ID = :inspId
			   AND R.STATUS = 1
			 ORDER BY T.SORT_SEQ NULLS LAST, R.INSP_ITEM_ID
			""";

	public static final String UPDATE_FIX = """
			UPDATE IM_INSP_RESULT
			   SET IS_FIXED = 1, FIX_DESCR = :fixDescr, FIX_BY = :fixBy, FIX_BY_NAME = :fixByName,
			       FIX_ACT_AS_ID = :fixActAsId, FIX_ACT_AS_NAME = :fixActAsName, FIX_DATE = :now,
			       UPDATE_DATE = :now, UPDATE_BY = :fixBy
			 WHERE INSP_ID = :inspId
			   AND INSP_ITEM_ID = :inspItemId
			   AND RESULT_CODE = 'EXCPT'
			   AND IS_FIXED = 0
			""";

	private final DbClient dbClient;
	private final String itflowDb;

	public InspDao(DbClient dbClient, @Value("${itflow.db.alias}") String itflowDb) {
		this.dbClient = dbClient;
		this.itflowDb = itflowDb;
	}

	public List<InspItem> findItems() {
		return dbClient.query(itflowDb, FIND_ITEMS, null, InspItem.class);
	}

	public int insertInsp(String inspId, LocalDateTime inspDate, String inspBy, String inspByName, String actAsId,
			String actAsName, LocalDateTime now) {
		Map<String, Object> params = new HashMap<>();
		params.put("inspId", inspId);
		params.put("inspDate", DaoSupport.ts(inspDate));
		params.put("inspBy", inspBy);
		params.put("inspByName", inspByName);
		params.put("actAsId", actAsId);
		params.put("actAsName", actAsName);
		params.put("now", DaoSupport.ts(now));
		return dbClient.update(itflowDb, INSERT_INSP, params);
	}

	public int insertResult(String inspId, InspItem item, String resultCode, String excptDescr, LocalDateTime now,
			String userId) {
		Map<String, Object> params = new HashMap<>();
		params.put("inspId", inspId);
		params.put("inspItemId", item.getInspItemId());
		params.put("itemNameSnap", item.getItemName());
		params.put("sectNameSnap", item.getSectName());
		params.put("resultCode", resultCode);
		params.put("excptDescr", excptDescr);
		params.put("now", DaoSupport.ts(now));
		params.put("userId", userId);
		return dbClient.update(itflowDb, INSERT_RESULT, params);
	}

	/** keyword 已跳脫；dateFrom／dateTo 含頭尾，null 表示不限 */
	public List<Insp> findList(String keyword, String stateCode, LocalDate dateFrom, LocalDate dateTo) {
		Map<String, Object> params = new HashMap<>();
		params.put("keyword", keyword);
		params.put("stateCode", stateCode);
		params.put("dateFrom", DaoSupport.ts(dateFrom));
		params.put("dateToNext", DaoSupport.ts(dateTo == null ? null : dateTo.plusDays(1)));
		return dbClient.query(itflowDb, FIND_LIST, params, Insp.class);
	}

	public InspDetail findById(String inspId) {
		Map<String, Object> params = new HashMap<>();
		params.put("inspId", inspId);
		return DaoSupport.first(dbClient.query(itflowDb, FIND_BY_ID, params, InspDetail.class));
	}

	public List<InspResult> findResults(String inspId) {
		Map<String, Object> params = new HashMap<>();
		params.put("inspId", inspId);
		return dbClient.query(itflowDb, FIND_RESULTS, params, InspResult.class);
	}

	public int fix(String inspId, String inspItemId, String fixDescr, String fixBy, String fixByName, String fixActAsId,
			String fixActAsName, LocalDateTime now) {
		Map<String, Object> params = new HashMap<>();
		params.put("inspId", inspId);
		params.put("inspItemId", inspItemId);
		params.put("fixDescr", fixDescr);
		params.put("fixBy", fixBy);
		params.put("fixByName", fixByName);
		params.put("fixActAsId", fixActAsId);
		params.put("fixActAsName", fixActAsName);
		params.put("now", DaoSupport.ts(now));
		return dbClient.update(itflowDb, UPDATE_FIX, params);
	}
}
