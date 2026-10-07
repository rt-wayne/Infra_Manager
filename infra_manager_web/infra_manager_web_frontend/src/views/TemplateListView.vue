<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: 新增：範本列表頁（S5 R2，/templates），比照舊系統 views/templates/index.ejs 的欄位：
            名稱、優先等級、建立者、套用次數、最後套用（時間＋人）、更新時間
            - 後端一次回全部（範本數量少），前端每頁 20 列分頁
            - 任何登入者都能新增；修改、刪除只有建立者本人與 admin（後端回 canEdit，第 43 項裁示 A）——
              按鈕只是方便，實際權限以後端 403 為準
            - 刪除先 confirm；成功後重新載入列表；失敗出 toast（403／404 訊息由後端帶）
            - 401 由登入處理器導頁，本頁不另出 toast
            S5 R3（2026-10-07）：每列加「建單」（任何登入者），導到 /apps/new?template=<id> 帶入範本內容
-->
<template>
  <main>
    <header class="hero">
      <h1>範本管理</h1>
      <div class="hero-links">
        <router-link class="new" :to="{ name: TEMPLATE_NEW_ROUTE }">＋ 新增範本</router-link>
        <router-link class="link" to="/">回首頁</router-link>
      </div>
    </header>

    <section class="card">
      <p class="hint">按「建單」或在新增申請單頁上方選範本，即可帶入內容；範本只有建立者本人與管理員可以修改、刪除</p>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <p v-else-if="!items" class="muted">載入中…</p>
      <p v-else-if="items.length === 0" class="muted">目前沒有範本</p>
      <template v-else>
        <table class="list">
          <colgroup>
            <col /><col class="c-prio" /><col class="c-who" /><col class="c-cnt" />
            <col class="c-last" /><col class="c-time" /><col class="c-act" />
          </colgroup>
          <thead>
            <tr>
              <th>範本名稱</th><th>優先</th><th>建立者</th><th>套用次數</th>
              <th>最後套用</th><th>更新時間</th><th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="t in pageItems" :key="t.tmplId">
              <td class="name">{{ t.tmplName }}</td>
              <td>
                <span v-if="t.prioCode" class="prio" :style="prioStyle(t.prioColor)" :title="t.prioName ?? ''">{{ t.prioCode }}</span>
                <span v-else class="muted">—</span>
              </td>
              <td>{{ t.ownerName || '—' }}</td>
              <td class="num">{{ t.useCnt }}</td>
              <td>
                <template v-if="t.lastUsedAt">
                  <div class="time">{{ t.lastUsedAt }}</div>
                  <div v-if="t.lastUsedByName" class="sub">{{ t.lastUsedByName }}</div>
                </template>
                <span v-else class="muted">尚未套用</span>
              </td>
              <td class="time">{{ t.updatedAt || '—' }}</td>
              <td>
                <div class="acts">
                  <router-link class="quiet small use" :to="{ name: APP_NEW_ROUTE, query: { template: t.tmplId } }">建單</router-link>
                  <template v-if="t.canEdit">
                    <router-link class="quiet small" :to="{ name: TEMPLATE_EDIT_ROUTE, params: { id: t.tmplId } }">修改</router-link>
                    <button class="quiet small danger" type="button" :disabled="busyId !== ''" @click="remove(t)">
                      {{ busyId === t.tmplId ? '刪除中…' : '刪除' }}
                    </button>
                  </template>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <AppPager v-if="items.length > PAGE_SIZE" :page="page" :size="PAGE_SIZE" :total="items.length" @change="p => (page = p)" />
      </template>
    </section>

    <ToastHost />
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppPager from '../components/AppPager.vue'
import ToastHost from '../components/ToastHost.vue'
import { deleteTemplate, listTemplates } from '../api/templates'
import { errorMessage, isUnauthorized } from '../api/http'
import { useToast } from '../composables/useToast'
import { APP_NEW_ROUTE, TEMPLATE_EDIT_ROUTE, TEMPLATE_NEW_ROUTE } from '../router/names'
import type { TemplateListItem } from '../types/template'
import { prioStyle } from '../utils/format'

const PAGE_SIZE = 20

const { toast } = useToast()

const items = ref<TemplateListItem[] | null>(null)
const error = ref('')
const page = ref(1)
/** 刪除中的範本 id；空字串表示沒有 */
const busyId = ref('')

const pageItems = computed(() => (items.value ?? []).slice((page.value - 1) * PAGE_SIZE, page.value * PAGE_SIZE))

async function load(): Promise<void> {
  error.value = ''
  try {
    const list = await listTemplates()
    items.value = list
    const pages = Math.max(1, Math.ceil(list.length / PAGE_SIZE))
    if (page.value > pages) page.value = pages
  } catch (e: unknown) {
    if (isUnauthorized(e)) {
      error.value = '尚未登入'
      return
    }
    error.value = errorMessage(e)
    toast(error.value, 'red')
  }
}

async function remove(t: TemplateListItem): Promise<void> {
  if (busyId.value) return
  if (!window.confirm(`確定要刪除範本「${t.tmplName}」？刪除後無法再套用。`)) return
  busyId.value = t.tmplId
  try {
    await deleteTemplate(t.tmplId)
    toast('範本已刪除', 'teal')
  } catch (e: unknown) {
    if (!isUnauthorized(e)) toast(errorMessage(e, '刪除失敗，請稍後再試'), 'red')
  } finally {
    busyId.value = ''
  }
  await load()
}

onMounted(() => {
  void load()
})
</script>

<style scoped>
main { width: 100%; max-width: 1280px; margin: 0 auto; padding: 24px 20px; }

.hero { margin-bottom: 16px; display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.hero h1 { margin: 0; font-size: 28px; color: var(--navy); }
.link { color: var(--blue); font-size: 17px; }
.hero-links { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
.new { background: var(--teal); color: #fff; font-weight: 700; border-radius: 7px; padding: 8px 18px; font-size: 17px; text-decoration: none; }

.card { background: #fff; border: 1px solid var(--line); border-radius: 10px; padding: 16px 20px; margin-bottom: 16px; }
.hint { margin: 0 0 8px; color: var(--gray); font-size: 15px; }
.muted { color: var(--gray); margin: 8px 0; }
.error { color: var(--red); font-weight: 700; margin: 8px 0; }

.list { width: 100%; table-layout: fixed; border-collapse: collapse; font-size: 16px; }
.list th { text-align: left; color: var(--gray); font-weight: 700; font-size: 15px; padding: 8px 6px; border-bottom: 2px solid var(--line); }
.list td { padding: 8px 6px; border-bottom: 1px solid var(--line); vertical-align: top; overflow-wrap: anywhere; }
.c-prio { width: 56px; }
.c-who { width: 120px; }
.c-cnt { width: 84px; }
.c-last { width: 150px; }
.c-time { width: 104px; }
.c-act { width: 196px; }
.name { font-weight: 700; }
.num { text-align: right; padding-right: 18px; }
.prio { display: inline-block; min-width: 36px; text-align: center; padding: 0 6px; border-radius: 6px; background: var(--line); font-weight: 700; font-size: 15px; }
.sub { color: var(--gray); font-size: 14px; }
.time { font-size: 14px; }

.acts { display: flex; flex-wrap: wrap; gap: 6px; }
.quiet { background: #fff; border: 1px solid var(--line); color: var(--dark); border-radius: 7px; padding: 8px 18px; font-size: 17px; cursor: pointer; text-decoration: none; }
.quiet:disabled { opacity: .55; cursor: default; }
.quiet.small { min-height: 36px; padding: 2px 12px; font-size: 15px; display: inline-flex; align-items: center; }
.danger { color: var(--red); border-color: var(--red); }
</style>
