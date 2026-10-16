<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: 新增：帳號明細彈窗（原型 openAccountViewModal；GET /accounts/{id}）；載入失敗只 toast
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: v2.0（開發文件 spec_im_acct_insp_v2.0.md 第 8.4 節）：主表內容加「待複驗」標記（U11）與已逾期狀態；
            非唯讀時依 allowedReqTypes 顯示異動按鈕；readonly（從鈴鐺開啟）不顯示異動按鈕
  AI版本  : Claude Fable 5 (claude-fable-5)
  修改日期: 2026-10-13
  變更說明: BACKLOG 第 46 項 S10：目前狀態加「審查異常」標籤，另列原因／標記日期／標記人（工號）
-->
<template>
  <BaseModal :title="`帳號詳情 — ${acct?.acctName || acctId}`" :sub="`單號：${acctId}`" @close="emit('close')">
    <p v-if="loading" class="hint">載入中…</p>
    <template v-else-if="acct">
      <div class="kv"><div class="k">帳號類別</div><div class="v">{{ catgNameOf(codes, acct.catgId, acct.catgName) }}{{ isOn(acct.isPriv) ? '（特權）' : '' }}</div></div>
      <div class="kv"><div class="k">所屬系統／設備</div><div class="v">{{ systemText }}</div></div>
      <div class="kv"><div class="k">資產分類碼</div><div class="v">{{ describeAsset(codes, acct.assetClassId) }}</div></div>
      <div class="kv"><div class="k">實際使用人／所屬單位</div><div class="v">{{ holderText }}</div></div>
      <div class="kv"><div class="k">權限範圍／存取對象</div><div class="v">{{ label(ACCESS_LEVEL_LABEL, acct.accessLevelCode) }}　·　{{ acct.accessTarget || '—' }}</div></div>
      <div class="kv"><div class="k">有效期限</div><div class="v">{{ acct.expiryTypeCode === 'DATE' ? dateOnly(acct.expiryDate) || '—' : '永久有效' }}</div></div>
      <div class="kv">
        <div class="k">目前狀態</div>
        <div class="v">
          <span class="tag" :class="stateTagClass(acct.acctStateCode)">{{ label(ACCT_STATE_LABEL, acct.acctStateCode) }}</span>
          <span v-if="isOn(acct.isReviewFlag)" class="tag tag-flag review-flag">審查異常</span>
          <span v-if="acct.pendingVerify" class="tag tag-info pending-verify">待複驗</span>
          <span v-else-if="acct.inFlight" class="hint">（有申請處理中）</span>
        </div>
      </div>
      <div v-if="isOn(acct.isReviewFlag)" class="kv"><div class="k">審查異常標記</div><div class="v review-flag-info">{{ reviewFlagText }}</div></div>
      <div v-if="acct.nextReviewDate" class="kv"><div class="k">定期審查</div><div class="v">上次 {{ dateOnly(acct.lastReviewDate) || '—' }}　·　下次 {{ dateOnly(acct.nextReviewDate) }}</div></div>
    </template>
    <template #actions>
      <AcctOpButtons v-if="!readonly && acct && ops.length > 0" :acct-id="acct.acctId" :acct-name="acct.acctName" :ops="ops" :show-view="false" />
    </template>
  </BaseModal>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AcctOpButtons from './AcctOpButtons.vue'
import BaseModal from './BaseModal.vue'
import { getAccount } from '../api/account'
import { errorMessage } from '../api/errors'
import { useSession } from '../composables/useSession'
import { useToast } from '../composables/useToast'
import type { Account } from '../types/account'
import { catgNameOf, describeAsset } from '../utils/codes'
import { dateOnly } from '../utils/date'
import {
  ACCESS_LEVEL_LABEL,
  ACCT_STATE_LABEL,
  DOOR_CATG_ID,
  DOOR_SCOPE_LABEL,
  accountOps,
  isOn,
  label,
  stateTagClass
} from '../utils/labels'

/** readonly：從鈴鐺開啟，不顯示異動按鈕 */
const props = withDefaults(defineProps<{ acctId: string; readonly?: boolean }>(), { readonly: false })
const emit = defineEmits<{ close: [] }>()
const { toast } = useToast()
const { codes, ensureCodes, can } = useSession()

const acct = ref<Account | null>(null)
const loading = ref(true)

const ops = computed(() => (acct.value && can('ACC_APPLY') ? accountOps(acct.value) : []))
const systemText = computed(() => {
  const a = acct.value
  if (!a) return ''
  if (a.catgId === DOOR_CATG_ID) return `機房門禁管制系統（${label(DOOR_SCOPE_LABEL, a.doorScopeCode)}）`
  return a.sysName || '—'
})
const holderText = computed(() => {
  const a = acct.value
  if (!a) return ''
  const parts = [a.holderName || '—', a.holderDept || '—']
  if (a.holderCntct) parts.push(`聯絡：${a.holderCntct}`)
  if (a.holderEmail) parts.push(`Email：${a.holderEmail}`)
  return parts.join('　·　')
})
/** S10：審查異常旗標的原因／標記日期／標記人（後端只有工號、無姓名欄） */
const reviewFlagText = computed(() => {
  const a = acct.value
  if (!a) return ''
  const parts = [`原因：${a.reviewFlagReason || '（未填）'}`]
  if (a.reviewFlagDate) parts.push(`標記日期：${dateOnly(a.reviewFlagDate)}`)
  if (a.reviewFlagBy) parts.push(`標記人：${a.reviewFlagBy}`)
  return parts.join('　·　')
})

onMounted(async () => {
  ensureCodes().catch(() => undefined)
  try {
    acct.value = await getAccount(props.acctId)
  } catch (e) {
    toast(errorMessage(e, '查詢失敗，請稍後再試'), 'amber')
  } finally {
    loading.value = false
  }
})
</script>
