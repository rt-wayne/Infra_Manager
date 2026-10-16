// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：登入頁路徑與登入後回跳路徑檢查（開發文件 spec_im_acct_insp_v2.0.md 第 8.1 節：登入後回原路由）
//           獨立成檔，讓登入頁不必 import 整個 router（避免循環引用）
// ============================================================

export const LOGIN_PATH = '/login'

/** 登入後要回去的路徑：只接受站內路徑（/ 開頭、非 //、非登入頁），其他一律回首頁 */
export function safeRedirect(v: unknown): string {
  if (typeof v !== 'string' || !v.startsWith('/') || v.startsWith('//') || v.startsWith(LOGIN_PATH)) return '/'
  return v
}
