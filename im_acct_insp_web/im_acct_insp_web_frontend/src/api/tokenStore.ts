// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：登入 token 存取（開發文件 spec_im_acct_insp_v2.0.md 第 8.1 節：token 存 sessionStorage）
//           取代舊 userIdStore（X-User-Id 已移除）；http.ts 的 interceptor 讀它帶 Authorization: Bearer
//           本檔不 import 其他模組，避免循環引用
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-11
// 變更說明: BACKLOG 第 28 項（刻意偏離 8.1 節）：token 改存 localStorage，同瀏覽器各分頁共用一份、關瀏覽器不登出；
//           新增 notifyOtherTabs（token 不變但身分變了時通知其他分頁，例如切換身分／切回）、
//           watchOtherTabs（其他分頁換 token、清 token 或發通知時，本分頁整頁重新整理成新身分；
//           從上一頁快取還原時也重新整理，避免漏接凍結期間的通知）
//           同日審查後：上一頁快取還原時，只有 token 或通知值跟離開時不同才重新整理（身分沒變時保留填到一半的表單）
// ============================================================

const KEY = 'im_acct_insp_web.token'
/** 身分變更通知；值每次不同（storage 事件只在值改變時觸發），內容無意義 */
const SIGNAL_KEY = 'im_acct_insp_web.identityChanged'

/** 目前 token；沒有或無法讀 localStorage 時回空字串 */
export function getToken(): string {
  try {
    return localStorage.getItem(KEY) ?? ''
  } catch {
    return ''
  }
}

/** 設定 token（空字串＝清除）；值有變時其他分頁會收到 storage 事件 */
export function setToken(token: string): void {
  try {
    if (token) {
      localStorage.setItem(KEY, token)
    } else {
      localStorage.removeItem(KEY)
    }
  } catch {
    // localStorage 不可用時只是不記住，重新整理後需重新登入
  }
}

/** 清除 token（登出、401） */
export function clearToken(): void {
  setToken('')
}

/** 通知其他分頁身分已變（token 沒變的情況：切換身分、切回） */
export function notifyOtherTabs(): void {
  try {
    localStorage.setItem(SIGNAL_KEY, `${Date.now()}-${Math.random()}`)
  } catch {
    // localStorage 不可用：其他分頁等下一次重新整理才會看到新身分
  }
}

/**
 * 監聽其他分頁的登入、登出、401 清 token 與身分變更通知，收到即執行 onChange（預設整頁重新整理）。
 * storage 事件只在「其他」分頁觸發，本分頁自己寫入不會觸發，不會自我重整成迴圈。
 * key 為 null 代表其他程式呼叫了 localStorage.clear()，token 也一併沒了，同樣處理。
 * 頁面從瀏覽器「上一頁快取」還原（pageshow 且 persisted）時，快取期間的通知已漏接：
 * 跟離開時（pagehide）記下的 token 與通知值比對，有變才同樣處理；沒有離開紀錄時視為有變。
 * 回傳移除監聽的函式（測試用）
 */
export function watchOtherTabs(onChange: () => void = () => location.reload()): () => void {
  // 離開頁面當下的 token＋通知值；本分頁自己寫入的也算在內，不必在 setToken／notifyOtherTabs 另外記
  let leftWith: string | null = null
  const snapshot = (): string => {
    try {
      return `${localStorage.getItem(KEY) ?? ''}|${localStorage.getItem(SIGNAL_KEY) ?? ''}`
    } catch {
      return ''
    }
  }
  const onStorage = (e: StorageEvent): void => {
    try {
      if (e.storageArea !== localStorage) return
    } catch {
      return
    }
    if (e.key === null || e.key === KEY || e.key === SIGNAL_KEY) onChange()
  }
  const onPageHide = (): void => {
    leftWith = snapshot()
  }
  const onPageShow = (e: PageTransitionEvent): void => {
    if (e.persisted && snapshot() !== leftWith) onChange()
  }
  window.addEventListener('storage', onStorage)
  window.addEventListener('pagehide', onPageHide)
  window.addEventListener('pageshow', onPageShow)
  return () => {
    window.removeEventListener('storage', onStorage)
    window.removeEventListener('pagehide', onPageHide)
    window.removeEventListener('pageshow', onPageShow)
  }
}
