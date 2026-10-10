// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：版面框架測試（開發文件 spec_im_acct_insp_v2.0.md 第 8.2 節）
//           導覽依 permissions[]（無 ACC_LIST_FULL 時「帳號列表」指向申請單列表、巡檢區依 INSP_VIEW／INSP_CREATE）；
//           canImpersonate 才顯示「切換身分」；切換中固定顯示 U01＋切回；切回後目前頁無權限導向首頁；
//           鈴鐺：無 ACC_LIST_FULL 點一筆開唯讀明細；登出清 token 回登入頁
// ============================================================
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import type { VueWrapper } from '@vue/test-utils'
import { RouterView, createMemoryHistory, createRouter } from 'vue-router'
import type { Router } from 'vue-router'
import { defineComponent, h } from 'vue'
import AppLayout from '../src/layouts/AppLayout.vue'
import { endImpersonate, getMe, logout } from '../src/api/auth'
import { getAccount } from '../src/api/account'
import { listExpiring, listTodos } from '../src/api/common'
import { getToken, setToken } from '../src/api/tokenStore'
import { resetSession } from '../src/composables/useSession'
import type { Me } from '../src/types/common'
import { GENERAL_PERMS, makeMe } from './helpers'

vi.mock('../src/api/auth', () => ({
  login: vi.fn(),
  logout: vi.fn(),
  getMe: vi.fn(),
  listUsers: vi.fn(),
  impersonate: vi.fn(),
  endImpersonate: vi.fn()
}))
vi.mock('../src/api/common', () => ({
  getCodes: vi.fn().mockResolvedValue({ categories: [], assetClasses: [], stages: [] }),
  getHomeStats: vi.fn(),
  listExpiring: vi.fn(),
  listTodos: vi.fn()
}))
vi.mock('../src/api/account', () => ({
  listAccounts: vi.fn(),
  getAccount: vi.fn(),
  getAccountHistory: vi.fn(),
  listMyAccounts: vi.fn(),
  lookupAccounts: vi.fn(),
  getLookupAccount: vi.fn()
}))

const Stub = defineComponent({ render: () => h('div', { class: 'page-stub' }) })

async function mountLayout(me: Me, path = '/'): Promise<{ w: VueWrapper; router: Router }> {
  vi.mocked(getMe).mockResolvedValue(me)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', component: Stub },
      {
        path: '/',
        component: AppLayout,
        children: [
          { path: '', component: Stub },
          { path: 'todo', component: Stub },
          { path: 'accounts', component: Stub, meta: { need: 'ACC_LIST_FULL' } },
          { path: 'requests', component: Stub, meta: { need: 'ACC_SELF_SERVICE' } },
          { path: 'requests/new', component: Stub, meta: { need: 'ACC_APPLY' } },
          { path: 'assets', component: Stub },
          { path: 'inspections', component: Stub, meta: { need: 'INSP_VIEW' } },
          { path: 'inspections/new', component: Stub, meta: { need: 'INSP_CREATE' } }
        ]
      }
    ]
  })
  await router.push(path)
  await router.isReady()
  const app = mount(defineComponent({ render: () => h(RouterView) }), { global: { plugins: [router] } })
  await flushPromises()
  return { w: app, router }
}

describe('AppLayout', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    sessionStorage.clear()
    resetSession()
    setToken('tok')
    vi.mocked(getMe).mockReset()
    vi.mocked(logout).mockReset().mockResolvedValue(undefined)
    vi.mocked(endImpersonate).mockReset()
    vi.mocked(listTodos).mockReset().mockResolvedValue([])
    vi.mocked(listExpiring).mockReset().mockResolvedValue([])
    vi.mocked(getAccount).mockReset()
  })

  afterEach(() => {
    vi.runAllTimers()
    vi.useRealTimers()
  })

  it('只有 GENERAL：帳號列表指向申請單列表、不顯示巡檢區與切換身分；顯示登入者與登出', async () => {
    const { w } = await mountLayout(makeMe())
    expect(w.find('.nav-accounts').attributes('href')).toBe('/requests')
    expect(w.find('.nav-insp').exists()).toBe(false)
    expect(w.find('.imp-start').exists()).toBe(false)
    expect(w.find('.imp-banner').exists()).toBe(false)
    expect(w.find('.who').text()).toContain('Kevin (李凱鈞)')
    expect(w.find('.logout').exists()).toBe(true)
    expect(w.find('select[aria-label=檢視身分]').exists()).toBe(false)
    w.unmount()
  })

  it('有 ACC_LIST_FULL、INSP_VIEW（無 INSP_CREATE）：帳號列表指向 /accounts、顯示巡檢紀錄但不顯示新增巡檢', async () => {
    const { w } = await mountLayout(makeMe({ permissions: [...GENERAL_PERMS, 'ACC_LIST_FULL', 'INSP_VIEW'] }))
    expect(w.find('.nav-accounts').attributes('href')).toBe('/accounts')
    expect(w.find('.nav-insp').exists()).toBe(true)
    expect(w.find('.nav-insp-new').exists()).toBe(false)
    w.unmount()
  })

  it('canImpersonate 才顯示「切換身分」', async () => {
    const { w } = await mountLayout(makeMe({ canImpersonate: true }))
    expect(w.find('.imp-start').exists()).toBe(true)
    w.unmount()
  })

  it('切換中固定顯示 U01＋切回；切回後目前頁無權限 → 首頁', async () => {
    const gary = makeMe({
      userId: '0000010', userName: 'Gary (許永成)', realUserId: '0000011', realUserName: 'Hank (黃志翰)',
      impersonating: true, permissions: [...GENERAL_PERMS, 'ACC_EXPIRY_ALL', 'APPR_G5', 'INSP_VIEW']
    })
    const { w, router } = await mountLayout(gary, '/inspections')
    expect(w.find('.imp-banner').text()).toContain('目前以 Gary (許永成) 身分操作')
    expect(w.find('.imp-end').text()).toBe('切回')
    expect(w.find('.imp-start').exists()).toBe(false)
    expect(w.find('.who').text()).toContain('Hank (黃志翰)')

    vi.mocked(endImpersonate).mockResolvedValue(makeMe({
      userId: '0000011', userName: 'Hank (黃志翰)', realUserId: '0000011', realUserName: 'Hank (黃志翰)', canImpersonate: true,
      permissions: [...GENERAL_PERMS, 'SYS_IMPERSONATE']
    }))
    await w.find('.imp-end').trigger('click')
    await flushPromises()
    expect(endImpersonate).toHaveBeenCalledTimes(1)
    expect(router.currentRoute.value.path).toBe('/')
    expect(w.find('.imp-banner').exists()).toBe(false)
    expect(w.find('.imp-start').exists()).toBe(true)
    w.unmount()
  })

  it('鈴鐺：無 ACC_LIST_FULL 點一筆開唯讀明細（不顯示異動按鈕）；無相關到期時沒有徽章', async () => {
    vi.mocked(listExpiring).mockResolvedValue([
      { acctId: 'AR1', acctName: 'dbuser', catgId: 'DB', catgName: '資料庫帳號', acctStateCode: 'APPROVED', expiryDate: '2026-10-17', daysLeft: 10 },
      { acctId: 'AR2', acctName: 'hostuser', catgId: 'HOST', catgName: '主機帳號', acctStateCode: 'EXPIRED', expiryDate: '2026-10-01', daysLeft: -6 }
    ])
    vi.mocked(getAccount).mockResolvedValue({
      acctId: 'AR1', catgId: 'DB', acctName: 'dbuser', sysName: 's', doorScopeCode: null, assetClassId: null, isPriv: 0,
      holderName: 'h', holderDept: 'd', holderCntct: null, holderEmail: null, accessLevelCode: 'RO', accessTarget: null,
      applyReason: null, expiryTypeCode: 'DATE', expiryDate: '2026-10-17', acctStateCode: 'APPROVED', allowedReqTypes: ['MODIFY']
    })
    const { w } = await mountLayout(makeMe())
    expect(w.find('.bell-badge').text()).toBe('2')
    await w.find('.bell-btn').trigger('click')
    expect(w.findAll('.bell-item')[1].text()).toContain('已逾期 6 天')
    await w.findAll('.bell-item')[0].trigger('click')
    await flushPromises()
    expect(getAccount).toHaveBeenCalledWith('AR1')
    expect(w.find('.modal').text()).toContain('dbuser')
    expect(w.find('.modal .op-modify').exists()).toBe(false)
    w.unmount()

    resetSession()
    vi.mocked(listExpiring).mockResolvedValue([])
    const { w: w2 } = await mountLayout(makeMe())
    expect(w2.find('.bell-badge').exists()).toBe(false)
    w2.unmount()
  })

  it('登出：呼叫後端、清 token、回登入頁', async () => {
    const { w, router } = await mountLayout(makeMe())
    await w.find('.logout').trigger('click')
    await flushPromises()
    expect(logout).toHaveBeenCalledTimes(1)
    expect(getToken()).toBe('')
    expect(router.currentRoute.value.path).toBe('/login')
    w.unmount()
  })
})
