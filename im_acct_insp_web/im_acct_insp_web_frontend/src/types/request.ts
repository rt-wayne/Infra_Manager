// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：申請單 API 型別（開發文件 spec_im_acct_insp.md 第 4.3 節；欄位對應 IM_ACCT_REQ）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（開發文件 spec_im_acct_insp_v2.0.md 第 6.5 節；Table 規劃 v0.2 第 7.15、7.16 節）：
//           RequestPayload 去掉申請人分機／Email（不儲存、不送）與附件檔名（改 multipart 上傳）；
//           新增 ResubmitPayload（＋versionNo）、ActionResult、Attachment；
//           RequestItem 改為 /requests 列表欄位；RequestDetail 加 versionNo、附件、history、actions
// AI版本  : Claude Fable 5 (claude-fable-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S10：RequestDetail 加 acctReviewFlag／acctReviewFlagReason／acctReviewFlagDate
//           （帳號掛審查異常旗標時後端帶出，明細關卡 2～5、7 顯示警示條）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S10 審查修正：加 acctReviewFlagWillClear（複驗通過會不會清，由後端判斷）
// ============================================================
import type { AccountHist, ReqTypeCode } from './account'

/** POST /requests 的 part form（JSON） */
export interface RequestPayload {
  reqTypeCode: ReqTypeCode
  /** 非 ADD 必填；ADD 送 null */
  acctId: string | null
  applyDept: string
  catgId: string
  acctName: string
  sysName: string
  doorScopeCode: string | null
  assetClassId: string | null
  isPriv: number
  holderName: string
  holderDept: string
  holderCntct: string
  holderEmail: string
  accessLevelCode: string | null
  accessTarget: string
  applyReason: string
  expiryTypeCode: string
  /** yyyy-MM-dd；永久有效時 null */
  expiryDate: string | null
  isUniqueConf: number
}

/** POST /requests/{id}/resubmit 的 part form（reqTypeCode、acctId 不可變，送了也忽略） */
export interface ResubmitPayload extends RequestPayload {
  versionNo: number
}

/** POST /requests 回傳 */
export interface SubmitResult {
  reqId: string
}

/** 核准、退件、重送、回報、複驗的回傳 */
export interface ActionResult {
  reqId: string
  reqStateCode: string
  currStageId: number | null
  versionNo: number
}

/** GET /requests 一列（我的申請單） */
export interface RequestItem {
  reqId: string
  acctId: string
  reqTypeCode: ReqTypeCode
  reqStateCode: string
  currStageId: number | null
  stageName?: string | null
  catgId: string
  catgName?: string
  acctName: string | null
  sysName: string | null
  applyDate?: string | null
  submitDate: string
  rejectReason?: string | null
  /** 本人可重新送出（已退件且為原申請人） */
  canResubmit?: boolean
}

/** 申請單附件（IM_ACCT_ATTACH，只列 STATUS＝1） */
export interface Attachment {
  attachId: number
  /** DOC 公文、PROOF 執行佐證 */
  attachTypeCode: string
  fileName: string
  fileSize: number
  createDate: string
  createByName?: string | null
}

/** 明細「我此刻可做」的動作 */
export type RequestAction = 'APPROVE' | 'REJECT' | 'EXEC_DONE' | 'VERIFY_OK' | 'VERIFY_REJECT' | 'RESUBMIT'

/** GET /requests/{id}：IM_ACCT_REQ 全欄＋名稱、附件、本單事件、可做動作 */
export interface RequestDetail {
  reqId: string
  acctId: string
  reqTypeCode: ReqTypeCode
  reqStateCode: string
  currStageId: number | null
  stageName?: string | null
  applyBy: string
  applyByName?: string | null
  applyDept: string | null
  applyDate?: string | null
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
  applyReason: string | null
  expiryTypeCode: string
  expiryDate: string | null
  isUniqueConf?: number
  submitDate: string
  resubCnt?: number
  rejectCnt?: number
  rejectStageId?: number | null
  rejectReason?: string | null
  rejectDate?: string | null
  execBy?: string | null
  execByName?: string | null
  execDate?: string | null
  execDescr?: string | null
  cardNo?: string | null
  effectDate?: string | null
  verifyBy?: string | null
  verifyByName?: string | null
  verifyDate?: string | null
  verifyRejectCnt?: number
  verifyRejectReason?: string | null
  /** 帳號目前掛審查異常旗標時帶出（S10：關卡 2～5、7 顯示警示；異動類申請才有 acctId 對應帳號） */
  acctReviewFlag?: number | null
  acctReviewFlagReason?: string | null
  acctReviewFlagDate?: string | null
  /** 本單複驗通過會清這個旗標（只在關卡 7 可能為 true；未帶旗標時 null） */
  acctReviewFlagWillClear?: boolean | null
  doneDate?: string | null
  versionNo: number
  attachments?: Attachment[]
  history?: AccountHist[]
  actions?: RequestAction[]
}

/** POST /requests/{id}/exec-done 的 part form */
export interface ExecDonePayload {
  versionNo: number
  cardNo: string
  execDescr: string
}
