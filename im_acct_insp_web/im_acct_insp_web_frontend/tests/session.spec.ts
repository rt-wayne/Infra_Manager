// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：身分測試（開發文件 spec_im_acct_insp.md 第 2 節）
//           http.ts 帶 X-User-Id（以假 adapter 攔截，不連網）；預設名單第一位；切換身分存 localStorage 並遞增 viewKey
//           變更單 #1：L6 paramsSerializer 完整編碼（含 [ ]）；L5 切換後目前頁無權限時不遞增 viewKey
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（開發文件 spec_im_acct_insp_v2.0.md 第 2.1、2.5、8.1 節）：改測 token 登入：
//           Authorization: Bearer 帶入、沒有 token 不帶、401 清 token 並呼叫導向處理（登入 API 本身除外）、
//           login 存 sessionStorage、logout 清除、can() 依 permissions[]、切換身分後依 canStay 決定是否重建畫面
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-11
// 變更說明: BACKLOG 第 28 項：token 改存 localStorage；切換身分／切回會寫身分變更通知；
//           watchOtherTabs 只對 token、通知與 clear() 的 storage 事件反應，其他 key 不理；
//           從上一頁快取還原（pageshow persisted）也反應；回傳的函式可移除監聽
//           同日審查後：還原時 token 與通知值跟離開（pagehide）時相同就不反應；
//           非快取還原（persisted=false）不反應；還原時讀不到 localStorage 視為有變
// ============================================================
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AxiosError } from 'axios'
import type { AxiosAdapter, InternalAxiosRequestConfig } from 'axios'
import http, { serializeParams, setUnauthorizedHandler } from '../src/api/http'
import { endImpersonate, getMe, impersonate, login, logout } from '../src/api/auth'
import { listExpiring, listTodos } from '../src/api/common'
import { getToken, setToken, watchOtherTabs } from '../src/api/tokenStore'
import { resetSession, useSession } from '../src/composables/useSession'
import { makeMe } from './helpers'

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
  listExpiring: vi.fn(),
  listTodos: vi.fn()
}))

/** 假 adapter：記下請求設定並回指定狀態碼（4xx 以 AxiosError reject，同真 adapter 的 settle） */
function adapter(status: number, data: unknown, seen: { cfg: InternalAxiosRequestConfig | null }): AxiosAdapter {
  return config => {
    seen.cfg = config
    const res = { data, status, statusText: '', headers: {}, config }
    if (status >= 400) {
      return Promise.reject(new AxiosError(`Request failed with status code ${status}`, 'ERR_BAD_REQUEST', config, null, res))
    }
    return Promise.resolve(res)
  }
}

describe('token 與 http', () => {
  beforeEach(() => {
    localStorage.clear()
    resetSession()
  })
  afterEach(() => {
    setUnauthorizedHandler(null)
  })

  it('每個請求帶 Authorization: Bearer <localStorage 的 token>', async () => {
    setToken('tok-123')
    const seen = { cfg: null as InternalAxiosRequestConfig | null }
    await http.get('/me', { adapter: adapter(200, {}, seen) })
    expect(seen.cfg?.headers.get('Authorization')).toBe('Bearer tok-123')
    expect(seen.cfg?.headers.get('X-User-Id')).toBeUndefined()
    expect(seen.cfg?.baseURL).toBe('/im_acct_insp_web/api/v1')
  })

  it('沒有 token 時不帶 Authorization', async () => {
    const seen = { cfg: null as InternalAxiosRequestConfig | null }
    await http.get('/codes', { adapter: adapter(200, {}, seen) })
    expect(seen.cfg?.headers.get('Authorization')).toBeUndefined()
  })

  it('收到 401：清 token、以後端 message 呼叫導向處理', async () => {
    setToken('tok-old')
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    const seen = { cfg: null as InternalAxiosRequestConfig | null }
    await expect(http.get('/todos', { adapter: adapter(401, { message: '登入已逾時，請重新登入' }, seen) })).rejects.toBeTruthy()
    expect(getToken()).toBe('')
    expect(handler).toHaveBeenCalledWith('登入已逾時，請重新登入')
  })

  it('登入 API 的 401（帳密錯、鎖定）不清 token、不導向', async () => {
    setToken('tok-keep')
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    const seen = { cfg: null as InternalAxiosRequestConfig | null }
    await expect(http.post('/auth/login', {}, { adapter: adapter(401, { message: '帳號或密碼錯誤' }, seen) })).rejects.toBeTruthy()
    expect(getToken()).toBe('tok-keep')
    expect(handler).not.toHaveBeenCalled()
  })

  it('403 不清 token', async () => {
    setToken('tok-keep')
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    const seen = { cfg: null as InternalAxiosRequestConfig | null }
    await expect(http.get('/accounts', { adapter: adapter(403, { message: '您沒有執行此操作的權限' }, seen) })).rejects.toBeTruthy()
    expect(getToken()).toBe('tok-keep')
    expect(handler).not.toHaveBeenCalled()
  })

  it('query 參數以 encodeURIComponent 完整編碼（含 [ ]）', async () => {
    let url = ''
    await http.get('/accounts/lookup', {
      params: { acctName: 'a[1] b&c=%', catgId: 'DB', empty: undefined },
      adapter: config => {
        url = http.getUri(config)
        return Promise.resolve({ data: [], status: 200, statusText: 'OK', headers: {}, config })
      }
    })
    expect(url).toBe('/im_acct_insp_web/api/v1/accounts/lookup?acctName=a%5B1%5D%20b%26c%3D%25&catgId=DB')
    expect(serializeParams({ 'k[]': ['x', 'y'], n: null })).toBe('k%5B%5D=x&k%5B%5D=y')
  })
})

describe('跨分頁同步（watchOtherTabs）', () => {
  let unwatch: (() => void) | null = null
  afterEach(() => {
    unwatch?.()
    unwatch = null
  })

  /** 模擬其他分頁改了 localStorage：瀏覽器只在其他分頁觸發 storage 事件，測試直接派送 */
  function fire(key: string | null, area: Storage = localStorage): void {
    window.dispatchEvent(new StorageEvent('storage', { key, storageArea: area }))
  }

  /** 模擬 pageshow；jsdom 未必有 PageTransitionEvent，以一般 Event 補 persisted */
  function firePageShow(persisted: boolean): void {
    const e = new Event('pageshow')
    Object.defineProperty(e, 'persisted', { value: persisted })
    window.dispatchEvent(e)
  }

  it('其他分頁換 token、發身分變更通知或 clear() 時執行 onChange；其他 key 與 sessionStorage 不理', () => {
    const onChange = vi.fn()
    unwatch = watchOtherTabs(onChange)
    fire('im_acct_insp_web.token')
    fire('im_acct_insp_web.identityChanged')
    fire(null)
    expect(onChange).toHaveBeenCalledTimes(3)
    fire('other.app.key')
    fire('im_acct_insp_web.token', sessionStorage)
    expect(onChange).toHaveBeenCalledTimes(3)
  })

  it('上一頁快取還原時，token 與通知值跟離開時相同就不理，有變才執行 onChange', () => {
    localStorage.clear()
    setToken('tok-a')
    const onChange = vi.fn()
    unwatch = watchOtherTabs(onChange)
    window.dispatchEvent(new Event('pagehide'))
    firePageShow(true)
    expect(onChange).not.toHaveBeenCalled()
    // 快取期間其他分頁換了 token
    window.dispatchEvent(new Event('pagehide'))
    setToken('tok-b')
    firePageShow(true)
    expect(onChange).toHaveBeenCalledTimes(1)
    // 快取期間其他分頁切換身分（token 沒變、通知值變）
    window.dispatchEvent(new Event('pagehide'))
    localStorage.setItem('im_acct_insp_web.identityChanged', 'x')
    firePageShow(true)
    expect(onChange).toHaveBeenCalledTimes(2)
    // 狀態有變但不是從上一頁快取還原（一般載入）：不理
    window.dispatchEvent(new Event('pagehide'))
    setToken('tok-c')
    firePageShow(false)
    expect(onChange).toHaveBeenCalledTimes(2)
  })

  it('上一頁快取還原時讀不到 localStorage：視為有變、執行 onChange', () => {
    localStorage.clear()
    setToken('tok-a')
    const onChange = vi.fn()
    unwatch = watchOtherTabs(onChange)
    window.dispatchEvent(new Event('pagehide'))
    const spy = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    try {
      firePageShow(true)
    } finally {
      spy.mockRestore()
    }
    expect(onChange).toHaveBeenCalledTimes(1)
  })

  it('從上一頁快取還原（pageshow persisted）而沒有離開紀錄時執行 onChange；一般載入不理', () => {
    const onChange = vi.fn()
    unwatch = watchOtherTabs(onChange)
    firePageShow(false)
    expect(onChange).not.toHaveBeenCalled()
    firePageShow(true)
    expect(onChange).toHaveBeenCalledTimes(1)
  })

  it('移除監聽後不再反應', () => {
    const onChange = vi.fn()
    watchOtherTabs(onChange)()
    fire('im_acct_insp_web.token')
    firePageShow(true)
    expect(onChange).not.toHaveBeenCalled()
  })
})

describe('useSession', () => {
  beforeEach(() => {
    localStorage.clear()
    resetSession()
    vi.mocked(login).mockReset()
    vi.mocked(logout).mockReset().mockResolvedValue(undefined)
    vi.mocked(getMe).mockReset()
    vi.mocked(impersonate).mockReset()
    vi.mocked(endImpersonate).mockReset()
    vi.mocked(listTodos).mockReset().mockResolvedValue([])
    vi.mocked(listExpiring).mockReset().mockResolvedValue([])
  })

  it('login 存 token 到 localStorage（各分頁共用）並帶入 me；logout 清掉', async () => {
    vi.mocked(login).mockResolvedValue({ token: 'tok-new', me: makeMe() })
    const s = useSession()
    await s.login('0000001', 'pw')
    expect(login).toHaveBeenCalledWith('0000001', 'pw')
    expect(getToken()).toBe('tok-new')
    expect(localStorage.getItem('im_acct_insp_web.token')).toBe('tok-new')
    expect(sessionStorage.getItem('im_acct_insp_web.token')).toBeNull()
    expect(s.userName.value).toBe('Kevin (李凱鈞)')

    await s.logout()
    expect(logout).toHaveBeenCalledTimes(1)
    expect(getToken()).toBe('')
    expect(s.me.value).toBeNull()
  })

  it('登出時後端失敗也照樣清掉本機 token', async () => {
    setToken('tok')
    vi.mocked(logout).mockRejectedValue(new Error('network'))
    await useSession().logout()
    expect(getToken()).toBe('')
  })

  it('沒有 token 時 initSession 不打 /me', async () => {
    await useSession().initSession()
    expect(getMe).not.toHaveBeenCalled()
  })

  it('can() 依 /me 的 permissions[]，不看角色', async () => {
    setToken('tok')
    vi.mocked(getMe).mockResolvedValue(makeMe({ roleIds: ['ACCT_REGISTRY'], permissions: ['ACC_SELF_SERVICE', 'INSP_VIEW'] }))
    const s = useSession()
    await s.initSession()
    expect(s.can('INSP_VIEW')).toBe(true)
    expect(s.can('ACC_SELF_SERVICE')).toBe(true)
    expect(s.can('ACC_LIST_FULL')).toBe(false)
    expect(s.can('INSP_CREATE')).toBe(false)
  })

  it('切換身分：套用回傳的 me；目前頁可留下才遞增 viewKey', async () => {
    setToken('tok')
    vi.mocked(getMe).mockResolvedValue(makeMe({ userId: '0000011', userName: 'Hank (黃志翰)', realUserId: '0000011', realUserName: 'Hank (黃志翰)', canImpersonate: true }))
    const gary = makeMe({
      userId: '0000010', userName: 'Gary (許永成)', realUserId: '0000011', realUserName: 'Hank (黃志翰)',
      impersonating: true, canImpersonate: false, permissions: ['APPR_G5']
    })
    vi.mocked(impersonate).mockResolvedValue(gary)
    const s = useSession()
    await s.initSession()
    expect(s.canImpersonate.value).toBe(true)

    const before = s.viewKey.value
    expect(localStorage.getItem('im_acct_insp_web.identityChanged')).toBeNull()
    const stay = await s.impersonate('0000010', () => s.can('ACC_LIST_FULL'))
    expect(impersonate).toHaveBeenCalledWith('0000010')
    // 寫了身分變更通知，其他分頁會收到 storage 事件
    const signal1 = localStorage.getItem('im_acct_insp_web.identityChanged')
    expect(signal1).toBeTruthy()
    expect(stay).toBe(false)
    expect(s.viewKey.value).toBe(before)
    expect(s.impersonating.value).toBe(true)
    expect(s.userName.value).toBe('Gary (許永成)')
    expect(s.realUserName.value).toBe('Hank (黃志翰)')

    vi.mocked(endImpersonate).mockResolvedValue(makeMe({ userId: '0000011', userName: 'Hank (黃志翰)', realUserId: '0000011', realUserName: 'Hank (黃志翰)', canImpersonate: true }))
    const stay2 = await s.endImpersonate()
    expect(stay2).toBe(true)
    expect(s.viewKey.value).toBe(before + 1)
    // 切回也通知，且值與上次不同（值相同時瀏覽器不觸發 storage 事件）
    expect(localStorage.getItem('im_acct_insp_web.identityChanged')).not.toBe(signal1)
    expect(s.impersonating.value).toBe(false)
  })
})
