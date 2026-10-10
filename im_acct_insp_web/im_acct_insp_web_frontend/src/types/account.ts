// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：帳號 API 型別（開發文件 spec_im_acct_insp.md 第 4.2 節；欄位對應 IM_ACCT、IM_ACCT_HIST）
//           變更單 #1：M1 AccountLookup 改為 lookup 清單的精簡欄位（全部必填）；S1 新增 AccountLookupDetail（/accounts/lookup/{id}）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（開發文件 spec_im_acct_insp_v2.0.md 第 6.4 節；Table 規劃 v0.2 第 6.5、7.14、7.17 節）：
//           申請類型刪 EXTEND；Account 刪展延／匯入欄位，加 pendingVerify、inFlightReqId、allowedReqTypes；
//           新增 AccountListResult（items＋各狀態／類別數量＋到期數）；AccountQuery 加 expiring；
//           AccountHist 加 actAsId／actAsName／isSelfAction／selfActionLabel
// ============================================================

/** 申請類型（IM_ACCT_REQ.REQ_TYPE_CODE） */
export type ReqTypeCode = 'ADD' | 'MODIFY' | 'DISABLE' | 'ENABLE' | 'DELETE'

/** IM_ACCT 一列（GET /accounts 的 items、/accounts/{id}、/accounts/mine） */
export interface Account {
  acctId: string
  catgId: string
  catgName?: string
  acctName: string | null
  sysName: string | null
  doorScopeCode: string | null
  assetClassId: string | null
  assetClassName?: string | null
  isPriv: number
  holderName: string | null
  holderDept: string | null
  holderCntct: string | null
  holderEmail: string | null
  accessLevelCode: string | null
  accessTarget: string | null
  applyReason: string | null
  expiryTypeCode: string
  expiryDate: string | null
  acctStateCode: string
  sourceCode?: string | null
  applyBy?: string | null
  lastReviewDate?: string | null
  nextReviewDate?: string | null
  isReviewFlag?: number
  reviewFlagReason?: string | null
  /** 是否有在途申請（REVIEW／EXEC／VERIFY） */
  inFlight?: boolean
  inFlightReqId?: string | null
  /** 有一筆待複驗（VERIFY）的申請單：帳號列表顯示「待複驗」標記 */
  pendingVerify?: boolean
  /** 依 Table 6.5 前提且無在途時可發起的異動 */
  allowedReqTypes?: ReqTypeCode[]
}

/** GET /accounts 回傳 */
export interface AccountListResult {
  items: Account[]
  /** 各狀態數量（套用狀態以外的條件） */
  stateCounts: Record<string, number>
  /** 各類別數量（套用類別以外的條件） */
  catgCounts: Record<string, number>
  /** 到期提醒範圍筆數（不受篩選影響） */
  expiringCount: number
}

/** GET /accounts 的查詢條件；空值不送 */
export interface AccountQuery {
  keyword?: string
  catgId?: string
  stateCode?: string
  assetClassId?: string
  /** '1'＝只看到期提醒範圍 */
  expiring?: string
}

/** GET /accounts/lookup?acctName 一列 */
export interface AccountLookup {
  acctId: string
  catgId: string
  catgName: string
  acctName: string | null
  sysName: string | null
  holderName: string | null
  acctStateCode: string
  expiryTypeCode: string
  expiryDate: string | null
  inFlight: boolean
  allowedReqTypes: ReqTypeCode[]
}

/** GET /accounts/lookup/{id}：異動表單帶入用的 IM_ACCT 內容欄位 */
export interface AccountLookupDetail {
  acctId: string
  catgId: string
  catgName?: string
  acctName: string | null
  sysName: string | null
  doorScopeCode: string | null
  assetClassId: string | null
  isPriv: number
  holderName: string | null
  holderDept: string | null
  holderCntct: string | null
  holderEmail: string | null
  accessLevelCode: string | null
  accessTarget: string | null
  applyReason?: string | null
  expiryTypeCode: string
  expiryDate: string | null
  acctStateCode?: string
}

/** IM_ACCT_HIST 一列（GET /accounts/{id}/history、申請單明細的 history） */
export interface AccountHist {
  histId: number
  actDate: string
  sourceCode?: string
  actionCode: string
  stageId?: number | null
  stageName?: string | null
  reqId?: string | null
  actBy?: string | null
  /** 實際操作人顯示名快照；系統排程為「系統排程」 */
  actByName?: string | null
  /** 代理身分（切換身分時） */
  actAsId?: string | null
  actAsName?: string | null
  applyDept?: string | null
  resultDescr?: string | null
  /** 自行核准／同人兼任旗標（NUMBER(1)） */
  isSelfAction?: number
  /** 空／自行核准／同人兼任 */
  selfActionLabel?: string | null
}
