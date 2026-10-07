// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-06
// 變更說明: 新增：申請單 API（S4 回合二），經殼 jar 轉發到後端 /api/apps
//           listApps：空字串與 false 的篩選不送（後端沒帶 from／to 時預設近 90 天）；400 帶訊息、401 交給 http 的處理器
//           S4 回合三：加 getApp（檢視頁）
//           S6 回合一-2（2026-10-07）：加 attachmentUrl／checkAttachment（附件下載：HEAD 確認後交給瀏覽器下載）
// ============================================================
import http from './http'
import type { AppDetail, AppListFilter, AppListResponse } from '../types/app'

export function toListParams(filter: AppListFilter, page: number): Record<string, string> {
  const p: Record<string, string> = {}
  if (filter.status) p.status = filter.status
  if (filter.priority) p.priority = filter.priority
  if (filter.source) p.source = filter.source
  if (filter.mine) p.mine = 'true'
  const q = filter.q.trim()
  if (q) p.q = q
  if (filter.from) p.from = filter.from
  if (filter.to) p.to = filter.to
  if (page > 1) p.page = String(page)
  return p
}

/* GET /apps */
export function listApps(filter: AppListFilter, page: number): Promise<AppListResponse> {
  return http.get<AppListResponse>('/apps', { params: toListParams(filter, page) }).then(r => r.data)
}

/* GET /apps/{id}；單號格式由後端檢查（不符或查無 404「找不到申請單」） */
export function getApp(appId: string): Promise<AppDetail> {
  return http.get<AppDetail>('/apps/' + encodeURIComponent(appId)).then(r => r.data)
}

function attachmentPath(appId: string, attachId: number): string {
  return '/apps/' + encodeURIComponent(appId) + '/attachments/' + attachId
}

/* 給 <a> 用的完整網址：瀏覽器自己下載、邊收邊寫檔，大檔不進記憶體 */
export function attachmentUrl(appId: string, attachId: number): string {
  return (http.defaults.baseURL ?? '') + attachmentPath(appId, attachId)
}

/* HEAD 先確認附件還在：直接開下載連結失敗時頁面拿不到原因，只會在瀏覽器下載列顯示失敗 */
export function checkAttachment(appId: string, attachId: number): Promise<void> {
  return http.head(attachmentPath(appId, attachId)).then(() => undefined)
}
