// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：申請單 API（開發文件 spec_im_acct_insp.md 第 4.3 節）
//           approve／verify-ok 沒有定義本文，送 {} 讓後端不論是否宣告 @RequestBody 都能接
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（開發文件 spec_im_acct_insp_v2.0.md 第 6.1、6.5 節）：
//           送出、重新送出、回報執行改 multipart（part form＝JSON Blob、檔案 part docFile／proofFile）；
//           核准、退件、複驗都帶 versionNo（樂觀鎖），回傳 { reqId, reqStateCode, currStageId, versionNo }
// ============================================================
import http from './http'
import type {
  ActionResult,
  ExecDonePayload,
  RequestDetail,
  RequestItem,
  RequestPayload,
  ResubmitPayload,
  SubmitResult
} from '../types/request'

function base(reqId: string): string {
  return `/requests/${encodeURIComponent(reqId)}`
}

/**
 * multipart 本文：part form 為 JSON（Content-Type application/json，以 Blob 送）；檔案 part 有才附。
 * Content-Type（含 boundary）由瀏覽器依 FormData 自動產生，不手動設定
 */
export function buildMultipart(form: object, fileField: string, file: File | null): FormData {
  const fd = new FormData()
  fd.append('form', new Blob([JSON.stringify(form)], { type: 'application/json' }))
  if (file) {
    fd.append(fileField, file, file.name)
  }
  return fd
}

/** 送出申請：POST /requests（multipart；docFile 選填） */
export function submitRequest(payload: RequestPayload, docFile: File | null): Promise<SubmitResult> {
  return http.post<SubmitResult>('/requests', buildMultipart(payload, 'docFile', docFile)).then(r => r.data)
}

/** 我的申請單：GET /requests?stateCode（空值不送） */
export function listMyRequests(stateCode: string): Promise<RequestItem[]> {
  const params = stateCode ? { stateCode } : {}
  return http.get<RequestItem[]>('/requests', { params }).then(r => r.data)
}

/** 申請單明細：GET /requests/{id} */
export function getRequest(reqId: string): Promise<RequestDetail> {
  return http.get<RequestDetail>(base(reqId)).then(r => r.data)
}

/** 關卡 2～5 核准：POST /requests/{id}/approve */
export function approveRequest(reqId: string, versionNo: number): Promise<ActionResult> {
  return http.post<ActionResult>(`${base(reqId)}/approve`, { versionNo }).then(r => r.data)
}

/** 關卡 2～5 退件：POST /requests/{id}/reject（原因必填） */
export function rejectRequest(reqId: string, versionNo: number, reason: string): Promise<ActionResult> {
  return http.post<ActionResult>(`${base(reqId)}/reject`, { versionNo, reason }).then(r => r.data)
}

/** 退件後重新送出：POST /requests/{id}/resubmit（multipart；docFile 選填，有則取代舊公文） */
export function resubmitRequest(reqId: string, payload: ResubmitPayload, docFile: File | null): Promise<ActionResult> {
  return http.post<ActionResult>(`${base(reqId)}/resubmit`, buildMultipart(payload, 'docFile', docFile)).then(r => r.data)
}

/** 關卡 6 回報執行完畢：POST /requests/{id}/exec-done（multipart；proofFile 必填） */
export function execDone(reqId: string, payload: ExecDonePayload, proofFile: File): Promise<ActionResult> {
  return http.post<ActionResult>(`${base(reqId)}/exec-done`, buildMultipart(payload, 'proofFile', proofFile)).then(r => r.data)
}

/** 關卡 7 複驗通過：POST /requests/{id}/verify-ok */
export function verifyOk(reqId: string, versionNo: number): Promise<ActionResult> {
  return http.post<ActionResult>(`${base(reqId)}/verify-ok`, { versionNo }).then(r => r.data)
}

/** 關卡 7 複驗退回：POST /requests/{id}/verify-reject（原因必填） */
export function verifyReject(reqId: string, versionNo: number, reason: string): Promise<ActionResult> {
  return http.post<ActionResult>(`${base(reqId)}/verify-reject`, { versionNo, reason }).then(r => r.data)
}
