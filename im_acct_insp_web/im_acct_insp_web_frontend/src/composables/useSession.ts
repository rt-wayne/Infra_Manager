// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：目前身分與共用資料（開發文件 spec_im_acct_insp.md 第 2、6 節）
//           身分名單來自 GET /users；目前工號存 localStorage，預設名單第一位；權限以 GET /me 為準
//           代碼表 GET /codes 快取；待辦數、到期清單供頁首與側欄顯示
//           模組層共用狀態（同 useToast 寫法）；前端判斷只決定顯示，後端一律自己驗權
//           變更單 #1：L5 switchUser 加 canStay：重載 /me 後先檢查目前頁權限，可留下才遞增 viewKey 重建畫面
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（開發文件 spec_im_acct_insp_v2.0.md 第 2.1、2.5、8.1、8.2 節）：
//           移除工號選擇（users／userId／switchUser）；改為 token 登入：login／logout，/me 只在有 token 時載入；
//           can(權限碼) 依 /me 的 permissions[]；切換身分 impersonate／切回 endImpersonate（回傳 me 即更新），
//           之後以 canStay 判斷目前頁可否留下；loginNotice 存 401 時要在登入頁顯示的後端訊息
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-11
// 變更說明: BACKLOG 第 28 項：token 改各分頁共用（tokenStore 改 localStorage）；切換身分／切回後 notifyOtherTabs，
//           讓其他分頁重新整理成新身分（登入、登出換掉或清掉 token，其他分頁自然會收到）
// ============================================================
import { computed, ref } from 'vue'
import { endImpersonate as apiEndImpersonate, getMe, impersonate as apiImpersonate, login as apiLogin, logout as apiLogout } from '../api/auth'
import { getCodes, listExpiring, listTodos } from '../api/common'
import { clearToken, getToken, notifyOtherTabs, setToken } from '../api/tokenStore'
import type { Codes, ExpiringItem, Me } from '../types/common'
import { hasPerm } from '../utils/perms'
import type { PermCode } from '../utils/perms'

const me = ref<Me | null>(null)
/** /me 載入失敗（非 401，例如連不上）時為 true；畫面提示 U09 */
const meFailed = ref(false)
const codes = ref<Codes | null>(null)
const todoCount = ref<number | null>(null)
const expiring = ref<ExpiringItem[]>([])
const bellOpen = ref(false)
/** 切換身分後遞增；版面以它當 router-view 的 key，讓畫面重新載入資料 */
const viewKey = ref(0)
/** 被導回登入頁時要顯示的訊息（401 的後端 message） */
const loginNotice = ref('')

let initPromise: Promise<void> | null = null
let codesPromise: Promise<Codes> | null = null

async function loadMe(): Promise<void> {
  try {
    me.value = await getMe()
    meFailed.value = false
  } catch {
    me.value = null
    // 401 已由 http.ts 清掉 token 並導回登入頁；其他錯誤保留 token，畫面提示稍後再試
    meFailed.value = getToken() !== ''
  }
}

/** 頁首鈴鐺與側欄待辦數；失敗時不擋畫面（待辦數顯示空白） */
async function refreshBadges(): Promise<void> {
  if (!getToken()) return
  const [t, e] = await Promise.allSettled([listTodos(), listExpiring()])
  todoCount.value = t.status === 'fulfilled' && Array.isArray(t.value) ? t.value.length : null
  expiring.value = e.status === 'fulfilled' && Array.isArray(e.value) ? e.value : []
}

/** 有 token 時載 /me；只跑一次，路由守衛與版面共用（登出、401 後由 resetSession 重來） */
function initSession(): Promise<void> {
  if (!initPromise) {
    initPromise = getToken() ? loadMe() : Promise.resolve()
  }
  return initPromise
}

/** 登入：成功存 token 與 me；失敗 reject（A01／A02 由登入頁顯示後端 message） */
async function login(userId: string, password: string): Promise<void> {
  const r = await apiLogin(userId, password)
  resetSession()
  setToken(r.token)
  me.value = r.me
  initPromise = Promise.resolve()
}

/** 登出：通知後端作廢 token（失敗也照樣清掉本機 token），清空共用狀態 */
async function logout(): Promise<void> {
  try {
    if (getToken()) await apiLogout()
  } catch {
    // 後端已失效或連不上：本機照樣登出
  }
  clearToken()
  resetSession()
}

/** 套用切換／切回後的 me，再以 canStay 判斷目前頁能否留下；可留下才遞增 viewKey 重建畫面 */
function applyMe(next: Me, canStay: () => boolean): boolean {
  // 身分記在後端 token 上，各分頁共用同一 token：其他分頁要重新整理才會一致
  notifyOtherTabs()
  me.value = next
  meFailed.value = false
  bellOpen.value = false
  const stay = canStay()
  if (stay) viewKey.value++
  void refreshBadges()
  return stay
}

/** 切換身分（實際使用者需 SYS_IMPERSONATE）；回傳目前頁可否留下 */
async function impersonate(targetUserId: string, canStay: () => boolean = () => true): Promise<boolean> {
  return applyMe(await apiImpersonate(targetUserId), canStay)
}

/** 切回本人；回傳目前頁可否留下 */
async function endImpersonate(canStay: () => boolean = () => true): Promise<boolean> {
  return applyMe(await apiEndImpersonate(), canStay)
}

/** 代碼表（快取）；失敗時 reject，下次呼叫重試 */
function ensureCodes(): Promise<Codes> {
  if (codes.value) return Promise.resolve(codes.value)
  if (!codesPromise) {
    codesPromise = getCodes()
      .then(c => {
        codes.value = c
        return c
      })
      .finally(() => {
        codesPromise = null
      })
  }
  return codesPromise
}

/** 清掉模組層狀態（登出、401、測試用）；loginNotice 不在此清 */
export function resetSession(): void {
  me.value = null
  meFailed.value = false
  codes.value = null
  todoCount.value = null
  expiring.value = []
  bellOpen.value = false
  viewKey.value = 0
  initPromise = null
  codesPromise = null
}

/** 權限判斷（有效身分的 permissions[]） */
function can(code: PermCode): boolean {
  return hasPerm(me.value?.permissions, code)
}

export function useSession() {
  /** 有效身分顯示名（切換中為代理身分） */
  const userName = computed(() => me.value?.userName ?? '')
  /** 實際登入者顯示名 */
  const realUserName = computed(() => me.value?.realUserName || me.value?.userName || '')
  const impersonating = computed(() => me.value?.impersonating === true)
  const canImpersonate = computed(() => me.value?.canImpersonate === true)
  return {
    me,
    meFailed,
    codes,
    todoCount,
    expiring,
    bellOpen,
    viewKey,
    loginNotice,
    userName,
    realUserName,
    impersonating,
    canImpersonate,
    can,
    initSession,
    login,
    logout,
    impersonate,
    endImpersonate,
    refreshBadges,
    ensureCodes,
    loadMe
  }
}
