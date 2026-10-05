// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-05
// 變更說明: 新增：hash 模式路由（規格 v4），首頁為 ExampleView；新增畫面時在 routes 加一筆
//           jdk25 階段 3：改 TypeScript
// ============================================================
import { createRouter, createWebHashHistory } from 'vue-router'
import ExampleView from '../views/ExampleView.vue'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', component: ExampleView }
  ]
})

export default router
