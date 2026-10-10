// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：日期字串工具（開發文件 spec_im_acct_insp.md 第 4 節：日期 yyyy-MM-dd、時間 yyyy-MM-dd HH:mm）
//           一律用本機時區，不經 toISOString（會變成 UTC 日期）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（開發文件 spec_im_acct_insp_v2.0.md 第 3 節：時間 yyyy-MM-dd HH:mm:ss）：
//           localInputToApi 輸出補秒；新增 localInputToDate（巡檢時間不可晚於現在的比較用）
// ============================================================

function pad(n: number): string {
  return String(n).padStart(2, '0')
}

/** Date → yyyy-MM-dd */
export function fmtDate(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** Date → yyyy-MM-dd HH:mm */
export function fmtDateTime(d: Date): string {
  return `${fmtDate(d)} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** Date → datetime-local 輸入框的值 yyyy-MM-ddTHH:mm */
export function toLocalInput(d: Date): string {
  return `${fmtDate(d)}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** datetime-local 的值 → yyyy-MM-dd HH:mm:ss（秒取輸入值，沒有則 00）；格式不對回空字串 */
export function localInputToApi(v: string): string {
  const m = /^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2})(:\d{2})?/.exec(v)
  return m ? `${m[1]} ${m[2]}${m[3] ?? ':00'}` : ''
}

/** datetime-local 的值 → Date（本機時區）；格式不對回 null */
export function localInputToDate(v: string): Date | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?/.exec(v)
  if (!m) return null
  return new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]), Number(m[4]), Number(m[5]), Number(m[6] ?? '0'))
}

/** 今天往前推 n-1 天（含今天共 n 天）的起日 */
export function daysAgo(n: number, today: Date = new Date()): string {
  const d = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  d.setDate(d.getDate() - (n - 1))
  return fmtDate(d)
}

/** 取日期字串前 10 碼（後端若回含時間的字串也只顯示日期） */
export function dateOnly(v: string | null | undefined): string {
  return v ? v.slice(0, 10) : ''
}
