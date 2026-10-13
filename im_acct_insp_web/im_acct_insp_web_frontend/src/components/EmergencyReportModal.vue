<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-13
  變更說明: 新增：緊急存取申請人回報彈窗（BACKLOG 第 46 項 S5 回合 2；POST /emergencies/{id}/report）
            由詳情彈窗開啟（疊在其上）。回報結果二選一：沒有異動（說明選填、不帶帳號）／異動了既有帳號
            （說明必填、以帳號選取器加入 1～20 個帳號、各帳號說明選填；S4-②③）。字數按碼點、去頭尾空白，各 500 字。
            回報只能一次、主管處理後不能再回報（⑧），彈窗副標寫明；不另開確認框。
            成功 toast 後 emit done；409（已回報、已處理或版本不符）toast 後同樣 emit done，由詳情彈窗關閉本彈窗並重載。
            送出中忽略關閉（同詳情彈窗：避免卸載後 emit 被丟掉）
-->
<template>
  <BaseModal :title="`回報 — ${emg.emgId}`" :sub="MSG_SUB" wide close-text="取消" @close="close">
    <div class="field">
      <label>回報結果（必選）</label>
      <div class="radio-row">
        <label><input v-model="typeCode" type="radio" name="emg-report-type" value="NONE" class="type-none" /> {{ EMG_REPORT_LABEL.NONE }}</label>
        <label><input v-model="typeCode" type="radio" name="emg-report-type" value="MODIFIED" class="type-modified" /> {{ EMG_REPORT_LABEL.MODIFIED }}</label>
      </div>
    </div>

    <div class="field">
      <label for="emg-report-descr">回報說明（{{ typeCode === 'MODIFIED' ? '必填' : '選填' }}，{{ DESCR_MAX }} 字內）</label>
      <textarea id="emg-report-descr" v-model="descr" rows="3" :placeholder="typeCode === 'MODIFIED' ? '請說明做了哪些異動' : '可補充說明（選填）'"></textarea>
      <div class="hint" :class="{ warn: descrCount > DESCR_MAX }">{{ descrCount }} / {{ DESCR_MAX }}</div>
    </div>

    <EmgAcctPicker v-if="typeCode === 'MODIFIED'" v-model="picked" />

    <template #actions>
      <button type="button" class="btn btn-primary confirm-report" :disabled="busy" @click="submit">{{ busy ? '送出中…' : '送出回報' }}</button>
    </template>
  </BaseModal>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import BaseModal from './BaseModal.vue'
import EmgAcctPicker from './EmgAcctPicker.vue'
import { reportEmergency } from '../api/emergency'
import { errorMessage, errorStatus } from '../api/errors'
import { useToast } from '../composables/useToast'
import type { EmgDetail, EmgPickedAcct, EmgReportPayload } from '../types/emergency'
import { EMG_REPORT_LABEL } from '../utils/labels'

const DESCR_MAX = 500
const MSG_SUB = '回報只能一次，送出後不能修改；主管處理後就不能再回報。'
const MSG_TYPE = '請選擇回報結果'
const MSG_ACCT = '請加入至少一個受影響帳號'
const MSG_DESCR = '請填寫回報說明'
const MSG_DESCR_LEN = `回報說明不可超過 ${DESCR_MAX} 字`
const MSG_ACCT_DESCR_LEN = `帳號異動說明不可超過 ${DESCR_MAX} 字`

const props = defineProps<{ emg: EmgDetail }>()
/** done：回報成功或 409（狀態已變），呼叫端關閉本彈窗並重載明細 */
const emit = defineEmits<{ close: []; done: [] }>()
const { toast } = useToast()

const typeCode = ref<'' | 'NONE' | 'MODIFIED'>('')
const descr = ref('')
/** 切到「沒有異動」時保留已加入的帳號（切回來不必重選），送出時不帶 */
const picked = ref<EmgPickedAcct[]>([])
const busy = ref(false)

/** 字數按字元（碼點）算、去頭尾空白，與後端一致 */
const descrCount = computed(() => [...descr.value.trim()].length)

function close(): void {
  if (busy.value) return
  emit('close')
}

/** 前端先擋，順序同後端：回報結果 → 帳號 → 說明必填 → 字數 */
function validate(): string {
  if (!typeCode.value) return MSG_TYPE
  const modified = typeCode.value === 'MODIFIED'
  if (modified && picked.value.length === 0) return MSG_ACCT
  if (modified && !descr.value.trim()) return MSG_DESCR
  if (descrCount.value > DESCR_MAX) return MSG_DESCR_LEN
  if (modified && picked.value.some(p => [...p.affectDescr.trim()].length > DESCR_MAX)) return MSG_ACCT_DESCR_LEN
  return ''
}

async function submit(): Promise<void> {
  if (busy.value) return
  const msg = validate()
  if (msg) {
    toast(msg, 'amber')
    return
  }
  const type = typeCode.value as 'NONE' | 'MODIFIED'
  const payload: EmgReportPayload = {
    reportTypeCode: type,
    reportDescr: descr.value.trim(),
    versionNo: props.emg.versionNo,
    affectAccts: type === 'MODIFIED'
      ? picked.value.map(p => ({ acctId: p.acctId, acctName: p.acctName, affectDescr: p.affectDescr.trim() }))
      : []
  }
  busy.value = true
  try {
    await reportEmergency(props.emg.emgId, payload)
    toast(`${props.emg.emgId} 已回報`, 'teal')
    emit('done')
  } catch (e) {
    toast(errorMessage(e, '操作失敗，請稍後再試'), 'amber')
    if (errorStatus(e) === 409) emit('done')
  } finally {
    busy.value = false
  }
}
</script>
