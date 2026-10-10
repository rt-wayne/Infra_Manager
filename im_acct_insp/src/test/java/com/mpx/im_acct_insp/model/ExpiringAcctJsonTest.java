package com.mpx.im_acct_insp.model;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：變更單 #1（L4）——/home/expiring 一筆序列化後不含 applyBy（spec v2.0 §6.3）
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class ExpiringAcctJsonTest {

	@Test
	void serialize_excludesApplyBy() {
		ExpiringAcct a = new ExpiringAcct();
		a.setAcctId("A0001");
		a.setAcctName("acct1");
		a.setCatgId("AD");
		a.setExpiryDate(LocalDateTime.of(2026, 10, 10, 0, 0));
		a.setDaysLeft(3L);
		a.setApplyBy("U001");

		String json = JsonMapper.builder().build().writeValueAsString(a);

		assertThat(json).doesNotContain("applyBy").doesNotContain("U001");
		assertThat(json).contains("\"acctId\":\"A0001\"").contains("\"expiryDate\":\"2026-10-10\"");
	}
}
