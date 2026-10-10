<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: 新增：帳號操作按鈕列（原型 buildLookupOpsHtml）：查看、歷史＋可發起的異動
            異動按鈕帶 query（reqType、acctId）進申請表單 /requests/new
            變更單 #1：S1 加 showView（預設 true）；false 時不顯示「查看」「歷史」，異動按鈕照舊
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: v2.0（開發文件 spec_im_acct_insp_v2.0.md 第 2.4、8.3 節）：異動按鈕另帶 acctName（lookup/{id} 精確相符用）
-->
<template>
  <span class="ops">
    <template v-if="showView">
      <button type="button" class="btn-link op-view" @click="emit('view')">查看</button>
      <button type="button" class="btn-link op-history" @click="emit('history')">歷史</button>
    </template>
    <button
      v-for="t in ops"
      :key="t"
      type="button"
      class="btn-link"
      :class="[`op-${t.toLowerCase()}`, { warn: t === 'DISABLE' || t === 'DELETE', ok: t === 'ENABLE' }]"
      @click="go(t)"
    >{{ REQ_TYPE_LABEL[t] }}</button>
  </span>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import type { ReqTypeCode } from '../types/account'
import { REQ_TYPE_LABEL } from '../utils/labels'

/** showView：是否顯示「查看」「歷史」；acctName：帶給表單做 lookup/{id} 精確相符 */
const props = withDefaults(defineProps<{ acctId: string; acctName?: string | null; ops: ReqTypeCode[]; showView?: boolean }>(), {
  acctName: '',
  showView: true
})
const emit = defineEmits<{ view: []; history: [] }>()
const router = useRouter()

function go(t: ReqTypeCode): void {
  const query: Record<string, string> = { reqType: t, acctId: props.acctId }
  if (props.acctName) query.acctName = props.acctName
  void router.push({ path: '/requests/new', query })
}
</script>
