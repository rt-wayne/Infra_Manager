// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-05
// 變更說明: 新增：axios 實例（規格 v4）；頁面只打自己殼 jar 的 /<專案名>/api/v1，由殼 jar 轉發給後端 API
//           timeout 120000：與殼 jar 的 read timeout 一致
//           jdk25 階段 3：改 TypeScript
//           Infra Manager S2 回合三（Claude Fable 5.1，2026-10-06）：
//           - 請求攔截器：每次從 cookie 讀 IM_XSRF 放進 header X-IM-XSRF（登入／登出會換發，所以不快取）
//           - 回應攔截器：401 時呼叫 onUnauthorized（由 useAuth 註冊：重查 /me、未登入就導登入頁）；
//             這裡不 import router，避免 router → view → api → router 循環
//           - errorMessage(e)：取後端 { message }，沒有就給通用文字；供各頁 toast 用（失敗不得顯示成查無資料）
// ============================================================
import axios, { AxiosError } from 'axios'
import type { ApiErrorBody } from '../types/auth'

export const XSRF_COOKIE = 'IM_XSRF'
export const XSRF_HEADER = 'X-IM-XSRF'

const http = axios.create({
  baseURL: '/infra_manager_web/api/v1',
  timeout: 120000
})

export function readCookie(name: string): string | null {
  const prefix = name + '='
  for (const part of document.cookie.split(';')) {
    const p = part.trim()
    if (p.startsWith(prefix)) return decodeURIComponent(p.slice(prefix.length))
  }
  return null
}

http.interceptors.request.use(config => {
  const token = readCookie(XSRF_COOKIE)
  if (token) config.headers.set(XSRF_HEADER, token)
  return config
})

type UnauthorizedHandler = () => void
let onUnauthorized: UnauthorizedHandler | null = null

/** useAuth 在啟動時註冊；401 時呼叫處理器，仍把錯誤往外丟讓呼叫端收尾 */
export function setUnauthorizedHandler(handler: UnauthorizedHandler | null): void {
  onUnauthorized = handler
}

http.interceptors.response.use(
  r => r,
  (error: unknown) => {
    if (error instanceof AxiosError && error.response?.status === 401 && onUnauthorized) {
      onUnauthorized()
    }
    return Promise.reject(error)
  }
)

/** 後端回的 message；沒有本文（網路斷、逾時、殼 jar 掛了）時給通用文字 */
export function errorMessage(e: unknown, fallback = '後端服務呼叫失敗，請稍後再試'): string {
  if (e instanceof AxiosError) {
    const body: unknown = e.response?.data
    if (body && typeof body === 'object' && 'message' in body) {
      const msg = (body as ApiErrorBody).message
      if (typeof msg === 'string' && msg.trim() !== '') return msg
    }
  }
  return fallback
}

export default http
