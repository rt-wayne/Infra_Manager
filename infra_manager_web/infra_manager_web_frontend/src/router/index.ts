// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-05
// 變更說明: 新增：hash 模式路由（規格 v4），首頁為 ExampleView；新增畫面時在 routes 加一筆
//           jdk25 階段 3：改 TypeScript
//           Infra Manager S1（Claude Fable 5.1，2026-10-06）：首頁改為 HomeView；範本的 ExampleView 與 /example 路由已移除（裁示 ⑤A）
//           Infra Manager S2 回合三（Claude Fable 5.1，2026-10-06）：加 /login、/change-password 與全域守衛：
//           - 第一次進站先打 /auth/me（順便拿 IM_XSRF）
//           - meta.public 以外的路由未登入 → /login?redirect=原路徑
//           - 已登入但仍是預設密碼 → 只能到 /change-password
//           - 已登入再進 /login → 回首頁
//           S4 回合二（Claude Opus 5.5，2026-10-06）：加 /apps 申請單列表（須登入）
//           S4 回合三（Claude Opus 5.5，2026-10-06）：加 /apps/:id 檢視頁；列表與檢視改具名路由（名稱在 router/names.ts）
//           S6 回合四（Claude Opus 5.5，2026-10-07）：加 /apps/new 新增草稿、/apps/:id/edit 編輯草稿（同一個 AppFormView）
//           S9 R3（Claude Opus 5.5，2026-10-07）：加 /apps/:id/resubmit 補件重送（仍是 AppFormView，依路由名稱切模式）
// ============================================================
import { createRouter, createWebHashHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import AppListView from '../views/AppListView.vue'
import AppViewView from '../views/AppViewView.vue'
import AppFormView from '../views/AppFormView.vue'
import { APP_EDIT_ROUTE, APP_LIST_ROUTE, APP_NEW_ROUTE, APP_RESUBMIT_ROUTE, APP_VIEW_ROUTE } from './names'
import LoginView from '../views/LoginView.vue'
import ChangePasswordView from '../views/ChangePasswordView.vue'
import { CHANGE_PASSWORD_PATH, LOGIN_PATH, useAuth } from '../composables/useAuth'

declare module 'vue-router' {
  interface RouteMeta {
    /** true：未登入也能看（登入頁） */
    public?: boolean
  }
}

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', component: HomeView },
    { path: LOGIN_PATH, component: LoginView, meta: { public: true } },
    { path: CHANGE_PASSWORD_PATH, component: ChangePasswordView },
    { path: '/apps', name: APP_LIST_ROUTE, component: AppListView },
    { path: '/apps/new', name: APP_NEW_ROUTE, component: AppFormView },
    { path: '/apps/:id', name: APP_VIEW_ROUTE, component: AppViewView },
    { path: '/apps/:id/edit', name: APP_EDIT_ROUTE, component: AppFormView },
    { path: '/apps/:id/resubmit', name: APP_RESUBMIT_ROUTE, component: AppFormView }
  ]
})

router.beforeEach(async to => {
  const auth = useAuth()
  const me = await auth.ensureLoaded()
  if (!me.loggedIn) {
    if (to.meta.public) return true
    return { path: LOGIN_PATH, query: to.fullPath === '/' ? {} : { redirect: to.fullPath } }
  }
  if (to.path === LOGIN_PATH) return { path: '/' }
  if (me.mustChangePassword === true && to.path !== CHANGE_PASSWORD_PATH) return { path: CHANGE_PASSWORD_PATH }
  return true
})

export default router
