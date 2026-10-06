<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-06
  變更說明: 新增：分頁列（S4 回合二）。顯示「第 x / y 頁，共 n 筆」與上一頁／下一頁；載入中兩鈕都停用
-->
<template>
  <nav class="pager" aria-label="分頁">
    <button class="quiet" type="button" :disabled="busy || page <= 1" @click="emit('change', page - 1)">上一頁</button>
    <span class="info">第 {{ page }} / {{ pages }} 頁，共 {{ total }} 筆</span>
    <button class="quiet" type="button" :disabled="busy || page >= pages" @click="emit('change', page + 1)">下一頁</button>
  </nav>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ page: number; size: number; total: number; busy?: boolean }>()
const emit = defineEmits<{ change: [page: number] }>()

const pages = computed(() => Math.max(1, Math.ceil(props.total / Math.max(1, props.size))))
</script>

<style scoped>
.pager { display: flex; align-items: center; justify-content: center; gap: 16px; margin-top: 14px; flex-wrap: wrap; }
.info { color: var(--gray); font-size: 17px; }
.quiet { background: #fff; border: 1px solid var(--line); color: var(--dark); border-radius: 7px; padding: 8px 18px; font-size: 17px; cursor: pointer; }
.quiet:disabled { opacity: .55; cursor: default; }
</style>
