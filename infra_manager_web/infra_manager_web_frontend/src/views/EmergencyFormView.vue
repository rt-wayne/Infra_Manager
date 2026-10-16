<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-13
  變更說明: 新增：緊急存取申請（BACKLOG 第 46 項 S3，裁示 ⑫；POST /emergencies，需 EMG_APPLY）
            欄位：帳號類別（列全部類別、不開放新增申請者加註「（停用）」，S3 裁示③B：後端只要求類別存在）、
            帳號或系統（200 字）、緊急事由（500 字）、預估時長（下拉 1～24 小時，S3 裁示④A：不會填出小數）；
            申請人唯讀（目前身分；切換中顯示「{代理}（實際操作人：{實際}）」）
            表單上方固定說明「送出即生效」；按送出先檢查必填與字數，再開確認框（S3 裁示⑤B：不提供撤回）
            確認後送出，成功 toast 單號並回清單；失敗顯示後端訊息、留在表單；
            沒有 HTTP 回應（逾時、中斷）或 5xx 時改提示先到清單確認，避免重複送出（審查第 1 項）；
            此提示除 toast 外另在表單上方常駐、附清單連結（複審①②）
            文字欄不設 maxlength（避免貼上時靜默截斷），字數按字元算、超過計數變紅並於送出時擋下（審查第 2、3 項）
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-16
  變更說明: 必填欄位醒目提醒：表單上方加「* 為必填欄位」；帳號類別、預估時長、帳號或系統、緊急事由標籤後加紅色 *
-->
<template>
  <main>
    <div class="priv-banner emg-notice">送出即生效，須於 24 小時內由主管補核准；送出後無法撤回。</div>
    <div v-if="unknownResult" class="priv-banner unknown-result" role="alert">
      {{ UNKNOWN_RESULT_MSG }}。<RouterLink to="/emergency">前往緊急存取清單</RouterLink>
    </div>
    <section class="form-section">
      <h3>緊急存取內容</h3>
      <p class="req-legend"><span class="req-mark">*</span> 為必填欄位</p>
      <div class="grid2">
        <div class="field">
          <label for="emgCatg">帳號類別<span class="req-mark" title="必填">*</span></label>
          <select id="emgCatg" v-model="form.catgId">
            <option value="" disabled>請選擇</option>
            <option v-for="c in catgOptions" :key="c.catgId" :value="c.catgId">{{ c.catgName }}{{ c.enabled ? '' : '（停用）' }}</option>
          </select>
        </div>
        <div class="field">
          <label for="emgHours">預估時長<span class="req-mark" title="必填">*</span></label>
          <select id="emgHours" v-model.number="form.expectHourQty">
            <option :value="0" disabled>請選擇</option>
            <option v-for="h in HOUR_OPTIONS" :key="h" :value="h">{{ h }} 小時</option>
          </select>
        </div>
        <div class="field">
          <label for="emgApplicant">申請人</label>
          <input id="emgApplicant" type="text" :value="applicantText" disabled />
        </div>
      </div>
      <div class="field">
        <label for="emgTarget">帳號或系統<span class="req-mark" title="必填">*</span><span class="counter" :class="{ over: targetLen > TARGET_MAX }">{{ targetLen }} / {{ TARGET_MAX }}</span></label>
        <input id="emgTarget" v-model="form.targetDescr" type="text" placeholder="例如：ERP 正式機 DB 的 sys 帳號" autocomplete="off" />
      </div>
      <div class="field">
        <label for="emgReason">緊急事由<span class="req-mark" title="必填">*</span><span class="counter" :class="{ over: reasonLen > REASON_MAX }">{{ reasonLen }} / {{ REASON_MAX }}</span></label>
        <textarea id="emgReason" v-model="form.applyReason" rows="4" placeholder="請說明為什麼需要緊急存取、預計做哪些操作"></textarea>
      </div>
      <div class="form-actions">
        <RouterLink class="btn" to="/emergency">取消</RouterLink>
        <button type="button" class="btn btn-primary submit" :disabled="submitting" @click="askConfirm">送出</button>
      </div>
    </section>

    <BaseModal v-if="confirming" title="確認送出緊急存取" close-text="返回修改" @close="confirming = false">
      <p class="confirm-text">送出後<b>立即生效</b>，且<b>無法撤回</b>。主管須在 24 小時內補核准（期限約 {{ dueText }}）。</p>
      <div class="kv"><div class="k">帳號或系統</div><div class="v">{{ form.targetDescr.trim() }}</div></div>
      <div class="kv"><div class="k">預估時長</div><div class="v">{{ form.expectHourQty }} 小時</div></div>
      <template #actions>
        <button type="button" class="btn btn-primary confirm-submit" :disabled="submitting" @click="submit">
          {{ submitting ? '送出中…' : '確認送出' }}
        </button>
      </template>
    </BaseModal>
    <ToastHost />
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import BaseModal from '../components/BaseModal.vue'
import ToastHost from '../components/ToastHost.vue'
import { getCodes } from '../api/common'
import { submitEmergency } from '../api/emergency'
import { errorMessage, errorStatus } from '../api/errors'
import { useSession } from '../composables/useSession'
import { useToast } from '../composables/useToast'
import type { Codes } from '../types/common'
import { fmtDateTime } from '../utils/date'
import { personText } from '../utils/labels'

/** 欄寬與後端 EmergencyService 一致（帳號或系統 200、緊急事由 500 字） */
const TARGET_MAX = 200
const REASON_MAX = 500
/** 預估時長 1～24 小時（裁示②） */
const HOUR_OPTIONS = Array.from({ length: 24 }, (_, i) => i + 1)
/** 補核准期限＝送出＋24 小時（確認框顯示的估計值；實際以後端送出時間為準） */
const DUE_MS = 24 * 60 * 60 * 1000
/** 送出結果不明（沒有回應或 5xx）時的提示（第一次可能已成功） */
const UNKNOWN_RESULT_MSG = '無法確認是否已送出，請先到緊急存取清單確認，避免重複送出'

const router = useRouter()
const { toast } = useToast()
const { me, userName, realUserName, impersonating } = useSession()

const codes = ref<Codes | null>(null)
const form = reactive({ catgId: '', targetDescr: '', applyReason: '', expectHourQty: 0 })
const confirming = ref(false)
const submitting = ref(false)
const dueText = ref('')
/** 上一次送出結果不明（常駐提示，不像 toast 會自動消失；離開頁面才清除） */
const unknownResult = ref(false)

/**
 * 送出結果是否不明：沒有 HTTP 回應（逾時、中斷）或 5xx（殼 jar 與後端斷線時回 500、日後反向代理 502／504）
 * 都可能已寫入；送出無法撤回，不可誘導重送。4xx 為後端驗證擋下，一定沒寫入
 */
function resultUnknown(e: unknown): boolean {
  const status = errorStatus(e)
  return status === 0 || status >= 500
}

/** 全部類別依排序；不開放新增申請的也列（加註「（停用）」） */
const catgOptions = computed(() => [...(codes.value?.categories ?? [])].sort((a, b) => a.sortSeq - b.sortSeq))
/** 申請人＝目前身分；切換中顯示實際操作人 */
const applicantText = computed(() => (me.value && impersonating.value ? personText(realUserName.value, userName.value) : realUserName.value))

/** 字數按字元（碼點）算、去頭尾空白，與後端 Texts.maxChars 一致（emoji、罕用字算 1 字） */
function charCount(s: string): number {
  return [...s.trim()].length
}
const targetLen = computed(() => charCount(form.targetDescr))
const reasonLen = computed(() => charCount(form.applyReason))

/** 前端檢查（後端仍會再驗）；回第一個錯誤訊息，沒錯回空字串 */
function validate(): string {
  if (!form.catgId) return '請填寫帳號類別'
  if (!form.targetDescr.trim()) return '請填寫帳號或系統'
  if (!form.applyReason.trim()) return '請填寫緊急事由'
  if (!form.expectHourQty) return '請填寫預估時長'
  if (targetLen.value > TARGET_MAX) return `帳號或系統不可超過 ${TARGET_MAX} 字`
  if (reasonLen.value > REASON_MAX) return `緊急事由不可超過 ${REASON_MAX} 字`
  return ''
}

function askConfirm(): void {
  const err = validate()
  if (err) {
    toast(err, 'amber')
    return
  }
  dueText.value = fmtDateTime(new Date(Date.now() + DUE_MS))
  confirming.value = true
}

async function submit(): Promise<void> {
  if (submitting.value) return
  submitting.value = true
  try {
    const r = await submitEmergency({
      catgId: form.catgId,
      targetDescr: form.targetDescr.trim(),
      applyReason: form.applyReason.trim(),
      expectHourQty: form.expectHourQty
    })
    toast(`緊急存取已送出並生效！單號 ${r?.emgId ?? ''}，主管須於 24 小時內補核准`, 'teal')
    confirming.value = false
    void router.push('/emergency')
  } catch (e) {
    confirming.value = false
    if (resultUnknown(e)) {
      unknownResult.value = true
      toast(UNKNOWN_RESULT_MSG, 'amber')
    } else {
      toast(errorMessage(e, '送出失敗，請稍後再試'), 'amber')
    }
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  try {
    codes.value = await getCodes()
  } catch (e) {
    toast(errorMessage(e, '查詢失敗，請稍後再試'), 'amber')
  }
})
</script>

<style scoped>
.counter { float: right; font-size: 15px; color: var(--gray); }
.counter.over { color: var(--red); font-weight: 700; }
.unknown-result a { font-weight: 700; }
.confirm-text { font-size: 18px; margin: 0 0 12px; }
a.btn { display: inline-flex; align-items: center; min-height: 48px; text-decoration: none; }
</style>
