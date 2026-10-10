// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：測試共用（假路由、代碼表假資料、後端錯誤回應）；不打真後端
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（開發文件 spec_im_acct_insp_v2.0.md）：代碼表改新格式（enabled、suggestExpiry、reviewTrigger、selectable、關卡 1～7）；
//           假路由加 /login；新增 makeMe（依權限碼組 /me）
// ============================================================
import { AxiosError, AxiosHeaders } from 'axios'
import type { AxiosResponse } from 'axios'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { Router } from 'vue-router'
import { defineComponent, h } from 'vue'
import type { Codes, Me } from '../src/types/common'

const Stub = defineComponent({ render: () => h('div') })

/** 只有路徑、沒有守衛的路由，讓畫面可用 useRoute／useRouter */
export async function makeRouter(path = '/'): Promise<Router> {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      '/',
      '/login',
      '/todo',
      '/accounts',
      '/requests',
      '/requests/new',
      '/assets',
      '/inspections',
      '/inspections/new',
      '/no-access'
    ].map(p => ({ path: p, component: Stub }))
  })
  await router.push(path)
  await router.isReady()
  return router
}

export const CODES: Codes = {
  categories: [
    { catgId: 'HOST', catgName: '主機帳號', catgDescr: '例如：POS 後台主機', enabled: true, suggestExpiry: false, sortSeq: 1 },
    { catgId: 'DB', catgName: '資料庫帳號', catgDescr: '例如：Oracle', enabled: true, suggestExpiry: false, sortSeq: 2 },
    { catgId: 'SVC', catgName: '服務帳號', catgDescr: '例如：備份服務', enabled: false, suggestExpiry: true, sortSeq: 4 },
    { catgId: 'VENDOR', catgName: '廠商/委外帳號', catgDescr: '例如：委外維護廠商臨時帳號', enabled: true, suggestExpiry: true, sortSeq: 6 },
    { catgId: 'CERT', catgName: '憑證/金鑰', catgDescr: '例如：SSL 憑證', enabled: false, suggestExpiry: true, sortSeq: 7 },
    { catgId: 'DOOR', catgName: '機房門禁卡', catgDescr: '卡鐘', enabled: true, suggestExpiry: false, sortSeq: 8 }
  ],
  assetClasses: [
    { assetClassId: 'H01', assetTypeCode: 'H', assetClassName: '伺服器－與營運直接相關', reviewTrigger: true, selectable: true, ctrlDescr: null, sortSeq: 1 },
    { assetClassId: 'H02', assetTypeCode: 'H', assetClassName: '伺服器－與營運間接相關', reviewTrigger: false, selectable: true, ctrlDescr: null, sortSeq: 2 },
    { assetClassId: 'H07', assetTypeCode: 'H', assetClassName: '個人電腦（含 NB）', reviewTrigger: false, selectable: false, ctrlDescr: null, sortSeq: 7 }
  ],
  stages: [
    { stageId: 1, stageName: '申請人送出', stageTypeCode: 'SUBMIT', permId: null },
    { stageId: 2, stageName: '機房管理員', stageTypeCode: 'APPR', permId: 'APPR_G2' },
    { stageId: 3, stageName: 'Infra 主管', stageTypeCode: 'APPR', permId: 'APPR_G3' },
    { stageId: 4, stageName: '資訊治理', stageTypeCode: 'APPR', permId: 'APPR_G4' },
    { stageId: 5, stageName: '資訊主管', stageTypeCode: 'APPR', permId: 'APPR_G5' },
    { stageId: 6, stageName: '執行人', stageTypeCode: 'EXEC', permId: null },
    { stageId: 7, stageName: '資訊治理複驗', stageTypeCode: 'VERIFY', permId: 'VERIFY_G7' }
  ]
}

/** GENERAL 角色的權限（每位啟用中使用者都有） */
export const GENERAL_PERMS = ['ACC_APPLY', 'ACC_SELF_SERVICE', 'EMG_APPLY', 'STATS_VIEW']

/** 組一份 /me；預設 Kevin、只有 GENERAL 權限、未切換 */
export function makeMe(over: Partial<Me> = {}): Me {
  return {
    userId: '0000001',
    userName: 'Kevin (李凱鈞)',
    realUserId: '0000001',
    realUserName: 'Kevin (李凱鈞)',
    impersonating: false,
    canImpersonate: false,
    permissions: [...GENERAL_PERMS],
    roleIds: [],
    execCatgIds: [],
    ...over
  }
}

/** 模擬後端回非 2xx（殼 jar 原樣轉回）：axios 會丟 AxiosError，response.data 為後端本文 */
export function httpError(status: number, data: unknown, url = '/x'): AxiosError {
  const config = { headers: new AxiosHeaders(), url }
  const response: AxiosResponse = { data, status, statusText: '', headers: {}, config }
  return new AxiosError(`Request failed with status code ${status}`, 'ERR_BAD_RESPONSE', config, null, response)
}

/** 最後一則 toast 的文字 */
export function lastToast(root: { findAll: (sel: string) => { text: () => string }[] }): string {
  const list = root.findAll('.toast')
  return list.length ? list[list.length - 1].text() : ''
}

/** 建一個指定大小的假檔案（內容為 0） */
export function makeFile(name: string, size = 16, type = 'application/pdf'): File {
  return new File([new Uint8Array(size)], name, { type })
}

/** 對 input[type=file] 設定選取的檔案並觸發 change */
export async function chooseFile(input: { element: Element; trigger: (ev: string) => Promise<void> }, file: File): Promise<void> {
  Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
  await input.trigger('change')
}
