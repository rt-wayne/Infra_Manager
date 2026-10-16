<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: 新增：資產分類說明（開發文件 spec_im_acct_insp.md 第 6 節；原型 view-asset-ref）
            全部 15 碼（GET /codes 的 assetClasses），與營運直接相關者標記；載入失敗只 toast
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: v2.0（開發文件 spec_im_acct_insp_v2.0.md 第 4.7、8.3 節）：說明文字改為審查條件碼（reviewTrigger）與不可選碼；載入失敗文案 U09
-->
<template>
  <main>
    <div class="section-note">
      ⓘ 依全聯實業《SR-008-A04 資訊資產標示、保護與處置說明表》，硬體與軟體資產分類碼一覽。本系統目前僅在「資產分類碼」欄位提供人工選擇對應分類，尚未支援自動辨識，選擇時請參考下表判斷。標記「⭐ 審查條件」者為審查條件碼，帳號生效後列入定期權限審查；此外，所有特權帳號（root／Administrator／sa／enable 模式等）與機房門禁卡不論資產分類碼為何，同樣列入審查。「不可選用」者僅列於本頁，申請表單不提供選擇。
    </div>
    <AssetClassTable v-if="codes" :rows="codes.assetClasses" />
    <ToastHost />
  </main>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import AssetClassTable from '../components/AssetClassTable.vue'
import ToastHost from '../components/ToastHost.vue'
import { errorMessage } from '../api/errors'
import { useSession } from '../composables/useSession'
import { useToast } from '../composables/useToast'

const { toast } = useToast()
const { codes, ensureCodes } = useSession()

onMounted(async () => {
  try {
    await ensureCodes()
  } catch (e) {
    toast(errorMessage(e, '查詢失敗，請稍後再試'), 'amber')
  }
})
</script>
