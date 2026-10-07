// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：申請單新增／編輯草稿表單測試（S6 回合四）；以 vi.mock 替換 apps API，不打真後端
//           驗：新增預設（申請人＝登入者、P3）；標題空白不打 API；新增 POST → 網址換編輯頁 → 上傳附件 → 回檢視頁；
//           編輯還原欄位、拿掉已停用選項、PUT 帶 rowVerNo；400 帶 field 標紅該欄；409 出「重新載入」；
//           非申請人／非草稿不給編輯；選檔預檢（類型、大小、總數）；附件上傳失敗留在清單、不回檢視頁、再存只重傳失敗的
// ============================================================
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { AxiosError, AxiosHeaders } from 'axios'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import AppFormView from '../src/views/AppFormView.vue'
import { createApp, getApp, getFormOptions, updateApp, uploadAttachment } from '../src/api/apps'
import { resetAuthStateForTest, useAuth } from '../src/composables/useAuth'
import { useToast } from '../src/composables/useToast'
import type { AppAttachment, AppDetail, FormOption, FormOptionsResponse } from '../src/types/app'
import { APP_EDIT_ROUTE, APP_LIST_ROUTE, APP_NEW_ROUTE, APP_VIEW_ROUTE } from '../src/router/names'

vi.mock('../src/api/apps', async importOriginal => ({
  ...(await importOriginal<typeof import('../src/api/apps')>()),
  getFormOptions: vi.fn(),
  getApp: vi.fn(),
  createApp: vi.fn(),
  updateApp: vi.fn(),
  uploadAttachment: vi.fn()
}))

const optsMock = vi.mocked(getFormOptions)
const getMock = vi.mocked(getApp)
const createMock = vi.mocked(createApp)
const updateMock = vi.mocked(updateApp)
const uploadMock = vi.mocked(uploadAttachment)
const Blank = { template: '<div />' }
const ID = 'IM20261007-001'

function makeRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/apps', name: APP_LIST_ROUTE, component: Blank },
      { path: '/apps/new', name: APP_NEW_ROUTE, component: AppFormView },
      { path: '/apps/:id', name: APP_VIEW_ROUTE, component: Blank },
      { path: '/apps/:id/edit', name: APP_EDIT_ROUTE, component: AppFormView }
    ]
  })
}

function httpError(status: number, message: string, extra: Record<string, unknown> = {}): AxiosError {
  const err = new AxiosError(message, String(status))
  err.response = { status, statusText: '', headers: {}, config: { headers: new AxiosHeaders() }, data: { message, ...extra } }
  return err
}

function opt(formOptionId: number, groupCode: string, code: string, name: string, up: number | null = null): FormOption {
  return {
    formOptionId, groupCode, code, name, upFormOptionId: up, colorCode: null, desc: null,
    timeLimitDesc: null, prioFlowDesc: null, sampleDesc: null, flowId: null, sortNo: null
  }
}

function options(upload = { maxMb: 50, maxFiles: 30 }): FormOptionsResponse {
  return {
    options: [
      opt(1, 'PRIO', 'P1', '緊急'), opt(2, 'PRIO', 'P2', '高'), opt(3, 'PRIO', 'P3', '中'), opt(4, 'PRIO', 'P4', '低'),
      opt(100, 'CATG', 'NET', '網路'), opt(101, 'CATG_ITEM', 'SW', '交換器', 100),
      opt(200, 'CATG', 'SYS', '系統'),
      opt(300, 'REASON', 'EOL', '汰換'), opt(400, 'SCOPE', 'NET', '網路')
    ],
    upload
  }
}

function attach(attachId: number, fileName: string): AppAttachment {
  return { attachId, ownerType: 'APP', ownerId: ID, fileName, byteQty: 10, mimeType: 'application/pdf', uploadedAt: '2026-10-07 10:00' }
}

function detail(over: Partial<AppDetail> = {}): AppDetail {
  return {
    appId: ID, title: '換交換器', prioCode: 'P2', prioName: '高', prioColor: null, statusCode: 'DRAFT',
    sourceCode: 'ONLINE', flowId: null, flowName: null, verNo: 1, rowVerNo: 3,
    applicant: { userId: 'T0001', name: '王小明', deptName: '資訊處', tel: '1234', email: 'a@example.com' },
    applyDate: '2026-10-07', selfExec: true, supplierExec: false, workModeCode: 'ONSITE', remoteMethod: null,
    supplier: { name: null, contact: null, tel: null, headCount: null },
    workSubject: '主題', impactDesc: null, workDetail: null, riskDesc: null, rollbackPlan: null,
    categories: [
      { formOptionId: 101, groupCode: 'CATG_ITEM', code: 'SW', name: '交換器', upCode: 'NET', otherText: null },
      { formOptionId: 999, groupCode: 'CATG_ITEM', code: 'OLD', name: '已停用', upCode: 'NET', otherText: null }
    ],
    reasons: [], otherReason: null, scopes: [],
    equipments: [{ seqNo: 1, name: 'SW-01', assetNo: null, modelNo: null, serialNo: null, purpose: null, mgmtIp: null }],
    planSteps: [], schedule: { start: null, end: null, estHours: null },
    location: { sourceCode: null, areaName: null, rackName: null, uRange: null, siteId: null, rackId: null, uStart: null, uEnd: null, omitReason: null },
    resubmitMemo: null, checklist: [], execution: null, approval: { apprId: null, statusCode: null, startedAt: null, closedAt: null, steps: [] },
    attachments: [attach(7, '舊檔.pdf'), { ...attach(8, '關卡檔.pdf'), ownerType: 'STEP' }],
    versions: [], events: [],
    permissions: { canDecide: false, canResubmit: false, canRecall: false, canExecute: false, canReview: false, canDelete: false, canAiReview: false, canSubmit: false, canEditDraft: true, deleteMode: null },
    createdAt: null, updatedAt: null,
    ...over
  }
}

async function mountAt(path: string): Promise<{ wrapper: VueWrapper; router: Router }> {
  const router = makeRouter()
  await router.push(path)
  await router.isReady()
  const wrapper = mount(AppFormView, { global: { plugins: [router] }, attachTo: document.body })
  await flushPromises()
  return { wrapper, router }
}

function toastMsgs(): string[] {
  return useToast().toasts.value.map(t => t.msg)
}

function file(name: string, size = 10): File {
  return new File([new Uint8Array(size)], name)
}

async function pick(wrapper: VueWrapper, files: File[]): Promise<void> {
  const input = wrapper.find('input[type="file"]')
  Object.defineProperty(input.element, 'files', { value: files, configurable: true })
  await input.trigger('change')
}

describe('AppFormView', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    for (const m of [optsMock, getMock, createMock, updateMock, uploadMock]) m.mockReset()
    resetAuthStateForTest()
    useAuth().me.value = { loggedIn: true, userName: '王小明' }
    optsMock.mockResolvedValue(options())
  })

  afterEach(() => {
    vi.runAllTimers()
    vi.useRealTimers()
    document.body.innerHTML = ''
  })

  it('新增：申請人是登入者、預設 P3，不打 getApp', async () => {
    const { wrapper } = await mountAt('/apps/new')
    expect(getMock).not.toHaveBeenCalled()
    expect(wrapper.find('h1').text()).toContain('新增申請單')
    expect(wrapper.find('.fixed').text()).toBe('王小明')
    expect(wrapper.find<HTMLInputElement>('input[type="radio"][value="P3"]').element.checked).toBe(true)
    expect(wrapper.findAll('.cat')).toHaveLength(2)
    expect(wrapper.findAll('.step-row')).toHaveLength(4)
    wrapper.unmount()
  })

  it('標題空白時不打 API、標紅標題', async () => {
    const { wrapper } = await mountAt('/apps/new')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(createMock).not.toHaveBeenCalled()
    expect(wrapper.find('.bar-msg').text()).toBe('請填寫標題')
    expect(wrapper.find('#f-title').classes()).toContain('bad')
    wrapper.unmount()
  })

  it('新增：POST → 網址換編輯頁 → 上傳附件 → 回檢視頁', async () => {
    createMock.mockResolvedValue({ appId: ID, rowVerNo: 0 })
    uploadMock.mockImplementation(async (_id, f) => attach(20, f.name))
    const { wrapper, router } = await mountAt('/apps/new')
    const replaced: string[] = []
    router.afterEach(to => { replaced.push(to.fullPath) })

    await wrapper.find('#f-title').setValue('換交換器')
    await wrapper.find('input[type="checkbox"][value="101"]').setValue(true)
    await pick(wrapper, [file('a.pdf'), file('b.png')])
    expect(wrapper.findAll('.files li')).toHaveLength(2)
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    const body = createMock.mock.calls[0][0]
    expect(body.title).toBe('換交換器')
    expect(body.prioCode).toBe('P3')
    expect(body.categoryItemIds).toEqual([101])
    expect(body.categoryOthers.map(c => c.formOptionId)).toEqual([100, 200])
    expect('rowVerNo' in body).toBe(false)
    expect(uploadMock).toHaveBeenCalledTimes(2)
    expect(uploadMock.mock.calls[0][0]).toBe(ID)
    expect(replaced).toEqual([`/apps/${ID}/edit`, `/apps/${ID}`])
    expect(optsMock).toHaveBeenCalledTimes(1)
    expect(toastMsgs()).toContain('草稿已儲存')
    wrapper.unmount()
  })

  it('編輯：還原欄位、拿掉已停用選項、只列申請單本身的附件、PUT 帶 rowVerNo', async () => {
    getMock.mockResolvedValue(detail())
    updateMock.mockResolvedValue({ appId: ID, rowVerNo: 4 })
    const { wrapper, router } = await mountAt(`/apps/${ID}/edit`)

    expect(getMock).toHaveBeenCalledWith(ID)
    expect(wrapper.find('h1').text()).toContain('編輯草稿')
    expect(wrapper.find<HTMLInputElement>('#f-title').element.value).toBe('換交換器')
    expect(wrapper.find<HTMLInputElement>('input[type="radio"][value="P2"]').element.checked).toBe(true)
    expect(wrapper.findAll('.files li')).toHaveLength(1)
    expect(wrapper.find('.files').text()).toContain('舊檔.pdf')

    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(updateMock).toHaveBeenCalledTimes(1)
    const [id, body] = updateMock.mock.calls[0]
    expect(id).toBe(ID)
    expect(body.rowVerNo).toBe(3)
    expect(body.categoryItemIds).toEqual([101])
    expect(body.equipments[0].name).toBe('SW-01')
    expect(router.currentRoute.value.fullPath).toBe(`/apps/${ID}`)
    wrapper.unmount()
  })

  it('400 帶 field 時標紅該欄、顯示訊息', async () => {
    getMock.mockResolvedValue(detail())
    updateMock.mockRejectedValue(httpError(400, '設備名稱過長', { field: 'equipments[0].name', max: 100, actual: 120 }))
    const { wrapper, router } = await mountAt(`/apps/${ID}/edit`)

    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(wrapper.find('[data-field="equipments[0].name"]').classes()).toContain('bad')
    expect(wrapper.find('.bar-msg').text()).toBe('設備名稱過長')
    expect(toastMsgs()).toContain('設備名稱過長')
    expect(router.currentRoute.value.fullPath).toBe(`/apps/${ID}/edit`)
    wrapper.unmount()
  })

  it('409 時出「重新載入」，按下重新取得申請單', async () => {
    getMock.mockResolvedValue(detail())
    updateMock.mockRejectedValue(httpError(409, '申請單已被其他人修改，請重新載入'))
    const { wrapper } = await mountAt(`/apps/${ID}/edit`)

    await wrapper.find('form').trigger('submit')
    await flushPromises()
    const btn = wrapper.findAll('button').find(b => b.text().startsWith('重新載入'))
    expect(btn).toBeDefined()
    expect(wrapper.find('.bar-msg').text()).toBe('申請單已被其他人修改，請重新載入')

    getMock.mockResolvedValue(detail({ title: '別人改的', rowVerNo: 4 }))
    await btn?.trigger('click')
    await flushPromises()
    expect(getMock).toHaveBeenCalledTimes(2)
    expect(wrapper.find<HTMLInputElement>('#f-title').element.value).toBe('別人改的')
    expect(wrapper.findAll('button').some(b => b.text().startsWith('重新載入'))).toBe(false)
    wrapper.unmount()
  })

  it('不是申請人、或已不是草稿時不給編輯', async () => {
    const perms = detail().permissions
    getMock.mockResolvedValue(detail({ permissions: { ...perms, canEditDraft: false } }))
    const a = await mountAt(`/apps/${ID}/edit`)
    expect(a.wrapper.find('[role="alert"]').text()).toBe('只有申請人可以編輯草稿')
    expect(a.wrapper.find('form').exists()).toBe(false)
    a.wrapper.unmount()

    getMock.mockResolvedValue(detail({ statusCode: 'IN_REVIEW', permissions: { ...perms, canEditDraft: false } }))
    const b = await mountAt(`/apps/${ID}/edit`)
    expect(b.wrapper.find('[role="alert"]').text()).toBe('申請單已不是草稿，無法編輯')
    b.wrapper.unmount()
  })

  it('選檔預檢：類型、大小、總數不符的不加入並列原因', async () => {
    optsMock.mockResolvedValue(options({ maxMb: 1, maxFiles: 2 }))
    const { wrapper } = await mountAt('/apps/new')

    await pick(wrapper, [file('病毒.exe'), file('大檔.pdf', 1024 * 1024 + 1), file('a.pdf'), file('b.pdf'), file('c.pdf')])
    expect(wrapper.findAll('.files li')).toHaveLength(2)
    const rejects = wrapper.findAll('.rejects li').map(li => li.text())
    expect(rejects).toEqual(['病毒.exe：不支援的檔案類型', '大檔.pdf：超過單檔上限 1 MB', 'c.pdf：附件最多 2 個'])
    expect(toastMsgs()).toContain('有 3 個檔案無法加入')
    wrapper.unmount()
  })

  it('附件上傳失敗：留在清單標原因、不回檢視頁；再存只重傳失敗的', async () => {
    getMock.mockResolvedValue(detail())
    updateMock.mockResolvedValue({ appId: ID, rowVerNo: 4 })
    uploadMock
      .mockResolvedValueOnce(attach(21, 'a.pdf'))
      .mockRejectedValueOnce(httpError(400, '不支援的檔案類型'))
    const { wrapper, router } = await mountAt(`/apps/${ID}/edit`)

    await pick(wrapper, [file('a.pdf'), file('b.pdf')])
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe(`/apps/${ID}/edit`)
    expect(wrapper.find('.bar-msg').text()).toContain('有附件上傳失敗')
    const failed = wrapper.findAll('.files li').filter(li => li.text().includes('失敗'))
    expect(failed).toHaveLength(1)
    expect(failed[0].text()).toContain('b.pdf')
    expect(failed[0].text()).toContain('不支援的檔案類型')

    uploadMock.mockResolvedValueOnce(attach(22, 'b.pdf'))
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(updateMock).toHaveBeenCalledTimes(2)
    expect(updateMock.mock.calls[1][1].rowVerNo).toBe(4)
    expect(uploadMock).toHaveBeenCalledTimes(3)
    expect(uploadMock.mock.calls[2][1].name).toBe('b.pdf')
    expect(router.currentRoute.value.fullPath).toBe(`/apps/${ID}`)
    wrapper.unmount()
  })
})
