// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：登入狀態 composable（S2 回合三）。模組層共用狀態，所有頁面與路由守衛看同一份
//           ensureLoaded：第一次（或被要求重查）打 GET /auth/me，順便拿到 IM_XSRF cookie
//           login／logout／changePassword 成功後更新狀態；401 處理器：重查 /me，未登入就導 /login（帶 redirect）
//           /me 本身打不到時視為未登入但不導頁（由守衛決定），並出 toast
//           2026-10-06 code review 第 1 項：logout 後 loaded 改設 false，讓守衛重打 /me 取新 IM_XSRF（修「登出後第一次登入必 403」）
// ============================================================
import { computed, ref } from 'vue'
import type { Router } from 'vue-router'
import * as authApi from '../api/auth'
import { errorMessage, setUnauthorizedHandler } from '../api/http'
import type { ChangePasswordRequest, LoginRequest, MeResponse } from '../types/auth'
import { useToast } from './useToast'

const me = ref<MeResponse>({ loggedIn: false })
const loaded = ref(false)
let pending: Promise<MeResponse> | null = null

export const LOGIN_PATH = '/login'
export const CHANGE_PASSWORD_PATH = '/change-password'

/** redirect 參數只接受站內路徑（以單一 / 開頭），其他一律回首頁（舊系統漏洞第 50 項 open redirect） */
export function safeRedirect(target: unknown): string {
  if (typeof target !== 'string') return '/'
  if (!target.startsWith('/') || target.startsWith('//') || target.includes('\\')) return '/'
  if (target === LOGIN_PATH || target.startsWith(LOGIN_PATH + '?')) return '/'
  return target
}

export function useAuth() {
  const { toast } = useToast()

  const loggedIn = computed(() => me.value.loggedIn)
  const mustChangePassword = computed(() => me.value.loggedIn && me.value.mustChangePassword === true)
  const userName = computed(() => me.value.userName ?? '')
  const roles = computed(() => me.value.roles ?? [])

  function hasRole(role: string): boolean {
    return roles.value.includes(role)
  }

  async function refresh(): Promise<MeResponse> {
    if (pending) return pending
    pending = authApi
      .getMe()
      .then(r => {
        me.value = r
        return r
      })
      .catch((e: unknown) => {
        me.value = { loggedIn: false }
        toast(errorMessage(e, '無法連線後端服務，請稍後再試'), 'amber')
        return me.value
      })
      .finally(() => {
        loaded.value = true
        pending = null
      })
    return pending
  }

  async function ensureLoaded(): Promise<MeResponse> {
    return loaded.value ? me.value : refresh()
  }

  async function login(body: LoginRequest): Promise<MeResponse> {
    const r = await authApi.login(body)
    me.value = r
    loaded.value = true
    return r
  }

  /** 登出後 loaded 設回 false：後端登出時會刪掉 IM_XSRF cookie，守衛導到登入頁時要重打 /me 拿新的，否則下一次登入少帶 CSRF header 會 403 */
  async function logout(): Promise<void> {
    try {
      await authApi.logout()
    } finally {
      me.value = { loggedIn: false }
      loaded.value = false
    }
  }

  async function changePassword(body: ChangePasswordRequest): Promise<MeResponse> {
    const r = await authApi.changePassword(body)
    me.value = r
    return r
  }

  /** main.ts 呼叫一次：API 回 401 → 重查 /me → 真的沒登入就導登入頁並記住原路徑 */
  function installUnauthorizedHandler(router: Router): void {
    setUnauthorizedHandler(() => {
      void refresh().then(r => {
        if (r.loggedIn) return
        const current = router.currentRoute.value
        if (current.path === LOGIN_PATH) return
        toast('登入已過期，請重新登入', 'amber')
        void router.push({ path: LOGIN_PATH, query: { redirect: current.fullPath } })
      })
    })
  }

  return {
    me,
    loaded,
    loggedIn,
    mustChangePassword,
    userName,
    roles,
    hasRole,
    refresh,
    ensureLoaded,
    login,
    logout,
    changePassword,
    installUnauthorizedHandler
  }
}

/** 測試用：清掉模組層狀態 */
export function resetAuthStateForTest(): void {
  me.value = { loggedIn: false }
  loaded.value = false
  pending = null
}
