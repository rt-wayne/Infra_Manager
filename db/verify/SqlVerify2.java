import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;

/**
 * 一次性驗證工具（BACKLOG 第 6 項回合二複審）：SETUP 第 6、7 節本次新增／修改的語句以測試值代入，
 * 關 autocommit、單一交易內逐句執行並記錄影響筆數，最後 ROLLBACK，
 * 再以唯讀查詢確認測試 DB 無殘留。帳密從環境變數 ORA_URL／ORA_USER／ORA_PWD 讀，不寫死。
 * 測試值：假工號 0000099、假 token 雜湊 VERIFYTMP0000099、資產分類 H01（設回現值）。
 * 寫入階段出錯會接住、照樣 ROLLBACK、跑殘留檢查並寫結果檔；連線或查詢階段出錯則直接拋出、
 * 不產生結果檔（開頭已先刪掉舊結果檔，所以 cat 不會誤印上一次的結果）。
 * 第 6 項結案時連同本資料夾一起刪除。
 *
 * 執行（Git Bash，在 repo 根目錄；驅動與專案 pom.xml 同版）。先單獨執行這行、輸入密碼：
 *   IFS= read -rs -p "密碼: " ORA_PWD; export ORA_PWD; echo
 * 再貼下面幾行：
 *   export ORA_URL="jdbc:oracle:thin:@【主機】:【port】:【SID】" ORA_USER=【帳號】
 *   M2="$USERPROFILE/.m2/repository/com/oracle/database"
 *   "$JAVA_HOME/bin/java" -cp "$M2/jdbc/ojdbc17/23.26.3.0.0/ojdbc17-23.26.3.0.0.jar;$M2/nls/orai18n/23.26.3.0.0/orai18n-23.26.3.0.0.jar" \
 *     db/verify/SqlVerify2.java db/verify/verify_result.txt
 *   unset ORA_PWD; cat db/verify/verify_result.txt
 */
public class SqlVerify2 {
	public static void main(String[] args) throws Exception {
		new File(args[0]).delete(); // 先刪舊結果檔，確保中途失敗時不會誤讀上一次的結果
		Class.forName("oracle.jdbc.OracleDriver");
		StringBuilder out = new StringBuilder("=== SETUP 第 6、7 節新增 SQL 驗證（測試值、ROLLBACK） ===\n");
		boolean ok = true;
		try (Connection c = DriverManager.getConnection(
				System.getenv("ORA_URL"), System.getenv("ORA_USER"), System.getenv("ORA_PWD"))) {
			c.setAutoCommit(false);
			out.append("DB 字元集：").append(text(c,
				"SELECT VALUE FROM NLS_DATABASE_PARAMETERS WHERE PARAMETER = 'NLS_CHARACTERSET'")).append('\n');
			// 測試工號已存在時不執行寫入，避免主鍵衝突或誤動到真實資料
			if (scalar(c, "SELECT COUNT(*) FROM IM_ACCT_USER WHERE USER_ID = '0000099'") > 0) {
				out.append("【中止】測試 DB 已有工號 0000099，未執行任何寫入\n");
				write(args[0], out);
				return;
			}
			int h01Before = scalar(c, "SELECT IS_SELECTABLE FROM IM_ASSET_CLASS WHERE ASSET_CLASS_ID = 'H01'");
			out.append("H01 IS_SELECTABLE 現值：").append(h01Before).append('\n');
			try {
				// 第 1 段（含 TEL_NO）
				ok &= upd(c, out, "1 新增使用者（含 TEL_NO）", 1,
					"INSERT INTO IM_ACCT_USER (USER_ID, USER_EN_NAME, USER_NAME, DEPT_NAME, EMAIL, TEL_NO, " +
					"PWD_HASH, PWD_UPDATE_DATE, STATUS, CREATE_DATE, CREATE_BY) " +
					"VALUES ('0000099', 'VerifyTmp', '驗證暫用', '測試', 'v@example.com', '#0000', " +
					"'PBKDF2$TEST', SYSDATE, 1, SYSDATE, '0000099')");
				// 第 4 段外洩處理：先造兩筆未登出、一筆已登出的 token，標登出應影響 2 筆
				for (String[] t : new String[][] {{"VERIFYTMP0000099A", "NULL"}, {"VERIFYTMP0000099B", "NULL"},
						{"VERIFYTMP0000099C", "SYSDATE - 1"}}) {
					upd(c, out, "  （造測試 token " + t[0].substring(16) + "）", 1,
						"INSERT INTO IM_ACCT_LOGIN_TOKEN (TOKEN_HASH, USER_ID, EXPIRE_DATE, LAST_ACCESS_DATE, " +
						"LOGOUT_DATE, CREATE_BY) VALUES ('" + t[0] + "', '0000099', SYSDATE + 1/3, SYSDATE, " +
						t[1] + ", '0000099')");
				}
				ok &= upd(c, out, "4 外洩：token 全標登出（應 2 筆）", 2,
					"UPDATE IM_ACCT_LOGIN_TOKEN SET LOGOUT_DATE = SYSDATE, UPDATE_DATE = SYSDATE, " +
					"UPDATE_BY = '0000099' WHERE USER_ID = '0000099' AND LOGOUT_DATE IS NULL");
				ok &= upd(c, out, "4 外洩：再跑一次（應 0 筆）", 0,
					"UPDATE IM_ACCT_LOGIN_TOKEN SET LOGOUT_DATE = SYSDATE, UPDATE_DATE = SYSDATE, " +
					"UPDATE_BY = '0000099' WHERE USER_ID = '0000099' AND LOGOUT_DATE IS NULL");
				// 第 9 段資產分類（設回現值）
				ok &= upd(c, out, "9 資產分類可否選用", 1,
					"UPDATE IM_ASSET_CLASS SET IS_SELECTABLE = " + h01Before + ", UPDATE_DATE = SYSDATE, " +
					"UPDATE_BY = '0000099' WHERE ASSET_CLASS_ID = 'H01'");
				// 查代碼新增兩句、第 7 節通知紀錄查詢
				ok &= rows(c, out, "查巡檢項目（應 14 列）",
					"SELECT INSP_ITEM_ID, SECT_NAME, ITEM_NAME, STATUS FROM IM_INSP_ITEM ORDER BY SORT_SEQ", 14);
				ok &= rows(c, out, "查資產分類（應 15 列）",
					"SELECT ASSET_CLASS_ID, ASSET_CLASS_NAME, IS_SELECTABLE FROM IM_ASSET_CLASS WHERE STATUS = 1 ORDER BY SORT_SEQ", 15);
				ok &= rows(c, out, "查週彙總通知紀錄（語法驗證，列數不限）",
					"SELECT RCPT_USER_ID, RCPT_EMAIL, SEND_DATE, RESULT_CODE, ERR_MSG FROM IM_ACCT_NOTIFY_LOG " +
					"WHERE EVENT_CODE = 'WEEKLY_SUMMARY' AND REF_ID = '20261005'", -1);
			} catch (SQLException e) {
				ok = false;
				out.append("【執行中斷】").append(e.getMessage()).append('\n');
			} finally {
				c.rollback();
				out.append("已 ROLLBACK\n");
			}
			out.append("\n=== 殘留檢查（應全部符合） ===\n");
			ok &= check(c, out, "使用者 0000099 筆數（應 0）",
				"SELECT COUNT(*) FROM IM_ACCT_USER WHERE USER_ID = '0000099'", 0);
			ok &= check(c, out, "token 0000099 筆數（應 0）",
				"SELECT COUNT(*) FROM IM_ACCT_LOGIN_TOKEN WHERE USER_ID = '0000099'", 0);
			ok &= check(c, out, "H01 IS_SELECTABLE（應＝現值 " + h01Before + "）",
				"SELECT IS_SELECTABLE FROM IM_ASSET_CLASS WHERE ASSET_CLASS_ID = 'H01'", h01Before);
			ok &= check(c, out, "H01 UPDATE_BY 不是 0000099（應 0）",
				"SELECT COUNT(*) FROM IM_ASSET_CLASS WHERE ASSET_CLASS_ID = 'H01' AND UPDATE_BY = '0000099'", 0);
		}
		out.append(ok ? "\n=== 結果：全部符合預期、已回滾、零殘留 ===\n" : "\n=== 結果：有異常，見上 ===\n");
		write(args[0], out);
	}

	static void write(String path, StringBuilder out) throws IOException {
		try (Writer w = new OutputStreamWriter(new FileOutputStream(path), StandardCharsets.UTF_8)) {
			w.write(out.toString());
		}
	}

	static boolean upd(Connection c, StringBuilder out, String label, int expect, String sql) throws SQLException {
		try (Statement st = c.createStatement()) {
			int n = st.executeUpdate(sql);
			out.append(String.format("%s：影響 %d 筆 %s%n", label, n, n == expect ? "OK" : "【預期 " + expect + " 筆，異常】"));
			return n == expect;
		}
	}

	static boolean rows(Connection c, StringBuilder out, String label, String sql, int expect) throws SQLException {
		int n = 0;
		try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
			while (rs.next()) n++;
		}
		boolean pass = expect < 0 || n == expect;
		out.append(String.format("%s：%d 列 %s%n", label, n, pass ? "OK" : "【異常】"));
		return pass;
	}

	static int scalar(Connection c, String sql) throws SQLException {
		try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
			rs.next();
			return rs.getInt(1);
		}
	}

	static String text(Connection c, String sql) throws SQLException {
		try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
			return rs.next() ? rs.getString(1) : "（查無）";
		}
	}

	static boolean check(Connection c, StringBuilder out, String label, String sql, int expect) throws SQLException {
		int v = scalar(c, sql);
		out.append(String.format("%s：%d %s%n", label, v, v == expect ? "OK" : "【異常】"));
		return v == expect;
	}
}
