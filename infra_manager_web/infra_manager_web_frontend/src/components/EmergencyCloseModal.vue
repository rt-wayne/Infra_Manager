<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-13
  變更說明: 新增：緊急存取異常結案彈窗（BACKLOG 第 46 項 S6 回合 2；POST /emergencies/{id}/close；S6-④ 不與回報彈窗合併）
            由詳情彈窗開啟（疊在其上），只給 EMG_DECIDE 對已退回且尚未結案的單。結案發現二選一：確認沒有帳號異動（不帶帳號）／
            有帳號異動（以帳號選取器加入 1～20 個帳號、各帳號說明選填）；結案說明兩種都必填（S6-①）。字數按碼點、去頭尾空白，各 500 字。
            結案只能一次，彈窗副標寫明有異動時會寫帳號歷史並標審查異常；不另開確認框。
            成功 toast 後 emit done；409（已結案或版本不符）toast 後同樣 emit done，由詳情彈窗關閉本彈窗並重載。
            送出中忽略關閉（同回報彈窗：避免卸載後 emit 被丟掉）
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-16
  變更說明: 必填欄位醒目提醒：結案發現的「（必選）」、結案說明的「必填」改為紅色 *；有異動時帳號選取器上方加「異動的帳號 *」
-->
<template>
  <BaseModal :title="`異常結案 — ${emg.emgId}`" :sub="MSG_SUB" wide close-text="取消" @close="close">
    <div class="field">
      <label>結案發現<span class="req-mark" title="必填">*</span></label>
      <div class="radio-row">
        <label><input v-model="resultCode" type="radio" name="emg-close-result" value="NO_CHANGE" class="result-no-change" /> {{ EMG_CLOSE_RESULT_LABEL.NO_CHANGE }}</label>
        <label><input v-model="resultCode" type="radio" name="emg-close-result" value="CHANGED" class="result-changed" /> {{ EMG_CLOSE_RESULT_LABEL.CHANGED }}</label>
      </div>
    </div>

    <div class="field">
      <label for="emg-close-descr">結案說明<span class="req-mark" title="必填">*</span>（{{ DESCR_MAX }} 字內）</label>
      <textarea id="emg-close-descr" v-model="descr" rows="3" :placeholder="descrPlaceholder"></textarea>
      <div class="hint" :class="{ warn: descrCount > DESCR_MAX }">{{ descrCount }} / {{ DESCR_MAX }}</div>
    </div>

    <div v-if="resultCode === 'CHANGED'" class="field picker-field">
      <label>異動的帳號<span class="req-mark" title="必填">*</span>（1～20 個）</label>
      <EmgAcctPicker v-model="picked" />
    </div>

    <template #actions>
      <button type="button" class="btn btn-primary confirm-close" :disabled="busy" @click="submit">{{ busy ? '送出中…' : '確認結案' }}</button>
    </template>
  </BaseModal>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import BaseModal from './BaseModal.vue'
import EmgAcctPicker from './EmgAcctPicker.vue'
import { closeEmergency } from '../api/emergency'
import { errorMessage, errorStatus } from '../api/errors'
import { useToast } from '../composables/useToast'
import type { EmgClosePayload, EmgDetail, EmgPickedAcct } from '../types/emergency'
import { EMG_CLOSE_RESULT_LABEL } from '../utils/labels'

const DESCR_MAX = 500
const MSG_SUB = '結案只能一次，送出後不能修改；有帳號異動時，各帳號會寫入歷史並標記審查異常。'
const MSG_RESULT = '請選擇結案發現'
const MSG_ACCT = '請加入至少一個受影響帳號'
const MSG_DESCR = '請填寫結案說明'
const MSG_DESCR_LEN = `結案說明不可超過 ${DESCR_MAX} 字`
const MSG_ACCT_DESCR_LEN = `帳號異動說明不可超過 ${DESCR_MAX} 字`

const props = defineProps<{ emg: EmgDetail }>()
/** done：結案成功或 409（狀態已變），呼叫端關閉本彈窗並重載明細 */
const emit = defineEmits<{ close: []; done: [] }>()
const { toast } = useToast()

const resultCode = ref<'' | 'NO_CHANGE' | 'CHANGED'>('')
const descr = ref('')
/** 切到「沒有異動」時保留已加入的帳號（切回來不必重選），送出時不帶 */
const picked = ref<EmgPickedAcct[]>([])
const busy = ref(false)

/** 字數按字元（碼點）算、去頭尾空白，與後端一致 */
const descrCount = computed(() => [...descr.value.trim()].length)
/** 提示文字隨結案發現變；未選時不偏向任一種，免得主管誤以為預設是「沒有異動」 */
const descrPlaceholder = computed(() => {
  if (resultCode.value === 'CHANGED') return '請說明發現了哪些異動'
  if (resultCode.value === 'NO_CHANGE') return '請說明如何確認沒有異動'
  return '請先選擇結案發現，再說明確認經過'
})

function close(): void {
  if (busy.value) return
  emit('close')
}

/** 前端先擋，順序同後端：結案發現 → 帳號 → 說明必填 → 字數 */
function validate(): string {
  if (!resultCode.value) return MSG_RESULT
  const changed = resultCode.value === 'CHANGED'
  if (changed && picked.value.length === 0) return MSG_ACCT
  if (!descr.value.trim()) return MSG_DESCR
  if (descrCount.value > DESCR_MAX) return MSG_DESCR_LEN
  if (changed && picked.value.some(p => [...p.affectDescr.trim()].length > DESCR_MAX)) return MSG_ACCT_DESCR_LEN
  return ''
}

async function submit(): Promise<void> {
  if (busy.value) return
  const msg = validate()
  if (msg) {
    toast(msg, 'amber')
    return
  }
  const code = resultCode.value as 'NO_CHANGE' | 'CHANGED'
  const payload: EmgClosePayload = {
    closeResultCode: code,
    closeDescr: descr.value.trim(),
    versionNo: props.emg.versionNo,
    affectAccts: code === 'CHANGED'
      ? picked.value.map(p => ({ acctId: p.acctId, acctName: p.acctName, affectDescr: p.affectDescr.trim() }))
      : []
  }
  busy.value = true
  try {
    await closeEmergency(props.emg.emgId, payload)
    toast(`${props.emg.emgId} 已異常結案`, 'teal')
    emit('done')
  } catch (e) {
    toast(errorMessage(e, '操作失敗，請稍後再試'), 'amber')
    if (errorStatus(e) === 409) emit('done')
  } finally {
    busy.value = false
  }
}
</script>
