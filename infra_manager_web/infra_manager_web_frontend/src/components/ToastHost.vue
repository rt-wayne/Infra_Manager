<!--
  AI版本  : Claude Fable 5.1 (claude-fable-5-1)
  修改日期: 2026-10-06
  變更說明: 新增：toast 顯示區（S2 回合三）。原本寫在 HomeView 裡，登入頁與改密碼頁也要用，抽成共用元件；
            樣式沿用 HomeView 的寫法（navy 底、teal／amber／red 變體）
-->
<template>
  <div id="toasts">
    <div v-for="t in toasts" :key="t.id" class="toast" :class="t.cls">{{ t.msg }}</div>
  </div>
</template>

<script setup lang="ts">
import { useToast } from '../composables/useToast'

const { toasts } = useToast()
</script>

<style scoped>
#toasts { position: fixed; top: 18px; right: 18px; display: flex; flex-direction: column; gap: 10px; z-index: 50; }
.toast { background: var(--navy); color: #fff; padding: 16px 22px; border-radius: 9px; font-size: 19px;
  box-shadow: 0 8px 20px rgba(0,0,0,.25); max-width: 460px; line-height: 1.5; }
.toast.teal { background: var(--teal-dark); }
.toast.amber { background: #92400e; }
.toast.red { background: var(--red); }
</style>
