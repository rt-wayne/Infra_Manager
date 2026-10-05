// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-05
// 變更說明: 新增：axios 實例（規格 v4）；頁面只打自己殼 jar 的 /<專案名>/api/v1，由殼 jar 轉發給後端 API
//           timeout 120000：與殼 jar 的 read timeout 一致
//           jdk25 階段 3：改 TypeScript
// ============================================================
import axios from 'axios'

const http = axios.create({
  baseURL: '/infra_manager_web/api/v1',
  timeout: 120000
})

export default http
