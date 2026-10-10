<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-05
  變更說明: 新增：前端範本根元件（規格 v4），只放 router-view；不內建殼整合（無 postMessage、無登入／選店）
            jdk25 階段 3：改 <script setup lang="ts">（本元件無邏輯）
-->
<script setup lang="ts">
// 只負責放 router-view，沒有邏輯
</script>

<template>
  <router-view />
</template>
