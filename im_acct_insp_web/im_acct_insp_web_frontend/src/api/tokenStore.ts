// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：登入 token 存取（開發文件 spec_im_acct_insp_v2.0.md 第 8.1 節：token 存 sessionStorage）
//           取代舊 userIdStore（X-User-Id 已移除）；http.ts 的 interceptor 讀它帶 Authorization: Bearer
//           本檔不 import 其他模組，避免循環引用
// ============================================================

const KEY = 'im_acct_insp_web.token'

/** 目前 token；沒有或無法讀 sessionStorage 時回空字串 */
export function getToken(): string {
  try {
    return sessionStorage.getItem(KEY) ?? ''
  } catch {
    return ''
  }
}

/** 設定 token（空字串＝清除） */
export function setToken(token: string): void {
  try {
    if (token) {
      sessionStorage.setItem(KEY, token)
    } else {
      sessionStorage.removeItem(KEY)
    }
  } catch {
    // sessionStorage 不可用時只是不記住，重新整理後需重新登入
  }
}

/** 清除 token（登出、401） */
export function clearToken(): void {
  setToken('')
}
