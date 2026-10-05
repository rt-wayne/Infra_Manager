// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-05
// 變更說明: 新增：ExampleView 範例測試（規格 D-42）；以 vi.mock 替換 API，不打真後端
//           驗：空輸入不送查詢、查詢失敗顯示 toast 且不顯示「查無資料」、查詢成功顯示筆數與分頁
//           審查修正：beforeEach 改用假計時器、afterEach 跑完計時器清空 toast，避免 toast 狀態跨案殘留
// ============================================================
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ExampleView from '../src/views/ExampleView.vue'
import { search } from '../src/api/example'
import type { ExampleRow } from '../src/types/example'

vi.mock('../src/api/example', () => ({
  search: vi.fn(),
  list: vi.fn()
}))

const searchMock = vi.mocked(search)

describe('ExampleView', () => {
  beforeEach(() => {
    // toast 清單是模組層共用狀態：用假計時器，每案結束時把 3 秒自動移除跑完，避免跨案殘留
    // 只假 setTimeout（toast 用），flushPromises 用的 setImmediate 維持真實
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    searchMock.mockReset()
  })

  afterEach(() => {
    vi.runAllTimers()
    vi.useRealTimers()
  })

  it('空輸入時不送查詢，只提示', async () => {
    const wrapper = mount(ExampleView)

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(searchMock).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('請輸入關鍵字')
    expect(wrapper.find('.empty').exists()).toBe(false)
    wrapper.unmount()
  })

  it('查詢失敗時顯示 toast，不顯示成查無資料', async () => {
    searchMock.mockRejectedValue(new Error('network'))
    const wrapper = mount(ExampleView)

    await wrapper.find('#kw').setValue('abc')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(searchMock).toHaveBeenCalledWith({ keyword: 'abc' })
    expect(wrapper.text()).toContain('查詢失敗，請稍後再試')
    expect(wrapper.find('.empty').exists()).toBe(false)
    wrapper.unmount()
  })

  it('查詢成功時顯示筆數，每頁 20 筆', async () => {
    const rows: ExampleRow[] = Array.from({ length: 25 }, (_, i) => ({ code: `C${i}`, name: `N${i}` }))
    searchMock.mockResolvedValue(rows)
    const wrapper = mount(ExampleView)

    await wrapper.find('#kw').setValue('abc')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('共 25 筆')
    expect(wrapper.findAll('tbody tr')).toHaveLength(20)
    expect(wrapper.find('.pager').exists()).toBe(true)
    wrapper.unmount()
  })
})
