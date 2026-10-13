// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: 新增：緊急存取 API（BACKLOG 第 46 項 S3；PRD API 表「緊急存取」）；查詢條件空值不送
// ============================================================
import http from './http'
import type { Emg, EmgDetail, EmgIdResult, EmgPayload, EmgQuery } from '../types/emergency'

/** 送出緊急存取：POST /emergencies（需 EMG_APPLY） */
export function submitEmergency(payload: EmgPayload): Promise<EmgIdResult> {
  return http.post<EmgIdResult>('/emergencies', payload).then(r => r.data)
}

/** 緊急存取清單：GET /emergencies?keyword&stateCode&dateFrom&dateTo（無 EMG_VIEW_ALL 後端只回本人的單） */
export function listEmergencies(q: EmgQuery): Promise<Emg[]> {
  const params: EmgQuery = {}
  if (q.keyword) params.keyword = q.keyword
  if (q.stateCode) params.stateCode = q.stateCode
  if (q.dateFrom) params.dateFrom = q.dateFrom
  if (q.dateTo) params.dateTo = q.dateTo
  return http.get<Emg[]>('/emergencies', { params }).then(r => r.data)
}

/** 緊急存取明細：GET /emergencies/{id}（查無或無權看 404） */
export function getEmergency(emgId: string): Promise<EmgDetail> {
  return http.get<EmgDetail>(`/emergencies/${encodeURIComponent(emgId)}`).then(r => r.data)
}
