// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-05
// 變更說明: 新增：前端範本進入點（規格 v4）；jdk25 階段 3：改 TypeScript
//           Infra Manager S2 回合三（Claude Fable 5.1，2026-10-06）：註冊 401 處理器（登入過期導回登入頁）
// ============================================================
import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { useAuth } from './composables/useAuth'
import './assets/main.css'

const app = createApp(App)
app.use(router)
useAuth().installUnauthorizedHandler(router)
app.mount('#app')
