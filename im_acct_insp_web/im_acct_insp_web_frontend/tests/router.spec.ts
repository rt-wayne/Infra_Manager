// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：登入守衛與權限碼守衛測試（開發文件 spec_im_acct_insp_v2.0.md 第 8.1、8.3 節）
//           沒有 token → 登入頁（帶 redirect）；已登入開登入頁 → 原路由或首頁；meta.need 權限碼不符 → /no-access；
//           /me 回 401 → 登入頁；任何 API 401 → 導回登入頁並記下後端 message
// ============================================================
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AxiosError } from 'axios'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { Router } from 'vue-router'
import { defineComponent, h } from 'vue'
import http from '../src/api/http'
import { getMe } from '../src/api/auth'
import { clearToken, getToken, setToken } from '../src/api/tokenStore'
import { resetSession, useSession } from '../src/composables/useSession'
import { authGuard, installUnauthorizedHandler, safeRedirect } from '../src/router'
import type { PermCode } from '../src/utils/perms'
import { httpError, makeMe } from './helpers'

vi.mock('../src/api/auth', () => ({
  login: vi.fn(),
  logout: vi.fn(),
  getMe: vi.fn(),
  listUsers: vi.fn(),
  impersonate: vi.fn(),
  endImpersonate: vi.fn()
}))
vi.mock('../src/api/common', () => ({
  getCodes: vi.fn(),
  getHomeStats: vi.fn(),
  listExpiring: vi.fn().mockResolvedValue([]),
  listTodos: vi.fn().mockResolvedValue([])
}))

const Stub = defineComponent({ render: () => h('div') })

/** 與正式路由相同的 meta，元件換成空殼 */
function makeGuardedRouter(): Router {
  const need = (p: PermCode) => ({ need: p })
  const r = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', component: Stub, meta: { public: true } },
      { path: '/', component: Stub },
      { path: '/todo', component: Stub },
      { path: '/accounts', component: Stub, meta: need('ACC_LIST_FULL') },
      { path: '/requests', component: Stub, meta: need('ACC_SELF_SERVICE') },
      { path: '/requests/new', component: Stub, meta: need('ACC_APPLY') },
      { path: '/inspections', component: Stub, meta: need('INSP_VIEW') },
      { path: '/inspections/new', component: Stub, meta: need('INSP_CREATE') },
      { path: '/no-access', component: Stub }
    ]
  })
  r.beforeEach(authGuard)
  installUnauthorizedHandler(r)
  return r
}

const meMock = vi.mocked(getMe)

describe('全域路由守衛', () => {
  beforeEach(() => {
    sessionStorage.clear()
    resetSession()
    useSession().loginNotice.value = ''
    meMock.mockReset().mockResolvedValue(makeMe())
  })

  it('沒有 token：任何頁都導向登入頁並帶 redirect（首頁不帶）', async () => {
    const r = makeGuardedRouter()
    await r.push('/todo')
    expect(r.currentRoute.value.path).toBe('/login')
    expect(r.currentRoute.value.query.redirect).toBe('/todo')
    expect(meMock).not.toHaveBeenCalled()

    await r.push('/')
    expect(r.currentRoute.value.fullPath).toBe('/login')
  })

  it('沒有 token 時登入頁可進入', async () => {
    const r = makeGuardedRouter()
    await r.push('/login')
    expect(r.currentRoute.value.path).toBe('/login')
  })

  it('已登入開登入頁：回 redirect 指定的站內路徑，否則首頁', async () => {
    setToken('tok')
    const r = makeGuardedRouter()
    await r.push('/login?redirect=/todo')
    expect(r.currentRoute.value.path).toBe('/todo')
    await r.push('/login?redirect=//evil.example.com')
    expect(r.currentRoute.value.path).toBe('/')
  })

  it('權限碼守衛：只有 GENERAL 權限時帳號列表、巡檢 → 無權限頁；申請單列表、新增申請可進', async () => {
    setToken('tok')
    const r = makeGuardedRouter()
    await r.push('/accounts')
    expect(r.currentRoute.value.path).toBe('/no-access')
    await r.push('/inspections')
    expect(r.currentRoute.value.path).toBe('/no-access')
    await r.push('/requests')
    expect(r.currentRoute.value.path).toBe('/requests')
    await r.push('/requests/new')
    expect(r.currentRoute.value.path).toBe('/requests/new')
    expect(meMock).toHaveBeenCalledTimes(1)
  })

  it('權限碼守衛：有 ACC_LIST_FULL、INSP_VIEW 可進；沒有 INSP_CREATE 不能進新增巡檢', async () => {
    setToken('tok')
    meMock.mockResolvedValue(makeMe({ permissions: ['ACC_SELF_SERVICE', 'ACC_LIST_FULL', 'INSP_VIEW'] }))
    const r = makeGuardedRouter()
    await r.push('/accounts')
    expect(r.currentRoute.value.path).toBe('/accounts')
    await r.push('/inspections')
    expect(r.currentRoute.value.path).toBe('/inspections')
    await r.push('/inspections/new')
    expect(r.currentRoute.value.path).toBe('/no-access')
  })

  it('/me 回 401（token 失效）：導向登入頁並保留原目標', async () => {
    setToken('tok-expired')
    meMock.mockImplementation(() => {
      // http.ts 的 401 interceptor 會先清掉 token
      clearToken()
      return Promise.reject(httpError(401, { message: '登入已逾時，請重新登入' }, '/me'))
    })
    const r = makeGuardedRouter()
    await r.push('/todo')
    expect(r.currentRoute.value.path).toBe('/login')
    expect(r.currentRoute.value.query.redirect).toBe('/todo')
  })

  it('使用中收到 401：清 token、導回登入頁（帶目前路徑）並記下後端 message', async () => {
    setToken('tok')
    const r = makeGuardedRouter()
    await r.push('/requests')
    expect(r.currentRoute.value.path).toBe('/requests')

    await expect(
      http.get('/requests', {
        adapter: config =>
          Promise.reject(
            new AxiosError('401', 'ERR_BAD_REQUEST', config, null, {
              data: { message: '登入已逾時，請重新登入' }, status: 401, statusText: '', headers: {}, config
            })
          )
      })
    ).rejects.toBeTruthy()
    await vi.waitFor(() => expect(r.currentRoute.value.path).toBe('/login'))
    expect(r.currentRoute.value.query.redirect).toBe('/requests')
    expect(getToken()).toBe('')
    expect(useSession().loginNotice.value).toBe('登入已逾時，請重新登入')
    expect(useSession().me.value).toBeNull()
  })

  it('safeRedirect 只接受站內路徑', () => {
    expect(safeRedirect('/accounts?expiring=1')).toBe('/accounts?expiring=1')
    expect(safeRedirect('https://x.example.com')).toBe('/')
    expect(safeRedirect('//x.example.com')).toBe('/')
    expect(safeRedirect('/login?redirect=/x')).toBe('/')
    expect(safeRedirect(undefined)).toBe('/')
  })
})
