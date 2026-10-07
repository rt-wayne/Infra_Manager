<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: 新增：範本新增／修改頁（S5 R2，/templates/new 與 /templates/:id/edit 共用，依有沒有 :id 判斷）
            - 表單欄位用 DraftFields（kind="template"）：同申請單表單，但不含申請人聯絡資料與開始／結束時間（只存預計耗時）
            - 上方多一個「範本名稱」（必填）；標題可空（套用後在申請單上填）
            - 選項來自 GET /form-options（只有啟用的）；修改時已停用的選項自動拿掉（否則後端回「選項不正確」存不了）
            - 修改：canEdit 為 false（不是建立者也不是 admin）時不顯示表單；實際權限以後端 403 為準
            - 存檔成功回範本列表；400 有帶 field 就標紅該欄並捲過去（tmplName 以外的路徑都相對 form，與 DraftFields 的 data-field 一致）
            - 401 由登入處理器導頁，本頁不另出 toast
-->
<template>
  <main ref="rootEl">
    <header class="hero">
      <div class="head">
        <h1>{{ editId ? '修改範本' : '新增範本' }}</h1>
        <p class="sub">範本存的是申請單的內容樣板；申請人聯絡資料與預定開始／結束時間在申請單上填</p>
      </div>
      <router-link class="link" :to="{ name: TEMPLATE_LIST_ROUTE }">← 回範本列表</router-link>
    </header>

    <p v-if="loadError" class="card error" role="alert">{{ loadError }}</p>
    <p v-else-if="loading" class="card muted">載入中…</p>

    <form v-else class="paper" novalidate @submit.prevent="save">
      <fieldset class="card sec">
        <legend>範本</legend>
        <label for="f-tmpl-name">範本名稱 <span class="req">*</span></label>
        <input id="f-tmpl-name" v-model="tmplName" v-bind="fx('tmplName')" placeholder="例：防火牆韌體升級" />
        <p v-if="meta" class="sub meta">
          建立者 {{ meta.ownerName || '—' }}・套用 {{ meta.useCnt }} 次<span v-if="meta.updatedAt">・最後更新 {{ meta.updatedAt }}</span>
        </p>
      </fieldset>

      <DraftFields v-model="form" :options="options" :bad-field="badField" kind="template" />

      <div class="bar">
        <p v-if="formError" class="bar-msg" role="alert">{{ formError }}</p>
        <div class="bar-btns">
          <router-link class="quiet cancel" :to="{ name: TEMPLATE_LIST_ROUTE }">取消</router-link>
          <button class="go" type="submit" :disabled="saving">{{ saving ? '儲存中…' : '儲存範本' }}</button>
        </div>
      </div>
    </form>

    <ToastHost />
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { AxiosError } from 'axios'
import DraftFields from '../components/DraftFields.vue'
import ToastHost from '../components/ToastHost.vue'
import { getFormOptions } from '../api/apps'
import { createTemplate, getTemplate, updateTemplate } from '../api/templates'
import { errorMessage, isUnauthorized } from '../api/http'
import { useToast } from '../composables/useToast'
import { TEMPLATE_LIST_ROUTE } from '../router/names'
import type { ApiFieldErrorBody, AppDraftForm, FormOption } from '../types/app'
import type { TemplateDetail } from '../types/template'
import { emptyForm, keepActive, safeFieldPath } from '../utils/draftForm'
import { formFromTemplate, toTemplateForm } from '../utils/templateForm'

const route = useRoute()
const router = useRouter()
const { toast } = useToast()

const rootEl = ref<HTMLElement | null>(null)

const editId = computed(() => {
  const id = route.params.id
  return typeof id === 'string' ? id : ''
})

const tmplName = ref('')
const form = ref<AppDraftForm>(emptyForm())
const options = ref<FormOption[]>([])
const meta = ref<Pick<TemplateDetail, 'ownerName' | 'useCnt' | 'updatedAt'> | null>(null)

const loading = ref(true)
const loadError = ref('')
const saving = ref(false)
const formError = ref('')
const badField = ref<string | null>(null)

/** 作業大類 id 依顯示順序（categoryOthers 每類送一筆，400 的索引才對得回畫面） */
const catgOrder = computed(() => options.value.filter(o => o.groupCode === 'CATG').map(o => o.formOptionId))

/** 範本名稱欄的 data-field 與標紅 class（其餘欄位在 DraftFields 裡） */
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
  badField.value = null
  try {
    const [opts, detail] = await Promise.all([getFormOptions(), id ? getTemplate(id) : Promise.resolve(null)])
    if (mine !== seq) return
    options.value = opts.options
    if (detail) {
      if (!detail.canEdit) {
        loadError.value = '只有範本建立者或管理員可以修改範本'
        return
      }
      tmplName.value = detail.tmplName
      form.value = keepActive(formFromTemplate(detail.form), opts.options)
      meta.value = { ownerName: detail.ownerName, useCnt: detail.useCnt, updatedAt: detail.updatedAt }
    } else {
      tmplName.value = ''
      form.value = emptyForm()
      meta.value = null
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

watch(editId, () => void load(), { immediate: true })

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

async function save(): Promise<void> {
  if (saving.value) return
  formError.value = ''
  badField.value = null
  if (tmplName.value.trim() === '') {
    formError.value = '請填寫範本名稱'
    void markField('tmplName')
    return
  }
  saving.value = true
  const body = { tmplName: tmplName.value, form: toTemplateForm(form.value, catgOrder.value) }
  try {
    if (editId.value) await updateTemplate(editId.value, body)
    else await createTemplate(body)
  } catch (e: unknown) {
    saving.value = false
    if (isUnauthorized(e)) return
    const msg = errorMessage(e, '儲存失敗，請稍後再試')
    formError.value = msg
    if (e instanceof AxiosError && e.response?.status === 400) {
      const f = fieldOf(e)
      if (f) void markField(f)
    }
    toast(msg, 'red')
    return
  }
  saving.value = false
  toast('範本已儲存', 'teal')
  await router.push({ name: TEMPLATE_LIST_ROUTE })
}
</script>

<style scoped>
main { width: 100%; max-width: 1180px; margin: 0 auto; padding: 24px 20px; }

.hero { margin-bottom: 16px; display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; }
.head { min-width: 0; }
.hero h1 { margin: 0; font-size: 26px; color: var(--navy); overflow-wrap: anywhere; }
.link { color: var(--blue); font-size: 17px; white-space: nowrap; }

.card { background: #fff; border: 1px solid var(--line); border-radius: 10px; padding: 16px 20px; margin: 0 0 16px; overflow-wrap: anywhere; min-width: 0; }
.sec legend { font-size: 21px; font-weight: 700; color: var(--teal-dark); padding: 0 6px; }
.muted { color: var(--gray); margin: 4px 0; }
.error { color: var(--red); font-weight: 700; }
.sub { color: var(--gray); font-size: 14px; font-weight: 400; }
.req { color: var(--red); }
.meta { margin: 8px 0 0; }

input {
  width: 100%; min-width: 0; padding: 6px 10px; border: 1px solid #cbd5e1; border-radius: 7px; font-size: 17px; background: #fff; color: var(--dark);
}
input.bad { border-color: var(--red); box-shadow: 0 0 0 2px rgba(220, 38, 38, .2); }
label { display: block; margin-bottom: 2px; color: var(--dark); font-weight: 700; }

.quiet { background: #fff; border: 1px solid var(--line); color: var(--dark); border-radius: 7px; padding: 8px 18px; font-size: 17px; cursor: pointer; font-weight: 400; }
.bar { position: sticky; bottom: 0; background: #fff; border: 1px solid var(--line); border-radius: 10px; padding: 10px 16px; display: flex; flex-wrap: wrap; align-items: center; justify-content: flex-end; gap: 10px; box-shadow: 0 -2px 8px rgba(0, 0, 0, .06); }
.bar-msg { flex: 1 1 300px; margin: 0; color: var(--red); font-weight: 700; overflow-wrap: anywhere; }
.bar-btns { display: flex; flex-wrap: wrap; gap: 10px; }
.cancel { text-decoration: none; display: inline-flex; align-items: center; }
.go { background: var(--teal); border: none; color: #fff; font-weight: 700; border-radius: 7px; padding: 10px 24px; font-size: 19px; cursor: pointer; min-width: 160px; }
.go:hover:not(:disabled) { filter: brightness(1.08); }
.go:disabled { opacity: .55; cursor: default; }
</style>
