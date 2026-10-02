# Infra Manager（Java 版）

> 遠端倉庫：https://github.com/rt-wayne/Infra_Manager.git（預設分支 `main`）
> 本檔描述**已拍板的目標系統**。標「待定，見 BACKLOG.md 第 N 項」的部分尚未裁示，以 BACKLOG 為準。

## 專案概述

機房設備異動申請系統：把機房設備異動（上下架、搬遷、維護等）的紙本申請與簽核流程數位化。申請人線上填寫異動申請單（含設備、機櫃位置、施工計畫、風險與回退方案），系統依簽核流程逐關送審，核准後由機房端執行並填寫檢核表，最後由治理單位審查結案；全程寄信通知，並可用 AI 預先審查申請單內容。使用者為公司內 IT 各單位申請人、各級主管、機房管理員與治理審查人員。

本專案是 Node 版（`D:\ai\Infra_Manager`，Express + EJS + JSON 檔儲存）的重寫版，改為 Spring Boot REST API + Vue 3 SPA + Oracle 19c。舊 Node 專案完全不動，切換後保留唯讀。

## 背景與動機

- 舊系統以 JSON 檔儲存，已出現實際資料問題：申請單編號用「當天檔案數 + 1」產生，刪單後新單會撞號並**覆蓋既有單**；時間欄位存台灣時間卻標 `Z`（「假 UTC」，每筆多 8 小時，信件再加 8 小時）；存取紀錄高併發時遺失。改用關聯式資料庫（Oracle 19c）與 DB 計數器根治
- 公司未來還有兩個模組（帳號權限管理、機房巡檢）要做，需要可共用的簽核引擎與待辦資料模型。這兩個模組**本次不做**，但簽核引擎（`workflow_def`／`approval_*` 系列表，`doc_type` 區分單據類型、`mode` 預留事後補核准、`approval_step_item` 預留逐項簽核）與附件表（`owner_type` 區分擁有者）要留得住擴充
- 帳號權限管理模組管的是「其他系統的帳號」（主機、資料庫、應用系統、服務、網路設備、廠商、憑證金鑰、門禁卡八類），不是本系統的 RBAC，所以本系統的角色只需預留「簽核角色池可擴充」
- 巢狀資料採「混合式」放法：常查欄位攤平成欄位、簽核鏈／版次／附件／AI 對話拆子表、AI 報告與範本表單留 JSON——兼顧查詢效能與遷移成本

---

## 第一區塊：功能說明（所有人適用）

### 功能清單

- **登入與帳號**：用帳號密碼登入（登入方式待定，見 BACKLOG.md 第 16 項）；第一次用預設密碼登入會被要求改密碼；改密碼規則為至少 6 字、不得等於帳號，改完後其他裝置的「記住我」失效。「記住我」是否保留舊 token 待定，見 BACKLOG.md 第 28 項
- **首頁**：看申請單統計與「我的待辦」（等我簽核的單）
- **申請單列表**：預設顯示近 90 天，可依狀態、優先等級、來源、只看我的、關鍵字、日期區間篩選；待我簽核的單排在最上面；顯示 AI 摘要與 AI 費用
- **新增／編輯申請單**：填寫基本資料、異動類別、原因、影響範圍、設備清單、機櫃 U 位、施工步驟、排程、風險評估與回退方案；可套用範本；可上傳附件（拖放、貼上截圖、上傳前預檢）；先存成草稿
- **送審與撤回**：申請人送審後依簽核流程逐關進行；AI 審查啟用時，送審前必須有一份與目前內容相符的 AI 報告；還沒有人簽之前可撤回成草稿
- **簽核**：當前關卡的候選人可同意或退件（退件必填意見），可附檔
- **退件補件**：被退件的單由申請人修改後補件，產生新版次並直接重新進入審核；舊版次的簽核紀錄與表單內容都保留可查
- **執行**：核准後由機房端填寫執行檢核表（11 項）與實際執行紀錄（實際起訖、結果、異常、後續事項）；填完進入待審查；執行端可退回申請
- **治理審查**：治理人員審查執行結果，通過即結案，退回則回到退件狀態
- **刪除**：申請人（符合條件時）或 admin 可刪除申請單，需輸入單號確認；刪除為軟刪除，編號不再被重用
- **AI 審查**（是否保留待定，見 BACKLOG.md 第 17 項）：對申請單做 AI 預審，內容沒變就略過、可強制重審；AI 執行在背景進行，畫面輪詢結果；可對報告追問；admin 可寄出報告
- **範本**：範本列表、檢視、新增、修改、刪除（修改／刪除的權限範圍待定，見 `BACKLOG.md` 第 30 項）
- **統計**：依日／週／月分桶統計申請單
- **機櫃盤點**：從 Impact 系統取得機櫃與設備資料（快取，背景刷新），在新增申請單時用機櫃 U 位視覺化選擇器選位置並帶入設備
- **後台管理（admin）**：使用者管理（新增、修改、停用；刪除前檢查是否被簽核流程引用）、簽核流程管理、外寄信件紀錄與失敗重寄、測試信、系統設定（workflowPolicy、上傳限制、排除記錄的 IP）、表單設定檢視、存取紀錄查詢
- **權限範圍**：未登入可看哪些頁面待定，見 BACKLOG.md 第 26 項；舊系統的已知權限漏洞照搬或修正待定，見 BACKLOG.md 第 30 項

### 使用者流程

1. 申請人登入 → 新增申請單（可套用範本、選機櫃位置、上傳附件）→ 存成草稿（`draft`）
2. （AI 審查啟用時）執行 AI 審查，取得與目前內容相符的報告
3. 送審 → 進入審核（`in_review`），系統依該單的簽核流程（`full`、`p2_high`、`p1_emergency`）逐關通知候選人
4. 每一關候選人同意 → 進入下一關；任一關退件 → `rejected`，申請人修改後補件 → 新版次重新審核
5. 全部關卡同意 → 核准（`approved`）→ 執行中（`in_execution`）
6. 機房端填寫檢核表與實際執行紀錄 → 待審查（`pending_review`）；執行端也可退回 → `rejected`
7. 治理人員審查：通過 → 結案（`executed`）；退回 → `rejected`
8. 各關卡與結案時系統寄信通知相關人員

### 畫面說明

Vue 3 SPA，共 20 頁：

| 頁面 | 用途 |
|---|---|
| LoginPage | 登入 |
| ChangePasswordPage | 改密碼（預設密碼登入後強制導向） |
| HomePage | 首頁統計、我的待辦 |
| AppListPage | 申請單列表與篩選 |
| AppFormPage | 新增／編輯草稿／補件共用表單，內含機櫃 U 位選擇器、設備可編輯表格、附件上傳 |
| AppViewPage | 檢視申請單；送審、撤回、簽核、刪除、AI 審查與追問、寄出報告 |
| ExecutePage | 執行檢核表、實際紀錄、執行端退回 |
| ReviewPage | 治理審查 |
| TemplateListPage／TemplateEditPage | 範本列表與編輯 |
| StatsPage | 統計 |
| AccessLogPage | 存取紀錄（admin） |
| AdminUsersPage | 使用者管理 |
| AdminWorkflowsPage | 簽核流程管理（關卡可拖曳排序） |
| AdminMailPage／MailDetailPage／MailTestPage | 外寄信件紀錄、信件內容、測試信 |
| AdminSettingsPage | 系統設定 |
| AdminFormSchemaPage | 表單設定（唯讀） |
| 403／錯誤頁 | 無權限與錯誤提示 |

畫面上的按鈕是否顯示，依後端在申請單 DTO 內回傳的 9 個 `canX` 權限旗標決定，前端不自行判斷權限。

---

## 第二區塊：技術規格（工程師適用）

### 技術架構

#### 技術選型
| 層 | 技術 |
|---|---|
| 後端 | Java 21 或 25 LTS + Spring Boot 最新 GA（確切版本 S1 動工當天確認，見 BACKLOG.md 第 39 項） |
| 前端 | Vue 3 + Vite + Vue Router + Pinia；UI 元件庫待定，見 BACKLOG.md 第 19 項 |
| 資料庫 | Oracle 19c；schema 由 Flyway 社群版管理（社群版支援 19c） |
| JDBC 驅動 | `com.oracle.database.jdbc:ojdbc11`（引入前過套件審查） |
| 建置工具 | Maven 或 Gradle，S1 決定 |
| 單元測試 DB | H2 Oracle 相容模式；開發期整合測試連公司 19c 測試 schema（本機沒有 Docker，不用 Testcontainers）。此做法 S1 開工前正式裁示，見 BACKLOG.md 第 1 項 |

#### Oracle 19c 連動規則（全系統適用）
- 空字串 `''` 在 Oracle 等於 NULL → 規則為「DB 存 NULL、API 回 `""`」，在 JPA 轉換層統一處理
- JSON 欄位用 `CLOB` + `CHECK (col IS JSON)`（19c 沒有原生 JSON 型別）
- 布林用 `NUMBER(1)`（23ai 以前沒有 boolean）
- Oracle 字串比對分大小寫：`login_id` 一律存小寫再比對
- 時間欄位型別為 `TIMESTAMP WITH TIME ZONE`；畫面一律以台灣時區顯示

#### Repo 結構
單一 repo（`D:\ai\Infra_Manager_Java`）：
```
backend/    Spring Boot REST API
frontend/   Vue 3 SPA
docs/plan/  規劃文件（歷史文件，不再更新）
```

#### 後端分層
- 套件依功能分：`identity`、`approval`（共用簽核引擎）、`changerequest`、`ai`、`mail`、`inventory`、`audit`
- 每個套件分四層：controller（DTO）→ service（transaction 邊界）→ domain（純邏輯，不依賴 Spring）→ repository（JPA）
- **簽核引擎**：`ApprovalChainBuilder` 建立簽核鏈，候選人在建單時固化寫入 `approval_step_candidate`；`DecisionPolicy` 處理同意／退件判定；補件時關閉舊 `approval_instance`、開新 instance、寫入 `cr_version`
- **Transaction**：一次簽核一個 transaction——以 `row_version` 樂觀鎖鎖定申請單（衝突回 409）→ 更新 step → 更新申請單狀態 → 插入 `mail_outbox` 與 `cr_event` → commit；寄信由 outbox worker 在 commit 後處理
- **認證**：server-side session（HttpOnly cookie）+ CSRF token；多機部署時接 Spring Session JDBC；不用 JWT。登入方式（DB 帳密或 AD/LDAP）待定，見 BACKLOG.md 第 16 項
- **授權**：`user_role` 轉成 `ROLE_*` authority 管 URL 層；與資料相關的判斷用 `@PreAuthorize("@crAuthz.canDecide(#id)")`；9 個 `canX` 旗標由後端算進 DTO
- **密碼相容**：舊格式 `scrypt$saltHex$hashHex`（N=16384、r=8、p=1、keylen=64），以自寫 encoder（BouncyCastle `SCrypt.generate`）掛在 `DelegatingPasswordEncoder`，登入成功時升級成新格式
- **AI 審查**（是否保留待定，見 BACKLOG.md 第 17 項；金鑰存放待定，見 BACKLOG.md 第 18 項）：Anthropic Java SDK，使用 OutputConfig（structured output）／tool use／thinking；Bedrock mantle 端點拒絕 `output_config.format`，走 bedrock 通道一律落到 tool 模式；aws 通道需自訂 baseUrl + `anthropic-workspace-id` header（未驗證，S11 先做概念驗證）；報告是否過期以「key 排序 JSON + SHA-256」的申請單快照 hash 判斷；retry 用 SDK 內建 + 退避 1s／3s；遇 refusal 用 fallbackModel 重送並記 `fell_back_from`；追問的輸入為快照、報告、最近 20 則對話、新問題，max_tokens 16000；審查為非同步（回 202，前端輪詢）；提示詞與 REVIEW_SCHEMA 放 `resources`
- **存取紀錄**：Servlet Filter 寫 DB（有上限佇列 + 批次寫入，定期清除）；loopback 改記 LAN IP；`excludeIps` 內的 IP 不記錄
- **信件**：JavaMailSender + `mail_outbox`（queued／sent／failed、重試次數）；所有信件內容（舊系統五種樣板 + 三種 inline HTML）改用 Thymeleaf 或 Mustache 樣板
- **附件**：下載一律經 controller 檢查權限；檔名 UTF-8；上傳限制（`uploadMaxFiles`、`uploadMaxMB`）每次請求讀 `site_setting`。實體存放方式待定，見 BACKLOG.md 第 20 項
- **機櫃盤點快取**：Caffeine + DB 快取列（`rack_inventory_cache`）；`@Scheduled` 背景刷新（stale-while-revalidate）；單一執行中旗標避免重複刷新；Impact 斷線時回舊快取
- **我的待辦數**：一條 SQL，靠 `approval_step_candidate` 的 IX(user_id)

#### 前端架構
- Vue 3 + Vite + Vue Router + Pinia；UI 元件庫待定，見 BACKLOG.md 第 19 項
- 互動元件：
  - DateTimePicker：固定台灣時區
  - 附件上傳：拖放、貼上截圖（自寫 paste handler）、上傳前預檢
  - **機櫃 U 位視覺化選擇器**：自寫元件，是前端最大工作項
  - 設備清單：可編輯表格（動態增刪列）
  - 簽核流程關卡排序：拖曳排序（SortableJS 或 vuedraggable，引入前審查）
  - 統計圖表：做法待 S14 前確認舊系統是否純 CSS 圖，見 BACKLOG.md 第 38 項
- 前端部署方式（打包進 jar 或 IIS 反向代理分離）待定，見 BACKLOG.md 第 21 項

#### 資料遷移（從舊 Node 系統）
- 工具：Java 匯入器（Spring Boot `import` profile + CommandLineRunner + JPA，可重跑、可測試）。遷移工具最終選擇待定，見 BACKLOG.md 第 29 項；三代 AI 報告（v1／v2／v3）的轉換方式待定，見 BACKLOG.md 第 24 項
- 資料量：applications 93 張 HIST + 263 張 IM（其中 162 張有 AI 報告）、已刪除 12 張、使用者約 22 人、範本 3 份
- 轉換規則：
  - 假 UTC 時間的處理方式待定，見 BACKLOG.md 第 25 項
  - `''` → NULL，依「Oracle 19c 連動規則」
  - `login_id` 正規化成小寫（舊資料如 `Alan`／`gary` 大小寫混用）；舊字串 ID 對應成新的 `app_user.id`
  - 簽核候選人 `candidateIds` 轉成 user FK，並存 name／email 快照
  - AI 報告重算 `snapshot_hash`：對 `aiReview.appSnapshot` 與目前申請單都用「key 排序 JSON + SHA-256」重算（舊 `computeAppHash` 是 `sha1(JSON.stringify(...))`，依賴 JS key 插入順序，Java 算不出同值）；舊值保留在 `legacy_sha1`
  - 已刪除目錄匯成軟刪除；IM20260918-003 撞號的那張已刪除單另編新號
  - 舊版次快照沒有表單內容，`cr_version.form_snapshot` 匯入為 NULL
  - `workflows.json` 關卡 key 與名稱錯位（如 key `dept_manager` 名稱卻是「機房管理員」），遷移以 name／role 為準
  - 附件以 `public/uploads` 為準（`Docs/` 與 `public/uploads/HIST-*` 約 93 份重複檔不重複匯入）
- 對帳：
  - 筆數依狀態分組一致（IM 263、HIST 93、deleted 12）
  - 「DB 匯出回舊 JSON」工具逐檔 diff（已知轉換列入白名單）
  - 附件數／大小／sha256 一致
  - 人工抽 5 張：IM20260826-005、一張 HIST，以及從非終結狀態 25 張中抽（rejected 18、in_review 3、in_execution 1、pending_review 3）

#### 部署與維運
- 開發期以 jar + bat 啟動（`start-new.bat`，port 3201）；正式環境啟動方式待定，見 BACKLOG.md 第 22 項。不使用 Docker（Windows Server 門檻高）
- 設定分三處：不變的放 `application.yml`；secret（session secret、機櫃 API 金鑰、SMTP 認證、AI 金鑰、Oracle 連線）放環境變數（AI 金鑰存放處待定，見 BACKLOG.md 第 18 項）；畫面上可調的放 `site_setting`
- 切換：新系統以 3201 port 上 UAT，正式切換後改用 3200，舊系統保留唯讀；切換策略待定，見 BACKLOG.md 第 27 項

### 資料結構

型別以 Oracle 寫法：`VARCHAR2`、`NUMBER`、`TIMESTAMP WITH TIME ZONE`、`CLOB`（JSON 欄位加 `IS JSON`）。PK 主鍵、FK 外鍵、IX 索引。

#### 身分
- `app_user`：id NUMBER PK（SEQUENCE）；login_id VARCHAR2(64) UNIQUE（存小寫）；name、email、title、department、phone；active NUMBER(1)；password_hash VARCHAR2(255)；password_is_default NUMBER(1)；password_changed_at
- `role`：code VARCHAR2(32) PK；name。初始 6 個：admin、it_manager、dept_manager、idc_admin、governance、infra
- `user_role`：(user_id FK, role_code FK) PK
- `remember_token`：id PK；user_id FK IX；token_hash；created_at；last_used_at；expires_at；user_agent（若改用 Spring remember-me 則換成 `persistent_logins`；remember-me 做法待定，見 BACKLOG.md 第 28 項）

#### 共用簽核引擎（給未來模組重用）
- `workflow_def`：id VARCHAR2 PK（沿用 full、p2_high、p1_emergency）；name；doc_type（CR、ACCOUNT、INSPECTION）；active
- `workflow_step_def`：id PK；workflow_id FK；seq；step_key；name；approver_type（USER／ROLE）；approver_user_id FK 可空；approver_role FK 可空；notify_only；allow_delegate；mode（SEQUENTIAL，預留 POST_HOC）
- `approval_instance`：id PK；doc_type；doc_id VARCHAR2；doc_version NUMBER；workflow_id；status；started_at；closed_at。**UNIQUE(doc_type, doc_id, doc_version)**
- `approval_step`：id PK；instance_id FK IX；seq；step_key；name；notify_only；status；decided_at；decided_by FK；comment
- `approval_step_candidate`（角色池）：(step_id, user_id) PK；name_snapshot；email_snapshot。**IX(user_id)**（「我的待辦」一條查詢）
- `approval_step_item`（預留逐項簽核）：step_id、item_ref、decision、comment。目前只建表

#### 申請單（CR）
- `change_request`：id VARCHAR2(20) PK（如 IM20260826-005）；title；priority；workflow_id；applicant_id FK IX；status IX；source（online／imported）；current_version；row_version（樂觀鎖）；created_at IX；updated_at；deleted_at、deleted_by、delete_reason、delete_via_role
  - basic 攤平：apply_date、department、applicant_name_snapshot、phone、email、executor_self、executor_vendor、work_mode、remote_method、vendor、vendor_contact、vendor_phone、vendor_headcount
  - work 攤平：subject、impact_desc、work_detail CLOB、risk_assessment CLOB、rollback_plan CLOB、reason_other、schedule_start、schedule_end、estimated_hours
  - location 攤平：loc_source、area、rack、u_position、site_id、rack_id、u_start、u_end、omit_reason
- `cr_category`：(cr_id, category_key, option_text) PK
- `cr_category_other`：(cr_id, category_key) PK；text
- `cr_reason`、`cr_impact_scope`：(cr_id, value) PK
- `cr_equipment`：id PK；cr_id FK；seq；name；asset_no IX；model；serial_no IX；purpose；mgmt_ip
- `cr_plan_step`：(cr_id, seq) PK；text（4 筆）
- `cr_checklist_item`：(cr_id, version, seq) PK；item；done；completed_at；executor（11 筆，依舊系統 form-schema.json）
- `cr_execution`：(cr_id, version) PK；actual_start、actual_end、result、exceptions_has、exceptions_desc、followups_has、followups_desc、notes、closed_by、closed_at
- `cr_version`：(cr_id, version) PK；status_at_close；reason；snapshot_at；form_snapshot CLOB JSON（舊資料匯入為 NULL）
- `cr_event`：id PK；cr_id IX；version；type（SUBMIT、RECALL、EXEC_REJECT、GOV_PASS、GOV_RETURN、RESUBMIT、DELETE）；by_user；at；comment（統一記錄治理審查、審查歷程、執行退回、補件說明）
- `cr_number_seq`：(prefix, ymd) PK；last_no。以 `SELECT … FOR UPDATE` 遞增產號，刪掉的號碼不再使用

#### 附件（各模組共用）
- `attachment`：id PK；owner_type（CR、STEP、AI）；owner_id；original_name；stored_name；storage_key；size_bytes；mime；sha256；uploaded_at；uploaded_by。IX(owner_type, owner_id)

#### AI
- `ai_review`：id VARCHAR2 PK（沿用 `ar_…`）；cr_id FK IX；created_at；created_by；model；effort；mode；fell_back_from；app_version；app_status；app_snapshot CLOB JSON；snapshot_hash（key 排序 JSON + SHA-256）；legacy_sha1；result CLOB JSON；result_schema（v1／v2／v3）；input_tokens、output_tokens、cache_read_tokens、cache_write_tokens；duration_ms；stop_reason；sent_at；sent_to CLOB JSON；status（PENDING、DONE、FAILED）
- `ai_review_message`：id PK；review_id FK IX；seq；role；content CLOB；by_user；app_version_at_ask；model；用量欄位；duration_ms；fell_back_from；created_at

#### 其他
- `template`：id PK；name；form CLOB JSON；owner_id；created_*；updated_*；usage_count；last_used_at；last_used_by
- `form_option`：(group_key, option_key) PK；name、color、definition、workflow_id、sort_no。**只在表單設定選擇放 DB 時建立**；表單設定放哪待定，見 BACKLOG.md 第 31 項
- `site_setting`：key PK、value（workflowPolicy、uploadMaxFiles、uploadMaxMB、excludeIps）
- `mail_outbox`：id PK；to_json、cc_json、bcc_json；subject；html CLOB；status；attempts；error；smtp_message_id；created_at；sent_at；meta CLOB JSON
- `access_log`：存取紀錄（欄位 S14 定）
- `rack_inventory_cache`：一列，payload CLOB JSON + fetched_at

#### 版次表達
簽核流程（`approval_instance`／`approval_step`）每版一列（待辦與統計要用）；表單內容存 `cr_version.form_snapshot` JSON（只做整份比對）。

#### 申請單編號
格式 `IM{yyyymmdd}-{NNN}` 與 `HIST-` 是否沿用待定，見 BACKLOG.md 第 23 項；不論格式為何，產號一律走 `cr_number_seq`。

### API 規格

全部 REST，前綴 `/api`；寫入需 session + CSRF token。「舊系統權限（對照用）」欄是 Node 版現況，**不是新系統的權限規則**；新系統的權限待 BACKLOG.md 第 26 項（未登入可看範圍）與第 30 項（舊漏洞處理）裁示後改寫本表。請求參數與回應格式各階段實作時補上。

| 新 REST API | 功能 | 舊系統權限（對照用） | Vue 頁面 |
|---|---|---|---|
| GET /api/health | 健康檢查 | — | — |
| GET /api/dashboard | 首頁統計、我的待辦 | 公開 | HomePage |
| POST /api/auth/login | 登入、remember-me、預設密碼導向改密碼 | 公開 | LoginPage |
| POST /api/auth/password | 改密碼（≥6 字、不得等於帳號、改完撤銷 token） | 登入 | ChangePasswordPage |
| POST /api/auth/logout | 登出 | 登入 | — |
| GET /api/apps?status&priority&source&mine&q&from&to | 列表（預設 90 天、待我簽核置頂、AI 摘要與費用） | 公開 | AppListPage |
| POST /api/apps（multipart） | 建草稿、附件、選範本、套 workflowPolicy | 登入 | AppFormPage |
| GET /api/apps/{id} | 檢視，含 9 個 canX 權限旗標 | 公開 | AppViewPage |
| PUT /api/apps/{id} | 草稿編輯 | 申請人或 admin | AppFormPage |
| POST /api/apps/{id}/submit | 送審（AI 啟用時須有 hash 相符報告） | 申請人或 admin | AppViewPage |
| POST /api/apps/{id}/recall | 撤回成草稿（無人簽時） | 申請人 | AppViewPage |
| POST /api/apps/{id}/decisions | 同意／退件（退件必填意見），可附檔 | 當前關卡候選人 | AppViewPage |
| POST /api/apps/{id}/resubmit | 退件補件 → 新版次直接進審核 | 申請人 | AppFormPage |
| PUT /api/apps/{id}/execution | 執行檢核表與實際紀錄；有填結果進 pending_review | idc_admin 或申請人 | ExecutePage |
| POST /api/apps/{id}/execution/reject | 執行端退回 → rejected | idc_admin 或申請人 | ExecutePage |
| POST /api/apps/{id}/governance-review | 治理審查：pass → executed、return → rejected | governance | ReviewPage |
| DELETE /api/apps/{id} | 軟刪除，需 confirmId | admin 或申請人（條件） | AppViewPage |
| POST /api/apps/{id}/ai-reviews | AI 審查（非同步 202；hash 沒變略過，force 強制） | 申請人（draft）或 admin | AppViewPage |
| POST /api/ai-reviews/{rid}/messages | AI 報告追問 | 登入 | AppViewPage |
| POST /api/ai-reviews/{rid}/send | 寄出 AI 報告 | admin | AppViewPage |
| /api/templates（CRUD） | 範本列表、JSON、增刪改 | 列表公開；增刪改任何登入者 | TemplateListPage、TemplateEditPage |
| GET /api/stats | 日／週／月分桶統計 | 公開 | StatsPage |
| GET /api/access-logs | 存取紀錄（最後 500 筆） | admin | AccessLogPage |
| GET /api/rack-data、GET /api/rack-data/status、POST /api/rack-data/refresh | 機櫃盤點資料、快取狀態、強制刷新（是否需登入待定，見 `BACKLOG.md` 第 26 項；refresh 限 admin） | 前兩者公開；refresh admin | 機櫃選擇器 |
| /api/admin/users | 使用者 CRUD、停用（刪除前查 workflow 引用） | admin | AdminUsersPage |
| /api/admin/workflows | 簽核流程 CRUD | admin | AdminWorkflowsPage |
| /api/admin/mail | 外寄紀錄、失敗紀錄、信件內容、測試信 | admin | AdminMailPage、MailDetailPage、MailTestPage |
| PUT /api/admin/settings | 系統設定 | admin | AdminSettingsPage |
| GET /api/form-schema | 表單設定（唯讀） | admin | AdminFormSchemaPage |

### 環境設定

secret 一律放環境變數，不寫進 repo 內任何檔案；實際怎麼設定見 `SETUP.md`。

| 環境變數 | 用途 |
|---|---|
| `IM_DB_URL` | Oracle JDBC 連線字串（格式 `jdbc:oracle:thin:@//host:1521/service`） |
| `IM_DB_USER` | Oracle 帳號 |
| `IM_DB_PASSWORD` | Oracle 密碼 |
| `IM_SESSION_SECRET` | session 簽章用 secret |
| `IM_SMTP_*` | SMTP 連線與認證（細項 S8 定） |
| `IM_RACK_API_KEY` | Impact 機櫃盤點 API 金鑰 |
| AI 金鑰 | 存放方式待定，見 BACKLOG.md 第 18 項 |

本系統分批施工中，進度見 `BACKLOG.md`。
