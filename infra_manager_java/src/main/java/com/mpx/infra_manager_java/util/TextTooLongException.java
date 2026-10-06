package com.mpx.infra_manager_java.util;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-05
// 變更說明: 新增：文字欄位超過字數上限（S1）；由 ApiExceptionHandler 轉成 400
//           2026-10-06 code review：field 為 API 的 JSON 欄位名（camelCase，前端用來標示哪一欄），訊息用中文標籤組；
//           不帶 DB 欄位名，避免回應洩漏 schema、前端 toast 出現英文欄名。
// ============================================================

public class TextTooLongException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final String field;
	private final String label;
	private final int max;
	private final int actual;

	public TextTooLongException(String field, String label, int max, int actual) {
		super("「" + label + "」超過 " + max + " 字（目前 " + actual + " 字）");
		this.field = field;
		this.label = label;
		this.max = max;
		this.actual = actual;
	}

	/** API 的 JSON 欄位名（camelCase） */
	public String getField() {
		return field;
	}

	/** 畫面上的中文欄位名 */
	public String getLabel() {
		return label;
	}

	public int getMax() {
		return max;
	}

	public int getActual() {
		return actual;
	}
}
