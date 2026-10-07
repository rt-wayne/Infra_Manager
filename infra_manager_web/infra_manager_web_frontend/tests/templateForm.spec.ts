// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：範本內容 ↔ 表單狀態轉換測試（S5 R2）
//           驗：null 範本與全 null 欄位都回新增預設（設備 1 列、步驟 4 列、P3）；有值時逐欄還原（CATG 其他說明、
//           「不適用」預設字還原成空原因、廠商、預計耗時）；轉成本文時不帶申請人與開始／結束時間、類別其他說明每個大類一筆、
//           沒勾廠商送 null、非遠端不送連線方式、不適用沒寫原因送「不適用」、沒勾不適用送 null、耗時空字串轉 null；
//           keepActive 拿掉已停用選項
// ============================================================
import { describe, expect, it } from 'vitest'
import { emptyForm, keepActive } from '../src/utils/draftForm'
import { formFromTemplate, toTemplateForm } from '../src/utils/templateForm'
import type { FormOption } from '../src/types/app'
import type { TemplateForm } from '../src/types/template'

function blank(): TemplateForm {
  return {
    title: null, prioCode: null, selfExec: null, supplierExec: null, workModeCode: null, remoteMethod: null,
    supplier: null, workSubject: null, impactDesc: null, workDetail: null, riskDesc: null, rollbackPlan: null,
    categoryItemIds: null, categoryOthers: null, reasonIds: null, otherReason: null, scopeIds: null,
    equipments: null, planSteps: null, schedule: null, location: null
  }
}

function opt(formOptionId: number, groupCode: string): FormOption {
  return {
    formOptionId, groupCode, code: String(formOptionId), name: String(formOptionId), upFormOptionId: null, colorCode: null,
    desc: null, timeLimitDesc: null, prioFlowDesc: null, sampleDesc: null, flowId: null, sortNo: null
  }
}

describe('formFromTemplate', () => {
  it('null 範本與全 null 欄位都回新增預設', () => {
    expect(formFromTemplate(null)).toEqual(emptyForm())
    expect(formFromTemplate(blank())).toEqual(emptyForm())
  })

  it('有值時逐欄還原，申請人與開始／結束時間保持空白', () => {
    const f = formFromTemplate({
      ...blank(),
      title: '升級',
      prioCode: 'P2',
      selfExec: false,
      supplierExec: true,
      workModeCode: 'REMOTE',
      remoteMethod: 'VPN',
      supplier: { name: '某廠商', contact: null, tel: '02', headCount: 2 },
      workSubject: '韌體升級',
      categoryItemIds: [101],
      categoryOthers: [{ formOptionId: 100, text: '其他網路' }, { formOptionId: 200, text: '' }],
      reasonIds: [300],
      scopeIds: [400],
      equipments: [{ name: 'FW-01', assetNo: null, modelNo: null, serialNo: null, purpose: null, mgmtIp: '10.0.0.1' }],
      planSteps: ['備份', null],
      schedule: { estHours: 2.5 },
      location: { omitReason: '不適用' }
    })
    expect(f).toMatchObject({
      title: '升級', prioCode: 'P2', selfExec: false, supplierExec: true, workModeCode: 'REMOTE', remoteMethod: 'VPN',
      supplierName: '某廠商', supplierContact: '', supplierTel: '02', headCount: 2, workSubject: '韌體升級',
      categoryItemIds: [101], categoryOthers: { 100: '其他網路' }, reasonIds: [300], scopeIds: [400],
      estHours: 2.5, locOmit: true, omitReason: '', deptName: '', tel: '', email: '', start: '', end: ''
    })
    expect(f.equipments).toEqual([{ name: 'FW-01', assetNo: '', modelNo: '', serialNo: '', purpose: '', mgmtIp: '10.0.0.1' }])
    expect(f.planSteps).toEqual(['備份', '', '', ''])
  })

  it('自訂的不適用原因原樣還原', () => {
    const f = formFromTemplate({ ...blank(), location: { omitReason: '雲端服務' } })
    expect(f.locOmit).toBe(true)
    expect(f.omitReason).toBe('雲端服務')
  })
})

describe('toTemplateForm', () => {
  it('預設表單：沒勾廠商送 null、非遠端不送連線方式、沒勾不適用送 null、耗時空字串轉 null、類別其他說明每個大類一筆', () => {
    const f = { ...emptyForm(), remoteMethod: '殘留', supplierName: '殘留', deptName: '資訊處', start: '2026-10-07T10:00' }
    const t = toTemplateForm(f, [100, 200])
    expect(t.supplier).toBeNull()
    expect(t.remoteMethod).toBe('')
    expect(t.location).toBeNull()
    expect(t.schedule).toEqual({ estHours: null })
    expect(t.categoryOthers).toEqual([{ formOptionId: 100, text: '' }, { formOptionId: 200, text: '' }])
    expect(t.prioCode).toBe('P3')
    expect(Object.keys(t)).not.toContain('applicant')
    expect(JSON.stringify(t)).not.toContain('2026-10-07T10:00')
  })

  it('有勾廠商、遠端、不適用時送出對應欄位；不適用沒寫原因送「不適用」', () => {
    const f = {
      ...emptyForm(),
      supplierExec: true, supplierName: '某廠商', headCount: '3',
      workModeCode: 'REMOTE', remoteMethod: 'RDP',
      locOmit: true, omitReason: '  ',
      estHours: '1.5',
      categoryOthers: { 200: '自訂系統' }
    }
    const t = toTemplateForm(f, [100, 200])
    expect(t.supplier).toEqual({ name: '某廠商', contact: '', tel: '', headCount: 3 })
    expect(t.remoteMethod).toBe('RDP')
    expect(t.location).toEqual({ omitReason: '不適用' })
    expect(t.schedule).toEqual({ estHours: 1.5 })
    expect(t.categoryOthers).toEqual([{ formOptionId: 100, text: '' }, { formOptionId: 200, text: '自訂系統' }])
  })

  it('往返：轉成範本再轉回表單內容一致（申請人與開始／結束時間除外）', () => {
    const f = { ...emptyForm(), title: 'T', workSubject: 'S', reasonIds: [300], planSteps: ['a', 'b', '', ''], estHours: 2 }
    expect(formFromTemplate(toTemplateForm(f, [100]))).toEqual(f)
  })
})

describe('keepActive', () => {
  it('拿掉已停用的類別、原因、範圍與其他說明', () => {
    const f = { ...emptyForm(), categoryItemIds: [101, 999], categoryOthers: { 100: '留', 998: '丟' }, reasonIds: [300, 997], scopeIds: [996] }
    const k = keepActive(f, [opt(100, 'CATG'), opt(101, 'CATG_ITEM'), opt(300, 'REASON')])
    expect(k.categoryItemIds).toEqual([101])
    expect(k.categoryOthers).toEqual({ 100: '留' })
    expect(k.reasonIds).toEqual([300])
    expect(k.scopeIds).toEqual([])
  })
})
