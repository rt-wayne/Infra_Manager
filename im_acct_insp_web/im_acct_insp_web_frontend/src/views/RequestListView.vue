<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: 新增：帳號申請單列表（開發文件 spec_im_acct_insp.md 第 6 節；任何身分都可進，只看自己的）三區塊：
            1. 對既有帳號提出異動申請：GET /accounts/lookup?keyword，依 inFlight／allowedReqTypes 顯示可做的異動
            2. 我曾申請新增的帳號：GET /accounts/mine
            3. 我的申請單：GET /requests?stateCode（狀態篩選送後端、單號／帳號名稱搜尋在前端）；已退件可「重新送出」
            各區塊失敗只 toast，不顯示成查無資料
            變更單 #1：S1 lookup 結果列的「查看」「歷史」只在有帳號列表權限（me.canAcctList）時顯示，異動按鈕照舊
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: v2.0（開發文件 spec_im_acct_insp_v2.0.md 第 2.4、8.3 節）：
            ① 改為輸入完整帳號名稱精確查詢（GET /accounts/lookup?acctName），顯示 U05；查無顯示 U06；在途列不顯示操作按鈕；
            異動按鈕帶 acctName 給表單（lookup/{id} 精確相符）；「查看」「歷史」只對有 ACC_LIST_FULL 者顯示
            ② 在途者只有查看、歷史，其餘依 allowedReqTypes
            ③ 狀態篩選（全部／審核中／待執行／待複驗／已核准／已退件）；審核中顯示 U10「審核中：待 ○○」；
            已退件顯示退件原因與「重新送出」（canResubmit）；異動按鈕只對有 ACC_APPLY 者顯示
-->
<template>
  <main>
    <div class="module-card">
      <h2>🔍 對既有帳號提出異動申請</h2>
      <form class="lookup" @submit.prevent="doLookup">
        <input v-model="lookupName" type="text" placeholder="請輸入完整帳號名稱" aria-label="完整帳號名稱" autocomplete="off" />
        <button type="submit" class="btn btn-primary" :disabled="lookupLoading">{{ lookupLoading ? '查詢中…' : '查詢' }}</button>
      </form>
      <div class="hint lookup-hint">{{ MSG_LOOKUP_HINT }}</div>
      <template v-if="lookupRows !== null">
        <div v-if="lookupRows.length === 0" class="note-box warn lookup-none">{{ MSG_LOOKUP_NONE }}</div>
        <div v-for="a in lookupRows" :key="a.acctId" class="kv lookup-row">
          <div class="k">{{ a.acctName || a.acctId }}（{{ catgNameOf(codes, a.catgId, a.catgName) }}）－ {{ label(ACCT_STATE_LABEL, a.acctStateCode) }}　單號 {{ a.acctId }}</div>
          <div class="v item">
            <span>{{ a.sysName || '—' }}　·　{{ a.holderName || '—' }}</span>
            <span v-if="a.inFlight" class="hint in-flight">此帳號目前已有一筆申請正在處理中，請等待該筆完成後再提出新的異動申請。</span>
            <AcctOpButtons
              v-else
              :acct-id="a.acctId"
              :acct-name="a.acctName"
              :ops="can('ACC_APPLY') ? accountOps(a) : []"
              :show-view="can('ACC_LIST_FULL')"
              @view="viewId = a.acctId"
              @history="openHistory(a.acctId, a.acctName)"
            />
          </div>
        </div>
      </template>
    </div>

    <div class="module-card">
      <h2>⭐ 我曾申請新增的帳號</h2>
      <template v-if="mine !== null">
        <div v-if="mine.length === 0" class="hint">目前沒有您送出過的新增申請。</div>
        <div v-for="a in mine" :key="a.acctId" class="kv mine-row">
          <div class="k">{{ a.acctName || (a.catgId === DOOR_CATG_ID ? '（門禁卡，待發卡）' : a.acctId) }}（{{ catgNameOf(codes, a.catgId, a.catgName) }}）－ {{ label(ACCT_STATE_LABEL, a.acctStateCode) }}　單號 {{ a.acctId }}</div>
          <div class="v item">
            <span>{{ a.sysName || '—' }}　·　{{ a.holderName || '—' }}</span>
            <span v-if="a.inFlight" class="ops">
              <span class="hint">申請處理中</span>
              <button type="button" class="btn-link" @click="viewId = a.acctId">查看</button>
              <button type="button" class="btn-link" @click="openHistory(a.acctId, a.acctName)">歷史</button>
            </span>
            <AcctOpButtons
              v-else
              :acct-id="a.acctId"
              :acct-name="a.acctName"
              :ops="can('ACC_APPLY') ? accountOps(a) : []"
              @view="viewId = a.acctId"
              @history="openHistory(a.acctId, a.acctName)"
            />
          </div>
        </div>
      </template>
    </div>

    <div class="module-card">
      <h2>📄 我的申請單</h2>
      <div class="filters">
        <input v-model="reqKeyword" type="text" placeholder="搜尋單號／帳號名稱" aria-label="搜尋單號或帳號名稱" autocomplete="off" />
        <select v-model="reqState" aria-label="申請單狀態" @change="loadRequests">
          <option v-for="o in REQ_STATE_FILTER" :key="o.value" :value="o.value">{{ o.label }}</option>
        </select>
      </div>
      <template v-if="reqs !== null">
        <div v-if="reqView.length > 0">
          <p class="res-head">共 {{ reqView.length }} 筆，第 {{ page }} / {{ totalPages }} 頁</p>
          <table class="tbl">
            <colgroup>
              <col style="width: 18%" />
              <col />
              <col style="width: 24%" />
              <col style="width: 15%" />
              <col style="width: 15%" />
            </colgroup>
            <thead>
              <tr><th>單號</th><th>類別／申請類型／帳號名稱</th><th>狀態</th><th>送出時間</th><th>操作</th></tr>
            </thead>
            <tbody>
              <tr v-for="r in pageRows" :key="r.reqId" :data-req="r.reqId">
                <td>{{ r.reqId }}</td>
                <td>{{ catgNameOf(codes, r.catgId, r.catgName) }} {{ label(REQ_TYPE_LABEL, r.reqTypeCode) }} — {{ r.acctName || (r.catgId === DOOR_CATG_ID ? '（門禁卡，待發卡）' : '—') }}</td>
                <td>
                  <span class="tag req-state" :class="stateTagClass(r.reqStateCode)">{{ reqStateText(r.reqStateCode, r.stageName, r.currStageId) }}</span>
                  <div v-if="r.reqStateCode === 'REJECTED' && r.rejectReason" class="hint warn reject-reason">退件原因：{{ r.rejectReason }}</div>
                </td>
                <td>{{ r.submitDate }}</td>
                <td>
                  <span class="ops">
                    <button type="button" class="btn-link" @click="detailId = r.reqId">查看明細</button>
                    <RouterLink
                      v-if="r.reqStateCode === 'REJECTED' && r.canResubmit !== false && can('ACC_APPLY')"
                      class="btn-link warn resubmit"
                      :to="{ path: '/requests/new', query: { resubmit: r.reqId } }"
                    >重新送出</RouterLink>
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
          <PagerBar v-model:page="page" :total-pages="totalPages" />
        </div>
        <div v-else class="empty">目前沒有符合條件的申請單</div>
      </template>
    </div>

    <AccountDetailModal v-if="viewId" :acct-id="viewId" @close="viewId = ''" />
    <AccountHistoryModal v-if="hist" :acct-id="hist.acctId" :title="hist.title" @close="hist = null" />
    <RequestDetailModal v-if="detailId" :req-id="detailId" @close="detailId = ''" />
    <ToastHost />
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import AccountDetailModal from '../components/AccountDetailModal.vue'
import AccountHistoryModal from '../components/AccountHistoryModal.vue'
import AcctOpButtons from '../components/AcctOpButtons.vue'
import PagerBar from '../components/PagerBar.vue'
import RequestDetailModal from '../components/RequestDetailModal.vue'
import ToastHost from '../components/ToastHost.vue'
import { listMyAccounts, lookupAccounts } from '../api/account'
import { errorMessage } from '../api/errors'
import { listMyRequests } from '../api/request'
import { usePaging } from '../composables/usePaging'
import { useSession } from '../composables/useSession'
import { useToast } from '../composables/useToast'
import type { Account, AccountLookup } from '../types/account'
import type { RequestItem } from '../types/request'
import { catgNameOf } from '../utils/codes'
import {
  ACCT_STATE_LABEL,
  DOOR_CATG_ID,
  REQ_STATE_FILTER,
  REQ_TYPE_LABEL,
  accountOps,
  label,
  reqStateText,
  stateTagClass
} from '../utils/labels'

/** U05 */
const MSG_LOOKUP_HINT = '請輸入完整帳號名稱（不提供模糊查詢）'
/** U06 */
const MSG_LOOKUP_NONE = '查無此帳號，請確認輸入的是完整帳號名稱'

const { toast } = useToast()
const { codes, ensureCodes, can } = useSession()

const lookupName = ref('')
const lookupRows = ref<AccountLookup[] | null>(null)
const lookupLoading = ref(false)

const mine = ref<Account[] | null>(null)

const reqKeyword = ref('')
const reqState = ref('')
const reqs = ref<RequestItem[] | null>(null)
const reqView = computed(() => {
  const k = reqKeyword.value.trim().toLowerCase()
  const list = reqs.value ?? []
  if (!k) return list
  return list.filter(r => `${r.reqId} ${r.acctName ?? ''}`.toLowerCase().includes(k))
})
const { page, totalPages, pageRows } = usePaging(reqView)

const viewId = ref('')
const detailId = ref('')
const hist = ref<{ acctId: string; title: string } | null>(null)

function openHistory(acctId: string, acctName: string | null): void {
  hist.value = { acctId, title: acctName ? `${acctId}（${acctName}）` : acctId }
}

async function doLookup(): Promise<void> {
  const name = lookupName.value.trim()
  if (!name) {
    toast(MSG_LOOKUP_HINT, 'amber')
    return
  }
  lookupLoading.value = true
  try {
    const data = await lookupAccounts(name)
    if (!Array.isArray(data)) throw new Error('unexpected response')
    lookupRows.value = data
  } catch (e) {
    toast(errorMessage(e, '查詢失敗，請稍後再試'), 'amber')
  } finally {
    lookupLoading.value = false
  }
}

async function loadMine(): Promise<void> {
  try {
    const data = await listMyAccounts()
    if (!Array.isArray(data)) throw new Error('unexpected response')
    mine.value = data
  } catch (e) {
    toast(errorMessage(e, '查詢失敗，請稍後再試'), 'amber')
  }
}

async function loadRequests(): Promise<void> {
  try {
    const data = await listMyRequests(reqState.value)
    if (!Array.isArray(data)) throw new Error('unexpected response')
    reqs.value = data
  } catch (e) {
    toast(errorMessage(e, '查詢失敗，請稍後再試'), 'amber')
  }
}

onMounted(() => {
  ensureCodes().catch(() => undefined)
  void loadMine()
  void loadRequests()
})
</script>

<style scoped>
.lookup { display: flex; gap: 10px; }
.lookup input { flex: 1; min-width: 0; }
.lookup-hint { margin: 4px 0 10px; }
.filters { display: flex; gap: 10px; margin-bottom: 10px; flex-wrap: wrap; }
.filters input { flex: 1 1 260px; min-width: 0; }
.item { display: flex; justify-content: space-between; align-items: center; gap: 4px 12px; flex-wrap: wrap; }
.reject-reason { margin-top: 4px; }
a.btn-link { display: inline-flex; align-items: center; min-height: 48px; }
</style>
