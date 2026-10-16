// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: 新增：緊急存取異常結案前端測試（BACKLOG 第 46 項 S6 回合 2）
//           詳情彈窗「異常結案」鈕條件（EMG_DECIDE、已退回、尚未結案；申請人本人有 EMG_DECIDE 也有；
//           未處理、已補核准、已結案、無權限都沒有）；
//           結案彈窗：結案發現必選（未選時提示文字不偏向任一種）、結案說明兩種都必填（只填空白視同未填）、
//           沒有異動不帶帳號、有異動須有帳號、字數按碼點、去頭尾空白（剛好 500 可送、501 擋）；切換結果保留已選帳號但「沒有異動」不送；成功 toast＋關彈窗＋重載＋emit changed；
//           一般錯誤留在彈窗；409 關彈窗並重載；送出中忽略關閉、連點只送一次；結案彈窗開著時忽略詳情彈窗關閉
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: 必填欄位醒目提醒：結案說明改以紅色 *（.req-mark）判斷必填
// ============================================================
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import type { DOMWrapper, VueWrapper } from '@vue/test-utils'
import EmergencyDetailModal from '../src/components/EmergencyDetailModal.vue'
import { lookupAccounts } from '../src/api/account'
import { closeEmergency, getEmergency } from '../src/api/emergency'
import { resetSession, useSession } from '../src/composables/useSession'
import { useToast } from '../src/composables/useToast'
import type { AccountLookup } from '../src/types/account'
import type { EmgDetail } from '../src/types/emergency'
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
const closeMock = vi.mocked(closeEmergency)
const lookupMock = vi.mocked(lookupAccounts)

function lastToastMsg(): string {
  const list = useToast().toasts.value
  return list.length ? list[list.length - 1].msg : ''
}

/** 預設為已退回、尚未結案（申請人回報有異動） */
function detail(over: Partial<EmgDetail> = {}): EmgDetail {
  return {
    emgId: 'EM20261013-001', catgId: 'DB', catgName: '資料庫帳號', targetDescr: 'ERP 正式機 DB 的 sys 帳號',
    applyBy: '0000001', applyByName: 'Kevin (李凱鈞)', createBy: '0000001', createByName: 'Kevin (李凱鈞)',
    applyReason: '月結批次卡住', expectHourQty: 2,
    submitDate: '2026-10-13 09:30:15', dueDate: '2026-10-14 09:30:15',
    reportTypeCode: 'MODIFIED', reportDescr: '重設密碼', reportDate: '2026-10-13 11:00:00', reportActBy: '0000001', reportActByName: 'Kevin (李凱鈞)',
    decideCode: 'RETURN', decideBy: '0000004', decideByName: 'Wayne', decideDate: '2026-10-13 12:00:00',
    decideActBy: '0000004', decideActByName: 'Wayne', returnReason: '異動未事先告知', isSelfDecide: 0,
    closeBy: null, closeByName: null, closeDate: null, closeActBy: null, closeActByName: null,
    closeDescr: null, closeResultCode: null, isSelfClose: null,
    versionNo: 2, emgStateCode: 'RETURNED', affectAccts: [], ...over
  }
}

/** 結案後的明細 */
function closed(resultCode: 'NO_CHANGE' | 'CHANGED', descr: string): EmgDetail {
  return detail({
    closeBy: '0000004', closeByName: 'Wayne', closeDate: '2026-10-13 14:00:00', closeActBy: '0000004', closeActByName: 'Wayne',
    closeResultCode: resultCode, closeDescr: descr, isSelfClose: 0, versionNo: 3, emgStateCode: 'CLOSED'
  })
}

function acct(over: Partial<AccountLookup> = {}): AccountLookup {
  return {
    acctId: 'AR20261007-001', catgId: 'DB', catgName: '資料庫帳號', acctName: 'sys', sysName: 'ERP',
    holderName: 'Kevin (李凱鈞)', acctStateCode: 'APPROVED', expiryTypeCode: 'PERM', expiryDate: null,
    inFlight: false, allowedReqTypes: [], ...over
  }
}

/** 主管（有 EMG_DECIDE，不是申請人） */
function asDecider(): void {
  useSession().me.value = makeMe({ userId: '0000004', realUserId: '0000004', permissions: [...makeMe().permissions, 'EMG_DECIDE'] })
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
  resetSession()
  asDecider()
  useToast().toasts.value = []
  getMock.mockReset()
  closeMock.mockReset()
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

/** 打開結案彈窗 */
async function openClose(w: VueWrapper): Promise<void> {
  await w.find('.act-close-case').trigger('click')
  expect(w.find('.confirm-close').exists()).toBe(true)
}

/** 在選取器查詢並加入第一列 */
async function pickFirst(w: VueWrapper, rows: AccountLookup[] = [acct()]): Promise<void> {
  lookupMock.mockResolvedValueOnce(rows)
  await w.find('.picker-input').setValue('sys')
  await w.find('.emg-picker form').trigger('submit')
  await flushPromises()
  await w.find('.picker-add').trigger('click')
}

describe('詳情彈窗「異常結案」鈕', () => {
  it('EMG_DECIDE、已退回、尚未結案才出現；未處理、已補核准、已結案都沒有', async () => {
    let w = await mountDetail(detail())
    expect(w.find('.act-close-case').exists()).toBe(true)
    expect(w.find('.act-approve').exists()).toBe(false)
    w.unmount()

    w = await mountDetail(detail({ decideCode: null, decideBy: null, decideDate: null, returnReason: null, versionNo: 1, emgStateCode: 'PENDING' }))
    expect(w.find('.act-close-case').exists()).toBe(false)
    expect(w.find('.act-approve').exists()).toBe(true)
    w.unmount()

    w = await mountDetail(detail({ decideCode: 'APPROVE', returnReason: null, emgStateCode: 'APPROVED' }))
    expect(w.find('.act-close-case').exists()).toBe(false)
    w.unmount()

    w = await mountDetail(closed('NO_CHANGE', '已確認'))
    expect(w.find('.act-close-case').exists()).toBe(false)
    w.unmount()
  })

  it('申請人本人有 EMG_DECIDE 也看得到（後端允許自行結案，並標 IS_SELF_CLOSE）', async () => {
    useSession().me.value = makeMe({ permissions: [...makeMe().permissions, 'EMG_DECIDE'] })
    const w = await mountDetail(detail())
    expect(w.find('.act-close-case').exists()).toBe(true)
    w.unmount()
  })

  it('沒有 EMG_DECIDE 看不到（申請人本人也一樣）', async () => {
    useSession().me.value = makeMe()
    const w = await mountDetail(detail())
    expect(w.find('.act-close-case').exists()).toBe(false)
    w.unmount()
  })
})

describe('結案彈窗', () => {
  it('沒有異動：未選結果擋下；說明必填（只填空白視同未填）；不顯示選取器、送出不帶帳號；成功 toast、關彈窗、重載、emit changed', async () => {
    const w = await mountDetail(detail())
    await openClose(w)
    const placeholder = () => w.find('#emg-close-descr').attributes('placeholder')
    expect(placeholder()).toBe('請先選擇結案發現，再說明確認經過')
    await w.find('.confirm-close').trigger('click')
    expect(lastToastMsg()).toBe('請選擇結案發現')

    await w.find('.result-no-change').setValue()
    expect(placeholder()).toBe('請說明如何確認沒有異動')
    expect(w.find('.emg-picker').exists()).toBe(false)
    expect(w.find('label[for="emg-close-descr"] .req-mark').exists()).toBe(true)
    await w.find('.confirm-close').trigger('click')
    expect(lastToastMsg()).toBe('請填寫結案說明')
    await w.find('#emg-close-descr').setValue(' 　 ')
    await w.find('.confirm-close').trigger('click')
    expect(lastToastMsg()).toBe('請填寫結案說明')
    expect(closeMock).not.toHaveBeenCalled()

    await w.find('#emg-close-descr').setValue('  已逐台確認  ')
    closeMock.mockResolvedValueOnce({ emgId: 'EM20261013-001', versionNo: 3 })
    getMock.mockResolvedValue(closed('NO_CHANGE', '已逐台確認'))
    await w.find('.confirm-close').trigger('click')
    await flushPromises()
    expect(closeMock).toHaveBeenCalledWith('EM20261013-001', { closeResultCode: 'NO_CHANGE', closeDescr: '已逐台確認', versionNo: 2, affectAccts: [] })
    expect(lastToastMsg()).toBe('EM20261013-001 已異常結案')
    expect(w.find('.confirm-close').exists()).toBe(false)
    expect(getMock).toHaveBeenCalledTimes(2)
    expect(w.find('.sec-close').text()).toContain('確認沒有帳號異動：已逐台確認')
    expect(w.find('.act-close-case').exists()).toBe(false)
    expect(w.emitted('changed')).toHaveLength(1)
    w.unmount()
  })

  it('有異動：須有帳號、說明必填且按碼點 500 字、帳號說明 500 字；切換結果保留已選帳號；送出去頭尾空白', async () => {
    const w = await mountDetail(detail())
    await openClose(w)
    await w.find('.result-changed').setValue()
    expect(w.find('#emg-close-descr').attributes('placeholder')).toBe('請說明發現了哪些異動')
    expect(w.find('.emg-picker').exists()).toBe(true)

    await w.find('.confirm-close').trigger('click')
    expect(lastToastMsg()).toBe('請加入至少一個受影響帳號')

    await pickFirst(w)
    await w.find('.confirm-close').trigger('click')
    expect(lastToastMsg()).toBe('請填寫結案說明')

    await w.find('#emg-close-descr').setValue('😀'.repeat(501))
    await w.find('.confirm-close').trigger('click')
    expect(lastToastMsg()).toBe('結案說明不可超過 500 字')
    // 剛好 500 個 emoji（UTF-16 長度 1000）＋前後空白：按碼點、去頭尾空白算沒超過
    await w.find('#emg-close-descr').setValue('  ' + '😀'.repeat(500) + '  ')

    await w.find('.picked-descr').setValue('😀'.repeat(501))
    await w.find('.confirm-close').trigger('click')
    expect(lastToastMsg()).toBe('帳號異動說明不可超過 500 字')
    expect(closeMock).not.toHaveBeenCalled()
    // 帳號說明同樣剛好 500 個 emoji＋前後空白可送
    await w.find('.picked-descr').setValue('  ' + '😀'.repeat(500) + '  ')

    await w.find('.result-no-change').setValue()
    await w.find('.result-changed').setValue()
    expect(w.findAll('.picked-row')).toHaveLength(1)

    closeMock.mockResolvedValueOnce({ emgId: 'EM20261013-001', versionNo: 3 })
    await w.find('.confirm-close').trigger('click')
    await flushPromises()
    expect(closeMock).toHaveBeenCalledWith('EM20261013-001', {
      closeResultCode: 'CHANGED', closeDescr: '😀'.repeat(500), versionNo: 2,
      affectAccts: [{ acctId: 'AR20261007-001', acctName: 'sys', affectDescr: '😀'.repeat(500) }]
    })
    expect(w.emitted('changed')).toHaveLength(1)
    w.unmount()
  })

  it('已選帳號後改選「沒有異動」：送出不帶帳號', async () => {
    const w = await mountDetail(detail())
    await openClose(w)
    await w.find('.result-changed').setValue()
    await pickFirst(w)
    await w.find('.result-no-change').setValue()
    await w.find('#emg-close-descr').setValue('誤判，實際沒有異動')
    closeMock.mockResolvedValueOnce({ emgId: 'EM20261013-001', versionNo: 3 })
    await w.find('.confirm-close').trigger('click')
    await flushPromises()
    expect(closeMock.mock.calls[0][1].affectAccts).toEqual([])
    w.unmount()
  })

  it('一般錯誤 toast 後端訊息、彈窗留著；409 關彈窗、重載並 emit changed', async () => {
    const w = await mountDetail(detail())
    await openClose(w)
    await w.find('.result-no-change').setValue()
    await w.find('#emg-close-descr').setValue('已確認')

    closeMock.mockRejectedValueOnce(httpError(400, { message: '欄位內容不正確：受影響帳號' }))
    await w.find('.confirm-close').trigger('click')
    await flushPromises()
    expect(lastToastMsg()).toBe('欄位內容不正確：受影響帳號')
    expect(w.find('.confirm-close').exists()).toBe(true)
    expect(getMock).toHaveBeenCalledTimes(1)
    expect(w.emitted('changed')).toBeUndefined()

    closeMock.mockRejectedValueOnce(httpError(409, { code: 'E08', message: '此申請已由他人處理，請重新整理' }))
    getMock.mockResolvedValue(closed('CHANGED', '別的主管結的'))
    await w.find('.confirm-close').trigger('click')
    await flushPromises()
    expect(lastToastMsg()).toBe('此申請已由他人處理，請重新整理')
    expect(w.find('.confirm-close').exists()).toBe(false)
    expect(getMock).toHaveBeenCalledTimes(2)
    expect(w.find('.act-close-case').exists()).toBe(false)
    expect(w.emitted('changed')).toHaveLength(1)
    w.unmount()
  })

  it('按取消只關結案彈窗；送出中取消與點遮罩都被忽略、連點只送一次，請求回來後才關', async () => {
    const w = await mountDetail(detail())
    await openClose(w)
    const cancel = () => modalOf(w, '.confirm-close')!.findAll('.actions .btn').at(-1)!
    await cancel().trigger('click')
    expect(w.find('.confirm-close').exists()).toBe(false)
    expect(w.find('.act-close-case').exists()).toBe(true)
    expect(w.emitted('close')).toBeUndefined()

    await openClose(w)
    await w.find('.result-no-change').setValue()
    await w.find('#emg-close-descr').setValue('已確認')
    let settle: (v: { emgId: string; versionNo: number }) => void = () => undefined
    closeMock.mockReturnValueOnce(new Promise(resolve => { settle = resolve }))
    await w.find('.confirm-close').trigger('click')
    expect(w.find('.confirm-close').text()).toBe('送出中…')
    await w.find('.confirm-close').trigger('click')
    expect(closeMock).toHaveBeenCalledTimes(1)
    await cancel().trigger('click')
    await w.findAll('.modal-overlay').at(-1)!.trigger('click')
    expect(w.find('.confirm-close').exists()).toBe(true)

    settle({ emgId: 'EM20261013-001', versionNo: 3 })
    await flushPromises()
    expect(w.find('.confirm-close').exists()).toBe(false)
    expect(w.emitted('changed')).toHaveLength(1)
    w.unmount()
  })

  it('結案彈窗開著時，底下詳情彈窗的關閉鈕被忽略；關掉結案彈窗後才關得掉', async () => {
    const w = await mountDetail(detail())
    const mainClose = () => modalOf(w, '.act-close-case')!.findAll('.actions .btn').at(-1)!
    await openClose(w)
    await mainClose().trigger('click')
    expect(w.emitted('close')).toBeUndefined()
    expect(w.find('.confirm-close').exists()).toBe(true)

    await modalOf(w, '.confirm-close')!.findAll('.actions .btn').at(-1)!.trigger('click')
    await mainClose().trigger('click')
    expect(w.emitted('close')).toHaveLength(1)
    w.unmount()
  })

  it('結案說明含 HTML 時以純文字顯示（不用 v-html）', async () => {
    const w = await mountDetail(closed('CHANGED', '<img src=x onerror=alert(1)>'))
    expect(w.find('.sec-close img').exists()).toBe(false)
    expect(w.find('.sec-close').text()).toContain('<img src=x onerror=alert(1)>')
    w.unmount()
  })
})
