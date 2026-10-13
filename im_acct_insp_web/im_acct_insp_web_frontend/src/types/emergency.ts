// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: 新增：緊急存取型別（BACKLOG 第 46 項 S3；後端 GET/POST /emergencies，PRD API 表「緊急存取」）
//           時間欄位一律 yyyy-MM-dd HH:mm:ss 字串；emgStateCode 為後端查詢當下計算的狀態
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S5：Emg 加送出的實際操作人 createBy 與四個實際操作人姓名；
//           新增回報本文 EmgReportPayload／EmgAffectInput、處理與回報共用的回傳 EmgVersionResult
// ============================================================

/** 緊急存取狀態（計算值）：待補核准／逾期未補核准／已補核准／已退回／已退回（已結案） */
export type EmgStateCode = 'PENDING' | 'OVERDUE' | 'APPROVED' | 'RETURNED' | 'CLOSED'

/** GET /emergencies 一列（IM_EMG_ACCESS 全欄＋姓名、類別名稱、計算狀態） */
export interface Emg {
  emgId: string
  catgId: string
  catgName: string | null
  targetDescr: string
  applyBy: string
  applyByName: string | null
  /** 送出的實際操作人（切換身分代送時與 applyBy 不同） */
  createBy: string | null
  createByName: string | null
  applyReason: string
  expectHourQty: number
  submitDate: string
  dueDate: string
  reportTypeCode: string | null
  reportDescr: string | null
  reportDate: string | null
  reportActBy: string | null
  reportActByName: string | null
  decideCode: string | null
  decideBy: string | null
  decideByName: string | null
  decideDate: string | null
  decideActBy: string | null
  decideActByName: string | null
  returnReason: string | null
  isSelfDecide: number | null
  closeBy: string | null
  closeByName: string | null
  closeDate: string | null
  closeActBy: string | null
  closeActByName: string | null
  closeDescr: string | null
  closeResultCode: string | null
  isSelfClose: number | null
  versionNo: number
  emgStateCode: EmgStateCode
}

/** 受影響帳號（IM_EMG_AFFECT_ACCT） */
export interface EmgAffectAcct {
  emgId: string
  affectTypeCode: string
  acctId: string
  acctName: string | null
  sysName: string | null
  catgId: string | null
  affectDescr: string | null
  recordBy: string
  recordByName: string | null
  recordDate: string
}

/** GET /emergencies/{id} */
export interface EmgDetail extends Emg {
  affectAccts: EmgAffectAcct[]
}

/** 清單查詢條件；空值不送 */
export interface EmgQuery {
  keyword?: string
  stateCode?: string
  dateFrom?: string
  dateTo?: string
}

/** POST /emergencies 本文 */
export interface EmgPayload {
  catgId: string
  targetDescr: string
  applyReason: string
  expectHourQty: number
}

/** POST /emergencies 回傳 */
export interface EmgIdResult {
  emgId: string
}

/** 回報「異動了既有帳號」時的一個受影響帳號：單號＋完整帳號名稱（後端驗兩者同時相符）、異動說明選填 */
export interface EmgAffectInput {
  acctId: string
  acctName: string
  affectDescr: string
}

/** POST /emergencies/{id}/report 本文：NONE 不帶帳號、說明選填；MODIFIED 帳號 1～20 個、說明必填 */
export interface EmgReportPayload {
  reportTypeCode: 'NONE' | 'MODIFIED'
  reportDescr: string
  versionNo: number
  affectAccts: EmgAffectInput[]
}

/** 回報、補核准、退回的回傳：單號與操作後的新版本 */
export interface EmgVersionResult {
  emgId: string
  versionNo: number
}
