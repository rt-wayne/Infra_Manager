<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-06
  變更說明: 新增：申請單列表頁（S4 回合二，唯讀）。GET /apps，每頁 20 筆，待我簽核的排最前並加標記
            篩選：狀態／優先／來源／只看待我簽核／關鍵字（≤100 字）／建立日期起迄；起日晚於迄日直接提示、不打 API
            兩個日期都不填時後端只回近 90 天（畫面有提示）；「清除」回到預設條件重查
            失敗 → toast 後端訊息並顯示錯誤文字，不顯示成「沒有符合條件」；401 由登入處理器導頁，本頁不另出 toast
            單號暫為純文字，檢視頁（/apps/:id）在回合三補上連結；AI 審查欄位待第 17 項
-->
<template>
  <main>
    <header class="hero">
      <h1>申請單列表</h1>
      <router-link class="link" to="/">回首頁</router-link>
    </header>

    <form class="card filters" @submit.prevent="search">
      <label>狀態
        <select v-model="filter.status" name="status">
          <option value="">全部</option>
          <option v-for="(name, code) in STATUS_LABELS" :key="code" :value="code">{{ name }}</option>
        </select>
      </label>
      <label>優先
        <select v-model="filter.priority" name="priority">
          <option value="">全部</option>
          <option v-for="p in PRIORITIES" :key="p" :value="p">{{ p }}</option>
        </select>
      </label>
      <label>來源
        <select v-model="filter.source" name="source">
          <option value="">全部</option>
          <option v-for="(name, code) in SOURCE_LABELS" :key="code" :value="code">{{ name }}</option>
        </select>
      </label>
      <label class="wide">關鍵字
        <input v-model="filter.q" name="q" type="text" maxlength="100" placeholder="單號、標題、作業主旨、申請人" />
      </label>
      <label>建立日期起
        <input v-model="filter.from" name="from" type="date" />
      </label>
      <label>建立日期迄
        <input v-model="filter.to" name="to" type="date" />
      </label>
      <label class="check">
        <input v-model="filter.mine" name="mine" type="checkbox" />
        只看待我簽核<span v-if="data">（{{ data.mineCount }}）</span>
      </label>
      <p class="hint">建立日期兩個都不填時，只顯示近 90 天的申請單</p>
      <div class="actions">
        <button class="go" type="submit" :disabled="loading">{{ loading ? '查詢中…' : '查詢' }}</button>
        <button class="quiet" type="button" :disabled="loading" @click="clear">清除</button>
      </div>
    </form>

    <section class="card">
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <p v-else-if="!data" class="muted">載入中…</p>
      <p v-else-if="data.items.length === 0" class="muted">沒有符合條件的申請單</p>
      <template v-else>
        <table class="list">
          <colgroup>
            <col class="c-id" /><col class="c-prio" /><col /><col class="c-who" />
            <col class="c-status" /><col class="c-step" /><col class="c-time" />
          </colgroup>
          <thead>
            <tr>
              <th>單號</th><th>優先</th><th>標題／作業主旨</th><th>申請人</th>
              <th>狀態</th><th>目前關卡</th><th>建立時間</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in data.items" :key="row.appId" :class="{ mine: row.mine }">
              <td>
                <span class="id">{{ row.appId }}</span>
                <span v-if="row.mine" class="badge">待我簽核</span>
              </td>
              <td>
                <span v-if="row.prioCode" class="prio" :style="prioStyle(row.prioColor)">{{ row.prioCode }}</span>
                <span v-else class="muted">—</span>
              </td>
              <td>
                <div class="title">{{ row.title || '（無標題）' }}</div>
                <div v-if="row.workSubject" class="sub">{{ row.workSubject }}</div>
              </td>
              <td>
                <div>{{ row.applicantName || '—' }}</div>
                <div v-if="row.applyDeptName" class="sub">{{ row.applyDeptName }}</div>
              </td>
              <td>
                <StatusPill :code="row.statusCode" />
                <div v-if="row.sourceCode === 'IMPORTED'" class="sub">紙本匯入</div>
              </td>
              <td>
                <div>{{ row.currentStep || '—' }}</div>
                <div v-if="row.currentApprover" class="sub">{{ row.currentApprover }}</div>
              </td>
              <td class="time">{{ row.createdAt || '—' }}</td>
            </tr>
          </tbody>
        </table>
        <AppPager :page="data.page" :size="data.size" :total="data.total" :busy="loading" @change="load" />
      </template>
    </section>

    <ToastHost />
  </main>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import AppPager from '../components/AppPager.vue'
import StatusPill from '../components/StatusPill.vue'
import ToastHost from '../components/ToastHost.vue'
import { listApps } from '../api/apps'
import { errorMessage, isUnauthorized } from '../api/http'
import { useToast } from '../composables/useToast'
import { PRIORITIES, SOURCE_LABELS, STATUS_LABELS, type AppListFilter, type AppListResponse } from '../types/app'

const { toast } = useToast()

function emptyFilter(): AppListFilter {
  return { status: '', priority: '', source: '', mine: false, q: '', from: '', to: '' }
}

const filter = reactive<AppListFilter>(emptyFilter())
/** 按「查詢」時的條件；換頁沿用這份，避免改了欄位沒按查詢就被換頁帶出去 */
let applied: AppListFilter = emptyFilter()

const data = ref<AppListResponse | null>(null)
const loading = ref(false)
const error = ref('')

const HEX = /^#[0-9a-fA-F]{6}$/

function prioStyle(color: string | null): Record<string, string> {
  return color && HEX.test(color) ? { backgroundColor: color, color: '#fff' } : {}
}

async function load(page: number): Promise<void> {
  loading.value = true
  try {
    data.value = await listApps(applied, page)
    error.value = ''
  } catch (e: unknown) {
    data.value = null
    if (isUnauthorized(e)) {
      error.value = '尚未登入'
      return
    }
    error.value = errorMessage(e)
    toast(error.value, 'red')
  } finally {
    loading.value = false
  }
}

function search(): void {
  if (filter.from && filter.to && filter.from > filter.to) {
    toast('起日不得晚於迄日', 'amber')
    return
  }
  applied = { ...filter }
  void load(1)
}

function clear(): void {
  Object.assign(filter, emptyFilter())
  search()
}

onMounted(() => load(1))
</script>

<style scoped>
main { width: 100%; max-width: 1280px; margin: 0 auto; padding: 24px 20px; }

.hero { margin-bottom: 16px; display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.hero h1 { margin: 0; font-size: 28px; color: var(--navy); }
.link { color: var(--blue); font-size: 17px; }

.card { background: #fff; border: 1px solid var(--line); border-radius: 10px; padding: 16px 20px; margin-bottom: 16px; }

.filters { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 12px 16px; align-items: end; }
.filters label { display: flex; flex-direction: column; gap: 4px; color: var(--gray); }
.filters .wide { grid-column: span 2; }
.filters select, .filters input[type="text"], .filters input[type="date"] {
  width: 100%; min-height: 44px; padding: 6px 10px; border: 1px solid var(--line); border-radius: 7px;
  font-size: 18px; color: var(--dark); background: #fff; }
.filters .check { flex-direction: row; align-items: center; gap: 8px; color: var(--dark); min-height: 44px; }
.filters .check input { width: 22px; height: 22px; }
.hint { grid-column: 1 / -1; margin: 0; color: var(--gray); font-size: 15px; }
.actions { grid-column: 1 / -1; display: flex; gap: 12px; }

.go { background: var(--teal); border: none; color: #fff; font-weight: 700; border-radius: 7px;
  padding: 10px 24px; font-size: 19px; cursor: pointer; min-width: 140px; }
.go:disabled { opacity: .55; cursor: default; }
.quiet { background: #fff; border: 1px solid var(--line); color: var(--dark); border-radius: 7px; padding: 8px 18px; font-size: 17px; cursor: pointer; }
.quiet:disabled { opacity: .55; cursor: default; }

.muted { color: var(--gray); margin: 8px 0; }
.error { color: var(--red); font-weight: 700; margin: 8px 0; }

.list { width: 100%; table-layout: fixed; border-collapse: collapse; font-size: 16px; }
.list th { text-align: left; color: var(--gray); font-weight: 700; font-size: 15px; padding: 8px 6px; border-bottom: 2px solid var(--line); }
.list td { padding: 8px 6px; border-bottom: 1px solid var(--line); vertical-align: top; overflow-wrap: anywhere; }
.list tr.mine { background: var(--teal-bg); }
.c-id { width: 150px; }
.c-prio { width: 56px; }
.c-who { width: 130px; }
.c-status { width: 112px; }
.c-step { width: 150px; }
.c-time { width: 104px; }
.id { font-weight: 700; color: var(--navy); }
.badge { display: inline-block; margin-top: 2px; padding: 0 8px; border-radius: 999px; background: var(--teal); color: #fff; font-size: 13px; font-weight: 700; }
.prio { display: inline-block; min-width: 36px; text-align: center; padding: 0 6px; border-radius: 6px; background: var(--line); font-weight: 700; font-size: 15px; }
.title { font-weight: 700; }
.sub { color: var(--gray); font-size: 14px; }
.time { font-size: 14px; }
</style>
