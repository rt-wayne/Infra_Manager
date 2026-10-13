<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-13
  變更說明: 新增：緊急存取唯讀詳情彈窗（BACKLOG 第 46 項 S3，裁示 ⑫；GET /emergencies/{id}）
            申請內容全部欄位；「申請人回報」「主管處理」「異常結案」三段有值才顯示內容，沒值顯示「尚未…」
            （異常結案段只在已退回時出現）；自核、自行結案、逾期後處理加標記（裁示⑤、㉖、D14）；
            受影響帳號有資料才列表。回報、處理、結案按鈕留給 S5／S6；查無或無權看（404）只 toast
-->
<template>
  <BaseModal :title="`緊急存取 — ${emgId}`" :sub="subText" wide @close="emit('close')">
    <p v-if="loading" class="hint">載入中…</p>
    <template v-else-if="d">
      <div class="grid2">
        <div class="kv"><div class="k">帳號類別</div><div class="v">{{ d.catgName || d.catgId }}</div></div>
        <div class="kv"><div class="k">申請人</div><div class="v">{{ d.applyByName || d.applyBy }}</div></div>
        <div class="kv"><div class="k">送出時間（生效）</div><div class="v">{{ toMinute(d.submitDate) }}</div></div>
        <div class="kv"><div class="k">補核准期限</div><div class="v">{{ toMinute(d.dueDate) }}</div></div>
        <div class="kv"><div class="k">預估時長</div><div class="v">{{ d.expectHourQty }} 小時</div></div>
      </div>
      <div class="kv"><div class="k">帳號或系統</div><div class="v">{{ d.targetDescr }}</div></div>
      <div class="kv"><div class="k">緊急事由</div><div class="v pre">{{ d.applyReason }}</div></div>

      <div class="divider-lbl">申請人回報</div>
      <template v-if="d.reportTypeCode">
        <div class="kv sec-report">
          <div class="k">{{ toMinute(d.reportDate) }}</div>
          <div class="v pre"><b>{{ label(EMG_REPORT_LABEL, d.reportTypeCode) }}</b>{{ d.reportDescr ? `：${d.reportDescr}` : '' }}</div>
        </div>
      </template>
      <p v-else class="hint sec-report">尚未回報</p>

      <div class="divider-lbl">主管處理</div>
      <template v-if="d.decideCode">
        <div class="kv sec-decide">
          <div class="k">
            {{ toMinute(d.decideDate) }}　{{ d.decideByName || d.decideBy }}
            <span v-if="isOn(d.isSelfDecide)" class="tag tag-warn self-decide">自核</span>
            <span v-if="decidedLate" class="tag tag-priv late">逾期後處理</span>
          </div>
          <div class="v pre"><b>{{ label(EMG_DECIDE_LABEL, d.decideCode) }}</b>{{ d.returnReason ? `：${d.returnReason}` : '' }}</div>
        </div>
      </template>
      <p v-else class="hint sec-decide">尚未處理</p>

      <template v-if="d.decideCode === 'RETURN'">
        <div class="divider-lbl">異常結案</div>
        <template v-if="d.closeDate">
          <div class="kv sec-close">
            <div class="k">
              {{ toMinute(d.closeDate) }}　{{ d.closeByName || d.closeBy }}
              <span v-if="isOn(d.isSelfClose)" class="tag tag-warn self-close">自行結案</span>
            </div>
            <div class="v pre"><b>{{ label(EMG_CLOSE_RESULT_LABEL, d.closeResultCode) }}</b>{{ d.closeDescr ? `：${d.closeDescr}` : '' }}</div>
          </div>
        </template>
        <p v-else class="hint sec-close">尚未結案</p>
      </template>

      <template v-if="d.affectAccts.length > 0">
        <div class="divider-lbl">受影響帳號</div>
        <table class="tbl affect-tbl">
          <thead><tr><th>來源</th><th>帳號</th><th>系統</th><th>說明</th><th>登記</th></tr></thead>
          <tbody>
            <tr v-for="a in d.affectAccts" :key="`${a.affectTypeCode}:${a.acctId}`">
              <td>{{ label(EMG_AFFECT_TYPE_LABEL, a.affectTypeCode) }}</td>
              <td>{{ a.acctName || a.acctId }}</td>
              <td>{{ a.sysName || '—' }}</td>
              <td>{{ a.affectDescr || '—' }}</td>
              <td>{{ a.recordByName || a.recordBy }}<div class="hint">{{ toMinute(a.recordDate) }}</div></td>
            </tr>
          </tbody>
        </table>
      </template>
    </template>
    <p v-else class="hint">無法載入此緊急存取單</p>
  </BaseModal>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import BaseModal from './BaseModal.vue'
import { getEmergency } from '../api/emergency'
import { errorMessage } from '../api/errors'
import { useToast } from '../composables/useToast'
import type { EmgDetail } from '../types/emergency'
import { toMinute } from '../utils/date'
import {
  EMG_AFFECT_TYPE_LABEL,
  EMG_CLOSE_RESULT_LABEL,
  EMG_DECIDE_LABEL,
  EMG_REPORT_LABEL,
  EMG_STATE_LABEL,
  isOn,
  label
} from '../utils/labels'

const props = defineProps<{ emgId: string }>()
const emit = defineEmits<{ close: [] }>()
const { toast } = useToast()

const d = ref<EmgDetail | null>(null)
const loading = ref(true)

const subText = computed(() => (d.value ? `狀態：${label(EMG_STATE_LABEL, d.value.emgStateCode)}` : ''))
/** 處理時間晚於補核准期限（D14：逾期後仍可處理，照實標示）；兩者同為 yyyy-MM-dd HH:mm:ss，可直接字串比較 */
const decidedLate = computed(() => !!d.value?.decideDate && d.value.decideDate > d.value.dueDate)

onMounted(async () => {
  try {
    const data = await getEmergency(props.emgId)
    d.value = { ...data, affectAccts: Array.isArray(data?.affectAccts) ? data.affectAccts : [] }
  } catch (e) {
    toast(errorMessage(e, '查詢失敗，請稍後再試'), 'amber')
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.pre { white-space: pre-wrap; }
.kv .k .tag { font-size: 15px; margin-left: 6px; }
.affect-tbl { font-size: 17px; }
</style>
