// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-05
// 變更說明: 新增：axios 實例（規格 v4）；頁面只打自己殼 jar 的 /<專案名>/api/v1，由殼 jar 轉發給後端 API
//           timeout 120000：與殼 jar 的 read timeout 一致
//           jdk25 階段 3：改 TypeScript
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: im_acct_insp（開發文件 spec_im_acct_insp.md 第 2 節）：request interceptor 帶 X-User-Id（目前身分，存 localStorage）
//           變更單 #1：L6 加 paramsSerializer，query 參數名與值一律 encodeURIComponent 完整編碼（含 [ ]）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（開發文件 spec_im_acct_insp_v2.0.md 第 2.1、8.1 節）：移除 X-User-Id；
//           每個請求帶 Authorization: Bearer <sessionStorage 的 token>；
//           回 401（登入、登出 API 本身除外）→ 清 token，交給 setUnauthorizedHandler 註冊的處理（router 導回登入頁並顯示後端 message）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-11
// 變更說明: BACKLOG 第 28 項：token 改存 localStorage（各分頁共用，本檔程式未改）；401 清 token 時其他分頁也會跟著回登入頁
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 1：baseURL 改 /infra_manager_web/api/v1（隨殼 jar context-path 改名）
// ============================================================
import axios from 'axios'
import { errorMessage } from './errors'
import { clearToken, getToken } from './tokenStore'

/** 401 預設文案（A03） */
export const MSG_SESSION_EXPIRED = '登入已逾時，請重新登入'

/** 不套 401 導向的路徑：登入失敗（A01／A02）由登入頁自己顯示；登出時 token 已失效也照常登出，不另顯示逾時訊息 */
const NO_REDIRECT_PATHS = ['/auth/login', '/auth/logout']

/**
 * query 參數序列化：axios 預設不編碼 [ ] 等字元，未編碼的 [ ] 會被 Tomcat 拒收（400），殼 jar 原樣接上的查詢字串
 * 也可能讓 java.net.URI 解析失敗；這裡名與值都用 encodeURIComponent。null／undefined 不送；陣列展開成同名多個參數
 */
export function serializeParams(params: Record<string, unknown>): string {
  const parts: string[] = []
  const add = (k: string, v: unknown): void => {
    if (v === null || v === undefined) return
    parts.push(`${encodeURIComponent(k)}=${encodeURIComponent(v instanceof Date ? v.toISOString() : String(v))}`)
  }
  for (const [k, v] of Object.entries(params)) {
    if (Array.isArray(v)) {
      for (const item of v) add(k, item)
    } else {
      add(k, v)
    }
  }
  return parts.join('&')
}

const http = axios.create({
  baseURL: '/infra_manager_web/api/v1',
  timeout: 120000,
  paramsSerializer: { serialize: serializeParams }
})

let unauthorizedHandler: ((message: string) => void) | null = null

/** 註冊 401 處理（router 在建立時註冊；測試可替換） */
export function setUnauthorizedHandler(fn: ((message: string) => void) | null): void {
  unauthorizedHandler = fn
}

// 每個請求帶 token；殼 jar 原樣轉送 Authorization 給後端
http.interceptors.request.use(config => {
  const token = getToken()
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
})

// 401：token 無效、過期或已登出 → 清 token、導回登入頁；錯誤照常 reject，呼叫端的 catch 不需特別處理
http.interceptors.response.use(
  r => r,
  (e: unknown) => {
    if (axios.isAxiosError(e) && e.response?.status === 401 && !NO_REDIRECT_PATHS.includes(e.config?.url ?? '')) {
      clearToken()
      unauthorizedHandler?.(errorMessage(e, MSG_SESSION_EXPIRED))
    }
    return Promise.reject(e)
  }
)

export default http
