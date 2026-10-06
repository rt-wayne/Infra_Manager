// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-06
// 變更說明: 新增：401 處理器測試（S4 回合二，BACKLOG 第 92 項 ⑥⑦）；用 axios adapter 直接回 401，不打真後端
//           驗：/auth/login 的 401（帳密錯誤）不觸發處理器；同時多支 API 401 只重查一次 /me、只出一次 toast、只導一次頁
// ============================================================
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import http, { setUnauthorizedHandler } from '../src/api/http'
import { getMe } from '../src/api/auth'
import { resetAuthStateForTest, useAuth } from '../src/composables/useAuth'
import { useToast } from '../src/composables/useToast'

vi.mock('../src/api/auth', () => ({
  getMe: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
  changePassword: vi.fn()
}))

const getMeMock = vi.mocked(getMe)
const Blank = { template: '<div />' }

function reject401(config: InternalAxiosRequestConfig): Promise<never> {
  const response = { status: 401, statusText: '', headers: {}, config: { headers: new AxiosHeaders() }, data: { message: '尚未登入' } }
  return Promise.reject(new AxiosError('401', '401', config, null, response))
}

async function makeRouter(): Promise<Router> {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: Blank },
      { path: '/login', component: Blank },
      { path: '/apps', component: Blank }
    ]
  })
  await router.push('/apps?q=x')
  await router.isReady()
  return router
}

function expiredToasts(): number {
  return useToast().toasts.value.filter(t => t.msg === '登入已過期，請重新登入').length
}

describe('401 處理器', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    getMeMock.mockReset()
    getMeMock.mockResolvedValue({ loggedIn: false })
    resetAuthStateForTest()
  })

  afterEach(() => {
    setUnauthorizedHandler(null)
    vi.runAllTimers()
    vi.useRealTimers()
  })

  it('/auth/login 回 401 時不觸發處理器', async () => {
    const router = await makeRouter()
    useAuth().installUnauthorizedHandler(router)

    await expect(http.post('/auth/login', {}, { adapter: reject401 })).rejects.toBeInstanceOf(AxiosError)
    await flushPromises()

    expect(getMeMock).not.toHaveBeenCalled()
    expect(router.currentRoute.value.path).toBe('/apps')
  })

  it('同時三支 API 回 401 只重查一次、只出一次 toast、只導一次頁', async () => {
    const router = await makeRouter()
    const push = vi.spyOn(router, 'push')
    useAuth().installUnauthorizedHandler(router)
    const before = expiredToasts()

    await Promise.allSettled([
      http.get('/apps', { adapter: reject401 }),
      http.get('/apps', { adapter: reject401 }),
      http.get('/options', { adapter: reject401 })
    ])
    await flushPromises()

    expect(getMeMock).toHaveBeenCalledTimes(1)
    expect(expiredToasts() - before).toBe(1)
    expect(push).toHaveBeenCalledTimes(1)
    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.redirect).toBe('/apps?q=x')
  })

  it('重查 /me 仍是登入狀態時不導頁也不出 toast', async () => {
    getMeMock.mockResolvedValue({ loggedIn: true, userId: 'T0001', loginId: 'wayne', userName: 'Wayne', roles: [], mustChangePassword: false })
    const router = await makeRouter()
    useAuth().installUnauthorizedHandler(router)
    const before = expiredToasts()

    await Promise.allSettled([http.get('/apps', { adapter: reject401 })])
    await flushPromises()

    expect(expiredToasts()).toBe(before)
    expect(router.currentRoute.value.path).toBe('/apps')
  })
})
