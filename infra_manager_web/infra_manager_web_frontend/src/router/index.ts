// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-05
// 變更說明: 新增：hash 模式路由（規格 v4），首頁為 ExampleView；新增畫面時在 routes 加一筆
//           jdk25 階段 3：改 TypeScript
//           Infra Manager S1（Claude Fable 5.1，2026-10-06）：首頁改為 HomeView；範本的 ExampleView 與 /example 路由已移除（裁示 ⑤A）
// ============================================================
import { createRouter, createWebHashHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', component: HomeView }
  ]
})

export default router
