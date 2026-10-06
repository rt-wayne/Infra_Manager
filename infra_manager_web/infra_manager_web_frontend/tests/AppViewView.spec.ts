// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-06
// 變更說明: 新增：申請單檢視頁測試（S4 回合三）；以 vi.mock 替換 getApp，不打真後端
//           驗：依路由 id 載入；狀態中文、簽核關卡與候選人、附件 KB 與停用的下載鈕；
//           動作鈕依 permissions 顯示、點了出「此功能尚未開放」；草稿簽核欄的「尚未送審」註記；
//           執行確認列依治理事件顯示退回；404 出 toast 並顯示錯誤；401 不另出 toast；換 id 重新載入
// ============================================================
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { AxiosError, AxiosHeaders } from 'axios'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import AppViewView from '../src/views/AppViewView.vue'
import { getApp } from '../src/api/apps'
import { useToast } from '../src/composables/useToast'
import { APP_LIST_ROUTE, APP_VIEW_ROUTE } from '../src/router/names'
import type { AppDetail, AppPermissions } from '../src/types/app'

vi.mock('../src/api/apps', async importOriginal => ({
  ...(await importOriginal<typeof import('../src/api/apps')>()),
  getApp: vi.fn()
}))

const getMock = vi.mocked(getApp)
const Blank = { template: '<div />' }

function makeRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/apps', name: APP_LIST_ROUTE, component: Blank },
      { path: '/apps/:id', name: APP_VIEW_ROUTE, component: AppViewView }
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

function detail(over: Partial<AppDetail> = {}): AppDetail {
  return {
    appId: 'IM20261006-001',
    title: '更換交換器',
    prioCode: 'P2',
    prioName: '高',
    prioColor: '#d9880f',
    statusCode: 'IN_REVIEW',
    sourceCode: 'ONLINE',
    flowId: 'F1',
    flowName: '一般流程',
    verNo: 1,
    applicant: { userId: '00001', name: '王小明', deptName: '資訊部', tel: '1234', email: 'a@example.com' },
    applyDate: '2026-10-06',
    selfExec: true,
    supplierExec: true,
    workModeCode: 'REMOTE',
    remoteMethod: 'VPN',
    supplier: { name: '某廠商', contact: '陳先生', tel: '5678', headCount: 2 },
    workSubject: '核心交換器汰換',
    impactDesc: '短暫斷線',
    workDetail: '第一行\n第二行',
    riskDesc: '低',
    rollbackPlan: '換回舊機',
    categories: [{ groupCode: 'CATG_ITEM', code: 'NET_SW', name: '交換器', upCode: 'NET', otherText: null }],
    reasons: [{ groupCode: 'REASON', code: 'EOL', name: '設備汰換', upCode: null, otherText: null }],
    otherReason: '順便整線',
    scopes: [{ groupCode: 'SCOPE', code: 'NET', name: '網路', upCode: null, otherText: null }],
    equipments: [{ seqNo: 1, name: 'SW-01', assetNo: 'A001', modelNo: 'C9300', serialNo: 'S1', purpose: '核心', mgmtIp: '10.0.0.1' }],
    planSteps: [],
    schedule: { start: '2026-10-07 22:00', end: '2026-10-08 02:00', estHours: 4 },
    location: { sourceCode: 'MANUAL', areaName: 'A 區', rackName: 'R01', uRange: null, siteId: null, rackId: null, uStart: 10, uEnd: 12, omitReason: null },
    resubmitMemo: null,
    checklist: [{ seqNo: 1, code: 'C1', name: '確認備份', done: true, doneAt: '2026-10-07 22:10', executor: '王小明' }],
    execution: null,
    approval: {
      apprId: 5,
      statusCode: 'PENDING',
      startedAt: '2026-10-06 10:00',
      closedAt: null,
      steps: [
        { seqNo: 1, stepCode: 'MGR', stepName: '部門主管', stepMode: 'SEQUENTIAL', notifyOnly: false, statusCode: 'APPROVED', deciderName: '李主管', decidedAt: '2026-10-06 11:00', memo: '同意', candidateNames: ['李主管', '張主管'] },
        { seqNo: 2, stepCode: 'IT', stepName: '資訊主管', stepMode: 'SEQUENTIAL', notifyOnly: false, statusCode: 'PENDING', deciderName: null, decidedAt: null, memo: null, candidateNames: ['趙經理'] }
      ]
    },
    attachments: [{ attachId: 9, ownerType: 'APP', ownerId: 'IM20261006-001', fileName: '拓樸圖.pdf', byteQty: 2048, mimeType: 'application/pdf', uploadedAt: '2026-10-06 09:40' }],
    versions: [],
    events: [{ eventId: 1, verNo: 1, code: 'SUBMIT', userName: '王小明', at: '2026-10-06 10:00', memo: null }],
    permissions: NO_PERMS,
    createdAt: '2026-10-06 09:30',
    updatedAt: '2026-10-06 10:00',
    ...over
  }
}

async function mountView(id = 'IM20261006-001') {
  const router = makeRouter()
  await router.push('/apps/' + id)
  await router.isReady()
  const wrapper = mount(AppViewView, { global: { plugins: [router] } })
  await flushPromises()
  return { wrapper, router }
}

function toastMsgs(): string[] {
  return useToast().toasts.value.map(t => t.msg)
}

describe('AppViewView', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    getMock.mockReset()
  })

  afterEach(() => {
    vi.runAllTimers()
    vi.useRealTimers()
  })

  it('依路由 id 載入並顯示各區塊', async () => {
    getMock.mockResolvedValue(detail())
    const { wrapper } = await mountView()

    expect(getMock).toHaveBeenCalledWith('IM20261006-001')
    const text = wrapper.text()
    expect(text).toContain('更換交換器')
    expect(text).toContain('簽核中')
    expect(text).toContain('自行處理 (內部 IT) + 委外廠商')
    expect(text).toContain('遠端連線處理')
    expect(text).toContain('連線方式：VPN')
    expect(text).toContain('某廠商')
    expect(text).toContain('交換器')
    expect(text).toContain('其他：順便整線')
    expect(text).toContain('10-12')
    expect(text).toContain('手動填寫')
    expect(text).toContain('SW-01')
    expect(text).toContain('■')
    expect(text).toContain('（尚未填寫）')

    const signoff = wrapper.find('table.signoff')
    expect(signoff.text()).toContain('李主管 / 張主管')
    expect(signoff.text()).toContain('共 2 人，任一可簽')
    expect(signoff.text()).toContain('實際：李主管')
    expect(signoff.text()).toContain('同意')
    expect(signoff.text()).toContain('待簽核')
    expect(signoff.text()).toContain('已送出')
    expect(text).not.toContain('尚未送審')

    expect(text).toContain('拓樸圖.pdf')
    expect(text).toContain('2.0 KB')
    expect(text).toContain('附件下載待開放')
    const download = wrapper.find('.files button')
    expect(download.attributes('disabled')).toBeDefined()

    expect(text).toContain('送審')
    expect(wrapper.find('.actions').exists()).toBe(false)
    wrapper.unmount()
  })

  it('動作鈕依 permissions 顯示，點了出「此功能尚未開放」', async () => {
    getMock.mockResolvedValue(detail({ permissions: { ...NO_PERMS, canDecide: true, canRecall: true } }))
    const { wrapper } = await mountView()

    const buttons = wrapper.findAll('.actions button')
    expect(buttons.map(b => b.text())).toEqual(['簽核', '撤回到草稿'])
    await buttons[0].trigger('click')
    expect(toastMsgs()).toContain('此功能尚未開放')
    wrapper.unmount()
  })

  it('草稿的簽核欄註明尚未送審、申請人列顯示未送出', async () => {
    getMock.mockResolvedValue(
      detail({
        statusCode: 'DRAFT',
        approval: {
          apprId: null, statusCode: null, startedAt: null, closedAt: null,
          steps: [{ seqNo: 1, stepCode: 'MGR', stepName: '部門主管', stepMode: 'SEQUENTIAL', notifyOnly: false, statusCode: 'WAITING', deciderName: null, decidedAt: null, memo: null, candidateNames: ['李主管'] }]
        },
        events: []
      })
    )
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('尚未送審，以下為流程預定的關卡')
    const signoff = wrapper.find('table.signoff')
    expect(signoff.text()).toContain('未送出')
    expect(signoff.text()).toContain('未輪到')
    wrapper.unmount()
  })

  it('治理退回時執行確認列顯示退回與退回人', async () => {
    getMock.mockResolvedValue(
      detail({
        statusCode: 'REJECTED',
        events: [
          { eventId: 1, verNo: 1, code: 'SUBMIT', userName: '王小明', at: '2026-10-06 10:00', memo: null },
          { eventId: 2, verNo: 1, code: 'GOV_RETURN', userName: '治理員', at: '2026-10-09 09:00', memo: '紀錄不完整' }
        ]
      })
    )
    const { wrapper } = await mountView()

    const rows = wrapper.findAll('table.signoff tbody tr')
    const gov = rows[rows.length - 1]
    expect(gov.text()).toContain('退回')
    expect(gov.text()).toContain('治理員')
    expect(gov.text()).toContain('紀錄不完整')
    wrapper.unmount()
  })

  it('404 出 toast 並顯示錯誤，不顯示空白頁', async () => {
    getMock.mockRejectedValue(httpError(404, '找不到申請單'))
    const { wrapper } = await mountView('IM20261006-999')

    expect(toastMsgs()).toContain('找不到申請單')
    expect(wrapper.find('[role="alert"]').text()).toBe('找不到申請單')
    expect(wrapper.find('article').exists()).toBe(false)
    expect(wrapper.text()).toContain('回列表')
    wrapper.unmount()
  })

  it('401 不另出 toast（交給登入處理器）', async () => {
    getMock.mockRejectedValue(httpError(401, '尚未登入'))
    const before = toastMsgs().length
    const { wrapper } = await mountView()

    expect(toastMsgs()).toHaveLength(before)
    expect(wrapper.find('[role="alert"]').text()).toBe('尚未登入')
    wrapper.unmount()
  })

  it('路由 id 改變時重新載入', async () => {
    getMock.mockResolvedValue(detail())
    const { wrapper, router } = await mountView()

    getMock.mockResolvedValue(detail({ appId: 'IM20261006-002', title: '第二張' }))
    await router.push('/apps/IM20261006-002')
    await flushPromises()

    expect(getMock).toHaveBeenLastCalledWith('IM20261006-002')
    expect(wrapper.text()).toContain('第二張')
    wrapper.unmount()
  })
})
