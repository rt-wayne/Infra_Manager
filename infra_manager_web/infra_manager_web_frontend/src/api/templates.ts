// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：範本 API（S5 R2），經殼 jar 轉發到後端 /api/templates
//           修改／刪除限建立者或 admin（403 訊息由後端帶）；找不到 404；欄位錯誤 400（可能帶 field）；401 交給 http 的處理器
// ============================================================
import http from './http'
import type { TemplateDetail, TemplateListItem, TemplateRequest, TemplateSaved } from '../types/template'

/* GET /templates 範本列表（名稱排序，只有未刪除的） */
export function listTemplates(): Promise<TemplateListItem[]> {
  return http.get<TemplateListItem[]>('/templates').then(r => r.data)
}

/* GET /templates/{id} 單筆含表單內容 */
export function getTemplate(tmplId: string): Promise<TemplateDetail> {
  return http.get<TemplateDetail>('/templates/' + encodeURIComponent(tmplId)).then(r => r.data)
}

/* POST /templates 新增 → 201 { tmplId } */
export function createTemplate(body: TemplateRequest): Promise<TemplateSaved> {
  return http.post<TemplateSaved>('/templates', body).then(r => r.data)
}

/* PUT /templates/{id} 修改（建立者或 admin）→ 200 { tmplId } */
export function updateTemplate(tmplId: string, body: TemplateRequest): Promise<TemplateSaved> {
  return http.put<TemplateSaved>('/templates/' + encodeURIComponent(tmplId), body).then(r => r.data)
}

/* DELETE /templates/{id} 軟刪除（建立者或 admin）→ 204 */
export function deleteTemplate(tmplId: string): Promise<void> {
  return http.delete('/templates/' + encodeURIComponent(tmplId)).then(() => undefined)
}
