package com.mpx.infra_manager_java.config;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：SQL 的 schema 前綴（S2，BACKLOG 第 77 項裁示 A）
//           ap_user 以 rd_user.表名 存取、不建同義詞；識別字不能用 :name 綁定，只能字串串接，
//           因此前綴只允許來自設定檔 db.schema.itflow，啟動時以白名單驗證（英文字母開頭，英數／_／$／#，最長 128），
//           不合法就啟動失敗。這是後端 README §8「SQL 沒有字串拼接」唯一的例外，DAO 一律經 table() 取得完整表名。
// ============================================================

import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DbSchema {

	/** Oracle 不加引號識別字：字母開頭，英數、底線、$、#，最長 128 */
	static final Pattern VALID = Pattern.compile("[A-Za-z][A-Za-z0-9_$#]{0,127}");

	private final String schema;

	public DbSchema(@Value("${db.schema.itflow:}") String schema) {
		String s = schema == null ? "" : schema.trim();
		if (!VALID.matcher(s).matches()) {
			throw new IllegalStateException(
					"db.schema.itflow 未設定或不是合法的 Oracle schema 名稱（英文字母開頭，只能含英數、_、$、#，最長 128）");
		}
		this.schema = s.toUpperCase(Locale.ROOT);
	}

	/** 回傳 SCHEMA.表名；表名由程式常數提供，不得來自請求 */
	public String table(String tableName) {
		return schema + "." + tableName;
	}
}
