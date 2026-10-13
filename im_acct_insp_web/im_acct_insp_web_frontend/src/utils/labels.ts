// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：代碼 → 畫面文字（開發文件 spec_im_acct_insp.md 第 3 節；文字依原型）
//           代碼值域依 Table 規劃 v0.1 第 6 節；查無對照時顯示原代碼
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（開發文件 spec_im_acct_insp_v2.0.md 第 4.8、4.14、4.15、8.1 節；Table 規劃 v0.2 第 6 節）：
//           代碼與中文名稱改依 Table v0.2 第 6 節集中一份：刪 EXTEND、VOID、CONFIRM；帳號狀態加 EXPIRED；
//           關卡 1～7（STAGE_LABEL）；歷史動作只留第一階段＋EXPIRE（第二階段預留碼一併列名）；角色改 6.1 的 9 個；
//           新增 reqStateText（U10）、histActorText（4.8 操作人）、personText（4.14 巡檢人員）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S3：緊急存取狀態 EMG_STATE_LABEL／EMG_STATE_FILTER（五種計算值）、
//           回報／處理／結案發現代碼文字；stateTagClass 加 PENDING（黃）、OVERDUE（紅）、RETURNED（橘）
//           （APPROVED 與帳號、申請單共用綠色；CLOSED 走預設灰色）
// ============================================================
import type { ReqTypeCode } from '../types/account'

/** 門禁卡類別代碼（IM_ACCT_CATG.CATG_ID） */
export const DOOR_CATG_ID = 'DOOR'

/** 申請類型（Table 6.5） */
export const REQ_TYPE_LABEL: Record<ReqTypeCode, string> = {
  ADD: '新增',
  MODIFY: '修改',
  DISABLE: '停用',
  ENABLE: '重新啟用',
  DELETE: '刪除'
}

/** 表單「申請類型」選項順序 */
export const REQ_TYPE_ORDER: ReqTypeCode[] = ['ADD', 'MODIFY', 'DISABLE', 'ENABLE', 'DELETE']

/** 帳號列表操作按鈕順序（spec 8.3：修改／停用／重新啟用／刪除） */
export const ACCT_OP_ORDER: ReqTypeCode[] = ['MODIFY', 'DISABLE', 'ENABLE', 'DELETE']

export function isReqType(v: unknown): v is ReqTypeCode {
  return typeof v === 'string' && (REQ_TYPE_ORDER as string[]).includes(v)
}

/** 帳號狀態（IM_ACCT.ACCT_STATE_CODE；與申請單狀態是不同集合） */
export const ACCT_STATE_LABEL: Record<string, string> = {
  REVIEW: '審核中',
  APPROVED: '已核准',
  EXPIRED: '已逾期',
  DISABLED: '已停用',
  REJECTED: '已退件',
  DELETED: '已刪除'
}

/** 帳號列表狀態篩選順序（空＝全部，不含已刪除） */
export const ACCT_STATE_ORDER: string[] = ['REVIEW', 'APPROVED', 'EXPIRED', 'DISABLED', 'REJECTED', 'DELETED']

/** 申請單狀態（IM_ACCT_REQ.REQ_STATE_CODE） */
export const REQ_STATE_LABEL: Record<string, string> = {
  REVIEW: '審核中',
  EXEC: '待執行',
  VERIFY: '待複驗',
  APPROVED: '已核准',
  REJECTED: '已退件'
}

/** 申請單列表狀態篩選（spec 8.3：全部／審核中／待執行／待複驗／已核准／已退件） */
export const REQ_STATE_FILTER: { value: string; label: string }[] = [
  { value: '', label: '全部' },
  { value: 'REVIEW', label: '審核中' },
  { value: 'EXEC', label: '待執行' },
  { value: 'VERIFY', label: '待複驗' },
  { value: 'APPROVED', label: '已核准' },
  { value: 'REJECTED', label: '已退件' }
]

/** 簽核關卡名稱（Table 6.4；/codes 未載入時的後備） */
export const STAGE_LABEL: Record<number, string> = {
  1: '申請人送出',
  2: '機房管理員',
  3: 'Infra 主管',
  4: '資訊治理',
  5: '資訊主管',
  6: '執行人',
  7: '資訊治理複驗'
}

/** 申請單狀態文字：審核中顯示 U10「審核中：待 {關卡名}」，其餘照狀態名稱 */
export function reqStateText(stateCode: string, stageName?: string | null, stageId?: number | null): string {
  if (stateCode === 'REVIEW') {
    const name = stageName || (typeof stageId === 'number' ? STAGE_LABEL[stageId] : '')
    if (name) return `審核中：待 ${name}`
  }
  return label(REQ_STATE_LABEL, stateCode)
}

/** 狀態 → tag 樣式 */
export function stateTagClass(code: string): string {
  switch (code) {
    case 'APPROVED':
    case 'NORMAL':
    case 'FIXED':
      return 'tag-ok'
    case 'REVIEW':
    case 'PENDING':
      return 'tag-pending'
    case 'EXEC':
    case 'DISABLED':
    case 'REJECTED':
    case 'EXCPT':
    case 'RETURNED':
      return 'tag-warn'
    case 'EXPIRED':
    case 'OVERDUE':
      return 'tag-priv'
    case 'VERIFY':
      return 'tag-info'
    default:
      return 'tag-gray'
  }
}

/** 緊急存取狀態（計算值 emgStateCode；不存欄位） */
export const EMG_STATE_LABEL: Record<string, string> = {
  PENDING: '待補核准',
  OVERDUE: '逾期未補核准',
  APPROVED: '已補核准',
  RETURNED: '已退回',
  CLOSED: '已退回（已結案）'
}

/** 緊急存取清單狀態篩選（空＝全部） */
export const EMG_STATE_FILTER: { value: string; label: string }[] = [
  { value: '', label: '全部狀態' },
  ...Object.entries(EMG_STATE_LABEL).map(([value, text]) => ({ value, label: text }))
]

/** 申請人回報（REPORT_TYPE_CODE） */
export const EMG_REPORT_LABEL: Record<string, string> = {
  NONE: '沒有異動',
  MODIFIED: '異動了既有帳號'
}

/** 主管處理（DECIDE_CODE） */
export const EMG_DECIDE_LABEL: Record<string, string> = {
  APPROVE: '補核准',
  RETURN: '退回'
}

/** 異常結案發現（CLOSE_RESULT_CODE） */
export const EMG_CLOSE_RESULT_LABEL: Record<string, string> = {
  NO_CHANGE: '確認沒有帳號異動',
  CHANGED: '有帳號異動'
}

/** 受影響帳號類別（AFFECT_TYPE_CODE） */
export const EMG_AFFECT_TYPE_LABEL: Record<string, string> = {
  REPORT: '申請人回報',
  CLOSE: '異常結案登記'
}

/** 巡檢整體狀態（計算值） */
export const INSP_STATE_LABEL: Record<string, string> = {
  NORMAL: '正常結案',
  EXCPT: '有異常',
  FIXED: '異常已排除'
}

/** 巡檢結果（IM_INSP_RESULT.RESULT_CODE） */
export const INSP_RESULT_LABEL: Record<string, string> = {
  NORMAL: '正常',
  EXCPT: '異常'
}

/** 權限範圍（ACCESS_LEVEL_CODE）；DOOR 為門禁卡固定值 */
export const ACCESS_LEVEL_OPTIONS: { value: string; label: string }[] = [
  { value: 'RO', label: '唯讀' },
  { value: 'OP', label: '操作' },
  { value: 'ADMIN', label: '管理員（完全控制）' }
]
export const ACCESS_LEVEL_LABEL: Record<string, string> = {
  RO: '唯讀',
  OP: '操作',
  ADMIN: '管理員（完全控制）',
  DOOR: '門禁通行權'
}

/** 門禁權限範圍（DOOR_SCOPE_CODE），選項文字同原型 */
export const DOOR_SCOPE_OPTIONS: { value: string; label: string }[] = [
  { value: 'TEST', label: '僅測試區（只能刷過第一道門，到 POS 測試機所在區域）' },
  { value: 'FULL', label: '機房完整權限（同一張卡可刷過第一、二道門，含測試區與正式機房）' }
]
export const DOOR_SCOPE_LABEL: Record<string, string> = {
  TEST: '僅測試區（第一道門）',
  FULL: '機房完整權限（兩道門）'
}

/** 附件類型（ATTACH_TYPE_CODE） */
export const ATTACH_TYPE_LABEL: Record<string, string> = {
  DOC: '公文',
  PROOF: '執行佐證'
}

/** 歷史動作（ACTION_CODE，Table 6.5；第二階段預留碼也列名，避免顯示原代碼） */
export const HIST_ACTION_LABEL: Record<string, string> = {
  SUBMIT: '送出申請',
  RESUB: '重新送出',
  APPROVE: '核准',
  REJECT: '退件',
  EXEC_DONE: '回報執行完畢（帳號生效）',
  VERIFY_OK: '複驗通過',
  VERIFY_REJECT: '複驗退回',
  EXPIRE: '到期轉為已逾期',
  REVIEW_DONE: '定期審查完成',
  REVIEW_REJECT: '定期審查駁回',
  FLAG_CLEAR: '清除審查異常標記',
  EMG_MODIFY: '緊急存取異動',
  EMG_CLOSE_MODIFY: '緊急存取結案異動'
}

/** 角色代碼 → 名稱（Table 6.1；切換身分名單顯示用） */
export const ROLE_LABEL: Record<string, string> = {
  SYS_ADMIN: '系統管理員',
  GENERAL: '一般人員',
  DC_ADMIN: '機房管理員',
  INFRA_MGR: 'Infra 主管',
  GOV: '資訊治理',
  GOV_MGR: '資訊治理主管',
  IT_HEAD: '資訊主管',
  ACCT_REGISTRY: '帳號清冊檢視',
  INSP_STAFF: '巡檢人員'
}

/** 角色清單 → 「名稱、名稱」；GENERAL 不列（每人都有） */
export function roleText(roleIds: string[] | null | undefined): string {
  return (roleIds ?? [])
    .filter(r => r !== 'GENERAL')
    .map(r => ROLE_LABEL[r] ?? r)
    .join('、')
}

/** 歷史操作人（spec 4.8）：無代理 → 實際操作人；有 → 「{實際}（以 {代理} 身分）」 */
export function histActorText(h: { actByName?: string | null; actBy?: string | null; actAsName?: string | null; actAsId?: string | null }): string {
  const by = h.actByName || h.actBy || '系統排程'
  if (!h.actAsId && !h.actAsName) return by
  return `${by}（以 ${h.actAsName || h.actAsId} 身分）`
}

/** 巡檢人員／處理人（spec 4.14）：無代理 → 實際人員；有 → 「{代理}（實際操作人：{實際}）」 */
export function personText(byName: string | null | undefined, asName: string | null | undefined): string {
  const by = byName || '—'
  return asName ? `${asName}（實際操作人：${by}）` : by
}

/** U02：此案件先前已被複驗退回過，上次退回原因：{VERIFY_REJECT_REASON} */
export function verifyRejectHint(reason: string | null | undefined): string {
  return `此案件先前已被複驗退回過，上次退回原因：${reason || '—'}`
}

/** 旗標欄（NUMBER(1)）轉布林；後端若回 boolean 也相容 */
export function isOn(v: number | boolean | null | undefined): boolean {
  return v === true || v === 1
}

/** 查表；查無時回原值，空值回「—」 */
export function label(map: Record<string, string>, code: string | null | undefined): string {
  if (!code) return '—'
  return map[code] ?? code
}

/** 剩餘天數文字（鈴鐺、到期提醒；已過為負數） */
export function daysLeftText(days: number): string {
  if (days > 0) return `${days} 天後到期`
  if (days === 0) return '今天到期'
  return `已逾期 ${-days} 天`
}

/**
 * 帳號可發起的異動：有在途申請時不給；後端回 allowedReqTypes 時以後端為準；
 * 否則依 Table 6.5 前提（MODIFY：APPROVED；DISABLE：APPROVED、EXPIRED；ENABLE：DISABLED；DELETE：APPROVED、DISABLED、EXPIRED）
 * 最終是否可申請由後端檢查
 */
export function accountOps(a: {
  acctStateCode: string
  inFlight?: boolean
  allowedReqTypes?: ReqTypeCode[]
}): ReqTypeCode[] {
  if (a.inFlight) return []
  if (Array.isArray(a.allowedReqTypes)) {
    return ACCT_OP_ORDER.filter(t => a.allowedReqTypes?.includes(t))
  }
  const pre: Record<string, ReqTypeCode[]> = {
    APPROVED: ['MODIFY', 'DISABLE', 'DELETE'],
    EXPIRED: ['DISABLE', 'DELETE'],
    DISABLED: ['ENABLE', 'DELETE']
  }
  const allowed = pre[a.acctStateCode] ?? []
  return ACCT_OP_ORDER.filter(t => allowed.includes(t))
}
