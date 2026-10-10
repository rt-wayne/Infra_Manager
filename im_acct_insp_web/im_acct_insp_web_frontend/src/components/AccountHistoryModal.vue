<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: 新增：查看歷史彈窗（開發文件 spec_im_acct_insp.md 第 6 節；GET /accounts/{id}/history）
            載入失敗只 toast，不顯示成「目前沒有歷史紀錄」
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: v2.0（開發文件 spec_im_acct_insp_v2.0.md 第 8.1、8.4 節）：載入失敗文案改 U09；操作人顯示規則改在 HistoryTable
-->
<template>
  <BaseModal :title="`異動歷史軌跡 — ${title || acctId}`" sub="此帳號從建立以來的所有申請與簽核紀錄。" wide @close="emit('close')">
    <p v-if="loading" class="hint">載入中…</p>
    <HistoryTable v-else-if="rows" :rows="rows" />
  </BaseModal>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import BaseModal from './BaseModal.vue'
import HistoryTable from './HistoryTable.vue'
import { getAccountHistory } from '../api/account'
import { errorMessage } from '../api/errors'
import { useToast } from '../composables/useToast'
import type { AccountHist } from '../types/account'

const props = defineProps<{ acctId: string; title?: string }>()
const emit = defineEmits<{ close: [] }>()
const { toast } = useToast()

const rows = ref<AccountHist[] | null>(null)
const loading = ref(true)

onMounted(async () => {
  try {
    const data = await getAccountHistory(props.acctId)
    if (!Array.isArray(data)) throw new Error('unexpected response')
    rows.value = data
  } catch (e) {
    toast(errorMessage(e, '查詢失敗，請稍後再試'), 'amber')
  } finally {
    loading.value = false
  }
})
</script>
