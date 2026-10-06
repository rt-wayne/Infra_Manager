<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-06
  變更說明: 新增：申請單狀態標籤（S4 回合二）。代碼對中文名稱與顏色；未知代碼原樣顯示、灰色
            S4 回合三：顏色改比照舊系統（已核准、已結案綠；簽核中、執行中藍；待治理審核橘；已退件紅）
-->
<template>
  <span class="pill" :class="cls">{{ label }}</span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { STATUS_LABELS, type AppStatus } from '../types/app'

const props = defineProps<{ code: string }>()

/** 顏色比照舊系統 status-pill.ejs（草稿原為紫色，本站色票沒有紫色，用灰色） */
const CLASSES: Record<AppStatus, string> = {
  DRAFT: 'gray',
  IN_REVIEW: 'blue',
  APPROVED: 'green',
  IN_EXECUTION: 'blue',
  PENDING_REVIEW: 'orange',
  EXECUTED: 'green',
  REJECTED: 'red'
}

function known(code: string): code is AppStatus {
  return code in STATUS_LABELS
}

const label = computed(() => (known(props.code) ? STATUS_LABELS[props.code] : props.code))
const cls = computed(() => (known(props.code) ? CLASSES[props.code] : 'gray'))
</script>

<style scoped>
.pill { display: inline-block; padding: 1px 10px; border-radius: 999px; font-size: 15px; font-weight: 700;
  white-space: nowrap; border: 1px solid currentColor; }
.gray { color: var(--gray); }
.blue { color: var(--blue); }
.orange { color: var(--orange); }
.green { color: var(--green); }
.red { color: var(--red); }
</style>
