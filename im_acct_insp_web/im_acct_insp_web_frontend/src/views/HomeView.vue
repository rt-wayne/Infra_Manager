<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: 新增：首頁（開發文件 spec_im_acct_insp.md 第 6 節；GET /home/stats）6 張卡片
            「30 天內到期」可點：有帳號列表權限到 /accounts，沒有就打開頁首鈴鐺清單
            載入失敗只 toast，卡片顯示「—」
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: v2.0（開發文件 spec_im_acct_insp_v2.0.md 第 4.13、8.3 節）：卡片名稱依 4.13 口徑（待審核、30 天內到期含已逾期、
            本月發現異常項目數＝未處理）；「30 天內到期」點擊：有 ACC_LIST_FULL → 帳號列表（到期篩選），否則 → 無權限頁
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-13
  變更說明: BACKLOG 第 46 項 S8（SA §7.1）：新增「🚨 緊急存取」區塊兩張卡（待補核准＝尚未處理含逾期、異常待追蹤＝已退回未結案；
            數字看全部）；點擊：有 EMG_VIEW_ALL → 緊急存取清單（?state=UNDECIDED／RETURNED），否則 → 無權限頁
-->
<template>
  <main>
    <div class="module-card">
      <h2>🔑 帳號權限管理</h2>
      <div class="stat-row">
        <div class="stat-card"><div class="num">{{ n(stats?.acctTotal) }}</div><div class="lbl">帳號總數</div></div>
        <div class="stat-card"><div class="num">{{ n(stats?.acctReviewing) }}</div><div class="lbl">待審核</div></div>
        <div class="stat-card click card-expiring" title="點擊查看到期清單" @click="onExpiringClick">
          <div class="num red">{{ n(stats?.acctExpiring) }}</div><div class="lbl">30 天內到期（含已逾期）⚠️</div>
        </div>
        <div class="stat-card"><div class="num">{{ n(stats?.acctPriv) }}</div><div class="lbl">特權帳號</div></div>
      </div>
    </div>
    <div class="module-card">
      <h2>🚨 緊急存取</h2>
      <div class="stat-row">
        <div class="stat-card click card-emg-undecided" title="點擊查看待補核准清單" @click="onEmgClick(EMG_UNDECIDED)">
          <div class="num warn">{{ n(stats?.emgUndecided) }}</div><div class="lbl">緊急存取待補核准</div>
        </div>
        <div class="stat-card click card-emg-returned" title="點擊查看已退回清單" @click="onEmgClick(EMG_RETURNED)">
          <div class="num red">{{ n(stats?.emgReturned) }}</div><div class="lbl">緊急存取異常待追蹤</div>
        </div>
      </div>
    </div>
    <div class="module-card">
      <h2>🛠 機房巡檢</h2>
      <div class="stat-row">
        <div class="stat-card"><div class="num">{{ n(stats?.inspThisMonth) }}</div><div class="lbl">本月巡檢次數</div></div>
        <div class="stat-card"><div class="num warn">{{ n(stats?.inspExcptThisMonth) }}</div><div class="lbl">本月發現異常項目數（未處理）</div></div>
      </div>
    </div>
    <ToastHost />
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import ToastHost from '../components/ToastHost.vue'
import { getHomeStats } from '../api/common'
import { errorMessage } from '../api/errors'
import { useSession } from '../composables/useSession'
import { useToast } from '../composables/useToast'
import type { HomeStats } from '../types/common'
import { EMG_RETURNED, EMG_UNDECIDED } from '../utils/labels'

const router = useRouter()
const { toast } = useToast()
const { can } = useSession()
const stats = ref<HomeStats | null>(null)

function n(v: number | undefined): string {
  return typeof v === 'number' ? String(v) : '—'
}

function onExpiringClick(): void {
  if (can('ACC_LIST_FULL')) {
    void router.push({ path: '/accounts', query: { expiring: '1' } })
  } else {
    void router.push('/no-access')
  }
}

/** 卡片數字看全部；沒有 EMG_VIEW_ALL 的人清單只看得到自己的單，筆數對不上，所以導無權限頁 */
function onEmgClick(state: string): void {
  if (can('EMG_VIEW_ALL')) {
    void router.push({ path: '/emergency', query: { state } })
  } else {
    void router.push('/no-access')
  }
}

onMounted(async () => {
  try {
    stats.value = await getHomeStats()
  } catch (e) {
    toast(errorMessage(e, '查詢失敗，請稍後再試'), 'amber')
  }
})
</script>

<style scoped>
.num.red { color: var(--red); }
.num.warn { color: var(--orange); }
</style>
