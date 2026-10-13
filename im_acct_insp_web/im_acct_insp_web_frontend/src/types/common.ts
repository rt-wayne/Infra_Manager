// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：身分、代碼表、首頁、待辦的 API 型別（開發文件 spec_im_acct_insp.md 第 4.1 節）
//           欄位名照後端 camelCase；旗標欄（isDirect、isShow 等）對應 NUMBER(1)，以 number 表示
//           變更單 #1：M2 Todo 的 catgId、isPriv 改為必填（後端 /todos 一律回傳）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（開發文件 spec_im_acct_insp_v2.0.md 第 6.2、6.3 節）：
//           Me 改為實際使用者／有效身分／permissions[]；新增 LoginResult；UserInfo 去掉 execCatgIds；
//           Category 加 enabled／suggestExpiry；AssetClass 改 reviewTrigger／selectable；Stage 改 permId；
//           ExpiringItem 加 catgId／acctStateCode；Todo 加 source／title／versionNo／verifyRejectCnt／verifyRejectReason 等
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-11
// 變更說明: BACKLOG 第 40 項：Me 加 deptName（有效身分的部門名稱，新增申請帶入申請單位）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S8：HomeStats 加 emgUndecided、emgReturned（首頁緊急存取兩張卡）
// ============================================================

/** 後端錯誤回應本文：{ message } */
export interface ApiErrorBody {
  message?: string
}

/** GET /users 一列：切換身分名單（STATUS＝1、不含自己） */
export interface UserInfo {
  userId: string
  userName: string
  roleIds: string[]
}

/** GET /me：目前身分（切換中 userId／userName 為代理身分，realUserId／realUserName 為實際登入者） */
export interface Me {
  userId: string
  userName: string
  realUserId: string
  realUserName: string
  impersonating: boolean
  /** 實際使用者有 SYS_IMPERSONATE 且未切換中 */
  canImpersonate: boolean
  /** 有效身分的權限碼（含 GENERAL）；前端只用它決定導覽與按鈕顯示 */
  permissions: string[]
  roleIds: string[]
  /** 有效身分是哪些帳號類別的執行人 */
  execCatgIds: string[]
  /** 使用者資料的分機（「同申請人」最後的後備來源；/me 未回傳時為 undefined） */
  telNo?: string | null
  /** 使用者資料的 Email（門禁卡「同申請人」的後備來源；/me 未回傳時為 undefined） */
  email?: string | null
  /** 使用者資料的部門名稱（新增申請帶入申請單位；/me 未回傳時為 undefined） */
  deptName?: string | null
}

/** POST /auth/login 回傳 */
export interface LoginResult {
  token: string
  me: Me
}

/** 帳號類別（IM_ACCT_CATG） */
export interface Category {
  catgId: string
  catgName: string
  catgDescr: string | null
  /** 開放新增申請（IS_ENABLED） */
  enabled: boolean
  /** 表單顯示「建議指定明確到期日」（IS_SUGGEST_EXPIRY） */
  suggestExpiry: boolean
  sortSeq: number
}

/** 資產分類碼（IM_ASSET_CLASS） */
export interface AssetClass {
  assetClassId: string
  assetTypeCode: string
  assetClassName: string
  /** 納入定期審查條件（IS_REVIEW_TRIGGER） */
  reviewTrigger: boolean
  /** 申請表單可選（IS_SELECTABLE） */
  selectable: boolean
  ctrlDescr: string | null
  sortSeq: number
}

/** 簽核關卡（IM_ACCT_APPR_STAGE）；stageTypeCode：SUBMIT／APPR／EXEC／VERIFY */
export interface Stage {
  stageId: number
  stageName: string
  stageTypeCode: string
  permId: string | null
}

/** GET /codes */
export interface Codes {
  categories: Category[]
  assetClasses: AssetClass[]
  stages: Stage[]
}

/** GET /home/stats */
export interface HomeStats {
  acctTotal: number
  acctReviewing: number
  acctExpiring: number
  acctPriv: number
  inspThisMonth: number
  inspExcptThisMonth: number
  /** 緊急存取待補核准（尚未處理，含逾期；看全部，不依登入者過濾） */
  emgUndecided: number
  /** 緊急存取異常待追蹤（已退回、尚未結案） */
  emgReturned: number
}

/** GET /home/expiring 一列（鈴鐺；含已逾期，daysLeft 已過為負數） */
export interface ExpiringItem {
  acctId: string
  acctName: string | null
  catgId: string
  catgName: string
  acctStateCode: string
  expiryDate: string
  daysLeft: number
}

/** 待辦可做的動作 */
export type ActionKind = 'APPROVE' | 'EXEC' | 'VERIFY'

/** GET /todos 一列 */
export interface Todo {
  /** 來源（第一階段固定 ACCT_REQ） */
  source: string
  reqId: string
  title: string
  reqTypeCode: string
  /** 帳號類別代碼；DOOR＝機房門禁卡（決定回報執行時卡號是否必填） */
  catgId: string
  catgName: string
  acctName: string | null
  /** 是否特權帳號（NUMBER(1)） */
  isPriv: number
  applyBy: string
  applyByName: string
  stageId: number
  stageName: string
  submitDate: string
  actionKind: ActionKind
  /** 樂觀鎖版本號，動作時帶回 */
  versionNo: number
  verifyRejectCnt: number
  verifyRejectReason: string | null
}
