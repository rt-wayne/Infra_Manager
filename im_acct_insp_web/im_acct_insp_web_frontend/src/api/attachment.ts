// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：附件下載（開發文件 spec_im_acct_insp_v2.0.md 第 4.10、8.1 節）：GET /attachments/{id}
//           需帶 Authorization，不能用一般連結：以 blob 取得後由瀏覽器存檔；
//           檔名取 Content-Disposition（RFC 5987 filename*），取不到用明細的原檔名
//           失敗時後端本文也是 blob，先轉回 JSON 讓 errorMessage 取得 message
// ============================================================
import axios from 'axios'
import http from './http'

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
