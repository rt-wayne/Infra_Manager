<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-06
  變更說明: 新增：申請單狀態標籤（S4 回合二）。代碼對中文名稱與顏色；未知代碼原樣顯示、灰色
-->
<template>
  <span class="pill" :class="cls">{{ label }}</span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { STATUS_LABELS, type AppStatus } from '../types/app'

const props = defineProps<{ code: string }>()

const CLASSES: Record<AppStatus, string> = {
  DRAFT: 'gray',
  IN_REVIEW: 'blue',
  APPROVED: 'teal',
  IN_EXECUTION: 'orange',
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
.teal { color: var(--teal-dark); }
.orange { color: var(--orange); }
.green { color: var(--green); }
.red { color: var(--red); }
</style>
