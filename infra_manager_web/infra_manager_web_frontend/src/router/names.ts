// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-06
// 變更說明: 新增：路由名稱常數（S4 回合三）。獨立成檔，頁面 import 它不會牽動 router/index.ts（避免 view ↔ router 循環）
//           S6 回合四（2026-10-07）：加新增／編輯草稿表單 APP_NEW_ROUTE、APP_EDIT_ROUTE
// ============================================================

export const APP_LIST_ROUTE = 'app-list'
export const APP_VIEW_ROUTE = 'app-view'
export const APP_NEW_ROUTE = 'app-new'
export const APP_EDIT_ROUTE = 'app-edit'
