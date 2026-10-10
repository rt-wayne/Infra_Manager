// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：共用 API（開發文件 spec_im_acct_insp.md 第 4.1 節）：/users、/me、/codes、/home/stats、/home/expiring、/todos
//           失敗時 Promise reject，由畫面 toast
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（開發文件 spec_im_acct_insp_v2.0.md 第 6.3 節）：/users、/me 移到 api/auth.ts（與登入、切換身分同檔）
// ============================================================
import http from './http'
import type { Codes, ExpiringItem, HomeStats, Todo } from '../types/common'

/** 代碼表：GET /codes */
export function getCodes(): Promise<Codes> {
  return http.get<Codes>('/codes').then(r => r.data)
}

/** 首頁卡片：GET /home/stats */
export function getHomeStats(): Promise<HomeStats> {
  return http.get<HomeStats>('/home/stats').then(r => r.data)
}

/** 鈴鐺到期清單：GET /home/expiring（只回與我相關的） */
export function listExpiring(): Promise<ExpiringItem[]> {
  return http.get<ExpiringItem[]>('/home/expiring').then(r => r.data)
}

/** 待辦：GET /todos（只回登入者此刻能處理的） */
export function listTodos(): Promise<Todo[]> {
  return http.get<Todo[]>('/todos').then(r => r.data)
}
