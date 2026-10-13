// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: 新增：緊急存取 API（BACKLOG 第 46 項 S3；PRD API 表「緊急存取」）；查詢條件空值不送
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S5：加回報 reportEmergency、補核准 approveEmergency、退回 returnEmergency（都帶 versionNo 樂觀鎖）
// ============================================================
import http from './http'
import type { Emg, EmgDetail, EmgIdResult, EmgPayload, EmgQuery, EmgReportPayload, EmgVersionResult } from '../types/emergency'

function base(emgId: string): string {
  return `/emergencies/${encodeURIComponent(emgId)}`
}

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
  return http.get<EmgDetail>(base(emgId)).then(r => r.data)
}

/** 申請人回報：POST /emergencies/{id}/report（本人即可；409 E08＝已回報、已處理或版本不符） */
export function reportEmergency(emgId: string, payload: EmgReportPayload): Promise<EmgVersionResult> {
  return http.post<EmgVersionResult>(`${base(emgId)}/report`, payload).then(r => r.data)
}

/** 主管補核准：POST /emergencies/{id}/approve（EMG_DECIDE；須先有回報，否則 400） */
export function approveEmergency(emgId: string, versionNo: number): Promise<EmgVersionResult> {
  return http.post<EmgVersionResult>(`${base(emgId)}/approve`, { versionNo }).then(r => r.data)
}

/** 主管退回：POST /emergencies/{id}/return（EMG_DECIDE；原因必填 500 字內） */
export function returnEmergency(emgId: string, versionNo: number, reason: string): Promise<EmgVersionResult> {
  return http.post<EmgVersionResult>(`${base(emgId)}/return`, { versionNo, reason }).then(r => r.data)
}
