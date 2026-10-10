package com.mpx.im_acct_insp.tools;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：密碼雜湊產生工具（spec v2.0 §9.2）
//           從標準輸入讀一行密碼（BufferedReader 讀 System.in，可被管線呼叫；不用 System.console()）
//           〔此句已由變更單 #1 取代：有主控台時改用 Console.readPassword，見下方變更單 #1〕，
//           輸出 PasswordHasher 格式的雜湊字串到標準輸出；不接受命令列參數、不印密碼、不寫檔、不記 log。
//           執行：建置後 "%JAVA_HOME%\bin\java" -cp target\classes com.mpx.im_acct_insp.tools.PwdHashTool
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 變更單 #1（M2）：有主控台（System.console() != null 且 isTerminal()）時改用 Console.readPassword，輸入不回顯；
//           無主控台（管線呼叫、測試環境）時維持 BufferedReader 讀標準輸入。
//           （JDK 22 起 stdin 被導向時 System.console() 仍可能非 null，故加 isTerminal() 判斷）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 變更單 #2：僅於檔頭標示「不用 System.console()」的舊說明已由變更單 #1 取代（程式未改）
// ============================================================

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.mpx.im_acct_insp.common.PasswordHasher;

public final class PwdHashTool {

	/** 單行密碼長度上限 */
	static final int MAX_LEN = 256;

	private PwdHashTool() {
	}

	public static void main(String[] args) throws IOException {
		if (args.length > 0) {
			System.err.println("不接受命令列參數；請由標準輸入提供密碼（一行）。");
			System.exit(2);
			return;
		}
		Console console = System.console();
		char[] pwd;
		if (console != null && console.isTerminal()) {
			pwd = readFromConsole(console);
		} else {
			BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
			pwd = readLine(in);
		}
		if (pwd == null || pwd.length == 0) {
			System.err.println("未讀到密碼（標準輸入為空或超過 " + MAX_LEN + " 字元）。");
			System.exit(1);
			return;
		}
		try {
			System.out.println(new PasswordHasher().hash(pwd));
		} finally {
			Arrays.fill(pwd, '\0');
		}
	}

	/**
	 * 互動式讀密碼（不回顯）；EOF 回 null，超過 MAX_LEN 清除後回 null。
	 */
	static char[] readFromConsole(Console console) {
		char[] pwd = console.readPassword("請輸入密碼（不回顯）：");
		if (pwd == null) {
			return null;
		}
		if (pwd.length > MAX_LEN) {
			Arrays.fill(pwd, '\0');
			return null;
		}
		return pwd;
	}

	/**
	 * 讀一行到 char[]（不經 String，用完可清除）；去掉行尾 CR／LF，其餘字元（含空白）原樣保留。
	 * 超過 MAX_LEN 回 null。
	 */
	static char[] readLine(BufferedReader in) throws IOException {
		char[] buf = new char[MAX_LEN + 1];
		int len = 0;
		int c;
		while ((c = in.read()) != -1 && c != '\n') {
			if (len > MAX_LEN) {
				Arrays.fill(buf, '\0');
				return null;
			}
			buf[len++] = (char) c;
		}
		if (len > 0 && buf[len - 1] == '\r') {
			len--;
		}
		if (len > MAX_LEN) {
			Arrays.fill(buf, '\0');
			return null;
		}
		char[] result = Arrays.copyOf(buf, len);
		Arrays.fill(buf, '\0');
		return result;
	}
}
