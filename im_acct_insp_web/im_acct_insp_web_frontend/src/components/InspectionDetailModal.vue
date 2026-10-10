<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: 新增：巡檢明細彈窗（原型 openInspectionModal；GET /inspections/{id}）
            列異常項目；未處理者可填處理說明後「標記已處理」（POST /inspections/{id}/items/{itemId}/fix，說明必填）
            標記成功後重載明細並通知列表重整（changed）
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: v2.0（開發文件 spec_im_acct_insp_v2.0.md 第 4.14、8.3 節）：列出各項結果（大類／項目用快照名稱）、
            異常項目的處理資訊（處理人依 4.14：有代理身分顯示「{代理}（實際操作人：{實際}）」）；
            巡檢人員同規則；「標記已處理」只對有 INSP_RESOLVE 者顯示，處理方式必填（E10）
-->
<template>
  <BaseModal :title="`巡檢紀錄 — ${inspId}`" :sub="subText" wide @close="emit('close')">
    <p v-if="loading" class="hint">載入中…</p>
    <template v-else-if="detail">
      <template v-for="g in groups" :key="g.sectName">
        <div class="divider-lbl">{{ g.sectName }}</div>
        <div v-for="r in g.items" :key="r.inspItemId" class="kv result-item" :class="{ 'excpt-item': r.resultCode === 'EXCPT' }" :data-item="r.inspItemId">
          <div class="k">
            {{ r.itemNameSnap }}
            <span class="tag" :class="r.resultCode === 'EXCPT' ? (isOn(r.isFixed) ? 'tag-ok' : 'tag-warn') : 'tag-ok'">
              {{ r.resultCode === 'EXCPT' ? (isOn(r.isFixed) ? '異常（已處理）' : '異常') : label(INSP_RESULT_LABEL, r.resultCode) }}
            </span>
          </div>
          <template v-if="r.resultCode === 'EXCPT'">
            <div class="v">{{ r.excptDescr || '（未填寫異常說明）' }}</div>
            <div v-if="isOn(r.isFixed)" class="fixed">
              ✓ 已處理（{{ r.fixDate || '—' }}，{{ personText(r.fixByName, r.fixActAsName) }}）：{{ r.fixDescr }}
            </div>
            <div v-else-if="canResolve" class="fix-box">
              <textarea v-model="notes[r.inspItemId]" rows="2" placeholder="請說明處理方式與結果" :aria-label="`${r.itemNameSnap} 處理方式與結果`"></textarea>
              <button type="button" class="btn btn-ok fix-btn" :disabled="busy" @click="fix(r.inspItemId)">標記已處理</button>
            </div>
          </template>
        </div>
      </template>
      <div v-if="detail.results.length === 0" class="kv"><div class="v hint">此巡檢單沒有項目結果</div></div>
    </template>
  </BaseModal>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import BaseModal from './BaseModal.vue'
import { errorMessage, errorStatus } from '../api/errors'
import { fixInspItem, getInspection } from '../api/inspection'
import { useSession } from '../composables/useSession'
import { useToast } from '../composables/useToast'
import type { InspResult, InspectionDetail } from '../types/inspection'
import { INSP_RESULT_LABEL, INSP_STATE_LABEL, isOn, label, personText } from '../utils/labels'

const props = defineProps<{ inspId: string }>()
const emit = defineEmits<{ close: []; changed: [] }>()
const { toast } = useToast()
const { can } = useSession()

const detail = ref<InspectionDetail | null>(null)
const loading = ref(true)
const busy = ref(false)
const notes = reactive<Record<string, string>>({})

const canResolve = computed(() => can('INSP_RESOLVE'))
/** 依快照大類分組，保留後端順序 */
const groups = computed(() => {
  const out: { sectName: string; items: InspResult[] }[] = []
  for (const r of detail.value?.results ?? []) {
    const last = out[out.length - 1]
    if (last && last.sectName === r.sectNameSnap) {
      last.items.push(r)
    } else {
      out.push({ sectName: r.sectNameSnap, items: [r] })
    }
  }
  return out
})
const subText = computed(() => {
  const d = detail.value
  if (!d) return ''
  return `巡檢時間：${d.inspDate}　·　巡檢人員：${personText(d.inspByName, d.actAsName)}　·　狀態：${label(INSP_STATE_LABEL, d.inspStateCode)}`
})

async function load(): Promise<void> {
  try {
    detail.value = await getInspection(props.inspId)
  } catch (e) {
    toast(errorMessage(e, '查詢失敗，請稍後再試'), 'amber')
  } finally {
    loading.value = false
  }
}

async function fix(itemId: string): Promise<void> {
  const text = (notes[itemId] ?? '').trim()
  if (!text) {
    toast('請填寫處理方式與結果', 'amber')
    return
  }
  busy.value = true
  try {
    await fixInspItem(props.inspId, itemId, { fixDescr: text })
    toast(`${props.inspId} 的異常項目已標記處理完畢`, 'teal')
    notes[itemId] = ''
    await load()
    emit('changed')
  } catch (e) {
    toast(errorMessage(e, '標記失敗，請稍後再試'), 'amber')
    // 409（已處理或不是異常項目）：重載明細與列表，讓畫面與後端一致
    if (errorStatus(e) === 409) {
      await load()
      emit('changed')
    }
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.result-item .k { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; color: var(--dark); font-size: 18px; }
.result-item .tag { font-size: 15px; }
.fixed { margin-top: 4px; color: var(--green); font-size: 17px; }
.fix-box { margin-top: 6px; display: flex; flex-direction: column; gap: 6px; align-items: flex-start; }
</style>
