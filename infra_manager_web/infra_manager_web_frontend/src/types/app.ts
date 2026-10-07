// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-06
// 變更說明: 新增：申請單列表的型別（S4 回合二）；對應後端 model.changerequest.AppListItem／AppListResponse
//           狀態、來源後端只回代碼，中文名稱在這裡對應；優先等級名稱與顏色由後端帶
//           S4 回合三：狀態中文改用舊系統畫面用語（回合二自訂的「審核中／核准／待治理審查／結案／退件」與舊系統不一致）；
//           補檢視頁型別 AppDetail（對應後端 model.changerequest.AppDetail）與關卡、事件、版次、作業方式等代碼的中文對照
//           S6 回合二 a（Claude Opus 5.5，2026-10-06）：AppDetail 補 rowVerNo（樂觀鎖）、AppOption 補 formOptionId；
//           新增 GET /form-options 回應型別 FormOptionsResponse（對應後端 model.changerequest.FormOptionsResponse）
//           S6 回合四（Claude Opus 5.5，2026-10-07）：新增草稿表單型別——送出本文 AppDraftRequest（對應後端
//           AppDraftRequest）、存檔回應 AppDraftSaved、400 欄位錯誤 ApiFieldErrorBody、畫面狀態 AppDraftForm；
//           附件副檔名白名單 ATTACH_EXTS（與後端 AttachmentTypes 一致，前端只做預檢）
//           S7 R3（Claude Fable 5.1，2026-10-07）：新增簽核流程型別——送審／撤回本文 FlowActionRequest、簽核本文
//           DecisionRequest（對應後端同名 record）、三者共用的回應 FlowActionResponse（{appId, rowVerNo}）
// ============================================================

export type AppStatus =
  | 'DRAFT'
  | 'IN_REVIEW'
  | 'APPROVED'
  | 'IN_EXECUTION'
  | 'PENDING_REVIEW'
  | 'EXECUTED'
  | 'REJECTED'

export type AppSource = 'ONLINE' | 'IMPORTED'

export type AppPriority = 'P1' | 'P2' | 'P3' | 'P4'

/** 與舊系統 views/partials/status-pill.ejs 用語一致 */
export const STATUS_LABELS: Record<AppStatus, string> = {
  DRAFT: '草稿',
  IN_REVIEW: '簽核中',
  APPROVED: '已核准(待執行)',
  IN_EXECUTION: '執行中',
  PENDING_REVIEW: '待治理審核',
  EXECUTED: '已結案',
  REJECTED: '已退件'
}

export const SOURCE_LABELS: Record<AppSource, string> = {
  ONLINE: '線上申請',
  IMPORTED: '紙本匯入'
}

export const PRIORITIES: AppPriority[] = ['P1', 'P2', 'P3', 'P4']

/** GET /apps 每一列 */
export interface AppListItem {
  appId: string
  title: string | null
  prioCode: string | null
  prioName: string | null
  /** #rrggbb，可能為 null */
  prioColor: string | null
  workSubject: string | null
  applyDeptName: string | null
  applicantName: string | null
  verNo: number | null
  statusCode: string
  sourceCode: string | null
  /** 目前待簽關卡名稱；非審核中為 null */
  currentStep: string | null
  /** 候選人姓名，或「N 人待簽」 */
  currentApprover: string | null
  /** 待我簽核 */
  mine: boolean
  /** yyyy-MM-dd HH:mm（台灣時間） */
  createdAt: string | null
}

/** GET /apps 回應 */
export interface AppListResponse {
  items: AppListItem[]
  total: number
  page: number
  size: number
  /** 待我簽核總數，不受篩選影響 */
  mineCount: number
}

/** GET /apps 查詢參數；空字串與 false 不送 */
export interface AppListFilter {
  status: AppStatus | ''
  priority: AppPriority | ''
  source: AppSource | ''
  mine: boolean
  q: string
  /** yyyy-MM-dd */
  from: string
  /** yyyy-MM-dd */
  to: string
}

/** 代碼對中文；查不到就原樣顯示 */
export function labelOf(map: Record<string, string>, code: string | null | undefined): string {
  if (!code) return ''
  return map[code] ?? code
}

export const WORK_MODE_LABELS: Record<string, string> = {
  ONSITE: '現場處理',
  REMOTE: '遠端連線處理'
}

export const LOC_SOURCE_LABELS: Record<string, string> = {
  IMPACT: '機房盤點連動',
  MANUAL: '手動填寫'
}

export const STEP_STATUS_LABELS: Record<string, string> = {
  WAITING: '未輪到',
  PENDING: '待簽核',
  APPROVED: '同意',
  REJECTED: '退件',
  SKIPPED: '跳過',
  CANCELLED: '已取消'
}

export const VERSION_CLOSE_LABELS: Record<string, string> = {
  REJECTED: '簽核退件',
  EXEC_REJECTED: '執行階段退回',
  GOV_RETURNED: '治理複驗退回',
  RECALLED: '撤回後改版'
}

export const EVENT_LABELS: Record<string, string> = {
  SUBMIT: '送審',
  RECALL: '撤回',
  EXEC_REJECT: '執行階段退回',
  GOV_PASS: '治理複驗通過',
  GOV_RETURN: '治理複驗退回',
  RESUBMIT: '補件重送',
  DELETE: '刪除',
  RESTORE: '還原'
}

export const ATTACH_OWNER_LABELS: Record<string, string> = {
  APP: '申請單',
  STEP: '簽核關卡',
  EVENT: '狀態事件',
  AI: 'AI 審查'
}

export interface AppApplicant {
  userId: string | null
  name: string | null
  deptName: string | null
  tel: string | null
  email: string | null
}

export interface AppSupplier {
  name: string | null
  contact: string | null
  tel: string | null
  headCount: number | null
}

/** groupCode：CATG／CATG_ITEM／REASON／SCOPE；upCode 只有 CATG_ITEM 有；otherText 只有 CATG 的「其他」補充有 */
export interface AppOption {
  formOptionId: number
  groupCode: string
  code: string
  name: string
  upCode: string | null
  otherText: string | null
}

export interface AppEquipment {
  seqNo: number | null
  name: string | null
  assetNo: string | null
  modelNo: string | null
  serialNo: string | null
  purpose: string | null
  mgmtIp: string | null
}

export interface AppPlanStep {
  seqNo: number | null
  text: string | null
}

export interface AppSchedule {
  start: string | null
  end: string | null
  estHours: number | null
}

export interface AppLocation {
  sourceCode: string | null
  areaName: string | null
  rackName: string | null
  uRange: string | null
  siteId: string | null
  rackId: string | null
  uStart: number | null
  uEnd: number | null
  omitReason: string | null
}

export interface AppCheckItem {
  seqNo: number | null
  code: string | null
  name: string | null
  done: boolean
  doneAt: string | null
  executor: string | null
}

export interface AppExecution {
  verNo: number | null
  actualStart: string | null
  actualEnd: string | null
  resultCode: string | null
  resultName: string | null
  exception: boolean
  exceptionDesc: string | null
  followUp: boolean
  followUpDesc: string | null
  memo: string | null
  executorName: string | null
  closedAt: string | null
}

export interface AppStep {
  seqNo: number | null
  stepCode: string | null
  stepName: string | null
  /** SEQUENTIAL／POST_HOC */
  stepMode: string | null
  notifyOnly: boolean
  statusCode: string | null
  deciderName: string | null
  decidedAt: string | null
  memo: string | null
  candidateNames: string[]
}

/** apprId 為 null：沒有簽核實例（草稿、已收回），steps 由流程定義展開、全部 WAITING */
export interface AppApproval {
  apprId: number | null
  statusCode: string | null
  startedAt: string | null
  closedAt: string | null
  steps: AppStep[]
}

export interface AppAttachment {
  attachId: number
  ownerType: string | null
  ownerId: string | null
  fileName: string | null
  byteQty: number | null
  mimeType: string | null
  uploadedAt: string | null
}

export interface AppVersion {
  verNo: number | null
  closeStatusCode: string | null
  reason: string | null
  snapAt: string | null
}

export interface AppEvent {
  eventId: number
  verNo: number | null
  code: string | null
  userName: string | null
  at: string | null
  memo: string | null
}

export interface AppPermissions {
  canDecide: boolean
  canResubmit: boolean
  canRecall: boolean
  canExecute: boolean
  canReview: boolean
  canDelete: boolean
  canAiReview: boolean
  canSubmit: boolean
  canEditDraft: boolean
  /** ADMIN／APPLICANT_PRE_REVIEW，canDelete 為 true 時才有 */
  deleteMode: string | null
}

/** GET /apps/{id} 回應；日期時間 yyyy-MM-dd HH:mm、日期 yyyy-MM-dd（台灣時間） */
export interface AppDetail {
  appId: string
  title: string | null
  prioCode: string | null
  prioName: string | null
  prioColor: string | null
  statusCode: string
  sourceCode: string | null
  flowId: string | null
  flowName: string | null
  verNo: number | null
  /** 樂觀鎖版號；PUT /apps/{id} 時原樣帶回 */
  rowVerNo: number
  applicant: AppApplicant
  applyDate: string | null
  selfExec: boolean
  supplierExec: boolean
  workModeCode: string | null
  remoteMethod: string | null
  supplier: AppSupplier
  workSubject: string | null
  impactDesc: string | null
  workDetail: string | null
  riskDesc: string | null
  rollbackPlan: string | null
  categories: AppOption[]
  reasons: AppOption[]
  otherReason: string | null
  scopes: AppOption[]
  equipments: AppEquipment[]
  planSteps: AppPlanStep[]
  schedule: AppSchedule
  location: AppLocation
  resubmitMemo: string | null
  checklist: AppCheckItem[]
  execution: AppExecution | null
  approval: AppApproval
  attachments: AppAttachment[]
  versions: AppVersion[]
  events: AppEvent[]
  permissions: AppPermissions
  createdAt: string | null
  updatedAt: string | null
}

/** GET /form-options 每一項；upFormOptionId 只有 CATG_ITEM 有（指向所屬 CATG） */
export interface FormOption {
  formOptionId: number
  /** PRIO／CATG／CATG_ITEM／REASON／SCOPE／CHECK_LIST／EXEC_RESULT／SIGN_ROLE */
  groupCode: string
  code: string
  name: string
  upFormOptionId: number | null
  colorCode: string | null
  desc: string | null
  timeLimitDesc: string | null
  prioFlowDesc: string | null
  sampleDesc: string | null
  flowId: string | null
  sortNo: number | null
}

/** GET /form-options 回應；upload 只供前端提示，實際檢核在後端 */
export interface FormOptionsResponse {
  options: FormOption[]
  upload: { maxMb: number; maxFiles: number }
}

/** 附件副檔名白名單（小寫、不含點）；與後端 AttachmentTypes 相同，前端只做預檢 */
export const ATTACH_EXTS: readonly string[] = [
  'jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx',
  'txt', 'csv', 'zip', 'msg'
]

export interface AppDraftEquipment {
  name: string
  assetNo: string
  modelNo: string
  serialNo: string
  purpose: string
  mgmtIp: string
}

/**
 * POST /apps、PUT /apps/{id} 本文；空字串由後端轉 null。
 * equipments、planSteps、categoryOthers 依畫面列序全部送出（空列後端略過），400 的 field 索引才對得回畫面
 */
export interface AppDraftRequest {
  title: string
  prioCode: string
  applicant: { deptName: string; tel: string; email: string }
  selfExec: boolean
  supplierExec: boolean
  workModeCode: string
  remoteMethod: string
  supplier: { name: string; contact: string; tel: string; headCount: number | null }
  workSubject: string
  impactDesc: string
  workDetail: string
  riskDesc: string
  rollbackPlan: string
  categoryItemIds: number[]
  categoryOthers: { formOptionId: number; text: string }[]
  reasonIds: number[]
  otherReason: string
  scopeIds: number[]
  equipments: AppDraftEquipment[]
  planSteps: string[]
  /** start／end：datetime-local 的 yyyy-MM-ddTHH:mm */
  schedule: { start: string; end: string; estHours: number | null }
  location: { omitReason: string }
  /** 只有 PUT 帶 */
  rowVerNo?: number
}

/** POST /apps（201）、PUT /apps/{id}（200）回應 */
export interface AppDraftSaved {
  appId: string
  rowVerNo: number
}

/** POST /apps/{id}/submit、/recall 本文；rowVerNo 是檢視頁拿到的樂觀鎖版號，reason 只有撤回用（選填） */
export interface FlowActionRequest {
  rowVerNo: number
  reason?: string
}

export type Decision = 'APPROVE' | 'REJECT'

/** POST /apps/{id}/decisions 本文；memo 退件必填、同意可空（後端自動填「同意」） */
export interface DecisionRequest {
  rowVerNo: number
  decision: Decision
  memo: string
}

/** 送審／撤回／簽核成功的回應；rowVerNo 是新版號（頁面會重新載入，不直接用它） */
export interface FlowActionResponse {
  appId: string
  rowVerNo: number
}

/** 400 欄位過長時多帶 field（例 equipments[0].name）、max、actual */
export interface ApiFieldErrorBody {
  message: string
  field?: string
  max?: number
  actual?: number
}

/** 表單畫面狀態；數字欄位綁 type=number，清空時是空字串 */
export interface AppDraftForm {
  title: string
  prioCode: string
  deptName: string
  tel: string
  email: string
  selfExec: boolean
  supplierExec: boolean
  workModeCode: string
  remoteMethod: string
  supplierName: string
  supplierContact: string
  supplierTel: string
  headCount: number | string
  workSubject: string
  impactDesc: string
  workDetail: string
  riskDesc: string
  rollbackPlan: string
  categoryItemIds: number[]
  /** key 為 CATG 的 formOptionId */
  categoryOthers: Record<number, string>
  reasonIds: number[]
  otherReason: string
  scopeIds: number[]
  equipments: AppDraftEquipment[]
  planSteps: string[]
  /** yyyy-MM-ddTHH:mm */
  start: string
  end: string
  estHours: number | string
  locOmit: boolean
  omitReason: string
}
