// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-12
// 變更說明: 新增：申請單明細附件列測試（BACKLOG 第 43 項）
//           .pdf .jpg .png 檔名可點開啟、Word 檔名為文字；每列都有下載鈕；
//           點擊當下同步呼叫開啟；新分頁被擋提示改用下載；開啟失敗顯示後端訊息；進行中各列各自停用
// ============================================================
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import type { VueWrapper } from '@vue/test-utils'
import RequestDetailModal from '../src/components/RequestDetailModal.vue'
import { MSG_POPUP_BLOCKED, downloadAttachment, openAttachment } from '../src/api/attachment'
import { getCodes } from '../src/api/common'
import { getRequest } from '../src/api/request'
import { resetSession } from '../src/composables/useSession'
import { useToast } from '../src/composables/useToast'
import type { RequestDetail } from '../src/types/request'
import { CODES, httpError, makeRouter } from './helpers'

vi.mock('../src/api/common', () => ({
  getCodes: vi.fn(),
  getHomeStats: vi.fn(),
  listExpiring: vi.fn(),
  listTodos: vi.fn(),
  listEmgReturned: vi.fn().mockResolvedValue([])
}))
vi.mock('../src/api/request', () => ({ getRequest: vi.fn() }))
vi.mock('../src/api/attachment', () => ({
  MSG_POPUP_BLOCKED: '瀏覽器擋下新分頁（測試）',
  downloadAttachment: vi.fn(),
  openAttachment: vi.fn(),
  revokeOpenedAttachments: vi.fn()
}))

const openMock = vi.mocked(openAttachment)
const downloadMock = vi.mocked(downloadAttachment)

const REQ = {
  reqId: 'AR1', reqTypeCode: 'ADD', catgId: 'HOST', reqStateCode: 'REVIEW', currStageId: 2,
  attachments: [
    { attachId: 11, attachTypeCode: 'DOC', fileName: '公文.pdf', fileSize: 2048, createDate: '2026-10-12 09:00:00' },
    { attachId: 12, attachTypeCode: 'DOC', fileName: '說明.docx', fileSize: 2048, createDate: '2026-10-12 09:00:00' },
    { attachId: 13, attachTypeCode: 'PROOF', fileName: '截圖.PNG', fileSize: 2048, createDate: '2026-10-12 09:00:00' }
  ],
  actions: []
} as unknown as RequestDetail

async function mountModal(): Promise<VueWrapper> {
  const router = await makeRouter('/requests')
  const w = mount(RequestDetailModal, { props: { reqId: 'AR1' }, global: { plugins: [router] } })
  await flushPromises()
  return w
}

function row(w: VueWrapper, attachId: number) {
  return w.find(`[data-attach="${attachId}"]`)
}

function lastToastMsg(): string {
  const list = useToast().toasts.value
  return list.length ? list[list.length - 1].msg : ''
}

describe('RequestDetailModal 附件', () => {
  beforeEach(() => {
    resetSession()
    useToast().toasts.value = []
    vi.mocked(getCodes).mockReset().mockResolvedValue(CODES)
    vi.mocked(getRequest).mockReset().mockResolvedValue(REQ)
    openMock.mockReset().mockResolvedValue(true)
    downloadMock.mockReset().mockResolvedValue()
  })

  it('PDF、PNG 檔名可點，Word 檔名為文字；每列都有下載鈕', async () => {
    const w = await mountModal()
    expect(row(w, 11).find('button.attach-open').text()).toBe('公文.pdf')
    expect(row(w, 13).find('button.attach-open').text()).toBe('截圖.PNG')
    expect(row(w, 12).find('.attach-open').exists()).toBe(false)
    expect(row(w, 12).find('span.attach-name').text()).toBe('說明.docx')
    for (const id of [11, 12, 13]) {
      expect(row(w, id).find('button.attach-dl').exists()).toBe(true)
    }
  })

  it('點檔名開啟；下載鈕仍走下載', async () => {
    const w = await mountModal()
    await row(w, 11).find('.attach-open').trigger('click')
    await flushPromises()
    expect(openMock).toHaveBeenCalledWith(11, '公文.pdf')
    expect(downloadMock).not.toHaveBeenCalled()

    await row(w, 11).find('.attach-dl').trigger('click')
    await flushPromises()
    expect(downloadMock).toHaveBeenCalledWith(11, '公文.pdf')
  })

  it('點檔名當下同步呼叫開啟（不先 await 其他東西，否則新分頁會被擋）', async () => {
    const w = await mountModal()
    void row(w, 11).find('.attach-open').trigger('click')
    expect(openMock).toHaveBeenCalledTimes(1)
    await flushPromises()
  })

  it('兩列同時進行：先完成的一列不會解鎖另一列', async () => {
    let finishOpen: (v: boolean) => void = () => undefined
    let finishDl: () => void = () => undefined
    openMock.mockReturnValue(new Promise<boolean>(r => { finishOpen = r }))
    downloadMock.mockReturnValue(new Promise<void>(r => { finishDl = r }))
    const w = await mountModal()
    await row(w, 11).find('.attach-open').trigger('click')
    await row(w, 12).find('.attach-dl').trigger('click')

    // 沒在處理的第 13 列全程可按
    expect(row(w, 13).find('.attach-open').attributes('disabled')).toBeUndefined()
    expect(row(w, 13).find('.attach-dl').attributes('disabled')).toBeUndefined()

    finishOpen(true)
    await flushPromises()
    expect(row(w, 11).find('.attach-open').attributes('disabled')).toBeUndefined()
    expect(row(w, 12).find('.attach-dl').attributes('disabled')).toBeDefined()

    finishDl()
    await flushPromises()
    expect(row(w, 12).find('.attach-dl').attributes('disabled')).toBeUndefined()
  })

  it('新分頁被擋：提示改用下載', async () => {
    openMock.mockResolvedValue(false)
    const w = await mountModal()
    await row(w, 13).find('.attach-open').trigger('click')
    await flushPromises()
    expect(lastToastMsg()).toBe(MSG_POPUP_BLOCKED)
  })

  it('開啟失敗：顯示後端訊息', async () => {
    openMock.mockRejectedValue(httpError(500, { message: '附件檔案不存在，請聯絡系統管理員' }))
    const w = await mountModal()
    await row(w, 11).find('.attach-open').trigger('click')
    await flushPromises()
    expect(lastToastMsg()).toBe('附件檔案不存在，請聯絡系統管理員')
  })

  it('開啟中：該列檔名與下載鈕停用，完成後恢復', async () => {
    let finish: (v: boolean) => void = () => undefined
    openMock.mockReturnValue(new Promise<boolean>(r => { finish = r }))
    const w = await mountModal()
    await row(w, 11).find('.attach-open').trigger('click')
    expect(row(w, 11).find('.attach-open').attributes('disabled')).toBeDefined()
    expect(row(w, 11).find('.attach-dl').attributes('disabled')).toBeDefined()
    finish(true)
    await flushPromises()
    expect(row(w, 11).find('.attach-open').attributes('disabled')).toBeUndefined()
  })
})
