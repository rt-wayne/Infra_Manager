<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: 新增：toast 顯示區（README 3.4：每個畫面都要放）；內容來自 useToast 的模組層共用清單
-->
<template>
  <div class="toasts">
    <div v-for="t in toasts" :key="t.id" class="toast" :class="t.cls">{{ t.msg }}</div>
  </div>
</template>

<script setup lang="ts">
import { useToast } from '../composables/useToast'

const { toasts } = useToast()
</script>
