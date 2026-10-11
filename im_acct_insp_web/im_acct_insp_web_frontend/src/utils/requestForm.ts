// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：申請表單資料模型、前端驗證、轉 API 本文（開發文件 spec_im_acct_insp.md 第 4.3、6 節）
//           驗證順序同 4.3：本人確認 → 必填（applyDept、catgId、applyReason、expiryTypeCode、holderName；
//           非門禁卡另需 acctName、sysName；門禁卡另需 doorScopeCode）→ 指定到期日時 expiryDate 必填
//           提示文字依原型 submitAccountForm
//           變更單 #1：S1 formFromAccount 改吃 AccountLookupDetail（GET /accounts/lookup/{id}）；
//           M3 退件重新送出時附件檔名留空，由使用者重填
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（開發文件 spec_im_acct_insp_v2.0.md 第 4.3、4.9、4.15 節）：
//           驗證依類型的必填表，文案改 E01～E04（E02 一次列出缺的欄位名，以頓號分隔）；到期日早於今天擋下（E04）；
//           停用／刪除只需申請單位與原因、不做到期日檢查；申請人分機／Email 只留在表單（「同申請人」來源），不進本文；
//           刪除展延與附件檔名；空的代碼欄（assetClassId、doorScopeCode、expiryDate）送 null
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-11
// 變更說明: BACKLOG 第 40 項：formFromAccount 註解補「申請單位由呼叫端依使用者資料帶入」（邏輯不變）
// ============================================================
import type { AccountLookupDetail, ReqTypeCode } from '../types/account'
import type { RequestDetail, RequestPayload } from '../types/request'
import { DOOR_CATG_ID, isOn } from './labels'

/** 表單狀態（畫面 v-model 用；勾選類用 boolean，送出時轉 0／1） */
export interface RequestForm {
  reqTypeCode: ReqTypeCode
  /** 目標帳號；ADD 為空字串 */
  acctId: string
  applyDept: string
  /** 申請人分機：只當「同申請人」帶入來源，不送出 */
  applyTelNo: string
  /** 申請人 Email：只當「同申請人」帶入來源，不送出 */
  applyEmail: string
  catgId: string
  acctName: string
  sysName: string
  doorScopeCode: string
  assetClassId: string
  isPriv: boolean
  holderName: string
  holderDept: string
  holderCntct: string
  holderEmail: string
  accessLevelCode: string
  accessTarget: string
  applyReason: string
  expiryTypeCode: string
  expiryDate: string
  isUniqueConf: boolean
}

export const MSG_UNIQUE_CONF = '請勾選帳號唯一性聲明'
export const MSG_REQUIRED_PREFIX = '請填寫必填欄位：'
export const MSG_EXPIRY_DATE = '請填寫到期日'
export const MSG_EXPIRY_PAST = '到期日不可早於今天'
/** U03 */
export const MSG_DELETE_WARN = '刪除後此帳號將無法再啟用或異動，僅保留歷史紀錄供稽核查詢。'

export function emptyForm(catgId = ''): RequestForm {
  return {
    reqTypeCode: 'ADD',
    acctId: '',
    applyDept: '',
    applyTelNo: '',
    applyEmail: '',
    catgId,
    acctName: '',
    sysName: '',
    doorScopeCode: 'TEST',
    assetClassId: '',
    isPriv: false,
    holderName: '',
    holderDept: '',
    holderCntct: '',
    holderEmail: '',
    accessLevelCode: catgId === DOOR_CATG_ID ? 'DOOR' : 'RO',
    accessTarget: '',
    applyReason: '',
    expiryTypeCode: 'PERM',
    expiryDate: '',
    isUniqueConf: false
  }
}

function s(v: string | null | undefined): string {
  return v ?? ''
}

/** 停用、刪除：只能改申請原因（其他內容由後端複製主表），不填期限 */
export function isReasonOnly(t: ReqTypeCode): boolean {
  return t === 'DISABLE' || t === 'DELETE'
}

/** 原因欄名稱（停用／刪除另有名稱） */
export function reasonFieldName(t: ReqTypeCode): string {
  if (t === 'DISABLE') return '停用原因'
  if (t === 'DELETE') return '刪除原因'
  return '申請原因'
}

/** 對既有帳號發起異動：帶入 /accounts/lookup/{id} 的主表當下內容；申請單位、原因清空（申請單位由呼叫端依使用者資料帶入） */
export function formFromAccount(acct: AccountLookupDetail, reqTypeCode: ReqTypeCode): RequestForm {
  const isDoor = acct.catgId === DOOR_CATG_ID
  return {
    ...emptyForm(acct.catgId),
    reqTypeCode,
    acctId: acct.acctId,
    acctName: s(acct.acctName),
    sysName: s(acct.sysName),
    doorScopeCode: acct.doorScopeCode || 'TEST',
    assetClassId: isDoor ? '' : s(acct.assetClassId),
    isPriv: isOn(acct.isPriv),
    holderName: s(acct.holderName),
    holderDept: s(acct.holderDept),
    holderCntct: s(acct.holderCntct),
    holderEmail: s(acct.holderEmail),
    accessLevelCode: isDoor ? 'DOOR' : acct.accessLevelCode || 'RO',
    accessTarget: s(acct.accessTarget),
    expiryTypeCode: acct.expiryTypeCode || 'PERM',
    expiryDate: acct.expiryTypeCode === 'DATE' ? s(acct.expiryDate).slice(0, 10) : ''
  }
}

/** 退件後重新送出：帶入原申請單全部內容（申請單位保留原值） */
export function formFromRequest(req: RequestDetail): RequestForm {
  const isDoor = req.catgId === DOOR_CATG_ID
  return {
    ...emptyForm(req.catgId),
    reqTypeCode: req.reqTypeCode,
    acctId: req.reqTypeCode === 'ADD' ? '' : s(req.acctId),
    applyDept: s(req.applyDept),
    acctName: s(req.acctName),
    sysName: s(req.sysName),
    doorScopeCode: req.doorScopeCode || 'TEST',
    assetClassId: isDoor ? '' : s(req.assetClassId),
    isPriv: isOn(req.isPriv),
    holderName: s(req.holderName),
    holderDept: s(req.holderDept),
    holderCntct: s(req.holderCntct),
    holderEmail: s(req.holderEmail),
    accessLevelCode: isDoor ? 'DOOR' : req.accessLevelCode || 'RO',
    accessTarget: s(req.accessTarget),
    applyReason: s(req.applyReason),
    expiryTypeCode: req.expiryTypeCode || 'PERM',
    expiryDate: req.expiryTypeCode === 'DATE' ? s(req.expiryDate).slice(0, 10) : '',
    isUniqueConf: false
  }
}

/** 依類型列出缺的必填欄位名（spec 4.3 依類型的欄位規則；順序同畫面） */
export function missingFields(f: RequestForm): string[] {
  const isDoor = f.catgId === DOOR_CATG_ID
  const blank = (v: string): boolean => !v.trim()
  const out: string[] = []
  if (blank(f.applyDept)) out.push('申請單位')
  if (isReasonOnly(f.reqTypeCode)) {
    if (blank(f.applyReason)) out.push(reasonFieldName(f.reqTypeCode))
    return out
  }
  if (f.reqTypeCode === 'ADD') {
    if (!f.catgId) out.push('帳號類別')
    if (!isDoor) {
      if (blank(f.acctName)) out.push('帳號名稱')
      if (blank(f.sysName)) out.push('所屬系統')
    }
  }
  if (isDoor && !f.doorScopeCode) out.push('門禁權限範圍')
  if (blank(f.holderName)) out.push('使用人')
  if (blank(f.applyReason)) out.push(reasonFieldName(f.reqTypeCode))
  if (!f.expiryTypeCode) out.push('有效期限類型')
  return out
}

/**
 * 前端驗證（順序同 spec 4.3，遇第一個錯誤即回）：E01 唯一性聲明 → E02 必填 → E03 指定到期日要有日期 → E04 不早於今天
 * 停用、刪除不做 E03、E04。today 為 yyyy-MM-dd
 */
export function validateRequestForm(f: RequestForm, today: string): string | null {
  if (!f.isUniqueConf) return MSG_UNIQUE_CONF
  const missing = missingFields(f)
  if (missing.length > 0) return `${MSG_REQUIRED_PREFIX}${missing.join('、')}`
  if (isReasonOnly(f.reqTypeCode)) return null
  if (f.expiryTypeCode === 'DATE' && !f.expiryDate) return MSG_EXPIRY_DATE
  if (f.expiryTypeCode === 'DATE' && f.expiryDate < today) return MSG_EXPIRY_PAST
  return null
}

/** 表單 → POST /requests 的 part form；門禁卡固定值由後端強制，這裡也照 4.9 送空值與 DOOR */
export function toPayload(f: RequestForm): RequestPayload {
  const isDoor = f.catgId === DOOR_CATG_ID
  const t = (v: string): string => v.trim()
  const isDate = f.expiryTypeCode === 'DATE' && !!f.expiryDate
  return {
    reqTypeCode: f.reqTypeCode,
    acctId: f.reqTypeCode === 'ADD' ? null : f.acctId,
    applyDept: t(f.applyDept),
    catgId: f.catgId,
    // 門禁卡新增時卡號要等執行人在關卡 6 登記
    acctName: isDoor && f.reqTypeCode === 'ADD' ? '' : t(f.acctName),
    sysName: isDoor ? '' : t(f.sysName),
    doorScopeCode: isDoor ? f.doorScopeCode || null : null,
    assetClassId: isDoor ? null : f.assetClassId || null,
    isPriv: f.isPriv ? 1 : 0,
    holderName: t(f.holderName),
    holderDept: t(f.holderDept),
    holderCntct: isDoor ? '' : t(f.holderCntct),
    holderEmail: isDoor ? t(f.holderEmail) : '',
    accessLevelCode: isDoor ? 'DOOR' : f.accessLevelCode || null,
    accessTarget: isDoor ? '' : t(f.accessTarget),
    applyReason: t(f.applyReason),
    expiryTypeCode: f.expiryTypeCode,
    expiryDate: isDate ? f.expiryDate : null,
    isUniqueConf: f.isUniqueConf ? 1 : 0
  }
}
