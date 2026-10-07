package com.mpx.infra_manager_java.model.template;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：新增／修改範本的請求本文（S5 R1）。ID、建立者、套用次數由伺服器決定，不收
// ============================================================

public record TemplateRequest(String tmplName, TemplateForm form) {
}
