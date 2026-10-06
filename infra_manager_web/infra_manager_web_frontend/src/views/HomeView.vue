<!--
  AI版本  : Claude Fable 5.1 (claude-fable-5-1)
  修改日期: 2026-10-05
  變更說明: 新增：首頁殼（S1）。顯示系統名稱與後端／資料庫連線狀態，功能選單待 S2 以後補
            載入時呼叫 GET /health：成功 → 顯示後端 UP、DB 依回應；失敗 → 後端 DOWN 並出 toast（不顯示成查無資料）
            沿用 main.css 色票與 19px 基礎字級，不用元件庫
-->
<template>
  <main>
    <header class="hero">
      <h1>機房設備異動申請系統</h1>
      <p class="sub">Infra Manager</p>
    </header>

    <section class="card">
      <h2>系統狀態</h2>
      <dl class="status">
        <dt>後端服務</dt>
        <dd :class="backendCls">{{ backendText }}</dd>
        <dt>資料庫</dt>
        <dd :class="dbCls">{{ dbText }}</dd>
        <dt>後端時間</dt>
        <dd>{{ health?.time ?? '—' }}</dd>
      </dl>
      <button class="go" type="button" :disabled="loading" @click="load">
        {{ loading ? '檢查中…' : '重新檢查' }}
      </button>
    </section>

    <div id="toasts">
      <div v-for="t in toasts" :key="t.id" class="toast" :class="t.cls">{{ t.msg }}</div>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getHealth } from '../api/health'
import { useToast } from '../composables/useToast'
import type { HealthStatus } from '../types/health'

type Phase = 'idle' | 'ok' | 'fail'

const { toasts, toast } = useToast()
const health = ref<HealthStatus | null>(null)
const phase = ref<Phase>('idle')
const loading = ref(false)

const backendText = computed(() => {
  if (phase.value === 'idle') return '檢查中…'
  return phase.value === 'ok' ? '正常' : '無法連線'
})
const backendCls = computed(() => (phase.value === 'ok' ? 'up' : phase.value === 'fail' ? 'down' : ''))

const dbText = computed(() => {
  if (phase.value !== 'ok' || !health.value) return phase.value === 'fail' ? '未知' : '檢查中…'
  return health.value.db === 'UP' ? '正常' : '無法連線'
})
const dbCls = computed(() => {
  if (phase.value !== 'ok' || !health.value) return ''
  return health.value.db === 'UP' ? 'up' : 'down'
})

async function load(): Promise<void> {
  loading.value = true
  try {
    health.value = await getHealth()
    phase.value = 'ok'
    if (health.value.db !== 'UP') {
      toast('資料庫無法連線', 'amber')
    }
  } catch {
    phase.value = 'fail'
    toast('後端服務呼叫失敗，請稍後再試', 'amber')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
main { width: 100%; max-width: 960px; margin: 0 auto; padding: 24px 20px; }

.hero { margin-bottom: 20px; }
.hero h1 { margin: 0; font-size: 30px; color: var(--navy); }
.sub { margin: 4px 0 0; color: var(--gray); font-size: 17px; }

.card { background: #fff; border: 1px solid var(--line); border-radius: 10px; padding: 16px 20px; }
.card h2 { margin: 0 0 12px; font-size: 22px; color: var(--teal-dark); }

.status { display: grid; grid-template-columns: 140px 1fr; row-gap: 8px; column-gap: 12px; margin: 0 0 16px; }
.status dt { color: var(--gray); font-size: 17px; }
.status dd { margin: 0; font-weight: 700; }
.status dd.up { color: var(--green); }
.status dd.down { color: var(--red); }

.go { background: var(--teal); border: none; color: #fff; font-weight: 700; border-radius: 7px;
  padding: 12px 24px; font-size: 19px; cursor: pointer; min-width: 160px; }
.go:hover:not(:disabled) { filter: brightness(1.08); }
.go:disabled { opacity: .55; cursor: default; }

#toasts { position: fixed; top: 18px; right: 18px; display: flex; flex-direction: column; gap: 10px; z-index: 50; }
.toast { background: var(--navy); color: #fff; padding: 16px 22px; border-radius: 9px; font-size: 19px;
  box-shadow: 0 8px 20px rgba(0,0,0,.25); max-width: 460px; line-height: 1.5; }
.toast.teal { background: var(--teal-dark); }
.toast.amber { background: #92400e; }
.toast.red { background: var(--red); }
</style>
