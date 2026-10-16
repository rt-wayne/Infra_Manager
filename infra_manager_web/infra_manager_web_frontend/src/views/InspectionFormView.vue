<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: 新增：新增巡檢（開發文件 spec_im_acct_insp.md 第 3.8、6 節；原型 view-insp-form）
            基本資料：巡檢時間預設現在可改、巡檢人員唯讀（目前身分）
            項目來自 GET /inspection-items，依 sectName 分組；每項 正常／異常／N.A. 三選一（預設正常），
            選異常時展開異常說明（必填）；異常數即時統計；「巡檢人員簽章並結案」POST /inspections
            項目載入失敗只 toast，不顯示表單
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: v2.0（開發文件 spec_im_acct_insp_v2.0.md 第 4.14、8.3 節）：結果只剩正常／異常、無預設，
            14 項都選完才能送出（未選完時按鈕停用並顯示尚餘項數）；異常說明必填（E10）；
            巡檢時間預設現在、不可晚於現在（輸入框 max＋送出前檢查）；送出格式 yyyy-MM-dd HH:mm:ss；
            巡檢人員依 4.14 顯示（切換中「{代理}（實際操作人：{實際}）」）
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-16
  變更說明: 必填欄位醒目提醒：上方加「* 為必填欄位」；巡檢時間、「二、巡檢項目」標題（每項都要選）加紅色 *；
            異常說明的「（必填）」改為紅色 *
-->
<template>
  <main>
    <p class="req-legend"><span class="req-mark">*</span> 為必填欄位</p>
    <section class="form-section">
      <h3>一、基本資料</h3>
      <div class="grid2">
        <div class="field"><label for="inspNo">巡檢單號</label><input id="inspNo" type="text" value="（送出後產生）" disabled /></div>
        <div class="field"><label for="inspDate">巡檢時間<span class="req-mark" title="必填">*</span></label><input id="inspDate" v-model="inspDate" type="datetime-local" :max="maxDate" /></div>
        <div class="field"><label for="inspector">巡檢人員</label><input id="inspector" type="text" :value="inspectorText" disabled /></div>
      </div>
    </section>

    <section v-if="items.length > 0" class="form-section">
      <h3>二、巡檢項目<span class="req-mark" title="必填">*</span><span class="items-req">每一項都要選擇結果</span></h3>
      <template v-for="g in groups" :key="g.sectName">
        <div class="divider-lbl">{{ g.sectName }}</div>
        <div
          v-for="it in g.items"
          :key="it.inspItemId"
          class="check-item"
          :class="{ excpt: results[it.inspItemId]?.resultCode === 'EXCPT', unset: !results[it.inspItemId]?.resultCode }"
          :data-item="it.inspItemId"
        >
          <div class="row">
            <div class="item-name">{{ it.itemName }}<div v-if="it.itemDescr" class="hint">{{ it.itemDescr }}</div></div>
            <div class="radio-row">
              <label v-for="o in RESULT_OPTIONS" :key="o.value">
                <input v-model="results[it.inspItemId].resultCode" type="radio" :name="`chk-${it.inspItemId}`" :value="o.value" /> {{ o.label }}
              </label>
            </div>
          </div>
          <div v-if="results[it.inspItemId]?.resultCode === 'EXCPT'" class="check-detail">
            <label :for="`desc-${it.inspItemId}`">異常說明<span class="req-mark" title="必填">*</span></label>
            <textarea :id="`desc-${it.inspItemId}`" v-model="results[it.inspItemId].excptDescr" rows="2" placeholder="請填寫異常說明"></textarea>
          </div>
        </div>
      </template>
    </section>

    <div v-if="items.length > 0" class="summary-bar">
      <span>
        異常項目：<b class="excpt-count">{{ excptCount }}</b>
        <span v-if="unsetCount > 0" class="hint unset-count">　尚有 {{ unsetCount }} 項未選擇結果</span>
      </span>
      <button type="button" class="btn btn-primary submit" :disabled="submitting || unsetCount > 0" @click="submit">
        {{ submitting ? '送出中…' : '巡檢人員簽章並結案' }}
      </button>
    </div>
    <ToastHost />
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import ToastHost from '../components/ToastHost.vue'
import { errorMessage } from '../api/errors'
import { listInspItems, submitInspection } from '../api/inspection'
import { useSession } from '../composables/useSession'
import { useToast } from '../composables/useToast'
import type { InspItem, ResultCode } from '../types/inspection'
import { localInputToApi, localInputToDate, toLocalInput } from '../utils/date'
import { personText } from '../utils/labels'

const RESULT_OPTIONS: { value: ResultCode; label: string }[] = [
  { value: 'NORMAL', label: '正常' },
  { value: 'EXCPT', label: '異常' }
]
const MSG_UNSET = '請完成所有巡檢項目的結果選擇'
const MSG_EXCPT = '請填寫異常說明'
const MSG_FUTURE = '巡檢時間不可晚於現在'

const router = useRouter()
const { toast } = useToast()
const { me, userName, realUserName, impersonating } = useSession()

const now = new Date()
const inspDate = ref(toLocalInput(now))
const maxDate = toLocalInput(now)
const items = ref<InspItem[]>([])
/** resultCode 空字串＝尚未選擇（無預設） */
const results = reactive<Record<string, { resultCode: ResultCode | ''; excptDescr: string }>>({})
const submitting = ref(false)

/** 4.14：無代理身分 → 實際人員；切換中 → 「{代理}（實際操作人：{實際}）」 */
const inspectorText = computed(() => (me.value && impersonating.value ? personText(realUserName.value, userName.value) : realUserName.value))

/** 依 sectName 分組，保留後端排序（SORT_SEQ） */
const groups = computed(() => {
  const out: { sectName: string; items: InspItem[] }[] = []
  for (const it of items.value) {
    const last = out[out.length - 1]
    if (last && last.sectName === it.sectName) {
      last.items.push(it)
    } else {
      out.push({ sectName: it.sectName, items: [it] })
    }
  }
  return out
})
const excptCount = computed(() => items.value.filter(it => results[it.inspItemId]?.resultCode === 'EXCPT').length)
const unsetCount = computed(() => items.value.filter(it => !results[it.inspItemId]?.resultCode).length)

async function submit(): Promise<void> {
  const date = localInputToApi(inspDate.value)
  const d = localInputToDate(inspDate.value)
  if (!date || !d) {
    toast('請填寫巡檢時間', 'amber')
    return
  }
  if (d.getTime() > Date.now()) {
    toast(MSG_FUTURE, 'amber')
    return
  }
  if (unsetCount.value > 0) {
    toast(MSG_UNSET, 'amber')
    return
  }
  for (const it of items.value) {
    const r = results[it.inspItemId]
    if (r.resultCode === 'EXCPT' && !r.excptDescr.trim()) {
      toast(MSG_EXCPT, 'amber')
      document.getElementById(`desc-${it.inspItemId}`)?.focus()
      return
    }
  }
  submitting.value = true
  try {
    const res = await submitInspection({
      inspDate: date,
      results: items.value.map(it => {
        const r = results[it.inspItemId]
        const code: ResultCode = r.resultCode === 'EXCPT' ? 'EXCPT' : 'NORMAL'
        return { inspItemId: it.inspItemId, resultCode: code, excptDescr: code === 'EXCPT' ? r.excptDescr.trim() : '' }
      })
    })
    toast(`巡檢紀錄 ${res?.inspId ?? ''} 已送出，巡檢人員簽章完成即結案`, 'teal')
    void router.push('/inspections')
  } catch (e) {
    toast(errorMessage(e, '送出失敗，請稍後再試'), 'amber')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  try {
    const data = await listInspItems()
    if (!Array.isArray(data)) throw new Error('unexpected response')
    for (const it of data) {
      results[it.inspItemId] = { resultCode: '', excptDescr: '' }
    }
    items.value = data
  } catch (e) {
    toast(errorMessage(e, '查詢失敗，請稍後再試'), 'amber')
  }
})
</script>

<style scoped>
.check-item.unset { border-style: dashed; }
.unset-count { font-size: 17px; }
.items-req { margin-left: 8px; font-size: 17px; font-weight: 400; color: var(--gray); }
</style>
