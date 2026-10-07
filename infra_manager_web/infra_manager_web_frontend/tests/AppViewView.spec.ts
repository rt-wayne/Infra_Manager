// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-06
// 變更說明: 新增：申請單檢視頁測試（S4 回合三）；以 vi.mock 替換 getApp，不打真後端
//           驗：依路由 id 載入；狀態中文、簽核關卡與候選人、附件 KB 與停用的下載鈕；
//           動作鈕依 permissions 顯示、點了出「此功能尚未開放」；草稿簽核欄的「尚未送審」註記；
//           執行確認列依治理事件顯示退回；404 出 toast 並顯示錯誤；401 不另出 toast；換 id 重新載入
//           S4 審查修正：類別「其他」補充的顯示；結果未填時例外／後續追蹤顯示 —；快速切換單號丟棄過期回應；
//           「送審」改為只在事件紀錄表斷言（原斷言會被任何位置的同字命中）
//           S6 回合二 a（Claude Opus 5.5，2026-10-06）：測試資料補 rowVerNo、formOptionId（型別新增必填欄位）
//           S6 回合一-2（2026-10-07）：下載鈕改為可用；下載先 HEAD 再開 <a download>、404 toast、401 不出 toast
//           S6 回合四（2026-10-07）：「編輯草稿」鈕導到 /apps/:id/edit
//           S9 R3（Claude Opus 5.5，2026-10-07）：「尚未開放」改用還沒做的執行紀錄鈕（簽核、撤回 S7 已開放）；
//           補件鈕導到 /apps/:id/resubmit；刪除面板（單號一致＋原因才能按、成功回列表、409 重載並收起面板）；歷次簽核掛在版次下
//           S10 R3（Claude Opus 5.5，2026-10-07）：「尚未開放」改用 AI 風險審查鈕（執行紀錄已開放）；檢核項測試資料補 userId／executorDesc；
//           執行鈕導到 /apps/:id/execute；治理審核面板（通過帶空意見、退回空白擋下、confirm 取消不送、409 重載並收起面板）
// ============================================================
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { AxiosError, AxiosHeaders } from 'axios'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import AppViewView from '../src/views/AppViewView.vue'
import { checkAttachment, deleteApp, getApp, reviewExecution } from '../src/api/apps'
import { useToast } from '../src/composables/useToast'
import { APP_EDIT_ROUTE, APP_EXECUTE_ROUTE, APP_LIST_ROUTE, APP_RESUBMIT_ROUTE, APP_VIEW_ROUTE } from '../src/router/names'
import type { AppDetail, AppPermissions } from '../src/types/app'

vi.mock('../src/api/apps', async importOriginal => ({
  ...(await importOriginal<typeof import('../src/api/apps')>()),
  getApp: vi.fn(),
  checkAttachment: vi.fn(),
  deleteApp: vi.fn(),
  reviewExecution: vi.fn()
}))

const getMock = vi.mocked(getApp)
const checkMock = vi.mocked(checkAttachment)
const deleteMock = vi.mocked(deleteApp)
const reviewMock = vi.mocked(reviewExecution)
const Blank = { template: '<div />' }

function makeRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/apps', name: APP_LIST_ROUTE, component: Blank },
      { path: '/apps/:id', name: APP_VIEW_ROUTE, component: AppViewView },
      { path: '/apps/:id/edit', name: APP_EDIT_ROUTE, component: Blank },
      { path: '/apps/:id/resubmit', name: APP_RESUBMIT_ROUTE, component: Blank },
      { path: '/apps/:id/execute', name: APP_EXECUTE_ROUTE, component: Blank }
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
    rowVerNo: 0,
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
    categories: [{ formOptionId: 11, groupCode: 'CATG_ITEM', code: 'NET_SW', name: '交換器', upCode: 'NET', otherText: null }],
    reasons: [{ formOptionId: 21, groupCode: 'REASON', code: 'EOL', name: '設備汰換', upCode: null, otherText: null }],
    otherReason: '順便整線',
    scopes: [{ formOptionId: 31, groupCode: 'SCOPE', code: 'NET', name: '網路', upCode: null, otherText: null }],
    equipments: [{ seqNo: 1, name: 'SW-01', assetNo: 'A001', modelNo: 'C9300', serialNo: 'S1', purpose: '核心', mgmtIp: '10.0.0.1' }],
    planSteps: [],
    schedule: { start: '2026-10-07 22:00', end: '2026-10-08 02:00', estHours: 4 },
    location: { sourceCode: 'MANUAL', areaName: 'A 區', rackName: 'R01', uRange: null, siteId: null, rackId: null, uStart: 10, uEnd: 12, omitReason: null },
    resubmitMemo: null,
    checklist: [{ seqNo: 1, code: 'C1', name: '確認備份', done: true, doneAt: '2026-10-07 22:10', executor: '王小明', userId: '00001', executorDesc: null }],
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
    approvalHistory: [],
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

/** 找表頭為 label 的那一列，回傳它的第一個資料格文字 */
function cellOf(wrapper: Awaited<ReturnType<typeof mountView>>['wrapper'], label: string): string | undefined {
  const row = wrapper.findAll('tr').find(tr => tr.findAll('th').some(th => th.text() === label))
  return row?.findAll('td')[0]?.text()
}

function toastMsgs(): string[] {
  return useToast().toasts.value.map(t => t.msg)
}

describe('AppViewView', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    getMock.mockReset()
    deleteMock.mockReset()
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
    expect(text).not.toContain('附件下載待開放')
    const download = wrapper.find('.files button')
    expect(download.attributes('disabled')).toBeUndefined()

    const events = wrapper.findAll('table.grid').at(-1)
    expect(events?.text()).toContain('送審')
    expect(wrapper.find('.actions').exists()).toBe(false)
    wrapper.unmount()
  })

  it('下載：HEAD 確認後用 <a download> 開附件網址', async () => {
    getMock.mockResolvedValue(detail())
    checkMock.mockResolvedValue(undefined)
    const clicked: string[] = []
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      clicked.push(this.getAttribute('href') + '|' + this.hasAttribute('download'))
    })
    const { wrapper } = await mountView()

    await wrapper.find('.files button').trigger('click')
    await flushPromises()

    expect(checkMock).toHaveBeenCalledWith('IM20261006-001', 9)
    expect(clicked).toEqual(['/infra_manager_web/api/v1/apps/IM20261006-001/attachments/9|true'])
    expect(document.querySelector('a[download]')).toBeNull()
    click.mockRestore()
    wrapper.unmount()
  })

  it('下載：HEAD 404 出 toast「找不到附件檔案」、不開連結；401 不另出 toast', async () => {
    getMock.mockResolvedValue(detail())
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    const { wrapper } = await mountView()

    checkMock.mockRejectedValueOnce(httpError(404, ''))
    await wrapper.find('.files button').trigger('click')
    await flushPromises()
    expect(toastMsgs()).toContain('找不到附件檔案')

    useToast().toasts.value.splice(0)
    checkMock.mockRejectedValueOnce(httpError(401, '尚未登入'))
    await wrapper.find('.files button').trigger('click')
    await flushPromises()
    expect(toastMsgs()).toEqual([])

    expect(click).not.toHaveBeenCalled()
    expect(wrapper.find('.files button').attributes('disabled')).toBeUndefined()
    click.mockRestore()
    wrapper.unmount()
  })

  it('動作鈕依 permissions 顯示，還沒做的出「此功能尚未開放」', async () => {
    getMock.mockResolvedValue(detail({ permissions: { ...NO_PERMS, canDecide: true, canRecall: true, canAiReview: true } }))
    const { wrapper } = await mountView()

    const buttons = wrapper.findAll('.actions button')
    expect(buttons.map(b => b.text())).toEqual(['AI 風險審查', '簽核', '撤回到草稿'])
    const before = toastMsgs().filter(m => m === '此功能尚未開放').length
    await buttons[0].trigger('click')
    expect(toastMsgs().filter(m => m === '此功能尚未開放').length).toBe(before + 1)
    wrapper.unmount()
  })

  it('填寫執行紀錄鈕導到執行頁', async () => {
    getMock.mockResolvedValue(detail({ statusCode: 'APPROVED', permissions: { ...NO_PERMS, canExecute: true } }))
    const { wrapper, router } = await mountView()

    await wrapper.findAll('.actions button').find(b => b.text() === '填寫執行紀錄')?.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/apps/IM20261006-001/execute')
    wrapper.unmount()
  })

  it('治理審核：通過可不填意見，帶 rowVerNo 呼叫 API、toast 並重新載入', async () => {
    getMock.mockResolvedValue(detail({ statusCode: 'PENDING_REVIEW', rowVerNo: 8, permissions: { ...NO_PERMS, canReview: true } }))
    reviewMock.mockResolvedValue({ appId: 'IM20261006-001', rowVerNo: 9, statusCode: 'EXECUTED' })
    const { wrapper } = await mountView()

    await wrapper.findAll('.actions button').find(b => b.text() === '治理審核')?.trigger('click')
    expect(wrapper.find('#review-memo').exists()).toBe(true)
    getMock.mockResolvedValue(detail({ statusCode: 'EXECUTED', rowVerNo: 9 }))
    await wrapper.findAll('button').find(b => b.text().includes('通過'))?.trigger('click')
    await flushPromises()

    expect(reviewMock).toHaveBeenCalledWith('IM20261006-001', { rowVerNo: 8, decision: 'PASS', memo: '' })
    expect(toastMsgs()).toContain('已通過，申請單結案')
    expect(getMock).toHaveBeenCalledTimes(2)
    expect(wrapper.find('#review-memo').exists()).toBe(false)
    wrapper.unmount()
  })

  it('治理審核退回：意見空白擋下；confirm 取消不送；確認後 409 toast、重載並收起面板', async () => {
    getMock.mockResolvedValue(detail({ statusCode: 'PENDING_REVIEW', rowVerNo: 3, permissions: { ...NO_PERMS, canReview: true } }))
    reviewMock.mockReset()
    reviewMock.mockRejectedValue(httpError(409, '申請單不是待治理審核狀態，無法審核，請重新載入頁面'))
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)
    const { wrapper } = await mountView()

    await wrapper.findAll('.actions button').find(b => b.text() === '治理審核')?.trigger('click')
    const returnBtn = () => wrapper.findAll('button').find(b => b.text().includes('退回'))
    await returnBtn()?.trigger('click')
    expect(toastMsgs()).toContain('退回請填寫意見，讓申請人知道要補什麼')
    expect(confirm).not.toHaveBeenCalled()

    await wrapper.find('#review-memo').setValue('  檢核表第 3 項未完成  ')
    await returnBtn()?.trigger('click')
    expect(confirm).toHaveBeenCalledTimes(1)
    expect(reviewMock).not.toHaveBeenCalled()

    confirm.mockReturnValue(true)
    getMock.mockResolvedValue(detail({ statusCode: 'EXECUTED', rowVerNo: 4 }))
    await returnBtn()?.trigger('click')
    await flushPromises()

    expect(reviewMock).toHaveBeenCalledWith('IM20261006-001', { rowVerNo: 3, decision: 'RETURN', memo: '檢核表第 3 項未完成' })
    expect(toastMsgs()).toContain('申請單不是待治理審核狀態，無法審核，請重新載入頁面')
    expect(getMock).toHaveBeenCalledTimes(2)
    expect(wrapper.find('#review-memo').exists()).toBe(false)
    confirm.mockRestore()
    wrapper.unmount()
  })

  it('補件重送鈕導到補件頁', async () => {
    getMock.mockResolvedValue(detail({ statusCode: 'REJECTED', permissions: { ...NO_PERMS, canResubmit: true } }))
    const { wrapper, router } = await mountView()

    await wrapper.findAll('.actions button').find(b => b.text() === '補件重送')?.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/apps/IM20261006-001/resubmit')
    wrapper.unmount()
  })

  it('刪除：單號一致且有原因才能按；成功帶本文呼叫 API、回列表並 toast', async () => {
    getMock.mockResolvedValue(detail({ rowVerNo: 6, permissions: { ...NO_PERMS, canDelete: true, deleteMode: 'ADMIN' } }))
    deleteMock.mockResolvedValue({ appId: 'IM20261006-001' })
    const { wrapper, router } = await mountView()

    await wrapper.findAll('.actions button').find(b => b.text() === '刪除申請單')?.trigger('click')
    const confirmBtn = () => wrapper.findAll('button').find(b => b.text().includes('確認刪除'))
    expect(wrapper.find('.del').text()).toContain('管理員刪除')
    expect(confirmBtn()?.attributes('disabled')).toBeDefined()

    await wrapper.find('#del-id').setValue('IM20261006-002')
    await wrapper.find('#del-reason').setValue('重複建單')
    expect(confirmBtn()?.attributes('disabled')).toBeDefined()

    await wrapper.find('#del-id').setValue(' IM20261006-001 ')
    expect(confirmBtn()?.attributes('disabled')).toBeUndefined()
    await confirmBtn()?.trigger('click')
    await flushPromises()

    expect(deleteMock).toHaveBeenCalledWith('IM20261006-001', { rowVerNo: 6, confirmId: 'IM20261006-001', reason: '重複建單' })
    expect(toastMsgs()).toContain('已刪除申請單 IM20261006-001')
    expect(router.currentRoute.value.fullPath).toBe('/apps')
    wrapper.unmount()
  })

  it('刪除 409：toast 後端訊息、重新載入，新狀態不能刪就收起面板', async () => {
    getMock.mockResolvedValue(detail({ permissions: { ...NO_PERMS, canDelete: true, deleteMode: 'APPLICANT_PRE_REVIEW' } }))
    deleteMock.mockRejectedValue(httpError(409, '申請單已在其他地方修改過，請重新載入頁面'))
    const { wrapper, router } = await mountView()

    await wrapper.findAll('.actions button').find(b => b.text() === '刪除申請單')?.trigger('click')
    await wrapper.find('#del-id').setValue('IM20261006-001')
    await wrapper.find('#del-reason').setValue('不需要了')
    getMock.mockResolvedValue(detail({ rowVerNo: 1, permissions: NO_PERMS }))
    await wrapper.findAll('button').find(b => b.text().includes('確認刪除'))?.trigger('click')
    await flushPromises()

    expect(toastMsgs()).toContain('申請單已在其他地方修改過，請重新載入頁面')
    expect(getMock).toHaveBeenCalledTimes(2)
    expect(wrapper.find('.del').exists()).toBe(false)
    expect(router.currentRoute.value.fullPath).toBe('/apps/IM20261006-001')
    wrapper.unmount()
  })

  it('歷次簽核掛在所屬版次底下；目前版次撤回的那輪另成一組', async () => {
    const step = { seqNo: 1, stepCode: 'MGR', stepName: '部門主管', stepMode: 'SEQUENTIAL', notifyOnly: false, decidedAt: '2026-10-05 11:00', candidateNames: [] }
    getMock.mockResolvedValue(
      detail({
        verNo: 2,
        versions: [{ verNo: 1, closeStatusCode: 'REJECTED', reason: '請補回復計畫', snapAt: '2026-10-05 12:00' }],
        approvalHistory: [
          { apprId: 3, verNo: 1, statusCode: 'REJECTED', startedAt: '2026-10-05 10:00', closedAt: '2026-10-05 11:00',
            steps: [{ ...step, statusCode: 'REJECTED', deciderName: '李主管', memo: '請補回復計畫' }] },
          { apprId: 4, verNo: 2, statusCode: 'RECALLED', startedAt: '2026-10-06 08:00', closedAt: '2026-10-06 08:30',
            steps: [{ ...step, statusCode: 'CANCELLED', deciderName: null, decidedAt: null, memo: null }] }
        ]
      })
    )
    const { wrapper } = await mountView()

    const groups = wrapper.findAll('.ver')
    expect(groups).toHaveLength(2)
    expect(groups[0].text()).toContain('v1')
    expect(groups[0].text()).toContain('簽核退件')
    expect(groups[0].find('.past').text()).toContain('李主管')
    expect(groups[0].find('.past').text()).toContain('退件')
    expect(groups[1].text()).toContain('目前版次')
    expect(groups[1].find('.past').text()).toContain('已撤回')
    wrapper.unmount()
  })

  it('編輯草稿鈕導到編輯頁', async () => {
    getMock.mockResolvedValue(detail({ statusCode: 'DRAFT', permissions: { ...NO_PERMS, canEditDraft: true } }))
    const { wrapper, router } = await mountView()

    const btn = wrapper.findAll('.actions button').find(b => b.text() === '編輯草稿')
    await btn?.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/apps/IM20261006-001/edit')
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

  it('類別「其他」補充顯示為「類別（其他）：…」', async () => {
    getMock.mockResolvedValue(
      detail({
        categories: [
          { formOptionId: 1, groupCode: 'CATG', code: 'server_storage', name: '伺服器/儲存', upCode: null, otherText: 'SAN 擴充櫃 DAE-02' },
          { formOptionId: 2, groupCode: 'CATG_ITEM', code: 'server_storage_01', name: '伺服器上架', upCode: 'server_storage', otherText: null }
        ]
      })
    )
    const { wrapper } = await mountView()

    const chips = wrapper.findAll('.chips li').map(li => li.text())
    expect(chips).toContain('伺服器/儲存（其他）：SAN 擴充櫃 DAE-02')
    expect(chips).toContain('伺服器上架')
    wrapper.unmount()
  })

  it('執行紀錄已建立但結果未填時，例外與後續追蹤顯示 —；填了才顯示有／無', async () => {
    const ex = {
      verNo: 1, actualStart: '2026-10-07 22:00', actualEnd: null, resultCode: null, resultName: null,
      exception: false, exceptionDesc: null, followUp: false, followUpDesc: null, memo: null, executorName: '王小明', closedAt: null
    }
    getMock.mockResolvedValue(detail({ statusCode: 'IN_EXECUTION', execution: ex }))
    const { wrapper } = await mountView()
    expect(cellOf(wrapper, '例外／衍生事件')).toBe('—')
    expect(cellOf(wrapper, '後續追蹤事項')).toBe('—')
    wrapper.unmount()

    getMock.mockResolvedValue(detail({ statusCode: 'PENDING_REVIEW', execution: { ...ex, resultCode: 'OK', resultName: '成功', followUp: true } }))
    const second = await mountView()
    expect(cellOf(second.wrapper, '例外／衍生事件')).toBe('無')
    expect(cellOf(second.wrapper, '後續追蹤事項')).toBe('有')
    second.wrapper.unmount()
  })

  it('快速切換單號時，慢回來的舊回應不會蓋掉新單', async () => {
    let resolveOld: (d: AppDetail) => void = () => {}
    getMock.mockImplementationOnce(() => new Promise<AppDetail>(r => { resolveOld = r }))
    const { wrapper, router } = await mountView()

    getMock.mockResolvedValueOnce(detail({ appId: 'IM20261006-002', title: '新單' }))
    await router.push('/apps/IM20261006-002')
    await flushPromises()
    resolveOld(detail({ title: '舊單' }))
    await flushPromises()

    expect(wrapper.text()).toContain('新單')
    expect(wrapper.text()).not.toContain('舊單')
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
