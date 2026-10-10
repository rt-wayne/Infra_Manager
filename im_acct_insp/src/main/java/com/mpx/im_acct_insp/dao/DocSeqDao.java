package com.mpx.im_acct_insp.dao;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：IM_DOC_SEQ 取號（spec §3.1 樂觀 CAS）：讀目前號、當日第一筆 INSERT、帶 CURR_SEQ 條件的 UPDATE
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（spec_im_acct_insp_v2.0.md §3、§4.1）：CREATE_DATE／UPDATE_DATE 改綁定 :now（Clock 產生），不用 SYSDATE
// ============================================================

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.mpx.common.db.DbClient;
import com.mpx.im_acct_insp.model.DocSeq;

@Repository
public class DocSeqDao {

	public static final String FIND_CURR_SEQ = """
			SELECT CURR_SEQ
			  FROM IM_DOC_SEQ
			 WHERE DOC_TYPE_ID = :docTypeId
			   AND SEQ_DATE = :seqDate
			""";

	public static final String INSERT_FIRST = """
			INSERT INTO IM_DOC_SEQ (DOC_TYPE_ID, SEQ_DATE, CURR_SEQ, CREATE_DATE, CREATE_BY)
			VALUES (:docTypeId, :seqDate, 1, :now, :userId)
			""";

	public static final String UPDATE_CAS = """
			UPDATE IM_DOC_SEQ
			   SET CURR_SEQ = :nextSeq, UPDATE_DATE = :now, UPDATE_BY = :userId
			 WHERE DOC_TYPE_ID = :docTypeId
			   AND SEQ_DATE = :seqDate
			   AND CURR_SEQ = :currSeq
			""";

	private final DbClient dbClient;
	private final String itflowDb;

	public DocSeqDao(DbClient dbClient, @Value("${db.connect.itflow}") String itflowDb) {
		this.dbClient = dbClient;
		this.itflowDb = itflowDb;
	}

	/** 查無當日資料回 null */
	public Integer findCurrSeq(String docTypeId, String seqDate) {
		Map<String, Object> params = new HashMap<>();
		params.put("docTypeId", docTypeId);
		params.put("seqDate", seqDate);
		List<DocSeq> rows = dbClient.query(itflowDb, FIND_CURR_SEQ, params, DocSeq.class);
		return rows.isEmpty() ? null : rows.get(0).getCurrSeq();
	}

	/** 當日第一筆；PK 衝突時丟 Spring DataIntegrityViolationException（DuplicateKeyException） */
	public int insertFirst(String docTypeId, String seqDate, String userId, LocalDateTime now) {
		Map<String, Object> params = new HashMap<>();
		params.put("docTypeId", docTypeId);
		params.put("seqDate", seqDate);
		params.put("userId", userId);
		params.put("now", DaoSupport.ts(now));
		return dbClient.update(itflowDb, INSERT_FIRST, params);
	}

	public int updateCas(String docTypeId, String seqDate, int currSeq, int nextSeq, String userId, LocalDateTime now) {
		Map<String, Object> params = new HashMap<>();
		params.put("docTypeId", docTypeId);
		params.put("seqDate", seqDate);
		params.put("currSeq", currSeq);
		params.put("nextSeq", nextSeq);
		params.put("userId", userId);
		params.put("now", DaoSupport.ts(now));
		return dbClient.update(itflowDb, UPDATE_CAS, params);
	}
}
