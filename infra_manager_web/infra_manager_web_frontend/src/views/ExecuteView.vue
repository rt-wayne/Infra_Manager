<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: 新增：填寫執行紀錄頁（S10 R3，施工計畫 ⑪ ⑬ ⑮），比照舊系統 views/apps/execute.ejs 的五、六兩段：
            五、作業執行檢核表——目前版次已展開就用 GET /apps/{id} 的 checklist；還沒展開（第一次填）改用
            GET /form-options 的 CHECK_LIST 選項畫列（序號 = 排序後的位置，與後端展開規則相同）；
            第 5～8 項名稱後接申請單作業步驟一～四的文字（同舊系統）
            執行人：系統使用者（只提供「填入我自己」，不開使用者查詢 API——⑪ 使用者裁示）或自由文字，至多擇一
            勾完成時填入當下時間、取消勾選清空；重存時把已存的完成時間原樣帶回（後端對沒帶時間的完成項補伺服器現在時間）
            六、實際執行紀錄——「暫存」一律不帶執行結果（停在執行中）；「完成並送治理審查」要選結果、先 confirm，成功回檢視頁
            下方「退回給申請人」：意見必填、先 confirm，成功回檢視頁
            沒有 canExecute（狀態已變、不是 idc_admin 或申請人）只顯示說明與回檢視頁連結
            失敗 toast 後端訊息；409／403／404 重新載入（isStale）；401 交給登入處理器
-->
<template>
  <main>
    <header class="hero">
      <div class="head">
        <h1><span class="id">{{ appId }}</span> 填寫執行紀錄</h1>
        <div v-if="app" class="meta">
          <StatusPill :code="app.statusCode" />
          <span class="title">{{ app.title ?? '' }}</span>
        </div>
      </div>
      <router-link class="link" :to="{ name: APP_VIEW_ROUTE, params: { id: appId } }">← 回檢視頁</router-link>
    </header>

    <p v-if="error" class="card error" role="alert">{{ error }}</p>
    <p v-else-if="!app" class="card muted">載入中…</p>
    <p v-else-if="!app.permissions.canExecute" class="card muted" role="status">
      這張申請單目前無法填寫執行紀錄（狀態已變更，或你不是機房管理員／申請人）。
      <router-link class="link" :to="{ name: APP_VIEW_ROUTE, params: { id: appId } }">回檢視頁查看最新狀態</router-link>
    </p>

    <template v-else>
      <section class="card">
        <h2>五、作業執行檢核表</h2>
        <p class="sub">勾選完成會自動填入當下時間，可再修改；執行人可「填入我自己」或直接輸入文字（例如廠商、門市 EDP）</p>
        <table class="ptable grid">
          <colgroup><col class="c-seq" /><col /><col class="c-at" /><col class="c-who" /></colgroup>
          <thead><tr><th>序</th><th>檢核項目</th><th>完成時間</th><th>執行人</th></tr></thead>
          <tbody>
            <tr v-for="r in rows" :key="r.seqNo">
              <td>{{ r.seqNo }}</td>
              <td>
                <label class="check">
                  <input type="checkbox" :checked="r.done" :disabled="busy" @change="toggleDone(r, $event)" />
                  <span>{{ r.name }}</span>
                </label>
              </td>
              <td>
                <input v-model="r.doneAt" type="datetime-local" :disabled="busy || !r.done" :aria-label="`第 ${r.seqNo} 項完成時間`" />
              </td>
              <td>
                <div v-if="r.userId" class="who">
                  <b>{{ r.userName || r.userId }}</b>
                  <button class="quiet small" type="button" :disabled="busy" @click="useText(r)">改填文字</button>
                </div>
                <div v-else class="who">
                  <input v-model="r.desc" type="text" maxlength="200" :disabled="busy" :aria-label="`第 ${r.seqNo} 項執行人`" />
                  <button class="quiet small" type="button" :disabled="busy || !myId" @click="useMe(r)">填入我自己</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </section>

      <section class="card form">
        <h2>六、實際執行紀錄</h2>
        <div class="two">
          <div>
            <label for="actual-start">實際開始時間</label>
            <input id="actual-start" v-model="form.actualStart" type="datetime-local" :disabled="busy" />
          </div>
          <div>
            <label for="actual-end">實際完成時間</label>
            <input id="actual-end" v-model="form.actualEnd" type="datetime-local" :disabled="busy" />
          </div>
        </div>
        <label for="result">執行結果 <span class="sub">（完成並送治理審查時必選；暫存不會儲存結果）</span></label>
        <select id="result" v-model="form.resultCode" :disabled="busy">
          <option value="">— 請選擇 —</option>
          <option v-for="o in resultOptions" :key="o.code" :value="o.code">{{ o.name }}</option>
        </select>

        <label class="check">
          <input type="checkbox" :checked="form.exception" :disabled="busy" @change="toggleFlag('exception', $event)" />
          <span>有例外／衍生事件</span>
        </label>
        <textarea v-if="form.exception" id="exception-desc" v-model="form.exceptionDesc" rows="2" maxlength="2000"
          :disabled="busy" aria-label="例外／衍生事件說明" placeholder="請說明發生了什麼事、如何處理"></textarea>

        <label class="check">
          <input type="checkbox" :checked="form.followUp" :disabled="busy" @change="toggleFlag('followUp', $event)" />
          <span>有後續追蹤事項</span>
        </label>
        <textarea v-if="form.followUp" id="follow-up-desc" v-model="form.followUpDesc" rows="2" maxlength="2000"
          :disabled="busy" aria-label="後續追蹤事項說明" placeholder="請說明要追蹤的事項與負責人"></textarea>

        <label for="exec-memo">執行備註</label>
        <textarea id="exec-memo" v-model="form.memo" rows="3" maxlength="2000" :disabled="busy"></textarea>

        <div class="actions">
          <button class="quiet" type="button" :disabled="busy" @click="save">暫存</button>
          <button class="go" type="button" :disabled="busy" @click="complete">✓ 完成並送治理審查</button>
        </div>
      </section>

      <section class="card form del" aria-label="退回給申請人">
        <h2>退回給申請人</h2>
        <p class="sub">無法依申請內容執行時（例如資訊不足、現場條件不符）退回，申請人需補件重送；已填的檢核表與執行紀錄會保留在這一版</p>
        <label for="reject-memo">退回意見</label>
        <textarea id="reject-memo" v-model="rejectMemo" rows="2" maxlength="2000" :disabled="busy"
          placeholder="請說明退回原因與要補的項目"></textarea>
        <div class="actions">
          <button class="danger" type="button" :disabled="busy" @click="reject">✗ 退回給申請人</button>
        </div>
      </section>
    </template>

    <ToastHost />
  </main>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StatusPill from '../components/StatusPill.vue'
import ToastHost from '../components/ToastHost.vue'
import { getApp, getFormOptions, rejectExecution, saveExecution } from '../api/apps'
import { errorMessage, isStale, isUnauthorized } from '../api/http'
import { useAuth } from '../composables/useAuth'
import { useToast } from '../composables/useToast'
import { APP_VIEW_ROUTE } from '../router/names'
import type { AppDetail, ExecutionRequest, FormOption } from '../types/app'
import { toLocalInput } from '../utils/draftForm'

const route = useRoute()
const router = useRouter()
const { toast } = useToast()
const { me } = useAuth()

const appId = computed(() => {
  const id = route.params.id
  return typeof id === 'string' ? id : ''
})

const myId = computed(() => me.value.userId ?? '')

const app = ref<AppDetail | null>(null)
const options = ref<FormOption[]>([])
const error = ref('')
/** 暫存／送審／退回進行中：鎖住全部輸入與按鈕 */
const busy = ref(false)

/** 檢核表一列的畫面狀態；userId 有值＝系統使用者（userName 只供顯示），否則看 desc（自由文字） */
interface Row {
  seqNo: number
  name: string
  done: boolean
  /** yyyy-MM-ddTHH:mm；未完成為空字串 */
  doneAt: string
  userId: string | null
  userName: string
  desc: string
}

const rows = ref<Row[]>([])

const form = reactive({
  actualStart: '',
  actualEnd: '',
  resultCode: '',
  exception: false,
  exceptionDesc: '',
  followUp: false,
  followUpDesc: '',
  memo: ''
})

const rejectMemo = ref('')

function bySort(a: FormOption, b: FormOption): number {
  return (a.sortNo ?? 0) - (b.sortNo ?? 0)
}

const resultOptions = computed(() => options.value.filter(o => o.groupCode === 'EXEC_RESULT').sort(bySort))

/** 第 5～8 項名稱後接作業步驟一～四（同舊系統）；步驟沒填就只顯示項目名稱 */
function rowName(a: AppDetail, seqNo: number, name: string): string {
  if (seqNo < 5 || seqNo > 8) return name
  const text = a.planSteps[seqNo - 5]?.text?.trim()
  return text ? `${name}：${text}` : name
}

/** 目前版次已展開就照存的列；還沒展開用啟用中的 CHECK_LIST 選項（排序後的位置 = 序號，與後端展開規則一致） */
function buildRows(a: AppDetail, opts: FormOption[]): Row[] {
  if (a.checklist.length) {
    return a.checklist.map((c, i) => {
      const seqNo = c.seqNo ?? i + 1
      return {
        seqNo,
        name: rowName(a, seqNo, c.name ?? ''),
        done: c.done,
        doneAt: c.done ? toLocalInput(c.doneAt) : '',
        userId: c.userId,
        userName: c.userId ? c.executor ?? '' : '',
        desc: c.userId ? '' : c.executorDesc ?? ''
      }
    })
  }
  return opts
    .filter(o => o.groupCode === 'CHECK_LIST')
    .sort(bySort)
    .map((o, i) => ({ seqNo: i + 1, name: rowName(a, i + 1, o.name), done: false, doneAt: '', userId: null, userName: '', desc: '' }))
}

function fillForm(a: AppDetail): void {
  const ex = a.execution
  form.actualStart = toLocalInput(ex?.actualStart)
  form.actualEnd = toLocalInput(ex?.actualEnd)
  form.resultCode = ex?.resultCode ?? ''
  form.exception = ex?.exception ?? false
  form.exceptionDesc = ex?.exceptionDesc ?? ''
  form.followUp = ex?.followUp ?? false
  form.followUpDesc = ex?.followUpDesc ?? ''
  form.memo = ex?.memo ?? ''
}

function pad(n: number): string {
  return String(n).padStart(2, '0')
}

/** 瀏覽器本機時間的 yyyy-MM-ddTHH:mm（使用者都在台灣，與後端台灣時間一致） */
function nowLocal(): string {
  const d = new Date()
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function toggleDone(r: Row, ev: Event): void {
  const checked = (ev.target as HTMLInputElement).checked
  r.done = checked
  r.doneAt = checked ? r.doneAt || nowLocal() : ''
}

function useMe(r: Row): void {
  if (!myId.value) return
  r.userId = myId.value
  r.userName = me.value.userName ?? ''
  r.desc = ''
}

function useText(r: Row): void {
  r.userId = null
  r.userName = ''
}

/** 取消勾選時後端會把說明存成 null：已有說明就先確認，確認後清掉，免得使用者以為還存著 */
function toggleFlag(key: 'exception' | 'followUp', ev: Event): void {
  const box = ev.target as HTMLInputElement
  const descKey = key === 'exception' ? 'exceptionDesc' : 'followUpDesc'
  if (!box.checked && form[descKey].trim() !== '' && !window.confirm('取消勾選後，已填的說明不會儲存，確定要取消？')) {
    box.checked = true
    return
  }
  form[key] = box.checked
  if (!box.checked) form[descKey] = ''
}

function blankToNull(v: string): string | null {
  const t = v.trim()
  return t === '' ? null : t
}

function buildRequest(rowVerNo: number, resultCode: string | null): ExecutionRequest {
  return {
    rowVerNo,
    checklist: rows.value.map(r => ({
      seqNo: r.seqNo,
      done: r.done,
      doneAt: r.done ? blankToNull(r.doneAt) : null,
      userId: r.userId,
      executorDesc: r.userId ? null : blankToNull(r.desc)
    })),
    actualStart: blankToNull(form.actualStart),
    actualEnd: blankToNull(form.actualEnd),
    resultCode,
    exception: form.exception,
    exceptionDesc: form.exception ? blankToNull(form.exceptionDesc) : null,
    followUp: form.followUp,
    followUpDesc: form.followUp ? blankToNull(form.followUpDesc) : null,
    memo: blankToNull(form.memo)
  }
}

/** 三個動作共用：失敗 toast 後端訊息，資料已過期（409／403／404）重新載入讓使用者看到最新狀態 */
async function run(call: (rowVerNo: number) => Promise<unknown>, onOk: () => Promise<void>): Promise<void> {
  const a = app.value
  if (!a || busy.value) return
  busy.value = true
  try {
    await call(a.rowVerNo)
    await onOk()
  } catch (e: unknown) {
    if (isUnauthorized(e)) return
    toast(errorMessage(e), 'red')
    if (isStale(e)) await load(appId.value, true)
  } finally {
    busy.value = false
  }
}

async function save(): Promise<void> {
  await run(v => saveExecution(appId.value, buildRequest(v, null)), async () => {
    toast('已暫存', 'teal')
    await load(appId.value, true)
  })
}

async function complete(): Promise<void> {
  if (!form.resultCode) {
    toast('完成並送治理審查前請選擇執行結果', 'red')
    return
  }
  if (!window.confirm('確定執行完成並送治理審查？送出後無法再修改執行紀錄，要等資訊治理人員審核。')) return
  const code = form.resultCode
  await run(v => saveExecution(appId.value, buildRequest(v, code)), async () => {
    toast('已送治理審查', 'teal')
    await router.push({ name: APP_VIEW_ROUTE, params: { id: appId.value } })
  })
}

async function reject(): Promise<void> {
  const m = rejectMemo.value.trim()
  if (!m) {
    toast('退回請填寫意見，讓申請人知道要補什麼', 'red')
    return
  }
  if (!window.confirm('確定退回給申請人？退回後申請人需補件重送，整張單重新簽核。')) return
  await run(v => rejectExecution(appId.value, { rowVerNo: v, memo: m }), async () => {
    toast('已退回給申請人', 'teal')
    await router.push({ name: APP_VIEW_ROUTE, params: { id: appId.value } })
  })
}

/** 每次載入遞增；回應回來時編號不是最新就丟掉，避免慢回來的舊單蓋掉新單 */
let seq = 0

/** keep：動作成功或資料過期後重新載入，保留畫面不閃「載入中」；表單一律換成後端最新值 */
async function load(id: string, keep = false): Promise<void> {
  const mine = ++seq
  if (!keep) {
    app.value = null
    rejectMemo.value = ''
  }
  error.value = ''
  try {
    const [data, opts] = await Promise.all([getApp(id), getFormOptions()])
    if (mine !== seq) return
    options.value = opts.options
    rows.value = buildRows(data, opts.options)
    fillForm(data)
    app.value = data
  } catch (e: unknown) {
    if (mine !== seq) return
    if (isUnauthorized(e)) {
      error.value = '尚未登入'
      return
    }
    error.value = errorMessage(e)
    toast(error.value, 'red')
  }
}

watch(appId, id => void load(id), { immediate: true })
</script>

<style scoped>
main { width: 100%; max-width: 1180px; margin: 0 auto; padding: 24px 20px; }

.hero { margin-bottom: 16px; display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; }
.head { min-width: 0; }
.hero h1 { margin: 0; font-size: 26px; color: var(--navy); overflow-wrap: anywhere; }
.hero .id { font-size: 18px; color: var(--gray); margin-right: 6px; }
.meta { display: flex; flex-wrap: wrap; align-items: center; gap: 6px 14px; margin-top: 6px; font-size: 16px; min-width: 0; }
.title { overflow-wrap: anywhere; }
.link { color: var(--blue); font-size: 17px; white-space: nowrap; }

.card { background: #fff; border: 1px solid var(--line); border-radius: 10px; padding: 16px 20px; margin: 0 0 16px; overflow-wrap: anywhere; }
.card h2 { margin: 0 0 10px; font-size: 21px; color: var(--teal-dark); }
.del { border-color: var(--red); }

.form label { display: block; font-weight: 700; margin: 12px 0 4px; }
.form label.check { font-weight: 400; }
.form textarea { width: 100%; box-sizing: border-box; border: 1px solid var(--line); border-radius: 7px; padding: 8px 10px; font: inherit; font-size: 17px; resize: vertical; }
.form select, .form input[type='datetime-local'] { box-sizing: border-box; border: 1px solid var(--line); border-radius: 7px; padding: 6px 10px; font: inherit; font-size: 17px; max-width: 100%; }
.two { display: flex; flex-wrap: wrap; gap: 0 24px; }
textarea:disabled, input:disabled, select:disabled { background: var(--bg); }

.check { display: flex; align-items: flex-start; gap: 8px; cursor: pointer; }
.check input { margin-top: 5px; flex: none; }

.actions { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 14px; }
.quiet { background: #fff; border: 1px solid var(--line); color: var(--dark); border-radius: 7px; padding: 8px 18px; font-size: 17px; cursor: pointer; }
.quiet.small { min-height: 34px; padding: 2px 10px; font-size: 15px; white-space: nowrap; }
.go { background: var(--teal); border: 1px solid var(--teal); color: #fff; border-radius: 7px; padding: 8px 18px; font-size: 17px; cursor: pointer; }
.danger { background: #fff; border: 1px solid var(--red); color: var(--red); border-radius: 7px; padding: 8px 18px; font-size: 17px; cursor: pointer; }
.quiet:disabled, .go:disabled, .danger:disabled { opacity: .55; cursor: default; }

.muted { color: var(--gray); margin: 4px 0; }
.error { color: var(--red); font-weight: 700; }
.sub { color: var(--gray); font-size: 14px; font-weight: 400; }

.ptable { width: 100%; table-layout: fixed; border-collapse: collapse; font-size: 16px; }
.ptable th, .ptable td { border: 1px solid var(--line); padding: 6px 8px; vertical-align: top; text-align: left; overflow-wrap: anywhere; }
.ptable.grid thead th { background: var(--bg); font-size: 15px; }
.ptable input[type='datetime-local'] { width: 100%; box-sizing: border-box; border: 1px solid var(--line); border-radius: 6px; padding: 3px 4px; font: inherit; font-size: 15px; }
.c-seq { width: 48px; }
.c-at { width: 200px; }
.c-who { width: 250px; }
.who { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; }
.who input { flex: 1 1 120px; min-width: 0; box-sizing: border-box; border: 1px solid var(--line); border-radius: 6px; padding: 3px 6px; font: inherit; font-size: 15px; }
</style>
