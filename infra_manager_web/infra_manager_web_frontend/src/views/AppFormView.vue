<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: 新增：申請單新增／編輯草稿表單（S6 回合四）。/apps/new 與 /apps/:id/edit 共用，區塊順序比照舊系統 views/apps/new.ejs：
            一、基本資料 二、事件分級 三、作業類別 四、異動作業內容，再接附件
            - 選項來自 GET /form-options（只有啟用的）；編輯時由 GET /apps/{id} 還原，已停用的選項自動拿掉（否則後端回「選項不正確」存不了）
            - 申請人姓名鎖定登入者（施工計畫假設 #6）；只有申請人能編輯（⑤A，依 permissions.canEditDraft）
            - 設備位置只做「不適用＋原因」或留空（⑦A），機房盤點選擇器待 S13
            - 存檔：新增 POST → 取得單號後網址換成編輯頁（之後再存走 PUT）；編輯 PUT 帶 rowVerNo（⑥A）；
              存檔成功後把待上傳附件逐檔上傳（一次一檔、顯示進度、單次 timeout 600 秒），全部成功才回檢視頁
            - 附件選檔時預檢副檔名／空檔／單檔大小／總檔數（本文送一半被 413 擋下時瀏覽器只會顯示連線錯誤）；拖放與貼上截圖留細節調整期
            - 400：後端有帶 field 就標紅該欄並捲過去（④B：不擋字數、CLOB 不用 maxlength）；409：提示並提供「重新載入」
            - 401 由登入處理器導頁，本頁不另出 toast
            S9 R3（Claude Opus 5.5，2026-10-07）：第三種模式「補件重送」（/apps/:id/resubmit，依路由名稱判斷）：
            - 只限退件單的申請人（permissions.canResubmit）；上方顯示退件資訊（退件那一關的意見，或執行／治理退回事件）
            - 多一個「補件說明」欄（選填、上限 2000 字由後端擋）；按鈕「補件並送審」先 confirm
            - 順序「先上傳附件、再補件」（裁示 ⑤A：退件單申請人可上傳；補件後就是審核中、不能再傳），
              任一檔失敗就不送補件；409 或 403 比照編輯草稿顯示「重新載入」按鈕（不自動重載，免得洗掉剛改的內容）
-->
<template>
  <main ref="rootEl">
    <header class="hero">
      <div class="head">
        <h1>{{ TITLES[mode] }} <span v-if="savedId" class="id">{{ savedId }}</span></h1>
        <p class="sub">{{ isResubmit ? '改好內容後按「補件並送審」，會成為新的版次並重新跑簽核' : '存草稿只要求標題與事件分級，其餘欄位送審時才檢查' }}</p>
      </div>
      <router-link class="link" :to="backTo">{{ savedId ? '← 回申請單' : '← 回列表' }}</router-link>
    </header>

    <p v-if="loadError" class="card error" role="alert">{{ loadError }}</p>
    <p v-else-if="loading" class="card muted">載入中…</p>

    <form v-else class="paper" novalidate @submit.prevent="save">
      <section v-if="isResubmit" class="card reject" aria-label="退件資訊">
        <h2>退件資訊</h2>
        <template v-if="reject">
          <p class="reject-who">{{ reject.label }}<span v-if="reject.by">・{{ reject.by }}</span><span v-if="reject.at" class="sub">・{{ reject.at }}</span></p>
          <p class="reject-memo">{{ reject.memo || '（沒有填寫意見）' }}</p>
        </template>
        <p v-else class="muted">（找不到退件紀錄）</p>
      </section>

      <fieldset class="card sec">
        <legend>一、基本資料</legend>
        <div class="grid">
          <div class="cell wide">
            <label for="f-title">標題 <span class="req">*</span></label>
            <input id="f-title" v-model="form.title" v-bind="fx('title')" placeholder="例：FortiADC 韌體升級" />
          </div>
          <div class="cell">
            <label>申請人</label>
            <div class="fixed">{{ applicantName || '—' }}</div>
          </div>
          <div class="cell">
            <label for="f-dept">申請單位</label>
            <input id="f-dept" v-model="form.deptName" v-bind="fx('applicant.deptName')" />
          </div>
          <div class="cell">
            <label for="f-tel">聯絡電話</label>
            <input id="f-tel" v-model="form.tel" v-bind="fx('applicant.tel')" placeholder="緊急聯繫用" />
          </div>
          <div class="cell">
            <label for="f-email">Email</label>
            <input id="f-email" v-model="form.email" type="email" v-bind="fx('applicant.email')" />
          </div>
          <div class="cell">
            <label>執行人員</label>
            <div class="checks">
              <label class="chk"><input v-model="form.selfExec" type="checkbox" /> 自行處理 (內部 IT)</label>
              <label class="chk"><input v-model="form.supplierExec" type="checkbox" /> 委外廠商</label>
            </div>
          </div>
          <div class="cell">
            <label>處理方式</label>
            <div class="checks">
              <label class="chk"><input v-model="form.workModeCode" type="radio" value="ONSITE" /> {{ WORK_MODE_LABELS.ONSITE }}</label>
              <label class="chk"><input v-model="form.workModeCode" type="radio" value="REMOTE" /> {{ WORK_MODE_LABELS.REMOTE }}</label>
            </div>
          </div>
          <div v-if="form.workModeCode === 'REMOTE'" class="cell wide">
            <label for="f-remote">遠端連線方式</label>
            <input id="f-remote" v-model="form.remoteMethod" v-bind="fx('remoteMethod')" placeholder="例：RDP / VPN+RDP / TeamViewer / Jumpbox" />
          </div>
          <template v-if="form.supplierExec">
            <div class="cell">
              <label for="f-sname">執行廠商</label>
              <input id="f-sname" v-model="form.supplierName" v-bind="fx('supplier.name')" />
            </div>
            <div class="cell">
              <label for="f-scontact">廠商聯絡人</label>
              <input id="f-scontact" v-model="form.supplierContact" v-bind="fx('supplier.contact')" />
            </div>
            <div class="cell">
              <label for="f-stel">廠商電話</label>
              <input id="f-stel" v-model="form.supplierTel" v-bind="fx('supplier.tel')" />
            </div>
            <div class="cell">
              <label for="f-head">廠商進場人數 <span class="sub">(現場處理才有意義)</span></label>
              <input id="f-head" v-model="form.headCount" type="number" min="0" max="9999" step="1" />
            </div>
          </template>
        </div>
      </fieldset>

      <fieldset class="card sec">
        <legend>二、事件分級 <span class="req">*</span> <span class="sub">（單選）</span></legend>
        <div class="prio-grid">
          <label v-for="p in prios" :key="p.formOptionId" class="prio-card" :class="{ on: form.prioCode === p.code }">
            <span class="prio-head">
              <input v-model="form.prioCode" type="radio" :value="p.code" />
              <span class="prio" :style="prioStyle(p.colorCode)">{{ p.code }}</span> {{ p.name }}
            </span>
            <span v-if="p.desc" class="prio-line"><b>【定義】</b>{{ p.desc }}</span>
            <span v-if="p.timeLimitDesc" class="prio-line"><b>【處理時效】</b>{{ p.timeLimitDesc }}</span>
            <span v-if="p.prioFlowDesc" class="prio-line"><b>【審核流程】</b>{{ p.prioFlowDesc }}</span>
            <span v-if="p.sampleDesc" class="prio-line sub"><b>【範例】</b>{{ p.sampleDesc }}</span>
          </label>
        </div>
      </fieldset>

      <fieldset class="card sec">
        <legend>三、作業類別 <span class="sub">（可複選）</span></legend>
        <p v-if="catgs.length === 0" class="muted">（沒有可選的作業類別）</p>
        <div v-for="(c, i) in catgs" :key="c.formOptionId" class="cat">
          <div class="cat-name">【{{ c.name }}】</div>
          <div class="checks">
            <label v-for="it in itemsOf(c.formOptionId)" :key="it.formOptionId" class="chk">
              <input v-model="form.categoryItemIds" type="checkbox" :value="it.formOptionId" /> {{ it.name }}
            </label>
            <label class="chk other">其他：
              <input v-model="form.categoryOthers[c.formOptionId]" v-bind="fx(`categoryOthers[${i}].text`)" placeholder="自訂" />
            </label>
          </div>
        </div>
      </fieldset>

      <fieldset class="card sec">
        <legend>四、異動作業內容</legend>
        <div class="row">
          <label for="f-subject">作業主題</label>
          <input id="f-subject" v-model="form.workSubject" v-bind="fx('workSubject')" placeholder="例：FortiADC 韌體升級" />
        </div>

        <div class="row">
          <label>異動原因 <span class="sub">（可複選）</span></label>
          <div class="checks">
            <label v-for="r in reasons" :key="r.formOptionId" class="chk">
              <input v-model="form.reasonIds" type="checkbox" :value="r.formOptionId" /> {{ r.name }}
            </label>
            <label class="chk other">其他：<input v-model="form.otherReason" v-bind="fx('otherReason')" /></label>
          </div>
        </div>

        <div class="row">
          <label>影響範圍 <span class="sub">（可複選）</span></label>
          <div class="checks">
            <label v-for="x in scopes" :key="x.formOptionId" class="chk">
              <input v-model="form.scopeIds" type="checkbox" :value="x.formOptionId" /> {{ x.name }}
            </label>
          </div>
          <label for="f-impact" class="mt">影響說明</label>
          <textarea id="f-impact" v-model="form.impactDesc" v-bind="fx('impactDesc')" rows="2" />
        </div>

        <div class="row">
          <label>設備位置</label>
          <label class="chk"><input v-model="form.locOmit" type="checkbox" /> 不適用（非機房設備，或機房機櫃尚未維護完成）</label>
          <input v-if="form.locOmit" v-model="form.omitReason" v-bind="fx('location.omitReason')" class="mt" placeholder="原因（例：雲端服務、終端 PC、外部 SaaS）" />
          <p v-else class="sub">機房盤點選擇器尚未開放，可先留空</p>
        </div>

        <div class="row">
          <label>涉及設備
            <button class="quiet small" type="button" :disabled="form.equipments.length >= MAX_ROWS" @click="addEquipment">＋ 新增一列</button>
          </label>
          <div v-for="(eq, i) in form.equipments" :key="i" class="eq-row">
            <input v-model="eq.name" v-bind="fx(`equipments[${i}].name`)" placeholder="設備名稱" :aria-label="`第 ${i + 1} 筆設備名稱`" />
            <input v-model="eq.assetNo" v-bind="fx(`equipments[${i}].assetNo`)" placeholder="資產編號" :aria-label="`第 ${i + 1} 筆資產編號`" />
            <input v-model="eq.modelNo" v-bind="fx(`equipments[${i}].modelNo`)" placeholder="設備型號" :aria-label="`第 ${i + 1} 筆設備型號`" />
            <input v-model="eq.serialNo" v-bind="fx(`equipments[${i}].serialNo`)" placeholder="序號" :aria-label="`第 ${i + 1} 筆序號`" />
            <input v-model="eq.purpose" v-bind="fx(`equipments[${i}].purpose`)" placeholder="用途" :aria-label="`第 ${i + 1} 筆用途`" />
            <input v-model="eq.mgmtIp" v-bind="fx(`equipments[${i}].mgmtIp`)" placeholder="IP" :aria-label="`第 ${i + 1} 筆 IP`" />
            <button class="quiet small" type="button" :aria-label="`移除第 ${i + 1} 筆設備`" @click="removeEquipment(i)">✕</button>
          </div>
        </div>

        <div class="row">
          <label>預計時間</label>
          <div class="grid three">
            <div class="cell">
              <label for="f-start" class="small">開始時間</label>
              <input id="f-start" v-model="form.start" type="datetime-local" step="600" @change="syncHours" />
            </div>
            <div class="cell">
              <label for="f-end" class="small">結束時間</label>
              <input id="f-end" v-model="form.end" type="datetime-local" step="600" @change="syncHours" />
            </div>
            <div class="cell">
              <label for="f-hours" class="small">預計耗時 (小時)</label>
              <input id="f-hours" v-model="form.estHours" type="number" min="0" max="9999.99" step="0.5" />
            </div>
          </div>
          <p class="sub">開始與結束都填好時自動算耗時，之後仍可手改</p>
        </div>

        <div class="row">
          <label>作業執行步驟
            <button class="quiet small" type="button" :disabled="form.planSteps.length >= MAX_ROWS" @click="addStep">＋ 新增一步</button>
          </label>
          <div v-for="n in form.planSteps.length" :key="n" class="step-row">
            <span class="no">{{ n }}.</span>
            <input v-model="form.planSteps[n - 1]" v-bind="fx(`planSteps[${n - 1}]`)" :placeholder="`步驟 ${n}`" :aria-label="`步驟 ${n}`" />
            <button class="quiet small" type="button" :aria-label="`移除步驟 ${n}`" @click="removeStep(n - 1)">✕</button>
          </div>
        </div>

        <div class="row">
          <label for="f-detail">詳細作業說明</label>
          <textarea id="f-detail" v-model="form.workDetail" v-bind="fx('workDetail')" rows="10" />
        </div>

        <div class="grid two">
          <div class="cell">
            <label for="f-risk">風險評估</label>
            <textarea id="f-risk" v-model="form.riskDesc" v-bind="fx('riskDesc')" rows="4" />
          </div>
          <div class="cell">
            <label for="f-rollback">回復計畫（若異動失敗之回復步驟）</label>
            <textarea id="f-rollback" v-model="form.rollbackPlan" v-bind="fx('rollbackPlan')" rows="4" />
          </div>
        </div>
      </fieldset>

      <fieldset class="card sec">
        <legend>附件 <span class="sub">（單檔 ≤ {{ upload.maxMb }} MB、最多 {{ upload.maxFiles }} 個；按「{{ SUBMIT_LABELS[mode] }}」後才上傳）</span></legend>
        <ul v-if="existing.length" class="files">
          <li v-for="f in existing" :key="f.attachId">
            <span class="tag">已上傳</span>
            <span class="fname">{{ f.fileName || '（未命名）' }}</span>
            <span class="sub">{{ kb(f.byteQty) }}・{{ f.uploadedAt ?? '' }}</span>
          </li>
        </ul>
        <ul v-if="queue.length" class="files">
          <li v-for="q in queue" :key="q.key">
            <span class="tag" :class="q.status">{{ QUEUE_LABELS[q.status] }}</span>
            <span class="fname">{{ q.file.name }}</span>
            <span class="sub">{{ kb(q.file.size) }}</span>
            <progress v-if="q.status === 'uploading'" max="100" :value="q.percent" :aria-label="`${q.file.name} 上傳進度`">{{ q.percent }}%</progress>
            <span v-if="q.status === 'uploading'" class="sub">{{ q.percent }}%</span>
            <span v-if="q.error" class="bad-text">{{ q.error }}</span>
            <button class="quiet small" type="button" :disabled="q.status === 'uploading'" @click="removeQueued(q.key)">移除</button>
          </li>
        </ul>
        <label class="pick">
          <input type="file" multiple :accept="ACCEPT" :disabled="saving" @change="onPick" />
        </label>
        <ul v-if="rejects.length" class="rejects" role="alert">
          <li v-for="(r, i) in rejects" :key="i">{{ r }}</li>
        </ul>
      </fieldset>

      <fieldset v-if="isResubmit" class="card sec">
        <legend>補件說明 <span class="sub">（選填：這次改了什麼，給簽核人看）</span></legend>
        <textarea id="f-resub" v-model="resubMemo" v-bind="fx('resubMemo')" rows="3" aria-label="補件說明" />
      </fieldset>

      <div class="bar">
        <p v-if="formError" class="bar-msg" role="alert">{{ formError }}</p>
        <div class="bar-btns">
          <button v-if="conflict" class="quiet" type="button" :disabled="saving" @click="reload">重新載入（放棄這次修改）</button>
          <router-link class="quiet cancel" :to="backTo">取消</router-link>
          <button class="go" type="submit" :disabled="saving">{{ saving ? savingText : SUBMIT_LABELS[mode] }}</button>
        </div>
      </div>
    </form>

    <ToastHost />
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter, type RouteLocationRaw } from 'vue-router'
import { AxiosError } from 'axios'
import ToastHost from '../components/ToastHost.vue'
import { createApp, getApp, getFormOptions, resubmitApp, updateApp, uploadAttachment } from '../api/apps'
import { errorMessage, isUnauthorized } from '../api/http'
import { useAuth } from '../composables/useAuth'
import { useToast } from '../composables/useToast'
import { APP_EDIT_ROUTE, APP_LIST_ROUTE, APP_RESUBMIT_ROUTE, APP_VIEW_ROUTE } from '../router/names'
import {
  ATTACH_EXTS,
  EVENT_LABELS,
  WORK_MODE_LABELS,
  type ApiFieldErrorBody,
  type AppAttachment,
  type AppDetail,
  type AppDraftForm,
  type FormOption
} from '../types/app'
import {
  emptyEquipment,
  emptyForm,
  formFromDetail,
  hoursBetween,
  precheckFile,
  safeFieldPath,
  toDraftRequest
} from '../utils/draftForm'
import { kb, prioStyle } from '../utils/format'

/** 與後端 AppDraftValidator.MAX_ROWS 相同 */
const MAX_ROWS = 100
const ACCEPT = ATTACH_EXTS.map(e => '.' + e).join(',')

type QueueStatus = 'wait' | 'uploading' | 'error'
interface QueueItem {
  key: number
  file: File
  status: QueueStatus
  percent: number
  error: string
}
const QUEUE_LABELS: Record<QueueStatus, string> = { wait: '待上傳', uploading: '上傳中', error: '失敗' }

type FormMode = 'new' | 'edit' | 'resubmit'
const TITLES: Record<FormMode, string> = { new: '新增申請單', edit: '編輯草稿', resubmit: '補件並重送' }
const SUBMIT_LABELS: Record<FormMode, string> = { new: '儲存草稿', edit: '儲存草稿', resubmit: '補件並送審' }

interface RejectInfo {
  label: string
  by: string
  at: string
  memo: string
}

/** 退件來源：簽核退件看目前實例裡退件那一關；執行階段／治理複驗退回看最後一筆對應事件 */
function rejectInfoOf(d: AppDetail): RejectInfo | null {
  const step = d.approval.steps.find(s => s.statusCode === 'REJECTED')
  if (step) {
    return { label: `簽核退件（${step.stepName ?? '關卡'}）`, by: step.deciderName ?? '', at: step.decidedAt ?? '', memo: step.memo ?? '' }
  }
  const ev = [...d.events].reverse().find(e => e.code === 'EXEC_REJECT' || e.code === 'GOV_RETURN')
  if (ev) return { label: EVENT_LABELS[ev.code ?? ''] ?? '退回', by: ev.userName ?? '', at: ev.at ?? '', memo: ev.memo ?? '' }
  return null
}

const route = useRoute()
const router = useRouter()
const auth = useAuth()
const { toast } = useToast()

const rootEl = ref<HTMLElement | null>(null)

const editId = computed(() => {
  const id = route.params.id
  return typeof id === 'string' ? id : ''
})
const isResubmit = computed(() => route.name === APP_RESUBMIT_ROUTE)
const mode = computed<FormMode>(() => (isResubmit.value ? 'resubmit' : editId.value !== '' ? 'edit' : 'new'))
const resubMemo = ref('')
const reject = ref<RejectInfo | null>(null)

const form = ref<AppDraftForm>(emptyForm())
const options = ref<FormOption[]>([])
const upload = ref({ maxMb: 50, maxFiles: 30 })
const existing = ref<AppAttachment[]>([])
const applicantName = ref('')
const rowVerNo = ref<number | null>(null)
/** 已存在的單號：編輯進來就有；新增在第一次存檔成功後才有 */
const savedId = ref('')

const loading = ref(true)
const loadError = ref('')
const saving = ref(false)
const savingText = ref('儲存中…')
const formError = ref('')
const conflict = ref(false)
const badField = ref<string | null>(null)

const queue = ref<QueueItem[]>([])
const rejects = ref<string[]>([])
let fileSeq = 0

const backTo = computed<RouteLocationRaw>(() =>
  savedId.value ? { name: APP_VIEW_ROUTE, params: { id: savedId.value } } : { name: APP_LIST_ROUTE }
)

function group(code: string): FormOption[] {
  return options.value.filter(o => o.groupCode === code)
}
const prios = computed(() => group('PRIO'))
const catgs = computed(() => group('CATG'))
const reasons = computed(() => group('REASON'))
const scopes = computed(() => group('SCOPE'))

function itemsOf(catgId: number): FormOption[] {
  return options.value.filter(o => o.groupCode === 'CATG_ITEM' && o.upFormOptionId === catgId)
}

/** 欄位的 data-field（給 400 定位）與標紅 class */
function fx(path: string): { 'data-field': string; class: { bad: boolean } } {
  return { 'data-field': path, class: { bad: badField.value === path } }
}

/** 編輯舊草稿時，已停用的選項不在 options 裡：留著會讓後端回「選項不正確」而永遠存不了 */
function keepActive(f: AppDraftForm, opts: FormOption[]): AppDraftForm {
  const active = new Set(opts.map(o => o.formOptionId))
  const others: Record<number, string> = {}
  for (const [k, v] of Object.entries(f.categoryOthers)) {
    if (active.has(Number(k))) others[Number(k)] = v
  }
  return {
    ...f,
    categoryItemIds: f.categoryItemIds.filter(id => active.has(id)),
    categoryOthers: others,
    reasonIds: f.reasonIds.filter(id => active.has(id)),
    scopeIds: f.scopeIds.filter(id => active.has(id))
  }
}

/** 每次載入遞增；回應回來時不是最新就丟掉 */
let seq = 0

async function load(): Promise<void> {
  const mine = ++seq
  const id = editId.value
  loading.value = true
  loadError.value = ''
  formError.value = ''
  conflict.value = false
  badField.value = null
  try {
    const [opts, detail] = await Promise.all([getFormOptions(), id ? getApp(id) : Promise.resolve(null)])
    if (mine !== seq) return
    options.value = opts.options
    upload.value = opts.upload
    if (detail) {
      if (isResubmit.value && !detail.permissions.canResubmit) {
        loadError.value = detail.statusCode === 'REJECTED' ? '只有申請人可以補件' : '申請單不是退件狀態，無法補件'
        return
      }
      if (!isResubmit.value && !detail.permissions.canEditDraft) {
        loadError.value = detail.statusCode === 'DRAFT' ? '只有申請人可以編輯草稿' : '申請單已不是草稿，無法編輯'
        return
      }
      reject.value = isResubmit.value ? rejectInfoOf(detail) : null
      form.value = keepActive(formFromDetail(detail), opts.options)
      existing.value = detail.attachments.filter(a => a.ownerType === 'APP')
      applicantName.value = detail.applicant.name ?? ''
      rowVerNo.value = detail.rowVerNo
      savedId.value = detail.appId
    } else {
      form.value = emptyForm()
      existing.value = []
      applicantName.value = auth.userName.value
      rowVerNo.value = null
      savedId.value = ''
    }
  } catch (e: unknown) {
    if (mine !== seq) return
    if (isUnauthorized(e)) {
      loadError.value = '尚未登入'
      return
    }
    loadError.value = errorMessage(e)
    toast(loadError.value, 'red')
  } finally {
    if (mine === seq) loading.value = false
  }
}

/** 新增存檔後網址換成編輯頁時單號已是 savedId，不重新載入（否則會清掉待上傳清單）；同一單號在編輯與補件間切換才重載 */
watch(
  [editId, isResubmit],
  ([id], old) => {
    const sameMode = old !== undefined && old[1] === isResubmit.value
    if (id && id === savedId.value && sameMode) return
    queue.value = []
    rejects.value = []
    resubMemo.value = ''
    void load()
  },
  { immediate: true }
)

function reload(): void {
  void load()
}

function addEquipment(): void {
  if (form.value.equipments.length < MAX_ROWS) form.value.equipments.push(emptyEquipment())
}

/** 最後一列不刪、改成清空，畫面上永遠留一列可填 */
function removeEquipment(i: number): void {
  if (form.value.equipments.length > 1) form.value.equipments.splice(i, 1)
  else form.value.equipments[0] = emptyEquipment()
}

function addStep(): void {
  if (form.value.planSteps.length < MAX_ROWS) form.value.planSteps.push('')
}

function removeStep(i: number): void {
  if (form.value.planSteps.length > 1) form.value.planSteps.splice(i, 1)
  else form.value.planSteps[0] = ''
}

function syncHours(): void {
  const h = hoursBetween(form.value.start, form.value.end)
  if (h != null) form.value.estHours = h
}

function onPick(e: Event): void {
  const input = e.target as HTMLInputElement
  const files = input.files ? Array.from(input.files) : []
  input.value = ''
  addFiles(files)
}

function addFiles(files: File[]): void {
  const problems: string[] = []
  for (const f of files) {
    if (existing.value.length + queue.value.length >= upload.value.maxFiles) {
      problems.push(`${f.name}：附件最多 ${upload.value.maxFiles} 個`)
      continue
    }
    const why = precheckFile(f, upload.value.maxMb)
    if (why) {
      problems.push(`${f.name}：${why}`)
      continue
    }
    queue.value.push({ key: ++fileSeq, file: f, status: 'wait', percent: 0, error: '' })
  }
  rejects.value = problems
  if (problems.length) toast(`有 ${problems.length} 個檔案無法加入`, 'red')
}

function removeQueued(key: number): void {
  queue.value = queue.value.filter(q => q.key !== key)
}

function statusOf(e: unknown): number | undefined {
  return e instanceof AxiosError ? e.response?.status : undefined
}

function fieldOf(e: unknown): string | null {
  if (!(e instanceof AxiosError)) return null
  const body: unknown = e.response?.data
  if (body && typeof body === 'object' && 'field' in body) return safeFieldPath((body as ApiFieldErrorBody).field)
  return null
}

async function markField(field: string): Promise<void> {
  badField.value = field
  await nextTick()
  const el = rootEl.value?.querySelector<HTMLElement>(`[data-field="${field}"]`)
  if (!el) return
  if (typeof el.scrollIntoView === 'function') el.scrollIntoView({ block: 'center' })
  el.focus({ preventScroll: true })
}

function saveFailed(e: unknown): void {
  if (isUnauthorized(e)) return
  const msg = errorMessage(e, '儲存失敗，請稍後再試')
  formError.value = msg
  const status = statusOf(e)
  if (status === 409) conflict.value = true
  if (status === 400) {
    const f = fieldOf(e)
    if (f) void markField(f)
  }
  toast(msg, 'red')
}

/** 逐檔上傳；成功的移到「已上傳」，失敗的留在清單標原因。409（已不是草稿）或 401 就停，不再試後面的 */
async function uploadQueue(id: string): Promise<boolean> {
  let allOk = true
  const items = [...queue.value]
  for (let i = 0; i < items.length; i++) {
    const item = items[i]
    savingText.value = `上傳附件 ${i + 1}/${items.length}…`
    item.status = 'uploading'
    item.percent = 0
    item.error = ''
    try {
      const a = await uploadAttachment(id, item.file, p => {
        item.percent = p
      })
      existing.value.push(a)
      queue.value = queue.value.filter(q => q.key !== item.key)
    } catch (e: unknown) {
      allOk = false
      item.status = 'error'
      item.error = isUnauthorized(e) ? '尚未登入' : errorMessage(e, '上傳失敗，請稍後再試')
      if (isUnauthorized(e) || statusOf(e) === 409) return false
    }
  }
  return allOk
}

async function save(): Promise<void> {
  if (saving.value) return
  formError.value = ''
  conflict.value = false
  badField.value = null
  if (form.value.title.trim() === '') {
    formError.value = '請填寫標題'
    void markField('title')
    return
  }
  if (isResubmit.value) {
    await resubmit()
    return
  }
  saving.value = true
  savingText.value = '儲存中…'
  const catgIds = catgs.value.map(c => c.formOptionId)
  try {
    if (savedId.value) {
      const r = await updateApp(savedId.value, toDraftRequest(form.value, catgIds, rowVerNo.value ?? 0))
      rowVerNo.value = r.rowVerNo
    } else {
      const r = await createApp(toDraftRequest(form.value, catgIds))
      savedId.value = r.appId
      rowVerNo.value = r.rowVerNo
      await router.replace({ name: APP_EDIT_ROUTE, params: { id: r.appId } })
    }
  } catch (e: unknown) {
    saving.value = false
    saveFailed(e)
    return
  }
  const ok = await uploadQueue(savedId.value)
  saving.value = false
  if (ok) {
    toast('草稿已儲存', 'teal')
    await router.push({ name: APP_VIEW_ROUTE, params: { id: savedId.value } })
    return
  }
  formError.value = '草稿內容已儲存，但有附件上傳失敗；可移除失敗的檔案，或再按「儲存草稿」重試'
  toast('有附件上傳失敗', 'amber')
}

/**
 * 補件：先上傳附件（補件後就是審核中、不能再傳），全部成功才送補件；任一檔失敗就停在本頁、內容不送出。
 * 409（版本或狀態已變）與 403 不自動重載（會洗掉剛改的內容），比照編輯草稿顯示「重新載入（放棄這次修改）」
 */
async function resubmit(): Promise<void> {
  if (!window.confirm('確定要補件並重新送審？送出後會成為新的版次，從第一關重新簽核。')) return
  saving.value = true
  const id = savedId.value
  if (queue.value.length) {
    const ok = await uploadQueue(id)
    if (!ok) {
      saving.value = false
      formError.value = '有附件上傳失敗，補件尚未送出；可移除失敗的檔案，或再按「補件並送審」重試'
      toast('有附件上傳失敗', 'amber')
      return
    }
  }
  savingText.value = '送出中…'
  const catgIds = catgs.value.map(c => c.formOptionId)
  try {
    await resubmitApp(id, {
      rowVerNo: rowVerNo.value ?? 0,
      resubMemo: resubMemo.value,
      form: toDraftRequest(form.value, catgIds)
    })
  } catch (e: unknown) {
    saving.value = false
    saveFailed(e)
    if (statusOf(e) === 403) conflict.value = true
    return
  }
  saving.value = false
  toast('已補件並重新送審', 'teal')
  await router.push({ name: APP_VIEW_ROUTE, params: { id } })
}
</script>

<style scoped>
main { width: 100%; max-width: 1180px; margin: 0 auto; padding: 24px 20px; }

.hero { margin-bottom: 16px; display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; }
.head { min-width: 0; }
.hero h1 { margin: 0; font-size: 26px; color: var(--navy); overflow-wrap: anywhere; }
.hero .id { font-size: 18px; color: var(--gray); margin-left: 6px; }
.link { color: var(--blue); font-size: 17px; white-space: nowrap; }

.card { background: #fff; border: 1px solid var(--line); border-radius: 10px; padding: 16px 20px; margin: 0 0 16px; overflow-wrap: anywhere; min-width: 0; }
.sec legend { font-size: 21px; font-weight: 700; color: var(--teal-dark); padding: 0 6px; }
.muted { color: var(--gray); margin: 4px 0; }
.error { color: var(--red); font-weight: 700; }
.sub { color: var(--gray); font-size: 14px; font-weight: 400; }
.req { color: var(--red); }

input:not([type="checkbox"]):not([type="radio"]):not([type="file"]), textarea {
  width: 100%; min-width: 0; padding: 6px 10px; border: 1px solid #cbd5e1; border-radius: 7px; font-size: 17px; background: #fff; color: var(--dark);
}
textarea { resize: vertical; line-height: 1.5; }
input.bad, textarea.bad { border-color: var(--red); box-shadow: 0 0 0 2px rgba(220, 38, 38, .2); }
label { display: block; margin-bottom: 2px; color: var(--dark); font-weight: 700; }
label.small { font-size: 15px; }
.mt { margin-top: 8px; }
.fixed { padding: 6px 0; font-weight: 700; color: var(--teal-dark); }

.grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px 16px; }
.grid.three { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.cell { min-width: 0; }
.cell.wide { grid-column: 1 / -1; }
.row { margin-bottom: 14px; min-width: 0; }

.checks { display: flex; flex-wrap: wrap; gap: 4px 18px; }
.chk { display: inline-flex; align-items: center; gap: 6px; font-weight: 400; margin: 0; }
.chk input[type="checkbox"], .chk input[type="radio"] { width: 18px; height: 18px; }
.chk.other { flex: 1 1 260px; min-width: 0; }
.chk.other input { flex: 1; width: auto; }

.prio-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(210px, 1fr)); gap: 10px; }
.prio-card { display: flex; flex-direction: column; gap: 2px; border: 1px solid var(--line); border-radius: 8px; padding: 8px 10px; font-weight: 400; font-size: 15px; cursor: pointer; min-width: 0; }
.prio-card.on { border-color: var(--teal); background: var(--teal-bg); }
.prio-head { display: flex; align-items: center; gap: 6px; font-weight: 700; font-size: 17px; }
.prio-head input { width: 18px; height: 18px; }
.prio { display: inline-block; padding: 0 8px; border-radius: 6px; background: var(--line); font-weight: 700; font-size: 15px; }
.prio-line { overflow-wrap: anywhere; }

.cat + .cat { margin-top: 8px; }
.cat-name { font-weight: 700; color: var(--navy); }

.eq-row { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)) auto; gap: 6px; margin-bottom: 6px; }
.eq-row input { font-size: 15px !important; }
.step-row { display: flex; align-items: center; gap: 6px; margin-bottom: 6px; }
.step-row .no { width: 28px; text-align: right; color: var(--gray); flex: none; }

.quiet { background: #fff; border: 1px solid var(--line); color: var(--dark); border-radius: 7px; padding: 8px 18px; font-size: 17px; cursor: pointer; font-weight: 400; }
.quiet:disabled { opacity: .55; cursor: default; }
.quiet.small { min-height: 36px; padding: 2px 12px; font-size: 15px; margin-left: 8px; }
.eq-row .quiet.small, .step-row .quiet.small { margin-left: 0; }

.files { list-style: none; margin: 0 0 10px; padding: 0; }
.files li { padding: 6px 0; border-bottom: 1px solid var(--line); display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.fname { font-weight: 700; overflow-wrap: anywhere; }
.tag { display: inline-block; padding: 0 8px; border-radius: 6px; background: var(--teal-bg); color: var(--teal-dark); font-size: 14px; font-weight: 700; }
.tag.wait { background: var(--bg); color: var(--gray); }
.tag.uploading { background: #e0e9f8; color: var(--blue); }
.tag.error { background: #fde8e8; color: var(--red); }
.bad-text { color: var(--red); font-size: 15px; }
progress { width: 160px; height: 14px; }
.pick { font-weight: 400; }
.rejects { margin: 8px 0 0; padding-left: 20px; color: var(--red); font-size: 15px; }

.bar { position: sticky; bottom: 0; background: #fff; border: 1px solid var(--line); border-radius: 10px; padding: 10px 16px; display: flex; flex-wrap: wrap; align-items: center; justify-content: flex-end; gap: 10px; box-shadow: 0 -2px 8px rgba(0, 0, 0, .06); }
.bar-msg { flex: 1 1 300px; margin: 0; color: var(--red); font-weight: 700; overflow-wrap: anywhere; }
.bar-btns { display: flex; flex-wrap: wrap; gap: 10px; }
.cancel { text-decoration: none; display: inline-flex; align-items: center; }
.reject { border-color: var(--red); background: #fdf2f2; }
.reject h2 { margin: 0 0 6px; font-size: 19px; color: var(--red); }
.reject-who { margin: 0 0 4px; font-weight: 700; }
.reject-memo { margin: 0; white-space: pre-wrap; }
.go { background: var(--teal); border: none; color: #fff; font-weight: 700; border-radius: 7px; padding: 10px 24px; font-size: 19px; cursor: pointer; min-width: 160px; }
.go:hover:not(:disabled) { filter: brightness(1.08); }
.go:disabled { opacity: .55; cursor: default; }
</style>
