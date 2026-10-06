// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-06
// 變更說明: 新增：申請單列表頁測試（S4 回合二）；以 vi.mock 替換 listApps，不打真後端
//           驗：進頁打第 1 頁；列資料、狀態中文、待我簽核標記與數量；空結果文字；失敗出 toast 且不顯示成沒有資料；
//           401 不另出 toast；篩選條件換成查詢參數（空值不送）；起日晚於迄日不打 API；換頁沿用已查詢的條件
//           S4 回合三：狀態中文改舊系統用語；單號連到 /apps/:id（測試路由改具名）
// ============================================================
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { AxiosError, AxiosHeaders } from 'axios'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import AppListView from '../src/views/AppListView.vue'
import { listApps, toListParams } from '../src/api/apps'
import { useToast } from '../src/composables/useToast'
import type { AppListFilter, AppListItem, AppListResponse } from '../src/types/app'
import { APP_LIST_ROUTE, APP_VIEW_ROUTE } from '../src/router/names'

vi.mock('../src/api/apps', async importOriginal => ({
  ...(await importOriginal<typeof import('../src/api/apps')>()),
  listApps: vi.fn()
}))

const listMock = vi.mocked(listApps)
const Blank = { template: '<div />' }

function makeRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: Blank },
      { path: '/apps', name: APP_LIST_ROUTE, component: AppListView },
      { path: '/apps/:id', name: APP_VIEW_ROUTE, component: Blank }
    ]
  })
}

function httpError(status: number, message: string): AxiosError {
  const err = new AxiosError(message, String(status))
  err.response = { status, statusText: '', headers: {}, config: { headers: new AxiosHeaders() }, data: { message } }
  return err
}

function row(over: Partial<AppListItem>): AppListItem {
  return {
    appId: 'IM20261006-001',
    title: '更換交換器',
    prioCode: 'P2',
    prioName: '高',
    prioColor: '#d9880f',
    workSubject: '核心交換器汰換',
    applyDeptName: '資訊部',
    applicantName: '王小明',
    verNo: 1,
    statusCode: 'IN_REVIEW',
    sourceCode: 'ONLINE',
    currentStep: '部門主管',
    currentApprover: '李主管',
    mine: false,
    createdAt: '2026-10-06 09:30',
    ...over
  }
}

function resp(items: AppListItem[], over: Partial<AppListResponse> = {}): AppListResponse {
  return { items, total: items.length, page: 1, size: 20, mineCount: 0, ...over }
}

async function mountList() {
  const router = makeRouter()
  await router.push('/apps')
  await router.isReady()
  const wrapper = mount(AppListView, { global: { plugins: [router] } })
  await flushPromises()
  return wrapper
}

function toastMsgs(): string[] {
  return useToast().toasts.value.map(t => t.msg)
}

const EMPTY: AppListFilter = { status: '', priority: '', source: '', mine: false, q: '', from: '', to: '' }

describe('AppListView', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    listMock.mockReset()
  })

  afterEach(() => {
    vi.runAllTimers()
    vi.useRealTimers()
  })

  it('進頁打第 1 頁並顯示列資料、狀態中文與待我簽核', async () => {
    listMock.mockResolvedValue(
      resp([row({ appId: 'IM20261006-002', mine: true }), row({ statusCode: 'EXECUTED', sourceCode: 'IMPORTED', currentStep: null, currentApprover: null })], { mineCount: 3 })
    )
    const wrapper = await mountList()

    expect(listMock).toHaveBeenCalledWith(EMPTY, 1)
    const rows = wrapper.findAll('tbody tr')
    expect(rows).toHaveLength(2)
    expect(rows[0].classes()).toContain('mine')
    expect(rows[0].text()).toContain('IM20261006-002')
    expect(rows[0].text()).toContain('待我簽核')
    expect(rows[0].text()).toContain('簽核中')
    expect(rows[1].text()).toContain('已結案')
    expect(rows[0].find('a.id').attributes('href')).toBe('/apps/IM20261006-002')
    expect(rows[1].text()).toContain('紙本匯入')
    expect(wrapper.text()).toContain('只看待我簽核（3）')
    expect(wrapper.text()).toContain('第 1 / 1 頁，共 2 筆')
    wrapper.unmount()
  })

  it('成功但沒有資料時顯示沒有符合條件', async () => {
    listMock.mockResolvedValue(resp([]))
    const wrapper = await mountList()

    expect(wrapper.text()).toContain('沒有符合條件的申請單')
    expect(wrapper.find('table').exists()).toBe(false)
    wrapper.unmount()
  })

  it('失敗時出 toast 與錯誤文字，不顯示成沒有資料', async () => {
    listMock.mockRejectedValue(httpError(400, '狀態篩選值不正確'))
    const wrapper = await mountList()

    expect(toastMsgs()).toContain('狀態篩選值不正確')
    expect(wrapper.find('[role="alert"]').text()).toBe('狀態篩選值不正確')
    expect(wrapper.text()).not.toContain('沒有符合條件')
    expect(wrapper.text()).not.toContain('查無資料')
    wrapper.unmount()
  })

  it('401 不另出 toast（交給登入處理器）', async () => {
    listMock.mockRejectedValue(httpError(401, '尚未登入'))
    const before = toastMsgs().length
    const wrapper = await mountList()

    expect(toastMsgs()).toHaveLength(before)
    expect(wrapper.text()).not.toContain('沒有符合條件')
    wrapper.unmount()
  })

  it('篩選條件送出時帶入，換頁沿用已查詢的條件', async () => {
    listMock.mockResolvedValue(resp([row({})], { total: 45 }))
    const wrapper = await mountList()

    await wrapper.find('select[name="status"]').setValue('IN_REVIEW')
    await wrapper.find('input[name="mine"]').setValue(true)
    await wrapper.find('input[name="q"]').setValue('交換器')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    const searched = { ...EMPTY, status: 'IN_REVIEW', mine: true, q: '交換器' }
    expect(listMock).toHaveBeenLastCalledWith(searched, 1)

    await wrapper.find('input[name="q"]').setValue('沒按查詢')
    const next = wrapper.findAll('.pager button')[1]
    await next.trigger('click')
    await flushPromises()
    expect(listMock).toHaveBeenLastCalledWith(searched, 2)
    wrapper.unmount()
  })

  it('起日晚於迄日時提示且不打 API', async () => {
    listMock.mockResolvedValue(resp([]))
    const wrapper = await mountList()
    listMock.mockClear()

    await wrapper.find('input[name="from"]').setValue('2026-10-06')
    await wrapper.find('input[name="to"]').setValue('2026-10-01')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(listMock).not.toHaveBeenCalled()
    expect(toastMsgs()).toContain('起日不得晚於迄日')
    wrapper.unmount()
  })

  it('toListParams 空值與 false 不送、關鍵字去空白、第 1 頁不帶 page', () => {
    expect(toListParams(EMPTY, 1)).toEqual({})
    expect(
      toListParams({ status: 'DRAFT', priority: 'P1', source: 'IMPORTED', mine: true, q: '  ab  ', from: '2026-10-01', to: '2026-10-06' }, 3)
    ).toEqual({ status: 'DRAFT', priority: 'P1', source: 'IMPORTED', mine: 'true', q: 'ab', from: '2026-10-01', to: '2026-10-06', page: '3' })
    expect(toListParams({ ...EMPTY, q: '   ' }, 1)).toEqual({})
  })
})
