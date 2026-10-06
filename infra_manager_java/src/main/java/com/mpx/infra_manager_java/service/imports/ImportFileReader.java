package com.mpx.infra_manager_java.service.imports;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：讀舊系統 users.json 與帳號工號對照 CSV（S2 回合二匯入器）。
//           users.json：{ "users": [ ... ] }，未知欄位忽略；解析失敗的例外不夾帶檔案原文。
//           對照 CSV：UTF-8，第一列表頭固定 login_id,user_id，之後每列兩欄、逗號分隔、不支援引號；
//           允許 Excel 存檔的 BOM 與空白列；帳號轉小寫；帳號或工號重複、欄位空白、欄數不對都整檔失敗
// ============================================================

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.mpx.infra_manager_java.model.imports.ImportMapping;
import com.mpx.infra_manager_java.model.imports.LegacyUser;
import com.mpx.infra_manager_java.model.imports.LegacyUserFile;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ImportFileReader {

	static final String MAPPING_HEADER = "login_id,user_id";

	private final JsonMapper mapper = JsonMapper.builder()
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
			.build();

	public List<LegacyUser> readUsers(Path file) {
		String json = readText(file, "users.json");
		LegacyUserFile parsed;
		try {
			parsed = mapper.readValue(json, LegacyUserFile.class);
		} catch (JacksonException e) {
			throw new UserImportException("users.json 解析失敗：" + e.getOriginalMessage());
		}
		if (parsed == null || parsed.users() == null) {
			throw new UserImportException("users.json 沒有 users 陣列");
		}
		return parsed.users();
	}

	public ImportMapping readMapping(Path file) {
		List<String> lines;
		try {
			lines = Files.readAllLines(file, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UserImportException("對照表讀取失敗：" + file);
		}
		Map<String, String> mapping = new HashMap<>();
		Set<String> userIds = new HashSet<>();
		List<String> problems = new ArrayList<>();
		boolean headerSeen = false;
		for (int i = 0; i < lines.size(); i++) {
			String line = stripBom(lines.get(i)).trim();
			int lineNo = i + 1;
			if (line.isEmpty()) {
				continue;
			}
			if (!headerSeen) {
				if (!MAPPING_HEADER.equals(line.replace(" ", "").toLowerCase(Locale.ROOT))) {
					throw new UserImportException("對照表第 1 列表頭必須是 " + MAPPING_HEADER);
				}
				headerSeen = true;
				continue;
			}
			String[] fields = line.split(",", -1);
			if (fields.length != 2) {
				problems.add("對照表第 " + lineNo + " 列應有 2 欄，實際 " + fields.length + " 欄");
				continue;
			}
			String loginId = fields[0].trim().toLowerCase(Locale.ROOT);
			String userId = fields[1].trim();
			if (loginId.isEmpty() || userId.isEmpty()) {
				problems.add("對照表第 " + lineNo + " 列帳號或工號空白");
				continue;
			}
			if (mapping.containsKey(loginId)) {
				problems.add("對照表第 " + lineNo + " 列帳號重複：" + loginId);
				continue;
			}
			if (!userIds.add(userId)) {
				problems.add("對照表第 " + lineNo + " 列工號重複：" + userId);
				continue;
			}
			mapping.put(loginId, userId);
		}
		if (!headerSeen) {
			throw new UserImportException("對照表是空檔，缺表頭 " + MAPPING_HEADER);
		}
		if (!problems.isEmpty()) {
			throw new UserImportException(problems);
		}
		return new ImportMapping(mapping);
	}

	private static String readText(Path file, String label) {
		try {
			return stripBom(Files.readString(file, StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new UserImportException(label + " 讀取失敗：" + file);
		}
	}

	private static String stripBom(String s) {
		return s.startsWith("﻿") ? s.substring(1) : s;
	}
}
