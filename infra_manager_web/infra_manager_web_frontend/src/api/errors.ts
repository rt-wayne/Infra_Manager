// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：API 失敗訊息取法（開發文件 spec_im_acct_insp.md 第 4 節通則）
//           後端回 { message } 時用該訊息（例 409「此申請已由他人處理，請重新整理」），否則用畫面給的預設文字
// ============================================================
import axios from 'axios'

/** 從 API 例外取出要顯示的訊息 */
export function errorMessage(e: unknown, fallback: string): string {
  if (axios.isAxiosError(e)) {
    const data: unknown = e.response?.data
    if (data && typeof data === 'object' && 'message' in data) {
      const msg = (data as { message: unknown }).message
      if (typeof msg === 'string' && msg.trim()) {
        return msg
      }
    }
  }
  return fallback
}

/** API 例外的 HTTP 狀態碼；不是 HTTP 錯誤時回 0 */
export function errorStatus(e: unknown): number {
  return axios.isAxiosError(e) ? (e.response?.status ?? 0) : 0
}
