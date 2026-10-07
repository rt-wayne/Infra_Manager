// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：範本型別（S5 R2），對應後端 model/template 的 TemplateListItem、TemplateDetail、TemplateForm、TemplateRequest
//           - TemplateForm 是申請單草稿本文的子集：不含申請人聯絡資料、預定開始／結束時間（只存預估工時，同舊系統）
//           - 後端 Jackson 會把 null 欄位照送，所以每個欄位都可能是 null；正規化後清單欄位是空陣列
// ============================================================
import type { AppDraftEquipment } from './app'

export interface TemplateForm {
  title: string | null
  prioCode: string | null
  selfExec: boolean | null
  supplierExec: boolean | null
  workModeCode: string | null
  remoteMethod: string | null
  supplier: { name: string | null; contact: string | null; tel: string | null; headCount: number | null } | null
  workSubject: string | null
  impactDesc: string | null
  workDetail: string | null
  riskDesc: string | null
  rollbackPlan: string | null
  categoryItemIds: number[] | null
  categoryOthers: { formOptionId: number; text: string | null }[] | null
  reasonIds: number[] | null
  otherReason: string | null
  scopeIds: number[] | null
  equipments: { [K in keyof AppDraftEquipment]: string | null }[] | null
  planSteps: (string | null)[] | null
  schedule: { estHours: number | null } | null
  location: { omitReason: string | null } | null
}

/** GET /api/templates 的一列 */
export interface TemplateListItem {
  tmplId: string
  tmplName: string
  prioCode: string | null
  prioName: string | null
  prioColor: string | null
  ownerName: string | null
  useCnt: number
  lastUsedAt: string | null
  lastUsedByName: string | null
  updatedAt: string | null
  /** 建立者本人或 admin 才是 true */
  canEdit: boolean
}

/** GET /api/templates/{id} */
export interface TemplateDetail {
  tmplId: string
  tmplName: string
  /** FORM_JSON 解析失敗時後端回 null */
  form: TemplateForm | null
  ownerName: string | null
  useCnt: number
  lastUsedAt: string | null
  lastUsedByName: string | null
  createdAt: string | null
  updatedAt: string | null
  canEdit: boolean
}

/** POST／PUT 本文 */
export interface TemplateRequest {
  tmplName: string
  form: TemplateForm
}

export interface TemplateSaved {
  tmplId: string
}
