// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-06
// 變更說明: 新增：申請單列表的型別（S4 回合二）；對應後端 model.changerequest.AppListItem／AppListResponse
//           狀態、來源後端只回代碼，中文名稱在這裡對應；優先等級名稱與顏色由後端帶
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

export const STATUS_LABELS: Record<AppStatus, string> = {
  DRAFT: '草稿',
  IN_REVIEW: '審核中',
  APPROVED: '核准',
  IN_EXECUTION: '執行中',
  PENDING_REVIEW: '待治理審查',
  EXECUTED: '結案',
  REJECTED: '退件'
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
