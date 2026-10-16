// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-06
// 變更說明: 新增：hash 模式路由（規格 v4），首頁為範例畫面；新增畫面時在 routes 加一筆
//           jdk25 階段 3：改 TypeScript
//           jdk25 階段 6（規格 v1.3 D-50）：移除範例畫面，routes 改為空陣列，首頁空白屬預期
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: im_acct_insp（開發文件 spec_im_acct_insp.md 第 6 節）：外框 AppLayout 底下 9 個畫面；
//           meta.title 為頁首頁名、meta.need 為需要的權限（acctList＝ACCT_LIST、insp＝INSP），
//           沒有權限時轉 /no-access（只是顯示控管，後端一律自己驗權）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（開發文件 spec_im_acct_insp_v2.0.md 第 8.1、8.3 節）：新增 /login（AppLayout 外）；
//           全域守衛 authGuard：沒有 token → 登入頁（帶 redirect，登入後回原路由）；已登入開登入頁 → 首頁；
//           meta.need 改為權限碼（PermCode），不符 → /no-access；
//           註冊 http 的 401 處理：清共用狀態、記下後端 message、導回登入頁
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S3：/emergency 緊急存取清單（登入即可，與通知信連結 #/emergency 一致）、
//           /emergency/new 緊急存取申請（需 EMG_APPLY）
// ============================================================
import { createRouter, createWebHashHistory } from 'vue-router'
import type { RouteLocationNormalized, RouteLocationRaw, RouteRecordRaw, Router } from 'vue-router'
import { setUnauthorizedHandler } from '../api/http'
import { getToken } from '../api/tokenStore'
import { resetSession, useSession } from '../composables/useSession'
import type { PermCode } from '../utils/perms'
import { LOGIN_PATH, safeRedirect } from './redirect'

export { LOGIN_PATH, safeRedirect }

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    /** 需要的權限碼；沒有 → /no-access */
    need?: PermCode
    /** 不需登入（登入頁） */
    public?: boolean
  }
}

/*
 * 新增畫面時在這裡加一筆 route，例：
 *   { path: '/', component: () => import('../views/<畫面>.vue') }
 * 寫法見專案 README 第 3 節
 */
const routes: RouteRecordRaw[] = [
  { path: LOGIN_PATH, component: () => import('../views/LoginView.vue'), meta: { title: '登入', public: true } },
  {
    path: '/',
    component: () => import('../layouts/AppLayout.vue'),
    children: [
      { path: '', component: () => import('../views/HomeView.vue'), meta: { title: '首頁' } },
      { path: 'todo', component: () => import('../views/TodoView.vue'), meta: { title: '待辦簽核' } },
      {
        path: 'accounts',
        component: () => import('../views/AccountListView.vue'),
        meta: { title: '帳號列表', need: 'ACC_LIST_FULL' }
      },
      {
        path: 'requests',
        component: () => import('../views/RequestListView.vue'),
        meta: { title: '帳號申請單列表', need: 'ACC_SELF_SERVICE' }
      },
      {
        path: 'requests/new',
        component: () => import('../views/RequestFormView.vue'),
        meta: { title: '帳號權限申請', need: 'ACC_APPLY' }
      },
      { path: 'emergency', component: () => import('../views/EmergencyListView.vue'), meta: { title: '緊急存取' } },
      {
        path: 'emergency/new',
        component: () => import('../views/EmergencyFormView.vue'),
        meta: { title: '緊急存取申請', need: 'EMG_APPLY' }
      },
      { path: 'assets', component: () => import('../views/AssetRefView.vue'), meta: { title: '資產分類說明' } },
      {
        path: 'inspections',
        component: () => import('../views/InspectionListView.vue'),
        meta: { title: '巡檢紀錄', need: 'INSP_VIEW' }
      },
      {
        path: 'inspections/new',
        component: () => import('../views/InspectionFormView.vue'),
        meta: { title: '新增巡檢', need: 'INSP_CREATE' }
      },
      { path: 'no-access', component: () => import('../views/NoAccessView.vue'), meta: { title: '沒有存取權限' } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

function toLogin(to: RouteLocationNormalized): RouteLocationRaw {
  return to.fullPath && to.fullPath !== '/' ? { path: LOGIN_PATH, query: { redirect: to.fullPath } } : LOGIN_PATH
}

/** 守衛正在等 /me：期間的 401 由守衛自己導向（保留原目標當 redirect），處理器不另外導 */
let guarding = false

/** 全域守衛：登入 → 權限碼 */
export async function authGuard(to: RouteLocationNormalized): Promise<boolean | RouteLocationRaw> {
  if (to.meta.public) {
    return getToken() ? safeRedirect(to.query.redirect) : true
  }
  if (!getToken()) return toLogin(to)
  const s = useSession()
  guarding = true
  try {
    await s.initSession()
  } finally {
    guarding = false
  }
  // /me 回 401 時 token 已被清掉
  if (!getToken()) return toLogin(to)
  if (to.meta.need && !s.can(to.meta.need)) return '/no-access'
  return true
}

/** 401 處理：清共用狀態、記下訊息，導回登入頁（已在登入頁就不再導） */
export function installUnauthorizedHandler(r: Router): void {
  setUnauthorizedHandler(message => {
    resetSession()
    useSession().loginNotice.value = message
    const cur = r.currentRoute.value
    if (guarding || cur.meta.public) return
    void r.replace(toLogin(cur))
  })
}

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach(authGuard)
installUnauthorizedHandler(router)

export default router
