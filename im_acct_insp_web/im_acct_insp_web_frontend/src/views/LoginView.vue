<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: 新增：登入頁（開發文件 spec_im_acct_insp_v2.0.md 第 2.1、8.1、8.3 節）
            工號、密碼、登入鈕；POST /auth/login 成功存 token（sessionStorage）後回原路由（query redirect），否則首頁
            錯誤顯示後端 message（A01、A02）；被 401 導回時顯示當時的後端 message（例 A03）
            已登入者由路由守衛導向首頁；密碼不留在畫面狀態以外的地方
-->
<template>
  <main class="login-page">
    <form class="module-card login-card" @submit.prevent="submit">
      <div class="login-brand">機房與帳號權限管理系統<small>Facility &amp; Account Management</small></div>
      <div v-if="message" class="note-box warn login-msg" role="alert">{{ message }}</div>
      <div class="field">
        <label for="loginUserId">工號</label>
        <input id="loginUserId" v-model="userId" type="text" autocomplete="username" />
      </div>
      <div class="field">
        <label for="loginPassword">密碼</label>
        <input id="loginPassword" v-model="password" type="password" autocomplete="current-password" />
      </div>
      <button type="submit" class="btn btn-primary login-btn" :disabled="busy">{{ busy ? '登入中…' : '登入' }}</button>
    </form>
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { errorMessage } from '../api/errors'
import { useSession } from '../composables/useSession'
import { safeRedirect } from '../router/redirect'

const route = useRoute()
const router = useRouter()
const { login, loginNotice } = useSession()

const userId = ref('')
const password = ref('')
const busy = ref(false)
const message = ref('')

async function submit(): Promise<void> {
  const id = userId.value.trim()
  if (!id || !password.value) {
    message.value = '請輸入工號與密碼'
    return
  }
  busy.value = true
  message.value = ''
  loginNotice.value = ''
  try {
    await login(id, password.value)
    password.value = ''
    await router.replace(safeRedirect(route.query.redirect))
  } catch (e) {
    password.value = ''
    message.value = errorMessage(e, '登入失敗，請稍後再試')
  } finally {
    busy.value = false
  }
}

onMounted(() => {
  // 被 401 導回：顯示後端訊息一次
  if (loginNotice.value) {
    message.value = loginNotice.value
    loginNotice.value = ''
  }
})
</script>

<style scoped>
.login-page { min-height: 100vh; display: flex; align-items: center; justify-content: center; padding: 20px; }
.login-card { width: 440px; max-width: 100%; padding: 28px 30px; }
.login-brand { font-size: 22px; font-weight: 700; color: var(--navy); margin-bottom: 18px; line-height: 1.4; }
.login-brand small { display: block; font-size: 15px; font-weight: 400; color: var(--gray); margin-top: 2px; }
.login-msg { margin: 0 0 14px; }
.field input { width: 100%; }
.login-btn { width: 100%; margin-top: 6px; }
</style>
