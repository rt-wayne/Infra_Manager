// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：登入頁測試（開發文件 spec_im_acct_insp_v2.0.md 第 2.1、8.3 節）
//           成功存 token 並回 redirect；失敗顯示後端 message（A01、A02）且不存 token；被 401 導回時顯示當時訊息
// ============================================================
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import LoginView from '../src/views/LoginView.vue'
import { login } from '../src/api/auth'
import { getToken } from '../src/api/tokenStore'
import { resetSession, useSession } from '../src/composables/useSession'
import { httpError, makeMe, makeRouter } from './helpers'

vi.mock('../src/api/auth', () => ({
  login: vi.fn(),
  logout: vi.fn(),
  getMe: vi.fn(),
  listUsers: vi.fn(),
  impersonate: vi.fn(),
  endImpersonate: vi.fn()
}))

const loginMock = vi.mocked(login)

async function mountLogin(path = '/login') {
  const router = await makeRouter(path)
  const w = mount(LoginView, { global: { plugins: [router] } })
  await flushPromises()
  return { w, router }
}

describe('LoginView', () => {
  beforeEach(() => {
    sessionStorage.clear()
    resetSession()
    useSession().loginNotice.value = ''
    loginMock.mockReset()
  })

  it('登入成功：存 token、帶入 me，回到 redirect 指定的頁', async () => {
    loginMock.mockResolvedValue({ token: 'tok-1', me: makeMe() })
    const { w, router } = await mountLogin('/login?redirect=/todo')
    await w.find('#loginUserId').setValue(' 0000001 ')
    await w.find('#loginPassword').setValue('secret')
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(loginMock).toHaveBeenCalledWith('0000001', 'secret')
    expect(getToken()).toBe('tok-1')
    expect(useSession().me.value?.userId).toBe('0000001')
    expect(router.currentRoute.value.path).toBe('/todo')
    expect((w.find('#loginPassword').element as HTMLInputElement).value).toBe('')
    w.unmount()
  })

  it('沒有 redirect 時登入後回首頁', async () => {
    loginMock.mockResolvedValue({ token: 'tok-1', me: makeMe() })
    const { w, router } = await mountLogin()
    await w.find('#loginUserId').setValue('0000001')
    await w.find('#loginPassword').setValue('secret')
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/')
    w.unmount()
  })

  it('登入失敗：顯示後端 message（A02），不存 token', async () => {
    loginMock.mockRejectedValue(httpError(401, { message: '此帳號已鎖定，請聯絡系統管理員' }, '/auth/login'))
    const { w, router } = await mountLogin()
    await w.find('#loginUserId').setValue('0000012')
    await w.find('#loginPassword').setValue('secret')
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(w.find('.login-msg').text()).toBe('此帳號已鎖定，請聯絡系統管理員')
    expect(getToken()).toBe('')
    expect(router.currentRoute.value.path).toBe('/login')
    w.unmount()
  })

  it('工號或密碼空白不送出', async () => {
    const { w } = await mountLogin()
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(loginMock).not.toHaveBeenCalled()
    expect(w.find('.login-msg').text()).toBe('請輸入工號與密碼')
    w.unmount()
  })

  it('被 401 導回時顯示當時的後端訊息', async () => {
    useSession().loginNotice.value = '登入已逾時，請重新登入'
    const { w } = await mountLogin()
    expect(w.find('.login-msg').text()).toBe('登入已逾時，請重新登入')
    expect(useSession().loginNotice.value).toBe('')
    w.unmount()
  })
})
