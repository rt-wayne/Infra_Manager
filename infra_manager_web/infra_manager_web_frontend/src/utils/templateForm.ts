// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：範本內容 ↔ 草稿表單畫面狀態的轉換（S5 R2；R3 套用範本也用 formFromTemplate）
//           - formFromTemplate：每個欄位都可能是 null（後端照送 null），缺的用 emptyForm 的預設；設備至少 1 列、步驟補到 4 列；
//             申請人聯絡資料與預定開始／結束時間範本不存，保持空白
//           - toTemplateForm：規則同 toDraftRequest（設備、步驟、類別其他說明依畫面順序全部送，400 的 field 索引才對得回畫面；
//             沒勾委外廠商不送廠商、不是遠端不送連線方式；勾「不適用」沒寫原因送「不適用」），只是不送申請人與開始／結束時間
// ============================================================
import type { AppDraftForm } from '../types/app'
import type { TemplateForm } from '../types/template'
import { DEFAULT_PLAN_ROWS, OMIT_DEFAULT, emptyEquipment, emptyForm, numOrNull } from './draftForm'

function s(v: string | null | undefined): string {
  return v ?? ''
}

export function formFromTemplate(t: TemplateForm | null): AppDraftForm {
  const base = emptyForm()
  if (!t) return base
  const others: Record<number, string> = {}
  for (const o of t.categoryOthers ?? []) {
    if (o.text) others[o.formOptionId] = o.text
  }
  const steps = (t.planSteps ?? []).map(x => s(x))
  while (steps.length < DEFAULT_PLAN_ROWS) steps.push('')
  const eqs = (t.equipments ?? []).map(e => ({
    name: s(e.name),
    assetNo: s(e.assetNo),
    modelNo: s(e.modelNo),
    serialNo: s(e.serialNo),
    purpose: s(e.purpose),
    mgmtIp: s(e.mgmtIp)
  }))
  const omit = t.location?.omitReason ?? ''
  return {
    ...base,
    title: s(t.title),
    prioCode: s(t.prioCode) || base.prioCode,
    selfExec: t.selfExec ?? base.selfExec,
    supplierExec: t.supplierExec ?? base.supplierExec,
    workModeCode: s(t.workModeCode) || base.workModeCode,
    remoteMethod: s(t.remoteMethod),
    supplierName: s(t.supplier?.name),
    supplierContact: s(t.supplier?.contact),
    supplierTel: s(t.supplier?.tel),
    headCount: t.supplier?.headCount ?? 0,
    workSubject: s(t.workSubject),
    impactDesc: s(t.impactDesc),
    workDetail: s(t.workDetail),
    riskDesc: s(t.riskDesc),
    rollbackPlan: s(t.rollbackPlan),
    categoryItemIds: [...(t.categoryItemIds ?? [])],
    categoryOthers: others,
    reasonIds: [...(t.reasonIds ?? [])],
    otherReason: s(t.otherReason),
    scopeIds: [...(t.scopeIds ?? [])],
    equipments: eqs.length ? eqs : [emptyEquipment()],
    planSteps: steps,
    estHours: t.schedule?.estHours ?? '',
    locOmit: omit !== '',
    omitReason: omit === OMIT_DEFAULT ? '' : omit
  }
}

/** @param catgIds 畫面上所有作業大類（CATG）的 formOptionId，依顯示順序（同 toDraftRequest） */
export function toTemplateForm(f: AppDraftForm, catgIds: number[]): TemplateForm {
  const remote = f.workModeCode === 'REMOTE'
  return {
    title: f.title,
    prioCode: f.prioCode,
    selfExec: f.selfExec,
    supplierExec: f.supplierExec,
    workModeCode: f.workModeCode,
    remoteMethod: remote ? f.remoteMethod : '',
    supplier: f.supplierExec
      ? { name: f.supplierName, contact: f.supplierContact, tel: f.supplierTel, headCount: numOrNull(f.headCount) }
      : null,
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
    schedule: { estHours: numOrNull(f.estHours) },
    location: f.locOmit ? { omitReason: f.omitReason.trim() || OMIT_DEFAULT } : null
  }
}
