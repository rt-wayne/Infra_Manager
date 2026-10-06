<!--
  AI版本  : Claude Fable 5.1 (claude-fable-5-1)
  修改日期: 2026-10-06
  變更說明: 新增：登入頁（S2 回合三）。帳號＋密碼送 POST /auth/login；401 等失敗一律 toast 後端訊息；
            成功後：預設密碼 → /change-password，否則回 redirect（只接受站內路徑）或首頁
            不用元件庫，沿用 main.css 色票與字級
-->
<template>
  <main class="login">
    <section class="card">
      <h1>機房設備異動申請系統</h1>
      <p class="sub">請登入</p>
      <form @submit.prevent="submit">
        <label for="loginId">帳號</label>
        <input id="loginId" v-model="loginId" type="text" autocomplete="username" maxlength="64" required autofocus>
        <label for="password">密碼</label>
        <input id="password" v-model="password" type="password" autocomplete="current-password" maxlength="128" required>
        <button class="go" type="submit" :disabled="busy">{{ busy ? '登入中…' : '登入' }}</button>
      </form>
    </section>
    <ToastHost />
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ToastHost from '../components/ToastHost.vue'
import { errorMessage } from '../api/http'
import { CHANGE_PASSWORD_PATH, safeRedirect, useAuth } from '../composables/useAuth'
import { useToast } from '../composables/useToast'

const route = useRoute()
const router = useRouter()
const auth = useAuth()
const { toast } = useToast()

const loginId = ref('')
const password = ref('')
const busy = ref(false)

async function submit(): Promise<void> {
  const id = loginId.value.trim()
  if (id === '' || password.value === '') {
    toast('請輸入帳號與密碼', 'amber')
    return
  }
  busy.value = true
  try {
    const me = await auth.login({ loginId: id, password: password.value })
    password.value = ''
    if (me.mustChangePassword === true) {
      toast('首次登入請先修改預設密碼', 'teal')
      await router.replace(CHANGE_PASSWORD_PATH)
    } else {
      await router.replace(safeRedirect(route.query.redirect))
    }
  } catch (e: unknown) {
    toast(errorMessage(e, '登入失敗，請稍後再試'), 'red')
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.login { min-height: 100vh; display: flex; align-items: center; justify-content: center; padding: 24px 20px; }
.card { background: #fff; border: 1px solid var(--line); border-radius: 10px; padding: 28px 32px; width: 100%; max-width: 440px; }
.card h1 { margin: 0; font-size: 26px; color: var(--navy); }
.sub { margin: 4px 0 20px; color: var(--gray); font-size: 17px; }
form { display: flex; flex-direction: column; gap: 6px; }
label { color: var(--gray); margin-top: 8px; }
input { padding: 10px 12px; border: 1px solid var(--line); border-radius: 7px; min-height: 48px; }
input:focus { outline: 2px solid var(--teal); border-color: var(--teal); }
.go { margin-top: 20px; background: var(--teal); border: none; color: #fff; font-weight: 700; border-radius: 7px;
  padding: 12px 24px; font-size: 19px; cursor: pointer; }
.go:hover:not(:disabled) { filter: brightness(1.08); }
.go:disabled { opacity: .55; cursor: default; }
</style>
