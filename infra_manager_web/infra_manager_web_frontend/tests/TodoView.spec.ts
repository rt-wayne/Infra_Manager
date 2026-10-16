// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：待辦簽核測試（開發文件 spec_im_acct_insp.md 第 6、7 節）
//           依 actionKind 顯示操作；退件原因必填；門禁卡新增回報執行要卡號；409 顯示後端訊息；載入失敗不顯示「目前沒有待辦」
//           變更單 #1：M2 待辦帶 catgId、isPriv；以 catgId＝DOOR 判斷門禁卡（不看類別名稱）；特權列顯示標記
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（開發文件 spec_im_acct_insp_v2.0.md 第 4.4、4.5、4.10、8.3 節）：
//           核准／複驗通過先確認、帶 versionNo；退件原因必填（E10）；回報執行：佐證必選（E12）、門禁卡新增卡號必填（E13）、
//           佐證讀取失敗 U08、multipart 送出；verifyRejectCnt＞0 時回報與複驗彈窗顯示 U02
// ============================================================
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import type { VueWrapper } from '@vue/test-utils'
import TodoView from '../src/views/TodoView.vue'
import { getCodes, listExpiring, listTodos } from '../src/api/common'
import { approveRequest, execDone, rejectRequest, verifyOk, verifyReject } from '../src/api/request'
import { resetSession } from '../src/composables/useSession'
import type { Todo } from '../src/types/common'
import { CODES, chooseFile, httpError, lastToast, makeFile, makeRouter } from './helpers'

vi.mock('../src/api/common', () => ({
  getCodes: vi.fn(),
  getHomeStats: vi.fn(),
  listExpiring: vi.fn(),
  listTodos: vi.fn(),
  listEmgReturned: vi.fn().mockResolvedValue([])
}))
vi.mock('../src/api/request', () => ({
  submitRequest: vi.fn(),
  listMyRequests: vi.fn(),
  getRequest: vi.fn(),
  approveRequest: vi.fn(),
  rejectRequest: vi.fn(),
  resubmitRequest: vi.fn(),
  execDone: vi.fn(),
  verifyOk: vi.fn(),
  verifyReject: vi.fn()
}))

const todosMock = vi.mocked(listTodos)
const approveMock = vi.mocked(approveRequest)
const rejectMock = vi.mocked(rejectRequest)
const execMock = vi.mocked(execDone)
const verifyOkMock = vi.mocked(verifyOk)
const verifyRejectMock = vi.mocked(verifyReject)

function todo(over: Partial<Todo>): Todo {
  return {
    source: 'ACCT_REQ', reqId: 'AR1', title: '', reqTypeCode: 'ADD', catgId: 'HOST', catgName: '主機帳號', acctName: 'root',
    isPriv: 0, applyBy: '0000001', applyByName: 'Kevin (李凱鈞)', stageId: 2, stageName: '機房管理員',
    submitDate: '2026-10-07 09:00:00', actionKind: 'APPROVE', versionNo: 0, verifyRejectCnt: 0, verifyRejectReason: null,
    ...over
  }
}

const TODOS: Todo[] = [
  todo({ reqId: 'AR1', catgId: 'DB', catgName: '資料庫帳號', isPriv: 1, acctName: 'sa', title: '資料庫帳號 新增 — sa', versionNo: 3 }),
  todo({ reqId: 'AR2', catgId: 'DOOR', catgName: '機房門禁卡', acctName: null, stageId: 6, stageName: '執行人', actionKind: 'EXEC', versionNo: 4 }),
  todo({ reqId: 'AR3', reqTypeCode: 'MODIFY', stageId: 6, stageName: '執行人', actionKind: 'EXEC', versionNo: 5, verifyRejectCnt: 1, verifyRejectReason: '截圖不清楚' }),
  todo({ reqId: 'AR4', stageId: 7, stageName: '資訊治理複驗', actionKind: 'VERIFY', versionNo: 6, verifyRejectCnt: 1, verifyRejectReason: '截圖不清楚' }),
  todo({ reqId: 'AR5', reqTypeCode: 'MODIFY', catgId: 'DOOR', catgName: '機房門禁卡', acctName: '0012345', stageId: 6, stageName: '執行人', actionKind: 'EXEC', versionNo: 7 })
]

async function mountTodo(): Promise<VueWrapper> {
  const router = await makeRouter('/todo')
  const w = mount(TodoView, { global: { plugins: [router] } })
  await flushPromises()
  return w
}

function row(w: VueWrapper, reqId: string) {
  return w.find(`tr[data-req="${reqId}"]`)
}

describe('TodoView', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    resetSession()
    vi.mocked(getCodes).mockReset().mockResolvedValue(CODES)
    vi.mocked(listExpiring).mockReset().mockResolvedValue([])
    todosMock.mockReset().mockResolvedValue(TODOS)
    const ok = { reqId: 'x', reqStateCode: 'REVIEW', currStageId: 3, versionNo: 9 }
    approveMock.mockReset().mockResolvedValue(ok)
    rejectMock.mockReset().mockResolvedValue(ok)
    execMock.mockReset().mockResolvedValue(ok)
    verifyOkMock.mockReset().mockResolvedValue(ok)
    verifyRejectMock.mockReset().mockResolvedValue(ok)
  })

  afterEach(() => {
    vi.runAllTimers()
    vi.useRealTimers()
  })

  it('依 actionKind 顯示操作按鈕；標題用後端 title；特權列加標記', async () => {
    const w = await mountTodo()
    const approve = row(w, 'AR1')
    expect(approve.find('.act-approve').exists()).toBe(true)
    expect(approve.find('.act-reject').exists()).toBe(true)
    expect(approve.find('.act-exec').exists()).toBe(false)
    expect(approve.text()).toContain('資料庫帳號 新增 — sa')
    expect(approve.find('.tag-priv').exists()).toBe(true)
    expect(approve.classes()).toContain('priv-row')

    expect(row(w, 'AR2').find('.act-exec').exists()).toBe(true)
    expect(row(w, 'AR2').text()).toContain('（門禁卡，待發卡）')
    expect(row(w, 'AR4').find('.act-verify-ok').exists()).toBe(true)
    expect(row(w, 'AR4').find('.act-verify-reject').exists()).toBe(true)
    w.unmount()
  })

  it('核准先開確認彈窗，確認後帶 versionNo 送出', async () => {
    const w = await mountTodo()
    await row(w, 'AR1').find('.act-approve').trigger('click')
    expect(approveMock).not.toHaveBeenCalled()
    expect(w.text()).toContain('確認以「機房管理員」核准此申請？')
    await w.find('button.confirm-ok').trigger('click')
    await flushPromises()
    expect(approveMock).toHaveBeenCalledWith('AR1', 3)
    w.unmount()
  })

  it('退件原因必填（E10），填了才帶 versionNo 送出', async () => {
    const w = await mountTodo()
    await row(w, 'AR1').find('.act-reject').trigger('click')
    await w.find('button.confirm-reason').trigger('click')
    await flushPromises()
    expect(rejectMock).not.toHaveBeenCalled()
    expect(lastToast(w)).toBe('請填寫原因')

    await w.find('#reason-text').setValue('權限過大')
    await w.find('button.confirm-reason').trigger('click')
    await flushPromises()
    expect(rejectMock).toHaveBeenCalledWith('AR1', 3, '權限過大')
    expect(todosMock.mock.calls.length).toBeGreaterThanOrEqual(2)
    w.unmount()
  })

  it('門禁卡新增回報執行：卡號必填（E13）、佐證必選（E12），以 multipart 送出', async () => {
    const w = await mountTodo()
    await row(w, 'AR2').find('.act-exec').trigger('click')
    expect(w.find('#exec-card').exists()).toBe(true)
    await w.find('button.confirm-exec').trigger('click')
    await flushPromises()
    expect(lastToast(w)).toBe('請填寫門禁卡號')

    await w.find('#exec-card').setValue('0012345678')
    await w.find('button.confirm-exec').trigger('click')
    await flushPromises()
    expect(lastToast(w)).toBe('請上傳執行佐證附件')
    expect(execMock).not.toHaveBeenCalled()

    await chooseFile(w.find('#exec-file'), makeFile('截圖.png', 64, 'image/png'))
    await w.find('#exec-note').setValue('已刷卡')
    await w.find('button.confirm-exec').trigger('click')
    await flushPromises()
    expect(execMock).toHaveBeenCalledTimes(1)
    const [id, payload, file] = execMock.mock.calls[0]
    expect(id).toBe('AR2')
    expect(payload).toEqual({ versionNo: 4, cardNo: '0012345678', execDescr: '已刷卡' })
    expect(file.name).toBe('截圖.png')
    expect(file.size).toBe(64)
    w.unmount()
  })

  it('門禁卡修改不需卡號；非門禁卡也不顯示卡號欄', async () => {
    const w = await mountTodo()
    await row(w, 'AR5').find('.act-exec').trigger('click')
    expect(w.find('#exec-card').exists()).toBe(false)
    await chooseFile(w.find('#exec-file'), makeFile('門禁系統.jpg', 8, 'image/jpeg'))
    await w.find('button.confirm-exec').trigger('click')
    await flushPromises()
    expect(execMock).toHaveBeenCalledWith('AR5', { versionNo: 7, cardNo: '', execDescr: '' }, expect.any(File))
    w.unmount()
  })

  it('佐證副檔名不符提示 E14 並清掉選擇；讀檔失敗提示 U08 並擋下', async () => {
    const w = await mountTodo()
    await row(w, 'AR3').find('.act-exec').trigger('click')
    await chooseFile(w.find('#exec-file'), makeFile('proof.txt'))
    expect(lastToast(w)).toBe('附件格式僅限 .pdf .doc .docx .jpg .png')
    expect(w.find('.exec-file-name').exists()).toBe(false)

    const bad = makeFile('proof.pdf')
    Object.defineProperty(bad, 'arrayBuffer', { value: () => Promise.reject(new Error('NotReadableError')) })
    await chooseFile(w.find('#exec-file'), bad)
    await w.find('button.confirm-exec').trigger('click')
    await flushPromises()
    expect(lastToast(w)).toBe('佐證附件讀取失敗，請重新選擇檔案')
    expect(execMock).not.toHaveBeenCalled()
    w.unmount()
  })

  it('曾被複驗退回：回報彈窗與複驗彈窗都顯示 U02（含上次原因）', async () => {
    const w = await mountTodo()
    await row(w, 'AR3').find('.act-exec').trigger('click')
    expect(w.find('.verify-hint').text()).toContain('此案件先前已被複驗退回過，上次退回原因：截圖不清楚')
    await w.find('.modal .actions .btn:not(.btn-primary)').trigger('click')

    await row(w, 'AR4').find('.act-verify-ok').trigger('click')
    expect(w.find('.verify-hint').text()).toContain('上次退回原因：截圖不清楚')
    await w.find('button.confirm-ok').trigger('click')
    await flushPromises()
    expect(verifyOkMock).toHaveBeenCalledWith('AR4', 6)

    await row(w, 'AR4').find('.act-verify-reject').trigger('click')
    expect(w.find('.verify-hint').text()).toContain('上次退回原因：截圖不清楚')
    await w.find('#reason-text').setValue('仍不清楚')
    await w.find('button.confirm-reason').trigger('click')
    await flushPromises()
    expect(verifyRejectMock).toHaveBeenCalledWith('AR4', 6, '仍不清楚')
    w.unmount()
  })

  it('沒被複驗退回過的回報彈窗不顯示 U02', async () => {
    const w = await mountTodo()
    await row(w, 'AR2').find('.act-exec').trigger('click')
    expect(w.find('.verify-hint').exists()).toBe(false)
    w.unmount()
  })

  it('核准遇 409 時顯示後端訊息並重載待辦', async () => {
    approveMock.mockRejectedValue(httpError(409, { message: '此申請已由他人處理，請重新整理' }))
    const w = await mountTodo()
    await row(w, 'AR1').find('.act-approve').trigger('click')
    await w.find('button.confirm-ok').trigger('click')
    await flushPromises()
    expect(lastToast(w)).toBe('此申請已由他人處理，請重新整理')
    expect(todosMock).toHaveBeenCalledTimes(2)
    expect(w.find('button.confirm-ok').exists()).toBe(false)
    w.unmount()
  })

  it('待辦載入失敗只 toast（U09），不顯示成「目前沒有待辦簽核」', async () => {
    todosMock.mockRejectedValue(new Error('network'))
    const w = await mountTodo()
    expect(lastToast(w)).toBe('查詢失敗，請稍後再試')
    expect(w.find('.empty').exists()).toBe(false)
    w.unmount()
  })

  it('成功回空陣列才顯示「目前沒有待辦簽核」', async () => {
    todosMock.mockResolvedValue([])
    const w = await mountTodo()
    expect(w.find('.empty').text()).toBe('目前沒有待辦簽核')
    w.unmount()
  })
})
