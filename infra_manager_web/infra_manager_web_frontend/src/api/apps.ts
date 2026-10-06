// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-06
// 變更說明: 新增：申請單 API（S4 回合二），經殼 jar 轉發到後端 /api/apps
//           listApps：空字串與 false 的篩選不送（後端沒帶 from／to 時預設近 90 天）；400 帶訊息、401 交給 http 的處理器
// ============================================================
import http from './http'
import type { AppListFilter, AppListResponse } from '../types/app'

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
