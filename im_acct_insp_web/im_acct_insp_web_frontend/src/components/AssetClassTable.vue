<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: 新增：資產分類碼表格（原型 buildAssetRefTableHtml）；資料來自 /codes 的 assetClasses（15 碼）
            與營運直接相關（isDirect＝1）標 ⭐ 並底色標示；isShow＝0 顯示「本系統未提供」
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: v2.0（開發文件 spec_im_acct_insp_v2.0.md 第 6.3、8.3 節）：審查條件碼改看 reviewTrigger（⭐＋底色）、
            不可選碼改看 selectable（「不可選用」）
-->
<template>
  <table class="tbl">
    <colgroup>
      <col style="width: 11%" />
      <col style="width: 11%" />
      <col style="width: 28%" />
      <col />
      <col style="width: 16%" />
    </colgroup>
    <thead>
      <tr><th>分類碼</th><th>資產類型</th><th>名稱</th><th>存取控制要求摘要</th><th>申請表單</th></tr>
    </thead>
    <tbody>
      <tr v-for="a in rows" :key="a.assetClassId" :class="{ direct: a.reviewTrigger }">
        <td><span class="tag tag-cat">{{ a.assetClassId }}</span></td>
        <td>{{ a.assetTypeCode === 'H' ? '硬體' : a.assetTypeCode === 'S' ? '軟體' : a.assetTypeCode }}</td>
        <td>{{ a.assetClassName }}<span v-if="a.reviewTrigger" class="star"> ⭐ 審查條件</span></td>
        <td class="descr">{{ a.ctrlDescr || '—' }}</td>
        <td>
          <span v-if="a.selectable" class="tag tag-ok">可選用</span>
          <span v-else class="tag tag-gray">不可選用</span>
        </td>
      </tr>
    </tbody>
  </table>
</template>

<script setup lang="ts">
import type { AssetClass } from '../types/common'

defineProps<{ rows: AssetClass[] }>()
</script>

<style scoped>
tr.direct td { background: #fdf3e9; }
.star { color: var(--orange); font-weight: 700; }
.descr { font-size: 17px; color: var(--gray); }
</style>
