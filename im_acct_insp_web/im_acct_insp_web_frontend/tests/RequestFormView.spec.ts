// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：申請表單測試（開發文件 spec_im_acct_insp.md 第 4.3、6、7 節）
//           驗證順序、門禁卡欄位切換、資產分類碼／特權提示、異動申請帶入與鎖定、後端 400 message 顯示
//           以 vi.mock 替換 api 模組，不打真後端
//           變更單 #1：S1 異動申請帶入改走 GET /accounts/lookup/{id}（不呼叫 /accounts/{id}）；M3 重新送出附件檔名留空
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（開發文件 spec_im_acct_insp_v2.0.md 第 4.3、4.9、4.10、4.15、8.3 節）：
//           E01～E04 順序與文案（E02 一次列出缺的欄位）、到期日不可早於今天；新增時類別只列 enabled、U04；
//           依類型顯示欄位（停用／刪除隱藏權限範圍、存取對象、有效期限，原因欄名、U03）；
//           申請人分機／Email 不送出、「同申請人」帶入；公文附件檢查（E14、E15）、讀檔失敗 U07、multipart 送出；
//           重送帶 versionNo、保留申請單位
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 變更單 #1（M1，T14）：門禁卡新增時 DOM 內無帳號名稱欄；修改門禁卡時以唯讀顯示 lookup 帶入的卡號
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 變更單 #2：「卡號無法在申請時預先填寫」提示只在門禁卡新增時出現，修改門禁卡時不出現
// ============================================================
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import type { VueWrapper } from '@vue/test-utils'
import RequestFormView from '../src/views/RequestFormView.vue'
import { getCodes } from '../src/api/common'
import { getAccount, getLookupAccount } from '../src/api/account'
import { getRequest, resubmitRequest, submitRequest } from '../src/api/request'
import { resetSession, useSession } from '../src/composables/useSession'
import type { AccountLookupDetail } from '../src/types/account'
import type { RequestDetail } from '../src/types/request'
import { fmtDate } from '../src/utils/date'
import { CODES, chooseFile, httpError, lastToast, makeFile, makeMe, makeRouter } from './helpers'

vi.mock('../src/api/common', () => ({
  getCodes: vi.fn(),
  getHomeStats: vi.fn(),
  listExpiring: vi.fn(),
  listTodos: vi.fn()
}))
vi.mock('../src/api/account', () => ({
  listAccounts: vi.fn(),
  getAccount: vi.fn(),
  getAccountHistory: vi.fn(),
  listMyAccounts: vi.fn(),
  lookupAccounts: vi.fn(),
  getLookupAccount: vi.fn()
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

const codesMock = vi.mocked(getCodes)
const accountMock = vi.mocked(getAccount)
const lookupDetailMock = vi.mocked(getLookupAccount)
const requestMock = vi.mocked(getRequest)
const submitMock = vi.mocked(submitRequest)
const resubmitMock = vi.mocked(resubmitRequest)

function daysFromToday(n: number): string {
  const d = new Date()
  d.setDate(d.getDate() + n)
  return fmtDate(d)
}

const ACCT: AccountLookupDetail = {
  acctId: 'AR20261001-001', catgId: 'DB', catgName: '資料庫帳號', acctName: 'sa', sysName: '財務 Oracle', doorScopeCode: null,
  assetClassId: 'H01', isPriv: 1, holderName: '王小明', holderDept: '資訊處', holderCntct: '1234', holderEmail: null,
  accessLevelCode: 'ADMIN', accessTarget: 'FINDB', expiryTypeCode: 'DATE', expiryDate: '2020-01-01', acctStateCode: 'APPROVED'
}

async function mountForm(path = '/requests/new'): Promise<VueWrapper> {
  const router = await makeRouter(path)
  const wrapper = mount(RequestFormView, { global: { plugins: [router] } })
  await flushPromises()
  return wrapper
}

async function clickSubmit(w: VueWrapper): Promise<void> {
  await w.find('button.submit').trigger('click')
  await flushPromises()
}

/** 填好一般新增的必填欄位 */
async function fillAdd(w: VueWrapper): Promise<void> {
  await w.find('#isUniqueConf').setValue(true)
  await w.find('#applyDept').setValue('資訊處')
  await w.find('#acctName').setValue('svc_backup')
  await w.find('#sysName').setValue('備份主機')
  await w.find('#holderName').setValue('王小明')
  await w.find('#applyReason').setValue('系統維運需要')
}

describe('RequestFormView', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    resetSession()
    useSession().me.value = makeMe({ telNo: '5566', email: 'kevin@example.com' })
    codesMock.mockReset().mockResolvedValue(CODES)
    accountMock.mockReset()
    lookupDetailMock.mockReset()
    requestMock.mockReset()
    submitMock.mockReset().mockResolvedValue({ reqId: 'AR20261007-001' })
    resubmitMock.mockReset().mockResolvedValue({ reqId: 'AR20261005-002', reqStateCode: 'REVIEW', currStageId: 2, versionNo: 3 })
  })

  afterEach(() => {
    vi.runAllTimers()
    vi.useRealTimers()
  })

  it('送出驗證順序 E01 → E02（一次列出缺的欄位）→ E03 → E04，通過才以 multipart 送出', async () => {
    const w = await mountForm()
    expect((w.find('#catgId').element as HTMLSelectElement).value).toBe('HOST')

    await clickSubmit(w)
    expect(lastToast(w)).toBe('請勾選帳號唯一性聲明')

    await w.find('#isUniqueConf').setValue(true)
    await clickSubmit(w)
    expect(lastToast(w)).toBe('請填寫必填欄位：申請單位、帳號名稱、所屬系統、使用人、申請原因')

    await w.find('#applyDept').setValue('資訊處')
    await w.find('#acctName').setValue('svc_backup')
    await w.find('#sysName').setValue('備份主機')
    await w.find('#holderName').setValue('王小明')
    await clickSubmit(w)
    expect(lastToast(w)).toBe('請填寫必填欄位：申請原因')

    await w.find('#applyReason').setValue('系統維運需要')
    await w.find('input[name=expiry][value=DATE]').setValue(true)
    await clickSubmit(w)
    expect(lastToast(w)).toBe('請填寫到期日')

    await w.find('#expiryDate').setValue(daysFromToday(-1))
    await clickSubmit(w)
    expect(lastToast(w)).toBe('到期日不可早於今天')
    expect(submitMock).not.toHaveBeenCalled()

    const exp = daysFromToday(60)
    await w.find('#expiryDate').setValue(exp)
    await clickSubmit(w)
    expect(submitMock).toHaveBeenCalledTimes(1)
    const [payload, file] = submitMock.mock.calls[0]
    expect(file).toBeNull()
    expect(payload).toMatchObject({
      reqTypeCode: 'ADD', acctId: null, applyDept: '資訊處', catgId: 'HOST', acctName: 'svc_backup', sysName: '備份主機',
      holderName: '王小明', accessLevelCode: 'RO', expiryTypeCode: 'DATE', expiryDate: exp, isUniqueConf: 1, isPriv: 0,
      assetClassId: null, doorScopeCode: null
    })
    expect(payload).not.toHaveProperty('applyTelNo')
    expect(payload).not.toHaveProperty('applyEmail')
    expect(payload).not.toHaveProperty('attachFileName')
    w.unmount()
  })

  it('到期日＝今天可送出', async () => {
    const w = await mountForm()
    await fillAdd(w)
    await w.find('input[name=expiry][value=DATE]').setValue(true)
    await w.find('#expiryDate').setValue(daysFromToday(0))
    await clickSubmit(w)
    expect(submitMock).toHaveBeenCalledTimes(1)
    w.unmount()
  })

  it('新增：類別下拉只列 enabled；suggestExpiry 類別顯示 U04；申請日期唯讀、申請單位空白；刪除展延', async () => {
    const w = await mountForm()
    const opts = w.find('#catgId').findAll('option').map(o => o.attributes('value'))
    expect(opts).toEqual(['HOST', 'DB', 'VENDOR', 'DOOR'])
    expect(w.find('.suggest-expiry').exists()).toBe(false)
    await w.find('#catgId').setValue('VENDOR')
    await flushPromises()
    expect(w.find('.suggest-expiry').text()).toContain('建議指定明確到期日')

    expect((w.find('#applyDate').element as HTMLInputElement).disabled).toBe(true)
    expect((w.find('#applyDate').element as HTMLInputElement).value).toBe(fmtDate(new Date()))
    expect((w.find('#applyDept').element as HTMLInputElement).value).toBe('')
    expect(w.findAll('input[name=reqType]').map(i => i.attributes('value'))).toEqual(['ADD', 'MODIFY', 'DISABLE', 'ENABLE', 'DELETE'])
    expect(w.text()).not.toContain('展延')
    w.unmount()
  })

  it('「同申請人」：使用人＝登入者、聯絡方式＝分機→Email→使用者資料、所屬單位空白才帶申請單位；分機與 Email 不送出', async () => {
    const w = await mountForm()
    await w.find('#applyDept').setValue('資訊處')
    await w.find('.same-as').trigger('click')
    expect((w.find('#holderName').element as HTMLInputElement).value).toBe('Kevin (李凱鈞)')
    expect((w.find('#holderCntct').element as HTMLInputElement).value).toBe('5566')
    expect((w.find('#holderDept').element as HTMLInputElement).value).toBe('資訊處')

    await w.find('#applyEmail').setValue('me@example.com')
    await w.find('#holderDept').setValue('營運處')
    await w.find('.same-as').trigger('click')
    expect((w.find('#holderCntct').element as HTMLInputElement).value).toBe('me@example.com')
    expect((w.find('#holderDept').element as HTMLInputElement).value).toBe('營運處')

    await w.find('#applyTelNo').setValue('1234')
    await w.find('.same-as').trigger('click')
    expect((w.find('#holderCntct').element as HTMLInputElement).value).toBe('1234')

    // 門禁卡：使用人 Email＝表單 Email
    await w.find('#catgId').setValue('DOOR')
    await flushPromises()
    await w.find('.same-as').trigger('click')
    expect((w.find('#holderEmail').element as HTMLInputElement).value).toBe('me@example.com')

    await w.find('#isUniqueConf').setValue(true)
    await w.find('#applyReason').setValue('新進人員')
    await clickSubmit(w)
    const payload = submitMock.mock.calls[0][0]
    expect(payload).not.toHaveProperty('applyTelNo')
    expect(payload).not.toHaveProperty('applyEmail')
    expect(payload.holderEmail).toBe('me@example.com')
    w.unmount()
  })

  it('門禁卡：隱藏帳號名稱輸入、所屬系統、權限範圍、存取對象、聯絡方式；顯示門禁權限範圍與使用人 Email', async () => {
    const w = await mountForm()
    expect(w.find('#acctName').exists()).toBe(true)
    await w.find('#catgId').setValue('DOOR')
    await flushPromises()
    expect(w.find('#acctName').exists()).toBe(false)
    expect(w.find('label[for=acctName]').exists()).toBe(false)
    expect(w.find('#sysName').exists()).toBe(false)
    expect(w.find('#accessLevel').exists()).toBe(false)
    expect(w.find('#accessTarget').exists()).toBe(false)
    expect(w.find('#holderCntct').exists()).toBe(false)
    expect(w.find('#doorScope').exists()).toBe(true)
    expect(w.find('#holderEmail').exists()).toBe(true)
    expect(w.text()).toContain('卡號無法在申請時預先填寫')
    expect((w.find('#assetClassId').element as HTMLSelectElement).disabled).toBe(true)

    await w.find('#isUniqueConf').setValue(true)
    await clickSubmit(w)
    expect(lastToast(w)).toBe('請填寫必填欄位：申請單位、使用人、申請原因')

    await w.find('#applyDept').setValue('資訊處')
    await w.find('#applyReason').setValue('新進人員')
    await w.find('#holderName').setValue('王小明')
    await w.find('#doorScope').setValue('FULL')
    await clickSubmit(w)
    expect(submitMock.mock.calls[0][0]).toMatchObject({
      catgId: 'DOOR', acctName: '', sysName: '', doorScopeCode: 'FULL', accessLevelCode: 'DOOR', assetClassId: null, holderCntct: ''
    })
    w.unmount()
  })

  it('資產分類只列可選碼、審查條件碼加標記與提示', async () => {
    const w = await mountForm()
    const values = w.find('#assetClassId').findAll('option').map(o => o.attributes('value'))
    expect(values).toContain('H01')
    expect(values).not.toContain('H07')
    expect(w.find('option.direct').text()).toContain('H01')
    expect(w.find('.asset-review-hint').exists()).toBe(false)
    await w.find('#assetClassId').setValue('H01')
    expect(w.find('.asset-review-hint').exists()).toBe(true)
    // 簽核路徑預覽：7 關名稱
    expect(w.findAll('.approval-step').map(s => s.find('.step-role').text())).toEqual(
      ['申請人送出', '機房管理員', 'Infra 主管', '資訊治理', '資訊主管', '執行人', '資訊治理複驗']
    )
    w.unmount()
  })

  it('修改：帶入 lookup/{id}?acctName，類別／名稱／系統鎖定；到期日早於今天擋下（E04）', async () => {
    lookupDetailMock.mockResolvedValue(ACCT)
    const w = await mountForm('/requests/new?reqType=MODIFY&acctId=AR20261001-001&acctName=sa')
    expect(lookupDetailMock).toHaveBeenCalledWith('AR20261001-001', 'sa')
    expect(accountMock).not.toHaveBeenCalled()
    expect((w.find('#catgId').element as HTMLSelectElement).disabled).toBe(true)
    expect((w.find('#acctName').element as HTMLInputElement).value).toBe('sa')
    expect((w.find('#acctName').element as HTMLInputElement).disabled).toBe(true)
    expect((w.find('#sysName').element as HTMLInputElement).disabled).toBe(true)
    expect((w.find('#accessLevel').element as HTMLSelectElement).value).toBe('ADMIN')
    expect((w.find('#isPriv').element as HTMLInputElement).checked).toBe(true)
    expect((w.find('#applyDept').element as HTMLInputElement).value).toBe('')
    expect((w.find('input[name=reqType][value=MODIFY]').element as HTMLInputElement).checked).toBe(true)

    await w.find('#isUniqueConf').setValue(true)
    await w.find('#applyDept').setValue('資訊處')
    await w.find('#applyReason').setValue('延長使用')
    await clickSubmit(w)
    expect(lastToast(w)).toBe('到期日不可早於今天')

    const exp = daysFromToday(90)
    await w.find('#expiryDate').setValue(exp)
    await w.find('#isPriv').setValue(false)
    await clickSubmit(w)
    expect(submitMock.mock.calls[0][0]).toMatchObject({ reqTypeCode: 'MODIFY', acctId: 'AR20261001-001', isPriv: 0, expiryDate: exp })
    w.unmount()
  })

  it('修改門禁卡：帳號名稱欄以唯讀顯示 lookup 帶入的卡號', async () => {
    lookupDetailMock.mockResolvedValue({
      ...ACCT, catgId: 'DOOR', catgName: '機房門禁卡', acctName: '0012345678', sysName: '機房門禁管制系統', doorScopeCode: 'FULL',
      assetClassId: null, isPriv: 0, holderCntct: null, holderEmail: 'wang@example.com', accessLevelCode: 'DOOR', accessTarget: null,
      expiryTypeCode: 'PERM', expiryDate: null
    })
    const w = await mountForm('/requests/new?reqType=MODIFY&acctId=AR20261001-002&acctName=0012345678')
    const input = w.find('#acctName').element as HTMLInputElement
    expect(input.value).toBe('0012345678')
    expect(input.readOnly).toBe(true)
    expect(input.disabled).toBe(true)
    expect(w.find('label[for=acctName]').text()).toBe('帳號名稱（卡號）')
    expect(w.find('#sysName').exists()).toBe(false)
    expect((w.find('#doorScope').element as HTMLSelectElement).value).toBe('FULL')
    expect(w.text()).not.toContain('卡號無法在申請時預先填寫')
    w.unmount()
  })

  it('停用：隱藏權限範圍、存取對象、有效期限；原因欄名「停用原因」；只需申請單位與原因、不檢查到期日', async () => {
    lookupDetailMock.mockResolvedValue(ACCT)
    const w = await mountForm('/requests/new?reqType=DISABLE&acctId=AR20261001-001&acctName=sa')
    expect(w.find('#accessLevel').exists()).toBe(false)
    expect(w.find('#accessTarget').exists()).toBe(false)
    expect(w.find('#expiryDate').exists()).toBe(false)
    expect(w.find('input[name=expiry]').exists()).toBe(false)
    expect(w.find('label[for=applyReason]').text()).toBe('停用原因')
    expect(w.find('.delete-warn').exists()).toBe(false)
    expect((w.find('#holderName').element as HTMLInputElement).disabled).toBe(true)

    await w.find('#isUniqueConf').setValue(true)
    await clickSubmit(w)
    expect(lastToast(w)).toBe('請填寫必填欄位：申請單位、停用原因')

    await w.find('#applyDept').setValue('資訊處')
    await w.find('#applyReason').setValue('離職')
    await clickSubmit(w)
    // 主表到期日已過也不擋（停用不做到期日檢查）
    expect(submitMock).toHaveBeenCalledTimes(1)
    expect(submitMock.mock.calls[0][0]).toMatchObject({ reqTypeCode: 'DISABLE', applyReason: '離職' })
    w.unmount()
  })

  it('刪除：顯示 U03 警示、原因欄名「刪除原因」、隱藏期限欄', async () => {
    lookupDetailMock.mockResolvedValue({ ...ACCT, acctStateCode: 'DISABLED' })
    const w = await mountForm('/requests/new?reqType=DELETE&acctId=AR20261001-001&acctName=sa')
    expect(w.find('.delete-warn').text()).toContain('刪除後此帳號將無法再啟用或異動，僅保留歷史紀錄供稽核查詢。')
    expect(w.find('label[for=applyReason]').text()).toBe('刪除原因')
    expect(w.find('#expiryDate').exists()).toBe(false)
    await w.find('#isUniqueConf').setValue(true)
    await w.find('#applyDept').setValue('資訊處')
    await clickSubmit(w)
    expect(lastToast(w)).toBe('請填寫必填欄位：刪除原因')
    w.unmount()
  })

  it('帶入失敗（例 404）：toast 後端訊息、不可送出', async () => {
    lookupDetailMock.mockRejectedValue(httpError(404, { message: '查無資料' }))
    const w = await mountForm('/requests/new?reqType=DISABLE&acctId=AR20261001-009&acctName=x')
    expect(lastToast(w)).toBe('查無資料')
    expect((w.find('button.submit').element as HTMLButtonElement).disabled).toBe(true)
    w.unmount()
  })

  it('公文附件：副檔名不符 E14、超過 10 MB E15，選了合格檔才隨 multipart 送出', async () => {
    const w = await mountForm()
    await fillAdd(w)
    const input = w.find('#docFile')

    await chooseFile(input, makeFile('setup.exe'))
    expect(lastToast(w)).toBe('附件格式僅限 .pdf .doc .docx .jpg .png')
    expect(w.find('.doc-file-name').exists()).toBe(false)

    const big = makeFile('big.pdf')
    Object.defineProperty(big, 'size', { value: 10 * 1024 * 1024 + 1 })
    await chooseFile(input, big)
    expect(lastToast(w)).toBe('附件大小不可超過 10 MB')
    expect(w.find('.doc-file-name').exists()).toBe(false)

    await chooseFile(input, makeFile('公文.PDF', 32))
    expect(w.find('.doc-file-name').text()).toContain('公文.PDF')
    await clickSubmit(w)
    expect(submitMock).toHaveBeenCalledTimes(1)
    const file = submitMock.mock.calls[0][1]
    expect(file?.name).toBe('公文.PDF')
    expect(file?.size).toBe(32)
    w.unmount()
  })

  it('公文讀取失敗：出現 U07，確認後不附公文送出', async () => {
    const w = await mountForm()
    await fillAdd(w)
    const bad = makeFile('公文.pdf')
    Object.defineProperty(bad, 'arrayBuffer', { value: () => Promise.reject(new Error('NotReadableError')) })
    await chooseFile(w.find('#docFile'), bad)
    await clickSubmit(w)
    expect(submitMock).not.toHaveBeenCalled()
    expect(w.find('.doc-read-fail').text()).toBe('公文附件讀取失敗，是否不附公文直接送出？')

    await w.find('button.send-without-doc').trigger('click')
    await flushPromises()
    expect(submitMock).toHaveBeenCalledTimes(1)
    expect(submitMock.mock.calls[0][1]).toBeNull()
    w.unmount()
  })

  it('後端回 400 時 toast 顯示後端 message', async () => {
    submitMock.mockRejectedValue(httpError(400, { message: '此帳號類別尚未開放申請' }))
    const w = await mountForm()
    await fillAdd(w)
    await clickSubmit(w)
    expect(lastToast(w)).toBe('此帳號類別尚未開放申請')
    w.unmount()
  })

  it('退件重送：帶入原內容、保留申請單位、顯示原申請日期，送出帶 versionNo', async () => {
    const req: RequestDetail = {
      reqId: 'AR20261005-002', acctId: 'AR20261005-002', reqTypeCode: 'ADD', reqStateCode: 'REJECTED', currStageId: null,
      applyBy: '0000001', applyDept: '資訊處', applyDate: '2026-10-05 10:00:00', catgId: 'HOST', acctName: 'svc_a', sysName: '主機 A',
      doorScopeCode: null, assetClassId: null, isPriv: 0, holderName: '王小明', holderDept: '資訊處', holderCntct: null, holderEmail: null,
      accessLevelCode: 'RO', accessTarget: null, applyReason: '原因', expiryTypeCode: 'PERM', expiryDate: null,
      submitDate: '2026-10-05 10:00:00', rejectReason: '名稱不符', versionNo: 2,
      attachments: [{ attachId: 5, attachTypeCode: 'DOC', fileName: '舊公文.pdf', fileSize: 2048, createDate: '2026-10-05 10:00:00' }]
    }
    requestMock.mockResolvedValue(req)
    const w = await mountForm('/requests/new?resubmit=AR20261005-002')
    expect((w.find('#acctName').element as HTMLInputElement).value).toBe('svc_a')
    expect((w.find('#applyDept').element as HTMLInputElement).value).toBe('資訊處')
    expect((w.find('#applyDate').element as HTMLInputElement).value).toBe('2026-10-05')
    expect(w.find('.old-doc').text()).toContain('舊公文.pdf')
    expect(w.text()).toContain('名稱不符')

    await w.find('#isUniqueConf').setValue(true)
    await w.find('#acctName').setValue('svc_b')
    await clickSubmit(w)
    expect(submitMock).not.toHaveBeenCalled()
    expect(resubmitMock).toHaveBeenCalledTimes(1)
    const [id, payload, file] = resubmitMock.mock.calls[0]
    expect(id).toBe('AR20261005-002')
    expect(payload).toMatchObject({ versionNo: 2, acctName: 'svc_b', applyDept: '資訊處' })
    expect(file).toBeNull()
    w.unmount()
  })
})
