<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-13
  變更說明: 新增：緊急存取受影響帳號選取器（BACKLOG 第 46 項 S5 回合 2）
            照 D29／㉑：輸入完整帳號名稱精確查詢（GET /accounts/lookup?acctName，後端限已核准／已逾期／已停用，
            不限申請人可看帳號；範圍在後端，R8）；查詢需 ACC_SELF_SERVICE，回 403 顯示「您沒有查詢帳號的權限，請聯絡管理者」（裁示②A）
            結果列「加入」：重複不加（顯示「已加入」）、滿 20 個停用（後端上限 EMG_AFFECT_MAX）；
            已加入列可填該帳號的異動說明（選填，500 字按碼點）與「移除」。以 v-model 回傳已加入清單；
            加入、移除、改說明都重建陣列交回，不直接改父層陣列元素（審查第 4 項）
-->
<template>
  <div class="emg-picker">
    <form class="lookup" @submit.prevent="doLookup">
      <input v-model="name" type="text" class="picker-input" placeholder="請輸入完整帳號名稱" aria-label="完整帳號名稱" autocomplete="off" />
      <button type="submit" class="btn btn-primary picker-search" :disabled="loading">{{ loading ? '查詢中…' : '查詢' }}</button>
    </form>
    <div class="hint picker-hint">{{ MSG_HINT }}</div>

    <div v-if="denied" class="note-box warn picker-denied">{{ MSG_DENIED }}</div>
    <template v-else-if="rows !== null">
      <div v-if="rows.length === 0" class="note-box warn picker-none">{{ MSG_NONE }}</div>
      <div v-for="a in rows" :key="a.acctId" class="kv picker-row">
        <div class="k">{{ a.catgName || a.catgId }}・{{ label(ACCT_STATE_LABEL, a.acctStateCode) }}</div>
        <div class="v row-line">
          <span>{{ a.acctName }}<span v-if="a.sysName" class="hint">（{{ a.sysName }}）</span><span v-if="a.holderName" class="hint">　{{ a.holderName }}</span></span>
          <button
            type="button"
            class="btn picker-add"
            :disabled="isPicked(a.acctId) || full || !a.acctName"
            @click="add(a)"
          >{{ isPicked(a.acctId) ? '已加入' : '加入' }}</button>
        </div>
      </div>
    </template>
    <p v-if="full" class="hint warn picker-full">{{ MSG_FULL }}</p>

    <div class="divider-lbl">已加入的帳號（{{ picked.length }} / {{ MAX }}）</div>
    <p v-if="picked.length === 0" class="hint picker-empty">尚未加入帳號</p>
    <div v-for="p in picked" :key="p.acctId" class="picked-row">
      <div class="row-line">
        <span><b>{{ p.acctName }}</b><span class="hint">　{{ p.catgName }}{{ p.sysName ? `・${p.sysName}` : '' }}・{{ label(ACCT_STATE_LABEL, p.acctStateCode) }}</span></span>
        <button type="button" class="btn picker-remove" @click="remove(p.acctId)">移除</button>
      </div>
      <input :value="p.affectDescr" type="text" class="picked-descr" @input="setDescr(p.acctId, ($event.target as HTMLInputElement).value)" :aria-label="`${p.acctName} 的異動說明`" placeholder="這個帳號做了什麼異動（選填）" autocomplete="off" />
      <div v-if="descrCount(p) > DESCR_MAX" class="hint warn picked-over">{{ descrCount(p) }} / {{ DESCR_MAX }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { lookupAccounts } from '../api/account'
import { errorMessage, errorStatus } from '../api/errors'
import { useToast } from '../composables/useToast'
import type { AccountLookup } from '../types/account'
import type { EmgPickedAcct } from '../types/emergency'
import { ACCT_STATE_LABEL, label } from '../utils/labels'

/** 受影響帳號上限，同後端 AcctConst.EMG_AFFECT_MAX */
const MAX = 20
/** 單一帳號異動說明上限，同後端 AFFECT_DESCR_MAX */
const DESCR_MAX = 500
const MSG_HINT = '請輸入完整帳號名稱（不提供模糊查詢）；只列已核准、已逾期、已停用的帳號'
const MSG_NONE = '查無此帳號，請確認輸入的是完整帳號名稱'
const MSG_DENIED = '您沒有查詢帳號的權限，請聯絡管理者'
const MSG_FULL = `最多 ${MAX} 個帳號`

const picked = defineModel<EmgPickedAcct[]>({ required: true })
const { toast } = useToast()

const name = ref('')
const rows = ref<AccountLookup[] | null>(null)
const loading = ref(false)
const denied = ref(false)

const full = computed(() => picked.value.length >= MAX)

function isPicked(acctId: string): boolean {
  return picked.value.some(p => p.acctId === acctId)
}

/** 字數按字元（碼點）算、去頭尾空白，與後端一致 */
function descrCount(p: EmgPickedAcct): number {
  return [...p.affectDescr.trim()].length
}

async function doLookup(): Promise<void> {
  const q = name.value.trim()
  if (!q) {
    toast(MSG_HINT, 'amber')
    return
  }
  loading.value = true
  try {
    const data = await lookupAccounts(q)
    if (!Array.isArray(data)) throw new Error('unexpected response')
    rows.value = data
    denied.value = false
  } catch (e) {
    if (errorStatus(e) === 403) {
      denied.value = true
    } else {
      toast(errorMessage(e, '查詢失敗，請稍後再試'), 'amber')
    }
  } finally {
    loading.value = false
  }
}

function add(a: AccountLookup): void {
  if (isPicked(a.acctId) || full.value || !a.acctName) return
  picked.value = [
    ...picked.value,
    { acctId: a.acctId, acctName: a.acctName, affectDescr: '', catgName: a.catgName || a.catgId, sysName: a.sysName, acctStateCode: a.acctStateCode }
  ]
}

function remove(acctId: string): void {
  picked.value = picked.value.filter(p => p.acctId !== acctId)
}

/** 改說明也重建陣列交回父層（不直接改父層陣列元素，只留 v-model 一條更新路徑） */
function setDescr(acctId: string, affectDescr: string): void {
  picked.value = picked.value.map(p => (p.acctId === acctId ? { ...p, affectDescr } : p))
}
</script>

<style scoped>
.lookup { display: flex; gap: 10px; }
.lookup input { flex: 1; min-width: 0; }
.picker-hint { margin: 4px 0 10px; }
.row-line { display: flex; align-items: center; justify-content: space-between; gap: 10px; flex-wrap: wrap; }
.picked-row { border-bottom: 1px solid var(--line); padding: 8px 0; }
.picked-descr { width: 100%; margin-top: 6px; }
</style>
