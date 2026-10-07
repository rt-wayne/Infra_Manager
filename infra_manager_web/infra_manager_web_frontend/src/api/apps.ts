// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-06
// 變更說明: 新增：申請單 API（S4 回合二），經殼 jar 轉發到後端 /api/apps
//           listApps：空字串與 false 的篩選不送（後端沒帶 from／to 時預設近 90 天）；400 帶訊息、401 交給 http 的處理器
//           S4 回合三：加 getApp（檢視頁）
//           S6 回合一-2（2026-10-07）：加 attachmentUrl／checkAttachment（附件下載：HEAD 確認後交給瀏覽器下載）
//           S6 回合四（2026-10-07）：加 getFormOptions／createApp／updateApp／uploadAttachment（草稿表單）；
//           上傳單次 timeout 600000 ms（施工計畫假設 #2：VPN／外點傳 50 MB），其餘呼叫維持 120000
//           S7 R3（Claude Fable 5.1，2026-10-07）：加 submitApp／recallApp／decideApp（送審、撤回、簽核；
//           body 都帶 rowVerNo，版本不符或狀態已變 409、非當事人 403、必填缺漏 400，訊息都由後端帶）
//           S9 R3（Claude Opus 5.5，2026-10-07）：加 resubmitApp（退件單補件重送）／deleteApp（DELETE 帶 JSON 本文）
//           S10 R3（Claude Opus 5.5，2026-10-07）：加 saveExecution（填寫執行紀錄）／rejectExecution（執行端退回）／
//           reviewExecution（治理審查）
//           S5 R3（Claude Opus 5.5，2026-10-07）：createApp 加選填 templateId（query 參數，用範本建單時累計套用次數）
// ============================================================
import http from './http'
import type {
  AppAttachment,
  AppDetail,
  AppDraftRequest,
  AppDraftSaved,
  AppListFilter,
  AppListResponse,
  DecisionRequest,
  DeleteRequest,
  DeleteResponse,
  ExecRejectRequest,
  ExecutionRequest,
  ExecutionSaved,
  FlowActionRequest,
  FlowActionResponse,
  FormOptionsResponse,
  GovernanceReviewRequest,
  ResubmitRequest
} from '../types/app'

export const UPLOAD_TIMEOUT_MS = 600000

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

/* GET /form-options：表單選項（只有啟用的）與上傳上限 */
export function getFormOptions(): Promise<FormOptionsResponse> {
  return http.get<FormOptionsResponse>('/form-options').then(r => r.data)
}

/* POST /apps 建草稿 → 201 { appId, rowVerNo: 0 }；用範本建單時帶 ?templateId=，後端同交易累計套用次數 */
export function createApp(body: AppDraftRequest, templateId?: string): Promise<AppDraftSaved> {
  const config = templateId ? { params: { templateId } } : undefined
  return http.post<AppDraftSaved>('/apps', body, config).then(r => r.data)
}

/* PUT /apps/{id} 編輯草稿（body.rowVerNo 必帶）→ 200 { appId, rowVerNo }；版本不符 409 */
export function updateApp(appId: string, body: AppDraftRequest): Promise<AppDraftSaved> {
  return http.put<AppDraftSaved>('/apps/' + encodeURIComponent(appId), body).then(r => r.data)
}

function flowPath(appId: string, action: string): string {
  return '/apps/' + encodeURIComponent(appId) + '/' + action
}

/* POST /apps/{id}/submit 送審（申請人或 admin；草稿 → 審核中）→ 200 { appId, rowVerNo } */
export function submitApp(appId: string, body: FlowActionRequest): Promise<FlowActionResponse> {
  return http.post<FlowActionResponse>(flowPath(appId, 'submit'), body).then(r => r.data)
}

/* POST /apps/{id}/recall 撤回到草稿（只限申請人、尚無關卡簽過）→ 200 { appId, rowVerNo } */
export function recallApp(appId: string, body: FlowActionRequest): Promise<FlowActionResponse> {
  return http.post<FlowActionResponse>(flowPath(appId, 'recall'), body).then(r => r.data)
}

/* POST /apps/{id}/decisions 目前關卡同意或退件（只限該關候選人）→ 200 { appId, rowVerNo } */
export function decideApp(appId: string, body: DecisionRequest): Promise<FlowActionResponse> {
  return http.post<FlowActionResponse>(flowPath(appId, 'decisions'), body).then(r => r.data)
}

/* POST /apps/{id}/resubmit 補件重送（只限申請人、退件狀態）→ 200 { appId, rowVerNo }；必填缺漏 400 整筆不存 */
export function resubmitApp(appId: string, body: ResubmitRequest): Promise<FlowActionResponse> {
  return http.post<FlowActionResponse>(flowPath(appId, 'resubmit'), body).then(r => r.data)
}

/* DELETE /apps/{id} 軟刪除（admin 任何狀態；申請人限尚無人簽過且未退件）→ 200 { appId }；單號不符或原因空白 400 */
export function deleteApp(appId: string, body: DeleteRequest): Promise<DeleteResponse> {
  return http.delete<DeleteResponse>('/apps/' + encodeURIComponent(appId), { data: body }).then(r => r.data)
}

/* PUT /apps/{id}/execution 填寫執行紀錄（idc_admin 或申請人；待執行／執行中）→ 200 { appId, rowVerNo, statusCode } */
export function saveExecution(appId: string, body: ExecutionRequest): Promise<ExecutionSaved> {
  return http.put<ExecutionSaved>(flowPath(appId, 'execution'), body).then(r => r.data)
}

/* POST /apps/{id}/execution/reject 執行端退回給申請人（權限同執行；意見必填）→ 200 { appId, rowVerNo } */
export function rejectExecution(appId: string, body: ExecRejectRequest): Promise<FlowActionResponse> {
  return http.post<FlowActionResponse>(flowPath(appId, 'execution/reject'), body).then(r => r.data)
}

/* POST /apps/{id}/governance-review 治理審查（只限 governance；待治理審核）→ 200 { appId, rowVerNo, statusCode } */
export function reviewExecution(appId: string, body: GovernanceReviewRequest): Promise<ExecutionSaved> {
  return http.post<ExecutionSaved>(flowPath(appId, 'governance-review'), body).then(r => r.data)
}

/* POST /apps/{id}/attachments 一次一檔（part 名 file）→ 201 附件資訊；onProgress 收 0～100 */
export function uploadAttachment(appId: string, file: File, onProgress?: (percent: number) => void): Promise<AppAttachment> {
  const form = new FormData()
  form.append('file', file)
  return http
    .post<AppAttachment>('/apps/' + encodeURIComponent(appId) + '/attachments', form, {
      timeout: UPLOAD_TIMEOUT_MS,
      onUploadProgress: e => {
        if (onProgress && e.total) onProgress(Math.min(100, Math.round((e.loaded * 100) / e.total)))
      }
    })
    .then(r => r.data)
}
