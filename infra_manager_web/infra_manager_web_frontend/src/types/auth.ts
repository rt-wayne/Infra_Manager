// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：認證 API 的請求與回應型別（S2 回合三）；對應後端 model.auth.MeResponse／LoginRequest／ChangePasswordRequest
//           錯誤回應一律 { message }（後端 ApiExceptionHandler.message）
// ============================================================

/** GET /auth/me、POST /auth/login、POST /auth/password 成功時的回傳；未登入時只有 loggedIn:false */
export interface MeResponse {
  loggedIn: boolean
  /** 工號 */
  userId?: string
  /** 登入帳號（小寫） */
  loginId?: string
  userName?: string
  /** 角色代碼（admin、applicant…） */
  roles?: string[]
  /** true 表示仍是預設密碼，須先改密碼才能用其他功能 */
  mustChangePassword?: boolean
}

/** POST /auth/login 本文 */
export interface LoginRequest {
  loginId: string
  password: string
}

/** POST /auth/password 本文 */
export interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
}

/** 後端 4xx／5xx 的 JSON 本文 */
export interface ApiErrorBody {
  message: string
}
