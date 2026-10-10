<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: 新增：沒有存取權限頁（開發文件 spec_im_acct_insp.md 第 2、6 節；原型 view-no-access）
-->
<template>
  <main>
    <div class="module-card no-access">
      <div class="lock">🔒</div>
      <h2>沒有存取權限</h2>
      <p class="hint">您目前的身分沒有這個權限，若有查詢需求請洽相關主管。</p>
      <RouterLink class="btn btn-primary" to="/">回到首頁</RouterLink>
    </div>
  </main>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>

<style scoped>
.no-access { text-align: center; padding: 56px 32px; }
.lock { font-size: 44px; margin-bottom: 8px; }
.no-access .hint { margin: 0 auto 20px; max-width: 520px; }
a.btn { display: inline-flex; align-items: center; min-height: 48px; text-decoration: none; }
</style>
