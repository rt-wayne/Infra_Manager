<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: 新增：巡檢紀錄（開發文件 spec_im_acct_insp.md 第 3.8、6 節；GET /inspections，需 INSP）
            篩選：關鍵字（單號、巡檢人員）、狀態（全部／正常結案／有異常／異常已排除）、
            日期（全部／近 7／30（預設）／90 天／自訂區間）→ 換算 dateFrom、dateTo 送後端
            查看彈窗列異常項目並可標記已處理；查詢失敗只 toast，不顯示成查無資料
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: v2.0（開發文件 spec_im_acct_insp_v2.0.md 第 4.14、8.3 節）：狀態用後端計算值 inspStateCode；
            巡檢人員依 4.14 顯示快照名稱與代理身分；異常數旁列未處理數；「新增巡檢」只對有 INSP_CREATE 者顯示
-->
<template>
  <main>
    <div class="toolbar">
      <form class="filters" @submit.prevent="load">
        <input v-model="keyword" type="text" placeholder="搜尋巡檢單號 / 巡檢人員" aria-label="關鍵字" autocomplete="off" />
        <button type="submit" class="btn btn-primary" :disabled="loading">{{ loading ? '查詢中…' : '搜尋' }}</button>
        <select v-model="stateCode" aria-label="狀態" @change="load">
          <option value="">全部狀態</option>
          <option value="NORMAL">正常結案</option>
          <option value="EXCPT">有異常</option>
          <option value="FIXED">異常已排除</option>
        </select>
        <select v-model="dateRange" aria-label="日期區間" @change="load">
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
      <RouterLink v-if="can('INSP_CREATE')" class="btn btn-primary" to="/inspections/new">＋ 新增巡檢</RouterLink>
    </div>

    <template v-if="loaded">
      <div v-if="rows.length > 0">
        <p class="res-head">共 {{ rows.length }} 筆，第 {{ page }} / {{ totalPages }} 頁</p>
        <table class="tbl">
          <colgroup>
            <col style="width: 20%" />
            <col style="width: 20%" />
            <col />
            <col style="width: 13%" />
            <col style="width: 16%" />
            <col style="width: 11%" />
          </colgroup>
          <thead>
            <tr><th>巡檢單號</th><th>巡檢時間</th><th>巡檢人員</th><th>異常數</th><th>狀態</th><th>操作</th></tr>
          </thead>
          <tbody>
            <tr v-for="r in pageRows" :key="r.inspId">
              <td>{{ r.inspId }}</td>
              <td>{{ r.inspDate }}</td>
              <td class="inspector">{{ personText(r.inspByName, r.actAsName) }}</td>
              <td>
                <span class="tag" :class="r.unfixedCnt > 0 ? 'tag-warn' : 'tag-ok'">{{ r.excptCnt }} 項</span>
                <div v-if="r.unfixedCnt > 0" class="hint">未處理 {{ r.unfixedCnt }} 項</div>
              </td>
              <td><span class="tag" :class="stateTagClass(r.inspStateCode)">{{ label(INSP_STATE_LABEL, r.inspStateCode) }}</span></td>
              <td><button type="button" class="btn-link" @click="detailId = r.inspId">查看</button></td>
            </tr>
          </tbody>
        </table>
        <PagerBar v-model:page="page" :total-pages="totalPages" />
      </div>
      <div v-else class="empty">查無資料，請換個條件再試一次</div>
    </template>

    <InspectionDetailModal v-if="detailId" :insp-id="detailId" @close="detailId = ''" @changed="load" />
    <ToastHost />
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import InspectionDetailModal from '../components/InspectionDetailModal.vue'
import PagerBar from '../components/PagerBar.vue'
import ToastHost from '../components/ToastHost.vue'
import { errorMessage } from '../api/errors'
import { listInspections } from '../api/inspection'
import { usePaging } from '../composables/usePaging'
import { useSession } from '../composables/useSession'
import { useToast } from '../composables/useToast'
import type { Inspection, InspQuery } from '../types/inspection'
import { daysAgo, fmtDate } from '../utils/date'
import { INSP_STATE_LABEL, label, personText, stateTagClass } from '../utils/labels'

const { toast } = useToast()
const { can } = useSession()

const keyword = ref('')
const stateCode = ref('')
const dateRange = ref('30')
const dateFrom = ref('')
const dateTo = ref('')
const rows = ref<Inspection[]>([])
const loaded = ref(false)
const loading = ref(false)
const detailId = ref('')
const { page, totalPages, pageRows } = usePaging(rows)

function query(): InspQuery {
  const q: InspQuery = { keyword: keyword.value.trim(), stateCode: stateCode.value }
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
    const data = await listInspections(q)
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
</style>
