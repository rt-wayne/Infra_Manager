<!--
  AI版本  : Claude Fable 5.1 (claude-fable-5-1)
  修改日期: 2026-10-05
  變更說明: 新增：首頁殼（S1）。顯示系統名稱與後端／資料庫連線狀態，功能選單待 S4 以後補
            載入時呼叫 GET /health：成功 → 顯示後端 UP、DB 依回應；失敗 → 後端 DOWN 並出 toast（不顯示成查無資料）
            S2 回合三（2026-10-06）：右上角顯示登入者姓名、修改密碼連結與登出鈕；toast 區抽成 ToastHost 元件
            沿用 main.css 色票與 19px 基礎字級，不用元件庫
            S4 回合二（Claude Opus 5.5，2026-10-06）：加「功能」卡片，連到 /apps 申請單列表
            S6 回合四（Claude Opus 5.5，2026-10-07）：「功能」卡片加「新增申請單」連到 /apps/new
-->
<template>
  <main>
    <header class="hero">
      <div>
        <h1>機房設備異動申請系統</h1>
        <p class="sub">Infra Manager</p>
      </div>
      <div class="who" v-if="auth.loggedIn.value">
        <span class="name">{{ auth.userName.value }}</span>
        <router-link class="link" :to="CHANGE_PASSWORD_PATH">修改密碼</router-link>
        <button class="quiet" type="button" :disabled="leaving" @click="doLogout">登出</button>
      </div>
    </header>

    <section class="card menu">
      <h2>功能</h2>
      <div class="menu-links">
        <router-link class="go" to="/apps">申請單列表</router-link>
        <router-link class="go" to="/apps/new">新增申請單</router-link>
      </div>
    </section>

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

    <ToastHost />
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import ToastHost from '../components/ToastHost.vue'
import { getHealth } from '../api/health'
import { errorMessage } from '../api/http'
import { CHANGE_PASSWORD_PATH, LOGIN_PATH, useAuth } from '../composables/useAuth'
import { useToast } from '../composables/useToast'
import type { HealthStatus } from '../types/health'

type Phase = 'idle' | 'ok' | 'fail'

const router = useRouter()
const auth = useAuth()
const { toast } = useToast()
const leaving = ref(false)

async function doLogout(): Promise<void> {
  leaving.value = true
  try {
    await auth.logout()
  } catch (e: unknown) {
    toast(errorMessage(e, '登出失敗'), 'amber')
  } finally {
    leaving.value = false
  }
  await router.replace(LOGIN_PATH)
}
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

.hero { margin-bottom: 20px; display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; flex-wrap: wrap; }
.hero h1 { margin: 0; font-size: 30px; color: var(--navy); }
.sub { margin: 4px 0 0; color: var(--gray); font-size: 17px; }
.who { display: flex; align-items: center; gap: 14px; }
.who .name { font-weight: 700; color: var(--teal-dark); }
.who .link { color: var(--blue); font-size: 17px; }
.quiet { background: #fff; border: 1px solid var(--line); color: var(--dark); border-radius: 7px; padding: 8px 18px; font-size: 17px; cursor: pointer; }
.quiet:disabled { opacity: .55; cursor: default; }

.card { background: #fff; border: 1px solid var(--line); border-radius: 10px; padding: 16px 20px; }
.card h2 { margin: 0 0 12px; font-size: 22px; color: var(--teal-dark); }
.menu { margin-bottom: 16px; }
.menu .go { display: inline-block; text-decoration: none; text-align: center; }
.menu-links { display: flex; flex-wrap: wrap; gap: 12px; }

.status { display: grid; grid-template-columns: 140px 1fr; row-gap: 8px; column-gap: 12px; margin: 0 0 16px; }
.status dt { color: var(--gray); font-size: 17px; }
.status dd { margin: 0; font-weight: 700; }
.status dd.up { color: var(--green); }
.status dd.down { color: var(--red); }

.go { background: var(--teal); border: none; color: #fff; font-weight: 700; border-radius: 7px;
  padding: 12px 24px; font-size: 19px; cursor: pointer; min-width: 160px; }
.go:hover:not(:disabled) { filter: brightness(1.08); }
.go:disabled { opacity: .55; cursor: default; }
</style>
