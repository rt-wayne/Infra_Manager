package com.mpx.infra_manager_java.model.template;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：範本列表一列的回應（S5 R1）。canEdit＝登入者是建立者或 admin（第 43 項裁示 A）；
//           時間為 yyyy-MM-dd HH:mm 台灣時間，updatedAt 沒改過時為建立時間
// ============================================================

public record TemplateListItem(String tmplId, String tmplName, String prioCode, String prioName, String prioColor,
		String ownerName, int useCnt, String lastUsedAt, String lastUsedByName, String updatedAt, boolean canEdit) {
}
