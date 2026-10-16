<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-13
  變更說明: 新增：緊急存取唯讀詳情彈窗（BACKLOG 第 46 項 S3，裁示 ⑫；GET /emergencies/{id}）
            申請內容全部欄位；「申請人回報」「主管處理」「異常結案」三段有值才顯示內容，沒值顯示「尚未…」
            （異常結案段只在已退回時出現）；自核、自行結案、逾期後處理加標記（裁示⑤、㉖、D14）；
            受影響帳號有資料才列表。回報、處理、結案按鈕留給 S5／S6；查無或無權看（404）只 toast
  AI版本  : Claude Fable 5.1 (claude-fable-5-1)
  修改日期: 2026-10-13
  變更說明: BACKLOG 第 46 項 S5 回合 1：
            送出、回報、處理、結案四個節點在實際操作人≠有效身分時顯示「{代理}（實際操作人：{實際}）」（S3 審查第 4 項、裁示①）；
            有 EMG_DECIDE 且尚未處理時底部出現「退回」「補核准」（裁示③A：動作放詳情彈窗，不另建處理彈窗）；
            補核准開確認框、退回開原因小彈窗（必填，同待辦頁退件）；尚未回報時補核准停用並提示（S4-⑦）；
            成功 toast、重載明細並 emit changed 讓清單重查；409（已由他人處理）toast 後同樣重載；回報按鈕留回合 2
            審查：請求送出中（busy）忽略主、子彈窗的關閉（否則元件卸載後 changed 被丟掉、清單停在舊狀態，裁示①A）
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-13
  變更說明: BACKLOG 第 46 項 S5 回合 2：底部加「回報」（申請人是我的有效身分、尚未回報、尚未處理；S4-⑬ 不需 EMG_APPLY），
            開 EmergencyReportModal；回報成功或 409 時關回報彈窗、重載明細並 emit changed；
            回報彈窗開著時忽略主彈窗關閉（審查第 1 項：鍵盤可摸到底下的關閉鈕）
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-13
  變更說明: BACKLOG 第 46 項 S6 回合 2：底部加「異常結案」（EMG_DECIDE、已退回、尚未結案；裁示⑥），開 EmergencyCloseModal（S6-④）；
            結案成功或 409 時與回報同一處理（關子彈窗、重載明細、emit changed）；結案彈窗開著時同樣忽略主彈窗關閉
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-16
  變更說明: 必填欄位醒目提醒：退回原因的「必填」改為紅色 *
-->
<template>
  <BaseModal :title="`緊急存取 — ${emgId}`" :sub="subText" wide @close="closeMain">
    <p v-if="loading" class="hint">載入中…</p>
    <template v-else-if="d">
      <div class="grid2">
        <div class="kv"><div class="k">帳號類別</div><div class="v">{{ d.catgName || d.catgId }}</div></div>
        <div class="kv"><div class="k">申請人</div><div class="v applicant">{{ applicantText }}</div></div>
        <div class="kv"><div class="k">送出時間（生效）</div><div class="v">{{ toMinute(d.submitDate) }}</div></div>
        <div class="kv"><div class="k">補核准期限</div><div class="v">{{ toMinute(d.dueDate) }}</div></div>
        <div class="kv"><div class="k">預估時長</div><div class="v">{{ d.expectHourQty }} 小時</div></div>
      </div>
      <div class="kv"><div class="k">帳號或系統</div><div class="v">{{ d.targetDescr }}</div></div>
      <div class="kv"><div class="k">緊急事由</div><div class="v pre">{{ d.applyReason }}</div></div>

      <div class="divider-lbl">申請人回報</div>
      <template v-if="d.reportTypeCode">
        <div class="kv sec-report">
          <div class="k">{{ toMinute(d.reportDate) }}<span v-if="reporterText" class="actor report-actor">　{{ reporterText }}</span></div>
          <div class="v pre"><b>{{ label(EMG_REPORT_LABEL, d.reportTypeCode) }}</b>{{ d.reportDescr ? `：${d.reportDescr}` : '' }}</div>
        </div>
      </template>
      <p v-else class="hint sec-report">尚未回報</p>

      <div class="divider-lbl">主管處理</div>
      <template v-if="d.decideCode">
        <div class="kv sec-decide">
          <div class="k">
            {{ toMinute(d.decideDate) }}　<span class="actor decide-actor">{{ deciderText }}</span>
            <span v-if="isOn(d.isSelfDecide)" class="tag tag-warn self-decide">自核</span>
            <span v-if="decidedLate" class="tag tag-priv late">逾期後處理</span>
          </div>
          <div class="v pre"><b>{{ label(EMG_DECIDE_LABEL, d.decideCode) }}</b>{{ d.returnReason ? `：${d.returnReason}` : '' }}</div>
        </div>
      </template>
      <p v-else class="hint sec-decide">尚未處理</p>

      <template v-if="d.decideCode === 'RETURN'">
        <div class="divider-lbl">異常結案</div>
        <template v-if="d.closeDate">
          <div class="kv sec-close">
            <div class="k">
              {{ toMinute(d.closeDate) }}　<span class="actor close-actor">{{ closerText }}</span>
              <span v-if="isOn(d.isSelfClose)" class="tag tag-warn self-close">自行結案</span>
            </div>
            <div class="v pre"><b>{{ label(EMG_CLOSE_RESULT_LABEL, d.closeResultCode) }}</b>{{ d.closeDescr ? `：${d.closeDescr}` : '' }}</div>
          </div>
        </template>
        <p v-else class="hint sec-close">尚未結案</p>
      </template>

      <template v-if="d.affectAccts.length > 0">
        <div class="divider-lbl">受影響帳號</div>
        <table class="tbl affect-tbl">
          <thead><tr><th>來源</th><th>帳號</th><th>系統</th><th>說明</th><th>登記</th></tr></thead>
          <tbody>
            <tr v-for="a in d.affectAccts" :key="`${a.affectTypeCode}:${a.acctId}`">
              <td>{{ label(EMG_AFFECT_TYPE_LABEL, a.affectTypeCode) }}</td>
              <td>{{ a.acctName || a.acctId }}</td>
              <td>{{ a.sysName || '—' }}</td>
              <td>{{ a.affectDescr || '—' }}</td>
              <td>{{ a.recordByName || a.recordBy }}<div class="hint">{{ toMinute(a.recordDate) }}</div></td>
            </tr>
          </tbody>
        </table>
      </template>

      <div v-if="canDecide && !d.reportTypeCode" class="hint warn approve-hint">申請人尚未回報，不能補核准；可先退回</div>
    </template>
    <p v-else class="hint">無法載入此緊急存取單</p>

    <template #actions>
      <button v-if="canReport" type="button" class="btn btn-primary act-report" :disabled="busy" @click="reporting = true">回報</button>
      <template v-if="d && canDecide">
        <button type="button" class="btn btn-warn act-return" :disabled="busy" @click="openReturn">退回</button>
        <button type="button" class="btn btn-primary act-approve" :disabled="busy || !d.reportTypeCode" @click="confirmApprove = true">補核准</button>
      </template>
      <button v-if="canClose" type="button" class="btn btn-primary act-close-case" :disabled="busy" @click="closingCase = true">異常結案</button>
    </template>
  </BaseModal>

  <BaseModal v-if="d && confirmApprove" :title="`補核准 — ${emgId}`" :sub="`${d.catgName || d.catgId} — ${d.targetDescr}`" close-text="取消" @close="closeSub">
    <p class="confirm-text">確認補核准此緊急存取？{{ d.reportTypeCode === 'MODIFIED' ? '申請人回報有帳號異動，將寫入各帳號的歷史紀錄。' : '' }}</p>
    <template #actions>
      <button type="button" class="btn btn-primary confirm-approve" :disabled="busy" @click="doApprove">確認補核准</button>
    </template>
  </BaseModal>

  <BaseModal v-if="d && returning" :title="`退回 — ${emgId}`" sub="請填寫退回原因，將通知申請人；回報有帳號異動時，各帳號會標記審查異常。" close-text="取消" @close="closeSub">
    <div class="field">
      <label for="emg-return-reason">退回原因<span class="req-mark" title="必填">*</span>（500 字內）</label>
      <textarea id="emg-return-reason" v-model="returnReason" rows="3" placeholder="請填寫退回原因"></textarea>
      <div class="hint" :class="{ warn: reasonCount > REASON_MAX }">{{ reasonCount }} / {{ REASON_MAX }}</div>
    </div>
    <template #actions>
      <button type="button" class="btn btn-primary confirm-return" :disabled="busy" @click="doReturn">確認退回</button>
    </template>
  </BaseModal>

  <EmergencyReportModal v-if="d && reporting" :emg="d" @close="reporting = false" @done="onSubDone" />
  <EmergencyCloseModal v-if="d && closingCase" :emg="d" @close="closingCase = false" @done="onSubDone" />
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import BaseModal from './BaseModal.vue'
import EmergencyCloseModal from './EmergencyCloseModal.vue'
import EmergencyReportModal from './EmergencyReportModal.vue'
import { approveEmergency, getEmergency, returnEmergency } from '../api/emergency'
import { errorMessage, errorStatus } from '../api/errors'
import { useSession } from '../composables/useSession'
import { useToast } from '../composables/useToast'
import type { EmgDetail } from '../types/emergency'
import { toMinute } from '../utils/date'
import {
  EMG_AFFECT_TYPE_LABEL,
  EMG_CLOSE_RESULT_LABEL,
  EMG_DECIDE_LABEL,
  EMG_REPORT_LABEL,
  EMG_STATE_LABEL,
  isOn,
  label,
  personText
} from '../utils/labels'

const REASON_MAX = 500
const MSG_REASON = '請填寫退回原因'
const MSG_REASON_LEN = `退回原因不可超過 ${REASON_MAX} 字`

const props = defineProps<{ emgId: string }>()
/** changed：補核准、退回、回報、異常結案成功（含 409 後重載），讓呼叫端重查清單 */
const emit = defineEmits<{ close: []; changed: [] }>()
const { toast } = useToast()
const { can, me } = useSession()

const d = ref<EmgDetail | null>(null)
const loading = ref(true)
const busy = ref(false)
const confirmApprove = ref(false)
const returning = ref(false)
const returnReason = ref('')
const reporting = ref(false)
/** 異常結案彈窗開著（不叫 closing：與「關閉彈窗」一字兩義） */
const closingCase = ref(false)

const subText = computed(() => (d.value ? `狀態：${label(EMG_STATE_LABEL, d.value.emgStateCode)}` : ''))
/** 處理時間晚於補核准期限（D14：逾期後仍可處理，照實標示）；兩者同為 yyyy-MM-dd HH:mm:ss，可直接字串比較 */
const decidedLate = computed(() => !!d.value?.decideDate && d.value.decideDate > d.value.dueDate)
/** 有 EMG_DECIDE 且尚未處理才能補核准或退回；逾期後仍可（D14） */
const canDecide = computed(() => can('EMG_DECIDE') && !!d.value && !d.value.decideCode)
/** 申請人是我的有效身分、尚未回報、尚未處理才能回報（S4-⑬：不需 EMG_APPLY；逾期後仍可）；後端同樣驗證 */
const canReport = computed(() => {
  const x = d.value
  return !!x && !!me.value && me.value.userId === x.applyBy && !x.reportTypeCode && !x.decideCode
})
/** 有 EMG_DECIDE、已退回、尚未結案才能異常結案（裁示⑥）；後端同樣驗證 */
const canClose = computed(() => can('EMG_DECIDE') && !!d.value && d.value.decideCode === 'RETURN' && !d.value.closeDate)
/** 字數按字元（碼點）算、去頭尾空白，與後端一致 */
const reasonCount = computed(() => [...returnReason.value.trim()].length)

/**
 * 節點操作人文字：實際操作人與有效身分不同（切換身分代操作）才加「（實際操作人：{實際}）」；
 * 工號相同或沒有實際操作人欄（舊資料）就只顯示有效身分
 */
function actorText(byName: string | null, byId: string | null, actByName: string | null, actById: string | null): string {
  const eff = byName || byId || '—'
  if (!actById || actById === byId) return eff
  return personText(actByName || actById, eff)
}
/** 申請人：送出的實際操作人是 CREATE_BY */
const applicantText = computed(() => (d.value ? actorText(d.value.applyByName, d.value.applyBy, d.value.createByName, d.value.createBy) : ''))
/** 回報人就是申請人，只在代回報時才顯示；本人回報不重複列名字 */
const reporterText = computed(() => {
  const x = d.value
  if (!x || !x.reportActBy || x.reportActBy === x.applyBy) return ''
  return actorText(x.applyByName, x.applyBy, x.reportActByName, x.reportActBy)
})
const deciderText = computed(() => (d.value ? actorText(d.value.decideByName, d.value.decideBy, d.value.decideActByName, d.value.decideActBy) : ''))
const closerText = computed(() => (d.value ? actorText(d.value.closeByName, d.value.closeBy, d.value.closeActByName, d.value.closeActBy) : ''))

async function load(): Promise<void> {
  try {
    const data = await getEmergency(props.emgId)
    d.value = { ...data, affectAccts: Array.isArray(data?.affectAccts) ? data.affectAccts : [] }
  } catch (e) {
    toast(errorMessage(e, '查詢失敗，請稍後再試'), 'amber')
  } finally {
    loading.value = false
  }
}

function openReturn(): void {
  returnReason.value = ''
  returning.value = true
}

/**
 * 請求送出中不關主彈窗：元件卸載後 Vue 會丟掉 emit，清單就收不到 changed。
 * 回報、結案彈窗開著時也不關：它們的送出中旗標是自己的，滑鼠點不到底下的主彈窗，但鍵盤 Tab 摸得到關閉鈕
 */
function closeMain(): void {
  if (busy.value || reporting.value || closingCase.value) return
  emit('close')
}

/** 子彈窗（確認框、原因框）取消或點遮罩只關子彈窗；送出中同樣忽略 */
function closeSub(): void {
  if (busy.value) return
  confirmApprove.value = false
  returning.value = false
}

/** 執行處理動作：成功 toast、關子彈窗、重載明細並通知清單；失敗 toast 後端訊息，409（已由他人處理）同樣重載讓畫面與後端一致 */
async function run(action: () => Promise<unknown>, okMsg: string): Promise<void> {
  if (busy.value) return
  busy.value = true
  try {
    await action()
    toast(okMsg, 'teal')
    confirmApprove.value = false
    returning.value = false
    await load()
    emit('changed')
  } catch (e) {
    toast(errorMessage(e, '操作失敗，請稍後再試'), 'amber')
    if (errorStatus(e) === 409) {
      confirmApprove.value = false
      returning.value = false
      await load()
      emit('changed')
    }
  } finally {
    busy.value = false
  }
}

/** 回報或結案成功、或 409：關該子彈窗、重載明細並通知清單；重載期間同樣不讓主彈窗關閉 */
async function onSubDone(): Promise<void> {
  reporting.value = false
  closingCase.value = false
  busy.value = true
  try {
    await load()
    emit('changed')
  } finally {
    busy.value = false
  }
}

function doApprove(): void {
  const x = d.value
  if (!x) return
  void run(() => approveEmergency(x.emgId, x.versionNo), `${x.emgId} 已補核准`)
}

function doReturn(): void {
  const x = d.value
  if (!x) return
  const text = returnReason.value.trim()
  if (!text) {
    toast(MSG_REASON, 'amber')
    return
  }
  if (reasonCount.value > REASON_MAX) {
    toast(MSG_REASON_LEN, 'amber')
    return
  }
  void run(() => returnEmergency(x.emgId, x.versionNo, text), `${x.emgId} 已退回，將通知申請人`)
}

onMounted(load)
</script>

<style scoped>
.pre { white-space: pre-wrap; }
.kv .k .tag { font-size: 15px; margin-left: 6px; }
.affect-tbl { font-size: 17px; }
.approve-hint { margin-top: 12px; }
</style>
