// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：草稿表單的純函式（S6 回合四），畫面狀態 ↔ API 本文的轉換與附件預檢，抽出來方便單元測試
//           - emptyForm：新增時的預設（事件分級 P3、自行處理、現場處理，與舊系統 new.ejs 相同；設備 1 列、步驟 4 列）
//           - formFromDetail：編輯時由檢視 API 回應還原（日期 yyyy-MM-dd HH:mm → datetime-local 的 yyyy-MM-ddTHH:mm）
//           - toDraftRequest：設備、步驟、類別其他說明依畫面順序全部送（空列後端略過），400 的 field 索引才對得回畫面；
//             沒勾委外廠商不送廠商欄、不是遠端不送連線方式；勾「不適用」但沒寫原因時送「不適用」（否則存檔後勾選會消失）
//           - precheckFile：只做預檢（副檔名、空檔、大小），實際檢核以後端為準；本文送一半被 413 擋下時瀏覽器只會顯示連線錯誤，所以預檢必做
//           S5 R2（2026-10-07）：MAX_ROWS 與 keepActive 自 AppFormView 移來（DraftFields 元件與範本編輯頁共用）
// ============================================================
import {
  ATTACH_EXTS,
  type AppDetail,
  type AppDraftEquipment,
  type AppDraftForm,
  type AppDraftRequest,
  type FormOption
} from '../types/app'

export const DEFAULT_PLAN_ROWS = 4
export const OMIT_DEFAULT = '不適用'
/** 與後端 AppDraftValidator.MAX_ROWS 相同（設備列、步驟列上限） */
export const MAX_ROWS = 100

/** 舊草稿或範本裡已停用的選項不在 options 裡：留著會讓後端回「選項不正確」而永遠存不了 */
export function keepActive(f: AppDraftForm, opts: FormOption[]): AppDraftForm {
  const active = new Set(opts.map(o => o.formOptionId))
  const others: Record<number, string> = {}
  for (const [k, v] of Object.entries(f.categoryOthers)) {
    if (active.has(Number(k))) others[Number(k)] = v
  }
  return {
    ...f,
    categoryItemIds: f.categoryItemIds.filter(id => active.has(id)),
    categoryOthers: others,
    reasonIds: f.reasonIds.filter(id => active.has(id)),
    scopeIds: f.scopeIds.filter(id => active.has(id))
  }
}

export function emptyEquipment(): AppDraftEquipment {
  return { name: '', assetNo: '', modelNo: '', serialNo: '', purpose: '', mgmtIp: '' }
}

export function emptyForm(): AppDraftForm {
  return {
    title: '',
    prioCode: 'P3',
    deptName: '',
    tel: '',
    email: '',
    selfExec: true,
    supplierExec: false,
    workModeCode: 'ONSITE',
    remoteMethod: '',
    supplierName: '',
    supplierContact: '',
    supplierTel: '',
    headCount: 0,
    workSubject: '',
    impactDesc: '',
    workDetail: '',
    riskDesc: '',
    rollbackPlan: '',
    categoryItemIds: [],
    categoryOthers: {},
    reasonIds: [],
    otherReason: '',
    scopeIds: [],
    equipments: [emptyEquipment()],
    planSteps: Array.from({ length: DEFAULT_PLAN_ROWS }, () => ''),
    start: '',
    end: '',
    estHours: '',
    locOmit: false,
    omitReason: ''
  }
}

/** yyyy-MM-dd HH:mm → yyyy-MM-ddTHH:mm；格式不對回空字串（讓使用者重填，不帶錯值） */
export function toLocalInput(v: string | null | undefined): string {
  if (!v) return ''
  const m = /^(\d{4}-\d{2}-\d{2})[ T](\d{2}:\d{2})/.exec(v)
  return m ? `${m[1]}T${m[2]}` : ''
}

function s(v: string | null | undefined): string {
  return v ?? ''
}

export function formFromDetail(d: AppDetail): AppDraftForm {
  const others: Record<number, string> = {}
  for (const c of d.categories) {
    if (c.groupCode === 'CATG' && c.otherText) others[c.formOptionId] = c.otherText
  }
  const steps = d.planSteps.map(p => s(p.text))
  while (steps.length < DEFAULT_PLAN_ROWS) steps.push('')
  const eqs = d.equipments.map(e => ({
    name: s(e.name),
    assetNo: s(e.assetNo),
    modelNo: s(e.modelNo),
    serialNo: s(e.serialNo),
    purpose: s(e.purpose),
    mgmtIp: s(e.mgmtIp)
  }))
  return {
    title: s(d.title),
    prioCode: s(d.prioCode) || 'P3',
    deptName: s(d.applicant.deptName),
    tel: s(d.applicant.tel),
    email: s(d.applicant.email),
    selfExec: d.selfExec,
    supplierExec: d.supplierExec,
    workModeCode: d.workModeCode || 'ONSITE',
    remoteMethod: s(d.remoteMethod),
    supplierName: s(d.supplier.name),
    supplierContact: s(d.supplier.contact),
    supplierTel: s(d.supplier.tel),
    headCount: d.supplier.headCount ?? 0,
    workSubject: s(d.workSubject),
    impactDesc: s(d.impactDesc),
    workDetail: s(d.workDetail),
    riskDesc: s(d.riskDesc),
    rollbackPlan: s(d.rollbackPlan),
    categoryItemIds: d.categories.filter(c => c.groupCode === 'CATG_ITEM').map(c => c.formOptionId),
    categoryOthers: others,
    reasonIds: d.reasons.map(r => r.formOptionId),
    otherReason: s(d.otherReason),
    scopeIds: d.scopes.map(x => x.formOptionId),
    equipments: eqs.length ? eqs : [emptyEquipment()],
    planSteps: steps,
    start: toLocalInput(d.schedule.start),
    end: toLocalInput(d.schedule.end),
    estHours: d.schedule.estHours ?? '',
    locOmit: !!d.location.omitReason,
    omitReason: d.location.omitReason === OMIT_DEFAULT ? '' : s(d.location.omitReason)
  }
}

/** type=number 清空時是空字串；非數字也當沒填（瀏覽器對不合法輸入本來就給空字串） */
export function numOrNull(v: number | string): number | null {
  if (typeof v === 'number') return Number.isFinite(v) ? v : null
  const t = v.trim()
  if (t === '') return null
  const n = Number(t)
  return Number.isFinite(n) ? n : null
}

/**
 * @param catgIds 畫面上所有作業大類（CATG）的 formOptionId，依顯示順序；
 *                categoryOthers 每個大類都送一筆（空字串後端略過），400 的 categoryOthers[i] 才對得回第 i 個大類
 */
export function toDraftRequest(f: AppDraftForm, catgIds: number[], rowVerNo?: number): AppDraftRequest {
  const remote = f.workModeCode === 'REMOTE'
  const body: AppDraftRequest = {
    title: f.title,
    prioCode: f.prioCode,
    applicant: { deptName: f.deptName, tel: f.tel, email: f.email },
    selfExec: f.selfExec,
    supplierExec: f.supplierExec,
    workModeCode: f.workModeCode,
    remoteMethod: remote ? f.remoteMethod : '',
    supplier: f.supplierExec
      ? { name: f.supplierName, contact: f.supplierContact, tel: f.supplierTel, headCount: numOrNull(f.headCount) }
      : { name: '', contact: '', tel: '', headCount: null },
    workSubject: f.workSubject,
    impactDesc: f.impactDesc,
    workDetail: f.workDetail,
    riskDesc: f.riskDesc,
    rollbackPlan: f.rollbackPlan,
    categoryItemIds: [...f.categoryItemIds],
    categoryOthers: catgIds.map(id => ({ formOptionId: id, text: f.categoryOthers[id] ?? '' })),
    reasonIds: [...f.reasonIds],
    otherReason: f.otherReason,
    scopeIds: [...f.scopeIds],
    equipments: f.equipments.map(e => ({ ...e })),
    planSteps: [...f.planSteps],
    schedule: { start: f.start, end: f.end, estHours: numOrNull(f.estHours) },
    location: { omitReason: f.locOmit ? f.omitReason.trim() || OMIT_DEFAULT : '' }
  }
  if (rowVerNo !== undefined) body.rowVerNo = rowVerNo
  return body
}

/** 兩個 datetime-local 相差幾小時（小數兩位）；任一沒填、格式不對或結束早於開始回 null */
export function hoursBetween(start: string, end: string): number | null {
  if (!start || !end) return null
  const a = Date.parse(start)
  const b = Date.parse(end)
  if (Number.isNaN(a) || Number.isNaN(b) || b < a) return null
  return Math.round(((b - a) / 3600000) * 100) / 100
}

/** 最後一個點之後、小寫；沒有副檔名（含隱藏檔 .xxx、點在結尾）回空字串，與後端 AttachmentTypes 同規則 */
export function fileExt(name: string): string {
  const base = name.slice(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1)
  const dot = base.lastIndexOf('.')
  if (dot <= 0 || dot === base.length - 1) return ''
  return base.slice(dot + 1).toLowerCase()
}

/** 回傳不能上傳的原因；可以上傳回 null */
export function precheckFile(file: { name: string; size: number }, maxMb: number): string | null {
  if (!ATTACH_EXTS.includes(fileExt(file.name))) return '不支援的檔案類型'
  if (file.size <= 0) return '檔案是空的'
  if (file.size > maxMb * 1024 * 1024) return `超過單檔上限 ${maxMb} MB`
  return null
}

/** 400 的 field 只接受「英數、點、方括號」，才拿去組 querySelector（後端值不直接進選擇器） */
export function safeFieldPath(field: unknown): string | null {
  return typeof field === 'string' && /^[A-Za-z0-9_.[\]]{1,80}$/.test(field) ? field : null
}
