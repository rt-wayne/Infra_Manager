// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：範本新增／修改頁測試（S5 R2）；以 vi.mock 替換 apps 與 templates API，不打真後端
//           驗：新增不顯示申請人聯絡資料與開始／結束時間、預設 P3、不打 getTemplate；範本名稱空白不打 API、標紅；
//           新增 POST 本文（範本名稱、標題可空、類別其他說明每個大類一筆、不帶申請人）→ 回列表；
//           修改還原欄位、拿掉已停用選項、PUT 到原 id；canEdit 為 false 不顯示表單；
//           400 帶 field 標紅 DraftFields 裡的欄位；403 顯示後端訊息、留在本頁；找不到範本顯示錯誤
// ============================================================
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { AxiosError, AxiosHeaders } from 'axios'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import TemplateEditView from '../src/views/TemplateEditView.vue'
import { getFormOptions } from '../src/api/apps'
import { createTemplate, getTemplate, updateTemplate } from '../src/api/templates'
import { useToast } from '../src/composables/useToast'
import { TEMPLATE_EDIT_ROUTE, TEMPLATE_LIST_ROUTE, TEMPLATE_NEW_ROUTE } from '../src/router/names'
import type { FormOption, FormOptionsResponse } from '../src/types/app'
import type { TemplateDetail, TemplateForm } from '../src/types/template'

vi.mock('../src/api/apps', async importOriginal => ({
  ...(await importOriginal<typeof import('../src/api/apps')>()),
  getFormOptions: vi.fn()
}))
vi.mock('../src/api/templates', async importOriginal => ({
  ...(await importOriginal<typeof import('../src/api/templates')>()),
  getTemplate: vi.fn(),
  createTemplate: vi.fn(),
  updateTemplate: vi.fn()
}))

const optsMock = vi.mocked(getFormOptions)
const getMock = vi.mocked(getTemplate)
const createMock = vi.mocked(createTemplate)
const updateMock = vi.mocked(updateTemplate)
const Blank = { template: '<div />' }
const ID = 'tpl_firewall_upgrade'

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

function options(): FormOptionsResponse {
  return {
    options: [
      opt(1, 'PRIO', 'P1', '緊急'), opt(2, 'PRIO', 'P2', '高'), opt(3, 'PRIO', 'P3', '中'),
      opt(100, 'CATG', 'NET', '網路'), opt(101, 'CATG_ITEM', 'SW', '交換器', 100),
      opt(200, 'CATG', 'SYS', '系統'),
      opt(300, 'REASON', 'EOL', '汰換'), opt(400, 'SCOPE', 'NET', '網路')
    ],
    upload: { maxMb: 50, maxFiles: 30 }
  }
}

function tform(over: Partial<TemplateForm> = {}): TemplateForm {
  return {
    title: null, prioCode: 'P2', selfExec: true, supplierExec: false, workModeCode: 'ONSITE', remoteMethod: null,
    supplier: null, workSubject: '韌體升級', impactDesc: null, workDetail: null, riskDesc: null, rollbackPlan: null,
    categoryItemIds: [101, 999], categoryOthers: [], reasonIds: [], otherReason: null, scopeIds: [],
    equipments: [{ name: 'FW-01', assetNo: null, modelNo: null, serialNo: null, purpose: null, mgmtIp: null }],
    planSteps: ['備份設定檔'], schedule: { estHours: 2 }, location: null, ...over
  }
}

function detail(over: Partial<TemplateDetail> = {}): TemplateDetail {
  return {
    tmplId: ID, tmplName: '防火牆韌體升級', form: tform(), ownerName: '王小明', useCnt: 7, lastUsedAt: null,
    lastUsedByName: null, createdAt: '2026-05-01 09:30', updatedAt: '2026-05-01 09:30', canEdit: true, ...over
  }
}

async function mountAt(path: string): Promise<{ wrapper: VueWrapper; router: Router }> {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/templates', name: TEMPLATE_LIST_ROUTE, component: Blank },
      { path: '/templates/new', name: TEMPLATE_NEW_ROUTE, component: TemplateEditView },
      { path: '/templates/:id/edit', name: TEMPLATE_EDIT_ROUTE, component: TemplateEditView }
    ]
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(TemplateEditView, { global: { plugins: [router] }, attachTo: document.body })
  await flushPromises()
  return { wrapper, router }
}

function toastMsgs(): string[] {
  return useToast().toasts.value.map(t => t.msg)
}

describe('TemplateEditView', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    for (const m of [optsMock, getMock, createMock, updateMock]) m.mockReset()
    useToast().toasts.value = []
    optsMock.mockResolvedValue(options())
  })

  afterEach(() => {
    vi.runAllTimers()
    vi.useRealTimers()
    document.body.innerHTML = ''
  })

  it('新增：不顯示申請人聯絡資料與開始／結束時間、預設 P3、不打 getTemplate', async () => {
    const { wrapper } = await mountAt('/templates/new')
    expect(getMock).not.toHaveBeenCalled()
    expect(wrapper.find('h1').text()).toBe('新增範本')
    for (const sel of ['#f-dept', '#f-tel', '#f-email', '#f-start', '#f-end', '.fixed']) {
      expect(wrapper.find(sel).exists()).toBe(false)
    }
    expect(wrapper.find('#f-hours').exists()).toBe(true)
    expect(wrapper.find<HTMLInputElement>('input[type="radio"][value="P3"]').element.checked).toBe(true)
    expect(wrapper.findAll('.step-row')).toHaveLength(4)
    wrapper.unmount()
  })

  it('範本名稱空白時不打 API、標紅範本名稱', async () => {
    const { wrapper } = await mountAt('/templates/new')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(createMock).not.toHaveBeenCalled()
    expect(wrapper.find('.bar-msg').text()).toBe('請填寫範本名稱')
    expect(wrapper.find('#f-tmpl-name').classes()).toContain('bad')
    wrapper.unmount()
  })

  it('新增：POST 本文（標題可空、類別其他說明每個大類一筆、不帶申請人）→ 回列表', async () => {
    createMock.mockResolvedValue({ tmplId: 'tpl_1_ab12' })
    const { wrapper, router } = await mountAt('/templates/new')
    await wrapper.find('#f-tmpl-name').setValue('換交換器')
    await wrapper.find('#f-subject').setValue('更換核心交換器')
    await wrapper.find('input[type="checkbox"][value="101"]').setValue(true)
    await wrapper.find('#f-hours').setValue('3')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    const body = createMock.mock.calls[0][0]
    expect(body.tmplName).toBe('換交換器')
    expect(body.form.title).toBe('')
    expect(body.form.prioCode).toBe('P3')
    expect(body.form.workSubject).toBe('更換核心交換器')
    expect(body.form.categoryItemIds).toEqual([101])
    expect(body.form.categoryOthers?.map(c => c.formOptionId)).toEqual([100, 200])
    expect(body.form.schedule).toEqual({ estHours: 3 })
    expect('applicant' in body.form).toBe(false)
    expect(router.currentRoute.value.fullPath).toBe('/templates')
    expect(toastMsgs()).toContain('範本已儲存')
    wrapper.unmount()
  })

  it('修改：還原欄位、拿掉已停用選項、PUT 到原 id', async () => {
    getMock.mockResolvedValue(detail())
    updateMock.mockResolvedValue({ tmplId: ID })
    const { wrapper, router } = await mountAt(`/templates/${ID}/edit`)

    expect(getMock).toHaveBeenCalledWith(ID)
    expect(wrapper.find('h1').text()).toBe('修改範本')
    expect(wrapper.find<HTMLInputElement>('#f-tmpl-name').element.value).toBe('防火牆韌體升級')
    expect(wrapper.find<HTMLInputElement>('input[type="radio"][value="P2"]').element.checked).toBe(true)
    expect(wrapper.find<HTMLInputElement>('#f-subject').element.value).toBe('韌體升級')
    expect(wrapper.text()).toContain('套用 7 次')

    await wrapper.find('form').trigger('submit')
    await flushPromises()
    const [id, body] = updateMock.mock.calls[0]
    expect(id).toBe(ID)
    expect(body.form.categoryItemIds).toEqual([101])
    expect(body.form.equipments?.[0].name).toBe('FW-01')
    expect(body.form.planSteps).toEqual(['備份設定檔', '', '', ''])
    expect(router.currentRoute.value.fullPath).toBe('/templates')
    wrapper.unmount()
  })

  it('不是建立者也不是 admin 時不顯示表單', async () => {
    getMock.mockResolvedValue(detail({ canEdit: false }))
    const { wrapper } = await mountAt(`/templates/${ID}/edit`)
    expect(wrapper.find('[role="alert"]').text()).toBe('只有範本建立者或管理員可以修改範本')
    expect(wrapper.find('form').exists()).toBe(false)
    wrapper.unmount()
  })

  it('找不到範本顯示後端訊息', async () => {
    getMock.mockRejectedValue(httpError(404, '找不到範本，可能已被刪除'))
    const { wrapper } = await mountAt(`/templates/${ID}/edit`)
    expect(wrapper.find('[role="alert"]').text()).toBe('找不到範本，可能已被刪除')
    expect(wrapper.find('form').exists()).toBe(false)
    wrapper.unmount()
  })

  it('400 帶 field 時標紅表單裡的欄位', async () => {
    getMock.mockResolvedValue(detail())
    updateMock.mockRejectedValue(httpError(400, '作業主題過長', { field: 'workSubject' }))
    const { wrapper, router } = await mountAt(`/templates/${ID}/edit`)
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(wrapper.find('#f-subject').classes()).toContain('bad')
    expect(wrapper.find('.bar-msg').text()).toBe('作業主題過長')
    expect(router.currentRoute.value.fullPath).toBe(`/templates/${ID}/edit`)
    wrapper.unmount()
  })

  it('403 顯示後端訊息、留在本頁', async () => {
    getMock.mockResolvedValue(detail())
    updateMock.mockRejectedValue(httpError(403, '沒有權限執行此操作'))
    const { wrapper, router } = await mountAt(`/templates/${ID}/edit`)
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(wrapper.find('.bar-msg').text()).toBe('沒有權限執行此操作')
    expect(toastMsgs()).toContain('沒有權限執行此操作')
    expect(router.currentRoute.value.fullPath).toBe(`/templates/${ID}/edit`)
    wrapper.unmount()
  })
})
