# 既有功能改寫規劃：Spring Boot REST + Vue SPA + Oracle 19c

> 來源：2026-10-02 `architect` 子代理對舊 Node 系統（`D:\ai\Infra_Manager`）的唯讀分析，加上使用者當日拍板的決定。
> 本檔是 `PRD.md`／`BACKLOG.md` 的依據，之後規劃內容若有變更，以 `PRD.md`／`BACKLOG.md` 為準，本檔不再更新（歷史文件）。

## 0. 已拍板的決定（2026-10-02）

| 項目 | 決定 |
|---|---|
| 後端 | Spring Boot（REST API） |
| 前端 | Vue 3 SPA（前後端分離） |
| 資料庫 | **Oracle 19c** |
| 版控 | GitHub `https://github.com/rt-wayne/Infra_Manager.git`，預設分支 `main` |
| 專案位置 | `D:\ai\Infra_Manager_Java`，單一 repo，`backend/` 與 `frontend/` 兩個子目錄 |
| 舊 Node 專案 | `D:\ai\Infra_Manager` 完全不動，切換後保留唯讀 |
| 兩個新模組（帳號權限管理、機房巡檢） | **本次不做**，但簽核引擎與待辦資料模型要留得住擴充 |
| 巢狀結構放法 | **B 混合式**：常查欄位攤平、簽核鏈／版次／附件／AI 對話拆子表、AI 報告與範本表單留 JSON |

### Oracle 19c 連動規則
- 空字串 `''` 在 Oracle 等於 NULL → 全系統規則「DB 存 NULL、API 回 `""`」，在 JPA 轉換層統一處理
- JSON 欄位用 `CLOB` + `CHECK (col IS JSON)`（19c 沒有原生 JSON 型別）
- 布林用 `NUMBER(1)`（23ai 以前沒有 boolean）
- Oracle 分大小寫：`login_id` 一律存小寫再比對（舊資料 `Alan`／`gary` 混用，匯入時正規化）
- Flyway 社群版支援 19c，不需付費版
- JDBC 驅動 `com.oracle.database.jdbc:ojdbc11`（引入前過套件審查）
- 本機沒有 Docker → 不能用 Testcontainers；開發期連公司 19c 測試 schema（連線放環境變數），單元測試先用 H2 Oracle 相容模式。**此假設 S1 時正式裁示**

### 尚未拍板（2026-10-02 使用者說「晚點再決定」）
②登入方式 ③AI 審查去留 ④AI 金鑰存放 ⑤UI 元件庫 ⑥附件存放 ⑦前端部署 ⑧Windows 啟動 ⑨申請單編號 ⑩三代 AI 報告格式 ⑪時間轉換 ⑫未登入可看 ⑬切換策略 ⑭remember-me ⑮遷移工具 ⑯既有漏洞照搬或修正 ⑰表單設定放哪。細節見第 8 節。

---

## 1. 結論總覽

技術上可做。4 項擋動工、17 項待裁示、14 項既有漏洞要決定照搬或修正。

1. 【擋動工】舊專案沒有 `.git` → **已解**：新 repo 建好
2. 【擋動工】AI 通道：`config/ai.json` 是 `provider: "aws"`（Claude Platform on AWS，baseURL `aws-external-anthropic.${region}.api.aws` + `anthropic-workspace-id` header）；程式另支援 anthropic 直連、bedrock（`bedrock-mantle.${region}.api.aws/anthropic` + bearer）。要確認兩台正式機一致
3. 【擋動工】所有時間欄位是「假 UTC」（存台灣時間卻標 `Z`，每筆多 8 小時）。實證：`data/applications/IM20260826-005.json:206-211` 附件 storedName 前綴 `1787716732068` = 2026-08-26T03:58:52Z，同筆 `uploadedAt` 卻是 `11:58:52Z`。來源 `lib/time.js` 的 `twNow() = Date.now()+8h` 再 `.toISOString()`。副作用：`lib/mailer.js` fmtTwDateTime 對 Z 字串再 +8h，完成信顯示 +16h；rememberToken 效期實際 30 天又 8 小時；`migrate-timezone.js` 曾把 access-logs 與 mail 全部 +8h
4. 【擋動工】`computeAppHash`（判斷 AI 報告是否過期）= `sha1(JSON.stringify({title, priority, basic, categories, categoryOthers, work, attachmentsCount}))`，依賴 JS key 插入順序，Java 算不出同值。做法：遷移時對 aiReview.appSnapshot 與目前申請單都用「key 排序 JSON + SHA-256」重算
5. 編號 `apps.js:88-93` `IM${yyyymmdd}-${當天檔案數+1}`，刪單後撞號**覆蓋既有單**。實證：`applications-deleted/_log.json` IM20260918-003 刪後被新單重用；IM20260423 的 002、003 刪後剩 001、004、005、006。新系統改 DB 計數器
6. 版次：`lib/workflow.js` createNewVersion 只快照 {version, status, steps, execution, checklist, governanceReview, reviewHistory, executionReject, snapshotAt, reason}，沒有 basic/work；8 份有 history。新模型補 `form_snapshot`
7. 簽核引擎抽成共用模組；未來新模組需「先啟用事後補核准」與「逐項簽核」，現引擎沒有
8. Java SDK 支援 structured output；Bedrock mantle 端點拒絕 `output_config.format`，走 bedrock 一定落到 tool 模式
9. Java 可透過 JNA `Crypt32Util.cryptUnprotectData` 解 DPAPI；限制是 CurrentUser 範圍綁 Windows 帳號，WinSW 用 LocalSystem 跑就解不開
10. 不用登入就能存取：GET `/`、`/apps`、`/apps/:id`、`/templates`、`/templates/:id/json`、`/stats`、`/api/rack-data`（回全部設備 mgmtIp 與序號）、`/uploads/*`（`server.js:69` 公開 static）
11. Oracle `''`=NULL（見第 0 節規則）
12. AI 同步呼叫 timeout 5 分鐘、maxTokens 32000、retry 2；SPA 改背景工作 + 輪詢
13. 資料量：applications 93 HIST + 263 IM（162 份有 aiReviews）、deleted 12、users 約 22、templates 3
14. 【無需處理】myTasksCount（`server.js:89-107` 每請求全掃）改 DB 索引查詢自然解

---

## 2. 既有功能盤點（route → 新 API → Vue 頁面）

「公開」＝不用登入。

| 舊 route | 功能 | 權限（現況） | 新 REST API | Vue 頁面 ／ 舊寫入 |
|---|---|---|---|---|
| GET / | 首頁統計、我的待辦（含 draft） | 公開 | GET /api/dashboard | HomePage ／ 無 |
| GET/POST /login | 登入、remember-me 30 天、強制改預設密碼 | 公開 | POST /api/auth/login | LoginPage ／ users.json(rememberTokens) |
| POST 改密碼 | ≥6 字、不得等於帳號、改完撤銷 token | 登入 | POST /api/auth/password | ChangePasswordPage ／ users.json |
| GET /logout | 登出 | 登入 | POST /api/auth/logout | — |
| GET /apps | 列表、預設 90 天、6 種篩選、待我簽核置頂、AI 摘要與費用 | 公開 | GET /api/apps?status&priority&source&mine&q&from&to | AppListPage |
| GET/POST /apps/new | 建草稿、附件、選範本、套 workflowPolicy | requireUser | POST /api/apps（multipart） | AppFormPage ／ applications、templates(usage)、uploads |
| GET /apps/:id | 檢視；後端算 9 個 canX 旗標 | 公開 | GET /api/apps/{id}（含 permissions） | AppViewPage |
| POST /:id/submit | 送審；AI 啟用時須有 hash 相符報告 | 申請人或 admin | POST /api/apps/{id}/submit | AppViewPage ／ applications、mail |
| POST /:id/recall | 撤回（無人簽時）→ draft | 申請人 | POST /api/apps/{id}/recall | AppViewPage |
| POST /:id/approve | 同意／退件（退件必填意見）、可附檔 | 當前關卡候選人 | POST /api/apps/{id}/decisions | AppViewPage |
| GET/POST /:id/edit, /resubmit | 草稿編輯；退件補件 → 新版次直接進審核 | GET 申請人或 admin；POST 只限申請人 | PUT /api/apps/{id}、POST /api/apps/{id}/resubmit | AppFormPage |
| GET/POST /:id/execute | 執行檢核表與實際紀錄；有填結果進 pending_review | 畫面限 idc_admin 或申請人，**POST 沒擋** | PUT /api/apps/{id}/execution | ExecutePage |
| POST /:id/execute-reject | 執行端退回 → rejected | idc_admin 或申請人 | POST /api/apps/{id}/execution/reject | ExecutePage |
| GET/POST /:id/review | 治理審查 pass→executed、return→rejected | governance | POST /api/apps/{id}/governance-review | ReviewPage |
| POST /:id/delete | 刪除歸檔，需 confirmId | admin 或申請人（條件） | DELETE /api/apps/{id}（軟刪除） | AppViewPage ／ applications-deleted、_log.json |
| POST /:id/ai-review | AI 審查；hash 沒變略過，force 強制 | 申請人（draft）或 admin | POST /api/apps/{id}/ai-reviews（非同步 202） | AppViewPage |
| POST …/:reviewId/chat | 追問 | 任何登入者 | POST /api/ai-reviews/{rid}/messages | AppViewPage |
| POST …/:reviewId/send | 寄出報告 | admin | POST /api/ai-reviews/{rid}/send | AppViewPage |
| /templates | 範本列表、JSON、增刪改 | 列表公開；增刪改任何登入者 | /api/templates CRUD | TemplateListPage、TemplateEditPage ／ templates.json |
| /stats | 日／週／月分桶統計 | 公開 | GET /api/stats | StatsPage |
| /logs | 存取紀錄最後 500 筆 | admin | GET /api/access-logs | AccessLogPage |
| /api/rack-data, /status, /refresh | 機櫃盤點（Impact API 快取） | 前兩者公開；refresh admin | 同路徑，改需登入 | 機櫃選擇器 ／ rack-inventory-cache.json |
| /admin/users | 使用者 CRUD、停用；刪除前查 workflow 引用 | admin | /api/admin/users | AdminUsersPage ／ users.json |
| /admin/workflows | 流程 CRUD（存檔丟掉 allowDelegate） | admin | /api/admin/workflows | AdminWorkflowsPage ／ workflows.json |
| /admin/mail, /mail/view, /mail/test | 外寄紀錄、失敗紀錄、測試信 | admin | /api/admin/mail | AdminMailPage、MailDetailPage、MailTestPage ／ mail-outbox |
| /admin/settings | 只改 workflowPolicy（非原子寫入） | admin | PUT /api/admin/settings | AdminSettingsPage ／ site.json |
| /admin/form-schema | 唯讀表單設定 | admin | GET /api/form-schema | AdminFormSchemaPage |
| accessLogger 中介層 | 每請求一筆 | — | Servlet Filter | — ／ access-logs/<date>.json |

---

## 3. 資料模型（B 混合式，Oracle 19c）

型別以 Oracle 寫法：`VARCHAR2`、`NUMBER`、`TIMESTAMP WITH TIME ZONE`、`CLOB`（JSON 加 `IS JSON`）。PK 主鍵、FK 外鍵、IX 索引。

### 身分
- `app_user`：id NUMBER PK（SEQUENCE）；login_id VARCHAR2(64) UNIQUE（存小寫）；name、email、title、department、phone；active NUMBER(1)；password_hash VARCHAR2(255)；password_is_default NUMBER(1)；password_changed_at
- `role`：code VARCHAR2(32) PK；name。初始 6 個：admin、it_manager、dept_manager、idc_admin、governance、infra
- `user_role`：(user_id FK, role_code FK) PK
- `remember_token`：id PK；user_id FK IX；token_hash；created_at；last_used_at；expires_at；user_agent（若用 Spring remember-me 則換 `persistent_logins`）

### 共用簽核引擎（給未來三模組重用）
- `workflow_def`：id VARCHAR2 PK（沿用 full、p2_high、p1_emergency）；name；doc_type（CR、ACCOUNT、INSPECTION）；active
- `workflow_step_def`：id PK；workflow_id FK；seq；step_key；name；approver_type（USER／ROLE）；approver_user_id FK 可空；approver_role FK 可空；notify_only；allow_delegate；mode（SEQUENTIAL，預留 POST_HOC）
- `approval_instance`：id PK；doc_type；doc_id VARCHAR2；doc_version NUMBER；workflow_id；status；started_at；closed_at。**UNIQUE(doc_type, doc_id, doc_version)**
- `approval_step`：id PK；instance_id FK IX；seq；step_key；name；notify_only；status；decided_at；decided_by FK；comment
- `approval_step_candidate`（角色池）：(step_id, user_id) PK；name_snapshot；email_snapshot。**IX(user_id)**（「我的待辦」一條查詢）
- `approval_step_item`（預留逐項簽核）：step_id、item_ref、decision、comment。本次只建表

### 申請單（CR）
- `change_request`：id VARCHAR2(20) PK（IM20260826-005）；title；priority；workflow_id；applicant_id FK IX；status IX；source（online／imported）；current_version；row_version（樂觀鎖）；created_at IX；updated_at；deleted_at、deleted_by、delete_reason、delete_via_role
  - basic 攤平：apply_date、department、applicant_name_snapshot、phone、email、executor_self、executor_vendor、work_mode、remote_method、vendor、vendor_contact、vendor_phone、vendor_headcount
  - work 攤平：subject、impact_desc、work_detail CLOB、risk_assessment CLOB、rollback_plan CLOB、reason_other、schedule_start、schedule_end、estimated_hours
  - location 攤平：loc_source、area、rack、u_position、site_id、rack_id、u_start、u_end、omit_reason
- `cr_category`：(cr_id, category_key, option_text) PK
- `cr_category_other`：(cr_id, category_key) PK；text
- `cr_reason`、`cr_impact_scope`：(cr_id, value) PK
- `cr_equipment`：id PK；cr_id FK；seq；name；asset_no IX；model；serial_no IX；purpose；mgmt_ip
- `cr_plan_step`：(cr_id, seq) PK；text（4 筆）
- `cr_checklist_item`：(cr_id, version, seq) PK；item；done；completed_at；executor（11 筆，form-schema.json:157-169）
- `cr_execution`：(cr_id, version) PK；actual_start、actual_end、result、exceptions_has、exceptions_desc、followups_has、followups_desc、notes、closed_by、closed_at
- `cr_version`：(cr_id, version) PK；status_at_close；reason；snapshot_at；form_snapshot CLOB JSON（舊資料匯入為 NULL）
- `cr_event`：id PK；cr_id IX；version；type（SUBMIT、RECALL、EXEC_REJECT、GOV_PASS、GOV_RETURN、RESUBMIT、DELETE）；by_user；at；comment（統一 governanceReview／reviewHistory／executionReject／resubmitNote）
- `cr_number_seq`：(prefix, ymd) PK；last_no。`SELECT … FOR UPDATE` 遞增，取代「當天檔案數 + 1」

### 附件（三模組共用）
- `attachment`：id PK；owner_type（CR、STEP、AI）；owner_id；original_name；stored_name；storage_key；size_bytes；mime；sha256；uploaded_at；uploaded_by。IX(owner_type, owner_id)

### AI
- `ai_review`：id VARCHAR2 PK（沿用 ar_…）；cr_id FK IX；created_at；created_by；model；effort；mode；fell_back_from；app_version；app_status；app_snapshot CLOB JSON；snapshot_hash（新算法）；legacy_sha1；result CLOB JSON；result_schema（v1／v2／v3）；input_tokens、output_tokens、cache_read_tokens、cache_write_tokens；duration_ms；stop_reason；sent_at；sent_to CLOB JSON；status（PENDING、DONE、FAILED）
- `ai_review_message`：id PK；review_id FK IX；seq；role；content CLOB；by_user；app_version_at_ask；model；用量欄位；duration_ms；fell_back_from；created_at

### 其他
- `template`：id PK；name；form CLOB JSON；owner_id；created_*；updated_*；usage_count；last_used_at；last_used_by
- `form_option`（若 ⑰ 選 A）：(group_key, option_key) PK；name、color、definition、workflow_id、sort_no
- `site_setting`：key PK、value（workflowPolicy、uploadMaxFiles、uploadMaxMB、excludeIps）
- `mail_outbox`：id PK；to_json、cc_json、bcc_json；subject；html CLOB；status；attempts；error；smtp_message_id；created_at；sent_at；meta CLOB JSON
- `access_log`：寫 DB 時建（有上限佇列 + 批次寫入，定期清除）
- `rack_inventory_cache`：一列，payload CLOB JSON + fetched_at

### 版次表達
簽核流程（approval_instance／step）每版一列（待辦與統計要用）；表單內容存 `cr_version.form_snapshot` JSON（只整份比對）。

### 申請單編號
格式 `IM{yyyymmdd}-{NNN}` 與 `HIST-` 可沿用（⑨ 待裁示），產號必須改 `cr_number_seq`，刪掉的號碼不再用。

---

## 4. 後端架構

- Java 21 或 25 LTS + Spring Boot 最新 GA（動工當天到 spring.io 確認版本與支援期）
- 套件依功能分：identity、approval（共用引擎）、changerequest、ai、mail、inventory、audit；每套件 controller（DTO）→ service（transaction 邊界）→ domain（純邏輯不依賴 Spring）→ repository（JPA）
- workflow 移植：buildApprovalChain → `ApprovalChainBuilder`（候選人建單時固化寫入 candidate 表）；applyDecision → `DecisionPolicy`；createNewVersion → 關舊 instance、開新 instance、寫 cr_version
- transaction：一次簽核一個 transaction：鎖 CR（row_version 樂觀鎖，衝突回 409）→ 更新 step → 更新 CR 狀態 → 插 mail_outbox 與 cr_event → commit；寄信由 outbox worker commit 後處理
- 認證：server-side session（HttpOnly cookie）+ CSRF token；多機時接 Spring Session JDBC。不用 JWT
- 授權：user_role → `ROLE_*` authority 管 URL 層；資料相關判斷用 `@PreAuthorize("@crAuthz.canDecide(#id)")`；9 個 canX 旗標由後端算進 DTO
- 密碼相容：舊格式 `scrypt$saltHex$hashHex`（N=16384、r=8、p=1、keylen=64），自寫 encoder（BouncyCastle `SCrypt.generate`）掛 DelegatingPasswordEncoder，登入成功時升級
- AI（若 ③ 保留）：Java SDK OutputConfig／tool use／thinking；aws 通道需自訂 baseUrl + header（未驗證，S11 先做概念驗證）；hash 改 key 排序 JSON + SHA-256；retry SDK 內建 + 退避 1s/3s；refusal 用 fallbackModel 重送並記 fell_back_from；followUp 照舊（快照、報告、最近 20 則、新問題，max_tokens 16000）；非同步 202 + 輪詢；提示詞與 REVIEW_SCHEMA 放 resources
- 存取紀錄：寫 DB（有查詢需求）；loopback 改記 LAN IP、excludeIps 照搬
- 信件：JavaMailSender + mail_outbox（queued／sent／failed、重試次數）；五種樣板 + apps.js 三種 inline HTML 全改 Thymeleaf／Mustache 樣板
- 附件：下載一律經 controller 檢查權限；檔名 UTF-8；上傳限制每次請求讀 site_setting
- 機櫃快取：Caffeine + DB 快取列；`@Scheduled` 背景刷新（stale-while-revalidate）；單一執行中旗標；API 改需登入
- myTasksCount：一條 SQL 靠 IX(user_id)

---

## 5. 前端架構

- Vue 3 + Vite + Vue Router + Pinia；UI 元件庫待 ⑤
- 20 頁：Login、ChangePassword、Home、AppList、AppForm（新增／編輯／補件共用）、AppView、Execute、Review、TemplateList、TemplateEdit、Stats、AccessLog、AdminUsers、AdminWorkflows、AdminMail、MailDetail、MailTest、AdminSettings、AdminFormSchema、403/錯誤頁
- 互動元件：dt-picker → DateTimePicker 固定台灣時區；附件拖放／貼上截圖／預檢（`new.ejs:719-763`）→ Upload + 自寫 paste handler；**機櫃 U 位視覺化選擇器（`new.ejs:823-1163` 約 340 行）必須自寫，是前端最大工作項**；設備動態列 → 可編輯表格；workflow 排序（`admin/workflows.ejs:168`）→ SortableJS／vuedraggable（引入前審查）；統計先確認是否純 CSS 圖
- 元件庫比較：Element Plus（後台元件齊、繁中、外觀制式）／Naive UI（TS 原生、主題好調、社群小）／PrimeVue（元件最多、曾大改版）／Vuetify（Material、樣式重）

---

## 6. 資料遷移

- 工具：Java 匯入器（Boot `import` profile + CommandLineRunner + JPA，可重跑可測試）；v1/v2 AI 正規化若需借 Node 則局部搭配（⑩⑮ 待裁示）
- 轉換：假 UTC 減 8 小時（⑪）；`''`→NULL 依第 0 節規則；candidateIds 轉 user FK 並存 name/email 快照；aiReviews 重算 snapshot_hash；deleted 目錄匯成軟刪除；IM20260918-003 撞號那張已刪除單另編新號
- 對帳：筆數依狀態分組一致（IM 263、HIST 93、deleted 12）；「DB 匯出回舊 JSON」工具逐檔 diff（已知轉換列白名單）；附件數／大小／sha256 一致；人工抽 5 張（含 IM20260826-005、一張 HIST、非終結狀態 25 張中抽：rejected 18、in_review 3、in_execution 1、pending_review 3）

---

## 7. 部署與維運

- 啟動：jar + bat（開發期）→ WinSW 服務（⑧ 待裁示）；Docker 不考慮（Windows Server 門檻高）
- 設定：不變的放 `application.yml`；secret（sessionSecret、rack apiKey、SMTP auth、AI 金鑰、Oracle 連線）放環境變數或 ④ 選定處；畫面可調的放 `site_setting`
- 注意：舊系統 sessionSecret 與 rack-inventory apiKey 明文在 config
- 切換：新系統 3201 port UAT → 凍結舊系統 → 最後一次匯入 → 對帳 → 新系統改 3200 → 舊系統唯讀（⑬ 待裁示）

---

## 8. 待裁示 17 項（① 已決定 Oracle，其餘晚點）

| # | 項目 | 選項 | architect 建議 |
|---|---|---|---|
| ① | DB 產品 | A MSSQL／B PostgreSQL／C Oracle | **已拍板 C Oracle 19c** |
| ② | 登入方式 | A DB 帳密沿用舊密碼／B AD/LDAP | A 先上線，B 之後加 |
| ③ | AI 審查去留 | A 保留移植／B 首版拿掉舊報告唯讀 | A（162 份在用） |
| ④ | AI 金鑰存放 | A 環境變數／B DPAPI via JNA／C AWS Secrets Manager | B，搭配 WinSW 指定帳號 |
| ⑤ | UI 元件庫 | A Element Plus／B Naive UI | A |
| ⑥ | 附件存放 | A 檔案系統 DB 存路徑／B DB BLOB | A，下載經後端檢查權限 |
| ⑦ | 前端部署 | A 打包進 jar／B IIS 反向代理分離 | A |
| ⑧ | Windows 啟動 | A jar+bat／B WinSW | B（開發期 A，S15 換 B） |
| ⑨ | 申請單編號 | A 沿用 IM 格式改 DB 計數／B 新格式 | A |
| ⑩ | 三代 AI 報告 | A 原樣存 Java 讀時正規化／B 原樣存 + Node 預轉顯示欄／C 全轉 v3 | B |
| ⑪ | 時間轉換 | A 匯入減 8h 存真 UTC／B 沿用假 UTC | A |
| ⑫ | 未登入可看 | A 全部須登入／B 維持公開 | A |
| ⑬ | 切換策略 | A 凍結一次切換／B 並行寫入 | A |
| ⑭ | remember-me | A 不搬 token 全部重登／B 移植舊 token | A |
| ⑮ | 遷移工具 | A Java 匯入器／B Node 匯出 + Java 匯入 | A（AI 部分借 Node） |
| ⑯ | 既有漏洞 | A 一律修正／B 完全照搬 | A，逐項列 CHANGELOG 並公告 |
| ⑰ | 表單設定 | A DB form_option／B JSON 檔 | B |

---

## 9. 既有漏洞與風險

### 既有漏洞（對應 ⑯）
1. 編號撞號覆蓋既有單（apps.js:88-93）
2. 多處不用登入；`/uploads` 公開 static（server.js:69）；`/api/rack-data` 回設備管理 IP
3. 範本任何登入者可改刪別人的（routes/templates.js）
4. POST `/:id/execute` 沒檢查角色
5. 補件跳過 AI 閘門；admin 能開編輯頁但送出被擋
6. notifyOnly、allowDelegate 沒實作；存檔丟掉 allowDelegate
7. 簽核候選人建單時固定，之後異動不反映
8. history 沒有表單內容快照
9. 假 UTC，信件再 +8h
10. login redirect 參數沒驗證（open redirect）
11. 預設密碼 = 帳號小寫
12. accessLogger 高併發遺失；settings 非原子寫入
13. 首頁「我的待辦」含 draft，與 server.js 算法不一致
14. workflows.json 關卡 key 與名稱錯位（key `dept_manager` 名稱卻是「機房管理員」）；遷移以 name/role 為準

### 改寫風險
15. 機櫃選擇器移植工作量最大（S13）
16. Java SDK 對 aws 通道自訂 header 未驗證（S11 先做）
17. 候選人 ID 大小寫混用；Oracle 分大小寫 → 匯入時統一小寫
18. 使用者改代理鍵後「改 ID 不連帶更新」問題消失，但匯入要把字串 ID 對應成新 id
19. P1「口頭報備、事後補單」（form-schema.json:9）系統沒強制
20. `Docs/` 與 `public/uploads/HIST-*` 約 93 份重複檔；遷移以 uploads 為準
21. smtp TLS 憑證驗證關閉（rejectUnauthorized false），移植時決定是否維持

---

## 10. 待確認（architect 列，原文）

1. ~~repo 平台~~ → 已解：GitHub rt-wayne/Infra_Manager
2. 正式環境 AI provider 是 `aws`（設定檔、SYSTEM_README:153）；兩台機器是否一致？
3. ~~DB 產品~~ → 已解：Oracle 19c；DBA 支援與測試 schema 待 S1 確認
4. `ai.key.enc` 用哪個 Windows 帳號、哪種範圍（CurrentUser／LocalMachine）加密？
5. schedule.start/end、execution.actualStart/End 是不是不帶 Z 的牆上時間？（S3 前量測）
6. aiReviews.appSnapshot 欄位範圍是否與 computeAppHash 輸入一致？（S3 前量測）
7. 附件總容量？（S3 前量測）
8. 帳號權限管理模組管「其他系統的帳號」（八類：主機、資料庫、應用系統、服務、網路設備、廠商、憑證金鑰、門禁卡），不是本系統 RBAC → role/permission 只需預留「簽核角色池可擴充」
9. AD 帳號 ID 能否與現有 login_id 一一對應？（② 選 B 時才需要）
10. 統計頁是否純 CSS 圖？（S14 前確認）
11. Spring Boot／Java 確切版本動工當天確認
12. Java SDK 是否支援自訂 baseUrl + `anthropic-workspace-id` header（S11 驗證）

---

## 11. 分階段施工計畫（S1～S15）

每階段 2～3 回合、可單獨驗收、做完 commit + push。

| 階段 | 目標 | 完成條件 |
|---|---|---|
| S1 骨架 | Boot + Vue/Vite 單一 repo、Flyway 第一支 migration（site_setting）、`/api/health`、Vue 首頁殼、`start-new.bat`（3201） | 瀏覽器開 3201 看到新首頁；`mvn test` 通過 |
| S2 帳號與登入 | app_user／role／user_role；users.json 匯入器；scrypt 相容 encoder；session + CSRF；登入／登出／改密碼 | 舊帳密能登入；預設密碼導向改密碼頁；匯入 22 人筆數一致 |
| S3 CR 資料表與匯入 | Flyway 建 CR 與 approval 表；申請單匯入器（時間轉換、撞號處理）；對帳報告 | 依狀態分組筆數與來源一致；報告進 repo |
| S4 唯讀列表與檢視 | GET 列表（6 篩選、待我簽核置頂）、GET 檢視（關卡、附件下載權限） | 抽 5 張新舊畫面一致 |
| S5 表單設定與範本 | form_option 或 JSON；範本 CRUD（建立者與 admin） | 3 份範本可見；非建立者改範本 403 |
| S6 新增、編輯草稿 | AppForm（不含機櫃選擇器）、附件上傳、編號計數器 | 建草稿成功；同日刪單再建不撞號（有測試） |
| S7 簽核引擎 | 送審、同意、退件、撤回；樂觀鎖；待辦數 | full 5 關走完；兩人同時簽其中一人 409 |
| S8 信件 outbox | mail_outbox、worker、樣板、admin 信件頁、測試信 | 三種信寄到測試信箱；SMTP 中斷 failed 可重寄 |
| S9 補件、版次、刪除 | resubmit 寫 cr_version 快照；軟刪除 | 補件後 v2，v1 簽核紀錄查得到 |
| S10 執行與治理審查 | Execute、execute-reject、Review | approved→in_execution→pending_review→executed；return→rejected |
| S11 AI 概念驗證與審查 | 先驗證 SDK 在正式通道的 structured output／tool 退回／自訂 header；再非同步審查 + hash 把關 | 同單審兩次第二次略過；refusal 有單元測試；**概念驗證失敗就停下回報** |
| S12 AI 對話、寄出、費用 | messages、send、aiPricing、v1~v3 顯示 | 三種舊格式正常顯示 |
| S13 機櫃選擇器 | 盤點快取 + U 位視覺化元件（可能超 3 回合，必要時拆兩段） | 選範圍帶入設備列；Impact 斷線顯示舊快取 |
| S14 後台與統計 | users、workflows、settings、form-schema、stats、access_log | 統計與舊系統同區間一致 |
| S15 服務化與切換演練 | WinSW、正式匯入演練、切換 runbook 進 SETUP.md | 演練機完整跑一次切換與回退 |
