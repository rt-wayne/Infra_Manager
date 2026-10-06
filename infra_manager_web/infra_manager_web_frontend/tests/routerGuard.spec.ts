// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：路由守衛測試（S2 回合三），用真的 router/index.ts、mock api/auth
//           驗：未登入進首頁 → /login；未登入進 /apps/1 → /login?redirect=/apps/1；
//           已登入進 /login → /；預設密碼進首頁 → /change-password；/me 打不到視為未登入並 toast
//           router 是模組單例，每個測試先用 startAt 把位置擺到與目標不同的路由（同路由的 push 不會觸發守衛）
// ============================================================
import { beforeEach, describe, expect, it, vi } from 'vitest'
import router from '../src/router'
import { getMe } from '../src/api/auth'
import { resetAuthStateForTest } from '../src/composables/useAuth'
import { useToast } from '../src/composables/useToast'
import type { MeResponse } from '../src/types/auth'

vi.mock('../src/api/auth', () => ({
  getMe: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
  changePassword: vi.fn()
}))

const meMock = vi.mocked(getMe)
const anonymous: MeResponse = { loggedIn: false }
const normal: MeResponse = { loggedIn: true, userId: 'T0001', loginId: 'wayne', userName: 'Wayne', roles: ['admin'], mustChangePassword: false }
const defaultPwd: MeResponse = { ...normal, mustChangePassword: true }

/** 以指定的登入狀態把 router 擺到 path，然後清掉狀態與 mock 紀錄，讓接下來的導航重新查 /me */
async function startAt(path: string, me: MeResponse): Promise<void> {
  resetAuthStateForTest()
  meMock.mockResolvedValue(me)
  await router.push(path)
  resetAuthStateForTest()
  meMock.mockReset()
  useToast().toasts.value = []
}

describe('路由守衛', () => {
  beforeEach(() => {
    meMock.mockReset()
    resetAuthStateForTest()
  })

  it('未登入進首頁導到登入頁，不帶 redirect', async () => {
    await startAt('/login', anonymous)
    meMock.mockResolvedValue(anonymous)
    await router.push('/')
    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.redirect).toBeUndefined()
    expect(meMock).toHaveBeenCalledTimes(1)
  })

  it('未登入進內頁導到登入頁並記住原路徑', async () => {
    await startAt('/login', anonymous)
    meMock.mockResolvedValue(anonymous)
    await router.push('/change-password')
    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.redirect).toBe('/change-password')
  })

  it('已登入再進登入頁會回首頁', async () => {
    await startAt('/', normal)
    meMock.mockResolvedValue(normal)
    await router.push('/login')
    expect(router.currentRoute.value.path).toBe('/')
  })

  it('預設密碼者只能到改密碼頁', async () => {
    await startAt('/login', anonymous)
    meMock.mockResolvedValue(defaultPwd)
    await router.push('/')
    expect(router.currentRoute.value.path).toBe('/change-password')
  })

  it('/me 打不到時視為未登入並出 toast', async () => {
    await startAt('/change-password', normal)
    meMock.mockRejectedValue(new Error('network'))
    await router.push('/')
    expect(router.currentRoute.value.path).toBe('/login')
    expect(useToast().toasts.value.map(t => t.msg)).toContain('無法連線後端服務，請稍後再試')
  })
})
