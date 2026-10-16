// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：權限碼（開發文件 spec_im_acct_insp_v2.0.md 第 2.3 節；Table 規劃 v0.2 第 6.2 節）
//           前端只用 /me 的 permissions[] 決定導覽、路由與按鈕顯示；後端一律自己再驗。程式不判斷角色名稱
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S3：加緊急存取 EMG_APPLY（申請頁與「申請」鈕）、EMG_VIEW_ALL（清單提示是否只看自己）
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S5：加 EMG_DECIDE（詳情彈窗的補核准／退回鈕）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 80 項：加 ACC_EXPORT（帳號列表「匯出 Excel」鈕）
// ============================================================

/** 畫面會用到的權限碼（第一階段＋緊急存取） */
export type PermCode =
  | 'ACC_APPLY'
  | 'ACC_SELF_SERVICE'
  | 'ACC_LIST_FULL'
  | 'ACC_EXPIRY_ALL'
  | 'ACC_EXPORT'
  | 'APPR_G2'
  | 'APPR_G3'
  | 'APPR_G4'
  | 'APPR_G5'
  | 'VERIFY_G7'
  | 'INSP_VIEW'
  | 'INSP_CREATE'
  | 'INSP_RESOLVE'
  | 'SYS_IMPERSONATE'
  | 'SYS_JOB_RUN'
  | 'EMG_APPLY'
  | 'EMG_VIEW_ALL'
  | 'EMG_DECIDE'

/** permissions[] 是否含指定權限碼 */
export function hasPerm(permissions: readonly string[] | null | undefined, code: PermCode): boolean {
  return Array.isArray(permissions) && permissions.includes(code)
}
