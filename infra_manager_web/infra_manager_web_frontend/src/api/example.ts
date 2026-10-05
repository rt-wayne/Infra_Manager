// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-05
// 變更說明: 新增：範例 API（規格 v4），對應殼 jar 的 ExampleController
//           回傳後端原樣的資料（r.data）；失敗時 Promise reject，由呼叫端提示，不得當成查無資料
//           jdk25 階段 3：改 TypeScript，參數與回傳型別引用 src/types/
// ============================================================
import http from './http'
import type { ExampleRow, ExampleSearchRequest } from '../types/example'

/* 查詢：POST /example/search，payload 原樣送出 */
export function search(payload: ExampleSearchRequest): Promise<ExampleRow[]> {
  return http.post<ExampleRow[]>('/example/search', payload).then(r => r.data)
}

/* 清單：GET /example/list */
export function list(): Promise<ExampleRow[]> {
  return http.get<ExampleRow[]>('/example/list').then(r => r.data)
}
