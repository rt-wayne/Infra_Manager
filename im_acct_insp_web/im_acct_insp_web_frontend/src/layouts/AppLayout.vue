<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: 新增：版面外框（開發文件 spec_im_acct_insp.md 第 6 節；配置依原型）
            左側導覽：首頁、待辦簽核＋數量、帳號權限管理區、機房巡檢區（無權限項目加鎖頭，點了進無權限頁）
            頁首：頁名、身分切換下拉（GET /users）、鈴鐺＋到期清單（GET /home/expiring）、登入者
            切換身分後重載 /me、徽章，並以 viewKey 讓目前畫面重新載入；目前頁需要的權限沒有時轉無權限頁
            router-view 的 key 含 fullPath：同一畫面換 query（例 新增申請 ↔ 異動申請）也會重建表單
            身分名單載入完成前不顯示內容區，避免畫面在沒有 X-User-Id 時先打 API
            變更單 #1：L5 切換身分先重載 /me 並檢查目前頁 meta.need，無權限直接轉 /no-access（不重建畫面），
            有權限才遞增 viewKey 重建，避免先以新身分打一次 API 跳 403 toast
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: v2.0（開發文件 spec_im_acct_insp_v2.0.md 第 8.2 節）：拿掉身分下拉；頁首顯示登入者（實際使用者）與「登出」；
            canImpersonate 時顯示「切換身分」（彈窗選人，名單 GET /users）；切換中固定顯示 U01「目前以 ○○ 身分操作」＋「切回」；
            切換／切回後目前頁無權限時導向首頁；導覽依 permissions[]：無 ACC_LIST_FULL 者「帳號列表」改指向帳號申請單列表，
            機房巡檢區有 INSP_VIEW 才顯示、新增巡檢有 INSP_CREATE 才顯示；
            鈴鐺含已逾期，點擊一筆：有 ACC_LIST_FULL → 帳號列表（到期篩選），否則開該筆唯讀明細
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-11
  變更說明: BACKLOG 第 39 項：無 ACC_LIST_FULL 時「帳號列表」仍指向申請單列表，但不反白、不帶 aria-current，
            在申請單列表頁只反白「帳號申請單列表」（原本兩項同時反白）
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-12
  變更說明: BACKLOG 第 42 項：「帳號列表」只對有 ACC_LIST_FULL 者顯示、一律連 /accounts；
            無權限者不再顯示（原本改指向申請單列表，與「帳號申請單列表」重複），第 39 項的不反白處理一併移除
-->
<template>
  <div class="app">
    <aside class="sidebar">
      <div class="brand">機房與帳號權限管理系統<small>Facility &amp; Account Management</small></div>
      <RouterLink class="nav-item" to="/">🏠 首頁</RouterLink>
      <RouterLink class="nav-item" to="/todo">
        <span>✅ 待辦簽核</span><span v-if="todoCount !== null" class="nav-badge">{{ todoCount }}</span>
      </RouterLink>
      <div class="nav-title">帳號權限管理</div>
      <RouterLink v-if="can('ACC_LIST_FULL')" class="nav-item nav-accounts" to="/accounts">📋 帳號列表</RouterLink>
      <RouterLink v-if="can('ACC_SELF_SERVICE')" class="nav-item nav-requests" to="/requests">📄 帳號申請單列表</RouterLink>
      <RouterLink v-if="can('ACC_APPLY')" class="nav-item" to="/requests/new">＋ 新增申請</RouterLink>
      <RouterLink class="nav-item" to="/assets">📖 資產分類說明</RouterLink>
      <template v-if="can('INSP_VIEW')">
        <div class="nav-title nav-insp">機房巡檢</div>
        <RouterLink class="nav-item" to="/inspections">📋 巡檢紀錄</RouterLink>
        <RouterLink v-if="can('INSP_CREATE')" class="nav-item nav-insp-new" to="/inspections/new">＋ 新增巡檢</RouterLink>
      </template>
    </aside>

    <div class="main">
      <header class="topbar" :class="{ impersonating }">
        <h1>{{ pageTitle }}</h1>
        <div class="user">
          <span v-if="impersonating" class="imp-banner">
            <span>目前以 <b>{{ userName }}</b> 身分操作</span>
            <button type="button" class="btn btn-warn imp-end" :disabled="switching" @click="onEndImpersonate">切回</button>
          </span>
          <button v-else-if="canImpersonate" type="button" class="btn imp-start" @click="showImp = true">切換身分</button>
          <div ref="bellWrap" class="bell-wrap">
            <button type="button" class="bell-btn" aria-label="到期提醒" @click="bellOpen = !bellOpen">
              🔔<span v-if="expiring.length > 0" class="bell-badge">{{ expiring.length }}</span>
            </button>
            <div v-if="bellOpen" class="bell-panel">
              <div class="bell-panel-title">⚠️ 30 天內到期或已逾期</div>
              <button v-for="x in expiring" :key="x.acctId" type="button" class="bell-item" @click="onBellItem(x)">
                <span><b>{{ x.acctName || x.acctId }}</b>（{{ x.catgName }}）{{ dateOnly(x.expiryDate) }}</span>
                <span class="bell-days">{{ daysLeftText(x.daysLeft) }}</span>
              </button>
              <div v-if="expiring.length === 0" class="hint">目前沒有與您相關的到期帳號</div>
              <button v-if="can('ACC_LIST_FULL') && expiring.length > 0" type="button" class="btn full" @click="goExpiring">查看全部到期清單</button>
            </div>
          </div>
          <span class="who">👤 {{ realUserName }}</span>
          <button type="button" class="btn logout" @click="onLogout">登出</button>
        </div>
      </header>
      <div class="content">
        <RouterView v-if="ready" :key="`${viewKey}:${route.fullPath}`" />
        <p v-else class="hint">載入中…</p>
      </div>
    </div>

    <ImpersonateModal v-if="showImp" :busy="switching" @close="showImp = false" @select="onImpersonate" />
    <AccountDetailModal v-if="bellAcctId" :acct-id="bellAcctId" readonly @close="bellAcctId = ''" />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import '../assets/app.css'
import AccountDetailModal from '../components/AccountDetailModal.vue'
import ImpersonateModal from '../components/ImpersonateModal.vue'
import { errorMessage } from '../api/errors'
import { useSession } from '../composables/useSession'
import { useToast } from '../composables/useToast'
import type { ExpiringItem } from '../types/common'
import { dateOnly } from '../utils/date'
import { daysLeftText } from '../utils/labels'

const route = useRoute()
const router = useRouter()
const { toast } = useToast()
const {
  userName, realUserName, impersonating, canImpersonate, todoCount, expiring, bellOpen, viewKey, meFailed,
  can, initSession, impersonate, endImpersonate, logout, refreshBadges
} = useSession()

const ready = ref(false)
const bellWrap = ref<HTMLElement | null>(null)
const showImp = ref(false)
const switching = ref(false)
const bellAcctId = ref('')

const pageTitle = computed(() => (typeof route.meta.title === 'string' ? route.meta.title : ''))

/** 目前身分是否有目前頁需要的權限（meta.need） */
function hasAccess(): boolean {
  const need = route.meta.need
  return !need || can(need)
}

/** 切換／切回後：目前頁沒有權限就回首頁 */
async function afterSwitch(stay: boolean): Promise<void> {
  if (!stay) await router.replace('/')
}

async function onImpersonate(userId: string): Promise<void> {
  if (!userId || switching.value) return
  switching.value = true
  try {
    const stay = await impersonate(userId, hasAccess)
    showImp.value = false
    await afterSwitch(stay)
  } catch (e) {
    toast(errorMessage(e, '切換身分失敗，請稍後再試'), 'amber')
  } finally {
    switching.value = false
  }
}

async function onEndImpersonate(): Promise<void> {
  if (switching.value) return
  switching.value = true
  try {
    await afterSwitch(await endImpersonate(hasAccess))
  } catch (e) {
    toast(errorMessage(e, '切回失敗，請稍後再試'), 'amber')
  } finally {
    switching.value = false
  }
}

async function onLogout(): Promise<void> {
  await logout()
  await router.replace('/login')
}

function goExpiring(): void {
  bellOpen.value = false
  void router.push({ path: '/accounts', query: { expiring: '1' } })
}

/** 鈴鐺一筆：有完整帳號列表權限 → 帳號列表（到期篩選）；否則開該筆唯讀明細 */
function onBellItem(x: ExpiringItem): void {
  if (can('ACC_LIST_FULL')) {
    goExpiring()
  } else {
    bellOpen.value = false
    bellAcctId.value = x.acctId
  }
}

function onDocClick(ev: MouseEvent): void {
  if (bellOpen.value && bellWrap.value && ev.target instanceof Node && !bellWrap.value.contains(ev.target)) {
    bellOpen.value = false
  }
}

// 換頁時更新待辦數（核准、退件後回側欄可看到新數字）
watch(() => route.fullPath, () => {
  if (ready.value) void refreshBadges()
})

onMounted(async () => {
  document.addEventListener('click', onDocClick)
  await initSession()
  if (meFailed.value) toast('查詢失敗，請稍後再試', 'amber')
  ready.value = true
  void refreshBadges()
})

onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
})
</script>

<style scoped>
.full { width: 100%; margin-top: 8px; }
.topbar.impersonating { position: sticky; top: 0; z-index: 30; }
.imp-banner { display: inline-flex; align-items: center; gap: 8px; background: #fdf3e9; border: 1px solid #e8c79a;
  color: #92400e; border-radius: 8px; padding: 2px 4px 2px 12px; font-size: 18px; }
.imp-banner .btn { min-height: 48px; padding: 4px 14px; font-size: 18px; }
.imp-start, .logout { min-height: 48px; padding: 4px 14px; font-size: 18px; }
.who { color: var(--dark); }
button.bell-item { width: 100%; background: none; border: none; border-bottom: 1px solid var(--line); text-align: left;
  cursor: pointer; color: inherit; font-family: inherit; min-height: 48px; align-items: center; }
button.bell-item:hover { background: var(--teal-bg); }
</style>
