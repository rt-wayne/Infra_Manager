// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：附件下載（開發文件 spec_im_acct_insp_v2.0.md 第 4.10、8.1 節）：GET /attachments/{id}
//           需帶 Authorization，不能用一般連結：以 blob 取得後由瀏覽器存檔；
//           檔名取 Content-Disposition（RFC 5987 filename*），取不到用明細的原檔名
//           失敗時後端本文也是 blob，先轉回 JSON 讓 errorMessage 取得 message
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-12
// 變更說明: BACKLOG 第 43 項：新增 openAttachment，.pdf .jpg .png 以新分頁開啟（下載功能保留）：
//           點擊當下先同步開空白分頁（await 之後才開會被彈出視窗攔截擋下），取得 blob 後再導向 blob 網址；
//           blob 類型依副檔名決定、不採用回應的 Content-Type（避免檔案被當成網頁執行而讀到本站 token）；
//           取檔期間空白分頁顯示「載入中…」；取檔失敗關掉空白分頁並 reject；取檔期間分頁已關則不建立 blob 網址；
//           取檔期間已登出或重新登入（釋放次數改變）則關掉分頁、不建立 blob 網址；
//           blob 網址登入期間不釋放（使用者裁示：實測 60 秒後釋放會讓檢視器存檔失敗），
//           登出、逾時、登入時 revokeOpenedAttachments 全部釋放（使用者裁示），本頁關閉或重新整理時由瀏覽器回收；
//           從新分頁存檔的檔名是 blob 的 UUID（使用者裁示接受，要原檔名用列上的下載鈕）
// ============================================================
import axios from 'axios'
import http from './http'
import { inlineMimeOf } from '../utils/attach'

export const MSG_POPUP_BLOCKED = '瀏覽器擋下新分頁，請允許本站開啟彈出視窗，或改用下載'

/** openAttachment 建立、尚未釋放的 blob 網址 */
const openedUrls = new Set<string>()
/** revokeOpenedAttachments 呼叫次數；取檔前後不同代表中途已登出或重新登入 */
let revokeCount = 0

/** 從 Content-Disposition 取檔名：優先 filename*=UTF-8''...，其次 filename="..." */
export function fileNameFromDisposition(header: unknown): string {
  if (typeof header !== 'string') return ''
  const star = /filename\*\s*=\s*[^']*'[^']*'([^;]+)/i.exec(header)
  if (star) {
    try {
      return decodeURIComponent(star[1].trim().replace(/^"|"$/g, ''))
    } catch {
      // 編碼不合法時改用 filename
    }
  }
  const plain = /filename\s*=\s*"?([^";]+)"?/i.exec(header)
  return plain ? plain[1].trim() : ''
}

/** 錯誤回應本文是 Blob 時轉成 JSON 物件（轉不了就保留原樣） */
async function normalizeBlobError(e: unknown): Promise<unknown> {
  if (axios.isAxiosError(e) && e.response && e.response.data instanceof Blob) {
    try {
      e.response.data = JSON.parse(await e.response.data.text()) as unknown
    } catch {
      // 非 JSON 本文：維持原樣，由呼叫端顯示預設訊息
    }
  }
  return e
}

/** 觸發瀏覽器存檔 */
export function saveBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

/** 下載附件：GET /attachments/{id}；失敗時 reject（本文已轉回 { message }） */
export async function downloadAttachment(attachId: number, fallbackName: string): Promise<void> {
  let res
  try {
    res = await http.get<Blob>(`/attachments/${encodeURIComponent(String(attachId))}`, { responseType: 'blob' })
  } catch (e) {
    throw await normalizeBlobError(e)
  }
  const name = fileNameFromDisposition(res.headers['content-disposition']) || fallbackName || `attachment-${attachId}`
  saveBlob(res.data, name)
}

/**
 * 以新分頁開啟附件：GET /attachments/{id}；須在點擊事件內同步呼叫。
 * 新分頁被瀏覽器擋下回 false；檔案類型不能開啟或取檔失敗時 reject（本文已轉回 { message }）；
 * 回 true 代表已處理、呼叫端不需提示（含取檔期間分頁已關或已登出），不等於已開啟成功
 */
export async function openAttachment(attachId: number, fileName: string): Promise<boolean> {
  const mime = inlineMimeOf(fileName)
  if (!mime) {
    throw new Error('此檔案類型無法在網頁開啟')
  }
  const win = window.open('', '_blank')
  if (!win) {
    return false
  }
  // 新分頁不需回頭操作本頁
  win.opener = null
  // 取檔期間空白分頁先顯示載入中（用 textContent，不寫入 HTML）
  win.document.title = '載入中…'
  win.document.body.textContent = '載入中…'
  const countBefore = revokeCount
  let res
  try {
    res = await http.get<Blob>(`/attachments/${encodeURIComponent(String(attachId))}`, { responseType: 'blob' })
  } catch (e) {
    win.close()
    throw await normalizeBlobError(e)
  }
  // 取檔期間使用者已關掉空白分頁：不建立 blob 網址（建了也沒人用）
  if (win.closed) {
    return true
  }
  // 取檔期間已登出或重新登入：關掉分頁、不建立 blob 網址（否則登出後仍可重開）
  if (revokeCount !== countBefore) {
    win.close()
    return true
  }
  // 登入期間不釋放 blob 網址：釋放後新分頁的下載／儲存與重新整理都會失敗；登出、逾時才釋放
  const url = URL.createObjectURL(new Blob([res.data], { type: mime }))
  openedUrls.add(url)
  win.location.href = url
  return true
}

/** 釋放本頁開過的所有附件 blob 網址；登出、401、登入時由 resetSession 呼叫，避免共用電腦從瀏覽紀錄重開上一位的附件 */
export function revokeOpenedAttachments(): void {
  revokeCount++
  openedUrls.forEach(url => URL.revokeObjectURL(url))
  openedUrls.clear()
}
