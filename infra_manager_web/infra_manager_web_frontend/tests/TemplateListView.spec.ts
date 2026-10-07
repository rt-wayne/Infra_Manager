// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：範本列表頁測試（S5 R2）；以 vi.mock 替換 templates API，不打真後端
//           驗：列出名稱／建立者／套用次數／最後套用；canEdit 才有修改與刪除；空清單提示；載入失敗出錯誤與 toast；
//           刪除先 confirm、取消不打 API、確定後打 DELETE 並重新載入；刪除 403 出後端訊息；超過 20 筆分頁
//           S5 R3：每列都有「建單」連到 /apps/new?template=<id>（不限建立者）
// ============================================================
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { AxiosError, AxiosHeaders } from 'axios'
import { createMemoryHistory, createRouter } from 'vue-router'
import TemplateListView from '../src/views/TemplateListView.vue'
import { deleteTemplate, listTemplates } from '../src/api/templates'
import { useToast } from '../src/composables/useToast'
import { APP_NEW_ROUTE, TEMPLATE_EDIT_ROUTE, TEMPLATE_LIST_ROUTE, TEMPLATE_NEW_ROUTE } from '../src/router/names'
import type { TemplateListItem } from '../src/types/template'

vi.mock('../src/api/templates', async importOriginal => ({
  ...(await importOriginal<typeof import('../src/api/templates')>()),
  listTemplates: vi.fn(),
  deleteTemplate: vi.fn()
}))

const listMock = vi.mocked(listTemplates)
const deleteMock = vi.mocked(deleteTemplate)
const Blank = { template: '<div />' }

function httpError(status: number, message: string): AxiosError {
  const err = new AxiosError(message, String(status))
  err.response = { status, statusText: '', headers: {}, config: { headers: new AxiosHeaders() }, data: { message } }
  return err
}

function item(tmplId: string, over: Partial<TemplateListItem> = {}): TemplateListItem {
  return {
    tmplId, tmplName: '範本 ' + tmplId, prioCode: 'P3', prioName: '中', prioColor: '#999999', ownerName: '王小明',
    useCnt: 0, lastUsedAt: null, lastUsedByName: null, updatedAt: '2026-10-07 10:00', canEdit: false, ...over
  }
}

async function mountList(): Promise<VueWrapper> {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: Blank },
      { path: '/apps/new', name: APP_NEW_ROUTE, component: Blank },
      { path: '/templates', name: TEMPLATE_LIST_ROUTE, component: TemplateListView },
      { path: '/templates/new', name: TEMPLATE_NEW_ROUTE, component: Blank },
      { path: '/templates/:id/edit', name: TEMPLATE_EDIT_ROUTE, component: Blank }
    ]
  })
  await router.push('/templates')
  await router.isReady()
  const wrapper = mount(TemplateListView, { global: { plugins: [router] } })
  await flushPromises()
  return wrapper
}

function toastMsgs(): string[] {
  return useToast().toasts.value.map(t => t.msg)
}

describe('TemplateListView', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    listMock.mockReset()
    deleteMock.mockReset()
    useToast().toasts.value = []
  })

  afterEach(() => {
    vi.runAllTimers()
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('列出範本；canEdit 才有修改與刪除', async () => {
    listMock.mockResolvedValue([
      item('a', { tmplName: '防火牆升級', useCnt: 7, lastUsedAt: '2026-09-18 16:12', lastUsedByName: '蕭建弘', canEdit: true }),
      item('b', { tmplName: '別人的範本' })
    ])
    const w = await mountList()
    const rows = w.findAll('tbody tr')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('防火牆升級')
    expect(rows[0].text()).toContain('7')
    expect(rows[0].text()).toContain('蕭建弘')
    expect(rows[0].find('a[href="/templates/a/edit"]').exists()).toBe(true)
    expect(rows[0].find('button').text()).toBe('刪除')
    expect(rows[1].text()).toContain('尚未套用')
    expect(rows[1].findAll('a').map(a => a.text())).toEqual(['建單'])
    expect(rows[1].find('a').attributes('href')).toBe('/apps/new?template=b')
    expect(rows[1].find('button').exists()).toBe(false)
    expect(w.find('a[href="/templates/new"]').exists()).toBe(true)
  })

  it('空清單顯示提示', async () => {
    listMock.mockResolvedValue([])
    const w = await mountList()
    expect(w.text()).toContain('目前沒有範本')
  })

  it('載入失敗顯示錯誤並出 toast', async () => {
    listMock.mockRejectedValue(httpError(500, '後端錯誤'))
    const w = await mountList()
    expect(w.find('[role="alert"]').text()).toBe('後端錯誤')
    expect(toastMsgs()).toContain('後端錯誤')
  })

  it('刪除：取消 confirm 不打 API；確定後 DELETE 並重新載入', async () => {
    listMock.mockResolvedValueOnce([item('a', { canEdit: true })]).mockResolvedValueOnce([])
    deleteMock.mockResolvedValue()
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true)
    const w = await mountList()

    await w.find('tbody button').trigger('click')
    await flushPromises()
    expect(deleteMock).not.toHaveBeenCalled()

    await w.find('tbody button').trigger('click')
    await flushPromises()
    expect(confirmSpy).toHaveBeenCalledTimes(2)
    expect(deleteMock).toHaveBeenCalledWith('a')
    expect(listMock).toHaveBeenCalledTimes(2)
    expect(toastMsgs()).toContain('範本已刪除')
    expect(w.text()).toContain('目前沒有範本')
  })

  it('刪除 403 出後端訊息，仍重新載入', async () => {
    listMock.mockResolvedValue([item('a', { canEdit: true })])
    deleteMock.mockRejectedValue(httpError(403, '沒有權限執行此操作'))
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const w = await mountList()
    await w.find('tbody button').trigger('click')
    await flushPromises()
    expect(toastMsgs()).toContain('沒有權限執行此操作')
    expect(listMock).toHaveBeenCalledTimes(2)
  })

  it('超過 20 筆分頁', async () => {
    listMock.mockResolvedValue(Array.from({ length: 25 }, (_, i) => item('t' + String(i).padStart(2, '0'))))
    const w = await mountList()
    expect(w.findAll('tbody tr')).toHaveLength(20)
    expect(w.text()).toContain('第 1 / 2 頁，共 25 筆')
    const next = w.findAll('nav button').find(b => b.text() === '下一頁')
    await next?.trigger('click')
    expect(w.findAll('tbody tr')).toHaveLength(5)
    expect(w.text()).toContain('範本 t20')
  })
})
