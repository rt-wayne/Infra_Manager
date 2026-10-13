// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: 新增：首頁測試（BACKLOG 第 46 項 S8）
//           第一階段六張卡回歸（名稱、數字、載入失敗顯示「—」並 toast、「30 天內到期」依 ACC_LIST_FULL 分流）；
//           緊急存取兩張卡：數字、點擊依 EMG_VIEW_ALL 分流（有 → 清單帶 state，沒有 → 無權限頁）
// ============================================================
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import type { VueWrapper } from '@vue/test-utils'
import type { Router } from 'vue-router'
import HomeView from '../src/views/HomeView.vue'
import { getHomeStats } from '../src/api/common'
import { resetSession, useSession } from '../src/composables/useSession'
import type { HomeStats } from '../src/types/common'
import { GENERAL_PERMS, httpError, lastToast, makeMe, makeRouter } from './helpers'

vi.mock('../src/api/common', () => ({
  getCodes: vi.fn(),
  getHomeStats: vi.fn(),
  listExpiring: vi.fn(),
  listTodos: vi.fn()
}))

const statsMock = vi.mocked(getHomeStats)

const STATS: HomeStats = {
  acctTotal: 25, acctReviewing: 10, acctExpiring: 4, acctPriv: 3,
  inspThisMonth: 7, inspExcptThisMonth: 2,
  emgUndecided: 5, emgReturned: 1
}

describe('首頁', () => {
  let router: Router

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    resetSession()
    useSession().me.value = makeMe()
    statsMock.mockReset().mockResolvedValue({ ...STATS })
  })

  afterEach(() => {
    vi.runAllTimers()
    vi.useRealTimers()
  })

  async function mountHome(): Promise<VueWrapper> {
    router = await makeRouter('/')
    const w = mount(HomeView, { global: { plugins: [router] } })
    await flushPromises()
    return w
  }

  /** 每張卡「標籤 → 數字」 */
  function cards(w: VueWrapper): Record<string, string> {
    return Object.fromEntries(w.findAll('.stat-card').map(c => [c.find('.lbl').text(), c.find('.num').text()]))
  }

  it('三個區塊八張卡：第一階段六張名稱與數字不變，緊急存取兩張帶後端數字', async () => {
    const w = await mountHome()
    expect(w.findAll('.module-card h2').map(h => h.text())).toEqual(['🔑 帳號權限管理', '🚨 緊急存取', '🛠 機房巡檢'])
    expect(cards(w)).toEqual({
      '帳號總數': '25',
      '待審核': '10',
      '30 天內到期（含已逾期）⚠️': '4',
      '特權帳號': '3',
      '緊急存取待補核准': '5',
      '緊急存取異常待追蹤': '1',
      '本月巡檢次數': '7',
      '本月發現異常項目數（未處理）': '2'
    })
    w.unmount()
  })

  it('數字 0 照常顯示；載入失敗八張卡都顯示「—」並 toast 後端訊息', async () => {
    statsMock.mockResolvedValueOnce({ ...STATS, emgUndecided: 0, emgReturned: 0 })
    let w = await mountHome()
    expect(w.find('.card-emg-undecided .num').text()).toBe('0')
    expect(w.find('.card-emg-returned .num').text()).toBe('0')
    w.unmount()

    statsMock.mockRejectedValueOnce(httpError(500, { message: '資料庫連線失敗' }))
    w = await mountHome()
    expect(Object.values(cards(w))).toEqual(Array(8).fill('—'))
    expect(lastToast(w)).toBe('資料庫連線失敗')
    w.unmount()
  })

  it('「30 天內到期」：有 ACC_LIST_FULL → 帳號列表到期篩選；沒有 → 無權限頁', async () => {
    let w = await mountHome()
    await w.find('.card-expiring').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/no-access')
    w.unmount()

    useSession().me.value = makeMe({ permissions: [...GENERAL_PERMS, 'ACC_LIST_FULL'] })
    w = await mountHome()
    await w.find('.card-expiring').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/accounts?expiring=1')
    w.unmount()
  })

  it('緊急存取兩張卡：沒有 EMG_VIEW_ALL → 無權限頁（清單只看得到自己的單，筆數對不上）', async () => {
    for (const sel of ['.card-emg-undecided', '.card-emg-returned']) {
      const w = await mountHome()
      await w.find(sel).trigger('click')
      await flushPromises()
      expect(router.currentRoute.value.path).toBe('/no-access')
      w.unmount()
    }
  })

  it('緊急存取兩張卡：有 EMG_VIEW_ALL → 清單並帶 state（待補核准 UNDECIDED、異常待追蹤 RETURNED）', async () => {
    useSession().me.value = makeMe({ permissions: [...GENERAL_PERMS, 'EMG_VIEW_ALL'] })
    const expected: [string, string][] = [['.card-emg-undecided', 'UNDECIDED'], ['.card-emg-returned', 'RETURNED']]
    for (const [sel, state] of expected) {
      const w = await mountHome()
      await w.find(sel).trigger('click')
      await flushPromises()
      expect(router.currentRoute.value.path).toBe('/emergency')
      expect(router.currentRoute.value.query).toEqual({ state })
      w.unmount()
    }
  })
})
