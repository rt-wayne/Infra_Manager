// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：權限碼（開發文件 spec_im_acct_insp_v2.0.md 第 2.3 節；Table 規劃 v0.2 第 6.2 節）
//           前端只用 /me 的 permissions[] 決定導覽、路由與按鈕顯示；後端一律自己再驗。程式不判斷角色名稱
// ============================================================

/** 畫面會用到的第一階段權限碼 */
export type PermCode =
  | 'ACC_APPLY'
  | 'ACC_SELF_SERVICE'
  | 'ACC_LIST_FULL'
  | 'ACC_EXPIRY_ALL'
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

/** permissions[] 是否含指定權限碼 */
export function hasPerm(permissions: readonly string[] | null | undefined, code: PermCode): boolean {
  return Array.isArray(permissions) && permissions.includes(code)
}
