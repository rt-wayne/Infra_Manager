// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：機房巡檢 API（開發文件 spec_im_acct_insp.md 第 4.4 節）；查詢條件空值不送
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（開發文件 spec_im_acct_insp_v2.0.md 第 6.6 節）：標記處理回傳 { inspId, inspStateCode }
// ============================================================
import http from './http'
import type {
  FixPayload,
  FixResult,
  InspItem,
  InspPayload,
  InspQuery,
  InspSubmitResult,
  Inspection,
  InspectionDetail
} from '../types/inspection'

/** 啟用的巡檢項目：GET /inspection-items（依 SORT_SEQ） */
export function listInspItems(): Promise<InspItem[]> {
  return http.get<InspItem[]>('/inspection-items').then(r => r.data)
}

/** 新增巡檢：POST /inspections */
export function submitInspection(payload: InspPayload): Promise<InspSubmitResult> {
  return http.post<InspSubmitResult>('/inspections', payload).then(r => r.data)
}

/** 巡檢紀錄：GET /inspections?keyword&stateCode&dateFrom&dateTo */
export function listInspections(q: InspQuery): Promise<Inspection[]> {
  const params: InspQuery = {}
  if (q.keyword) params.keyword = q.keyword
  if (q.stateCode) params.stateCode = q.stateCode
  if (q.dateFrom) params.dateFrom = q.dateFrom
  if (q.dateTo) params.dateTo = q.dateTo
  return http.get<Inspection[]>('/inspections', { params }).then(r => r.data)
}

/** 巡檢明細：GET /inspections/{id} */
export function getInspection(inspId: string): Promise<InspectionDetail> {
  return http.get<InspectionDetail>(`/inspections/${encodeURIComponent(inspId)}`).then(r => r.data)
}

/** 標記異常已處理：POST /inspections/{id}/items/{itemId}/fix（需 INSP_RESOLVE） */
export function fixInspItem(inspId: string, itemId: string, payload: FixPayload): Promise<FixResult> {
  return http
    .post<FixResult>(`/inspections/${encodeURIComponent(inspId)}/items/${encodeURIComponent(itemId)}/fix`, payload)
    .then(r => r.data)
}
