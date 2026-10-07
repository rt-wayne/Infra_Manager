# 第 5 項：S5 範本（施工計畫）

> 衝刺期規則見 `CLAUDE.md`「基本功能衝刺期」：回合內只跑單元測試＋commit＋push；整合測試（`./mvnw verify`）、
> 手動驗證、`code-reviewer` 都在階段結束一次做。

## 已拍板（2026-10-07 使用者「全部依建議」）
- **① 第 30 項＝A**：舊系統已知漏洞一律修正，逐項列 CHANGELOG 並公告（S4～S10 已實際走 A：撞號、免登入、open redirect、執行角色）
- **② 第 43 項＝A**：範本只有**建立者本人與 admin** 可修改、刪除；任何登入者可新增、檢視、套用
- **③ 舊範本建立者**：舊 3 份範本都沒有 `createdBy`、`IM_TMPL.OWNER_USER_ID` 為 NOT NULL——正式匯入時掛給使用者指定的一位 admin 工號（屆時索取）；S5 測試種子掛 wayne（T0001）

## 範圍與假設（Claude 自行決定，使用者可推翻）
- S5 只做範本；表單選項的後台寫入是第 74 項，排 S14
- 刪除為軟刪除（`STATUS=0`，V1 已定）；列表與檢視只看 `STATUS=1`
- 範本表單內容（`FORM_JSON`）用新系統草稿請求的子集（欄位名同 `AppDraftRequest`），不收申請人聯絡資料、預定開始／結束時間（同舊系統範本只存預估工時）；寫入前以 `AppDraftValidator` 同一套規則檢查並正規化（標題可空、優先等級必填）
- 範本 ID 由伺服器產生 `tpl_<毫秒>_<4 位亂數 hex>`（舊系統可自訂 ID，新系統不開放）；匯入時沿用舊 id
- 不做樂觀鎖（`IM_TMPL` 沒有版本欄，同舊系統後寫者為準）；只有建立者與 admin 能改，撞寫機率低
- 範本名稱不檢查重複（DDL 沒有唯一鍵，舊系統也不檢查）
- 套用次數在「用範本建出的草稿存檔成功」時 +1（同舊系統在建單時累計，不在按下套用時）

## API
| 方法 | 路徑 | 說明 | 權限 |
|------|------|------|------|
| GET | /api/templates | 列表（名稱排序）：id、名稱、優先等級、建立者、套用次數、最後套用時間與人、更新時間、`canEdit` | 登入 |
| GET | /api/templates/{id} | 單筆含 `form` | 登入 |
| POST | /api/templates | `{tmplName, form}` → 201 `{tmplId}` | 登入 |
| PUT | /api/templates/{id} | `{tmplName, form}` → 200 `{tmplId}` | 建立者或 admin |
| DELETE | /api/templates/{id} | 軟刪除 → 204 | 建立者或 admin |

## 回合
| 回合 | 內容 | 狀態 |
|------|------|------|
| R1 | 後端 CRUD：model、`TemplateDao`、`TemplateService`、`TemplateController`；`AppDraftValidator` 加「標題可空」入口；單元測試＋`TemplateServiceIT`（經服務層打真實 DB，階段末跑） | 完成 |
| R2 | 前端：`types/template.ts`、`api/templates.ts`、`TemplateListView`、`TemplateEditView`（表單欄位沿用 AppFormView 的寫法）、路由與首頁入口；型別檢查＋build＋spec | 未開始 |
| R3 | 套用範本：AppFormView 新增時可選範本帶入（已停用選項自動拿掉，同第 99 項 ②），`POST /api/apps` 帶 `templateId` 時同交易累計 `USE_CNT`／`LAST_USE_*`；測試種子 `db/oracle/sample/S5_sample_templates.sql`（3 份，掛 T0001） | 未開始 |
| 階段末 | `./mvnw verify`、`code-reviewer`、補 PRD／CHANGELOG（第 30、43 項結案） | 未開始 |
