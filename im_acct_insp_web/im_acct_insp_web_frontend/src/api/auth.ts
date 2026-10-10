// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：身分 API（開發文件 spec_im_acct_insp_v2.0.md 第 6.2 節）：
//           登入、登出、/me、切換身分名單 /users、切換身分、切回；失敗時 Promise reject，由畫面提示
// ============================================================
import http from './http'
import type { LoginResult, Me, UserInfo } from '../types/common'

/** 登入：POST /auth/login（不需 token） */
export function login(userId: string, password: string): Promise<LoginResult> {
  return http.post<LoginResult>('/auth/login', { userId, password }).then(r => r.data)
}

/** 登出：POST /auth/logout（token 立即作廢） */
export function logout(): Promise<void> {
  return http.post('/auth/logout', {}).then(() => undefined)
}

/** 目前身分：GET /me */
export function getMe(): Promise<Me> {
  return http.get<Me>('/me').then(r => r.data)
}

/** 切換身分名單：GET /users（實際使用者需 SYS_IMPERSONATE；STATUS＝1、不含自己） */
export function listUsers(): Promise<UserInfo[]> {
  return http.get<UserInfo[]>('/users').then(r => r.data)
}

/** 切換身分：POST /auth/impersonate，回傳切換後的 me */
export function impersonate(targetUserId: string): Promise<Me> {
  return http.post<Me>('/auth/impersonate', { targetUserId }).then(r => r.data)
}

/** 切回：POST /auth/impersonate/end，回傳切回後的 me */
export function endImpersonate(): Promise<Me> {
  return http.post<Me>('/auth/impersonate/end', {}).then(r => r.data)
}
