<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: 新增：待辦簽核（開發文件 spec_im_acct_insp.md 第 6 節；GET /todos，只列登入者此刻能處理的）
            依 actionKind 顯示操作：APPROVE 核准／退件、EXEC 回報執行完畢、VERIFY 複驗通過／複驗退回；每列可查看明細
            退件、複驗退回彈窗填原因（必填）；回報執行彈窗：門禁卡新增顯示卡號必填欄
            操作失敗 toast 後端 message（例 409「此申請已由他人處理，請重新整理」）
            變更單 #1：M2 以 catgId＝DOOR 判斷門禁卡（決定卡號必填欄），刪除以類別名稱比對的後備邏輯；特權列標「特權」
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: v2.0（開發文件 spec_im_acct_insp_v2.0.md 第 4.4、4.5、4.10、8.3、8.4 節）：
            所有動作帶 versionNo（樂觀鎖），409 toast 後端 message 並重載；核准與複驗通過改為先開確認彈窗；
            回報執行彈窗：佐證附件必選（E12，副檔名／10 MB 先檢查，送出前讀檔失敗提示 U08 並擋下）、
            門禁卡新增卡號必填（E13）、執行說明選填，改 multipart 送出；
            verifyRejectCnt＞0 時回報與複驗彈窗顯示 U02；退件／複驗退回原因必填（E10）；
            明細彈窗內的按鈕依明細 actions[] 顯示，版本號以明細為準；欄位：單號、類型、標題、申請人、目前關卡、送出時間、操作
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-16
  變更說明: 必填欄位醒目提醒：退件／退回原因、門禁卡號、執行佐證附件標籤的「（必填）」改為紅色 *
-->
<template>
  <main>
    <div class="section-note">ⓘ 這裡只列出目前身分此刻可以處理的申請。</div>

    <template v-if="loaded">
      <div v-if="rows.length > 0">
        <p class="res-head">共 {{ rows.length }} 筆，第 {{ page }} / {{ totalPages }} 頁</p>
        <table class="tbl">
          <colgroup>
            <col style="width: 15%" />
            <col style="width: 11%" />
            <col />
            <col style="width: 11%" />
            <col style="width: 11%" />
            <col style="width: 11%" />
            <col style="width: 18%" />
          </colgroup>
          <thead>
            <tr><th>單號</th><th>類型</th><th>標題</th><th>申請人</th><th>目前關卡</th><th>送出時間</th><th>操作</th></tr>
          </thead>
          <tbody>
            <tr v-for="t in pageRows" :key="t.reqId" :class="{ 'priv-row': isOn(t.isPriv) }" :data-req="t.reqId">
              <td>{{ t.reqId }}</td>
              <td>
                <span class="tag" :class="kindTag[t.actionKind]">{{ kindLabel[t.actionKind] }}</span>
                <div class="type-text">{{ label(REQ_TYPE_LABEL, t.reqTypeCode) }}<span v-if="isOn(t.isPriv)" class="tag tag-priv">特權</span></div>
              </td>
              <td>{{ titleOf(t) }}</td>
              <td>{{ t.applyByName }}</td>
              <td>{{ t.stageName }}</td>
              <td>{{ t.submitDate }}</td>
              <td>
                <span class="ops">
                  <button type="button" class="btn-link" @click="detailId = t.reqId">查看明細</button>
                  <template v-if="t.actionKind === 'APPROVE'">
                    <button type="button" class="btn-link act-approve" :disabled="busy" @click="openConfirm('approve', t)">核准</button>
                    <button type="button" class="btn-link warn act-reject" :disabled="busy" @click="openReason('reject', t)">退件</button>
                  </template>
                  <button v-else-if="t.actionKind === 'EXEC'" type="button" class="btn-link act-exec" :disabled="busy" @click="openExec(t)">回報執行完畢</button>
                  <template v-else-if="t.actionKind === 'VERIFY'">
                    <button type="button" class="btn-link ok act-verify-ok" :disabled="busy" @click="openConfirm('verifyOk', t)">複驗通過</button>
                    <button type="button" class="btn-link warn act-verify-reject" :disabled="busy" @click="openReason('verifyReject', t)">複驗退回</button>
                  </template>
                </span>
              </td>
            </tr>
          </tbody>
        </table>
        <PagerBar v-model:page="page" :total-pages="totalPages" />
      </div>
      <div v-else class="empty">目前沒有待辦簽核</div>
    </template>

    <RequestDetailModal v-if="detailId" :req-id="detailId" @close="detailId = ''">
      <template #actions="{ req }">
        <template v-if="detailTodo && req">
          <button v-if="req.actions?.includes('REJECT')" type="button" class="btn btn-warn" :disabled="busy" @click="openReason('reject', withVer(detailTodo, req.versionNo))">退件</button>
          <button v-if="req.actions?.includes('APPROVE')" type="button" class="btn btn-primary" :disabled="busy" @click="openConfirm('approve', withVer(detailTodo, req.versionNo))">核准</button>
          <button v-if="req.actions?.includes('EXEC_DONE')" type="button" class="btn btn-primary" :disabled="busy" @click="openExec(withVer(detailTodo, req.versionNo))">回報執行完畢</button>
          <button v-if="req.actions?.includes('VERIFY_REJECT')" type="button" class="btn btn-warn" :disabled="busy" @click="openReason('verifyReject', withVer(detailTodo, req.versionNo))">複驗退回</button>
          <button v-if="req.actions?.includes('VERIFY_OK')" type="button" class="btn btn-primary" :disabled="busy" @click="openConfirm('verifyOk', withVer(detailTodo, req.versionNo))">複驗通過</button>
        </template>
      </template>
    </RequestDetailModal>

    <BaseModal
      v-if="confirm"
      :title="confirm.kind === 'approve' ? `核准 — ${confirm.todo.reqId}` : `複驗通過 — ${confirm.todo.reqId}`"
      :sub="titleOf(confirm.todo)"
      close-text="取消"
      @close="confirm = null"
    >
      <div v-if="confirm.kind === 'verifyOk' && confirm.todo.verifyRejectCnt > 0" class="note-box warn verify-hint">⚠️ {{ verifyRejectHint(confirm.todo.verifyRejectReason) }}</div>
      <p class="confirm-text">{{ confirm.kind === 'approve' ? `確認以「${confirm.todo.stageName}」核准此申請？` : '確認已核對執行佐證，複驗通過並結案？' }}</p>
      <template #actions>
        <button type="button" class="btn btn-primary confirm-ok" :disabled="busy" @click="confirmOk">
          {{ confirm.kind === 'approve' ? '確認核准' : '確認複驗通過' }}
        </button>
      </template>
    </BaseModal>

    <BaseModal
      v-if="reason"
      :title="reason.kind === 'reject' ? `退件 — ${reason.todo.reqId}` : `複驗退回 — ${reason.todo.reqId}`"
      :sub="reason.kind === 'reject' ? '請填寫退件原因，將通知申請人修正後重新送出。' : '請填寫退回原因，案件將退回執行人重新處理。'"
      close-text="取消"
      @close="reason = null"
    >
      <div v-if="reason.kind === 'verifyReject' && reason.todo.verifyRejectCnt > 0" class="note-box warn verify-hint">⚠️ {{ verifyRejectHint(reason.todo.verifyRejectReason) }}</div>
      <div class="field">
        <label for="reason-text">{{ reason.kind === 'reject' ? '退件原因' : '退回原因' }}<span class="req-mark" title="必填">*</span></label>
        <textarea id="reason-text" v-model="reasonText" rows="3" :placeholder="reason.kind === 'reject' ? '請填寫退件原因' : '請填寫退回原因'"></textarea>
      </div>
      <template #actions>
        <button type="button" class="btn btn-primary confirm-reason" :disabled="busy" @click="confirmReason">
          {{ reason.kind === 'reject' ? '確認退件' : '確認退回' }}
        </button>
      </template>
    </BaseModal>

    <BaseModal v-if="exec" title="回報執行完畢" :sub="`單號：${exec.reqId}　·　${titleOf(exec)}`" close-text="取消" @close="exec = null">
      <div v-if="exec.verifyRejectCnt > 0" class="note-box warn verify-hint">⚠️ {{ verifyRejectHint(exec.verifyRejectReason) }}</div>
      <template v-if="execNeedsCard">
        <div class="note-box">ⓘ 請實際持使用人識別證刷過卡鐘讀卡機，讀出卡鐘顯示的卡號後填入下方，卡號將成為此帳號的正式識別。</div>
        <div class="field exec-card">
          <label for="exec-card">門禁卡號<span class="req-mark" title="必填">*</span></label>
          <input id="exec-card" v-model="execCardNo" type="text" placeholder="請填寫卡鐘顯示的卡號" autocomplete="off" />
        </div>
      </template>
      <div v-else-if="execIsDoor" class="note-box">ⓘ 此為既有門禁卡的{{ label(REQ_TYPE_LABEL, exec.reqTypeCode) }}操作，不需要重新刷卡取號；請附上門禁系統操作截圖作為佐證。</div>
      <div class="field">
        <label for="exec-file">執行佐證附件<span class="req-mark" title="必填">*</span>（限 .pdf .doc .docx .jpg .png，10 MB 以內）</label>
        <input id="exec-file" ref="execFileInput" type="file" :accept="ATTACH_ACCEPT" @change="onExecFile" />
        <div v-if="execFile" class="hint exec-file-name">已選擇：{{ execFile.name }}（{{ fileSizeText(execFile.size) }}）</div>
      </div>
      <div class="field">
        <label for="exec-note">執行說明（選填）</label>
        <textarea id="exec-note" v-model="execNote" rows="2" placeholder="可補充實際執行的操作內容"></textarea>
      </div>
      <template #actions>
        <button type="button" class="btn btn-primary confirm-exec" :disabled="busy" @click="confirmExec">確認回報執行完畢</button>
      </template>
    </BaseModal>

    <ToastHost />
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import BaseModal from '../components/BaseModal.vue'
import PagerBar from '../components/PagerBar.vue'
import RequestDetailModal from '../components/RequestDetailModal.vue'
import ToastHost from '../components/ToastHost.vue'
import { listTodos } from '../api/common'
import { errorMessage, errorStatus } from '../api/errors'
import { approveRequest, execDone, rejectRequest, verifyOk, verifyReject } from '../api/request'
import { usePaging } from '../composables/usePaging'
import { useSession } from '../composables/useSession'
import { useToast } from '../composables/useToast'
import type { ActionKind, Todo } from '../types/common'
import { ATTACH_ACCEPT, MSG_PROOF_READ_FAIL, checkAttachFile, fileSizeText, readAttachFile } from '../utils/attach'
import { DOOR_CATG_ID, REQ_TYPE_LABEL, isOn, label, verifyRejectHint } from '../utils/labels'

const MSG_REASON = '請填寫原因'
const MSG_PROOF = '請上傳執行佐證附件'
const MSG_CARD = '請填寫門禁卡號'

const { toast } = useToast()
const { refreshBadges } = useSession()

const kindLabel: Record<ActionKind, string> = { APPROVE: '待簽核', EXEC: '待執行', VERIFY: '待複驗' }
const kindTag: Record<ActionKind, string> = { APPROVE: 'tag-pending', EXEC: 'tag-warn', VERIFY: 'tag-info' }

const rows = ref<Todo[]>([])
const loaded = ref(false)
const busy = ref(false)
const { page, totalPages, pageRows } = usePaging(rows)

const detailId = ref('')
const detailTodo = computed(() => rows.value.find(t => t.reqId === detailId.value) ?? null)

const confirm = ref<{ kind: 'approve' | 'verifyOk'; todo: Todo } | null>(null)
const reason = ref<{ kind: 'reject' | 'verifyReject'; todo: Todo } | null>(null)
const reasonText = ref('')

const exec = ref<Todo | null>(null)
const execCardNo = ref('')
const execNote = ref('')
const execFile = ref<File | null>(null)
const execFileInput = ref<HTMLInputElement | null>(null)

function isDoorTodo(t: Todo): boolean {
  return t.catgId === DOOR_CATG_ID
}
const execIsDoor = computed(() => (exec.value ? isDoorTodo(exec.value) : false))
/** 只有門禁卡「新增」要刷卡取號（E13）；其他類型是對既有卡操作 */
const execNeedsCard = computed(() => execIsDoor.value && exec.value?.reqTypeCode === 'ADD')

function titleOf(t: Todo): string {
  if (t.title) return t.title
  const name = t.acctName || (isDoorTodo(t) ? '（門禁卡，待發卡）' : '—')
  return `${t.catgName} ${label(REQ_TYPE_LABEL, t.reqTypeCode)} — ${name}`
}

/** 從明細彈窗操作時以明細的版本號為準 */
function withVer(t: Todo, versionNo: number | undefined): Todo {
  return typeof versionNo === 'number' ? { ...t, versionNo } : t
}

async function load(): Promise<void> {
  try {
    const data = await listTodos()
    if (!Array.isArray(data)) throw new Error('unexpected response')
    rows.value = data
    loaded.value = true
  } catch (e) {
    // 失敗只出 toast、保留原畫面；不得顯示成「目前沒有待辦」
    toast(errorMessage(e, '查詢失敗，請稍後再試'), 'amber')
  }
}

/** 執行一個動作：成功 toast、關彈窗、重載待辦與側欄數字；失敗 toast 後端訊息，409 重載清單 */
async function run(action: () => Promise<unknown>, okMsg: string): Promise<void> {
  if (busy.value) return
  busy.value = true
  try {
    await action()
    toast(okMsg, 'teal')
    confirm.value = null
    reason.value = null
    exec.value = null
    detailId.value = ''
    await load()
    void refreshBadges()
  } catch (e) {
    toast(errorMessage(e, '操作失敗，請稍後再試'), 'amber')
    // 409＝已由他人處理、狀態或版本不符：關彈窗並重載待辦，讓畫面與後端一致
    if (errorStatus(e) === 409) {
      confirm.value = null
      reason.value = null
      exec.value = null
      detailId.value = ''
      void load()
    }
  } finally {
    busy.value = false
  }
}

function openConfirm(kind: 'approve' | 'verifyOk', t: Todo | null): void {
  if (t) confirm.value = { kind, todo: t }
}

function confirmOk(): void {
  const c = confirm.value
  if (!c) return
  const t = c.todo
  if (c.kind === 'approve') {
    void run(() => approveRequest(t.reqId, t.versionNo), `${t.reqId} 已核准，送往下一關`)
  } else {
    void run(() => verifyOk(t.reqId, t.versionNo), `${t.reqId} 已複驗通過並結案`)
  }
}

function openReason(kind: 'reject' | 'verifyReject', t: Todo | null): void {
  if (!t) return
  reasonText.value = ''
  reason.value = { kind, todo: t }
}

function confirmReason(): void {
  const r = reason.value
  if (!r) return
  const text = reasonText.value.trim()
  if (!text) {
    toast(MSG_REASON, 'amber')
    return
  }
  const t = r.todo
  if (r.kind === 'reject') {
    void run(() => rejectRequest(t.reqId, t.versionNo, text), `${t.reqId} 已退件，將通知申請人修正後重新送出`)
  } else {
    void run(() => verifyReject(t.reqId, t.versionNo, text), `${t.reqId} 複驗退回，退回執行人重新處理`)
  }
}

function clearExecFile(): void {
  execFile.value = null
  if (execFileInput.value) execFileInput.value.value = ''
}

function openExec(t: Todo | null): void {
  if (!t) return
  execCardNo.value = ''
  execNote.value = ''
  execFile.value = null
  exec.value = t
}

/** 選檔即檢查副檔名與大小；不符提示並清掉選擇 */
function onExecFile(ev: Event): void {
  const f = (ev.target as HTMLInputElement).files?.[0] ?? null
  if (!f) {
    execFile.value = null
    return
  }
  const err = checkAttachFile(f)
  if (err) {
    toast(err, 'amber')
    clearExecFile()
    return
  }
  execFile.value = f
}

async function confirmExec(): Promise<void> {
  const t = exec.value
  if (!t || busy.value) return
  const cardNo = execCardNo.value.trim()
  if (execNeedsCard.value && !cardNo) {
    toast(MSG_CARD, 'amber')
    return
  }
  const f = execFile.value
  if (!f) {
    toast(MSG_PROOF, 'amber')
    return
  }
  let proof: File
  try {
    proof = await readAttachFile(f)
  } catch {
    // D17：佐證讀取失敗 → U08 並擋下
    toast(MSG_PROOF_READ_FAIL, 'amber')
    clearExecFile()
    return
  }
  void run(
    () => execDone(t.reqId, { versionNo: t.versionNo, cardNo: execNeedsCard.value ? cardNo : '', execDescr: execNote.value.trim() }, proof),
    `${t.reqId} 已回報執行完畢，待資訊治理複驗`
  )
}

onMounted(() => {
  void load()
})
</script>

<style scoped>
.type-text { font-size: 16px; margin-top: 2px; display: flex; flex-wrap: wrap; gap: 4px; align-items: center; }
.type-text .tag { font-size: 14px; }
.confirm-text { margin: 4px 0 0; }
</style>
