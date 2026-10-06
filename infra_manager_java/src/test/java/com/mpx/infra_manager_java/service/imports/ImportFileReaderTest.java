package com.mpx.infra_manager_java.service.imports;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：users.json 與對照 CSV 讀取測試（S2 回合二匯入器）。
//           用 @TempDir 寫暫存檔；驗證 BOM、空白列、表頭檢查、欄數、重複、未知 JSON 欄位忽略、檔案不存在與 JSON 壞掉的錯誤訊息
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mpx.infra_manager_java.model.imports.ImportMapping;
import com.mpx.infra_manager_java.model.imports.LegacyUser;

class ImportFileReaderTest {

	private final ImportFileReader reader = new ImportFileReader();

	@TempDir
	Path dir;

	@Test
	void 讀users_json忽略未知欄位() throws IOException {
		Path file = write("users.json", """
				{ "users": [ { "id": "gary", "name": "Gary", "email": "g@x", "title": "協理", "department": "資訊部",
				  "phone": "", "roles": ["admin", "it_manager"], "active": true,
				  "passwordHash": "scrypt$abc", "passwordIsDefault": false, "rememberTokens": [ { "hash": "h" } ],
				  "passwordChangedAt": "2026-05-12T09:43:08.757Z" },
				  { "id": "alex", "name": "Alex" } ] }
				""");

		List<LegacyUser> users = reader.readUsers(file);

		assertThat(users).hasSize(2);
		assertThat(users.get(0).id()).isEqualTo("gary");
		assertThat(users.get(0).roles()).containsExactly("admin", "it_manager");
		assertThat(users.get(0).active()).isTrue();
		assertThat(users.get(1).roles()).isNull();
		assertThat(users.get(1).active()).isNull();
	}

	@Test
	void users_json帶BOM也能讀() throws IOException {
		Path file = write("users.json", "﻿{ \"users\": [] }");

		assertThat(reader.readUsers(file)).isEmpty();
	}

	@Test
	void users_json沒有users陣列或格式壞掉或檔案不存在都丟UserImportException() throws IOException {
		Path noUsers = write("a.json", "{ \"people\": [] }");
		Path broken = write("b.json", "{ \"users\": [ { \"id\": ");

		assertThatThrownBy(() -> reader.readUsers(noUsers)).isInstanceOf(UserImportException.class)
				.hasMessageContaining("沒有 users 陣列");
		assertThatThrownBy(() -> reader.readUsers(broken)).isInstanceOf(UserImportException.class)
				.hasMessageContaining("解析失敗");
		assertThatThrownBy(() -> reader.readUsers(dir.resolve("missing.json"))).isInstanceOf(UserImportException.class)
				.hasMessageContaining("讀取失敗");
	}

	@Test
	void 讀對照表_表頭不分大小寫_帳號轉小寫_允許BOM與空白列() throws IOException {
		Path file = write("map.csv", "﻿Login_ID, User_ID\r\n\r\n Wayne , T0001 \r\nAlan Kuo,A002\r\n\r\n");

		ImportMapping mapping = reader.readMapping(file);

		assertThat(mapping.size()).isEqualTo(2);
		assertThat(mapping.userIdOf("wayne")).contains("T0001");
		assertThat(mapping.userIdOf("alan kuo")).contains("A002");
		assertThat(mapping.userIdOf("Wayne")).isEmpty();
	}

	@Test
	void 對照表表頭錯或空檔整檔失敗() throws IOException {
		Path badHeader = write("a.csv", "account,emp_no\nwayne,T0001\n");
		Path empty = write("b.csv", "\n\n");

		assertThatThrownBy(() -> reader.readMapping(badHeader)).isInstanceOf(UserImportException.class)
				.hasMessageContaining("表頭必須是 login_id,user_id");
		assertThatThrownBy(() -> reader.readMapping(empty)).isInstanceOf(UserImportException.class)
				.hasMessageContaining("空檔");
		assertThatThrownBy(() -> reader.readMapping(dir.resolve("missing.csv"))).isInstanceOf(UserImportException.class)
				.hasMessageContaining("讀取失敗");
	}

	@Test
	void 對照表欄數不對_空白_帳號重複_工號重複一次全列() throws IOException {
		Path file = write("a.csv", "login_id,user_id\nwayne,T0001\nonlyone\n,A003\nWAYNE,A004\nbob,T0001\n");

		assertThatThrownBy(() -> reader.readMapping(file)).isInstanceOf(UserImportException.class)
				.satisfies(e -> assertThat(((UserImportException) e).getProblems()).containsExactly(
						"對照表第 3 列應有 2 欄，實際 1 欄",
						"對照表第 4 列帳號或工號空白",
						"對照表第 5 列帳號重複：wayne",
						"對照表第 6 列工號重複：T0001"));
	}

	private Path write(String name, String content) throws IOException {
		Path file = dir.resolve(name);
		Files.writeString(file, content, StandardCharsets.UTF_8);
		return file;
	}
}
