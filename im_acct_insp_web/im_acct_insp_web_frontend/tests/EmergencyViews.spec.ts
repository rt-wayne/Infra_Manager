// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: 新增：緊急存取前端測試（BACKLOG 第 46 項 S3）
//           清單：預設不帶日期條件、狀態標籤、申請鈕依 EMG_APPLY、「只顯示您送出的」依 EMG_VIEW_ALL、失敗 toast、空結果、查看開彈窗；
//           申請：停用類別加註、必填與字數檢查、確認框、送出本文、成功回清單、失敗留在表單；
//           詳情：尚未回報／尚未處理、退回才有異常結案段、自核與逾期後處理標記、受影響帳號列表
//           S3 審查：沒有回應或 5xx 時提示先查清單（toast＋常駐提示）；不設 maxlength、字數按字元且不含頭尾空白；
//           切換中申請人文字；狀態下拉重查；API 空值不送
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S5 回合 1：四個節點的實際操作人文字（相同不顯示、不同顯示「{代理}（實際操作人：{實際}）」）；
//           退回／補核准按鈕只對 EMG_DECIDE 且尚未處理顯示、尚未回報時補核准停用並提示；
//           補核准確認框、退回原因必填與 500 字上限；成功 toast＋重載明細＋emit changed；409 關子彈窗並重載；
//           清單收到 changed 重查；API：report／approve／return 路徑與本文
//           S5 審查：退回原因字數按碼點（501 個 emoji 擋、500 個可送）；退回 409 同樣關原因框並重載；
//           子彈窗取消只關子彈窗、不 emit close；送出中主、子彈窗的關閉都被忽略
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S6 回合 2：mock 補 closeEmergency；退回成功重載後出現異常結案鈕、退回鈕消失
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S8：清單讀網址 ?state=（合法值當預設篩選、首查即帶、日期維持全部；不合法視為全部，皆只查一次）；
//           PENDING 文字改「待補核准（未逾期）」
// AI版本  : Claude Fable 5 (claude-fable-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S9 審查第 3(b) 項：釘住 listEmgReturned 實際請求（/emergencies、stateCode=RETURNED）；
//           mock 工廠補 listEmgReturned
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
import { approveEmergency, getEmergency, listEmergencies, returnEmergency, submitEmergency } from '../src/api/emergency'
import { resetSession, useSession } from '../src/composables/useSession'
import { useToast } from '../src/composables/useToast'
import type { Emg, EmgAffectAcct, EmgDetail } from '../src/types/emergency'
import { CODES, GENERAL_PERMS, httpError, lastToast, makeMe, makeRouter } from './helpers'

/** 單獨掛載彈窗時沒有 ToastHost，直接讀 toast 狀態的最後一則 */
function lastToastMsg(): string {
  const list = useToast().toasts.value
  return list.length ? list[list.length - 1].msg : ''
}

vi.mock('../src/api/common', () => ({
  getCodes: vi.fn(),
  getHomeStats: vi.fn(),
  listExpiring: vi.fn(),
  listTodos: vi.fn(),
  listEmgReturned: vi.fn().mockResolvedValue([])
}))
vi.mock('../src/api/emergency', () => ({
  submitEmergency: vi.fn(),
  listEmergencies: vi.fn(),
  getEmergency: vi.fn(),
  reportEmergency: vi.fn(),
  approveEmergency: vi.fn(),
  returnEmergency: vi.fn(),
  closeEmergency: vi.fn()
}))

const listMock = vi.mocked(listEmergencies)
const getMock = vi.mocked(getEmergency)
const submitMock = vi.mocked(submitEmergency)
const approveMock = vi.mocked(approveEmergency)
const returnMock = vi.mocked(returnEmergency)

function emg(over: Partial<Emg> = {}): Emg {
  return {
    emgId: 'EM20261013-001', catgId: 'DB', catgName: '資料庫帳號', targetDescr: 'ERP 正式機 DB 的 sys 帳號',
    applyBy: '0000001', applyByName: 'Kevin (李凱鈞)', createBy: '0000001', createByName: 'Kevin (李凱鈞)',
    applyReason: '月結批次卡住\n需人工處理', expectHourQty: 2,
    submitDate: '2026-10-13 09:30:15', dueDate: '2026-10-14 09:30:15',
    reportTypeCode: null, reportDescr: null, reportDate: null, reportActBy: null, reportActByName: null,
    decideCode: null, decideBy: null, decideByName: null, decideDate: null, decideActBy: null, decideActByName: null,
    returnReason: null, isSelfDecide: null,
    closeBy: null, closeByName: null, closeDate: null, closeActBy: null, closeActByName: null,
    closeDescr: null, closeResultCode: null, isSelfClose: null,
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

  async function mountList(path = '/emergency'): Promise<VueWrapper> {
    const router = await makeRouter(path)
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
      'EM20261013-001', '2026-10-13 09:30', 'Kevin (李凱鈞)', '資料庫帳號', 'ERP 正式機 DB 的 sys 帳號', '2026-10-14 09:30', '待補核准（未逾期）'
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

  it('網址帶 state（首頁卡導入）：下拉預設該值、首查即帶狀態碼且不帶日期；下拉有「未處理（含逾期）」', async () => {
    for (const [state, text] of [['UNDECIDED', '未處理（含逾期）'], ['RETURNED', '已退回']]) {
      listMock.mockClear()
      const w = await mountList(`/emergency?state=${state}`)
      expect(listMock).toHaveBeenCalledTimes(1)
      expect(listMock).toHaveBeenCalledWith({ keyword: '', stateCode: state })
      const sel = w.find('select[aria-label=狀態]').element as HTMLSelectElement
      expect(sel.value).toBe(state)
      expect(sel.selectedOptions[0].text).toBe(text)
      expect((w.find('select[aria-label=送出日期區間]').element as HTMLSelectElement).value).toBe('all')
      w.unmount()
    }
  })

  it('網址 state 不是下拉有的值（含重複參數）：視為全部狀態', async () => {
    for (const path of ['/emergency?state=DONE', '/emergency?state=undecided', '/emergency?state=UNDECIDED&state=RETURNED']) {
      listMock.mockClear()
      const w = await mountList(path)
      expect(listMock).toHaveBeenCalledTimes(1)
      expect(listMock).toHaveBeenCalledWith({ keyword: '', stateCode: '' })
      expect((w.find('select[aria-label=狀態]').element as HTMLSelectElement).value).toBe('')
      w.unmount()
    }
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

  it('鈴鐺類別 3 查詢（S9）：固定帶 stateCode=RETURNED', async () => {
    const api = await vi.importActual<typeof import('../src/api/common')>('../src/api/common')
    const get = vi.spyOn(http, 'get').mockResolvedValue({ data: [] })
    await api.listEmgReturned()
    expect(get).toHaveBeenLastCalledWith('/emergencies', { params: { stateCode: 'RETURNED' } })
  })

  it('回報、補核准、退回：路徑帶編碼單號，本文帶版本號', async () => {
    const api = await vi.importActual<typeof import('../src/api/emergency')>('../src/api/emergency')
    const post = vi.spyOn(http, 'post').mockResolvedValue({ data: { emgId: 'EM/1', versionNo: 2 } })
    await api.approveEmergency('EM/1', 1)
    expect(post).toHaveBeenLastCalledWith('/emergencies/EM%2F1/approve', { versionNo: 1 })
    await api.returnEmergency('EM/1', 1, '事由不足')
    expect(post).toHaveBeenLastCalledWith('/emergencies/EM%2F1/return', { versionNo: 1, reason: '事由不足' })
    const payload = { reportTypeCode: 'NONE' as const, reportDescr: '', versionNo: 1, affectAccts: [] }
    await expect(api.reportEmergency('EM/1', payload)).resolves.toEqual({ emgId: 'EM/1', versionNo: 2 })
    expect(post).toHaveBeenLastCalledWith('/emergencies/EM%2F1/report', payload)
  })
})

describe('緊急存取詳情彈窗', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    resetSession()
    useSession().me.value = makeMe()
    useToast().toasts.value = []
    getMock.mockReset()
    approveMock.mockReset()
    returnMock.mockReset()
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
    expect(w.find('.sub').text()).toBe('狀態：待補核准（未逾期）')
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

  it('實際操作人與有效身分相同（或舊資料沒有）時不顯示；不同時四個節點都顯示「{代理}（實際操作人：{實際}）」', async () => {
    let w = await mountDetail(detail({
      emgStateCode: 'CLOSED', reportTypeCode: 'NONE', reportDate: '2026-10-13 11:00:00', reportActBy: '0000001', reportActByName: 'Kevin (李凱鈞)',
      decideCode: 'RETURN', decideBy: '0000009', decideByName: '主管', decideDate: '2026-10-13 15:00:00', decideActBy: null, decideActByName: null,
      returnReason: '事由不足', isSelfDecide: 0,
      closeBy: '0000009', closeByName: '主管', closeDate: '2026-10-13 16:00:00', closeActBy: '0000009', closeActByName: '主管', closeResultCode: 'NO_CHANGE', isSelfClose: 1
    }))
    expect(w.find('.applicant').text()).toBe('Kevin (李凱鈞)')
    expect(w.find('.report-actor').exists()).toBe(false)
    expect(w.find('.decide-actor').text()).toBe('主管')
    expect(w.find('.close-actor').text()).toBe('主管')
    expect(w.text()).not.toContain('實際操作人')
    w.unmount()

    w = await mountDetail(detail({
      emgStateCode: 'CLOSED', createBy: '0000002', createByName: 'Hank (王漢克)',
      reportTypeCode: 'NONE', reportDate: '2026-10-13 11:00:00', reportActBy: '0000002', reportActByName: 'Hank (王漢克)',
      decideCode: 'RETURN', decideBy: '0000009', decideByName: '主管', decideDate: '2026-10-13 15:00:00', decideActBy: '0000003', decideActByName: 'Terry (陳泰瑞)',
      returnReason: '事由不足', isSelfDecide: 0,
      closeBy: '0000009', closeByName: '主管', closeDate: '2026-10-13 16:00:00', closeActBy: '0000004', closeActByName: null, closeResultCode: 'NO_CHANGE', isSelfClose: 0
    }))
    expect(w.find('.applicant').text()).toBe('Kevin (李凱鈞)（實際操作人：Hank (王漢克)）')
    expect(w.find('.report-actor').text()).toContain('Kevin (李凱鈞)（實際操作人：Hank (王漢克)）')
    expect(w.find('.decide-actor').text()).toBe('主管（實際操作人：Terry (陳泰瑞)）')
    // 實際操作人查不到姓名時退回顯示工號
    expect(w.find('.close-actor').text()).toBe('主管（實際操作人：0000004）')
    w.unmount()
  })

  it('沒有 EMG_DECIDE 或已處理：沒有退回／補核准按鈕；有 EMG_DECIDE 且尚未處理：兩鈕出現，尚未回報時補核准停用並提示', async () => {
    let w = await mountDetail(detail())
    expect(w.find('.act-return').exists()).toBe(false)
    expect(w.find('.act-approve').exists()).toBe(false)
    expect(w.find('.approve-hint').exists()).toBe(false)
    w.unmount()

    useSession().me.value = makeMe({ permissions: ['EMG_DECIDE', 'EMG_VIEW_ALL'] })
    w = await mountDetail(detail({ emgStateCode: 'APPROVED', decideCode: 'APPROVE', decideBy: '0000009', decideByName: '主管', decideDate: '2026-10-13 15:00:00', isSelfDecide: 0 }))
    expect(w.find('.act-return').exists()).toBe(false)
    expect(w.find('.act-approve').exists()).toBe(false)
    w.unmount()

    w = await mountDetail(detail({ emgStateCode: 'OVERDUE' }))
    expect(w.find('.act-return').attributes('disabled')).toBeUndefined()
    expect(w.find('.act-approve').attributes('disabled')).toBeDefined()
    expect(w.find('.approve-hint').text()).toContain('申請人尚未回報')
    w.unmount()

    w = await mountDetail(detail({ reportTypeCode: 'NONE', reportDate: '2026-10-13 11:00:00', reportActBy: '0000001' }))
    expect(w.find('.act-approve').attributes('disabled')).toBeUndefined()
    expect(w.find('.approve-hint').exists()).toBe(false)
    w.unmount()
  })

  it('補核准：確認框說明有異動時會寫歷史；確認後送單號與版本號，成功 toast、重載明細、emit changed', async () => {
    useSession().me.value = makeMe({ permissions: ['EMG_DECIDE'] })
    const before = detail({ reportTypeCode: 'MODIFIED', reportDescr: '重設密碼', reportDate: '2026-10-13 11:00:00', reportActBy: '0000001', versionNo: 1 })
    const after = detail({ ...before, emgStateCode: 'APPROVED', decideCode: 'APPROVE', decideBy: '0000001', decideByName: 'Kevin (李凱鈞)', decideDate: '2026-10-13 12:00:00', isSelfDecide: 1, versionNo: 2 })
    getMock.mockResolvedValueOnce(before).mockResolvedValueOnce(after)
    approveMock.mockResolvedValue({ emgId: 'EM20261013-001', versionNo: 2 })
    const w = mount(EmergencyDetailModal, { props: { emgId: 'EM20261013-001' } })
    await flushPromises()

    await w.find('.act-approve').trigger('click')
    expect(w.find('.confirm-approve').exists()).toBe(true)
    expect(w.text()).toContain('將寫入各帳號的歷史紀錄')
    await w.find('.confirm-approve').trigger('click')
    await flushPromises()

    expect(approveMock).toHaveBeenCalledWith('EM20261013-001', 1)
    expect(lastToastMsg()).toContain('EM20261013-001 已補核准')
    expect(w.find('.confirm-approve').exists()).toBe(false)
    expect(getMock).toHaveBeenCalledTimes(2)
    expect(w.find('.sub').text()).toBe('狀態：已補核准')
    expect(w.find('.self-decide').exists()).toBe(true)
    expect(w.find('.act-approve').exists()).toBe(false)
    expect(w.emitted('changed')).toHaveLength(1)
    w.unmount()
  })

  it('退回：原因空白或超過 500 字擋下不送；成功送去頭尾空白的原因、toast、重載、emit changed', async () => {
    useSession().me.value = makeMe({ permissions: ['EMG_DECIDE'] })
    const before = detail({ versionNo: 3 })
    const after = detail({ ...before, emgStateCode: 'RETURNED', decideCode: 'RETURN', decideBy: '0000001', decideByName: 'Kevin (李凱鈞)', decideDate: '2026-10-13 12:00:00', returnReason: '事由不足', isSelfDecide: 1, versionNo: 4 })
    getMock.mockResolvedValueOnce(before).mockResolvedValueOnce(after)
    returnMock.mockResolvedValue({ emgId: 'EM20261013-001', versionNo: 4 })
    const w = mount(EmergencyDetailModal, { props: { emgId: 'EM20261013-001' } })
    await flushPromises()

    await w.find('.act-return').trigger('click')
    expect(w.find('.confirm-return').exists()).toBe(true)
    await w.find('.confirm-return').trigger('click')
    expect(lastToastMsg()).toBe('請填寫退回原因')
    expect(returnMock).not.toHaveBeenCalled()

    await w.find('#emg-return-reason').setValue('長'.repeat(501))
    await w.find('.confirm-return').trigger('click')
    expect(lastToastMsg()).toContain('不可超過 500 字')
    // 字數按碼點算（與後端 codePointCount 一致）：501 個 emoji 擋下、500 個可送
    await w.find('#emg-return-reason').setValue('😀'.repeat(501))
    await w.find('.confirm-return').trigger('click')
    expect(lastToastMsg()).toContain('不可超過 500 字')
    expect(returnMock).not.toHaveBeenCalled()

    returnMock.mockRejectedValueOnce(httpError(500, { message: '資料寫入未完成' }))
    await w.find('#emg-return-reason').setValue('😀'.repeat(500))
    await w.find('.confirm-return').trigger('click')
    await flushPromises()
    expect(returnMock).toHaveBeenCalledWith('EM20261013-001', 3, '😀'.repeat(500))
    expect(w.find('.confirm-return').exists()).toBe(true)

    await w.find('#emg-return-reason').setValue('  事由不足  ')
    await w.find('.confirm-return').trigger('click')
    await flushPromises()
    expect(returnMock).toHaveBeenLastCalledWith('EM20261013-001', 3, '事由不足')
    expect(lastToastMsg()).toContain('EM20261013-001 已退回')
    expect(w.find('.confirm-return').exists()).toBe(false)
    expect(w.find('.sec-decide').text()).toContain('退回：事由不足')
    expect(w.find('.sec-close').text()).toBe('尚未結案')
    // 退回後重載：退回／補核准鈕消失，改出現異常結案鈕（S6）
    expect(w.find('.act-return').exists()).toBe(false)
    expect(w.find('.act-approve').exists()).toBe(false)
    expect(w.find('.act-close-case').exists()).toBe(true)
    expect(w.emitted('changed')).toHaveLength(1)
    w.unmount()
  })

  it('處理失敗：一般錯誤只 toast、子彈窗留著；409 關子彈窗、重載明細並 emit changed', async () => {
    useSession().me.value = makeMe({ permissions: ['EMG_DECIDE'] })
    const before = detail({ reportTypeCode: 'NONE', reportDate: '2026-10-13 11:00:00', reportActBy: '0000001' })
    getMock.mockResolvedValue(before)
    approveMock.mockRejectedValueOnce(httpError(500, { message: '資料寫入未完成' }))
    const w = mount(EmergencyDetailModal, { props: { emgId: 'EM20261013-001' } })
    await flushPromises()

    await w.find('.act-approve').trigger('click')
    await w.find('.confirm-approve').trigger('click')
    await flushPromises()
    expect(lastToastMsg()).toBe('資料寫入未完成')
    expect(w.find('.confirm-approve').exists()).toBe(true)
    expect(getMock).toHaveBeenCalledTimes(1)
    expect(w.emitted('changed')).toBeUndefined()

    approveMock.mockRejectedValueOnce(httpError(409, { code: 'E08', message: '此申請已由他人處理，請重新整理' }))
    await w.find('.confirm-approve').trigger('click')
    await flushPromises()
    expect(lastToastMsg()).toBe('此申請已由他人處理，請重新整理')
    expect(w.find('.confirm-approve').exists()).toBe(false)
    expect(getMock).toHaveBeenCalledTimes(2)
    expect(w.emitted('changed')).toHaveLength(1)

    // 退回走同一條 run()：409 同樣關原因框、重載、emit changed
    returnMock.mockRejectedValueOnce(httpError(409, { code: 'E08', message: '此申請已由他人處理，請重新整理' }))
    await w.find('.act-return').trigger('click')
    await w.find('#emg-return-reason').setValue('事由不足')
    await w.find('.confirm-return').trigger('click')
    await flushPromises()
    expect(w.find('.confirm-return').exists()).toBe(false)
    expect(getMock).toHaveBeenCalledTimes(3)
    expect(w.emitted('changed')).toHaveLength(2)
    w.unmount()
  })

  it('子彈窗按取消只關子彈窗：主彈窗還在、不 emit close；送出中主、子彈窗的關閉都被忽略', async () => {
    useSession().me.value = makeMe({ permissions: ['EMG_DECIDE'] })
    getMock.mockResolvedValue(detail({ reportTypeCode: 'NONE', reportDate: '2026-10-13 11:00:00', reportActBy: '0000001' }))
    const w = mount(EmergencyDetailModal, { props: { emgId: 'EM20261013-001' } })
    await flushPromises()

    await w.find('.act-approve').trigger('click')
    const cancelOf = (sel: string) => w.findAll('.modal').find(m => m.find(sel).exists())!.findAll('.actions .btn').at(-1)!
    await cancelOf('.confirm-approve').trigger('click')
    expect(w.find('.confirm-approve').exists()).toBe(false)
    expect(w.find('.act-approve').exists()).toBe(true)
    expect(w.emitted('close')).toBeUndefined()

    await w.find('.act-return').trigger('click')
    await cancelOf('.confirm-return').trigger('click')
    expect(w.find('.confirm-return').exists()).toBe(false)
    expect(w.find('.act-return').exists()).toBe(true)
    expect(w.emitted('close')).toBeUndefined()

    // 請求懸著：取消子彈窗、關主彈窗都無效；請求回來後才能關
    let settle: (v: { emgId: string; versionNo: number }) => void = () => undefined
    approveMock.mockReturnValueOnce(new Promise(resolve => { settle = resolve }))
    await w.find('.act-approve').trigger('click')
    await w.find('.confirm-approve').trigger('click')
    await cancelOf('.confirm-approve').trigger('click')
    expect(w.find('.confirm-approve').exists()).toBe(true)
    await w.findAll('.modal')[0].findAll('.actions .btn').at(-1)!.trigger('click')
    expect(w.emitted('close')).toBeUndefined()
    // 點遮罩與按關閉鈕走同一個 close 事件，送出中同樣被忽略
    await w.findAll('.modal-overlay')[0].trigger('click')
    expect(w.emitted('close')).toBeUndefined()
    await w.findAll('.modal-overlay').at(-1)!.trigger('click')
    expect(w.find('.confirm-approve').exists()).toBe(true)

    settle({ emgId: 'EM20261013-001', versionNo: 2 })
    await flushPromises()
    expect(w.find('.confirm-approve').exists()).toBe(false)
    expect(w.emitted('changed')).toHaveLength(1)
    await w.findAll('.modal')[0].findAll('.actions .btn').at(-1)!.trigger('click')
    expect(w.emitted('close')).toHaveLength(1)
    w.unmount()
  })
})

describe('緊急存取清單 × 詳情彈窗', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    resetSession()
    useSession().me.value = makeMe({ permissions: ['EMG_DECIDE', 'EMG_VIEW_ALL'] })
    listMock.mockReset().mockResolvedValue([emg()])
    getMock.mockReset().mockResolvedValue(detail({ reportTypeCode: 'NONE', reportDate: '2026-10-13 11:00:00', reportActBy: '0000001' }))
    approveMock.mockReset().mockResolvedValue({ emgId: 'EM20261013-001', versionNo: 2 })
  })

  afterEach(() => {
    vi.runAllTimers()
    vi.useRealTimers()
  })

  it('詳情內補核准成功後清單重查', async () => {
    const router = await makeRouter('/emergency')
    const w = mount(EmergencyListView, { global: { plugins: [router] } })
    await flushPromises()
    expect(listMock).toHaveBeenCalledTimes(1)

    await w.find('tbody tr button').trigger('click')
    await flushPromises()
    await w.find('.act-approve').trigger('click')
    await w.find('.confirm-approve').trigger('click')
    await flushPromises()
    expect(approveMock).toHaveBeenCalledTimes(1)
    expect(listMock).toHaveBeenCalledTimes(2)
    w.unmount()
  })
})
