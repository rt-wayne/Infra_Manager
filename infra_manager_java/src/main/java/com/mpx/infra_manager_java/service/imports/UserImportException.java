package com.mpx.infra_manager_java.service.imports;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：使用者匯入失敗（S2 回合二匯入器）。
//           檔案讀不到、格式不對、資料檢核不過（未知角色、欄位過長、帳號被別的工號佔用⋯⋯）都用這個例外，
//           problems 把同一輪找到的問題全部列出，操作者一次修完再重跑；RuntimeException 讓 @Transactional 整批回滾
// ============================================================

import java.util.List;

public class UserImportException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final transient List<String> problems;

	public UserImportException(String problem) {
		this(List.of(problem));
	}

	public UserImportException(List<String> problems) {
		super(String.join("；", problems));
		this.problems = List.copyOf(problems);
	}

	public List<String> getProblems() {
		return problems;
	}
}
