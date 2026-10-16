// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: 新增：帳號列表匯出 xlsx（BACKLOG 第 80 項，SA v0.5 第 8.2 節「帳號權限清單」）
//           欄位只有畫面 8 欄（使用者裁示；SA 寫「欄位依規格 6.2」但原始規格書缺件，見 BACKLOG 第 16 項）；
//           儲存格文字與畫面同一組函式（帳號名稱、所屬系統、到期日），類別帶「特權」、狀態帶「審查異常」「待複驗」「申請處理中」；
//           一律排除已刪除（SA 第 8.2 節）；檔名「帳號權限清單_YYYYMMDD.xlsx」（SA 第 8.2 節）。
//           write-excel-file 以動態 import 載入，只在按匯出時下載；其壓縮背景執行緒需殼 jar CSP 的 worker-src blob:
// ============================================================
import type { Account } from '../types/account'
import type { Codes } from '../types/common'
import { catgNameOf } from './codes'
import { dateOnly, fmtDate } from './date'
import { ACCT_STATE_LABEL, DOOR_CATG_ID, DOOR_SCOPE_LABEL, isOn, label } from './labels'

export const EXPORT_HEADERS = ['單號', '類別', '帳號名稱', '所屬系統', '使用人', '資產分類', '到期日', '狀態']
/** 欄寬（字元數），順序同 EXPORT_HEADERS */
const COLUMN_WIDTHS = [18, 18, 24, 28, 14, 10, 12, 26]

/** 帳號名稱欄：門禁卡新增尚未發卡時沒有卡號 */
export function acctNameText(a: Account): string {
  return a.acctName || (a.catgId === DOOR_CATG_ID ? '（門禁卡，待發卡）' : '—')
}

/** 所屬系統欄：門禁卡顯示門禁權限範圍 */
export function sysText(a: Account): string {
  return a.catgId === DOOR_CATG_ID ? label(DOOR_SCOPE_LABEL, a.doorScopeCode) : a.sysName || '—'
}

/** 到期日欄 */
export function expiryText(a: Account): string {
  return a.expiryTypeCode === 'DATE' ? dateOnly(a.expiryDate) || '—' : '永久'
}

/** 類別欄：畫面上的「特權」標籤併入同一格 */
function catgCell(codes: Codes | null, a: Account): string {
  const name = catgNameOf(codes, a.catgId, a.catgName)
  return isOn(a.isPriv) ? `${name}、特權` : name
}

/** 狀態欄：畫面上的「審查異常」「待複驗」標籤與「申請處理中」提示併入同一格（待複驗與處理中擇一，同畫面） */
function stateCell(a: Account): string {
  const parts = [label(ACCT_STATE_LABEL, a.acctStateCode)]
  if (isOn(a.isReviewFlag)) parts.push('審查異常')
  if (a.pendingVerify) parts.push('待複驗')
  else if (a.inFlight) parts.push('申請處理中')
  return parts.join('、')
}

/** 匯出範圍：目前篩選結果，一律排除已刪除 */
export function exportableRows(rows: Account[]): Account[] {
  return rows.filter(a => a.acctStateCode !== 'DELETED')
}

/** 標題列＋資料列，皆為文字（不轉成數字或日期，避免 Excel 改寫單號與日期格式） */
export function buildExportRows(codes: Codes | null, rows: Account[]): string[][] {
  return [
    EXPORT_HEADERS,
    ...exportableRows(rows).map(a => [
      a.acctId,
      catgCell(codes, a),
      acctNameText(a),
      sysText(a),
      a.holderName || '—',
      a.assetClassId || '未分類',
      expiryText(a),
      stateCell(a)
    ])
  ]
}

/** 帳號權限清單_YYYYMMDD.xlsx（本機日期） */
export function exportFileName(now: Date): string {
  return `帳號權限清單_${fmtDate(now).replace(/-/g, '')}.xlsx`
}

/** 產生 xlsx 並觸發下載；標題列粗體並凍結 */
export async function exportAccounts(codes: Codes | null, rows: Account[], now: Date = new Date()): Promise<void> {
  const { default: writeXlsxFile } = await import('write-excel-file/browser')
  const [head, ...body] = buildExportRows(codes, rows)
  const data = [head.map(value => ({ value, fontWeight: 'bold' as const })), ...body]
  await writeXlsxFile(data, {
    sheet: '帳號權限清單',
    columns: COLUMN_WIDTHS.map(width => ({ width })),
    stickyRowsCount: 1
  }).toFile(exportFileName(now))
}
