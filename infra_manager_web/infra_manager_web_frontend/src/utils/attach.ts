// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：附件前端檢查與讀檔（開發文件 spec_im_acct_insp_v2.0.md 第 4.10 節）
//           副檔名（不分大小寫）限 .pdf .doc .docx .jpg .png（E14）、單檔 ≤ 10 MB（E15）、空檔視同未上傳；
//           檔頭內容由後端檢查。送出前先把檔案讀進記憶體（D17：讀取失敗時由畫面提示 U07／U08）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-12
// 變更說明: BACKLOG 第 43 項：新增 inlineMimeOf，判斷附件能否在瀏覽器新分頁開啟（.pdf .jpg .png）並回固定 MIME；
//           .doc .docx 瀏覽器無法顯示，回 null（只能下載）
// ============================================================

/** 允許的副檔名 */
export const ATTACH_EXTS = ['.pdf', '.doc', '.docx', '.jpg', '.png']

/** input[type=file] 的 accept */
export const ATTACH_ACCEPT = ATTACH_EXTS.join(',')

/** 單檔上限 10 MB（10×1024×1024 byte） */
export const ATTACH_MAX_BYTES = 10 * 1024 * 1024

export const MSG_ATTACH_EXT = '附件格式僅限 .pdf .doc .docx .jpg .png'
export const MSG_ATTACH_SIZE = '附件大小不可超過 10 MB'
export const MSG_ATTACH_EMPTY = '所選檔案沒有內容，視同未上傳'
export const MSG_DOC_READ_FAIL = '公文附件讀取失敗，是否不附公文直接送出？'
export const MSG_PROOF_READ_FAIL = '佐證附件讀取失敗，請重新選擇檔案'

/** 檢查選取的檔案；通過回 null，否則回要提示的訊息（空檔回 MSG_ATTACH_EMPTY，呼叫端視同未選） */
export function checkAttachFile(file: { name: string; size: number }): string | null {
  const name = file.name.toLowerCase()
  if (!ATTACH_EXTS.some(ext => name.endsWith(ext))) return MSG_ATTACH_EXT
  if (file.size > ATTACH_MAX_BYTES) return MSG_ATTACH_SIZE
  if (file.size === 0) return MSG_ATTACH_EMPTY
  return null
}

/**
 * 送出前讀檔：讀成功回一份記憶體中的 File（之後不再碰磁碟上的原檔）；
 * 原檔已被移動、刪除或無權讀取時 reject
 */
export async function readAttachFile(file: File): Promise<File> {
  const buf = await file.arrayBuffer()
  return new File([buf], file.name, { type: file.type })
}

/** 瀏覽器能直接顯示的副檔名與其 MIME；Word 檔不在內（瀏覽器無法顯示，只能下載） */
const INLINE_MIME: Record<string, string> = {
  '.pdf': 'application/pdf',
  '.jpg': 'image/jpeg',
  '.png': 'image/png'
}

/** 依檔名（不分大小寫）回可在新分頁開啟的 MIME；不能開啟回 null */
export function inlineMimeOf(fileName: string): string | null {
  const name = fileName.toLowerCase()
  const ext = Object.keys(INLINE_MIME).find(e => name.endsWith(e))
  return ext ? INLINE_MIME[ext] : null
}

/** 檔案大小顯示（KB／MB） */
export function fileSizeText(bytes: number): string {
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  return `${Math.max(1, Math.round(bytes / 1024))} KB`
}
