// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-05
// 變更說明: 新增：健康檢查 API（S1），對應殼 jar 的 HealthController；失敗時 Promise reject，由呼叫端提示
// ============================================================
import http from './http'
import type { HealthStatus } from '../types/health'

/* GET /health */
export function getHealth(): Promise<HealthStatus> {
  return http.get<HealthStatus>('/health').then(r => r.data)
}
