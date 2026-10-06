// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-05
// 變更說明: 新增：GET /health 的回應型別（S1）；對應後端 com.mpx.infra_manager_java.model.HealthStatus
// ============================================================

/** GET /health 回傳 */
export interface HealthStatus {
  /** 後端程式狀態，固定 UP */
  status: string
  /** 資料庫連線：UP／DOWN */
  db: string
  /** 後端時間（台灣時間，yyyy-MM-dd HH:mm:ss） */
  time: string
}
