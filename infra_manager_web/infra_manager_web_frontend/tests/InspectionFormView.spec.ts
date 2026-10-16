// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：新增巡檢測試（開發文件 spec_im_acct_insp.md 第 3.8、6、7 節）
//           依 sectName 分組、異常數即時統計、異常說明必填、送出本文涵蓋全部項目
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（開發文件 spec_im_acct_insp_v2.0.md 第 4.14、8.3 節）：結果只剩正常／異常、無預設、全部選完才能送出；
//           異常說明必填（E10）；巡檢時間不可晚於現在；送出格式 yyyy-MM-dd HH:mm:ss；切換中巡檢人員顯示代理身分
// ============================================================
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import type { VueWrapper } from '@vue/test-utils'
import InspectionFormView from '../src/views/InspectionFormView.vue'
import { listInspItems, submitInspection } from '../src/api/inspection'
import { resetSession, useSession } from '../src/composables/useSession'
import type { InspItem } from '../src/types/inspection'
import { toLocalInput } from '../src/utils/date'
import { lastToast, makeMe, makeRouter } from './helpers'

vi.mock('../src/api/inspection', () => ({
  listInspItems: vi.fn(),
  submitInspection: vi.fn(),
  listInspections: vi.fn(),
  getInspection: vi.fn(),
  fixInspItem: vi.fn()
}))

const itemsMock = vi.mocked(listInspItems)
const submitMock = vi.mocked(submitInspection)

const ITEMS: InspItem[] = [
  { inspItemId: 'A01', sectCode: 'A', sectName: '甲、機房環境設施', itemName: '機房溫度' },
  { inspItemId: 'A02', sectCode: 'A', sectName: '甲、機房環境設施', itemName: '機房溼度' },
  { inspItemId: 'B01', sectCode: 'B', sectName: '乙、資訊設備狀態', itemName: '交換器面板燈是否全部正常' }
]

async function mountForm(): Promise<VueWrapper> {
  const router = await makeRouter('/inspections/new')
  const w = mount(InspectionFormView, { global: { plugins: [router] } })
  await flushPromises()
  return w
}

describe('InspectionFormView', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    resetSession()
    useSession().me.value = makeMe({ permissions: ['INSP_VIEW', 'INSP_CREATE'] })
    itemsMock.mockReset().mockResolvedValue(ITEMS)
    submitMock.mockReset().mockResolvedValue({ inspId: 'IN20261007-001' })
  })

  afterEach(() => {
    vi.runAllTimers()
    vi.useRealTimers()
  })

  it('依大類分組；每項只有正常／異常、無預設；全部選完前不能送出', async () => {
    const w = await mountForm()
    expect(w.findAll('.divider-lbl').map(d => d.text())).toEqual(['甲、機房環境設施', '乙、資訊設備狀態'])
    expect(w.findAll('input[name=chk-A01]').map(i => i.attributes('value'))).toEqual(['NORMAL', 'EXCPT'])
    expect(w.findAll('input[type=radio]:checked')).toHaveLength(0)
    expect(w.find('.unset-count').text()).toContain('尚有 3 項未選擇結果')
    expect((w.find('button.submit').element as HTMLButtonElement).disabled).toBe(true)

    await w.find('input[name=chk-A01][value=NORMAL]').setValue(true)
    await w.find('input[name=chk-A02][value=EXCPT]').setValue(true)
    expect(w.find('.excpt-count').text()).toBe('1')
    expect(w.find('#desc-A02').exists()).toBe(true)
    expect((w.find('button.submit').element as HTMLButtonElement).disabled).toBe(true)

    await w.find('input[name=chk-B01][value=NORMAL]').setValue(true)
    expect(w.find('.unset-count').exists()).toBe(false)
    expect((w.find('button.submit').element as HTMLButtonElement).disabled).toBe(false)
    w.unmount()
  })

  it('異常未填說明擋下（E10）；填了才送出，時間格式含秒、涵蓋全部項目', async () => {
    const w = await mountForm()
    await w.find('input[name=chk-A01][value=NORMAL]').setValue(true)
    await w.find('input[name=chk-A02][value=EXCPT]').setValue(true)
    await w.find('input[name=chk-B01][value=NORMAL]').setValue(true)
    await w.find('button.submit').trigger('click')
    await flushPromises()
    expect(submitMock).not.toHaveBeenCalled()
    expect(lastToast(w)).toBe('請填寫異常說明')

    await w.find('#desc-A02').setValue('溼度 80%')
    await w.find('#inspDate').setValue('2026-10-07T09:30')
    await w.find('button.submit').trigger('click')
    await flushPromises()
    expect(submitMock).toHaveBeenCalledWith({
      inspDate: '2026-10-07 09:30:00',
      results: [
        { inspItemId: 'A01', resultCode: 'NORMAL', excptDescr: '' },
        { inspItemId: 'A02', resultCode: 'EXCPT', excptDescr: '溼度 80%' },
        { inspItemId: 'B01', resultCode: 'NORMAL', excptDescr: '' }
      ]
    })
    w.unmount()
  })

  it('巡檢時間不可晚於現在', async () => {
    const w = await mountForm()
    for (const id of ['A01', 'A02', 'B01']) {
      await w.find(`input[name=chk-${id}][value=NORMAL]`).setValue(true)
    }
    const future = new Date(Date.now() + 2 * 60 * 60 * 1000)
    await w.find('#inspDate').setValue(toLocalInput(future))
    await w.find('button.submit').trigger('click')
    await flushPromises()
    expect(submitMock).not.toHaveBeenCalled()
    expect(lastToast(w)).toBe('巡檢時間不可晚於現在')
    expect(w.find('#inspDate').attributes('max')).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/)
    w.unmount()
  })

  it('切換身分中：巡檢人員顯示「{代理}（實際操作人：{實際}）」', async () => {
    useSession().me.value = makeMe({
      userId: '0000001', userName: 'Kevin (李凱鈞)', realUserId: '0000011', realUserName: 'Hank (黃志翰)',
      impersonating: true, permissions: ['INSP_CREATE']
    })
    const w = await mountForm()
    expect((w.find('#inspector').element as HTMLInputElement).value).toBe('Kevin (李凱鈞)（實際操作人：Hank (黃志翰)）')
    w.unmount()
  })

  it('項目載入失敗只 toast（U09），不顯示表單', async () => {
    itemsMock.mockRejectedValue(new Error('network'))
    const w = await mountForm()
    expect(lastToast(w)).toBe('查詢失敗，請稍後再試')
    expect(w.find('.check-item').exists()).toBe(false)
    expect(w.find('button.submit').exists()).toBe(false)
    w.unmount()
  })
})
