<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-06
  變更說明: 新增：申請單檢視頁（S4 回合三，唯讀）。GET /apps/{id}，區塊順序比照舊系統 views/apps/view.ejs：
            一、基本資料 二、事件分級 三、作業類別 四、異動作業內容 五、作業執行檢核表 六、實際執行紀錄 七、簽核欄，
            再接附件、歷史版次、事件紀錄
            與舊畫面的差異：選項只列有勾選的（舊畫面連未勾選的也列成 ☐），作業類別不分組（後端只帶上層代碼、沒帶名稱）
            動作鈕依 permissions 顯示，S4 一律 toast「此功能尚未開放」；附件下載鈕停用（殼 jar 二進位透傳留 S6，BACKLOG 第 79 項）
            失敗 → toast 後端訊息並顯示錯誤文字（404「找不到申請單」）；401 由登入處理器導頁，本頁不另出 toast
            S4 審查修正：類別「其他」補充顯示為「類別（其他）：…」；長字串一律可斷行（1024 寬不出水平捲軸）；
            快速切換單號時丟棄過期回應；執行結果未填時「例外」「後續追蹤」顯示 —，不顯示成「無」
            S6 回合一-2（2026-10-07）：附件下載鈕啟用——先 HEAD 確認（404 toast「找不到附件檔案」），再用 <a download>
            交給瀏覽器下載（殼 jar 已串流透傳，大檔不進頁面記憶體）
            S6 回合四（2026-10-07）：「編輯草稿」改導到 /apps/:id/edit，其餘動作仍是「此功能尚未開放」
            S7 R3（Claude Fable 5.1，2026-10-07）：送審／撤回／簽核接真 API——送審先 confirm；撤回展開面板（原因選填）再 confirm；
            簽核展開面板（意見欄＋同意／退件，退件前端先擋空白再 confirm）。成功 toast 後重新載入（不清空畫面）；
            409 一律 toast 後端訊息並重新載入讓使用者看到最新狀態；400／403 只 toast；401 交給登入處理器。
            其餘動作（補件、執行、治理審核、刪除、AI）仍是「此功能尚未開放」
            S9 R3（Claude Opus 5.5，2026-10-07）：「補件重送」導到 /apps/:id/resubmit；「刪除申請單」展開面板（輸入單號＋原因，
            單號一致且原因不空白才能按），成功導回列表並 toast；歷史版次區每一版底下列出該版的歷次簽核（approvalHistory，
            含撤回、取消那幾輪）；流程動作失敗時 409／403／404 都重新載入（第 104 項 N2），重載後面板對應的權限沒了就收起（N1）
-->
<template>
  <main>
    <header class="hero">
      <div class="head">
        <h1><span class="id">{{ appId }}</span> {{ app?.title ?? '' }}</h1>
        <div v-if="app" class="meta">
          <span v-if="app.prioName" class="prio" :style="prioStyle(app.prioColor)">{{ app.prioName }}</span>
          <StatusPill :code="app.statusCode" />
          <span>版次 v{{ app.verNo ?? '—' }}</span>
          <span>申請人 <b>{{ app.applicant.name || '—' }}</b></span>
          <span>建立 {{ app.createdAt || '—' }}</span>
          <span v-if="app.sourceCode === 'IMPORTED'" class="tag">紙本匯入</span>
        </div>
      </div>
      <router-link class="link" :to="{ name: APP_LIST_ROUTE }">← 回列表</router-link>
    </header>

    <p v-if="error" class="card error" role="alert">{{ error }}</p>
    <p v-else-if="!app" class="card muted">載入中…</p>

    <template v-else>
      <section v-if="actions.length" class="card actions" aria-label="可執行動作">
        <button v-for="a in actions" :key="a.key" class="quiet" :class="{ on: panelOf(a.key) === panel }" type="button"
          :disabled="busy" @click="act(a.key)">{{ a.label }}</button>
      </section>

      <section v-if="panel === 'decide'" class="card flow" aria-label="簽核">
        <h2>簽核<span v-if="currentStepName" class="sub">：{{ currentStepName }}</span></h2>
        <p class="sub">同意時意見可留空，系統會自動填「同意」；退件必須填寫原因，讓申請人知道要補什麼</p>
        <label for="memo">簽核意見</label>
        <textarea id="memo" v-model="memo" rows="3" maxlength="2000" :disabled="busy"
          placeholder="同意可留空；退件請說明原因與要補件的項目"></textarea>
        <div class="actions">
          <button class="go" type="button" :disabled="busy" @click="decide('APPROVE')">✓ 同意</button>
          <button class="danger" type="button" :disabled="busy" @click="decide('REJECT')">✗ 退件</button>
          <button class="quiet" type="button" :disabled="busy" @click="panel = null">取消</button>
        </div>
      </section>

      <section v-else-if="panel === 'recall'" class="card flow" aria-label="撤回到草稿">
        <h2>撤回到草稿</h2>
        <p class="sub">此申請單已送審但尚無任何關卡簽核，可撤回到草稿繼續修改，改完要重新送審；若已有人簽核完成，撤回會被拒絕，只能等退件後補件重送</p>
        <label for="reason">撤回原因（選填）</label>
        <textarea id="reason" v-model="reason" rows="2" maxlength="2000" :disabled="busy"></textarea>
        <div class="actions">
          <button class="go" type="button" :disabled="busy" @click="recall">↩ 撤回到草稿</button>
          <button class="quiet" type="button" :disabled="busy" @click="panel = null">取消</button>
        </div>
      </section>

      <section v-else-if="panel === 'delete'" class="card flow del" aria-label="刪除申請單">
        <h2>刪除申請單</h2>
        <p class="sub">{{ app.permissions.deleteMode === 'ADMIN' ? '管理員刪除：任何狀態都可刪，進行中的簽核會一併取消' : '尚無任何關卡簽核，申請人可自行刪除' }}；刪除後列表與待辦都不再顯示這張單</p>
        <label for="del-id">請輸入申請單號 <b>{{ app.appId }}</b> 確認</label>
        <input id="del-id" v-model="confirmId" type="text" autocomplete="off" :disabled="busy" />
        <label for="del-reason">刪除原因</label>
        <textarea id="del-reason" v-model="delReason" rows="2" maxlength="500" :disabled="busy"></textarea>
        <div class="actions">
          <button class="danger" type="button" :disabled="busy || !canConfirmDelete" @click="remove">✗ 確認刪除</button>
          <button class="quiet" type="button" :disabled="busy" @click="panel = null">取消</button>
        </div>
      </section>

      <article class="card paper">
        <section>
          <h2>一、基本資料</h2>
          <table class="ptable">
            <colgroup><col class="c-h" /><col /><col class="c-h" /><col /></colgroup>
            <tbody>
              <tr><th>申請單號</th><td>{{ app.appId }}</td><th>申請日期</th><td>{{ dash(app.applyDate) }}</td></tr>
              <tr><th>申請單位</th><td>{{ dash(app.applicant.deptName) }}</td><th>申請人</th><td>{{ dash(app.applicant.name) }}</td></tr>
              <tr><th>聯絡電話</th><td>{{ dash(app.applicant.tel) }}</td><th>Email</th><td>{{ dash(app.applicant.email) }}</td></tr>
              <tr>
                <th>執行人員</th><td><b>{{ executorLabel }}</b></td>
                <th>處理方式</th>
                <td>
                  <b>{{ dash(labelOf(WORK_MODE_LABELS, app.workModeCode)) }}</b>
                  <div v-if="app.workModeCode === 'REMOTE' && app.remoteMethod" class="sub">連線方式：{{ app.remoteMethod }}</div>
                </td>
              </tr>
              <template v-if="showSupplier">
                <tr><th>執行廠商</th><td>{{ dash(app.supplier.name) }}</td><th>廠商聯絡人</th><td>{{ dash(app.supplier.contact) }}</td></tr>
                <tr><th>廠商電話</th><td>{{ dash(app.supplier.tel) }}</td><th>廠商進場人數</th><td>{{ app.supplier.headCount ?? 0 }}</td></tr>
              </template>
              <tr><th>簽核流程</th><td colspan="3">{{ dash(app.flowName) }}</td></tr>
            </tbody>
          </table>
        </section>

        <section>
          <h2>二、事件分級</h2>
          <p>
            <span v-if="app.prioCode" class="prio" :style="prioStyle(app.prioColor)">{{ app.prioCode }}</span>
            {{ dash(app.prioName) }}
          </p>
        </section>

        <section>
          <h2>三、作業類別</h2>
          <p v-if="app.categories.length === 0" class="muted">（未填）</p>
          <ul v-else class="chips">
            <li v-for="c in app.categories" :key="c.groupCode + c.code">
              {{ c.name }}<template v-if="c.otherText">（其他）：{{ c.otherText }}</template>
            </li>
          </ul>
        </section>

        <section>
          <h2>四、異動作業內容</h2>
          <table class="ptable">
            <colgroup><col class="c-h" /><col /><col class="c-h" /><col /></colgroup>
            <tbody>
              <tr><th>作業主題</th><td colspan="3">{{ dash(app.workSubject) }}</td></tr>
              <tr>
                <th>異動原因</th>
                <td colspan="3">
                  <ul v-if="app.reasons.length || app.otherReason" class="chips">
                    <li v-for="r in app.reasons" :key="r.code">{{ r.name }}</li>
                    <li v-if="app.otherReason">其他：{{ app.otherReason }}</li>
                  </ul>
                  <span v-else class="muted">（未填）</span>
                </td>
              </tr>
              <tr>
                <th>影響範圍</th>
                <td colspan="3">
                  <ul v-if="app.scopes.length" class="chips">
                    <li v-for="s in app.scopes" :key="s.code">{{ s.name }}</li>
                  </ul>
                  <div><b>影響說明：</b><span class="pre">{{ dash(app.impactDesc) }}</span></div>
                </td>
              </tr>
              <tr>
                <th>設備位置</th>
                <td colspan="3">
                  <template v-if="app.location.omitReason">
                    <span class="tag">不適用</span> <span class="sub">{{ app.location.omitReason }}</span>
                  </template>
                  <template v-else>
                    機房區域：<b>{{ dash(app.location.areaName) }}</b>
                    機櫃編號：<b>{{ dash(app.location.rackName ?? app.location.rackId) }}</b>
                    U 位：<b>{{ dash(uRange) }}</b>
                    <span v-if="app.location.sourceCode" class="tag">{{ labelOf(LOC_SOURCE_LABELS, app.location.sourceCode) }}</span>
                  </template>
                </td>
              </tr>
              <tr>
                <th>涉及設備</th>
                <td colspan="3">
                  <div v-for="eq in app.equipments" :key="eq.seqNo ?? eq.name ?? ''" class="eq">
                    設備名稱：<b>{{ dash(eq.name) }}</b>
                    資產編號：<b>{{ dash(eq.assetNo) }}</b>
                    設備型號：<b>{{ dash(eq.modelNo) }}</b>
                    序號：<b>{{ dash(eq.serialNo) }}</b>
                    <div v-if="eq.purpose || eq.mgmtIp" class="sub">
                      <template v-if="eq.purpose">用途：{{ eq.purpose }}</template>
                      <template v-if="eq.purpose && eq.mgmtIp">　·　</template>
                      <template v-if="eq.mgmtIp">IP：{{ eq.mgmtIp }}</template>
                    </div>
                  </div>
                  <span v-if="app.equipments.length === 0" class="muted">（未填）</span>
                </td>
              </tr>
              <tr>
                <th>預計時間</th>
                <td colspan="3">
                  開始：<b>{{ dash(app.schedule.start) }}</b>
                  結束：<b>{{ dash(app.schedule.end) }}</b>
                  預計耗時：<b>{{ app.schedule.estHours ?? 0 }}</b> 小時
                </td>
              </tr>
              <tr v-if="app.planSteps.length">
                <th>作業步驟</th>
                <td colspan="3">
                  <ol class="steps">
                    <li v-for="p in app.planSteps" :key="p.seqNo ?? p.text ?? ''" class="pre">{{ p.text }}</li>
                  </ol>
                </td>
              </tr>
              <tr><th>詳細作業說明</th><td colspan="3" class="pre">{{ dash(app.workDetail) }}</td></tr>
              <tr>
                <th>風險評估</th><td class="pre">{{ dash(app.riskDesc) }}</td>
                <th>回復計畫</th><td class="pre">{{ dash(app.rollbackPlan) }}</td>
              </tr>
              <tr v-if="app.resubmitMemo"><th>補件說明</th><td colspan="3" class="pre">{{ app.resubmitMemo }}</td></tr>
            </tbody>
          </table>
        </section>

        <section>
          <h2>五、作業執行檢核表</h2>
          <p v-if="app.checklist.length === 0" class="muted">（尚無檢核項目）</p>
          <table v-else class="ptable grid">
            <colgroup><col class="c-seq" /><col /><col class="c-at" /><col class="c-who" /></colgroup>
            <thead><tr><th>序</th><th>檢核項目</th><th>完成時間</th><th>執行人</th></tr></thead>
            <tbody>
              <tr v-for="c in app.checklist" :key="c.seqNo ?? c.code ?? ''">
                <td>{{ c.seqNo }}</td>
                <td><span class="box">{{ c.done ? '■' : '☐' }}</span> {{ c.name }}</td>
                <td>{{ c.doneAt ?? '' }}</td>
                <td>{{ c.executor ?? '' }}</td>
              </tr>
            </tbody>
          </table>
        </section>

        <section>
          <h2>六、實際執行紀錄</h2>
          <p v-if="!app.execution" class="muted">（尚未填寫）</p>
          <table v-else class="ptable">
            <colgroup><col class="c-h" /><col /><col class="c-h" /><col /></colgroup>
            <tbody>
              <tr>
                <th>實際開始時間</th><td>{{ dash(app.execution.actualStart) }}</td>
                <th>實際完成時間</th><td>{{ dash(app.execution.actualEnd) }}</td>
              </tr>
              <tr><th>執行結果</th><td colspan="3">{{ dash(app.execution.resultName) }}</td></tr>
              <tr>
                <th>例外／衍生事件</th>
                <td colspan="3">
                  {{ yesNo(app.execution, app.execution.exception) }}
                  <div v-if="app.execution.exceptionDesc" class="pre">{{ app.execution.exceptionDesc }}</div>
                </td>
              </tr>
              <tr>
                <th>後續追蹤事項</th>
                <td colspan="3">
                  {{ yesNo(app.execution, app.execution.followUp) }}
                  <div v-if="app.execution.followUpDesc" class="pre">{{ app.execution.followUpDesc }}</div>
                </td>
              </tr>
              <tr><th>執行備註</th><td colspan="3" class="pre">{{ dash(app.execution.memo) }}</td></tr>
            </tbody>
          </table>
        </section>

        <section>
          <h2>七、簽核欄</h2>
          <p v-if="app.approval.apprId == null && app.approval.steps.length" class="sub">尚未送審，以下為流程預定的關卡</p>
          <table class="ptable grid signoff">
            <colgroup><col class="c-step" /><col class="c-who2" /><col class="c-st" /><col class="c-at" /><col /></colgroup>
            <thead><tr><th>關卡</th><th>簽核人</th><th>狀態</th><th>日期</th><th>意見</th></tr></thead>
            <tbody>
              <tr>
                <td><b>申請人</b></td>
                <td>{{ dash(app.applicant.name) }}</td>
                <td><span class="pill" :class="app.statusCode === 'DRAFT' ? 'gray' : 'green'">{{ app.statusCode === 'DRAFT' ? '未送出' : '已送出' }}</span></td>
                <td>{{ app.statusCode === 'DRAFT' ? '' : app.createdAt ?? '' }}</td>
                <td class="sub">（送出申請）</td>
              </tr>
              <tr v-for="s in app.approval.steps" :key="(s.seqNo ?? 0) + (s.stepCode ?? '')">
                <td>
                  <b>{{ s.stepName }}</b>
                  <div v-if="s.notifyOnly" class="sub">僅通知</div>
                  <div v-if="s.stepMode === 'POST_HOC'" class="sub">事後補核</div>
                </td>
                <td>
                  {{ s.candidateNames.join(' / ') || '—' }}
                  <div v-if="s.candidateNames.length > 1" class="sub">共 {{ s.candidateNames.length }} 人，任一可簽</div>
                  <div v-if="s.deciderName" class="sub">實際：{{ s.deciderName }}</div>
                </td>
                <td><span class="pill" :class="stepCls(s.statusCode)">{{ labelOf(STEP_STATUS_LABELS, s.statusCode) || '—' }}</span></td>
                <td>{{ s.decidedAt ?? '' }}</td>
                <td class="pre">{{ s.memo ?? '' }}</td>
              </tr>
              <tr>
                <td><b>執行</b></td>
                <td>{{ execRow.who }}</td>
                <td><span class="pill" :class="execRow.cls">{{ execRow.text }}</span></td>
                <td>{{ execRow.at }}</td>
                <td class="pre">{{ execRow.memo }}</td>
              </tr>
              <tr>
                <td><b>執行確認</b></td>
                <td><span class="sub">資訊治理人員</span><div v-if="govRow.who">{{ govRow.who }}</div></td>
                <td><span class="pill" :class="govRow.cls">{{ govRow.text }}</span></td>
                <td>{{ govRow.at }}</td>
                <td class="pre">{{ govRow.memo }}</td>
              </tr>
            </tbody>
          </table>
        </section>
      </article>

      <section class="card">
        <h2>附件</h2>
        <p v-if="app.attachments.length === 0" class="muted">（沒有附件）</p>
        <template v-else>
          <ul class="files">
            <li v-for="f in app.attachments" :key="f.attachId">
              <button class="quiet small" type="button" :disabled="downloading === f.attachId"
                @click="download(f.attachId)">下載</button>
              <span class="fname">{{ f.fileName || '（未命名）' }}</span>
              <span class="sub">{{ kb(f.byteQty) }}・{{ labelOf(ATTACH_OWNER_LABELS, f.ownerType) }}・{{ f.uploadedAt ?? '' }}</span>
            </li>
          </ul>
        </template>
      </section>

      <section v-if="versionGroups.length" class="card">
        <h2>歷史版次與歷次簽核</h2>
        <div v-for="g in versionGroups" :key="g.verNo ?? 0" class="ver">
          <p class="ver-head">
            <b>v{{ g.verNo ?? '—' }}</b>
            <template v-if="g.version">・{{ labelOf(VERSION_CLOSE_LABELS, g.version.closeStatusCode) || '—' }}・{{ g.version.snapAt ?? '' }}</template>
            <template v-else>・目前版次</template>
            <span v-if="g.version?.reason" class="pre"> — {{ g.version.reason }}</span>
          </p>
          <p v-if="g.approvals.length === 0" class="sub">（這一版沒有其他簽核紀錄）</p>
          <div v-for="p in g.approvals" :key="p.apprId" class="past">
            <p class="sub">
              <span class="pill" :class="apprCls(p.statusCode)">{{ labelOf(APPR_STATUS_LABELS, p.statusCode) || '—' }}</span>
              {{ p.startedAt ?? '' }} ～ {{ p.closedAt ?? '' }}
            </p>
            <table class="ptable grid">
              <colgroup><col class="c-step" /><col class="c-who2" /><col class="c-st" /><col class="c-at" /><col /></colgroup>
              <thead><tr><th>關卡</th><th>簽核人</th><th>狀態</th><th>日期</th><th>意見</th></tr></thead>
              <tbody>
                <tr v-for="s in p.steps" :key="(s.seqNo ?? 0) + (s.stepCode ?? '')">
                  <td><b>{{ s.stepName }}</b></td>
                  <td>{{ dash(s.deciderName) }}</td>
                  <td><span class="pill" :class="stepCls(s.statusCode)">{{ labelOf(STEP_STATUS_LABELS, s.statusCode) || '—' }}</span></td>
                  <td>{{ s.decidedAt ?? '' }}</td>
                  <td class="pre">{{ s.memo ?? '' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <section v-if="app.events.length" class="card">
        <h2>事件紀錄</h2>
        <table class="ptable grid">
          <colgroup><col class="c-at" /><col class="c-seq" /><col class="c-st" /><col class="c-who2" /><col /></colgroup>
          <thead><tr><th>時間</th><th>版次</th><th>事件</th><th>人員</th><th>說明</th></tr></thead>
          <tbody>
            <tr v-for="e in app.events" :key="e.eventId">
              <td>{{ e.at ?? '' }}</td>
              <td>v{{ e.verNo ?? '—' }}</td>
              <td>{{ labelOf(EVENT_LABELS, e.code) }}</td>
              <td>{{ e.userName ?? '' }}</td>
              <td class="pre">{{ e.memo ?? '' }}</td>
            </tr>
          </tbody>
        </table>
      </section>
    </template>

    <ToastHost />
  </main>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StatusPill from '../components/StatusPill.vue'
import ToastHost from '../components/ToastHost.vue'
import { AxiosError } from 'axios'
import { attachmentUrl, checkAttachment, decideApp, deleteApp, getApp, recallApp, submitApp } from '../api/apps'
import { errorMessage, isUnauthorized } from '../api/http'
import { useToast } from '../composables/useToast'
import { APP_EDIT_ROUTE, APP_LIST_ROUTE, APP_RESUBMIT_ROUTE } from '../router/names'
import {
  APPR_STATUS_LABELS,
  ATTACH_OWNER_LABELS,
  EVENT_LABELS,
  LOC_SOURCE_LABELS,
  STEP_STATUS_LABELS,
  VERSION_CLOSE_LABELS,
  WORK_MODE_LABELS,
  labelOf,
  type AppDetail,
  type AppEvent,
  type AppExecution,
  type AppPastApproval,
  type AppPermissions,
  type AppVersion,
  type Decision,
  type FlowActionResponse
} from '../types/app'
import { kb, prioStyle } from '../utils/format'

const route = useRoute()
const router = useRouter()
const { toast } = useToast()

const app = ref<AppDetail | null>(null)
const error = ref('')

const appId = computed(() => {
  const id = route.params.id
  return typeof id === 'string' ? id : ''
})

type PermKey = Exclude<keyof AppPermissions, 'deleteMode'>

/** 順序比照舊檢視頁由上到下的操作區塊 */
const ACTIONS: { key: PermKey; label: string }[] = [
  { key: 'canEditDraft', label: '編輯草稿' },
  { key: 'canSubmit', label: '送審' },
  { key: 'canAiReview', label: 'AI 風險審查' },
  { key: 'canDecide', label: '簽核' },
  { key: 'canRecall', label: '撤回到草稿' },
  { key: 'canResubmit', label: '補件重送' },
  { key: 'canExecute', label: '填寫執行紀錄' },
  { key: 'canReview', label: '治理審核' },
  { key: 'canDelete', label: '刪除申請單' }
]

const actions = computed(() => (app.value ? ACTIONS.filter(a => app.value?.permissions[a.key] === true) : []))

type Panel = 'decide' | 'recall' | 'delete' | null

/** 展開中的操作面板（簽核意見／撤回原因／刪除確認）；送審不需要面板，confirm 後直接送 */
const panel = ref<Panel>(null)
const memo = ref('')
const reason = ref('')
const confirmId = ref('')
const delReason = ref('')
/** 送審／撤回／簽核／刪除進行中：鎖住全部動作鈕，避免連點或同時送兩個動作 */
const busy = ref(false)

/** 面板與開啟它所需的權限；重新載入後權限沒了就收起面板 */
const PANEL_PERMS: Record<Exclude<Panel, null>, PermKey> = { decide: 'canDecide', recall: 'canRecall', delete: 'canDelete' }

function panelOf(key: PermKey): Panel {
  for (const [p, k] of Object.entries(PANEL_PERMS)) {
    if (k === key) return p as Exclude<Panel, null>
  }
  return null
}

/** 單號要一字不差（前後空白不算），原因不得空白；後端會再檢查一次 */
const canConfirmDelete = computed(() => confirmId.value.trim() === appId.value && delReason.value.trim() !== '')

/** 目前輪到的關卡名稱（序號最小的 PENDING），給簽核面板標題用 */
const currentStepName = computed(() => {
  const steps = app.value?.approval.steps ?? []
  return steps.find(s => s.statusCode === 'PENDING')?.stepName ?? ''
})

/** 已開放的動作導到對應頁或展開面板；其餘還沒做的出 toast */
function act(key: PermKey): void {
  if (key === 'canEditDraft') {
    void router.push({ name: APP_EDIT_ROUTE, params: { id: appId.value } })
    return
  }
  if (key === 'canResubmit') {
    void router.push({ name: APP_RESUBMIT_ROUTE, params: { id: appId.value } })
    return
  }
  if (key === 'canSubmit') {
    void submit()
    return
  }
  const p = panelOf(key)
  if (p) {
    panel.value = panel.value === p ? null : p
    return
  }
  toast('此功能尚未開放', 'amber')
}

function statusOf(e: unknown): number | undefined {
  return e instanceof AxiosError ? e.response?.status : undefined
}

/** 409（版本過期、狀態已變、關卡被搶簽）、403（權限已變）、404（已被刪除）：畫面上的資料已過期，要重新載入 */
function isStale(e: unknown): boolean {
  const s = statusOf(e)
  return s === 409 || s === 403 || s === 404
}

/**
 * 三個流程動作共用：帶目前的 rowVerNo 呼叫 API，成功就收面板、toast、重新載入；
 * 失敗 toast 後端訊息；資料已過期（isStale）另外重新載入，讓使用者直接看到最新狀態
 */
async function runFlow(okMsg: string, call: (rowVerNo: number) => Promise<FlowActionResponse>): Promise<void> {
  const a = app.value
  if (!a || busy.value) return
  busy.value = true
  try {
    await call(a.rowVerNo)
    panel.value = null
    memo.value = ''
    reason.value = ''
    toast(okMsg, 'teal')
    await load(appId.value, true)
  } catch (e: unknown) {
    if (isUnauthorized(e)) return
    toast(errorMessage(e), 'red')
    if (isStale(e)) await load(appId.value, true)
  } finally {
    busy.value = false
  }
}

/** 刪除成功導回列表（這張單已查不到）；失敗同 runFlow */
async function remove(): Promise<void> {
  const a = app.value
  if (!a || busy.value || !canConfirmDelete.value) return
  busy.value = true
  try {
    await deleteApp(appId.value, { rowVerNo: a.rowVerNo, confirmId: confirmId.value.trim(), reason: delReason.value.trim() })
    toast(`已刪除申請單 ${appId.value}`, 'teal')
    await router.push({ name: APP_LIST_ROUTE })
  } catch (e: unknown) {
    if (isUnauthorized(e)) return
    toast(errorMessage(e), 'red')
    if (isStale(e)) await load(appId.value, true)
  } finally {
    busy.value = false
  }
}

async function submit(): Promise<void> {
  if (!window.confirm('確定要正式送審？送審後申請單將進入第一關簽核流程。')) return
  await runFlow('已送審，申請單進入簽核流程', v => submitApp(appId.value, { rowVerNo: v }))
}

async function recall(): Promise<void> {
  if (!window.confirm('確定撤回到草稿？撤回後要重新送審才會再進入簽核流程。')) return
  const r = reason.value.trim()
  await runFlow('已撤回到草稿', v => recallApp(appId.value, r ? { rowVerNo: v, reason: r } : { rowVerNo: v }))
}

async function decide(d: Decision): Promise<void> {
  const m = memo.value.trim()
  if (d === 'REJECT') {
    if (!m) {
      toast('退件時必須填寫原因，讓申請人知道要補什麼', 'red')
      return
    }
    if (!window.confirm('確定要退件給申請人？退件後申請單結束這一輪簽核，申請人需補件重送。')) return
  }
  await runFlow(d === 'APPROVE' ? '已同意' : '已退件', v => decideApp(appId.value, { rowVerNo: v, decision: d, memo: m }))
}

const downloading = ref<number | null>(null)

/** HEAD 沒有本文，拿不到後端訊息，依狀態碼給文字 */
async function download(attachId: number): Promise<void> {
  const id = appId.value
  downloading.value = attachId
  try {
    await checkAttachment(id, attachId)
  } catch (e) {
    if (!isUnauthorized(e)) {
      const status = e instanceof AxiosError ? e.response?.status : undefined
      toast(status === 404 ? '找不到附件檔案' : errorMessage(e), 'red')
    }
    return
  } finally {
    downloading.value = null
  }
  const a = document.createElement('a')
  a.href = attachmentUrl(id, attachId)
  a.download = ''
  document.body.appendChild(a)
  a.click()
  a.remove()
}

function dash(v: string | null | undefined): string {
  return v == null || v === '' ? '—' : v
}

/** 執行結果還沒填時旗標只是預設 0，不代表「無」 */
function yesNo(ex: AppExecution, flag: boolean): string {
  if (!ex.resultCode) return '—'
  return flag ? '有' : '無'
}

const executorLabel = computed(() => {
  if (!app.value) return '—'
  const parts: string[] = []
  if (app.value.selfExec) parts.push('自行處理 (內部 IT)')
  if (app.value.supplierExec) parts.push('委外廠商')
  return parts.length ? parts.join(' + ') : '—'
})

const showSupplier = computed(() => {
  const a = app.value
  return !!a && (a.supplierExec || !!a.supplier.name || !!a.supplier.contact || !!a.supplier.tel)
})

const uRange = computed(() => {
  const l = app.value?.location
  if (!l) return ''
  if (l.uRange) return l.uRange
  if (l.uStart != null && l.uEnd != null) return l.uStart === l.uEnd ? String(l.uStart) : `${l.uStart}-${l.uEnd}`
  return ''
})

function apprCls(code: string | null): string {
  switch (code) {
    case 'APPROVED': return 'green'
    case 'REJECTED': return 'red'
    case 'PENDING': return 'blue'
    default: return 'gray'
  }
}

interface VersionGroup { verNo: number | null; version: AppVersion | null; approvals: AppPastApproval[] }

/** 每個舊版次一組，底下掛該版的過去簽核；目前版次若有撤回、取消那幾輪也獨立成一組（排最後） */
const versionGroups = computed<VersionGroup[]>(() => {
  const a = app.value
  if (!a) return []
  const history = a.approvalHistory
  const groups: VersionGroup[] = a.versions.map(v => ({ verNo: v.verNo, version: v, approvals: history.filter(h => h.verNo === v.verNo) }))
  const known = new Set(a.versions.map(v => v.verNo))
  const rest = history.filter(h => !known.has(h.verNo))
  if (rest.length) groups.push({ verNo: a.verNo, version: null, approvals: rest })
  return groups
})

function stepCls(code: string | null): string {
  switch (code) {
    case 'APPROVED': return 'green'
    case 'REJECTED': return 'red'
    case 'PENDING': return 'blue'
    default: return 'gray'
  }
}

/** 目前版次最後一筆指定事件 */
function lastEvent(codes: string[]): AppEvent | null {
  const a = app.value
  if (!a) return null
  const hit = a.events.filter(e => e.verNo === a.verNo && e.code != null && codes.includes(e.code))
  return hit.length ? hit[hit.length - 1] : null
}

interface SignRow { who: string; text: string; cls: string; at: string; memo: string }

const execRow = computed<SignRow>(() => {
  const a = app.value
  const ex = a?.execution ?? null
  const rejected = a?.statusCode === 'REJECTED' ? lastEvent(['EXEC_REJECT']) : null
  if (rejected) return { who: rejected.userName ?? '', text: '退回', cls: 'red', at: rejected.at ?? '', memo: rejected.memo ?? '' }
  const who = ex?.executorName || '機房管理員 / 申請人'
  if (ex?.resultCode) return { who, text: '已填寫', cls: 'green', at: ex.closedAt ?? '', memo: ex.resultName ?? '' }
  if (a?.statusCode === 'IN_EXECUTION') return { who, text: '執行中', cls: 'blue', at: '', memo: '' }
  return { who, text: '待執行', cls: 'gray', at: '', memo: '' }
})

const govRow = computed<SignRow>(() => {
  const a = app.value
  const ev = lastEvent(['GOV_PASS', 'GOV_RETURN'])
  const base = { who: ev?.userName ?? '', at: ev?.at ?? '', memo: ev?.memo ?? '' }
  if (a?.statusCode === 'EXECUTED') return { ...base, text: '已結案', cls: 'green' }
  if (a?.statusCode === 'PENDING_REVIEW') return { ...base, text: '待審核', cls: 'orange' }
  if (a?.statusCode === 'REJECTED' && ev?.code === 'GOV_RETURN') return { ...base, text: '退回', cls: 'red' }
  return { who: '', at: '', memo: '', text: '—', cls: 'gray' }
})

/** 每次載入遞增；回應回來時編號不是最新就丟掉，避免慢回來的舊單蓋掉新單 */
let seq = 0

/** keep：動作成功或資料過期後重新載入，保留畫面不閃「載入中」；換單號時不保留並收起面板 */
async function load(id: string, keep = false): Promise<void> {
  const mine = ++seq
  if (!keep) {
    app.value = null
    panel.value = null
    memo.value = ''
    reason.value = ''
    confirmId.value = ''
    delReason.value = ''
  }
  error.value = ''
  try {
    const data = await getApp(id)
    if (mine !== seq) return
    app.value = data
    // 重載後已沒有開這個面板的權限（例如關卡被別人簽走）：收起來，免得留著一個一定會失敗的按鈕
    if (panel.value && data.permissions[PANEL_PERMS[panel.value]] !== true) panel.value = null
  } catch (e: unknown) {
    if (mine !== seq) return
    if (isUnauthorized(e)) {
      error.value = '尚未登入'
      return
    }
    error.value = errorMessage(e)
    toast(error.value, 'red')
  }
}

watch(appId, id => void load(id), { immediate: true })
</script>

<style scoped>
main { width: 100%; max-width: 1180px; margin: 0 auto; padding: 24px 20px; }

.hero { margin-bottom: 16px; display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; }
.head { min-width: 0; }
.hero h1 { margin: 0; font-size: 26px; color: var(--navy); overflow-wrap: anywhere; }
.hero .id { font-size: 18px; color: var(--gray); margin-right: 6px; }
.meta { display: flex; flex-wrap: wrap; align-items: center; gap: 6px 14px; margin-top: 6px; font-size: 16px; }
.link { color: var(--blue); font-size: 17px; white-space: nowrap; }

.card { background: #fff; border: 1px solid var(--line); border-radius: 10px; padding: 16px 20px; margin: 0 0 16px; overflow-wrap: anywhere; }
.hero, .meta, .chips li { min-width: 0; overflow-wrap: anywhere; }
.card h2, .paper h2 { margin: 0 0 10px; font-size: 21px; color: var(--teal-dark); }
.paper section + section { margin-top: 22px; }

.actions { display: flex; flex-wrap: wrap; gap: 10px; }
.quiet { background: #fff; border: 1px solid var(--line); color: var(--dark); border-radius: 7px; padding: 8px 18px; font-size: 17px; cursor: pointer; }
.quiet:disabled { opacity: .55; cursor: default; }
.quiet.small { min-height: 36px; padding: 2px 12px; font-size: 15px; }
.quiet.on { border-color: var(--teal); color: var(--teal-dark); background: var(--teal-bg); }

.flow label { display: block; font-weight: 700; margin: 10px 0 4px; }
.flow textarea { width: 100%; box-sizing: border-box; border: 1px solid var(--line); border-radius: 7px; padding: 8px 10px; font: inherit; font-size: 17px; resize: vertical; }
.flow textarea:disabled, .flow input:disabled { background: var(--bg); }
.flow input { width: 100%; max-width: 360px; box-sizing: border-box; border: 1px solid var(--line); border-radius: 7px; padding: 6px 10px; font: inherit; font-size: 17px; }
.del { border-color: var(--red); }
.ver + .ver { margin-top: 14px; padding-top: 10px; border-top: 1px solid var(--line); }
.ver-head { margin: 0 0 6px; }
.past { margin: 6px 0 0 12px; min-width: 0; }
.past .sub { margin: 4px 0; }
.flow .actions { margin-top: 12px; }
.go { background: var(--teal); border: 1px solid var(--teal); color: #fff; border-radius: 7px; padding: 8px 18px; font-size: 17px; cursor: pointer; }
.danger { background: #fff; border: 1px solid var(--red); color: var(--red); border-radius: 7px; padding: 8px 18px; font-size: 17px; cursor: pointer; }
.go:disabled, .danger:disabled { opacity: .55; cursor: default; }

.muted { color: var(--gray); margin: 4px 0; }
.error { color: var(--red); font-weight: 700; }
.sub { color: var(--gray); font-size: 14px; }
.pre { white-space: pre-wrap; }
.tag { display: inline-block; padding: 0 8px; border-radius: 6px; background: var(--teal-bg); color: var(--teal-dark); font-size: 14px; font-weight: 700; }
.prio { display: inline-block; padding: 0 8px; border-radius: 6px; background: var(--line); font-weight: 700; font-size: 15px; }
.pill { display: inline-block; padding: 0 10px; border-radius: 999px; font-size: 14px; font-weight: 700; border: 1px solid currentColor; white-space: nowrap; }
.pill.gray { color: var(--gray); }
.pill.blue { color: var(--blue); }
.pill.green { color: var(--green); }
.pill.red { color: var(--red); }
.pill.orange { color: var(--orange); }

.ptable { width: 100%; table-layout: fixed; border-collapse: collapse; font-size: 16px; }
.ptable th, .ptable td { border: 1px solid var(--line); padding: 6px 8px; vertical-align: top; text-align: left; overflow-wrap: anywhere; }
.ptable tbody th { background: var(--bg); color: var(--dark); font-weight: 700; }
.c-h { width: 128px; }
.ptable.grid thead th { background: var(--bg); font-size: 15px; }
.c-seq { width: 56px; }
.c-at { width: 150px; }
.c-who { width: 140px; }
.c-who2 { width: 180px; }
.c-step { width: 150px; }
.c-st { width: 110px; }
.box { font-family: monospace; }

.chips { list-style: none; margin: 0; padding: 0; display: flex; flex-wrap: wrap; gap: 6px 8px; }
.chips li { padding: 0 10px; border: 1px solid var(--teal); border-radius: 6px; color: var(--teal-dark); font-size: 15px; }
.eq + .eq { margin-top: 6px; padding-top: 6px; border-top: 1px dashed var(--line); }
.steps { margin: 0; padding-left: 22px; }

.files, .plain { list-style: none; margin: 0; padding: 0; }
.files li, .plain li { padding: 6px 0; border-bottom: 1px solid var(--line); display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.plain li { display: block; }
.fname { font-weight: 700; overflow-wrap: anywhere; }
</style>
