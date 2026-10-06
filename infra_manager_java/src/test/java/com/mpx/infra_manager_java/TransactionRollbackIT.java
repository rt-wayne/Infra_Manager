package com.mpx.infra_manager_java;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：交易回滾整合測試（code review 第 3 項，裁示 ③B）。*IT 只由 mvnw verify 執行。
//           連真實公司測試 Oracle；host.properties 的 API 位址為空、或測試表 IM_TX_TEST 尚未建立
//           （db/oracle/V2__tx_test_table.sql）時略過。
//           驗證：@Transactional 範圍（TransactionTemplate）內用 DbClient 寫兩筆後丟例外 → 0 筆；正常結束 → 2 筆。
//           寫入的資料以隨機 TEST_KEY 區分，測試結束一律刪除。表名以 ALL_TABLES 查到的 OWNER 加 schema 前綴存取（不建同義詞）。
//           2026-10-06 複審裁示 ①A：schema 前綴是識別字、Oracle 不能用 :name 綁定，是 README「SQL 不拼接」的唯一例外；
//           OWNER 必須通過白名單（大寫英數與 _ $ #）才串進 SQL，值一律仍走具名參數。
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.mpx.common.db.DbClient;
import com.mpx.infra_manager_java.model.DualRow;

@SpringBootTest
class TransactionRollbackIT {

	/** ALL_TABLES 查表擁有者用 */
	public static class OwnerRow {
		private String owner;

		public String getOwner() {
			return owner;
		}

		public void setOwner(String owner) {
			this.owner = owner;
		}
	}

	/** 允許串進 SQL 的 schema 名：Oracle 未加引號的識別字（大寫開頭、英數與 _ $ #，最長 128） */
	static final String OWNER_PATTERN = "[A-Z][A-Z0-9_$#]{0,127}";

	@Autowired
	private DbClient dbClient;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Value("${db.connect.api.domain.path:}")
	private String apiUrl;

	@Value("${db.connect.itflow}")
	private String itflowDb;

	private String table;
	private String testKey;

	@BeforeEach
	void setUp() {
		assumeTrue(apiUrl != null && !apiUrl.isBlank(), "host.properties 未設定連線資訊 API，略過整合測試");
		List<OwnerRow> owners = dbClient.query(itflowDb,
				"SELECT OWNER FROM ALL_TABLES WHERE TABLE_NAME = 'IM_TX_TEST' ORDER BY OWNER", null, OwnerRow.class);
		assumeTrue(!owners.isEmpty(), "測試表 IM_TX_TEST 尚未建立（db/oracle/V2__tx_test_table.sql），略過");
		String owner = owners.get(0).getOwner();
		if (owner == null || !owner.matches(OWNER_PATTERN)) {
			throw new IllegalStateException("IM_TX_TEST 的 OWNER 不是合法 Oracle 識別字，拒絕串進 SQL");
		}
		table = owner + ".IM_TX_TEST";
		testKey = UUID.randomUUID().toString();
	}

	@AfterEach
	void cleanUp() {
		if (table != null && testKey != null) {
			dbClient.update(itflowDb, "DELETE FROM " + table + " WHERE TEST_KEY = :key", Map.of("key", testKey));
		}
	}

	@Test
	void 交易內丟例外時兩筆都回滾() {
		TransactionTemplate tx = new TransactionTemplate(transactionManager);

		assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
			insert("第一筆");
			insert("第二筆");
			throw new IllegalStateException("故意回滾");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(count()).isZero();
	}

	@Test
	void 交易正常結束時兩筆都寫入() {
		TransactionTemplate tx = new TransactionTemplate(transactionManager);

		tx.executeWithoutResult(status -> {
			insert("第一筆");
			insert("第二筆");
		});

		assertThat(count()).isEqualTo(2);
	}

	private void insert(String memo) {
		dbClient.update(itflowDb,
				"INSERT INTO " + table + " (TEST_KEY, MEMO, CREATE_BY) VALUES (:key, :memo, 'TEST')",
				Map.of("key", testKey, "memo", memo));
	}

	private int count() {
		List<DualRow> rows = dbClient.query(itflowDb,
				"SELECT COUNT(*) AS OK FROM " + table + " WHERE TEST_KEY = :key", Map.of("key", testKey), DualRow.class);
		return rows.isEmpty() || rows.get(0).getOk() == null ? 0 : rows.get(0).getOk();
	}
}
