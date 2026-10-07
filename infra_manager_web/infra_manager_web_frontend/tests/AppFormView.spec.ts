// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：申請單新增／編輯草稿表單測試（S6 回合四）；以 vi.mock 替換 apps API，不打真後端
//           驗：新增預設（申請人＝登入者、P3）；標題空白不打 API；新增 POST → 網址換編輯頁 → 上傳附件 → 回檢視頁；
//           編輯還原欄位、拿掉已停用選項、PUT 帶 rowVerNo；400 帶 field 標紅該欄；409 出「重新載入」；
//           非申請人／非草稿不給編輯；選檔預檢（類型、大小、總數）；附件上傳失敗留在清單、不回檢視頁、再存只重傳失敗的
//           S9 R3（Claude Opus 5.5，2026-10-07）：補件模式——退件資訊、confirm、先上傳再補件（巢狀本文）、上傳失敗不送補件、
//           非 canResubmit 不給補件、409 出「重新載入」
//           S5 R3（Claude Opus 5.5，2026-10-07）：套用範本——沒範本不顯示下拉、編輯不打範本清單；選範本帶入欄位、拿掉已停用選項、
//           保留申請人聯絡資料、POST 帶 templateId；改過內容先 confirm、取消不套用；?template= 直接帶入；
//           範本載入失敗出 toast 且下拉回原值；清單載入失敗只顯示提示
// ============================================================
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { AxiosError, AxiosHeaders } from 'axios'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import AppFormView from '../src/views/AppFormView.vue'
import { createApp, getApp, getFormOptions, resubmitApp, updateApp, uploadAttachment } from '../src/api/apps'
import { getTemplate, listTemplates } from '../src/api/templates'
import type { TemplateDetail, TemplateListItem } from '../src/types/template'
import { resetAuthStateForTest, useAuth } from '../src/composables/useAuth'
import { useToast } from '../src/composables/useToast'
import type { AppAttachment, AppDetail, FormOption, FormOptionsResponse } from '../src/types/app'
import { APP_EDIT_ROUTE, APP_LIST_ROUTE, APP_NEW_ROUTE, APP_RESUBMIT_ROUTE, APP_VIEW_ROUTE } from '../src/router/names'

vi.mock('../src/api/apps', async importOriginal => ({
  ...(await importOriginal<typeof import('../src/api/apps')>()),
  getFormOptions: vi.fn(),
  getApp: vi.fn(),
  createApp: vi.fn(),
  updateApp: vi.fn(),
  uploadAttachment: vi.fn(),
  resubmitApp: vi.fn()
}))

vi.mock('../src/api/templates', async importOriginal => ({
  ...(await importOriginal<typeof import('../src/api/templates')>()),
  listTemplates: vi.fn(),
  getTemplate: vi.fn()
}))

const listTplMock = vi.mocked(listTemplates)
const getTplMock = vi.mocked(getTemplate)
const optsMock = vi.mocked(getFormOptions)
const getMock = vi.mocked(getApp)
const createMock = vi.mocked(createApp)
const updateMock = vi.mocked(updateApp)
const uploadMock = vi.mocked(uploadAttachment)
const resubmitMock = vi.mocked(resubmitApp)
const Blank = { template: '<div />' }
const ID = 'IM20261007-001'

function makeRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/apps', name: APP_LIST_ROUTE, component: Blank },
      { path: '/apps/new', name: APP_NEW_ROUTE, component: AppFormView },
      { path: '/apps/:id', name: APP_VIEW_ROUTE, component: Blank },
      { path: '/apps/:id/edit', name: APP_EDIT_ROUTE, component: AppFormView },
      { path: '/apps/:id/resubmit', name: APP_RESUBMIT_ROUTE, component: AppFormView }
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
    approvalHistory: [],
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
    for (const m of [optsMock, getMock, createMock, updateMock, uploadMock, resubmitMock, listTplMock, getTplMock]) m.mockReset()
    resetAuthStateForTest()
    useAuth().me.value = { loggedIn: true, userName: '王小明' }
    optsMock.mockResolvedValue(options())
    listTplMock.mockResolvedValue([])
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
    expect(createMock.mock.calls[0][1]).toBeUndefined()
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

  describe('補件模式', () => {
    function rejected(over: Partial<AppDetail> = {}): AppDetail {
      const base = detail()
      return detail({
        statusCode: 'REJECTED',
        rowVerNo: 7,
        approval: {
          apprId: 50, statusCode: 'REJECTED', startedAt: '2026-10-07 09:00', closedAt: '2026-10-07 10:00',
          steps: [
            { seqNo: 1, stepCode: 'MGR', stepName: '主管', stepMode: 'SEQUENTIAL', notifyOnly: false, statusCode: 'REJECTED',
              deciderName: '李主管', decidedAt: '2026-10-07 10:00', memo: '請補回復計畫', candidateNames: ['李主管'] }
          ]
        },
        permissions: { ...base.permissions, canEditDraft: false, canResubmit: true },
        ...over
      })
    }

    it('顯示退件資訊與補件說明欄；confirm 後先上傳附件再補件（巢狀本文帶 rowVerNo）', async () => {
      getMock.mockResolvedValue(rejected())
      uploadMock.mockResolvedValue(attach(30, 'plan.pdf'))
      resubmitMock.mockResolvedValue({ appId: ID, rowVerNo: 8 })
      const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)
      const { wrapper, router } = await mountAt(`/apps/${ID}/resubmit`)

      expect(wrapper.find('h1').text()).toContain('補件並重送')
      expect(wrapper.find('.reject').text()).toContain('李主管')
      expect(wrapper.find('.reject').text()).toContain('請補回復計畫')
      expect(wrapper.find('button[type="submit"]').text()).toBe('補件並送審')

      await pick(wrapper, [file('plan.pdf')])
      await wrapper.find('#f-resub').setValue('已補回復計畫')
      await wrapper.find('form').trigger('submit')
      await flushPromises()

      expect(confirmSpy).toHaveBeenCalledOnce()
      expect(uploadMock.mock.invocationCallOrder[0]).toBeLessThan(resubmitMock.mock.invocationCallOrder[0])
      const [id, body] = resubmitMock.mock.calls[0]
      expect(id).toBe(ID)
      expect(body.rowVerNo).toBe(7)
      expect(body.resubMemo).toBe('已補回復計畫')
      expect(body.form.title).toBe('換交換器')
      expect(body.form.rowVerNo).toBeUndefined()
      expect(updateMock).not.toHaveBeenCalled()
      expect(toastMsgs()).toContain('已補件並重新送審')
      expect(router.currentRoute.value.fullPath).toBe(`/apps/${ID}`)
      confirmSpy.mockRestore()
      wrapper.unmount()
    })

    it('confirm 取消就不送；附件上傳失敗時不送補件', async () => {
      getMock.mockResolvedValue(rejected())
      const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false)
      const { wrapper, router } = await mountAt(`/apps/${ID}/resubmit`)
      await wrapper.find('form').trigger('submit')
      await flushPromises()
      expect(resubmitMock).not.toHaveBeenCalled()

      confirmSpy.mockReturnValue(true)
      uploadMock.mockRejectedValueOnce(httpError(400, '不支援的檔案類型'))
      await pick(wrapper, [file('a.pdf')])
      await wrapper.find('form').trigger('submit')
      await flushPromises()
      expect(uploadMock).toHaveBeenCalledOnce()
      expect(resubmitMock).not.toHaveBeenCalled()
      expect(wrapper.find('.bar-msg').text()).toContain('補件尚未送出')
      expect(router.currentRoute.value.fullPath).toBe(`/apps/${ID}/resubmit`)
      confirmSpy.mockRestore()
      wrapper.unmount()
    })

    it('不是可補件狀態時不給補件', async () => {
      const perms = rejected().permissions
      getMock.mockResolvedValue(rejected({ permissions: { ...perms, canResubmit: false } }))
      const a = await mountAt(`/apps/${ID}/resubmit`)
      expect(a.wrapper.find('[role="alert"]').text()).toBe('只有申請人可以補件')
      expect(a.wrapper.find('form').exists()).toBe(false)
      a.wrapper.unmount()

      getMock.mockResolvedValue(rejected({ statusCode: 'IN_REVIEW', permissions: { ...perms, canResubmit: false } }))
      const b = await mountAt(`/apps/${ID}/resubmit`)
      expect(b.wrapper.find('[role="alert"]').text()).toBe('申請單不是退件狀態，無法補件')
      b.wrapper.unmount()
    })

    it('409 時出「重新載入」、不自動重載（保留剛改的內容）', async () => {
      getMock.mockResolvedValue(rejected())
      resubmitMock.mockRejectedValue(httpError(409, '申請單不是退件狀態，無法補件，請重新載入頁面'))
      const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)
      const { wrapper } = await mountAt(`/apps/${ID}/resubmit`)
      await wrapper.find('#f-title').setValue('我改過的標題')
      await wrapper.find('form').trigger('submit')
      await flushPromises()

      expect(getMock).toHaveBeenCalledOnce()
      expect(wrapper.find<HTMLInputElement>('#f-title').element.value).toBe('我改過的標題')
      expect(wrapper.find('.bar-msg').text()).toBe('申請單不是退件狀態，無法補件，請重新載入頁面')
      expect(wrapper.findAll('button').some(b => b.text().startsWith('重新載入'))).toBe(true)
      confirmSpy.mockRestore()
      wrapper.unmount()
    })
  })

  describe('套用範本', () => {
    const TPL = 'tpl_firewall_upgrade'

    function tplItem(tmplId: string, tmplName: string): TemplateListItem {
      return {
        tmplId, tmplName, prioCode: 'P2', prioName: '高', prioColor: null, ownerName: '王小明',
        useCnt: 0, lastUsedAt: null, lastUsedByName: null, updatedAt: '2026-10-07 10:00', canEdit: false
      }
    }

    function tplDetail(): TemplateDetail {
      return {
        tmplId: TPL, tmplName: '防火牆韌體升級', ownerName: '王小明', useCnt: 7, lastUsedAt: null, lastUsedByName: null,
        createdAt: '2026-05-01 09:30', updatedAt: '2026-05-01 09:30', canEdit: false,
        form: {
          title: '防火牆韌體升級', prioCode: 'P2', selfExec: true, supplierExec: false, workModeCode: 'ONSITE', remoteMethod: null,
          supplier: null, workSubject: '韌體升級', impactDesc: null, workDetail: null, riskDesc: null, rollbackPlan: null,
          categoryItemIds: [101, 999], categoryOthers: [], reasonIds: [300], otherReason: null, scopeIds: [],
          equipments: [{ name: 'FW-01', assetNo: null, modelNo: null, serialNo: null, purpose: null, mgmtIp: null }],
          planSteps: ['備份設定檔'], schedule: { estHours: 2 }, location: null
        }
      }
    }

    it('沒有範本時不顯示下拉；編輯模式不打範本清單', async () => {
      const a = await mountAt('/apps/new')
      expect(listTplMock).toHaveBeenCalledOnce()
      expect(a.wrapper.find('#f-tpl').exists()).toBe(false)
      a.wrapper.unmount()

      listTplMock.mockClear()
      getMock.mockResolvedValue(detail())
      const b = await mountAt(`/apps/${ID}/edit`)
      expect(listTplMock).not.toHaveBeenCalled()
      expect(b.wrapper.find('#f-tpl').exists()).toBe(false)
      b.wrapper.unmount()
    })

    it('選範本：帶入欄位、拿掉已停用選項、保留申請人聯絡資料；POST 帶 templateId', async () => {
      listTplMock.mockResolvedValue([tplItem(TPL, '防火牆韌體升級'), tplItem('tpl_b', '另一份')])
      getTplMock.mockResolvedValue(tplDetail())
      createMock.mockResolvedValue({ appId: ID, rowVerNo: 0 })
      const { wrapper } = await mountAt('/apps/new')
      expect(wrapper.findAll('#f-tpl option').map(o => o.text())).toEqual(['— 不套用 —', '防火牆韌體升級', '另一份'])

      await wrapper.find('#f-tel').setValue('5678')
      const confirmSpy = vi.spyOn(window, 'confirm')
      await wrapper.find('#f-tpl').setValue(TPL)
      await flushPromises()

      expect(confirmSpy).not.toHaveBeenCalled()
      expect(getTplMock).toHaveBeenCalledWith(TPL)
      expect(wrapper.find<HTMLInputElement>('#f-title').element.value).toBe('防火牆韌體升級')
      expect(wrapper.find<HTMLInputElement>('#f-subject').element.value).toBe('韌體升級')
      expect(wrapper.find<HTMLInputElement>('input[type="radio"][value="P2"]').element.checked).toBe(true)
      expect(wrapper.find<HTMLInputElement>('#f-tel').element.value).toBe('5678')
      expect(toastMsgs()).toContain('已套用範本「防火牆韌體升級」')

      await wrapper.find('form').trigger('submit')
      await flushPromises()
      const [body, templateId] = createMock.mock.calls[0]
      expect(templateId).toBe(TPL)
      expect(body.categoryItemIds).toEqual([101])
      expect(body.reasonIds).toEqual([300])
      expect(body.applicant.tel).toBe('5678')
      expect(body.planSteps).toEqual(['備份設定檔', '', '', ''])
      confirmSpy.mockRestore()
      wrapper.unmount()
    })

    it('改過內容後選範本先 confirm；取消不套用、下拉回原值；改選「不套用」POST 不帶 templateId', async () => {
      listTplMock.mockResolvedValue([tplItem(TPL, '防火牆韌體升級')])
      getTplMock.mockResolvedValue(tplDetail())
      createMock.mockResolvedValue({ appId: ID, rowVerNo: 0 })
      const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true)
      const { wrapper } = await mountAt('/apps/new')

      await wrapper.find('#f-title').setValue('我自己寫的')
      await wrapper.find('#f-tpl').setValue(TPL)
      await flushPromises()
      expect(confirmSpy).toHaveBeenCalledOnce()
      expect(getTplMock).not.toHaveBeenCalled()
      expect(wrapper.find<HTMLSelectElement>('#f-tpl').element.value).toBe('')
      expect(wrapper.find<HTMLInputElement>('#f-title').element.value).toBe('我自己寫的')

      await wrapper.find('#f-tpl').setValue(TPL)
      await flushPromises()
      expect(wrapper.find<HTMLInputElement>('#f-title').element.value).toBe('防火牆韌體升級')

      await wrapper.find('#f-tpl').setValue('')
      await wrapper.find('form').trigger('submit')
      await flushPromises()
      expect(createMock.mock.calls[0][1]).toBeUndefined()
      expect(createMock.mock.calls[0][0].title).toBe('防火牆韌體升級')
      confirmSpy.mockRestore()
      wrapper.unmount()
    })

    it('網址帶 ?template= 時直接帶入', async () => {
      listTplMock.mockResolvedValue([tplItem(TPL, '防火牆韌體升級')])
      getTplMock.mockResolvedValue(tplDetail())
      const { wrapper } = await mountAt(`/apps/new?template=${TPL}`)
      expect(getTplMock).toHaveBeenCalledWith(TPL)
      expect(wrapper.find<HTMLSelectElement>('#f-tpl').element.value).toBe(TPL)
      expect(wrapper.find<HTMLInputElement>('#f-subject').element.value).toBe('韌體升級')
      wrapper.unmount()
    })

    it('範本載入失敗出 toast、下拉回原值；清單載入失敗只顯示提示、仍可填寫', async () => {
      listTplMock.mockResolvedValue([tplItem(TPL, '防火牆韌體升級')])
      getTplMock.mockRejectedValue(httpError(404, '找不到範本，可能已被刪除'))
      const a = await mountAt('/apps/new')
      await a.wrapper.find('#f-tpl').setValue(TPL)
      await flushPromises()
      expect(toastMsgs()).toContain('找不到範本，可能已被刪除')
      expect(a.wrapper.find<HTMLSelectElement>('#f-tpl').element.value).toBe('')
      a.wrapper.unmount()

      listTplMock.mockRejectedValue(httpError(500, '後端錯誤'))
      const b = await mountAt('/apps/new')
      expect(b.wrapper.text()).toContain('範本清單載入失敗，可直接填寫')
      expect(b.wrapper.find('#f-title').exists()).toBe(true)
      expect(toastMsgs()).not.toContain('後端錯誤')
      b.wrapper.unmount()
    })
  })
})
