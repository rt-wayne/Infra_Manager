<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: 新增：帳號列表／查詢（開發文件 spec_im_acct_insp.md 第 6 節；GET /accounts，需 ACCT_LIST）
            篩選：關鍵字（按搜尋或 Enter）、狀態、資產分類碼送後端；類別分頁籤（含數量）在前端依查詢結果切換
            「未分類」不送 assetClassId，改在前端篩 assetClassId 為空者
            欄位同原型；操作：查看、歷史，依狀態與 inFlight 顯示修改／刪除／展延／停用／重新啟用
            到期提醒橫幅（GET /home/expiring）；查詢失敗只 toast，不顯示成查無資料
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: v2.0（開發文件 spec_im_acct_insp_v2.0.md 第 4.13、6.4、8.3 節）：/accounts 改回 { items, stateCounts, catgCounts, expiringCount }；
            上方橫幅 U14（expiringCount＞0，點擊套用到期篩選；query expiring=1 進來時預設套用）；
            類別頁籤 8 類都顯示、數量用 catgCounts，類別改送後端；狀態篩選含數量、含已逾期，預設全部不含已刪除；
            欄位：單號、類別、帳號名稱、所屬系統、使用人、資產分類、特權、到期日、狀態＋「待複驗」標記（U11）；
            操作：查看、歷史，有 ACC_APPLY 時依 allowedReqTypes 顯示修改／停用／重新啟用／刪除
-->
<template>
  <main>
    <button v-if="expiringCount > 0" type="button" class="expiry-banner" @click="applyExpiring">
      ⚠️ 有 {{ expiringCount }} 個帳號 30 天內到期或已逾期<span class="banner-go">（點此只看這些帳號）</span>
    </button>

    <div class="toolbar">
      <form class="filters" @submit.prevent="load">
        <input v-model="keyword" type="text" placeholder="搜尋帳號名稱 / 單號 / 使用人" aria-label="關鍵字" autocomplete="off" />
        <button type="submit" class="btn btn-primary" :disabled="loading">{{ loading ? '查詢中…' : '搜尋' }}</button>
        <select v-model="stateCode" aria-label="狀態" class="state-filter" @change="load">
          <option value="">全部狀態（不含已刪除）{{ loaded ? `　${allStateCount}` : '' }}</option>
          <option v-for="s in ACCT_STATE_ORDER" :key="s" :value="s">{{ ACCT_STATE_LABEL[s] }}{{ loaded ? `　${stateCounts[s] ?? 0}` : '' }}</option>
        </select>
        <select v-model="assetFilter" aria-label="資產分類碼" @change="load">
          <option value="">全部資產分類碼</option>
          <option :value="NONE">未分類</option>
          <option v-for="a in codes?.assetClasses ?? []" :key="a.assetClassId" :value="a.assetClassId">{{ a.assetClassId }}</option>
        </select>
      </form>
      <RouterLink v-if="can('ACC_APPLY')" class="btn btn-primary" to="/requests/new">＋ 新增申請</RouterLink>
    </div>

    <div v-if="expiringOnly" class="filter-chip">
      目前只顯示 30 天內到期或已逾期的帳號
      <button type="button" class="btn-link clear-expiring" @click="clearExpiring">取消到期篩選</button>
    </div>

    <div class="cat-tabs">
      <button type="button" class="cat-tab" :class="{ active: catTab === '' }" @click="setTab('')">
        全部 <span class="cat-count">{{ loaded ? allCatgCount : '—' }}</span>
      </button>
      <button
        v-for="c in categories"
        :key="c.catgId"
        type="button"
        class="cat-tab"
        :class="{ active: catTab === c.catgId }"
        :data-catg="c.catgId"
        @click="setTab(c.catgId)"
      >{{ c.catgName }} <span class="cat-count">{{ loaded ? catgCounts[c.catgId] ?? 0 : '—' }}</span></button>
    </div>

    <template v-if="loaded">
      <div v-if="viewRows.length > 0">
        <p class="res-head">共 {{ viewRows.length }} 筆，第 {{ page }} / {{ totalPages }} 頁</p>
        <table class="tbl acct">
          <colgroup>
            <col style="width: 13%" />
            <col style="width: 10%" />
            <col style="width: 11%" />
            <col style="width: 11%" />
            <col style="width: 8%" />
            <col style="width: 7%" />
            <col style="width: 10%" />
            <col style="width: 10%" />
            <col style="width: 20%" />
          </colgroup>
          <thead>
            <tr>
              <th>單號</th><th>類別</th><th>帳號名稱</th><th>所屬系統</th><th>使用人</th>
              <th>資產分類</th><th>到期日</th><th>狀態</th><th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="a in pageRows" :key="a.acctId" :data-acct="a.acctId">
              <td>{{ a.acctId }}</td>
              <td>
                <span class="tag tag-cat">{{ catgNameOf(codes, a.catgId, a.catgName) }}</span>
                <span v-if="isOn(a.isPriv)" class="tag tag-priv">特權</span>
              </td>
              <td>{{ a.acctName || (a.catgId === DOOR_CATG_ID ? '（門禁卡，待發卡）' : '—') }}</td>
              <td>{{ a.catgId === DOOR_CATG_ID ? label(DOOR_SCOPE_LABEL, a.doorScopeCode) : a.sysName || '—' }}</td>
              <td>{{ a.holderName || '—' }}</td>
              <td>{{ a.assetClassId || '未分類' }}</td>
              <td>{{ a.expiryTypeCode === 'DATE' ? dateOnly(a.expiryDate) || '—' : '永久' }}</td>
              <td>
                <span class="tag" :class="stateTagClass(a.acctStateCode)">{{ label(ACCT_STATE_LABEL, a.acctStateCode) }}</span>
                <span v-if="a.pendingVerify" class="tag tag-info pending-verify">待複驗</span>
                <div v-else-if="a.inFlight" class="hint">申請處理中</div>
              </td>
              <td>
                <AcctOpButtons
                  :acct-id="a.acctId"
                  :acct-name="a.acctName"
                  :ops="can('ACC_APPLY') ? accountOps(a) : []"
                  @view="viewId = a.acctId"
                  @history="openHistory(a)"
                />
              </td>
            </tr>
          </tbody>
        </table>
        <PagerBar v-model:page="page" :total-pages="totalPages" />
      </div>
      <div v-else class="empty">查無資料，請換個條件再試一次</div>
    </template>

    <AccountDetailModal v-if="viewId" :acct-id="viewId" @close="viewId = ''" />
    <AccountHistoryModal v-if="hist" :acct-id="hist.acctId" :title="hist.title" @close="hist = null" />
    <ToastHost />
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import AccountDetailModal from '../components/AccountDetailModal.vue'
import AccountHistoryModal from '../components/AccountHistoryModal.vue'
import AcctOpButtons from '../components/AcctOpButtons.vue'
import PagerBar from '../components/PagerBar.vue'
import ToastHost from '../components/ToastHost.vue'
import { listAccounts } from '../api/account'
import { errorMessage } from '../api/errors'
import { usePaging } from '../composables/usePaging'
import { useSession } from '../composables/useSession'
import { useToast } from '../composables/useToast'
import type { Account } from '../types/account'
import { catgNameOf } from '../utils/codes'
import { dateOnly } from '../utils/date'
import {
  ACCT_STATE_LABEL,
  ACCT_STATE_ORDER,
  DOOR_CATG_ID,
  DOOR_SCOPE_LABEL,
  accountOps,
  isOn,
  label,
  stateTagClass
} from '../utils/labels'

const NONE = '__NONE__'

const route = useRoute()
const { toast } = useToast()
const { codes, ensureCodes, can } = useSession()

const keyword = ref('')
const stateCode = ref('')
const assetFilter = ref('')
const catTab = ref('')
const expiringOnly = ref(route.query.expiring === '1')
const rows = ref<Account[]>([])
const stateCounts = ref<Record<string, number>>({})
const catgCounts = ref<Record<string, number>>({})
const expiringCount = ref(0)
const loaded = ref(false)
const loading = ref(false)
const viewId = ref('')
const hist = ref<{ acctId: string; title: string } | null>(null)

/** 8 類都顯示（含尚未開放新增的類別） */
const categories = computed(() => [...(codes.value?.categories ?? [])].sort((a, b) => a.sortSeq - b.sortSeq))
/** 「未分類」由前端篩 assetClassId 為空者 */
const viewRows = computed(() => (assetFilter.value === NONE ? rows.value.filter(a => !a.assetClassId) : rows.value))
const { page, totalPages, pageRows } = usePaging(viewRows)
const allCatgCount = computed(() => Object.values(catgCounts.value).reduce((s, n) => s + n, 0))
/** 「全部狀態」不含已刪除 */
const allStateCount = computed(() =>
  Object.entries(stateCounts.value).reduce((s, [k, n]) => (k === 'DELETED' ? s : s + n), 0)
)

function openHistory(a: Account): void {
  hist.value = { acctId: a.acctId, title: a.acctName ? `${a.acctId}（${a.acctName}）` : a.acctId }
}

function setTab(catgId: string): void {
  catTab.value = catgId
  void load()
}

function applyExpiring(): void {
  expiringOnly.value = true
  void load()
}

function clearExpiring(): void {
  expiringOnly.value = false
  void load()
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const data = await listAccounts({
      keyword: keyword.value.trim(),
      catgId: catTab.value,
      stateCode: stateCode.value,
      assetClassId: assetFilter.value === NONE ? '' : assetFilter.value,
      expiring: expiringOnly.value ? '1' : ''
    })
    if (!data || !Array.isArray(data.items)) throw new Error('unexpected response')
    rows.value = data.items
    stateCounts.value = data.stateCounts ?? {}
    catgCounts.value = data.catgCounts ?? {}
    expiringCount.value = typeof data.expiringCount === 'number' ? data.expiringCount : 0
    loaded.value = true
  } catch (e) {
    toast(errorMessage(e, '查詢失敗，請稍後再試'), 'amber')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  ensureCodes().catch(() => toast('查詢失敗，請稍後再試', 'amber'))
  await load()
})
</script>

<style scoped>
.acct { font-size: 17px; }
.acct .tag { font-size: 15px; }
.expiry-banner { display: block; width: 100%; text-align: left; background: #fdf3e9; border: 1px solid #e8c79a; border-radius: 8px;
  padding: 10px 16px; margin-bottom: 14px; font-weight: 700; color: #92400e; font-size: 19px; cursor: pointer; }
.expiry-banner:hover { background: #fbe8d3; }
.banner-go { font-weight: 400; font-size: 17px; }
.filter-chip { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; background: #eef3f8; border: 1px solid var(--line);
  border-radius: 8px; padding: 4px 12px; margin-bottom: 10px; font-size: 18px; color: var(--navy); }
.state-filter { max-width: 300px; }
.cat-tabs { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 12px; }
.cat-tab { background: #fff; border: 1px solid #cbd5e1; border-radius: 999px; padding: 4px 14px; font-size: 17px;
  color: var(--gray); cursor: pointer; display: flex; align-items: center; gap: 6px; }
.cat-tab.active { background: var(--navy); color: #fff; border-color: var(--navy); font-weight: 700; }
.cat-count { background: #eef1f5; color: var(--gray); border-radius: 999px; padding: 0 8px; font-size: 15px; font-weight: 700; }
.cat-tab.active .cat-count { background: rgba(255, 255, 255, .22); color: #fff; }
a.btn { display: inline-flex; align-items: center; min-height: 48px; text-decoration: none; }
</style>
