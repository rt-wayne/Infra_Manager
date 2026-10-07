// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：草稿表單純函式測試（S6 回合四）
//           驗：新增預設值；檢視回應還原成表單（日期格式、CATG 其他說明、步驟補滿 4 列、「不適用」預設字）；
//           轉成本文（類別其他說明每個大類一筆、沒勾廠商不送廠商欄、非遠端不送連線方式、不適用沒寫原因送「不適用」、數字空字串轉 null）；
//           耗時計算；副檔名規則與後端相同；附件預檢；400 field 只收安全字元
//           S9 R3（Claude Opus 5.5，2026-10-07）：測試資料補 approvalHistory（型別新增必填欄位）
// ============================================================
import { describe, expect, it } from 'vitest'
import {
  emptyForm,
  fileExt,
  formFromDetail,
  hoursBetween,
  numOrNull,
  precheckFile,
  safeFieldPath,
  toDraftRequest,
  toLocalInput
} from '../src/utils/draftForm'
import type { AppDetail } from '../src/types/app'

function detail(over: Partial<AppDetail> = {}): AppDetail {
  return {
    appId: 'IM20261007-001', title: '換交換器', prioCode: 'P2', prioName: '高', prioColor: null, statusCode: 'DRAFT',
    sourceCode: 'ONLINE', flowId: null, flowName: null, verNo: 1, rowVerNo: 3,
    applicant: { userId: 'T0001', name: '王小明', deptName: '資訊處', tel: '1234', email: 'a@example.com' },
    applyDate: '2026-10-07', selfExec: false, supplierExec: true, workModeCode: 'REMOTE', remoteMethod: 'VPN',
    supplier: { name: '某廠商', contact: '陳先生', tel: '5678', headCount: 2 },
    workSubject: '主題', impactDesc: '影響', workDetail: '細節', riskDesc: '風險', rollbackPlan: '回復',
    categories: [
      { formOptionId: 101, groupCode: 'CATG_ITEM', code: 'SW', name: '交換器', upCode: 'NET', otherText: null },
      { formOptionId: 200, groupCode: 'CATG', code: 'SYS', name: '系統', upCode: null, otherText: '自訂系統' }
    ],
    reasons: [{ formOptionId: 300, groupCode: 'REASON', code: 'EOL', name: '汰換', upCode: null, otherText: null }],
    otherReason: '順便', scopes: [{ formOptionId: 400, groupCode: 'SCOPE', code: 'NET', name: '網路', upCode: null, otherText: null }],
    equipments: [{ seqNo: 1, name: 'SW-01', assetNo: null, modelNo: 'C9300', serialNo: null, purpose: null, mgmtIp: '10.0.0.1' }],
    planSteps: [{ seqNo: 1, text: '備份' }, { seqNo: 2, text: '更換' }],
    schedule: { start: '2026-10-08 22:00', end: '2026-10-09 01:30', estHours: 3.5 },
    location: { sourceCode: null, areaName: null, rackName: null, uRange: null, siteId: null, rackId: null, uStart: null, uEnd: null, omitReason: '不適用' },
    resubmitMemo: null, checklist: [], execution: null, approval: { apprId: null, statusCode: null, startedAt: null, closedAt: null, steps: [] },
    approvalHistory: [], attachments: [], versions: [], events: [],
    permissions: { canDecide: false, canResubmit: false, canRecall: false, canExecute: false, canReview: false, canDelete: false, canAiReview: false, canSubmit: false, canEditDraft: true, deleteMode: null },
    createdAt: null, updatedAt: null,
    ...over
  }
}

describe('draftForm', () => {
  it('新增預設：P3、自行處理、現場處理、設備 1 列、步驟 4 列', () => {
    const f = emptyForm()
    expect(f.prioCode).toBe('P3')
    expect(f.selfExec).toBe(true)
    expect(f.supplierExec).toBe(false)
    expect(f.workModeCode).toBe('ONSITE')
    expect(f.equipments).toHaveLength(1)
    expect(f.planSteps).toEqual(['', '', '', ''])
  })

  it('由檢視回應還原表單', () => {
    const f = formFromDetail(detail())
    expect(f.title).toBe('換交換器')
    expect(f.start).toBe('2026-10-08T22:00')
    expect(f.end).toBe('2026-10-09T01:30')
    expect(f.estHours).toBe(3.5)
    expect(f.categoryItemIds).toEqual([101])
    expect(f.categoryOthers).toEqual({ 200: '自訂系統' })
    expect(f.reasonIds).toEqual([300])
    expect(f.scopeIds).toEqual([400])
    expect(f.planSteps).toEqual(['備份', '更換', '', ''])
    expect(f.equipments[0]).toEqual({ name: 'SW-01', assetNo: '', modelNo: 'C9300', serialNo: '', purpose: '', mgmtIp: '10.0.0.1' })
    expect(f.locOmit).toBe(true)
    expect(f.omitReason).toBe('')
    expect(f.headCount).toBe(2)
  })

  it('沒有設備時還原成 1 列空白；沒有位置原因時不勾不適用', () => {
    const f = formFromDetail(detail({
      equipments: [],
      location: { sourceCode: null, areaName: null, rackName: null, uRange: null, siteId: null, rackId: null, uStart: null, uEnd: null, omitReason: null }
    }))
    expect(f.equipments).toHaveLength(1)
    expect(f.equipments[0].name).toBe('')
    expect(f.locOmit).toBe(false)
  })

  it('轉成本文：類別其他說明每個大類一筆、依畫面順序', () => {
    const f = formFromDetail(detail())
    const b = toDraftRequest(f, [100, 200], 3)
    expect(b.categoryOthers).toEqual([{ formOptionId: 100, text: '' }, { formOptionId: 200, text: '自訂系統' }])
    expect(b.rowVerNo).toBe(3)
    expect(b.remoteMethod).toBe('VPN')
    expect(b.supplier).toEqual({ name: '某廠商', contact: '陳先生', tel: '5678', headCount: 2 })
    expect(b.schedule).toEqual({ start: '2026-10-08T22:00', end: '2026-10-09T01:30', estHours: 3.5 })
    expect(b.location.omitReason).toBe('不適用')
    expect(b.planSteps).toEqual(['備份', '更換', '', ''])
  })

  it('轉成本文：沒勾廠商不送廠商欄、現場處理不送連線方式、新增不帶 rowVerNo', () => {
    const f = { ...formFromDetail(detail()), supplierExec: false, workModeCode: 'ONSITE', locOmit: false, estHours: '' }
    const b = toDraftRequest(f, [])
    expect(b.supplier).toEqual({ name: '', contact: '', tel: '', headCount: null })
    expect(b.remoteMethod).toBe('')
    expect(b.location.omitReason).toBe('')
    expect(b.schedule.estHours).toBeNull()
    expect('rowVerNo' in b).toBe(false)
  })

  it('不適用有寫原因時送原因', () => {
    const f = { ...emptyForm(), locOmit: true, omitReason: '  雲端服務  ' }
    expect(toDraftRequest(f, []).location.omitReason).toBe('雲端服務')
  })

  it('日期與數字轉換', () => {
    expect(toLocalInput('2026-10-08 22:00')).toBe('2026-10-08T22:00')
    expect(toLocalInput(null)).toBe('')
    expect(toLocalInput('壞掉')).toBe('')
    expect(numOrNull('')).toBeNull()
    expect(numOrNull(' 2.5 ')).toBe(2.5)
    expect(numOrNull(0)).toBe(0)
    expect(numOrNull(Number.NaN)).toBeNull()
    expect(hoursBetween('2026-10-08T22:00', '2026-10-09T01:30')).toBe(3.5)
    expect(hoursBetween('2026-10-08T22:00', '2026-10-08T21:00')).toBeNull()
    expect(hoursBetween('', '2026-10-08T21:00')).toBeNull()
  })

  it('副檔名規則與後端相同', () => {
    expect(fileExt('報價單.PDF')).toBe('pdf')
    expect(fileExt('a.tar.zip')).toBe('zip')
    expect(fileExt('.hidden')).toBe('')
    expect(fileExt('結尾點.')).toBe('')
    expect(fileExt('沒有副檔名')).toBe('')
    expect(fileExt('dir.v2/檔案')).toBe('')
  })

  it('附件預檢：類型、空檔、大小', () => {
    expect(precheckFile({ name: 'a.exe', size: 10 }, 50)).toBe('不支援的檔案類型')
    expect(precheckFile({ name: 'a.pdf', size: 0 }, 50)).toBe('檔案是空的')
    expect(precheckFile({ name: 'a.pdf', size: 50 * 1024 * 1024 + 1 }, 50)).toBe('超過單檔上限 50 MB')
    expect(precheckFile({ name: 'a.msg', size: 50 * 1024 * 1024 }, 50)).toBeNull()
  })

  it('400 field 只收英數、點、方括號', () => {
    expect(safeFieldPath('equipments[0].name')).toBe('equipments[0].name')
    expect(safeFieldPath('a"],[x')).toBeNull()
    expect(safeFieldPath(3)).toBeNull()
  })
})
