// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: 新增：緊急存取回報前端測試（BACKLOG 第 46 項 S5 回合 2）
//           詳情彈窗「回報」鈕條件（申請人是有效身分、尚未回報、尚未處理；切換中看有效身分；逾期仍可）；
//           回報彈窗：回報結果必選、沒有異動不帶帳號且說明選填、異動須有帳號且說明必填、字數按碼點、去頭尾空白；
//           切換結果保留已選帳號但「沒有異動」不送；成功 toast＋關彈窗＋重載＋emit changed；
//           一般錯誤留在彈窗；409 關彈窗並重載；送出中忽略關閉。
//           帳號選取器：完整名稱查詢、空白擋下、查無提示、403 顯示無權限提示、其他錯誤 toast；
//           加入／重複不加／名稱為空不能加／滿 20 停用並提示／移除
//           審查第一輪補：只填空白視同未填、碼點能過的案例（300 個 emoji、前後帶空白的 500 個 emoji）、
//           沒有異動也限 500 字、連點只送一次、回報彈窗開著時忽略詳情彈窗關閉、改帳號說明以新陣列交回
// ============================================================
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import type { DOMWrapper, VueWrapper } from '@vue/test-utils'
import EmergencyDetailModal from '../src/components/EmergencyDetailModal.vue'
import EmgAcctPicker from '../src/components/EmgAcctPicker.vue'
import { lookupAccounts } from '../src/api/account'
import { getEmergency, reportEmergency } from '../src/api/emergency'
import { resetSession, useSession } from '../src/composables/useSession'
import { useToast } from '../src/composables/useToast'
import type { AccountLookup } from '../src/types/account'
import type { EmgDetail, EmgPickedAcct } from '../src/types/emergency'
import { httpError, makeMe } from './helpers'

vi.mock('../src/api/emergency', () => ({
  submitEmergency: vi.fn(),
  listEmergencies: vi.fn(),
  getEmergency: vi.fn(),
  reportEmergency: vi.fn(),
  approveEmergency: vi.fn(),
  returnEmergency: vi.fn(),
  closeEmergency: vi.fn()
}))
vi.mock('../src/api/account', () => ({
  lookupAccounts: vi.fn()
}))

const getMock = vi.mocked(getEmergency)
const reportMock = vi.mocked(reportEmergency)
const lookupMock = vi.mocked(lookupAccounts)

function lastToastMsg(): string {
  const list = useToast().toasts.value
  return list.length ? list[list.length - 1].msg : ''
}

function detail(over: Partial<EmgDetail> = {}): EmgDetail {
  return {
    emgId: 'EM20261013-001', catgId: 'DB', catgName: '資料庫帳號', targetDescr: 'ERP 正式機 DB 的 sys 帳號',
    applyBy: '0000001', applyByName: 'Kevin (李凱鈞)', createBy: '0000001', createByName: 'Kevin (李凱鈞)',
    applyReason: '月結批次卡住', expectHourQty: 2,
    submitDate: '2026-10-13 09:30:15', dueDate: '2026-10-14 09:30:15',
    reportTypeCode: null, reportDescr: null, reportDate: null, reportActBy: null, reportActByName: null,
    decideCode: null, decideBy: null, decideByName: null, decideDate: null, decideActBy: null, decideActByName: null,
    returnReason: null, isSelfDecide: null,
    closeBy: null, closeByName: null, closeDate: null, closeActBy: null, closeActByName: null,
    closeDescr: null, closeResultCode: null, isSelfClose: null,
    versionNo: 1, emgStateCode: 'PENDING', affectAccts: [], ...over
  }
}

function acct(over: Partial<AccountLookup> = {}): AccountLookup {
  return {
    acctId: 'AR20261007-001', catgId: 'DB', catgName: '資料庫帳號', acctName: 'sys', sysName: 'ERP',
    holderName: 'Kevin (李凱鈞)', acctStateCode: 'APPROVED', expiryTypeCode: 'PERM', expiryDate: null,
    inFlight: false, allowedReqTypes: [], ...over
  }
}

function picked(i: number): EmgPickedAcct {
  return { acctId: `AR20261007-${String(i).padStart(3, '0')}`, acctName: `a${i}`, affectDescr: '', catgName: '主機帳號', sysName: null, acctStateCode: 'APPROVED' }
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
  resetSession()
  useSession().me.value = makeMe()
  useToast().toasts.value = []
  getMock.mockReset()
  reportMock.mockReset()
  lookupMock.mockReset()
})

afterEach(() => {
  vi.runAllTimers()
  vi.useRealTimers()
})

async function mountDetail(d: EmgDetail): Promise<VueWrapper> {
  getMock.mockResolvedValue(d)
  const w = mount(EmergencyDetailModal, { props: { emgId: 'EM20261013-001' } })
  await flushPromises()
  return w
}

/** 含指定選擇器的那個彈窗 */
function modalOf(w: VueWrapper, sel: string): DOMWrapper<Element> | undefined {
  return w.findAll('.modal').find(m => m.find(sel).exists())
}

/** 打開回報彈窗 */
async function openReport(w: VueWrapper): Promise<void> {
  await w.find('.act-report').trigger('click')
  expect(w.find('.confirm-report').exists()).toBe(true)
}

/** 在選取器查詢並加入第一列 */
async function pickFirst(w: VueWrapper, rows: AccountLookup[] = [acct()]): Promise<void> {
  lookupMock.mockResolvedValueOnce(rows)
  await w.find('.picker-input').setValue('sys')
  await w.find('.emg-picker form').trigger('submit')
  await flushPromises()
  await w.find('.picker-add').trigger('click')
}

describe('詳情彈窗「回報」鈕', () => {
  it('申請人是我、尚未回報、尚未處理才出現；逾期仍可；別人的單、已回報、已處理都沒有', async () => {
    let w = await mountDetail(detail())
    expect(w.find('.act-report').exists()).toBe(true)
    w.unmount()

    w = await mountDetail(detail({ emgStateCode: 'OVERDUE' }))
    expect(w.find('.act-report').exists()).toBe(true)
    w.unmount()

    w = await mountDetail(detail({ applyBy: '0000009' }))
    expect(w.find('.act-report').exists()).toBe(false)
    w.unmount()

    w = await mountDetail(detail({ reportTypeCode: 'NONE', reportDate: '2026-10-13 11:00:00', reportActBy: '0000001' }))
    expect(w.find('.act-report').exists()).toBe(false)
    w.unmount()

    w = await mountDetail(detail({ decideCode: 'RETURN', decideBy: '0000004', decideDate: '2026-10-13 12:00:00', emgStateCode: 'RETURNED' }))
    expect(w.find('.act-report').exists()).toBe(false)
    w.unmount()
  })

  it('不需 EMG_APPLY；切換身分時看有效身分（代申請人回報）', async () => {
    useSession().me.value = makeMe({ permissions: [], userId: '0000001', realUserId: '0000099', impersonating: true })
    let w = await mountDetail(detail())
    expect(w.find('.act-report').exists()).toBe(true)
    w.unmount()

    useSession().me.value = makeMe({ userId: '0000099', realUserId: '0000001', impersonating: true })
    w = await mountDetail(detail())
    expect(w.find('.act-report').exists()).toBe(false)
    w.unmount()
  })
})

describe('回報彈窗', () => {
  it('沒有異動：未選結果擋下；不顯示選取器；說明選填，送出不帶帳號；成功 toast、關彈窗、重載、emit changed', async () => {
    const w = await mountDetail(detail())
    await openReport(w)
    await w.find('.confirm-report').trigger('click')
    expect(lastToastMsg()).toBe('請選擇回報結果')
    expect(reportMock).not.toHaveBeenCalled()

    await w.find('.type-none').setValue()
    expect(w.find('.emg-picker').exists()).toBe(false)
    expect(w.find('label[for="emg-report-descr"]').text()).toContain('選填')
    reportMock.mockResolvedValueOnce({ emgId: 'EM20261013-001', versionNo: 2 })
    getMock.mockResolvedValue(detail({ reportTypeCode: 'NONE', reportDate: '2026-10-13 11:00:00', reportActBy: '0000001', versionNo: 2 }))
    await w.find('.confirm-report').trigger('click')
    await flushPromises()
    expect(reportMock).toHaveBeenCalledWith('EM20261013-001', { reportTypeCode: 'NONE', reportDescr: '', versionNo: 1, affectAccts: [] })
    expect(lastToastMsg()).toBe('EM20261013-001 已回報')
    expect(w.find('.confirm-report').exists()).toBe(false)
    expect(getMock).toHaveBeenCalledTimes(2)
    expect(w.find('.sec-report').text()).toContain('沒有異動')
    expect(w.find('.act-report').exists()).toBe(false)
    expect(w.emitted('changed')).toHaveLength(1)
    w.unmount()
  })

  it('異動：須有帳號、說明必填、說明按碼點 500 字；切換結果保留已選帳號；送出去頭尾空白的說明與帳號', async () => {
    const w = await mountDetail(detail())
    await openReport(w)
    await w.find('.type-modified').setValue()
    expect(w.find('.emg-picker').exists()).toBe(true)
    expect(w.find('label[for="emg-report-descr"]').text()).toContain('必填')

    await w.find('.confirm-report').trigger('click')
    expect(lastToastMsg()).toBe('請加入至少一個受影響帳號')

    await pickFirst(w)
    expect(w.findAll('.picked-row')).toHaveLength(1)
    await w.find('.confirm-report').trigger('click')
    expect(lastToastMsg()).toBe('請填寫回報說明')
    // 只填空白（含全形空白）視同未填
    await w.find('#emg-report-descr').setValue(' 　 ')
    await w.find('.confirm-report').trigger('click')
    expect(lastToastMsg()).toBe('請填寫回報說明')

    await w.find('#emg-report-descr').setValue('😀'.repeat(501))
    await w.find('.confirm-report').trigger('click')
    expect(lastToastMsg()).toBe('回報說明不可超過 500 字')
    await w.find('#emg-report-descr').setValue('  重設 sys 密碼  ')

    await w.find('.picked-descr').setValue('😀'.repeat(501))
    expect(w.find('.picked-over').text()).toBe('501 / 500')
    await w.find('.confirm-report').trigger('click')
    expect(lastToastMsg()).toBe('帳號異動說明不可超過 500 字')
    expect(reportMock).not.toHaveBeenCalled()
    await w.find('.picked-descr').setValue('  改密碼  ')

    // 切到「沒有異動」再切回來，已選帳號與說明都還在
    await w.find('.type-none').setValue()
    await w.find('.type-modified').setValue()
    expect(w.findAll('.picked-row')).toHaveLength(1)
    expect((w.find('.picked-descr').element as HTMLInputElement).value).toBe('  改密碼  ')

    reportMock.mockResolvedValueOnce({ emgId: 'EM20261013-001', versionNo: 2 })
    await w.find('.confirm-report').trigger('click')
    await flushPromises()
    expect(reportMock).toHaveBeenCalledWith('EM20261013-001', {
      reportTypeCode: 'MODIFIED', reportDescr: '重設 sys 密碼', versionNo: 1,
      affectAccts: [{ acctId: 'AR20261007-001', acctName: 'sys', affectDescr: '改密碼' }]
    })
    expect(w.emitted('changed')).toHaveLength(1)
    w.unmount()
  })

  it('字數按碼點、先去頭尾空白再算：300 個 emoji（UTF-16 長度 600）與前後帶空白的 500 個 emoji 都能送出', async () => {
    const w = await mountDetail(detail())
    await openReport(w)
    await w.find('.type-modified').setValue()
    await pickFirst(w)
    await w.find('#emg-report-descr').setValue('😀'.repeat(300))
    await w.find('.picked-descr').setValue('  ' + '😀'.repeat(500) + '  ')
    expect(w.find('.picked-over').exists()).toBe(false)
    reportMock.mockResolvedValueOnce({ emgId: 'EM20261013-001', versionNo: 2 })
    await w.find('.confirm-report').trigger('click')
    await flushPromises()
    expect(reportMock).toHaveBeenCalledTimes(1)
    expect(reportMock.mock.calls[0][1].reportDescr).toBe('😀'.repeat(300))
    expect(reportMock.mock.calls[0][1].affectAccts[0].affectDescr).toBe('😀'.repeat(500))
    w.unmount()
  })

  it('沒有異動：說明選填但仍限 500 字', async () => {
    const w = await mountDetail(detail())
    await openReport(w)
    await w.find('.type-none').setValue()
    await w.find('#emg-report-descr').setValue('x'.repeat(501))
    await w.find('.confirm-report').trigger('click')
    expect(lastToastMsg()).toBe('回報說明不可超過 500 字')
    expect(reportMock).not.toHaveBeenCalled()
    w.unmount()
  })

  it('已選帳號後改選「沒有異動」：送出不帶帳號', async () => {
    const w = await mountDetail(detail())
    await openReport(w)
    await w.find('.type-modified').setValue()
    await pickFirst(w)
    await w.find('.type-none').setValue()
    reportMock.mockResolvedValueOnce({ emgId: 'EM20261013-001', versionNo: 2 })
    await w.find('.confirm-report').trigger('click')
    await flushPromises()
    expect(reportMock.mock.calls[0][1].affectAccts).toEqual([])
    w.unmount()
  })

  it('一般錯誤 toast 後端訊息、彈窗留著；409 關彈窗、重載並 emit changed', async () => {
    const w = await mountDetail(detail())
    await openReport(w)
    await w.find('.type-none').setValue()

    reportMock.mockRejectedValueOnce(httpError(400, { message: '欄位內容不正確：受影響帳號' }))
    await w.find('.confirm-report').trigger('click')
    await flushPromises()
    expect(lastToastMsg()).toBe('欄位內容不正確：受影響帳號')
    expect(w.find('.confirm-report').exists()).toBe(true)
    expect(getMock).toHaveBeenCalledTimes(1)
    expect(w.emitted('changed')).toBeUndefined()

    reportMock.mockRejectedValueOnce(httpError(409, { code: 'E08', message: '此申請已由他人處理，請重新整理' }))
    await w.find('.confirm-report').trigger('click')
    await flushPromises()
    expect(lastToastMsg()).toBe('此申請已由他人處理，請重新整理')
    expect(w.find('.confirm-report').exists()).toBe(false)
    expect(getMock).toHaveBeenCalledTimes(2)
    expect(w.emitted('changed')).toHaveLength(1)
    w.unmount()
  })

  it('按取消只關回報彈窗；送出中取消與點遮罩都被忽略，請求回來後才關', async () => {
    const w = await mountDetail(detail())
    await openReport(w)
    const cancel = () => modalOf(w, '.confirm-report')!.findAll('.actions .btn').at(-1)!
    await cancel().trigger('click')
    expect(w.find('.confirm-report').exists()).toBe(false)
    expect(w.find('.act-report').exists()).toBe(true)
    expect(w.emitted('close')).toBeUndefined()

    await openReport(w)
    await w.find('.type-none').setValue()
    let settle: (v: { emgId: string; versionNo: number }) => void = () => undefined
    reportMock.mockReturnValueOnce(new Promise(resolve => { settle = resolve }))
    await w.find('.confirm-report').trigger('click')
    expect(w.find('.confirm-report').text()).toBe('送出中…')
    // 連點只送一次
    await w.find('.confirm-report').trigger('click')
    expect(reportMock).toHaveBeenCalledTimes(1)
    await cancel().trigger('click')
    await w.findAll('.modal-overlay').at(-1)!.trigger('click')
    expect(w.find('.confirm-report').exists()).toBe(true)

    settle({ emgId: 'EM20261013-001', versionNo: 2 })
    await flushPromises()
    expect(w.find('.confirm-report').exists()).toBe(false)
    expect(w.emitted('changed')).toHaveLength(1)
    w.unmount()
  })

  it('回報彈窗開著時，底下詳情彈窗的關閉鈕被忽略（鍵盤可摸到）；關掉回報彈窗後才關得掉', async () => {
    const w = await mountDetail(detail())
    const mainClose = () => modalOf(w, '.act-report')!.findAll('.actions .btn').at(-1)!
    await openReport(w)
    await mainClose().trigger('click')
    expect(w.emitted('close')).toBeUndefined()
    expect(w.find('.confirm-report').exists()).toBe(true)

    await modalOf(w, '.confirm-report')!.findAll('.actions .btn').at(-1)!.trigger('click')
    await mainClose().trigger('click')
    expect(w.emitted('close')).toHaveLength(1)
    w.unmount()
  })
})

describe('帳號選取器', () => {
  function mountPicker(initial: EmgPickedAcct[] = []): VueWrapper {
    const w: VueWrapper = mount(EmgAcctPicker, {
      props: {
        modelValue: initial,
        'onUpdate:modelValue': (v: EmgPickedAcct[]) => w.setProps({ modelValue: v })
      }
    })
    return w
  }
  const model = (w: VueWrapper) => (w.props() as { modelValue: EmgPickedAcct[] }).modelValue

  async function lookup(w: VueWrapper, name: string): Promise<void> {
    await w.find('.picker-input').setValue(name)
    await w.find('form').trigger('submit')
    await flushPromises()
  }

  it('空白不查並提示；送去頭尾空白的完整名稱；查無顯示提示', async () => {
    const w = mountPicker()
    await lookup(w, '   ')
    expect(lookupMock).not.toHaveBeenCalled()
    expect(lastToastMsg()).toContain('請輸入完整帳號名稱')

    lookupMock.mockResolvedValueOnce([])
    await lookup(w, '  sys  ')
    expect(lookupMock).toHaveBeenCalledWith('sys')
    expect(w.find('.picker-none').text()).toBe('查無此帳號，請確認輸入的是完整帳號名稱')
    w.unmount()
  })

  it('403 顯示「您沒有查詢帳號的權限，請聯絡管理者」；其他錯誤 toast、不顯示無權限', async () => {
    const w = mountPicker()
    lookupMock.mockRejectedValueOnce(httpError(403, { code: 'N03', message: '您沒有此功能的權限' }))
    await lookup(w, 'sys')
    expect(w.find('.picker-denied').text()).toBe('您沒有查詢帳號的權限，請聯絡管理者')
    w.unmount()

    const w2 = mountPicker()
    lookupMock.mockRejectedValueOnce(httpError(500, { message: '查詢失敗，請稍後再試' }))
    await lookup(w2, 'sys')
    expect(w2.find('.picker-denied').exists()).toBe(false)
    expect(lastToastMsg()).toBe('查詢失敗，請稍後再試')
    w2.unmount()
  })

  it('加入後顯示「已加入」不能重複加；名稱為空不能加；移除', async () => {
    const w = mountPicker()
    lookupMock.mockResolvedValueOnce([acct(), acct({ acctId: 'AR20261007-002', acctName: null })])
    await lookup(w, 'sys')
    const adds = w.findAll('.picker-add')
    expect(adds[1].attributes('disabled')).toBeDefined()
    await adds[0].trigger('click')
    await adds[0].trigger('click')
    expect(model(w)).toEqual([
      { acctId: 'AR20261007-001', acctName: 'sys', affectDescr: '', catgName: '資料庫帳號', sysName: 'ERP', acctStateCode: 'APPROVED' }
    ])
    expect(w.findAll('.picker-add')[0].text()).toBe('已加入')
    expect(w.findAll('.picker-add')[0].attributes('disabled')).toBeDefined()

    await w.find('.picker-remove').trigger('click')
    expect(model(w)).toEqual([])
    expect(w.find('.picker-empty').exists()).toBe(true)
    expect(w.findAll('.picker-add')[0].text()).toBe('加入')
    w.unmount()
  })

  it('改帳號說明以新陣列交回父層、不改原陣列；只動該列', async () => {
    const initial = [picked(1), picked(2)]
    const w = mountPicker(initial)
    await w.findAll('.picked-descr')[1].setValue('改密碼')
    expect(model(w)).not.toBe(initial)
    expect(initial[1].affectDescr).toBe('')
    expect(model(w).map(p => p.affectDescr)).toEqual(['', '改密碼'])
    expect(model(w)[0]).toEqual(picked(1))
    w.unmount()
  })

  it('滿 20 個：加入鈕停用並提示上限', async () => {
    const w = mountPicker(Array.from({ length: 19 }, (_, i) => picked(i + 1)))
    lookupMock.mockResolvedValueOnce([acct({ acctId: 'AR20261008-001', acctName: 'x1' }), acct({ acctId: 'AR20261008-002', acctName: 'x2' })])
    await lookup(w, 'x')
    expect(w.find('.picker-full').exists()).toBe(false)
    await w.findAll('.picker-add')[0].trigger('click')
    expect(model(w)).toHaveLength(20)
    expect(w.find('.picker-full').text()).toBe('最多 20 個帳號')
    expect(w.findAll('.picker-add')[1].attributes('disabled')).toBeDefined()
    await w.findAll('.picker-add')[1].trigger('click')
    expect(model(w)).toHaveLength(20)
    w.unmount()
  })
})
