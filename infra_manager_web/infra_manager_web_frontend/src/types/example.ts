// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-05
// 變更說明: 新增：範例 API 的請求／回應型別（規格 D-41：API 型別一律放 src/types/）
//           後端欄位以後端 API 實際回傳為準；換成自己的欄位時同步修改 ExampleView 的 columns
// ============================================================

/** POST /example/search 的請求本文 */
export interface ExampleSearchRequest {
  keyword: string
}

/** /example/search、/example/list 回傳陣列中的一列 */
export interface ExampleRow {
  code?: string
  name?: string
  /** 後端可能多帶其他欄位；以 unknown 接住，使用前自行判斷型別 */
  [key: string]: unknown
}
