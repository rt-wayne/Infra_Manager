// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：登入頁測試（S2 回合三）；以 vi.mock 替換 api/auth，不打真後端
//           驗：空欄位不送出；401 顯示後端訊息（不是查無資料）；成功且非預設密碼回 redirect；預設密碼導改密碼頁；
//           redirect 為外站網址時回首頁（open redirect）
// ============================================================
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { AxiosError, AxiosHeaders } from 'axios'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import LoginView from '../src/views/LoginView.vue'
import { login } from '../src/api/auth'
import { resetAuthStateForTest, safeRedirect } from '../src/composables/useAuth'

vi.mock('../src/api/auth', () => ({
  getMe: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
  changePassword: vi.fn()
}))

const loginMock = vi.mocked(login)
const Blank = { template: '<div />' }

function makeRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: Blank },
      { path: '/login', component: LoginView },
      { path: '/change-password', component: Blank },
      { path: '/apps/1', component: Blank }
    ]
  })
}

function httpError(status: number, message: string): AxiosError {
  const err = new AxiosError(message, String(status))
  err.response = { status, statusText: '', headers: {}, config: { headers: new AxiosHeaders() }, data: { message } }
  return err
}

async function mountAt(router: Router, path: string) {
  await router.push(path)
  await router.isReady()
  const wrapper = mount(LoginView, { global: { plugins: [router] } })
  await flushPromises()
  return wrapper
}

describe('LoginView', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    loginMock.mockReset()
    resetAuthStateForTest()
  })

  afterEach(() => {
    vi.runAllTimers()
    vi.useRealTimers()
  })

  it('帳號或密碼空白時不送出並提示', async () => {
    const router = makeRouter()
    const wrapper = await mountAt(router, '/login')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(loginMock).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('請輸入帳號與密碼')
    wrapper.unmount()
  })

  it('401 時顯示後端訊息、留在登入頁', async () => {
    loginMock.mockRejectedValue(httpError(401, '帳號或密碼錯誤'))
    const router = makeRouter()
    const wrapper = await mountAt(router, '/login')
    await wrapper.find('#loginId').setValue(' Wayne ')
    await wrapper.find('#password').setValue('x')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(loginMock).toHaveBeenCalledWith({ loginId: 'Wayne', password: 'x' })
    expect(wrapper.text()).toContain('帳號或密碼錯誤')
    expect(wrapper.text()).not.toContain('查無資料')
    expect(router.currentRoute.value.path).toBe('/login')
    wrapper.unmount()
  })

  it('成功且非預設密碼時回 redirect 指定的站內路徑', async () => {
    loginMock.mockResolvedValue({ loggedIn: true, userId: 'T0001', loginId: 'wayne', userName: 'Wayne', roles: ['admin'], mustChangePassword: false })
    const router = makeRouter()
    const wrapper = await mountAt(router, '/login?redirect=/apps/1')
    await wrapper.find('#loginId').setValue('wayne')
    await wrapper.find('#password').setValue('secret')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/apps/1')
    wrapper.unmount()
  })

  it('成功但仍是預設密碼時導到改密碼頁', async () => {
    loginMock.mockResolvedValue({ loggedIn: true, userId: 'T0001', loginId: 'wayne', userName: 'Wayne', roles: ['admin'], mustChangePassword: true })
    const router = makeRouter()
    const wrapper = await mountAt(router, '/login')
    await wrapper.find('#loginId').setValue('wayne')
    await wrapper.find('#password').setValue('wayne')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/change-password')
    expect(wrapper.text()).toContain('首次登入請先修改預設密碼')
    wrapper.unmount()
  })

  it('safeRedirect 只接受站內路徑', () => {
    expect(safeRedirect('/apps/1')).toBe('/apps/1')
    expect(safeRedirect('//evil.example')).toBe('/')
    expect(safeRedirect('https://evil.example')).toBe('/')
    expect(safeRedirect('/login?redirect=/x')).toBe('/')
    expect(safeRedirect(undefined)).toBe('/')
    expect(safeRedirect(['/a'])).toBe('/')
  })
})
