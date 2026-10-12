<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: 新增：申請單明細彈窗（原型 openDetailModal；GET /requests/{id}）：申請內容、執行回報、複驗退回提示、歷史
            該關操作按鈕由呼叫端放 actions slot（待辦）；載入失敗只 toast
            變更單 #1：M3 移除「附件（公文）」列（第一階段附件只記在歷史文字）
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: v2.0（開發文件 spec_im_acct_insp_v2.0.md 第 4.10、8.4 節）：目前狀態顯示 U10「審核中：待 ○○」；
            關卡 6、7 且 verifyRejectCnt＞0 顯示 U02；附件清單（公文／執行佐證）可下載（blob，帶 Authorization）；
            停用／刪除不顯示權限範圍與期限；actions slot 傳出 req（含 versionNo、actions[]），
            呼叫端沒給按鈕時依 actions[] 顯示：RESUBMIT → 重新送出；其他簽核動作 → 前往待辦簽核
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-12
  變更說明: BACKLOG 第 43 項：.pdf .jpg .png 的檔名可點、以新分頁開啟；.doc .docx 檔名維持文字；每列保留「下載」鈕；
            新分頁被瀏覽器擋下時提示改用下載；開啟或下載進行中，該列檔名與下載鈕都停用（busyAttachIds 各列各自鎖定）
-->
<template>
  <BaseModal :title="`申請明細 — ${reqId}`" sub="簽核前的完整申請內容，作為核准／退件的判斷依據。" wide @close="emit('close')">
    <p v-if="loading" class="hint">載入中…</p>
    <template v-else-if="req">
      <div v-if="showVerifyHint" class="note-box warn verify-hint">⚠️ {{ verifyRejectHint(req.verifyRejectReason) }}</div>
      <div v-if="req.reqStateCode === 'REJECTED' && req.rejectReason" class="note-box warn">退件原因：<b>{{ req.rejectReason }}</b></div>
      <div class="kv"><div class="k">申請單位／申請人</div><div class="v">{{ req.applyDept || '（未填寫）' }}　·　{{ req.applyByName || req.applyBy }}</div></div>
      <div class="kv"><div class="k">類別／申請類型</div><div class="v">{{ catgNameOf(codes, req.catgId, req.catgName) }}{{ isOn(req.isPriv) ? '（特權）' : '' }}　·　{{ label(REQ_TYPE_LABEL, req.reqTypeCode) }}</div></div>
      <div class="kv"><div class="k">帳號名稱／所屬系統設備</div><div class="v">{{ req.acctName || (isDoor ? '（門禁卡，待發卡）' : '—') }}　·　{{ systemText }}</div></div>
      <div class="kv"><div class="k">資產分類碼</div><div class="v">{{ describeAsset(codes, req.assetClassId) }}</div></div>
      <div class="kv"><div class="k">實際使用人／所屬單位</div><div class="v">{{ holderText }}</div></div>
      <div class="kv"><div class="k">權限範圍／存取對象</div><div class="v">{{ scopeText }}</div></div>
      <div class="kv"><div class="k">{{ reasonFieldName(req.reqTypeCode) }}</div><div class="v">{{ req.applyReason || '（未填寫）' }}</div></div>
      <div v-if="req.execDescr || req.cardNo || req.execByName" class="kv"><div class="k">執行回報</div><div class="v">{{ execText }}</div></div>
      <div class="kv"><div class="k">有效期限</div><div class="v">{{ expiryText }}</div></div>
      <div class="kv"><div class="k">申請日期／最近送出</div><div class="v">{{ dateOnly(req.applyDate) || '—' }}　·　{{ req.submitDate || '—' }}</div></div>
      <div class="kv"><div class="k">目前狀態</div><div class="v req-state">{{ reqStateText(req.reqStateCode, req.stageName, req.currStageId) }}</div></div>

      <div class="divider-lbl">附件</div>
      <div v-if="(req.attachments ?? []).length === 0" class="hint attach-none">無附件</div>
      <div v-for="a in req.attachments ?? []" :key="a.attachId" class="attach-row" :data-attach="a.attachId">
        <span class="tag tag-cat">{{ label(ATTACH_TYPE_LABEL, a.attachTypeCode) }}</span>
        <button
          v-if="inlineMimeOf(a.fileName)"
          type="button"
          class="btn-link attach-name attach-open"
          title="在新分頁開啟"
          :disabled="busyAttachIds.has(a.attachId)"
          @click="viewAttachment(a)"
        >{{ a.fileName }}</button>
        <span v-else class="attach-name">{{ a.fileName }}</span>
        <span class="hint">{{ fileSizeText(a.fileSize) }}　·　{{ a.createDate }}{{ a.createByName ? `　·　${a.createByName}` : '' }}</span>
        <button type="button" class="btn-link attach-dl" :disabled="busyAttachIds.has(a.attachId)" @click="download(a)">下載</button>
      </div>

      <template v-if="req.history">
        <div class="divider-lbl">本單事件</div>
        <HistoryTable :rows="req.history" />
      </template>
    </template>
    <template #actions>
      <slot name="actions" :req="req">
        <RouterLink
          v-if="req?.actions?.includes('RESUBMIT')"
          class="btn btn-warn act-resubmit"
          :to="{ path: '/requests/new', query: { resubmit: req.reqId } }"
        >重新送出</RouterLink>
        <RouterLink v-if="hasFlowAction" class="btn btn-primary act-todo" to="/todo">前往待辦簽核處理</RouterLink>
      </slot>
    </template>
  </BaseModal>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import BaseModal from './BaseModal.vue'
import HistoryTable from './HistoryTable.vue'
import { MSG_POPUP_BLOCKED, downloadAttachment, openAttachment } from '../api/attachment'
import { errorMessage } from '../api/errors'
import { getRequest } from '../api/request'
import { useSession } from '../composables/useSession'
import { useToast } from '../composables/useToast'
import type { Attachment, RequestDetail } from '../types/request'
import { fileSizeText, inlineMimeOf } from '../utils/attach'
import { catgNameOf, describeAsset } from '../utils/codes'
import { dateOnly } from '../utils/date'
import {
  ACCESS_LEVEL_LABEL,
  ATTACH_TYPE_LABEL,
  DOOR_CATG_ID,
  DOOR_SCOPE_LABEL,
  REQ_TYPE_LABEL,
  isOn,
  label,
  reqStateText,
  verifyRejectHint
} from '../utils/labels'
import { isReasonOnly, reasonFieldName } from '../utils/requestForm'

const props = defineProps<{ reqId: string }>()
const emit = defineEmits<{ close: [] }>()
const { toast } = useToast()
const { codes, ensureCodes } = useSession()

const req = ref<RequestDetail | null>(null)
const loading = ref(true)
/** 開啟或下載進行中的附件；各列各自鎖定，一列完成不會解鎖另一列 */
const busyAttachIds = ref(new Set<number>())

const isDoor = computed(() => req.value?.catgId === DOOR_CATG_ID)
const noScope = computed(() => (req.value ? isReasonOnly(req.value.reqTypeCode) : false))
/** U02：關卡 6、7 且曾被複驗退回 */
const showVerifyHint = computed(() => {
  const r = req.value
  return !!r && (r.currStageId === 6 || r.currStageId === 7) && (r.verifyRejectCnt ?? 0) > 0
})
const hasFlowAction = computed(() =>
  (req.value?.actions ?? []).some(a => a === 'APPROVE' || a === 'REJECT' || a === 'EXEC_DONE' || a === 'VERIFY_OK' || a === 'VERIFY_REJECT')
)
const systemText = computed(() => {
  const r = req.value
  if (!r) return ''
  if (isDoor.value) return `機房門禁管制系統（${label(DOOR_SCOPE_LABEL, r.doorScopeCode)}）`
  return r.sysName || '—'
})
const holderText = computed(() => {
  const r = req.value
  if (!r) return ''
  const parts = [r.holderName || '—', r.holderDept || '—']
  if (r.holderCntct) parts.push(`聯絡：${r.holderCntct}`)
  if (r.holderEmail) parts.push(`Email：${r.holderEmail}`)
  return parts.join('　·　')
})
const scopeText = computed(() => {
  const r = req.value
  if (!r) return ''
  if (noScope.value) return `不適用（${label(REQ_TYPE_LABEL, r.reqTypeCode)}申請）`
  return `${label(ACCESS_LEVEL_LABEL, r.accessLevelCode)}　·　${isDoor.value ? label(DOOR_SCOPE_LABEL, r.doorScopeCode) : r.accessTarget || '—'}`
})
const expiryText = computed(() => {
  const r = req.value
  if (!r) return ''
  if (noScope.value) return `不適用（${label(REQ_TYPE_LABEL, r.reqTypeCode)}申請）`
  return r.expiryTypeCode === 'DATE' ? dateOnly(r.expiryDate) || '（未填寫到期日）' : '永久有效'
})
const execText = computed(() => {
  const r = req.value
  if (!r) return ''
  const parts: string[] = []
  if (r.execByName) parts.push(`執行人：${r.execByName}${r.execDate ? `（${r.execDate}）` : ''}`)
  if (r.cardNo) parts.push(`卡號：${r.cardNo}`)
  if (r.execDescr) parts.push(`執行說明：${r.execDescr}`)
  return parts.join('　·　')
})

async function download(a: Attachment): Promise<void> {
  busyAttachIds.value.add(a.attachId)
  try {
    await downloadAttachment(a.attachId, a.fileName)
  } catch (e) {
    toast(errorMessage(e, '下載失敗，請稍後再試'), 'amber')
  } finally {
    busyAttachIds.value.delete(a.attachId)
  }
}

/** 新分頁開啟；openAttachment 須在點擊當下同步呼叫（先開空白分頁），不可先 await 其他東西 */
async function viewAttachment(a: Attachment): Promise<void> {
  busyAttachIds.value.add(a.attachId)
  try {
    if (!(await openAttachment(a.attachId, a.fileName))) {
      toast(MSG_POPUP_BLOCKED, 'amber')
    }
  } catch (e) {
    toast(errorMessage(e, '開啟失敗，請稍後再試'), 'amber')
  } finally {
    busyAttachIds.value.delete(a.attachId)
  }
}

onMounted(async () => {
  ensureCodes().catch(() => undefined)
  try {
    req.value = await getRequest(props.reqId)
  } catch (e) {
    toast(errorMessage(e, '查詢失敗，請稍後再試'), 'amber')
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.attach-row { display: flex; align-items: center; gap: 4px 10px; flex-wrap: wrap; padding: 4px 0; border-bottom: 1px dashed var(--line); }
.attach-name { font-weight: 700; overflow-wrap: anywhere; min-width: 0; }
.attach-open { padding: 0; text-align: left; }
.attach-row .hint { margin-top: 0; }
a.btn { display: inline-flex; align-items: center; min-height: 48px; text-decoration: none; }
</style>
