// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：清單畫面查詢失敗／空結果測試（開發文件 spec_im_acct_insp.md 第 6 節）
//           失敗一律 toast（後端回 {message} 時顯示該訊息），「查無資料」只在成功回空陣列時顯示；每頁 20 筆
//           變更單 #1：S1 帳號申請單列表的 lookup 結果列，無帳號列表權限時不顯示「查看」「歷史」，異動按鈕照舊
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（開發文件 spec_im_acct_insp_v2.0.md 第 4.8、4.13、4.14、8.3、8.4 節）：
//           帳號列表：U14 橫幅與到期篩選、8 類頁籤數量、狀態數量含已逾期、待複驗標記、依權限顯示異動；
//           申請單列表：完整帳號名稱查詢（U05／U06）、在途不顯示操作、U10、退件原因與重新送出；
//           巡檢紀錄：巡檢人員代理身分顯示；歷史表格：操作人「（以 ○○ 身分）」與 U12 標籤
// ============================================================
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import type { Component } from 'vue'
import AccountListView from '../src/views/AccountListView.vue'
import InspectionListView from '../src/views/InspectionListView.vue'
import RequestListView from '../src/views/RequestListView.vue'
import HistoryTable from '../src/components/HistoryTable.vue'
import { getCodes, listExpiring } from '../src/api/common'
import { listAccounts, listMyAccounts, lookupAccounts } from '../src/api/account'
import { listInspections } from '../src/api/inspection'
import { listMyRequests } from '../src/api/request'
import { resetSession, useSession } from '../src/composables/useSession'
import type { Account, AccountListResult, AccountLookup } from '../src/types/account'
import { CODES, GENERAL_PERMS, httpError, lastToast, makeMe, makeRouter } from './helpers'

vi.mock('../src/api/common', () => ({
  getCodes: vi.fn(),
  getHomeStats: vi.fn(),
  listExpiring: vi.fn(),
  listTodos: vi.fn(),
  listEmgReturned: vi.fn().mockResolvedValue([])
}))
vi.mock('../src/api/account', () => ({
  listAccounts: vi.fn(),
  getAccount: vi.fn(),
  getAccountHistory: vi.fn(),
  listMyAccounts: vi.fn(),
  lookupAccounts: vi.fn(),
  getLookupAccount: vi.fn()
}))
vi.mock('../src/api/request', () => ({
  submitRequest: vi.fn(),
  listMyRequests: vi.fn(),
  getRequest: vi.fn(),
  approveRequest: vi.fn(),
  rejectRequest: vi.fn(),
  resubmitRequest: vi.fn(),
  execDone: vi.fn(),
  verifyOk: vi.fn(),
  verifyReject: vi.fn()
}))
vi.mock('../src/api/inspection', () => ({
  listInspItems: vi.fn(),
  submitInspection: vi.fn(),
  listInspections: vi.fn(),
  getInspection: vi.fn(),
  fixInspItem: vi.fn()
}))

const accountsMock = vi.mocked(listAccounts)
const inspMock = vi.mocked(listInspections)

async function mountView(view: Component, path: string) {
  const router = await makeRouter(path)
  const w = mount(view, { global: { plugins: [router] } })
  await flushPromises()
  return w
}

function acct(i: number, catgId = 'HOST'): Account {
  return {
    acctId: `AR20261007-${String(i).padStart(3, '0')}`, catgId, acctName: `user${i}`, sysName: 'sys', doorScopeCode: null,
    assetClassId: null, isPriv: 0, holderName: 'h', holderDept: 'd', holderCntct: null, holderEmail: null,
    accessLevelCode: 'RO', accessTarget: null, applyReason: null, expiryTypeCode: 'PERM', expiryDate: null,
    acctStateCode: 'APPROVED', inFlight: false, pendingVerify: false, allowedReqTypes: ['MODIFY', 'DISABLE', 'DELETE']
  }
}

function listResult(items: Account[], over: Partial<AccountListResult> = {}): AccountListResult {
  return { items, stateCounts: { APPROVED: items.length }, catgCounts: { HOST: items.length }, expiringCount: 0, ...over }
}

describe('帳號列表', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    resetSession()
    useSession().me.value = makeMe({ permissions: [...GENERAL_PERMS, 'ACC_LIST_FULL'] })
    vi.mocked(getCodes).mockReset().mockResolvedValue(CODES)
    vi.mocked(listExpiring).mockReset().mockResolvedValue([])
    accountsMock.mockReset()
  })

  afterEach(() => {
    vi.runAllTimers()
    vi.useRealTimers()
  })

  it('查詢失敗：toast U09，不顯示查無資料', async () => {
    accountsMock.mockRejectedValue(new Error('network'))
    const w = await mountView(AccountListView, '/accounts')
    expect(lastToast(w)).toBe('查詢失敗，請稍後再試')
    expect(w.find('.empty').exists()).toBe(false)
    w.unmount()
  })

  it('後端回 403：toast 顯示後端 message', async () => {
    accountsMock.mockRejectedValue(httpError(403, { message: '您沒有執行此操作的權限' }))
    const w = await mountView(AccountListView, '/accounts')
    expect(lastToast(w)).toBe('您沒有執行此操作的權限')
    w.unmount()
  })

  it('成功回空清單才顯示查無資料', async () => {
    accountsMock.mockResolvedValue(listResult([]))
    const w = await mountView(AccountListView, '/accounts')
    expect(w.find('.empty').exists()).toBe(true)
    w.unmount()
  })

  it('每頁 20 筆；8 類頁籤都顯示並用 catgCounts；狀態含已逾期與數量；待複驗標記；依 allowedReqTypes 顯示異動', async () => {
    const list = Array.from({ length: 25 }, (_, i) => acct(i + 1))
    list[0] = { ...list[0], acctStateCode: 'DISABLED', allowedReqTypes: ['ENABLE', 'DELETE'] }
    list[1] = { ...list[1], inFlight: true, pendingVerify: true, allowedReqTypes: [] }
    list[2] = { ...list[2], acctStateCode: 'EXPIRED', allowedReqTypes: ['DISABLE', 'DELETE'] }
    accountsMock.mockResolvedValue(listResult(list, {
      stateCounts: { APPROVED: 22, EXPIRED: 1, DISABLED: 1, REVIEW: 1, DELETED: 4 },
      catgCounts: { HOST: 20, DB: 5 }
    }))
    const w = await mountView(AccountListView, '/accounts')
    expect(w.findAll('tbody tr')).toHaveLength(20)
    expect(w.text()).toContain('共 25 筆，第 1 / 2 頁')

    expect(w.findAll('.cat-tab')).toHaveLength(CODES.categories.length + 1)
    expect(w.find('.cat-tab[data-catg=DB] .cat-count').text()).toBe('5')
    expect(w.find('.cat-tab[data-catg=SVC] .cat-count').text()).toBe('0')
    expect(w.find('.cat-tab:not([data-catg]) .cat-count').text()).toBe('25')

    const stateOpts = w.find('select.state-filter').findAll('option').map(o => o.text())
    expect(stateOpts[0]).toContain('全部狀態（不含已刪除）')
    expect(stateOpts[0]).toContain('25')
    expect(stateOpts.some(t => t.startsWith('已逾期') && t.includes('1'))).toBe(true)

    const rows = w.findAll('tbody tr')
    expect(rows[0].text()).toContain('重新啟用')
    expect(rows[0].text()).not.toContain('修改')
    expect(rows[1].find('.pending-verify').text()).toBe('待複驗')
    expect(rows[1].find('.op-modify').exists()).toBe(false)
    expect(rows[2].text()).toContain('已逾期')
    expect(rows[2].find('.op-disable').exists()).toBe(true)
    expect(rows[2].find('.op-modify').exists()).toBe(false)
    expect(rows[3].find('.op-modify').exists()).toBe(true)
    expect(w.text()).not.toContain('展延')

    await w.find('.cat-tab[data-catg=DB]').trigger('click')
    await flushPromises()
    expect(accountsMock.mock.calls.at(-1)?.[0]).toMatchObject({ catgId: 'DB' })
    w.unmount()
  })

  it('U14 橫幅：點擊套用到期篩選（expiring=1）；從 query expiring=1 進來預設套用', async () => {
    accountsMock.mockResolvedValue(listResult([acct(1)], { expiringCount: 3 }))
    const w = await mountView(AccountListView, '/accounts')
    expect(accountsMock.mock.calls[0][0].expiring).toBe('')
    expect(w.find('.expiry-banner').text()).toContain('有 3 個帳號 30 天內到期或已逾期')
    await w.find('.expiry-banner').trigger('click')
    await flushPromises()
    expect(accountsMock.mock.calls.at(-1)?.[0]).toMatchObject({ expiring: '1' })
    expect(w.find('.filter-chip').exists()).toBe(true)
    w.unmount()

    accountsMock.mockClear()
    const w2 = await mountView(AccountListView, '/accounts?expiring=1')
    expect(accountsMock.mock.calls[0][0]).toMatchObject({ expiring: '1' })
    w2.unmount()
  })

  it('沒有 ACC_APPLY 時不顯示異動按鈕', async () => {
    useSession().me.value = makeMe({ permissions: ['ACC_LIST_FULL'] })
    accountsMock.mockResolvedValue(listResult([acct(1)]))
    const w = await mountView(AccountListView, '/accounts')
    expect(w.find('tbody .op-modify').exists()).toBe(false)
    expect(w.find('tbody .op-view').exists()).toBe(true)
    w.unmount()
  })
})

describe('帳號申請單列表', () => {
  const LOOKUP: AccountLookup[] = [{
    acctId: 'AR20261001-001', catgId: 'DB', catgName: '資料庫帳號', acctName: 'sa', sysName: '財務 Oracle',
    holderName: '王小明', acctStateCode: 'APPROVED', expiryTypeCode: 'PERM', expiryDate: null,
    inFlight: false, allowedReqTypes: ['MODIFY', 'DISABLE', 'DELETE']
  }]

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    resetSession()
    useSession().me.value = makeMe()
    vi.mocked(getCodes).mockReset().mockResolvedValue(CODES)
    vi.mocked(listMyAccounts).mockReset().mockResolvedValue([])
    vi.mocked(listMyRequests).mockReset().mockResolvedValue([])
    vi.mocked(lookupAccounts).mockReset().mockResolvedValue(LOOKUP)
  })

  afterEach(() => {
    vi.runAllTimers()
    vi.useRealTimers()
  })

  async function lookup(name: string) {
    const w = await mountView(RequestListView, '/requests')
    await w.find('form.lookup input').setValue(name)
    await w.find('form.lookup').trigger('submit')
    await flushPromises()
    return w
  }

  it('以完整帳號名稱查詢（acctName），顯示 U05；無 ACC_LIST_FULL 不顯示查看／歷史，異動按鈕照舊並帶 acctName', async () => {
    const w = await lookup('  sa ')
    expect(lookupAccounts).toHaveBeenCalledWith('sa')
    expect(w.find('.lookup-hint').text()).toBe('請輸入完整帳號名稱（不提供模糊查詢）')
    const r = w.find('.lookup-row')
    expect(r.find('.op-view').exists()).toBe(false)
    expect(r.find('.op-modify').exists()).toBe(true)
    expect(r.find('.op-disable').exists()).toBe(true)
    w.unmount()
  })

  it('查無顯示 U06', async () => {
    vi.mocked(lookupAccounts).mockResolvedValue([])
    const w = await lookup('s')
    expect(w.find('.lookup-none').text()).toBe('查無此帳號，請確認輸入的是完整帳號名稱')
    w.unmount()
  })

  it('查詢失敗只 toast，不顯示成查無', async () => {
    vi.mocked(lookupAccounts).mockRejectedValue(new Error('network'))
    const w = await lookup('sa')
    expect(lastToast(w)).toBe('查詢失敗，請稍後再試')
    expect(w.find('.lookup-none').exists()).toBe(false)
    w.unmount()
  })

  it('結果列在途時不顯示操作按鈕', async () => {
    vi.mocked(lookupAccounts).mockResolvedValue([{ ...LOOKUP[0], inFlight: true, allowedReqTypes: [] }])
    const w = await lookup('sa')
    const r = w.find('.lookup-row')
    expect(r.find('.in-flight').exists()).toBe(true)
    expect(r.find('.ops').exists()).toBe(false)
    w.unmount()
  })

  it('我的申請單：審核中顯示 U10、已退件顯示原因與重新送出', async () => {
    vi.mocked(listMyRequests).mockResolvedValue([
      { reqId: 'AR1', acctId: 'AR1', reqTypeCode: 'ADD', reqStateCode: 'REVIEW', currStageId: 3, stageName: 'Infra 主管', catgId: 'DB', acctName: 'sa', sysName: 's', submitDate: '2026-10-07 09:00:00' },
      { reqId: 'AR2', acctId: 'AR2', reqTypeCode: 'ADD', reqStateCode: 'REJECTED', currStageId: null, catgId: 'DB', acctName: 'sb', sysName: 's', submitDate: '2026-10-07 09:00:00', rejectReason: '名稱不符', canResubmit: true }
    ])
    const w = await mountView(RequestListView, '/requests')
    expect(w.find('tr[data-req=AR1] .req-state').text()).toBe('審核中：待 Infra 主管')
    expect(w.find('tr[data-req=AR1] .resubmit').exists()).toBe(false)
    expect(w.find('tr[data-req=AR2] .reject-reason').text()).toContain('名稱不符')
    expect(w.find('tr[data-req=AR2] .resubmit').exists()).toBe(true)
    expect(w.findAll('select[aria-label=申請單狀態] option').map(o => o.text())).toEqual(['全部', '審核中', '待執行', '待複驗', '已核准', '已退件'])
    w.unmount()
  })
})

describe('巡檢紀錄', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    resetSession()
    useSession().me.value = makeMe({ permissions: ['INSP_VIEW'] })
    inspMock.mockReset()
  })

  afterEach(() => {
    vi.runAllTimers()
    vi.useRealTimers()
  })

  it('預設送近 30 天；查詢失敗只 toast', async () => {
    inspMock.mockRejectedValue(new Error('network'))
    const w = await mountView(InspectionListView, '/inspections')
    const q = inspMock.mock.calls[0][0]
    expect(q.dateFrom).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(q.dateTo).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(lastToast(w)).toBe('查詢失敗，請稍後再試')
    expect(w.find('.empty').exists()).toBe(false)
    w.unmount()
  })

  it('巡檢人員依 4.14 顯示代理身分；狀態用後端計算值；沒有 INSP_CREATE 不顯示新增巡檢', async () => {
    inspMock.mockResolvedValue([
      { inspId: 'IN1', inspDate: '2026-10-07 09:00:00', inspByName: 'Hank (黃志翰)', actAsName: 'Kevin (李凱鈞)', excptCnt: 2, unfixedCnt: 1, inspStateCode: 'EXCPT' },
      { inspId: 'IN2', inspDate: '2026-10-07 10:00:00', inspByName: 'Kevin (李凱鈞)', actAsName: null, excptCnt: 0, unfixedCnt: 0, inspStateCode: 'NORMAL' }
    ])
    const w = await mountView(InspectionListView, '/inspections')
    const rows = w.findAll('tbody tr')
    expect(rows[0].find('.inspector').text()).toBe('Kevin (李凱鈞)（實際操作人：Hank (黃志翰)）')
    expect(rows[0].text()).toContain('有異常')
    expect(rows[0].text()).toContain('未處理 1 項')
    expect(rows[1].find('.inspector').text()).toBe('Kevin (李凱鈞)')
    expect(rows[1].text()).toContain('正常結案')
    expect(w.text()).not.toContain('新增巡檢')
    w.unmount()
  })

  it('成功回空陣列才顯示查無資料', async () => {
    inspMock.mockResolvedValue([])
    const w = await mountView(InspectionListView, '/inspections')
    expect(w.find('.empty').exists()).toBe(true)
    w.unmount()
  })
})

describe('歷史表格', () => {
  it('操作人顯示快照姓名與「（以 ○○ 身分）」；IS_SELF_ACTION＝1 顯示 U12 標籤；系統排程', () => {
    const w = mount(HistoryTable, {
      props: {
        rows: [
          { histId: 1, actDate: '2026-10-07 09:00:00', actionCode: 'APPROVE', stageName: '資訊主管', actBy: '0000011', actByName: 'Hank (黃志翰)', actAsId: '0000010', actAsName: 'Gary (許永成)', isSelfAction: 0 },
          { histId: 2, actDate: '2026-10-07 10:00:00', actionCode: 'APPROVE', stageName: '資訊治理', actBy: '0000001', actByName: 'Kevin (李凱鈞)', isSelfAction: 1, selfActionLabel: '自行核准' },
          { histId: 3, actDate: '2026-10-08 00:10:00', actionCode: 'EXPIRE', actBy: null, actByName: '系統排程', isSelfAction: 0 }
        ]
      }
    })
    const rows = w.findAll('tbody tr')
    expect(rows[0].find('.actor').text()).toBe('Hank (黃志翰)（以 Gary (許永成) 身分）')
    expect(rows[0].find('.self-action').exists()).toBe(false)
    expect(rows[1].find('.actor').text()).toBe('Kevin (李凱鈞)')
    expect(rows[1].find('.self-action').text()).toBe('自行核准')
    expect(rows[2].text()).toContain('到期轉為已逾期')
    expect(rows[2].find('.actor').text()).toBe('系統排程')
    w.unmount()
  })
})
