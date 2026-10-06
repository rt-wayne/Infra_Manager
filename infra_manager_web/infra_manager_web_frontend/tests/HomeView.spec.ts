// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-05
// 變更說明: 新增：HomeView 測試（S1）；以 vi.mock 替換 API，不打真後端
//           驗：載入時呼叫 /health；DB UP 顯示兩個正常；DB DOWN 顯示資料庫無法連線並出 toast；API 失敗顯示後端無法連線並出 toast
// ============================================================
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import HomeView from '../src/views/HomeView.vue'
import { getHealth } from '../src/api/health'

vi.mock('../src/api/health', () => ({
  getHealth: vi.fn()
}))

const healthMock = vi.mocked(getHealth)

describe('HomeView', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    healthMock.mockReset()
  })

  afterEach(() => {
    vi.runAllTimers()
    vi.useRealTimers()
  })

  it('載入時呼叫 health，DB UP 時兩項都顯示正常', async () => {
    healthMock.mockResolvedValue({ status: 'UP', db: 'UP', time: '2026-10-05 10:00:00' })
    const wrapper = mount(HomeView)
    await flushPromises()

    expect(healthMock).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('機房設備異動申請系統')
    expect(wrapper.findAll('dd.up')).toHaveLength(2)
    expect(wrapper.text()).toContain('2026-10-05 10:00:00')
    expect(wrapper.find('.toast').exists()).toBe(false)
    wrapper.unmount()
  })

  it('DB DOWN 時資料庫顯示無法連線並出 toast', async () => {
    healthMock.mockResolvedValue({ status: 'UP', db: 'DOWN', time: '2026-10-05 10:00:00' })
    const wrapper = mount(HomeView)
    await flushPromises()

    expect(wrapper.findAll('dd.up')).toHaveLength(1)
    expect(wrapper.findAll('dd.down')).toHaveLength(1)
    expect(wrapper.text()).toContain('資料庫無法連線')
    wrapper.unmount()
  })

  it('API 失敗時後端顯示無法連線並出 toast', async () => {
    healthMock.mockRejectedValue(new Error('network'))
    const wrapper = mount(HomeView)
    await flushPromises()

    expect(wrapper.find('dd.down').exists()).toBe(true)
    expect(wrapper.text()).toContain('後端服務呼叫失敗，請稍後再試')
    expect(wrapper.text()).not.toContain('查無資料')
    wrapper.unmount()
  })

  it('按重新檢查會再呼叫一次', async () => {
    healthMock.mockResolvedValue({ status: 'UP', db: 'UP', time: '2026-10-05 10:00:00' })
    const wrapper = mount(HomeView)
    await flushPromises()

    await wrapper.find('button.go').trigger('click')
    await flushPromises()

    expect(healthMock).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })
})
