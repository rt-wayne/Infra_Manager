package com.mpx.infra_manager_java.model.template;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：單一範本的回應（S5 R1），比列表多 form；canEdit 規則同 TemplateListItem
// ============================================================

public record TemplateDetail(String tmplId, String tmplName, TemplateForm form, String ownerName, int useCnt,
		String lastUsedAt, String lastUsedByName, String createdAt, String updatedAt, boolean canEdit) {
}
