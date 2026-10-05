<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-05
  變更說明: 新增：範例查詢頁（規格 v4），比照 store_web_barcode 的版面
            搜尋列＋表格＋每頁 20 筆分頁＋空狀態＋toast；查詢前擋空輸入
            查詢失敗只出 toast「查詢失敗，請稍後再試」並保留原畫面，不得顯示成「查無資料」
            後端回應假設為陣列；欄位定義在 columns，換成自己的欄位即可
            jdk25 階段 3：改 <script setup lang="ts">；邏輯與版面不變
-->
<template>
  <main>
    <!-- ── 搜尋列 ── -->
    <form class="search-bar" @submit.prevent="doSearch">
      <div class="fld">
        <label for="kw">關鍵字</label>
        <input id="kw" v-model="keyword" type="text" autocomplete="off" />
      </div>
      <button class="go" type="submit" :disabled="loading">{{ loading ? '查詢中…' : '搜尋' }}</button>
      <button class="clear" type="button" :disabled="loading" @click="clearAll">清除</button>
    </form>

    <!-- ── 結果 ── -->
    <template v-if="searched">
      <div v-if="rows.length > 0">
        <div class="res-head">
          <span class="res-total">共 {{ rows.length }} 筆</span>
          <span class="res-page">第 {{ page }} / {{ totalPages }} 頁</span>
        </div>
        <table class="tbl">
          <thead>
            <tr>
              <th v-for="c in columns" :key="c.key">{{ c.label }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(r, i) in pageRows" :key="i">
              <td v-for="c in columns" :key="c.key">{{ r[c.key] }}</td>
            </tr>
          </tbody>
        </table>
        <div v-if="totalPages > 1" class="pager">
          <button class="pg-btn" type="button" :disabled="page <= 1" @click="page--">上一頁</button>
          <span class="pg-info">{{ page }} / {{ totalPages }}</span>
          <button class="pg-btn" type="button" :disabled="page >= totalPages" @click="page++">下一頁</button>
        </div>
      </div>
      <div v-else class="empty">
        <b>查無資料</b>
        請換個條件再試一次
      </div>
    </template>
    <p v-else class="hint">輸入關鍵字後按「搜尋」</p>

    <!-- ── Toast 通知 ── -->
    <div id="toasts">
      <div v-for="t in toasts" :key="t.id" class="toast" :class="t.cls">{{ t.msg }}</div>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { search } from '../api/example'
import { useToast } from '../composables/useToast'
import type { ExampleRow } from '../types/example'

const PAGE_SIZE = 20

/* 表格欄位：key 為後端回應的屬性名，label 為表頭；換成自己的欄位 */
interface Column {
  key: string
  label: string
}

const columns: Column[] = [
  { key: 'code', label: '代碼' },
  { key: 'name', label: '名稱' }
]

const { toasts, toast } = useToast()
const keyword = ref('')
const rows = ref<ExampleRow[]>([])
const page = ref(1)
const loading = ref(false)
const searched = ref(false)

const totalPages = computed(() => Math.max(1, Math.ceil(rows.value.length / PAGE_SIZE)))
const pageRows = computed(() => rows.value.slice((page.value - 1) * PAGE_SIZE, page.value * PAGE_SIZE))

async function doSearch(): Promise<void> {
  const kw = keyword.value.trim()
  /* 查詢前擋空輸入：不打 API、不動畫面，只出提示 */
  if (!kw) {
    toast('請輸入關鍵字', 'amber')
    return
  }
  loading.value = true
  try {
    const data = await search({ keyword: kw })
    if (!Array.isArray(data)) {
      throw new Error('unexpected response')
    }
    rows.value = data
    page.value = 1
    searched.value = true
  } catch {
    /* 失敗只出 toast，保留原畫面；不得把失敗顯示成「查無資料」 */
    toast('查詢失敗，請稍後再試', 'amber')
  } finally {
    loading.value = false
  }
}

function clearAll(): void {
  keyword.value = ''
  rows.value = []
  page.value = 1
  searched.value = false
}
</script>

<style scoped>
/* 寬度由各專案決定：本範本不設 max-width */
main { width: 100%; padding: 20px 20px 24px; }

/* ── 搜尋列 ── */
.search-bar { display: flex; gap: 12px; align-items: flex-end; flex-wrap: wrap; background: #fff;
  border: 1px solid var(--line); border-radius: 10px; padding: 12px 16px; margin-bottom: 14px; }
.fld { display: flex; flex-direction: column; gap: 4px; }
.fld label { color: var(--gray); }
.fld input { border: 1px solid var(--line); border-radius: 7px; padding: 10px 14px;
  color: var(--dark); width: 340px; max-width: 100%; min-height: 48px; }
.fld input:focus { outline: 2px solid var(--teal); outline-offset: 1px; border-color: var(--teal); }
/* min-width 吸收「搜尋」↔「查詢中…」的文字寬度差 */
.go { background: var(--teal); border: none; color: #fff; font-weight: 700; border-radius: 7px;
  padding: 12px 24px; font-size: 19px; cursor: pointer; min-width: 140px; }
.go:hover:not(:disabled) { filter: brightness(1.08); }
.go:disabled { opacity: .55; cursor: default; }
.clear { background: #fff; border: 1px solid var(--line); border-radius: 7px; padding: 12px 20px;
  font-size: 19px; cursor: pointer; color: var(--gray); }
.clear:hover:not(:disabled) { background: var(--teal-bg); color: var(--teal-dark); }

.hint { color: var(--gray); font-size: 17px; margin-top: 16px; font-style: italic; }

/* ── 結果 ── */
.res-head { display: flex; align-items: baseline; gap: 10px; margin: 18px 0 8px; }
.res-total { font-size: 21px; font-weight: 700; }
.res-page { margin-left: auto; font-size: 17px; color: var(--gray); font-variant-numeric: tabular-nums; }
.tbl { width: 100%; border-collapse: collapse; background: #fff; border: 1px solid var(--line); }
.tbl th { background: #eef2f7; font-size: 18px; padding: 12px 14px; text-align: left; white-space: nowrap; }
.tbl td { padding: 12px 14px; font-size: 19px; line-height: 1.5; border-top: 1px solid var(--line);
  overflow-wrap: anywhere; }
.tbl tbody tr:hover { background: #f8fafc; }

/* ── 分頁 ── */
.pager { display: flex; align-items: center; justify-content: center; gap: 14px; margin-top: 16px; }
.pg-btn { background: #fff; border: 1px solid var(--line); border-radius: 8px; padding: 14px 30px;
  font-size: 19px; cursor: pointer; color: var(--dark); min-width: 150px; }
.pg-btn:hover:not(:disabled) { background: var(--teal-bg); color: var(--teal-dark); }
.pg-btn:disabled { opacity: .4; cursor: default; }
.pg-info { font-size: 19px; color: var(--gray); font-variant-numeric: tabular-nums; }

/* ── 查無資料 ── */
.empty { text-align: center; color: var(--gray); padding: 56px 0 40px; }
.empty b { display: block; font-size: 24px; color: var(--dark); margin-bottom: 10px; }

/* ── Toast ── */
#toasts { position: fixed; top: 18px; right: 18px; display: flex; flex-direction: column; gap: 10px; z-index: 50; }
.toast { background: var(--navy); color: #fff; padding: 16px 22px; border-radius: 9px; font-size: 19px;
  box-shadow: 0 8px 20px rgba(0,0,0,.25); max-width: 460px; line-height: 1.5; }
.toast.teal { background: var(--teal-dark); }
.toast.amber { background: #92400e; }
.toast.red { background: var(--red); }
</style>
