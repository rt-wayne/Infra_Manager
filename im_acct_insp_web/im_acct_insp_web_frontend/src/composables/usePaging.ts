// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：清單分頁（README 第 5 節：清單每頁 20 筆）；資料變動時回第 1 頁
// ============================================================
import { computed, ref, watch } from 'vue'
import type { Ref } from 'vue'

export const PAGE_SIZE = 20

export function usePaging<T>(rows: Ref<T[]>) {
  const page = ref(1)
  const totalPages = computed(() => Math.max(1, Math.ceil(rows.value.length / PAGE_SIZE)))
  const pageRows = computed(() => rows.value.slice((page.value - 1) * PAGE_SIZE, page.value * PAGE_SIZE))
  watch(rows, () => {
    page.value = 1
  })
  return { page, totalPages, pageRows }
}
