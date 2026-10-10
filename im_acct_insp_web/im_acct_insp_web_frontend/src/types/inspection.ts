// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：機房巡檢 API 型別（開發文件 spec_im_acct_insp.md 第 4.4 節；欄位對應 IM_INSP、IM_INSP_RESULT、IM_INSP_ITEM）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（開發文件 spec_im_acct_insp_v2.0.md 第 6.6 節；Table 規劃 v0.2 第 7.18、7.19 節）：
//           結果只剩 NORMAL／EXCPT（刪 NA）；列表加 actAsName、unfixedCnt；明細結果改快照名稱，處理人加代理身分；
//           inspDate 格式 yyyy-MM-dd HH:mm:ss；新增 FixResult
// ============================================================

/** 巡檢結果代碼 */
export type ResultCode = 'NORMAL' | 'EXCPT'

/** GET /inspection-items 一列 */
export interface InspItem {
  inspItemId: string
  sectCode?: string
  sectName: string
  itemName: string
  itemDescr?: string | null
  sortSeq?: number
}

/** POST /inspections 的單項結果 */
export interface InspResultInput {
  inspItemId: string
  resultCode: ResultCode
  excptDescr: string
}

/** POST /inspections 本文；inspDate 格式 yyyy-MM-dd HH:mm:ss */
export interface InspPayload {
  inspDate: string
  results: InspResultInput[]
}

/** POST /inspections 回傳 */
export interface InspSubmitResult {
  inspId: string
}

/** GET /inspections 查詢條件；空值不送 */
export interface InspQuery {
  keyword?: string
  stateCode?: string
  dateFrom?: string
  dateTo?: string
}

/** GET /inspections 一列；inspStateCode 為後端計算值（NORMAL／EXCPT／FIXED） */
export interface Inspection {
  inspId: string
  inspDate: string
  /** 實際填寫人顯示名快照 */
  inspByName: string
  /** 代理身分顯示名快照（切換身分時） */
  actAsName?: string | null
  excptCnt: number
  unfixedCnt: number
  inspStateCode: string
}

/** 巡檢明細的單項結果（名稱一律用送出當下的快照） */
export interface InspResult {
  inspItemId: string
  sectNameSnap: string
  itemNameSnap: string
  resultCode: ResultCode
  excptDescr: string | null
  isFixed: number
  fixDescr: string | null
  fixByName: string | null
  fixActAsName?: string | null
  fixDate: string | null
}

/** GET /inspections/{id} */
export interface InspectionDetail extends Inspection {
  signDate?: string | null
  results: InspResult[]
}

/** POST /inspections/{id}/items/{itemId}/fix 本文 */
export interface FixPayload {
  fixDescr: string
}

/** POST /inspections/{id}/items/{itemId}/fix 回傳 */
export interface FixResult {
  inspId: string
  inspStateCode: string
}
