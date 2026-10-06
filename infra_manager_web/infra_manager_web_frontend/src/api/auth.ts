// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：認證 API（S2 回合三），經殼 jar 轉發到後端 /api/auth/*
//           getMe 永遠 200（未登入 loggedIn:false），其他失敗 reject 由呼叫端取 message 提示
// ============================================================
import http from './http'
import type { ChangePasswordRequest, LoginRequest, MeResponse } from '../types/auth'

/* GET /auth/me：同時讓後端發 IM_XSRF cookie */
export function getMe(): Promise<MeResponse> {
  return http.get<MeResponse>('/auth/me').then(r => r.data)
}

/* POST /auth/login：401 帳號或密碼錯誤、400 請求格式錯誤 */
export function login(body: LoginRequest): Promise<MeResponse> {
  return http.post<MeResponse>('/auth/login', body).then(r => r.data)
}

/* POST /auth/logout：204 */
export function logout(): Promise<void> {
  return http.post('/auth/logout').then(() => undefined)
}

/* POST /auth/password：400 帶規則訊息 */
export function changePassword(body: ChangePasswordRequest): Promise<MeResponse> {
  return http.post<MeResponse>('/auth/password', body).then(r => r.data)
}
