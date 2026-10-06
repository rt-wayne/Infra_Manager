// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-06
// 變更說明: 新增：列表頁與檢視頁共用的顯示小工具（S4 回合三）
//           prioStyle：優先等級底色只接受 #rrggbb，其他值不套用（後端資料不直接進 style）
// ============================================================

const HEX = /^#[0-9a-fA-F]{6}$/

export function prioStyle(color: string | null | undefined): Record<string, string> {
  return color && HEX.test(color) ? { backgroundColor: color, color: '#fff' } : {}
}

/** 位元組 → KB，小數一位（與舊系統附件清單一致） */
export function kb(bytes: number | null | undefined): string {
  if (bytes == null || bytes < 0) return '—'
  return (bytes / 1024).toFixed(1) + ' KB'
}
