<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: 新增：彈窗外框（原型 modal-overlay）；點遮罩或「關閉」觸發 close；操作按鈕放 actions slot
-->
<template>
  <div class="modal-overlay" @click.self="emit('close')">
    <div class="modal" :class="{ wide }" role="dialog" aria-modal="true">
      <h3>{{ title }}</h3>
      <div v-if="sub" class="sub">{{ sub }}</div>
      <slot />
      <div class="actions">
        <slot name="actions" />
        <button type="button" class="btn" @click="emit('close')">{{ closeText }}</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ title: string; sub?: string; wide?: boolean; closeText?: string }>(), {
  sub: '',
  wide: false,
  closeText: '關閉'
})
const emit = defineEmits<{ close: [] }>()
</script>
