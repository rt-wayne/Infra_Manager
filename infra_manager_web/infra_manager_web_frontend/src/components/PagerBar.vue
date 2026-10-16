<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: 新增：分頁列（每頁 20 筆，README 3.4 寫法）；只有一頁時不顯示
-->
<template>
  <div v-if="totalPages > 1" class="pager">
    <button type="button" class="btn" :disabled="page <= 1" @click="emit('update:page', page - 1)">上一頁</button>
    <span>{{ page }} / {{ totalPages }}</span>
    <button type="button" class="btn" :disabled="page >= totalPages" @click="emit('update:page', page + 1)">下一頁</button>
  </div>
</template>

<script setup lang="ts">
defineProps<{ page: number; totalPages: number }>()
const emit = defineEmits<{ 'update:page': [value: number] }>()
</script>
