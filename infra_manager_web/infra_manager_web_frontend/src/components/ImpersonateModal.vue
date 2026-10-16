<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: 新增：切換身分彈窗（開發文件 spec_im_acct_insp_v2.0.md 第 2.5、8.2 節）
            名單來自 GET /users（STATUS＝1、不含自己，實際使用者需 SYS_IMPERSONATE）；選人後 emit select，由版面呼叫切換
            名單載入失敗只 toast（U09），不顯示成沒有人可選
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-16
  變更說明: 必填欄位醒目提醒：「切換為」標籤後加紅色 *
-->
<template>
  <BaseModal title="切換身分" sub="切換後畫面與權限完全等同所選的人；所有操作同時記錄您本人與代理身分。" close-text="取消" @close="emit('close')">
    <p v-if="loading" class="hint">載入中…</p>
    <template v-else-if="users">
      <div v-if="users.length === 0" class="hint">目前沒有可切換的使用者</div>
      <div v-else class="field">
        <label for="impTarget">切換為<span class="req-mark" title="必填">*</span></label>
        <select id="impTarget" v-model="target">
          <option value="" disabled>請選擇使用者</option>
          <option v-for="u in users" :key="u.userId" :value="u.userId">{{ optionText(u) }}</option>
        </select>
      </div>
    </template>
    <template #actions>
      <button type="button" class="btn btn-primary imp-confirm" :disabled="!target || busy" @click="emit('select', target)">確認切換</button>
    </template>
  </BaseModal>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import BaseModal from './BaseModal.vue'
import { listUsers } from '../api/auth'
import { errorMessage } from '../api/errors'
import { useToast } from '../composables/useToast'
import type { UserInfo } from '../types/common'
import { roleText } from '../utils/labels'

withDefaults(defineProps<{ busy?: boolean }>(), { busy: false })
const emit = defineEmits<{ close: []; select: [userId: string] }>()
const { toast } = useToast()

const users = ref<UserInfo[] | null>(null)
const loading = ref(true)
const target = ref('')

function optionText(u: UserInfo): string {
  const roles = roleText(u.roleIds)
  return roles ? `${u.userName}（${roles}）` : u.userName
}

onMounted(async () => {
  try {
    const data = await listUsers()
    if (!Array.isArray(data)) throw new Error('unexpected response')
    users.value = data
  } catch (e) {
    toast(errorMessage(e, '查詢失敗，請稍後再試'), 'amber')
  } finally {
    loading.value = false
  }
})
</script>
