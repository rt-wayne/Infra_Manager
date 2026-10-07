<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: 新增：申請單新增／編輯草稿表單（S6 回合四）。/apps/new 與 /apps/:id/edit 共用，區塊順序比照舊系統 views/apps/new.ejs：
            一、基本資料 二、事件分級 三、作業類別 四、異動作業內容，再接附件
            - 選項來自 GET /form-options（只有啟用的）；編輯時由 GET /apps/{id} 還原，已停用的選項自動拿掉（否則後端回「選項不正確」存不了）
            - 申請人姓名鎖定登入者（施工計畫假設 #6）；只有申請人能編輯（⑤A，依 permissions.canEditDraft）
            - 設備位置只做「不適用＋原因」或留空（⑦A），機房盤點選擇器待 S13
            - 存檔：新增 POST → 取得單號後網址換成編輯頁（之後再存走 PUT）；編輯 PUT 帶 rowVerNo（⑥A）；
              存檔成功後把待上傳附件逐檔上傳（一次一檔、顯示進度、單次 timeout 600 秒），全部成功才回檢視頁
            - 附件選檔時預檢副檔名／空檔／單檔大小／總檔數（本文送一半被 413 擋下時瀏覽器只會顯示連線錯誤）；拖放與貼上截圖留細節調整期
            - 400：後端有帶 field 就標紅該欄並捲過去（④B：不擋字數、CLOB 不用 maxlength）；409：提示並提供「重新載入」
            - 401 由登入處理器導頁，本頁不另出 toast
            S9 R3（Claude Opus 5.5，2026-10-07）：第三種模式「補件重送」（/apps/:id/resubmit，依路由名稱判斷）：
            - 只限退件單的申請人（permissions.canResubmit）；上方顯示退件資訊（退件那一關的意見，或執行／治理退回事件）
            - 多一個「補件說明」欄（選填、上限 2000 字由後端擋）；按鈕「補件並送審」先 confirm
            - 順序「先上傳附件、再補件」（裁示 ⑤A：退件單申請人可上傳；補件後就是審核中、不能再傳），
              任一檔失敗就不送補件；409 或 403 比照編輯草稿顯示「重新載入」按鈕（不自動重載，免得洗掉剛改的內容）
            S5 R2（Claude Opus 5.5，2026-10-07）：一～四區塊原樣抽成 components/DraftFields.vue（kind="app"，與範本編輯頁共用），
            畫面與行為不變；keepActive、MAX_ROWS 移到 utils/draftForm
            S5 R3（Claude Opus 5.5，2026-10-07）：新增模式最上方加「套用範本」下拉（同舊系統位置），也接受 ?template=<id>：
            - 就地填入、不重載頁面（舊系統整頁重載會丟掉已填內容）；改過內容先 confirm；申請人聯絡資料與預定時間保留
            - 已停用的選項自動拿掉（同編輯還原）；範本清單載入失敗只顯示一行提示、不擋填寫
            - 第一次存檔 POST 帶 templateId，後端同交易累計套用次數；之後存檔走 PUT 不再累計
-->
<template>
  <main ref="rootEl">
    <header class="hero">
      <div class="head">
        <h1>{{ TITLES[mode] }} <span v-if="savedId" class="id">{{ savedId }}</span></h1>
        <p class="sub">{{ isResubmit ? '改好內容後按「補件並送審」，會成為新的版次並重新跑簽核' : '存草稿只要求標題與事件分級，其餘欄位送審時才檢查' }}</p>
      </div>
      <router-link class="link" :to="backTo">{{ savedId ? '← 回申請單' : '← 回列表' }}</router-link>
    </header>

    <p v-if="loadError" class="card error" role="alert">{{ loadError }}</p>
    <p v-else-if="loading" class="card muted">載入中…</p>

    <form v-else class="paper" novalidate @submit.prevent="save">
      <section v-if="isResubmit" class="card reject" aria-label="退件資訊">
        <h2>退件資訊</h2>
        <template v-if="reject">
          <p class="reject-who">{{ reject.label }}<span v-if="reject.by">・{{ reject.by }}</span><span v-if="reject.at" class="sub">・{{ reject.at }}</span></p>
          <p class="reject-memo">{{ reject.memo || '（沒有填寫意見）' }}</p>
        </template>
        <p v-else class="muted">（找不到退件紀錄）</p>
      </section>

      <section v-if="mode === 'new' && (templates.length || tplListError)" class="card tpl" aria-label="套用範本">
        <label for="f-tpl">套用範本 <span class="sub">（選填：選取後自動填入欄位，申請人聯絡資料與預定時間保留，其餘仍可再修改）</span></label>
        <select id="f-tpl" :value="tplId" :disabled="applying || saving" @change="onPickTemplate">
          <option value="">— 不套用 —</option>
          <option v-for="t in templates" :key="t.tmplId" :value="t.tmplId">{{ t.tmplName }}</option>
        </select>
        <p v-if="tplListError" class="sub">{{ tplListError }}</p>
      </section>

      <DraftFields v-model="form" :options="options" :bad-field="badField" kind="app" :applicant-name="applicantName" />

      <fieldset class="card sec">
        <legend>附件 <span class="sub">（單檔 ≤ {{ upload.maxMb }} MB、最多 {{ upload.maxFiles }} 個；按「{{ SUBMIT_LABELS[mode] }}」後才上傳）</span></legend>
        <ul v-if="existing.length" class="files">
          <li v-for="f in existing" :key="f.attachId">
            <span class="tag">已上傳</span>
            <span class="fname">{{ f.fileName || '（未命名）' }}</span>
            <span class="sub">{{ kb(f.byteQty) }}・{{ f.uploadedAt ?? '' }}</span>
          </li>
        </ul>
        <ul v-if="queue.length" class="files">
          <li v-for="q in queue" :key="q.key">
            <span class="tag" :class="q.status">{{ QUEUE_LABELS[q.status] }}</span>
            <span class="fname">{{ q.file.name }}</span>
            <span class="sub">{{ kb(q.file.size) }}</span>
            <progress v-if="q.status === 'uploading'" max="100" :value="q.percent" :aria-label="`${q.file.name} 上傳進度`">{{ q.percent }}%</progress>
            <span v-if="q.status === 'uploading'" class="sub">{{ q.percent }}%</span>
            <span v-if="q.error" class="bad-text">{{ q.error }}</span>
            <button class="quiet small" type="button" :disabled="q.status === 'uploading'" @click="removeQueued(q.key)">移除</button>
          </li>
        </ul>
        <label class="pick">
          <input type="file" multiple :accept="ACCEPT" :disabled="saving" @change="onPick" />
        </label>
        <ul v-if="rejects.length" class="rejects" role="alert">
          <li v-for="(r, i) in rejects" :key="i">{{ r }}</li>
        </ul>
      </fieldset>

      <fieldset v-if="isResubmit" class="card sec">
        <legend>補件說明 <span class="sub">（選填：這次改了什麼，給簽核人看）</span></legend>
        <textarea id="f-resub" v-model="resubMemo" v-bind="fx('resubMemo')" rows="3" aria-label="補件說明" />
      </fieldset>

      <div class="bar">
        <p v-if="formError" class="bar-msg" role="alert">{{ formError }}</p>
        <div class="bar-btns">
          <button v-if="conflict" class="quiet" type="button" :disabled="saving" @click="reload">重新載入（放棄這次修改）</button>
          <router-link class="quiet cancel" :to="backTo">取消</router-link>
          <button class="go" type="submit" :disabled="saving">{{ saving ? savingText : SUBMIT_LABELS[mode] }}</button>
        </div>
      </div>
    </form>

    <ToastHost />
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter, type RouteLocationRaw } from 'vue-router'
import { AxiosError } from 'axios'
import ToastHost from '../components/ToastHost.vue'
import DraftFields from '../components/DraftFields.vue'
import { createApp, getApp, getFormOptions, resubmitApp, updateApp, uploadAttachment } from '../api/apps'
import { getTemplate, listTemplates } from '../api/templates'
import { errorMessage, isUnauthorized } from '../api/http'
import { useAuth } from '../composables/useAuth'
import { useToast } from '../composables/useToast'
import { APP_EDIT_ROUTE, APP_LIST_ROUTE, APP_RESUBMIT_ROUTE, APP_VIEW_ROUTE } from '../router/names'
import {
  ATTACH_EXTS,
  EVENT_LABELS,
  type ApiFieldErrorBody,
  type AppAttachment,
  type AppDetail,
  type AppDraftForm,
  type FormOption
} from '../types/app'
import type { TemplateListItem } from '../types/template'
import { emptyForm, formFromDetail, keepActive, precheckFile, safeFieldPath, toDraftRequest } from '../utils/draftForm'
import { kb } from '../utils/format'
import { formFromTemplate } from '../utils/templateForm'

const ACCEPT = ATTACH_EXTS.map(e => '.' + e).join(',')

type QueueStatus = 'wait' | 'uploading' | 'error'
interface QueueItem {
  key: number
  file: File
  status: QueueStatus
  percent: number
  error: string
}
const QUEUE_LABELS: Record<QueueStatus, string> = { wait: '待上傳', uploading: '上傳中', error: '失敗' }

type FormMode = 'new' | 'edit' | 'resubmit'
const TITLES: Record<FormMode, string> = { new: '新增申請單', edit: '編輯草稿', resubmit: '補件並重送' }
const SUBMIT_LABELS: Record<FormMode, string> = { new: '儲存草稿', edit: '儲存草稿', resubmit: '補件並送審' }

interface RejectInfo {
  label: string
  by: string
  at: string
  memo: string
}

/** 退件來源：簽核退件看目前實例裡退件那一關；執行階段／治理複驗退回看最後一筆對應事件 */
function rejectInfoOf(d: AppDetail): RejectInfo | null {
  const step = d.approval.steps.find(s => s.statusCode === 'REJECTED')
  if (step) {
    return { label: `簽核退件（${step.stepName ?? '關卡'}）`, by: step.deciderName ?? '', at: step.decidedAt ?? '', memo: step.memo ?? '' }
  }
  const ev = [...d.events].reverse().find(e => e.code === 'EXEC_REJECT' || e.code === 'GOV_RETURN')
  if (ev) return { label: EVENT_LABELS[ev.code ?? ''] ?? '退回', by: ev.userName ?? '', at: ev.at ?? '', memo: ev.memo ?? '' }
  return null
}

const route = useRoute()
const router = useRouter()
const auth = useAuth()
const { toast } = useToast()

const rootEl = ref<HTMLElement | null>(null)

const editId = computed(() => {
  const id = route.params.id
  return typeof id === 'string' ? id : ''
})
const isResubmit = computed(() => route.name === APP_RESUBMIT_ROUTE)
const mode = computed<FormMode>(() => (isResubmit.value ? 'resubmit' : editId.value !== '' ? 'edit' : 'new'))
const resubMemo = ref('')
const reject = ref<RejectInfo | null>(null)

const form = ref<AppDraftForm>(emptyForm())
const options = ref<FormOption[]>([])
const upload = ref({ maxMb: 50, maxFiles: 30 })
const existing = ref<AppAttachment[]>([])
const applicantName = ref('')
const rowVerNo = ref<number | null>(null)
/** 已存在的單號：編輯進來就有；新增在第一次存檔成功後才有 */
const savedId = ref('')

const loading = ref(true)
const loadError = ref('')
const saving = ref(false)
const savingText = ref('儲存中…')
const formError = ref('')
const conflict = ref(false)
const badField = ref<string | null>(null)

const queue = ref<QueueItem[]>([])
const rejects = ref<string[]>([])
let fileSeq = 0

/** 套用範本（只有新增模式）：清單載入失敗不擋填寫；tplId 為目前套用的範本，第一次存檔時帶給後端累計次數 */
const templates = ref<TemplateListItem[]>([])
const tplListError = ref('')
const tplId = ref('')
const applying = ref(false)
/** 上次載入或套用後的內容；不同代表使用者改過，套用前要 confirm */
let baseline = ''

/** 套用時保留的欄位不算進「改過」：申請人聯絡資料與預定開始／結束時間 */
function contentKey(f: AppDraftForm): string {
  return JSON.stringify({ ...f, deptName: '', tel: '', email: '', start: '', end: '' })
}

const backTo = computed<RouteLocationRaw>(() =>
  savedId.value ? { name: APP_VIEW_ROUTE, params: { id: savedId.value } } : { name: APP_LIST_ROUTE }
)

/** 作業大類 id 依顯示順序（categoryOthers 每類送一筆，400 的索引才對得回畫面） */
const catgOrder = computed(() => options.value.filter(o => o.groupCode === 'CATG').map(o => o.formOptionId))

/** 補件說明欄（不在 DraftFields 裡）的 data-field 與標紅 class */
function fx(path: string): { 'data-field': string; class: { bad: boolean } } {
  return { 'data-field': path, class: { bad: badField.value === path } }
}

/** 每次載入遞增；回應回來時不是最新就丟掉 */
let seq = 0

async function load(): Promise<void> {
  const mine = ++seq
  const id = editId.value
  loading.value = true
  loadError.value = ''
  formError.value = ''
  conflict.value = false
  badField.value = null
  try {
    const [opts, detail, tpls] = await Promise.all([
      getFormOptions(),
      id ? getApp(id) : Promise.resolve(null),
      id ? Promise.resolve(null) : listTemplates().catch((): null => null)
    ])
    if (mine !== seq) return
    options.value = opts.options
    upload.value = opts.upload
    if (detail) {
      if (isResubmit.value && !detail.permissions.canResubmit) {
        loadError.value = detail.statusCode === 'REJECTED' ? '只有申請人可以補件' : '申請單不是退件狀態，無法補件'
        return
      }
      if (!isResubmit.value && !detail.permissions.canEditDraft) {
        loadError.value = detail.statusCode === 'DRAFT' ? '只有申請人可以編輯草稿' : '申請單已不是草稿，無法編輯'
        return
      }
      reject.value = isResubmit.value ? rejectInfoOf(detail) : null
      form.value = keepActive(formFromDetail(detail), opts.options)
      existing.value = detail.attachments.filter(a => a.ownerType === 'APP')
      applicantName.value = detail.applicant.name ?? ''
      rowVerNo.value = detail.rowVerNo
      savedId.value = detail.appId
    } else {
      form.value = emptyForm()
      existing.value = []
      applicantName.value = auth.userName.value
      rowVerNo.value = null
      savedId.value = ''
      templates.value = tpls ?? []
      tplListError.value = tpls ? '' : '範本清單載入失敗，可直接填寫'
      tplId.value = ''
      baseline = contentKey(form.value)
      const q = route.query.template
      if (typeof q === 'string' && q !== '') await applyTemplate(q)
    }
  } catch (e: unknown) {
    if (mine !== seq) return
    if (isUnauthorized(e)) {
      loadError.value = '尚未登入'
      return
    }
    loadError.value = errorMessage(e)
    toast(loadError.value, 'red')
  } finally {
    if (mine === seq) loading.value = false
  }
}

/** 新增存檔後網址換成編輯頁時單號已是 savedId，不重新載入（否則會清掉待上傳清單）；同一單號在編輯與補件間切換才重載 */
watch(
  [editId, isResubmit],
  ([id], old) => {
    const sameMode = old !== undefined && old[1] === isResubmit.value
    if (id && id === savedId.value && sameMode) return
    queue.value = []
    rejects.value = []
    resubMemo.value = ''
    void load()
  },
  { immediate: true }
)

function reload(): void {
  void load()
}

/** 範本內容填入表單（已停用選項拿掉）；申請人聯絡資料與預定時間保留目前值。成功回 true */
async function applyTemplate(id: string): Promise<boolean> {
  const mine = seq
  applying.value = true
  try {
    const d = await getTemplate(id)
    if (mine !== seq) return false
    const cur = form.value
    form.value = {
      ...keepActive(formFromTemplate(d.form), options.value),
      deptName: cur.deptName, tel: cur.tel, email: cur.email, start: cur.start, end: cur.end
    }
    tplId.value = d.tmplId
    baseline = contentKey(form.value)
    toast(`已套用範本「${d.tmplName}」`, 'teal')
    return true
  } catch (e: unknown) {
    if (mine === seq && !isUnauthorized(e)) toast(errorMessage(e, '範本載入失敗，請稍後再試'), 'red')
    return false
  } finally {
    applying.value = false
  }
}

/** 下拉選了範本：改過內容先 confirm；取消或套用失敗時下拉回到原本的值。選「不套用」只取消累計，不清內容 */
async function onPickTemplate(e: Event): Promise<void> {
  const sel = e.target as HTMLSelectElement
  const next = sel.value
  if (next === '') {
    tplId.value = ''
    return
  }
  if (contentKey(form.value) !== baseline && !window.confirm('套用範本會覆蓋目前填寫的內容（申請人聯絡資料與預定時間保留），確定要套用？')) {
    sel.value = tplId.value
    return
  }
  if (!(await applyTemplate(next))) sel.value = tplId.value
}

function onPick(e: Event): void {
  const input = e.target as HTMLInputElement
  const files = input.files ? Array.from(input.files) : []
  input.value = ''
  addFiles(files)
}

function addFiles(files: File[]): void {
  const problems: string[] = []
  for (const f of files) {
    if (existing.value.length + queue.value.length >= upload.value.maxFiles) {
      problems.push(`${f.name}：附件最多 ${upload.value.maxFiles} 個`)
      continue
    }
    const why = precheckFile(f, upload.value.maxMb)
    if (why) {
      problems.push(`${f.name}：${why}`)
      continue
    }
    queue.value.push({ key: ++fileSeq, file: f, status: 'wait', percent: 0, error: '' })
  }
  rejects.value = problems
  if (problems.length) toast(`有 ${problems.length} 個檔案無法加入`, 'red')
}

function removeQueued(key: number): void {
  queue.value = queue.value.filter(q => q.key !== key)
}

function statusOf(e: unknown): number | undefined {
  return e instanceof AxiosError ? e.response?.status : undefined
}

function fieldOf(e: unknown): string | null {
  if (!(e instanceof AxiosError)) return null
  const body: unknown = e.response?.data
  if (body && typeof body === 'object' && 'field' in body) return safeFieldPath((body as ApiFieldErrorBody).field)
  return null
}

async function markField(field: string): Promise<void> {
  badField.value = field
  await nextTick()
  const el = rootEl.value?.querySelector<HTMLElement>(`[data-field="${field}"]`)
  if (!el) return
  if (typeof el.scrollIntoView === 'function') el.scrollIntoView({ block: 'center' })
  el.focus({ preventScroll: true })
}

function saveFailed(e: unknown): void {
  if (isUnauthorized(e)) return
  const msg = errorMessage(e, '儲存失敗，請稍後再試')
  formError.value = msg
  const status = statusOf(e)
  if (status === 409) conflict.value = true
  if (status === 400) {
    const f = fieldOf(e)
    if (f) void markField(f)
  }
  toast(msg, 'red')
}

/** 逐檔上傳；成功的移到「已上傳」，失敗的留在清單標原因。409（已不是草稿）或 401 就停，不再試後面的 */
async function uploadQueue(id: string): Promise<boolean> {
  let allOk = true
  const items = [...queue.value]
  for (let i = 0; i < items.length; i++) {
    const item = items[i]
    savingText.value = `上傳附件 ${i + 1}/${items.length}…`
    item.status = 'uploading'
    item.percent = 0
    item.error = ''
    try {
      const a = await uploadAttachment(id, item.file, p => {
        item.percent = p
      })
      existing.value.push(a)
      queue.value = queue.value.filter(q => q.key !== item.key)
    } catch (e: unknown) {
      allOk = false
      item.status = 'error'
      item.error = isUnauthorized(e) ? '尚未登入' : errorMessage(e, '上傳失敗，請稍後再試')
      if (isUnauthorized(e) || statusOf(e) === 409) return false
    }
  }
  return allOk
}

async function save(): Promise<void> {
  if (saving.value) return
  formError.value = ''
  conflict.value = false
  badField.value = null
  if (form.value.title.trim() === '') {
    formError.value = '請填寫標題'
    void markField('title')
    return
  }
  if (isResubmit.value) {
    await resubmit()
    return
  }
  saving.value = true
  savingText.value = '儲存中…'
  const catgIds = catgOrder.value
  try {
    if (savedId.value) {
      const r = await updateApp(savedId.value, toDraftRequest(form.value, catgIds, rowVerNo.value ?? 0))
      rowVerNo.value = r.rowVerNo
    } else {
      const r = await createApp(toDraftRequest(form.value, catgIds), tplId.value || undefined)
      savedId.value = r.appId
      rowVerNo.value = r.rowVerNo
      await router.replace({ name: APP_EDIT_ROUTE, params: { id: r.appId } })
    }
  } catch (e: unknown) {
    saving.value = false
    saveFailed(e)
    return
  }
  const ok = await uploadQueue(savedId.value)
  saving.value = false
  if (ok) {
    toast('草稿已儲存', 'teal')
    await router.push({ name: APP_VIEW_ROUTE, params: { id: savedId.value } })
    return
  }
  formError.value = '草稿內容已儲存，但有附件上傳失敗；可移除失敗的檔案，或再按「儲存草稿」重試'
  toast('有附件上傳失敗', 'amber')
}

/**
 * 補件：先上傳附件（補件後就是審核中、不能再傳），全部成功才送補件；任一檔失敗就停在本頁、內容不送出。
 * 409（版本或狀態已變）與 403 不自動重載（會洗掉剛改的內容），比照編輯草稿顯示「重新載入（放棄這次修改）」
 */
async function resubmit(): Promise<void> {
  if (!window.confirm('確定要補件並重新送審？送出後會成為新的版次，從第一關重新簽核。')) return
  saving.value = true
  const id = savedId.value
  if (queue.value.length) {
    const ok = await uploadQueue(id)
    if (!ok) {
      saving.value = false
      formError.value = '有附件上傳失敗，補件尚未送出；可移除失敗的檔案，或再按「補件並送審」重試'
      toast('有附件上傳失敗', 'amber')
      return
    }
  }
  savingText.value = '送出中…'
  const catgIds = catgOrder.value
  try {
    await resubmitApp(id, {
      rowVerNo: rowVerNo.value ?? 0,
      resubMemo: resubMemo.value,
      form: toDraftRequest(form.value, catgIds)
    })
  } catch (e: unknown) {
    saving.value = false
    saveFailed(e)
    if (statusOf(e) === 403) conflict.value = true
    return
  }
  saving.value = false
  toast('已補件並重新送審', 'teal')
  await router.push({ name: APP_VIEW_ROUTE, params: { id } })
}
</script>

<style scoped>
main { width: 100%; max-width: 1180px; margin: 0 auto; padding: 24px 20px; }

.hero { margin-bottom: 16px; display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; }
.head { min-width: 0; }
.hero h1 { margin: 0; font-size: 26px; color: var(--navy); overflow-wrap: anywhere; }
.hero .id { font-size: 18px; color: var(--gray); margin-left: 6px; }
.link { color: var(--blue); font-size: 17px; white-space: nowrap; }

.card { background: #fff; border: 1px solid var(--line); border-radius: 10px; padding: 16px 20px; margin: 0 0 16px; overflow-wrap: anywhere; min-width: 0; }
.sec legend { font-size: 21px; font-weight: 700; color: var(--teal-dark); padding: 0 6px; }
.muted { color: var(--gray); margin: 4px 0; }
.error { color: var(--red); font-weight: 700; }
.sub { color: var(--gray); font-size: 14px; font-weight: 400; }

/* 一～四區塊的欄位樣式在 DraftFields.vue；這裡只留補件說明欄 */
textarea {
  width: 100%; min-width: 0; padding: 6px 10px; border: 1px solid #cbd5e1; border-radius: 7px; font-size: 17px; background: #fff; color: var(--dark);
  resize: vertical; line-height: 1.5;
}
textarea.bad { border-color: var(--red); box-shadow: 0 0 0 2px rgba(220, 38, 38, .2); }
label { display: block; margin-bottom: 2px; color: var(--dark); font-weight: 700; }
.tpl select { width: 100%; max-width: 560px; min-width: 0; padding: 6px 10px; border: 1px solid #cbd5e1; border-radius: 7px; font-size: 17px; background: #fff; color: var(--dark); }
.tpl .sub { margin: 4px 0 0; }

.quiet { background: #fff; border: 1px solid var(--line); color: var(--dark); border-radius: 7px; padding: 8px 18px; font-size: 17px; cursor: pointer; font-weight: 400; }
.quiet:disabled { opacity: .55; cursor: default; }
.quiet.small { min-height: 36px; padding: 2px 12px; font-size: 15px; margin-left: 8px; }

.files { list-style: none; margin: 0 0 10px; padding: 0; }
.files li { padding: 6px 0; border-bottom: 1px solid var(--line); display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.fname { font-weight: 700; overflow-wrap: anywhere; }
.tag { display: inline-block; padding: 0 8px; border-radius: 6px; background: var(--teal-bg); color: var(--teal-dark); font-size: 14px; font-weight: 700; }
.tag.wait { background: var(--bg); color: var(--gray); }
.tag.uploading { background: #e0e9f8; color: var(--blue); }
.tag.error { background: #fde8e8; color: var(--red); }
.bad-text { color: var(--red); font-size: 15px; }
progress { width: 160px; height: 14px; }
.pick { font-weight: 400; }
.rejects { margin: 8px 0 0; padding-left: 20px; color: var(--red); font-size: 15px; }

.bar { position: sticky; bottom: 0; background: #fff; border: 1px solid var(--line); border-radius: 10px; padding: 10px 16px; display: flex; flex-wrap: wrap; align-items: center; justify-content: flex-end; gap: 10px; box-shadow: 0 -2px 8px rgba(0, 0, 0, .06); }
.bar-msg { flex: 1 1 300px; margin: 0; color: var(--red); font-weight: 700; overflow-wrap: anywhere; }
.bar-btns { display: flex; flex-wrap: wrap; gap: 10px; }
.cancel { text-decoration: none; display: inline-flex; align-items: center; }
.reject { border-color: var(--red); background: #fdf2f2; }
.reject h2 { margin: 0 0 6px; font-size: 19px; color: var(--red); }
.reject-who { margin: 0 0 4px; font-weight: 700; }
.reject-memo { margin: 0; white-space: pre-wrap; }
.go { background: var(--teal); border: none; color: #fff; font-weight: 700; border-radius: 7px; padding: 10px 24px; font-size: 19px; cursor: pointer; min-width: 160px; }
.go:hover:not(:disabled) { filter: brightness(1.08); }
.go:disabled { opacity: .55; cursor: default; }
</style>
