// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-12
// 變更說明: 新增：附件在網頁開啟測試（BACKLOG 第 43 項）
//           inlineMimeOf 只認 .pdf .jpg .png（不分大小寫）；openAttachment 先同步開分頁、被擋回 false 且不取檔；
//           blob 類型依副檔名、不採用回應本文與 content-type 標頭的類型；取檔期間分頁顯示載入中；
//           取檔失敗關分頁並帶回後端 message；取檔期間分頁已關不建立 blob；取檔期間已登出關分頁、不建立 blob；
//           blob 網址登入期間不釋放，resetSession（登出、逾時、登入）時全部釋放
// ============================================================
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import http from '../src/api/http'
import { openAttachment, revokeOpenedAttachments } from '../src/api/attachment'
import { resetSession } from '../src/composables/useSession'
import { errorMessage } from '../src/api/errors'
import { inlineMimeOf } from '../src/utils/attach'
import { httpError } from './helpers'

vi.mock('../src/api/http', () => ({ default: { get: vi.fn() } }))

const getMock = vi.mocked(http.get)

describe('inlineMimeOf', () => {
  it('.pdf .jpg .png 回固定 MIME，不分大小寫', () => {
    expect(inlineMimeOf('公文.pdf')).toBe('application/pdf')
    expect(inlineMimeOf('截圖.JPG')).toBe('image/jpeg')
    expect(inlineMimeOf('a.b.Png')).toBe('image/png')
  })

  it('Word 檔與其他副檔名回 null', () => {
    expect(inlineMimeOf('公文.doc')).toBeNull()
    expect(inlineMimeOf('公文.docx')).toBeNull()
    expect(inlineMimeOf('pdf')).toBeNull()
    expect(inlineMimeOf('a.pdf.html')).toBeNull()
  })
})

describe('openAttachment', () => {
  let win: {
    opener: unknown
    closed: boolean
    close: ReturnType<typeof vi.fn>
    location: { href: string }
    document: { title: string; body: { textContent: string } }
  }
  let openSpy: ReturnType<typeof vi.spyOn>
  let createSpy: ReturnType<typeof vi.fn>
  let revokeSpy: ReturnType<typeof vi.fn>
  // 測試後換回，避免其他測試檔共用同一環境時拿到假函式
  const realCreate = URL.createObjectURL
  const realRevoke = URL.revokeObjectURL

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    getMock.mockReset()
    win = { opener: window, closed: false, close: vi.fn(), location: { href: '' }, document: { title: '', body: { textContent: '' } } }
    openSpy = vi.spyOn(window, 'open').mockReturnValue(win as unknown as Window)
    let seq = 0
    createSpy = vi.fn(() => `blob:x/${++seq}`)
    revokeSpy = vi.fn()
    URL.createObjectURL = createSpy as unknown as typeof URL.createObjectURL
    URL.revokeObjectURL = revokeSpy as unknown as typeof URL.revokeObjectURL
  })

  afterEach(() => {
    // 清掉模組層記下的網址，避免影響下一則測試
    revokeOpenedAttachments()
    URL.createObjectURL = realCreate
    URL.revokeObjectURL = realRevoke
    openSpy.mockRestore()
    vi.useRealTimers()
  })

  it('同步開分頁：呼叫當下（尚未經過任何 await）分頁已開', async () => {
    getMock.mockResolvedValue({ data: new Blob(['x']), headers: {} })
    const p = openAttachment(7, 'a.pdf')
    expect(openSpy).toHaveBeenCalledTimes(1)
    await p
  })

  it('取檔期間空白分頁顯示載入中', async () => {
    getMock.mockImplementation(async () => {
      expect(win.document.title).toBe('載入中…')
      expect(win.document.body.textContent).toBe('載入中…')
      return { data: new Blob(['x']), headers: {} }
    })
    await expect(openAttachment(7, 'a.png')).resolves.toBe(true)
  })

  it('成功：取檔前已開分頁，導向 blob 網址；blob 類型依副檔名而非回應類型', async () => {
    getMock.mockImplementation(async () => {
      // 取檔時分頁必須已經開好（點擊當下同步開，否則會被彈出視窗攔截擋下）
      expect(openSpy).toHaveBeenCalledTimes(1)
      // 回應本文與標頭都宣稱是 HTML，仍須以副檔名的 PDF 開啟
      return { data: new Blob(['%PDF'], { type: 'text/html' }), headers: { 'content-type': 'text/html' } }
    })

    await expect(openAttachment(7, '公文.PDF')).resolves.toBe(true)

    expect(getMock).toHaveBeenCalledWith('/attachments/7', { responseType: 'blob' })
    expect(win.opener).toBeNull()
    expect(win.location.href).toBe('blob:x/1')
    const blob = createSpy.mock.calls[0][0] as Blob
    expect(blob.type).toBe('application/pdf')
    expect(win.close).not.toHaveBeenCalled()
  })

  it('blob 網址不主動釋放（釋放後新分頁無法存檔與重新整理）', async () => {
    getMock.mockResolvedValue({ data: new Blob(['x']), headers: {} })
    await openAttachment(7, 'a.png')

    vi.advanceTimersByTime(60 * 60 * 1000)
    expect(revokeSpy).not.toHaveBeenCalled()
  })

  it('登出、逾時、登入（resetSession）時釋放本頁開過的所有附件網址，只釋放一次', async () => {
    getMock.mockResolvedValue({ data: new Blob(['x']), headers: {} })
    await openAttachment(7, 'a.pdf')
    await openAttachment(8, 'b.png')

    resetSession()
    expect(revokeSpy.mock.calls.map(c => c[0])).toEqual(['blob:x/1', 'blob:x/2'])

    resetSession()
    expect(revokeSpy).toHaveBeenCalledTimes(2)
  })

  it('取檔期間使用者已關掉空白分頁：不建立 blob 網址', async () => {
    getMock.mockImplementation(async () => {
      win.closed = true
      return { data: new Blob(['x']), headers: {} }
    })
    await expect(openAttachment(7, 'a.pdf')).resolves.toBe(true)
    expect(createSpy).not.toHaveBeenCalled()
    expect(win.location.href).toBe('')
  })

  it('取檔期間已登出（resetSession）：關掉分頁，不建立 blob 網址', async () => {
    getMock.mockImplementation(async () => {
      resetSession()
      return { data: new Blob(['x']), headers: {} }
    })
    await expect(openAttachment(7, 'a.pdf')).resolves.toBe(true)
    expect(win.close).toHaveBeenCalledTimes(1)
    expect(createSpy).not.toHaveBeenCalled()
    expect(win.location.href).toBe('')

    // 下一次登入期間照常開啟
    getMock.mockResolvedValue({ data: new Blob(['x']), headers: {} })
    win.close.mockClear()
    await openAttachment(8, 'b.png')
    expect(win.close).not.toHaveBeenCalled()
    expect(win.location.href).toBe('blob:x/1')
  })

  it('新分頁被擋：回 false，不取檔', async () => {
    openSpy.mockReturnValue(null)
    await expect(openAttachment(7, 'a.jpg')).resolves.toBe(false)
    expect(getMock).not.toHaveBeenCalled()
  })

  it('取檔失敗：關掉空白分頁，錯誤本文轉回 JSON 供 errorMessage 取用', async () => {
    getMock.mockRejectedValue(httpError(403, new Blob([JSON.stringify({ message: '無權限檢視此申請單' })])))

    const err = await openAttachment(7, 'a.pdf').catch((e: unknown) => e)

    expect(win.close).toHaveBeenCalledTimes(1)
    expect(errorMessage(err, '預設')).toBe('無權限檢視此申請單')
    expect(createSpy).not.toHaveBeenCalled()
  })

  it('Word 檔：不開分頁、不取檔，直接 reject', async () => {
    await expect(openAttachment(7, '公文.docx')).rejects.toThrow()
    expect(openSpy).not.toHaveBeenCalled()
    expect(getMock).not.toHaveBeenCalled()
  })
})
