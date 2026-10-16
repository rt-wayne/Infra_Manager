// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-05
// 變更說明: 新增：toast 通知（規格 v4），比照 store_web_barcode 的寫法抽成 composable
//           toasts 為模組層共用狀態；toast(msg, cls) 顯示約 3 秒後自動消失；cls 可用 teal／amber／red
//           jdk25 階段 3：改 TypeScript
// ============================================================
import { ref } from 'vue'

export interface Toast {
  id: number
  msg: string
  cls: string
}

const toasts = ref<Toast[]>([])
let seq = 0

export function useToast() {
  function toast(msg: string, cls = ''): void {
    const id = ++seq
    toasts.value.push({ id, msg, cls })
    setTimeout(() => {
      toasts.value = toasts.value.filter(t => t.id !== id)
    }, 3000)
  }
  return { toasts, toast }
}
