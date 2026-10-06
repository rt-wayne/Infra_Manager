<!--
  AI版本  : Claude Fable 5.1 (claude-fable-5-1)
  修改日期: 2026-10-06
  變更說明: 新增：改密碼頁（S2 回合三）。舊密碼＋新密碼＋再輸入一次送 POST /auth/password；
            前端只檢「兩次輸入相同」與「新密碼至少 6 字」做即時提示，其餘規則以後端 400 訊息為準（toast）
            字數不 trim（與後端一致，code review 第 2 項）：全空白交給後端回「新密碼不得全為空白」
            預設密碼者被守衛導來此頁、改完才放行；成功後回首頁。頁上提供登出鈕讓不想改的人離開
-->
<template>
  <main class="change">
    <section class="card">
      <h1>修改密碼</h1>
      <p class="sub" v-if="auth.mustChangePassword.value">您目前使用的是預設密碼，請先修改後才能使用系統</p>
      <p class="sub" v-else>{{ auth.userName.value }}</p>
      <form @submit.prevent="submit">
        <label for="oldPassword">舊密碼</label>
        <input id="oldPassword" v-model="oldPassword" type="password" autocomplete="current-password" maxlength="128" required>
        <label for="newPassword">新密碼（至少 6 個字）</label>
        <input id="newPassword" v-model="newPassword" type="password" autocomplete="new-password" maxlength="128" required>
        <label for="confirm">再輸入一次新密碼</label>
        <input id="confirm" v-model="confirm" type="password" autocomplete="new-password" maxlength="128" required>
        <div class="actions">
          <button class="go" type="submit" :disabled="busy">{{ busy ? '處理中…' : '確定修改' }}</button>
          <button class="quiet" type="button" :disabled="busy" @click="doLogout">登出</button>
        </div>
      </form>
    </section>
    <ToastHost />
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import ToastHost from '../components/ToastHost.vue'
import { errorMessage } from '../api/http'
import { LOGIN_PATH, useAuth } from '../composables/useAuth'
import { useToast } from '../composables/useToast'

const router = useRouter()
const auth = useAuth()
const { toast } = useToast()

const oldPassword = ref('')
const newPassword = ref('')
const confirm = ref('')
const busy = ref(false)

function codePoints(s: string): number {
  return Array.from(s).length
}

async function submit(): Promise<void> {
  if (oldPassword.value === '' || newPassword.value === '' || confirm.value === '') {
    toast('三個欄位都要填', 'amber')
    return
  }
  if (newPassword.value !== confirm.value) {
    toast('兩次輸入的新密碼不同', 'amber')
    return
  }
  if (codePoints(newPassword.value) < 6) {
    toast('新密碼至少 6 個字', 'amber')
    return
  }
  busy.value = true
  try {
    await auth.changePassword({ oldPassword: oldPassword.value, newPassword: newPassword.value })
    oldPassword.value = ''
    newPassword.value = ''
    confirm.value = ''
    toast('密碼已修改', 'teal')
    await router.replace('/')
  } catch (e: unknown) {
    toast(errorMessage(e, '修改失敗，請稍後再試'), 'red')
  } finally {
    busy.value = false
  }
}

async function doLogout(): Promise<void> {
  busy.value = true
  try {
    await auth.logout()
  } catch (e: unknown) {
    toast(errorMessage(e, '登出失敗'), 'amber')
  } finally {
    busy.value = false
  }
  await router.replace(LOGIN_PATH)
}
</script>

<style scoped>
.change { min-height: 100vh; display: flex; align-items: center; justify-content: center; padding: 24px 20px; }
.card { background: #fff; border: 1px solid var(--line); border-radius: 10px; padding: 28px 32px; width: 100%; max-width: 480px; }
.card h1 { margin: 0; font-size: 26px; color: var(--navy); }
.sub { margin: 4px 0 20px; color: var(--orange); font-size: 17px; }
form { display: flex; flex-direction: column; gap: 6px; }
label { color: var(--gray); margin-top: 8px; }
input { padding: 10px 12px; border: 1px solid var(--line); border-radius: 7px; min-height: 48px; }
input:focus { outline: 2px solid var(--teal); border-color: var(--teal); }
.actions { display: flex; gap: 12px; margin-top: 20px; flex-wrap: wrap; }
.go { flex: 1; background: var(--teal); border: none; color: #fff; font-weight: 700; border-radius: 7px;
  padding: 12px 24px; font-size: 19px; cursor: pointer; }
.quiet { background: #fff; border: 1px solid var(--line); color: var(--dark); border-radius: 7px; padding: 12px 20px; font-size: 19px; cursor: pointer; }
.go:hover:not(:disabled) { filter: brightness(1.08); }
button:disabled { opacity: .55; cursor: default; }
</style>
