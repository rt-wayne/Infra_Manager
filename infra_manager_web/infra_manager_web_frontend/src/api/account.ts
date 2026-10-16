// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：帳號 API（開發文件 spec_im_acct_insp.md 第 4.2 節）；查詢條件空值不送
//           變更單 #1：S1 新增 getLookupAccount（GET /accounts/lookup/{id}，異動表單帶入用）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（開發文件 spec_im_acct_insp_v2.0.md 第 6.4 節）：/accounts 回 { items, stateCounts, catgCounts, expiringCount }、
//           加 expiring 條件；lookup 改為 acctName 完整精確查詢；lookup/{id} 帶 acctName 參數
// ============================================================
import http from './http'
import type {
  Account,
  AccountHist,
  AccountListResult,
  AccountLookup,
  AccountLookupDetail,
  AccountQuery
} from '../types/account'

/** 去掉空值，避免送出 keyword= 這類空參數 */
function compact(q: AccountQuery): AccountQuery {
  const out: AccountQuery = {}
  for (const [k, v] of Object.entries(q) as [keyof AccountQuery, string | undefined][]) {
    if (v) {
      out[k] = v
    }
  }
  return out
}

/** 帳號列表：GET /accounts?keyword&catgId&stateCode&assetClassId&expiring（需 ACC_LIST_FULL） */
export function listAccounts(q: AccountQuery): Promise<AccountListResult> {
  return http.get<AccountListResult>('/accounts', { params: compact(q) }).then(r => r.data)
}

/** 帳號明細：GET /accounts/{id} */
export function getAccount(acctId: string): Promise<Account> {
  return http.get<Account>(`/accounts/${encodeURIComponent(acctId)}`).then(r => r.data)
}

/** 帳號歷史：GET /accounts/{id}/history */
export function getAccountHistory(acctId: string): Promise<AccountHist[]> {
  return http.get<AccountHist[]>(`/accounts/${encodeURIComponent(acctId)}/history`).then(r => r.data)
}

/** 我曾申請新增的帳號：GET /accounts/mine */
export function listMyAccounts(): Promise<Account[]> {
  return http.get<Account[]>('/accounts/mine').then(r => r.data)
}

/** 對既有帳號提出異動：GET /accounts/lookup?acctName（完整帳號名稱精確比對，不提供模糊查詢） */
export function lookupAccounts(acctName: string): Promise<AccountLookup[]> {
  return http.get<AccountLookup[]>('/accounts/lookup', { params: { acctName } }).then(r => r.data)
}

/**
 * 異動表單帶入：GET /accounts/lookup/{id}?acctName（需 ACC_APPLY；acctName 須與該帳號完整相符，或本人可看該帳號）
 * acctName 空值不送
 */
export function getLookupAccount(acctId: string, acctName: string): Promise<AccountLookupDetail> {
  const params = acctName ? { acctName } : {}
  return http
    .get<AccountLookupDetail>(`/accounts/lookup/${encodeURIComponent(acctId)}`, { params })
    .then(r => r.data)
}
