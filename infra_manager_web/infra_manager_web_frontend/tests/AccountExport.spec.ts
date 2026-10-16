// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: 新增：帳號列表匯出 xlsx 測試（BACKLOG 第 80 項）
//           儲存格文字：8 欄標題、類別帶「特權」、狀態帶「審查異常」與「待複驗」／「申請處理中」擇一、門禁卡帳號名稱與所屬系統、
//           到期日永久／指定日、資產分類未分類、一律排除已刪除；檔名 帳號權限清單_YYYYMMDD.xlsx；標題列粗體、凍結、欄寬 8 欄
//           畫面：只對 ACC_EXPORT 顯示；匯出全部分頁與前端「未分類」篩選結果；狀態篩選「已刪除」或沒有資料時停用並提示；
//           匯出失敗 toast；write-excel-file 以 vi.mock 替換，不產生實際檔案
// ============================================================
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import AccountListView from '../src/views/AccountListView.vue'
import { getCodes, listExpiring } from '../src/api/common'
import { listAccounts } from '../src/api/account'
import { resetSession, useSession } from '../src/composables/useSession'
import type { Account, AccountListResult } from '../src/types/account'
import { EXPORT_HEADERS, buildExportRows, exportFileName } from '../src/utils/accountExport'
import { CODES, GENERAL_PERMS, lastToast, makeMe, makeRouter } from './helpers'

const xlsx = vi.hoisted(() => {
  const toFile = vi.fn()
  const write = vi.fn(() => ({ toFile, toBlob: vi.fn() }))
  return { toFile, write }
})
vi.mock('write-excel-file/browser', () => ({ default: xlsx.write }))
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

const accountsMock = vi.mocked(listAccounts)

function acct(i: number, over: Partial<Account> = {}): Account {
  return {
    acctId: `AR20261007-${String(i).padStart(3, '0')}`, catgId: 'HOST', acctName: `user${i}`, sysName: 'sys', doorScopeCode: null,
    assetClassId: null, isPriv: 0, holderName: 'h', holderDept: 'd', holderCntct: null, holderEmail: null,
    accessLevelCode: 'RO', accessTarget: null, applyReason: null, expiryTypeCode: 'PERM', expiryDate: null,
    acctStateCode: 'APPROVED', inFlight: false, pendingVerify: false, allowedReqTypes: [], ...over
  }
}

function listResult(items: Account[]): AccountListResult {
  return { items, stateCounts: { APPROVED: items.length }, catgCounts: { HOST: items.length }, expiringCount: 0 }
}

async function mountList(path = '/accounts') {
  const router = await makeRouter(path)
  const w = mount(AccountListView, { global: { plugins: [router] } })
  await flushPromises()
  return w
}

describe('帳號匯出：儲存格內容', () => {
  it('8 欄標題；類別帶特權、狀態帶審查異常與待複驗／申請處理中擇一；門禁卡、到期日、未分類同畫面；排除已刪除', () => {
    const rows = buildExportRows(CODES, [
      acct(1, { isPriv: 1, assetClassId: 'H01', expiryTypeCode: 'DATE', expiryDate: '2026-12-31 00:00:00' }),
      acct(2, { isReviewFlag: 1, inFlight: true, pendingVerify: true }),
      acct(3, { inFlight: true, acctStateCode: 'EXPIRED' }),
      acct(4, { catgId: 'DOOR', acctName: null, sysName: null, doorScopeCode: 'FULL', holderName: null }),
      acct(5, { acctStateCode: 'DELETED' })
    ])
    expect(rows[0]).toEqual(EXPORT_HEADERS)
    expect(rows[0]).toEqual(['單號', '類別', '帳號名稱', '所屬系統', '使用人', '資產分類', '到期日', '狀態'])
    expect(rows).toHaveLength(5)
    expect(rows[1]).toEqual(['AR20261007-001', '主機帳號、特權', 'user1', 'sys', 'h', 'H01', '2026-12-31', '已核准'])
    expect(rows[2][7]).toBe('已核准、審查異常、待複驗')
    expect(rows[3][7]).toBe('已逾期、申請處理中')
    expect(rows[3][6]).toBe('永久')
    expect(rows[4].slice(1, 6)).toEqual(['機房門禁卡', '（門禁卡，待發卡）', '機房完整權限（兩道門）', '—', '未分類'])
    expect(rows.some(r => r[0] === 'AR20261007-005')).toBe(false)
  })

  it('檔名：帳號權限清單_YYYYMMDD.xlsx（本機日期）', () => {
    expect(exportFileName(new Date(2026, 9, 6))).toBe('帳號權限清單_20261006.xlsx')
  })
})

describe('帳號列表：匯出 Excel 按鈕', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    resetSession()
    useSession().me.value = makeMe({ permissions: [...GENERAL_PERMS, 'ACC_LIST_FULL', 'ACC_EXPORT'] })
    vi.mocked(getCodes).mockReset().mockResolvedValue(CODES)
    vi.mocked(listExpiring).mockReset().mockResolvedValue([])
    accountsMock.mockReset()
    xlsx.write.mockClear()
    xlsx.toFile.mockReset().mockResolvedValue(undefined)
  })

  afterEach(() => {
    vi.runAllTimers()
    vi.useRealTimers()
  })

  it('沒有 ACC_EXPORT 不顯示', async () => {
    useSession().me.value = makeMe({ permissions: [...GENERAL_PERMS, 'ACC_LIST_FULL'] })
    accountsMock.mockResolvedValue(listResult([acct(1)]))
    const w = await mountList()
    expect(w.find('.export-btn').exists()).toBe(false)
    w.unmount()
  })

  it('匯出全部分頁（25 筆，不只第 1 頁的 20 筆）；標題列粗體、凍結、8 欄寬；檔名帶日期', async () => {
    accountsMock.mockResolvedValue(listResult(Array.from({ length: 25 }, (_, i) => acct(i + 1))))
    const w = await mountList()
    expect(w.findAll('tbody tr')).toHaveLength(20)
    await w.find('.export-btn').trigger('click')
    await flushPromises()
    expect(xlsx.write).toHaveBeenCalledTimes(1)
    const [data, opts] = xlsx.write.mock.calls[0] as unknown as [unknown[][], Record<string, unknown>]
    expect(data).toHaveLength(26)
    expect(data[0][0]).toEqual({ value: '單號', fontWeight: 'bold' })
    expect(data[25][0]).toBe('AR20261007-025')
    expect(opts).toMatchObject({ sheet: '帳號權限清單', stickyRowsCount: 1 })
    expect(opts.columns).toHaveLength(8)
    expect(xlsx.toFile.mock.calls[0][0]).toMatch(/^帳號權限清單_\d{8}\.xlsx$/)
    w.unmount()
  })

  it('匯出套用前端「未分類」篩選', async () => {
    accountsMock.mockResolvedValue(listResult([acct(1, { assetClassId: 'H01' }), acct(2)]))
    const w = await mountList()
    await w.find('select[aria-label=資產分類碼]').setValue('__NONE__')
    await flushPromises()
    await w.find('.export-btn').trigger('click')
    await flushPromises()
    const [data] = xlsx.write.mock.calls[0] as unknown as [unknown[][]]
    expect(data.map(r => r[0])).toEqual([{ value: '單號', fontWeight: 'bold' }, 'AR20261007-002'])
    w.unmount()
  })

  it('狀態篩選「已刪除」：停用並提示，不匯出', async () => {
    accountsMock.mockResolvedValue(listResult([acct(1)]))
    const w = await mountList()
    accountsMock.mockResolvedValue(listResult([acct(1, { acctStateCode: 'DELETED' })]))
    await w.find('select.state-filter').setValue('DELETED')
    await flushPromises()
    expect((w.find('.export-btn').element as HTMLButtonElement).disabled).toBe(true)
    expect(w.find('.export-hint').text()).toBe('已刪除的帳號不提供匯出，請改選其他狀態')
    await w.find('.export-btn').trigger('click')
    expect(xlsx.write).not.toHaveBeenCalled()
    w.unmount()
  })

  it('沒有資料：停用並提示', async () => {
    accountsMock.mockResolvedValue(listResult([]))
    const w = await mountList()
    expect((w.find('.export-btn').element as HTMLButtonElement).disabled).toBe(true)
    expect(w.find('.export-hint').text()).toBe('目前沒有可匯出的帳號')
    w.unmount()
  })

  it('匯出失敗：toast，按鈕恢復可按', async () => {
    accountsMock.mockResolvedValue(listResult([acct(1)]))
    xlsx.toFile.mockRejectedValueOnce(new Error('worker blocked'))
    const w = await mountList()
    await w.find('.export-btn').trigger('click')
    await flushPromises()
    expect(lastToast(w)).toBe('匯出失敗，請稍後再試')
    expect((w.find('.export-btn').element as HTMLButtonElement).disabled).toBe(false)
    expect(w.find('.export-btn').text()).toBe('匯出 Excel')
    w.unmount()
  })
})
