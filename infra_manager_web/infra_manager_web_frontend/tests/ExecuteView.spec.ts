// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：填寫執行紀錄頁測試（S10 R3）；以 vi.mock 替換 apps API，不打真後端
//           驗：檢核表還沒展開時用 CHECK_LIST 選項畫列（依排序、序號從 1 起）、第 5～8 項接作業步驟；
//           暫存不帶結果、已存的完成時間與執行人原樣帶回；勾選填當下時間、取消清空；「填入我自己」帶登入者工號；
//           送審要選結果並 confirm、成功回檢視頁；退回意見必填並 confirm；409 toast 後重載；沒有 canExecute 只顯示說明；
//           取消勾選例外時已有說明要先確認
// ============================================================
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { AxiosError, AxiosHeaders } from 'axios'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import ExecuteView from '../src/views/ExecuteView.vue'
import { getApp, getFormOptions, rejectExecution, saveExecution } from '../src/api/apps'
import { resetAuthStateForTest, useAuth } from '../src/composables/useAuth'
import { useToast } from '../src/composables/useToast'
import { APP_EXECUTE_ROUTE, APP_VIEW_ROUTE } from '../src/router/names'
import type { AppCheckItem, AppDetail, AppPermissions, FormOption, FormOptionsResponse } from '../src/types/app'

vi.mock('../src/api/apps', async importOriginal => ({
  ...(await importOriginal<typeof import('../src/api/apps')>()),
  getApp: vi.fn(),
  getFormOptions: vi.fn(),
  saveExecution: vi.fn(),
  rejectExecution: vi.fn()
}))

const getMock = vi.mocked(getApp)
const optionsMock = vi.mocked(getFormOptions)
const saveMock = vi.mocked(saveExecution)
const rejectMock = vi.mocked(rejectExecution)
const Blank = { template: '<div />' }
const APP_ID = 'IM20261007-001'

function makeRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/apps/:id', name: APP_VIEW_ROUTE, component: Blank },
      { path: '/apps/:id/execute', name: APP_EXECUTE_ROUTE, component: ExecuteView }
    ]
  })
}

function httpError(status: number, message: string): AxiosError {
  const err = new AxiosError(message, String(status))
  err.response = { status, statusText: '', headers: {}, config: { headers: new AxiosHeaders() }, data: { message } }
  return err
}

const NO_PERMS: AppPermissions = {
  canDecide: false, canResubmit: false, canRecall: false, canExecute: false, canReview: false,
  canDelete: false, canAiReview: false, canSubmit: false, canEditDraft: false, deleteMode: null
}

const EXEC_PERMS: AppPermissions = { ...NO_PERMS, canExecute: true }

function opt(id: number, groupCode: string, code: string, name: string, sortNo: number): FormOption {
  return {
    formOptionId: id, groupCode, code, name, upFormOptionId: null, colorCode: null, desc: null,
    timeLimitDesc: null, prioFlowDesc: null, sampleDesc: null, flowId: null, sortNo
  }
}

/** 9 個檢核項故意亂序給，驗前端依 sortNo 排；另給兩個執行結果 */
function formOptions(): FormOptionsResponse {
  const checks = [9, 3, 1, 2, 4, 5, 6, 7, 8].map(n => opt(100 + n, 'CHECK_LIST', 'C' + n, '檢核' + n, n * 10))
  return {
    options: [...checks, opt(201, 'EXEC_RESULT', 'FAIL', '失敗', 20), opt(200, 'EXEC_RESULT', 'OK', '成功', 10)],
    upload: { maxMb: 50, maxFiles: 10 }
  }
}

function check(seqNo: number, over: Partial<AppCheckItem> = {}): AppCheckItem {
  return { seqNo, code: 'C' + seqNo, name: '檢核' + seqNo, done: false, doneAt: null, executor: null, userId: null, executorDesc: null, ...over }
}

function detail(over: Partial<AppDetail> = {}): AppDetail {
  return {
    appId: APP_ID,
    title: '更換交換器',
    prioCode: 'P2', prioName: '高', prioColor: '#d9880f',
    statusCode: 'APPROVED',
    sourceCode: 'ONLINE',
    flowId: 'F1', flowName: '一般流程',
    verNo: 1,
    rowVerNo: 5,
    applicant: { userId: 'T0001', name: '王小明', deptName: '資訊部', tel: '1234', email: 'a@example.com' },
    applyDate: '2026-10-07',
    selfExec: true, supplierExec: false, workModeCode: 'ONSITE', remoteMethod: null,
    supplier: { name: null, contact: null, tel: null, headCount: null },
    workSubject: '核心交換器汰換', impactDesc: null, workDetail: null, riskDesc: null, rollbackPlan: null,
    categories: [], reasons: [], otherReason: null, scopes: [], equipments: [],
    planSteps: [
      { seqNo: 1, text: '關閉舊機' },
      { seqNo: 2, text: '上架新機' },
      { seqNo: 3, text: '' },
      { seqNo: 4, text: '測試連線' }
    ],
    schedule: { start: '2026-10-07 22:00', end: '2026-10-08 02:00', estHours: 4 },
    location: { sourceCode: null, areaName: null, rackName: null, uRange: null, siteId: null, rackId: null, uStart: null, uEnd: null, omitReason: '遠端作業' },
    resubmitMemo: null,
    checklist: [],
    execution: null,
    approval: { apprId: 5, statusCode: 'APPROVED', startedAt: null, closedAt: null, steps: [] },
    approvalHistory: [],
    attachments: [],
    versions: [],
    events: [],
    permissions: EXEC_PERMS,
    createdAt: '2026-10-07 09:30',
    updatedAt: '2026-10-07 10:00',
    ...over
  }
}

/** 已展開過的檢核表：第 1 項系統使用者完成、第 2 項手填文字完成、其餘未完成 */
function savedDetail(): AppDetail {
  return detail({
    statusCode: 'IN_EXECUTION',
    checklist: [
      check(1, { done: true, doneAt: '2026-10-07 22:10', executor: '王小明', userId: 'T0001' }),
      check(2, { done: true, doneAt: '2026-10-07 22:20', executor: '門市EDP、廠商', executorDesc: '門市EDP、廠商' }),
      check(3)
    ],
    execution: {
      verNo: 1, actualStart: '2026-10-07 22:00', actualEnd: null, resultCode: null, resultName: null,
      exception: true, exceptionDesc: '風扇異音', followUp: false, followUpDesc: null, memo: '先暫停', executorName: '王小明', closedAt: null
    }
  })
}

async function mountView() {
  const router = makeRouter()
  await router.push(`/apps/${APP_ID}/execute`)
  await router.isReady()
  const wrapper = mount(ExecuteView, { global: { plugins: [router] } })
  await flushPromises()
  return { wrapper, router }
}

type Wrapper = Awaited<ReturnType<typeof mountView>>['wrapper']

function rowsOf(wrapper: Wrapper) {
  return wrapper.findAll('table tbody tr')
}

function button(wrapper: Wrapper, text: string) {
  return wrapper.findAll('button').find(b => b.text().includes(text))
}

function toastMsgs(): string[] {
  return useToast().toasts.value.map(t => t.msg)
}

describe('ExecuteView', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    getMock.mockReset()
    optionsMock.mockReset()
    saveMock.mockReset()
    rejectMock.mockReset()
    optionsMock.mockResolvedValue(formOptions())
    useAuth().me.value = { loggedIn: true, userId: 'T0002', userName: '李機房', roles: ['idc_admin'] }
  })

  afterEach(() => {
    vi.runAllTimers()
    vi.useRealTimers()
    resetAuthStateForTest()
  })

  it('檢核表還沒展開：用 CHECK_LIST 選項依排序畫列，第 5～8 項接作業步驟（步驟空白只顯示名稱）', async () => {
    getMock.mockResolvedValue(detail())
    const { wrapper } = await mountView()

    const rows = rowsOf(wrapper)
    expect(rows).toHaveLength(9)
    expect(rows.map(r => r.findAll('td')[0].text())).toEqual(['1', '2', '3', '4', '5', '6', '7', '8', '9'])
    expect(rows[0].text()).toContain('檢核1')
    expect(rows[3].text()).not.toContain('：')
    expect(rows[4].text()).toContain('檢核5：關閉舊機')
    expect(rows[5].text()).toContain('檢核6：上架新機')
    expect(rows[6].text()).toContain('檢核7')
    expect(rows[6].text()).not.toContain('檢核7：')
    expect(rows[7].text()).toContain('檢核8：測試連線')
    expect(rows[8].text()).not.toContain('：')

    const results = wrapper.findAll('#result option').map(o => o.text())
    expect(results).toEqual(['— 請選擇 —', '成功', '失敗'])
    wrapper.unmount()
  })

  it('暫存：不帶結果，已存的完成時間、工號、手填執行人與執行紀錄原樣帶回；成功 toast 並重新載入', async () => {
    getMock.mockResolvedValue(savedDetail())
    saveMock.mockResolvedValue({ appId: APP_ID, rowVerNo: 6, statusCode: 'IN_EXECUTION' })
    const { wrapper } = await mountView()

    expect(rowsOf(wrapper)[0].text()).toContain('王小明')
    await wrapper.find('#result').setValue('OK')
    await button(wrapper, '暫存')?.trigger('click')
    await flushPromises()

    expect(saveMock).toHaveBeenCalledWith(APP_ID, {
      rowVerNo: 5,
      checklist: [
        { seqNo: 1, done: true, doneAt: '2026-10-07T22:10', userId: 'T0001', executorDesc: null },
        { seqNo: 2, done: true, doneAt: '2026-10-07T22:20', userId: null, executorDesc: '門市EDP、廠商' },
        { seqNo: 3, done: false, doneAt: null, userId: null, executorDesc: null }
      ],
      actualStart: '2026-10-07T22:00',
      actualEnd: null,
      resultCode: null,
      exception: true,
      exceptionDesc: '風扇異音',
      followUp: false,
      followUpDesc: null,
      memo: '先暫停'
    })
    expect(toastMsgs()).toContain('已暫存')
    expect(getMock).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('勾選完成填入當下時間、取消勾選清空；「填入我自己」帶登入者工號，「改填文字」換回輸入框', async () => {
    getMock.mockResolvedValue(savedDetail())
    saveMock.mockResolvedValue({ appId: APP_ID, rowVerNo: 6, statusCode: 'IN_EXECUTION' })
    const { wrapper } = await mountView()

    const third = () => rowsOf(wrapper)[2]
    await third().find('input[type="checkbox"]').setValue(true)
    const at = (third().find('input[type="datetime-local"]').element as HTMLInputElement).value
    expect(at).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/)
    await third().findAll('button').find(b => b.text() === '填入我自己')?.trigger('click')
    expect(third().text()).toContain('李機房')

    await rowsOf(wrapper)[0].find('input[type="checkbox"]').setValue(false)
    await rowsOf(wrapper)[0].findAll('button').find(b => b.text() === '改填文字')?.trigger('click')
    await rowsOf(wrapper)[0].find('input[type="text"]').setValue('  廠商陳先生  ')

    await button(wrapper, '暫存')?.trigger('click')
    await flushPromises()
    const body = saveMock.mock.calls[0][1]
    expect(body.checklist[0]).toEqual({ seqNo: 1, done: false, doneAt: null, userId: null, executorDesc: '廠商陳先生' })
    expect(body.checklist[2]).toEqual({ seqNo: 3, done: true, doneAt: at, userId: 'T0002', executorDesc: null })
    wrapper.unmount()
  })

  it('完成並送治理審查：沒選結果擋下；confirm 取消不送；確認後帶結果送出並回檢視頁', async () => {
    getMock.mockResolvedValue(savedDetail())
    saveMock.mockResolvedValue({ appId: APP_ID, rowVerNo: 6, statusCode: 'PENDING_REVIEW' })
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)
    const { wrapper, router } = await mountView()

    await button(wrapper, '完成並送治理審查')?.trigger('click')
    expect(toastMsgs()).toContain('完成並送治理審查前請選擇執行結果')
    expect(confirm).not.toHaveBeenCalled()

    await wrapper.find('#result').setValue('OK')
    await button(wrapper, '完成並送治理審查')?.trigger('click')
    expect(confirm).toHaveBeenCalledTimes(1)
    expect(saveMock).not.toHaveBeenCalled()

    confirm.mockReturnValue(true)
    await button(wrapper, '完成並送治理審查')?.trigger('click')
    await flushPromises()
    expect(saveMock.mock.calls[0][1].resultCode).toBe('OK')
    expect(toastMsgs()).toContain('已送治理審查')
    expect(router.currentRoute.value.fullPath).toBe(`/apps/${APP_ID}`)
    confirm.mockRestore()
    wrapper.unmount()
  })

  it('退回給申請人：意見空白擋下；確認後帶意見送出並回檢視頁', async () => {
    getMock.mockResolvedValue(detail())
    rejectMock.mockResolvedValue({ appId: APP_ID, rowVerNo: 6 })
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)
    const { wrapper, router } = await mountView()

    await button(wrapper, '退回給申請人')?.trigger('click')
    expect(toastMsgs()).toContain('退回請填寫意見，讓申請人知道要補什麼')
    expect(confirm).not.toHaveBeenCalled()

    await wrapper.find('#reject-memo').setValue('  機櫃空間不足  ')
    await button(wrapper, '退回給申請人')?.trigger('click')
    await flushPromises()
    expect(rejectMock).toHaveBeenCalledWith(APP_ID, { rowVerNo: 5, memo: '機櫃空間不足' })
    expect(toastMsgs()).toContain('已退回給申請人')
    expect(router.currentRoute.value.fullPath).toBe(`/apps/${APP_ID}`)
    confirm.mockRestore()
    wrapper.unmount()
  })

  it('409：toast 後端訊息並重新載入；新狀態沒有執行權限就只顯示說明', async () => {
    getMock.mockResolvedValue(savedDetail())
    saveMock.mockRejectedValue(httpError(409, '申請單已在其他地方修改過，請重新載入頁面'))
    const { wrapper } = await mountView()

    getMock.mockResolvedValue(detail({ statusCode: 'PENDING_REVIEW', rowVerNo: 7, permissions: NO_PERMS }))
    await button(wrapper, '暫存')?.trigger('click')
    await flushPromises()

    expect(toastMsgs()).toContain('申請單已在其他地方修改過，請重新載入頁面')
    expect(getMock).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[role="status"]').text()).toContain('這張申請單目前無法填寫執行紀錄')
    expect(button(wrapper, '暫存')).toBeUndefined()
    wrapper.unmount()
  })

  it('400 只 toast、不重新載入（保留使用者已填的內容）', async () => {
    getMock.mockResolvedValue(savedDetail())
    saveMock.mockRejectedValue(httpError(400, '實際完成時間不得早於實際開始時間'))
    const { wrapper } = await mountView()

    await wrapper.find('#exec-memo').setValue('改過的備註')
    await button(wrapper, '暫存')?.trigger('click')
    await flushPromises()

    expect(toastMsgs()).toContain('實際完成時間不得早於實際開始時間')
    expect(getMock).toHaveBeenCalledTimes(1)
    expect((wrapper.find('#exec-memo').element as HTMLTextAreaElement).value).toBe('改過的備註')
    wrapper.unmount()
  })

  it('沒有 canExecute：只顯示說明與回檢視頁連結', async () => {
    getMock.mockResolvedValue(detail({ statusCode: 'EXECUTED', permissions: NO_PERMS }))
    const { wrapper } = await mountView()

    expect(wrapper.find('[role="status"]').text()).toContain('這張申請單目前無法填寫執行紀錄')
    expect(wrapper.find('table').exists()).toBe(false)
    expect(wrapper.text()).toContain('回檢視頁')
    wrapper.unmount()
  })

  it('取消勾選例外：已有說明先確認，取消就維持勾選；確認後說明清空', async () => {
    getMock.mockResolvedValue(savedDetail())
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)
    const { wrapper } = await mountView()

    const box = () => wrapper.findAll('label.check input').find(i => i.element.parentElement?.textContent?.includes('例外'))
    expect((wrapper.find('#exception-desc').element as HTMLTextAreaElement).value).toBe('風扇異音')
    await box()?.setValue(false)
    expect(confirm).toHaveBeenCalledTimes(1)
    expect((box()?.element as HTMLInputElement).checked).toBe(true)
    expect(wrapper.find('#exception-desc').exists()).toBe(true)

    confirm.mockReturnValue(true)
    await box()?.setValue(false)
    expect(wrapper.find('#exception-desc').exists()).toBe(false)
    await box()?.setValue(true)
    expect((wrapper.find('#exception-desc').element as HTMLTextAreaElement).value).toBe('')
    confirm.mockRestore()
    wrapper.unmount()
  })
})
