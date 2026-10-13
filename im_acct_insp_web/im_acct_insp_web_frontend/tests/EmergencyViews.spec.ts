// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: 新增：緊急存取前端測試（BACKLOG 第 46 項 S3）
//           清單：預設不帶日期條件、狀態標籤、申請鈕依 EMG_APPLY、「只顯示您送出的」依 EMG_VIEW_ALL、失敗 toast、空結果、查看開彈窗；
//           申請：停用類別加註、必填與字數檢查、確認框、送出本文、成功回清單、失敗留在表單；
//           詳情：尚未回報／尚未處理、退回才有異常結案段、自核與逾期後處理標記、受影響帳號列表
//           S3 審查：沒有回應或 5xx 時提示先查清單（toast＋常駐提示）；不設 maxlength、字數按字元且不含頭尾空白；
//           切換中申請人文字；狀態下拉重查；API 空值不送
// ============================================================
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import type { VueWrapper } from '@vue/test-utils'
import { AxiosError } from 'axios'
import http from '../src/api/http'
import EmergencyListView from '../src/views/EmergencyListView.vue'
import EmergencyFormView from '../src/views/EmergencyFormView.vue'
import EmergencyDetailModal from '../src/components/EmergencyDetailModal.vue'
import { getCodes } from '../src/api/common'
import { getEmergency, listEmergencies, submitEmergency } from '../src/api/emergency'
import { resetSession, useSession } from '../src/composables/useSession'
import type { Emg, EmgAffectAcct, EmgDetail } from '../src/types/emergency'
import { CODES, GENERAL_PERMS, httpError, lastToast, makeMe, makeRouter } from './helpers'

vi.mock('../src/api/common', () => ({
  getCodes: vi.fn(),
  getHomeStats: vi.fn(),
  listExpiring: vi.fn(),
  listTodos: vi.fn()
}))
vi.mock('../src/api/emergency', () => ({
  submitEmergency: vi.fn(),
  listEmergencies: vi.fn(),
  getEmergency: vi.fn()
}))

const listMock = vi.mocked(listEmergencies)
const getMock = vi.mocked(getEmergency)
const submitMock = vi.mocked(submitEmergency)

function emg(over: Partial<Emg> = {}): Emg {
  return {
    emgId: 'EM20261013-001', catgId: 'DB', catgName: '資料庫帳號', targetDescr: 'ERP 正式機 DB 的 sys 帳號',
    applyBy: '0000001', applyByName: 'Kevin (李凱鈞)', applyReason: '月結批次卡住\n需人工處理', expectHourQty: 2,
    submitDate: '2026-10-13 09:30:15', dueDate: '2026-10-14 09:30:15',
    reportTypeCode: null, reportDescr: null, reportDate: null, reportActBy: null,
    decideCode: null, decideBy: null, decideByName: null, decideDate: null, decideActBy: null, returnReason: null, isSelfDecide: null,
    closeBy: null, closeByName: null, closeDate: null, closeActBy: null, closeDescr: null, closeResultCode: null, isSelfClose: null,
    versionNo: 1, emgStateCode: 'PENDING', ...over
  }
}

function detail(over: Partial<EmgDetail> = {}): EmgDetail {
  return { ...emg(over), affectAccts: [], ...over }
}

function affect(over: Partial<EmgAffectAcct> = {}): EmgAffectAcct {
  return {
    emgId: 'EM20261013-001', affectTypeCode: 'REPORT', acctId: 'AR20261007-001', acctName: 'sys', sysName: 'ERP',
    catgId: 'DB', affectDescr: '重設密碼', recordBy: '0000001', recordByName: 'Kevin (李凱鈞)', recordDate: '2026-10-13 11:00:00',
    ...over
  }
}

describe('緊急存取清單', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    resetSession()
    useSession().me.value = makeMe()
    listMock.mockReset().mockResolvedValue([emg(), emg({ emgId: 'EM20261012-001', emgStateCode: 'OVERDUE' })])
    getMock.mockReset().mockResolvedValue(detail())
  })

  afterEach(() => {
    vi.runAllTimers()
    vi.useRealTimers()
  })

  async function mountList(): Promise<VueWrapper> {
    const router = await makeRouter('/emergency')
    const w = mount(EmergencyListView, { global: { plugins: [router] } })
    await flushPromises()
    return w
  }

  it('預設不帶日期條件；列出單號、時間到分、狀態標籤', async () => {
    const w = await mountList()
    expect(listMock).toHaveBeenCalledWith({ keyword: '', stateCode: '' })
    const rows = w.findAll('tbody tr')
    expect(rows).toHaveLength(2)
    const cells = rows[0].findAll('td').map(c => c.text())
    expect(cells.slice(0, 7)).toEqual([
      'EM20261013-001', '2026-10-13 09:30', 'Kevin (李凱鈞)', '資料庫帳號', 'ERP 正式機 DB 的 sys 帳號', '2026-10-14 09:30', '待補核准'
    ])
    expect(rows[1].find('.tag').text()).toBe('逾期未補核准')
    expect(rows[1].find('.tag').classes()).toContain('tag-priv')
    w.unmount()
  })

  it('只有 GENERAL：有申請鈕、顯示「只顯示您送出的」；有 EMG_VIEW_ALL 無 EMG_APPLY：反之', async () => {
    let w = await mountList()
    expect(w.find('.emg-new').attributes('href')).toBe('/emergency/new')
    expect(w.find('.own-only').text()).toBe('只顯示您送出的緊急存取單')
    w.unmount()

    useSession().me.value = makeMe({ permissions: ['ACC_SELF_SERVICE', 'EMG_VIEW_ALL'] })
    w = await mountList()
    expect(w.find('.emg-new').exists()).toBe(false)
    expect(w.find('.own-only').exists()).toBe(false)
    w.unmount()
  })

  it('選近 7 天帶起迄日；自訂區間起日晚於迄日擋下', async () => {
    const w = await mountList()
    await w.find('select[aria-label=送出日期區間]').setValue('7')
    await flushPromises()
    const q = listMock.mock.calls[listMock.mock.calls.length - 1][0]
    expect(q.dateFrom).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(q.dateTo).toMatch(/^\d{4}-\d{2}-\d{2}$/)

    await w.find('select[aria-label=送出日期區間]').setValue('custom')
    await flushPromises()
    const calls = listMock.mock.calls.length
    await w.find('input[aria-label=起日]').setValue('2026-10-13')
    await w.find('input[aria-label=迄日]').setValue('2026-10-01')
    await flushPromises()
    expect(lastToast(w)).toBe('起日不可晚於迄日')
    expect(listMock.mock.calls.length).toBe(calls + 1)
    w.unmount()
  })

  it('改狀態下拉即重查並帶狀態碼', async () => {
    const w = await mountList()
    await w.find('select[aria-label=狀態]').setValue('OVERDUE')
    await flushPromises()
    expect(listMock).toHaveBeenLastCalledWith({ keyword: '', stateCode: 'OVERDUE' })
    w.unmount()
  })

  it('查詢失敗只 toast（顯示後端訊息）；成功回空陣列才顯示查無資料', async () => {
    listMock.mockRejectedValueOnce(httpError(500, { message: '資料庫連線失敗' }))
    let w = await mountList()
    expect(lastToast(w)).toBe('資料庫連線失敗')
    expect(w.find('.empty').exists()).toBe(false)
    w.unmount()

    listMock.mockResolvedValueOnce([])
    w = await mountList()
    expect(w.find('.empty').text()).toContain('查無資料')
    w.unmount()
  })

  it('「查看」開唯讀詳情彈窗', async () => {
    const w = await mountList()
    await w.findAll('tbody tr')[0].find('button.btn-link').trigger('click')
    await flushPromises()
    expect(getMock).toHaveBeenCalledWith('EM20261013-001')
    expect(w.find('.modal h3').text()).toBe('緊急存取 — EM20261013-001')
    w.unmount()
  })
})

describe('緊急存取申請', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    resetSession()
    useSession().me.value = makeMe({ permissions: [...GENERAL_PERMS] })
    vi.mocked(getCodes).mockReset().mockResolvedValue(CODES)
    submitMock.mockReset().mockResolvedValue({ emgId: 'EM20261013-002' })
  })

  afterEach(() => {
    vi.runAllTimers()
    vi.useRealTimers()
  })

  async function mountForm() {
    const router = await makeRouter('/emergency/new')
    const w = mount(EmergencyFormView, { global: { plugins: [router] } })
    await flushPromises()
    return { w, router }
  }

  async function fill(w: VueWrapper): Promise<void> {
    await w.find('#emgCatg').setValue('DB')
    await w.find('#emgTarget').setValue('  ERP 正式機 DB 的 sys 帳號（測試）  ')
    await w.find('#emgReason').setValue('S3 上線測試')
    await w.find('#emgHours').setValue('1')
  }

  it('固定說明送出即生效；類別列全部、依排序、停用加註；時長 1～24；申請人為登入者', async () => {
    const { w } = await mountForm()
    expect(w.find('.emg-notice').text()).toContain('送出即生效')
    const catg = w.findAll('#emgCatg option').map(o => o.text())
    expect(catg).toEqual(['請選擇', '主機帳號', '資料庫帳號', '服務帳號（停用）', '廠商/委外帳號', '憑證/金鑰（停用）', '機房門禁卡'])
    const hours = w.findAll('#emgHours option')
    expect(hours).toHaveLength(25)
    expect(hours[1].text()).toBe('1 小時')
    expect(hours[24].text()).toBe('24 小時')
    expect((w.find('#emgApplicant').element as HTMLInputElement).value).toBe('Kevin (李凱鈞)')
    w.unmount()
  })

  it('切換身分中：申請人顯示「{代理}（實際操作人：{實際}）」', async () => {
    useSession().me.value = makeMe({ userId: '0000009', userName: 'Amy (王小美)', impersonating: true })
    const { w } = await mountForm()
    expect((w.find('#emgApplicant').element as HTMLInputElement).value).toBe('Amy (王小美)（實際操作人：Kevin (李凱鈞)）')
    w.unmount()
  })

  it('文字欄不設 maxlength；字數按字元算（emoji 算 1），超過計數變紅', async () => {
    const { w } = await mountForm()
    expect(w.find('#emgTarget').attributes('maxlength')).toBeUndefined()
    expect(w.find('#emgReason').attributes('maxlength')).toBeUndefined()
    await w.find('#emgTarget').setValue('🚨' + 'x'.repeat(199))
    const counter = w.findAll('.counter')[0]
    expect(counter.text()).toBe('200 / 200')
    expect(counter.classes()).not.toContain('over')
    await w.find('#emgTarget').setValue('🚨'.repeat(201))
    expect(counter.text()).toBe('201 / 200')
    expect(counter.classes()).toContain('over')
    w.unmount()
  })

  it('字數不含頭尾空白：頭尾空白＋200 字仍為 200、可送出', async () => {
    const { w } = await mountForm()
    await fill(w)
    await w.find('#emgTarget').setValue('  ' + 'x'.repeat(200) + '  ')
    const counter = w.findAll('.counter')[0]
    expect(counter.text()).toBe('200 / 200')
    expect(counter.classes()).not.toContain('over')
    await w.find('button.submit').trigger('click')
    await flushPromises()
    expect(w.find('button.confirm-submit').exists()).toBe(true)
    w.unmount()
  })

  it('emoji 共 200 字可送出（與後端按字元計一致）', async () => {
    const { w } = await mountForm()
    await fill(w)
    await w.find('#emgTarget').setValue('🚨'.repeat(200))
    await w.find('button.submit').trigger('click')
    await flushPromises()
    expect(w.find('button.confirm-submit').exists()).toBe(true)
    w.unmount()
  })

  it('必填依序檢查；字數超過上限擋下', async () => {
    const { w } = await mountForm()
    const click = async () => {
      await w.find('button.submit').trigger('click')
      await flushPromises()
    }
    await click()
    expect(lastToast(w)).toBe('請填寫帳號類別')
    await w.find('#emgCatg').setValue('SVC')
    await click()
    expect(lastToast(w)).toBe('請填寫帳號或系統')
    await w.find('#emgTarget').setValue('   ')
    await click()
    expect(lastToast(w)).toBe('請填寫帳號或系統')
    await w.find('#emgTarget').setValue('x')
    await click()
    expect(lastToast(w)).toBe('請填寫緊急事由')
    await w.find('#emgReason').setValue('y')
    await click()
    expect(lastToast(w)).toBe('請填寫預估時長')
    await w.find('#emgHours').setValue('3')
    await w.find('#emgTarget').setValue('x'.repeat(201))
    await click()
    expect(lastToast(w)).toBe('帳號或系統不可超過 200 字')
    await w.find('#emgTarget').setValue('x')
    await w.find('#emgReason').setValue('y'.repeat(501))
    await click()
    expect(lastToast(w)).toBe('緊急事由不可超過 500 字')
    expect(w.find('.modal').exists()).toBe(false)
    expect(submitMock).not.toHaveBeenCalled()
    w.unmount()
  })

  it('送出先開確認框；返回修改不送出', async () => {
    const { w } = await mountForm()
    await fill(w)
    await w.find('button.submit').trigger('click')
    await flushPromises()
    expect(w.find('.modal h3').text()).toBe('確認送出緊急存取')
    expect(w.find('.confirm-text').text()).toContain('立即生效')
    expect(w.find('.confirm-text').text()).toContain('無法撤回')
    expect(w.find('.confirm-text').text()).toMatch(/期限約 \d{4}-\d{2}-\d{2} \d{2}:\d{2}/)
    const back = w.findAll('.modal .actions button').find(b => b.text() === '返回修改')
    await back!.trigger('click')
    expect(w.find('.modal').exists()).toBe(false)
    expect(submitMock).not.toHaveBeenCalled()
    w.unmount()
  })

  it('確認送出：本文去頭尾空白、時長為數字；成功 toast 單號並回清單', async () => {
    const { w, router } = await mountForm()
    await fill(w)
    await w.find('button.submit').trigger('click')
    await flushPromises()
    await w.find('button.confirm-submit').trigger('click')
    await flushPromises()
    expect(submitMock).toHaveBeenCalledWith({
      catgId: 'DB', targetDescr: 'ERP 正式機 DB 的 sys 帳號（測試）', applyReason: 'S3 上線測試', expectHourQty: 1
    })
    expect(lastToast(w)).toBe('緊急存取已送出並生效！單號 EM20261013-002，主管須於 24 小時內補核准')
    expect(router.currentRoute.value.path).toBe('/emergency')
    w.unmount()
  })

  it('送出失敗：關確認框、toast 後端訊息、留在表單', async () => {
    submitMock.mockRejectedValueOnce(httpError(400, { message: '帳號類別不存在' }))
    const { w, router } = await mountForm()
    await fill(w)
    await w.find('button.submit').trigger('click')
    await flushPromises()
    await w.find('button.confirm-submit').trigger('click')
    await flushPromises()
    expect(lastToast(w)).toBe('帳號類別不存在')
    expect(w.find('.unknown-result').exists()).toBe(false)
    expect(w.find('.modal').exists()).toBe(false)
    expect(router.currentRoute.value.path).toBe('/emergency/new')
    expect((w.find('#emgTarget').element as HTMLInputElement).value).toBe('  ERP 正式機 DB 的 sys 帳號（測試）  ')
    w.unmount()
  })

  it('送出沒有回應（逾時）：不叫人重送，改提示先到清單確認', async () => {
    submitMock.mockRejectedValueOnce(new AxiosError('timeout of 120000ms exceeded', 'ECONNABORTED'))
    const { w, router } = await mountForm()
    await fill(w)
    await w.find('button.submit').trigger('click')
    await flushPromises()
    await w.find('button.confirm-submit').trigger('click')
    await flushPromises()
    expect(lastToast(w)).toBe('無法確認是否已送出，請先到緊急存取清單確認，避免重複送出')
    expect(router.currentRoute.value.path).toBe('/emergency/new')
    // 常駐提示（toast 消失後仍在）附清單連結
    vi.runAllTimers()
    await flushPromises()
    const notice = w.find('.unknown-result')
    expect(notice.text()).toContain('無法確認是否已送出')
    expect(notice.find('a').attributes('href')).toBe('/emergency')
    w.unmount()
  })

  it('送出回 5xx（帶不帶訊息皆同）：視為結果不明、不顯示後端訊息；4xx 無訊息才退回「送出失敗」', async () => {
    let w: VueWrapper
    // 正式環境後端 500 一律帶訊息（GlobalExceptionHandler），仍須優先判斷結果不明
    for (const err of [httpError(500, {}), httpError(500, { message: '系統錯誤' }), httpError(502, undefined)]) {
      submitMock.mockRejectedValueOnce(err)
      ;({ w } = await mountForm())
      await fill(w)
      await w.find('button.submit').trigger('click')
      await flushPromises()
      await w.find('button.confirm-submit').trigger('click')
      await flushPromises()
      expect(lastToast(w)).toBe('無法確認是否已送出，請先到緊急存取清單確認，避免重複送出')
      expect(w.find('.unknown-result').exists()).toBe(true)
      w.unmount()
    }

    submitMock.mockRejectedValueOnce(httpError(403, {}))
    ;({ w } = await mountForm())
    await fill(w)
    await w.find('button.submit').trigger('click')
    await flushPromises()
    await w.find('button.confirm-submit').trigger('click')
    await flushPromises()
    expect(lastToast(w)).toBe('送出失敗，請稍後再試')
    expect(w.find('.unknown-result').exists()).toBe(false)
    w.unmount()
  })
})

describe('緊急存取 API', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('清單查詢條件空值不送；明細單號經編碼', async () => {
    const api = await vi.importActual<typeof import('../src/api/emergency')>('../src/api/emergency')
    const get = vi.spyOn(http, 'get').mockResolvedValue({ data: [] })
    await api.listEmergencies({ keyword: '', stateCode: 'PENDING', dateFrom: '', dateTo: '2026-10-13' })
    expect(get).toHaveBeenLastCalledWith('/emergencies', { params: { stateCode: 'PENDING', dateTo: '2026-10-13' } })
    await api.getEmergency('EM/1?x')
    expect(get).toHaveBeenLastCalledWith('/emergencies/EM%2F1%3Fx')
  })
})

describe('緊急存取詳情彈窗', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    getMock.mockReset()
  })

  afterEach(() => {
    vi.runAllTimers()
    vi.useRealTimers()
  })

  async function mountDetail(d: EmgDetail | Error): Promise<VueWrapper> {
    if (d instanceof Error) getMock.mockRejectedValueOnce(d)
    else getMock.mockResolvedValueOnce(d)
    const w = mount(EmergencyDetailModal, { props: { emgId: 'EM20261013-001' } })
    await flushPromises()
    return w
  }

  it('待補核准：申請內容齊全、尚未回報、尚未處理，沒有異常結案段與受影響帳號', async () => {
    const w = await mountDetail(detail())
    expect(w.find('.sub').text()).toBe('狀態：待補核准')
    expect(w.text()).toContain('月結批次卡住')
    expect(w.find('.sec-report').text()).toBe('尚未回報')
    expect(w.find('.sec-decide').text()).toBe('尚未處理')
    expect(w.find('.sec-close').exists()).toBe(false)
    expect(w.find('.affect-tbl').exists()).toBe(false)
    w.unmount()
  })

  it('已補核准：回報內容、自核與逾期後處理標記；受影響帳號列表', async () => {
    const w = await mountDetail(detail({
      emgStateCode: 'APPROVED', reportTypeCode: 'MODIFIED', reportDescr: '重設 sys 密碼', reportDate: '2026-10-13 11:00:00',
      decideCode: 'APPROVE', decideBy: '0000001', decideByName: 'Kevin (李凱鈞)', decideDate: '2026-10-14 10:00:00', isSelfDecide: 1,
      affectAccts: [affect()]
    }))
    expect(w.find('.sub').text()).toBe('狀態：已補核准')
    expect(w.find('.sec-report').text()).toContain('異動了既有帳號：重設 sys 密碼')
    expect(w.find('.sec-decide').text()).toContain('補核准')
    expect(w.find('.self-decide').exists()).toBe(true)
    expect(w.find('.late').text()).toBe('逾期後處理')
    expect(w.find('.sec-close').exists()).toBe(false)
    const cells = w.findAll('.affect-tbl tbody td').map(c => c.text())
    expect(cells.slice(0, 4)).toEqual(['申請人回報', 'sys', 'ERP', '重設密碼'])
    w.unmount()
  })

  it('期限內處理無逾期標記；非自核無自核標記', async () => {
    const w = await mountDetail(detail({
      emgStateCode: 'APPROVED', decideCode: 'APPROVE', decideBy: '0000009', decideByName: '主管', decideDate: '2026-10-13 15:00:00', isSelfDecide: 0
    }))
    expect(w.find('.self-decide').exists()).toBe(false)
    expect(w.find('.late').exists()).toBe(false)
    w.unmount()
  })

  it('已退回：出現異常結案段（尚未結案）；已結案顯示結果與自行結案標記', async () => {
    let w = await mountDetail(detail({
      emgStateCode: 'RETURNED', decideCode: 'RETURN', decideBy: '0000009', decideByName: '主管', decideDate: '2026-10-13 15:00:00',
      returnReason: '事由不足', isSelfDecide: 0
    }))
    expect(w.find('.sec-decide').text()).toContain('退回：事由不足')
    expect(w.find('.sec-close').text()).toBe('尚未結案')
    w.unmount()

    w = await mountDetail(detail({
      emgStateCode: 'CLOSED', decideCode: 'RETURN', decideBy: '0000009', decideByName: '主管', decideDate: '2026-10-13 15:00:00',
      returnReason: '事由不足', isSelfDecide: 0,
      closeBy: '0000009', closeByName: '主管', closeDate: '2026-10-13 16:00:00', closeResultCode: 'NO_CHANGE', closeDescr: '已查核', isSelfClose: 1
    }))
    expect(w.find('.sub').text()).toBe('狀態：已退回（已結案）')
    expect(w.find('.sec-close').text()).toContain('確認沒有帳號異動：已查核')
    expect(w.find('.self-close').exists()).toBe(true)
    w.unmount()
  })

  it('affectAccts 不是陣列視同空；載入失敗 toast 並顯示無法載入', async () => {
    let w = await mountDetail({ ...detail(), affectAccts: null as unknown as EmgAffectAcct[] })
    expect(w.find('.affect-tbl').exists()).toBe(false)
    w.unmount()

    w = await mountDetail(httpError(404, { message: '查無此緊急存取單' }))
    expect(w.text()).toContain('無法載入此緊急存取單')
    w.unmount()
  })
})
