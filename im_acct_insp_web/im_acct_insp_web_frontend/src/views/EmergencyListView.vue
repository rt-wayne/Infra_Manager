<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-13
  變更說明: 新增：緊急存取清單（BACKLOG 第 46 項 S3，裁示 ⑫；GET /emergencies，登入即可）
            篩選：關鍵字（單號、申請人、帳號或系統）、狀態（五種計算值）、
            送出日期（全部（預設，S3 裁示②A：逾期未處理的舊單不被藏起來）／近 7／30／90 天／自訂區間）
            無 EMG_VIEW_ALL 者顯示「只顯示您送出的緊急存取單」（實際範圍由後端限制）；
            「＋ 緊急存取申請」只對有 EMG_APPLY 者顯示；「查看」開唯讀詳情彈窗；查詢失敗只 toast
-->
<template>
  <main>
    <div class="toolbar">
      <form class="filters" @submit.prevent="load">
        <input v-model="keyword" type="text" placeholder="搜尋單號 / 申請人 / 帳號或系統" aria-label="關鍵字" autocomplete="off" />
        <button type="submit" class="btn btn-primary" :disabled="loading">{{ loading ? '查詢中…' : '搜尋' }}</button>
        <select v-model="stateCode" aria-label="狀態" @change="load">
          <option v-for="o in EMG_STATE_FILTER" :key="o.value" :value="o.value">{{ o.label }}</option>
        </select>
        <select v-model="dateRange" aria-label="送出日期區間" @change="load">
          <option value="all">全部日期</option>
          <option value="7">近 7 天</option>
          <option value="30">近 30 天</option>
          <option value="90">近 90 天</option>
          <option value="custom">自訂區間</option>
        </select>
        <span v-if="dateRange === 'custom'" class="range">
          <input v-model="dateFrom" type="date" aria-label="起日" @change="load" />
          <span>～</span>
          <input v-model="dateTo" type="date" aria-label="迄日" @change="load" />
        </span>
      </form>
      <RouterLink v-if="can('EMG_APPLY')" class="btn btn-primary emg-new" to="/emergency/new">＋ 緊急存取申請</RouterLink>
    </div>
    <p v-if="!can('EMG_VIEW_ALL')" class="hint own-only">只顯示您送出的緊急存取單</p>

    <template v-if="loaded">
      <div v-if="rows.length > 0">
        <p class="res-head">共 {{ rows.length }} 筆，第 {{ page }} / {{ totalPages }} 頁</p>
        <table class="tbl">
          <colgroup>
            <col style="width: 15%" />
            <col style="width: 13%" />
            <col style="width: 10%" />
            <col style="width: 11%" />
            <col />
            <col style="width: 13%" />
            <col style="width: 12%" />
            <col style="width: 7%" />
          </colgroup>
          <thead>
            <tr>
              <th>單號</th><th>送出時間</th><th>申請人</th><th>帳號類別</th><th>帳號或系統</th><th>補核准期限</th><th>狀態</th><th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in pageRows" :key="r.emgId">
              <td>{{ r.emgId }}</td>
              <td>{{ toMinute(r.submitDate) }}</td>
              <td>{{ r.applyByName || r.applyBy }}</td>
              <td>{{ r.catgName || r.catgId }}</td>
              <td>{{ r.targetDescr }}</td>
              <td>{{ toMinute(r.dueDate) }}</td>
              <td><span class="tag" :class="stateTagClass(r.emgStateCode)">{{ label(EMG_STATE_LABEL, r.emgStateCode) }}</span></td>
              <td><button type="button" class="btn-link" @click="detailId = r.emgId">查看</button></td>
            </tr>
          </tbody>
        </table>
        <PagerBar v-model:page="page" :total-pages="totalPages" />
      </div>
      <div v-else class="empty">查無資料，請換個條件再試一次</div>
    </template>

    <EmergencyDetailModal v-if="detailId" :emg-id="detailId" @close="detailId = ''" />
    <ToastHost />
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import EmergencyDetailModal from '../components/EmergencyDetailModal.vue'
import PagerBar from '../components/PagerBar.vue'
import ToastHost from '../components/ToastHost.vue'
import { errorMessage } from '../api/errors'
import { listEmergencies } from '../api/emergency'
import { usePaging } from '../composables/usePaging'
import { useSession } from '../composables/useSession'
import { useToast } from '../composables/useToast'
import type { Emg, EmgQuery } from '../types/emergency'
import { daysAgo, fmtDate, toMinute } from '../utils/date'
import { EMG_STATE_FILTER, EMG_STATE_LABEL, label, stateTagClass } from '../utils/labels'

const { toast } = useToast()
const { can } = useSession()

const keyword = ref('')
const stateCode = ref('')
const dateRange = ref('all')
const dateFrom = ref('')
const dateTo = ref('')
const rows = ref<Emg[]>([])
const loaded = ref(false)
const loading = ref(false)
const detailId = ref('')
const { page, totalPages, pageRows } = usePaging(rows)

function query(): EmgQuery {
  const q: EmgQuery = { keyword: keyword.value.trim(), stateCode: stateCode.value }
  if (dateRange.value === 'custom') {
    q.dateFrom = dateFrom.value
    q.dateTo = dateTo.value
  } else if (dateRange.value !== 'all') {
    q.dateFrom = daysAgo(Number(dateRange.value))
    q.dateTo = fmtDate(new Date())
  }
  return q
}

async function load(): Promise<void> {
  const q = query()
  if (q.dateFrom && q.dateTo && q.dateFrom > q.dateTo) {
    toast('起日不可晚於迄日', 'amber')
    return
  }
  loading.value = true
  try {
    const data = await listEmergencies(q)
    if (!Array.isArray(data)) throw new Error('unexpected response')
    rows.value = data
    loaded.value = true
  } catch (e) {
    toast(errorMessage(e, '查詢失敗，請稍後再試'), 'amber')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.range { display: inline-flex; align-items: center; gap: 6px; }
.range input { width: 180px; }
a.btn { display: inline-flex; align-items: center; min-height: 48px; text-decoration: none; }
.own-only { margin: -4px 0 10px; }
</style>
