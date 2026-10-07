<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: 新增：申請單表單「一、基本資料～四、異動作業內容」四個區塊（S5 R2，自 AppFormView 原樣抽出），
            申請單表單與範本編輯頁共用，S5 R3 套用範本也是填這份狀態
            - kind="app"：同原本申請單表單（標題必填記號、申請人與聯絡資料、預定開始／結束時間＋自動算耗時）
            - kind="template"：範本不存申請人聯絡資料與開始／結束時間（同舊系統），只留預計耗時；標題可空（套用後再填）
            - badField：400 回應帶的欄位路徑，對應欄位標紅；data-field 供父層 querySelector 捲動定位
-->
<template>
  <fieldset class="card sec">
    <legend>一、基本資料</legend>
    <div class="grid">
      <div class="cell wide">
        <label for="f-title">標題 <span v-if="kind === 'app'" class="req">*</span><span v-else class="sub">（選填，套用後仍可修改）</span></label>
        <input id="f-title" v-model="form.title" v-bind="fx('title')" placeholder="例：FortiADC 韌體升級" />
      </div>
      <template v-if="kind === 'app'">
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
      </template>
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
      <div v-if="kind === 'app'" class="grid three">
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
      <div v-else class="grid three">
        <div class="cell">
          <label for="f-hours" class="small">預計耗時 (小時)</label>
          <input id="f-hours" v-model="form.estHours" type="number" min="0" max="9999.99" step="0.5" />
        </div>
      </div>
      <p class="sub">{{ kind === 'app' ? '開始與結束都填好時自動算耗時，之後仍可手改' : '範本只存預計耗時，開始與結束時間在申請單上填' }}</p>
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
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { WORK_MODE_LABELS, type AppDraftForm, type FormOption } from '../types/app'
import { MAX_ROWS, emptyEquipment, hoursBetween } from '../utils/draftForm'
import { prioStyle } from '../utils/format'

const form = defineModel<AppDraftForm>({ required: true })
const props = defineProps<{
  options: FormOption[]
  badField: string | null
  kind: 'app' | 'template'
  applicantName?: string
}>()

function group(code: string): FormOption[] {
  return props.options.filter(o => o.groupCode === code)
}
const prios = computed(() => group('PRIO'))
const catgs = computed(() => group('CATG'))
const reasons = computed(() => group('REASON'))
const scopes = computed(() => group('SCOPE'))

function itemsOf(catgId: number): FormOption[] {
  return props.options.filter(o => o.groupCode === 'CATG_ITEM' && o.upFormOptionId === catgId)
}

/** 欄位的 data-field（給 400 定位）與標紅 class */
function fx(path: string): { 'data-field': string; class: { bad: boolean } } {
  return { 'data-field': path, class: { bad: props.badField === path } }
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
</script>

<style scoped>
.card { background: #fff; border: 1px solid var(--line); border-radius: 10px; padding: 16px 20px; margin: 0 0 16px; overflow-wrap: anywhere; min-width: 0; }
.sec legend { font-size: 21px; font-weight: 700; color: var(--teal-dark); padding: 0 6px; }
.muted { color: var(--gray); margin: 4px 0; }
.sub { color: var(--gray); font-size: 14px; font-weight: 400; }
.req { color: var(--red); }

input:not([type="checkbox"]):not([type="radio"]), textarea {
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
</style>
