<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: 新增：歷史軌跡表格（原型 openHistoryModal：時間、動作、簽核/操作人、結果）；帳號歷史與申請單明細共用
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: v2.0（開發文件 spec_im_acct_insp_v2.0.md 第 4.8、8.4 節）：操作人顯示快照姓名，切換身分時
            「{實際}（以 {代理} 身分）」；IS_SELF_ACTION＝1 顯示 U12 標籤（自行核准／同人兼任）
-->
<template>
  <table class="tbl hist">
    <colgroup>
      <col style="width: 20%" />
      <col style="width: 21%" />
      <col style="width: 22%" />
      <col />
    </colgroup>
    <thead>
      <tr><th>時間</th><th>動作</th><th>操作人</th><th>結果</th></tr>
    </thead>
    <tbody>
      <tr v-for="h in rows" :key="h.histId" :data-hist="h.histId">
        <td>{{ h.actDate }}</td>
        <td>
          {{ label(HIST_ACTION_LABEL, h.actionCode) }}<span v-if="h.stageName" class="hint">（{{ h.stageName }}）</span>
          <div v-if="isOn(h.isSelfAction)"><span class="tag tag-priv self-action">{{ h.selfActionLabel || '自行核准／同人兼任' }}</span></div>
        </td>
        <td class="actor">{{ histActorText(h) }}</td>
        <td>{{ h.resultDescr || '—' }}</td>
      </tr>
      <tr v-if="rows.length === 0">
        <td colspan="4" class="empty-cell">目前沒有歷史紀錄</td>
      </tr>
    </tbody>
  </table>
</template>

<script setup lang="ts">
import type { AccountHist } from '../types/account'
import { HIST_ACTION_LABEL, histActorText, isOn, label } from '../utils/labels'

defineProps<{ rows: AccountHist[] }>()
</script>

<style scoped>
.hist { font-size: 17px; }
.self-action { font-size: 15px; }
</style>
