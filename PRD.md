# Infra Manager（Java 版）

> 遠端倉庫：https://github.com/rt-wayne/Infra_Manager.git（預設分支 `main`）
> 本檔描述**已拍板的目標系統**。標「待定，見 BACKLOG.md 第 N 項」的部分尚未裁示，以 BACKLOG 為準。

## 專案概述

機房設備異動申請系統：把機房設備異動（上下架、搬遷、維護等）的紙本申請與簽核流程數位化。申請人線上填寫異動申請單（含設備、機櫃位置、施工計畫、風險與回退方案），系統依簽核流程逐關送審，核准後由機房端執行並填寫檢核表，最後由治理單位審查結案；全程寄信通知，並可用 AI 預先審查申請單內容。使用者為公司內 IT 各單位申請人、各級主管、機房管理員與治理審查人員。

本專案是 Node 版（`D:\ai\Infra_Manager`，Express + EJS + JSON 檔儲存）的重寫版，改為 Spring Boot REST API + Vue 3 SPA + Oracle 19c。舊 Node 專案完全不動，切換後保留唯讀。

## 背景與動機

- 舊系統以 JSON 檔儲存，已出現實際資料問題：申請單編號用「當天檔案數 + 1」產生，刪單後新單會撞號並**覆蓋既有單**；時間欄位存台灣時間卻標 `Z`（「假 UTC」，每筆多 8 小時，信件再加 8 小時）；存取紀錄高併發時遺失。改用關聯式資料庫（Oracle 19c）與 DB 計數器根治
- 公司未來還有兩個模組（帳號權限管理、機房巡檢）要做，需要可共用的簽核引擎與待辦資料模型。這兩個模組**本次不做**，但簽核引擎（`IM_FLOW`／`IM_FLOW_STEP` 流程定義與 `IM_APPR` 系列簽核實例表：`DOC_TYPE` 區分單據類型、`STEP_MODE_CODE = POST_HOC` 支援事後補核、`IM_APPR_STEP_ITEM` 支援逐項簽核）與附件表（`IM_ATTACH.OWNER_TYPE` 區分擁有者）要留得住擴充
- 資料庫依公司《DB規範_V1_20261002_JLA》16 條撰寫，技術棧依公司技術規範選定，讓 DBA 與維運單位能用公司標準審查與接手
- 帳號權限管理模組管的是「其他系統的帳號」（主機、資料庫、應用系統、服務、網路設備、廠商、憑證金鑰、門禁卡八類），不是本系統的 RBAC，所以本系統的角色只需預留「簽核角色池可擴充」
- 巢狀資料採「混合式」放法：常查欄位攤平成欄位、簽核鏈／版次／附件／AI 對話拆子表、AI 報告與範本表單留 JSON——兼顧查詢效能與遷移成本

---

## 第一區塊：功能說明（所有人適用）

### 功能清單

- **登入與帳號**：用帳號密碼登入（登入方式待定，見 BACKLOG.md 第 16 項）；第一次用預設密碼登入會被要求改密碼；改密碼規則為至少 6 字、不得等於帳號，改完後其他裝置的「記住我」失效。「記住我」是否保留舊 token 待定，見 BACKLOG.md 第 28 項
- **首頁**：看申請單統計與「我的待辦」（等我簽核的單）；並顯示系統狀態（後端服務與資料庫是否正常、後端時間），可按「重新檢查」
- **申請單列表**：預設顯示近 90 天，可依狀態、優先等級、來源、只看我的、關鍵字、日期區間篩選；待我簽核的單排在最上面；顯示 AI 摘要與 AI 費用
- **新增／編輯申請單**：填寫基本資料、異動類別、原因、影響範圍、設備清單、機櫃 U 位、施工步驟、排程、風險評估與回退方案；可套用範本；可上傳附件（拖放、貼上截圖、上傳前預檢）；先存成草稿
- **送審與撤回**：申請人送審後依簽核流程逐關進行；AI 審查啟用時，送審前必須有一份與目前內容相符的 AI 報告；還沒有人簽之前可撤回成草稿
- **簽核**：當前關卡的候選人可同意或退件（退件必填意見），可附檔
- **退件補件**：被退件的單由申請人修改後補件，產生新版次並直接重新進入審核；舊版次的簽核紀錄與表單內容都保留可查
- **執行**：核准後由機房端填寫執行檢核表（11 項）與實際執行紀錄（實際起訖、結果、異常、後續事項）；每個檢核項的執行人可以選系統使用者，或填自由文字（廠商、多人等沒有系統帳號的執行者，限 200 字），兩者至多擇一；填完進入待審查；執行端可退回申請
- **治理審查**：治理人員審查執行結果，通過即結案，退回則回到退件狀態
- **刪除**：申請人（符合條件時）或 admin 可刪除申請單，需輸入單號確認；刪除為軟刪除，編號不再被重用
- **AI 審查**（是否保留待定，見 BACKLOG.md 第 17 項）：對申請單做 AI 預審，內容沒變就略過、可強制重審；AI 執行在背景進行，畫面輪詢結果；可對報告追問；admin 可寄出報告
- **範本**：範本列表、檢視、新增、修改、刪除（修改／刪除的權限範圍待定，見 `BACKLOG.md` 第 30 項）
- **統計**：依日／週／月分桶統計申請單
- **機櫃盤點**：從 Impact 系統取得機櫃與設備資料（快取，背景刷新），在新增申請單時用機櫃 U 位視覺化選擇器選位置並帶入設備
- **後台管理（admin）**：使用者管理（新增、修改、停用；使用者只能停用、不能刪除）、簽核流程管理、外寄信件紀錄與失敗重寄、測試信、系統設定（workflowPolicy、上傳限制、排除記錄的 IP）、表單設定維護（優先等級、設備類別、申請原因、影響範圍、檢核項等選項存在資料庫，admin 可在後台維護）、存取紀錄查詢
- **權限範圍**：未登入可看哪些頁面待定，見 BACKLOG.md 第 26 項；舊系統的已知權限漏洞照搬或修正待定，見 BACKLOG.md 第 30 項

### 使用者流程

1. 申請人登入 → 新增申請單（可套用範本、選機櫃位置、上傳附件）→ 存成草稿（`DRAFT`）
2. （AI 審查啟用時）執行 AI 審查，取得與目前內容相符的報告
3. 送審 → 進入審核（`IN_REVIEW`），系統依該單的簽核流程（`full`、`p2_high`、`p1_emergency`）逐關通知候選人
4. 每一關候選人同意 → 進入下一關；任一關退件 → `REJECTED`，申請人修改後補件 → 新版次重新審核
5. 全部關卡同意 → 核准（`APPROVED`）→ 執行中（`IN_EXECUTION`）
6. 機房端填寫檢核表與實際執行紀錄 → 待審查（`PENDING_REVIEW`）；執行端也可退回 → `REJECTED`
7. 治理人員審查：通過 → 結案（`EXECUTED`）；退回 → `REJECTED`
8. 各關卡與結案時系統寄信通知相關人員

括號內為申請單狀態代碼（`IM_APP.APP_STATUS_CODE`，共七種）；刪除不是狀態，見「資料結構」的 `IM_APP`。

### 畫面說明

Vue 3 SPA，共 20 頁：

| 頁面 | 用途 |
|---|---|
| LoginView | 登入 |
| ChangePasswordView | 改密碼（預設密碼登入後強制導向） |
| HomeView | 首頁統計、我的待辦、系統狀態（後端服務／資料庫「正常」或「無法連線」） |
| AppListView | 申請單列表與篩選 |
| AppFormView | 新增／編輯草稿／補件共用表單，內含機櫃 U 位選擇器、設備可編輯表格、附件上傳 |
| AppViewView | 檢視申請單；送審、撤回、簽核、刪除、AI 審查與追問、寄出報告 |
| ExecuteView | 執行檢核表（每項執行人選系統使用者或填自由文字）、實際紀錄、執行端退回 |
| ReviewView | 治理審查 |
| TemplateListView／TemplateEditView | 範本列表與編輯 |
| StatsView | 統計 |
| AccessLogView | 存取紀錄（admin） |
| AdminUsersView | 使用者管理 |
| AdminWorkflowsView | 簽核流程管理（關卡可拖曳排序） |
| AdminMailView／MailDetailView／MailTestView | 外寄信件紀錄、信件內容、測試信 |
| AdminSettingsView | 系統設定 |
| AdminFormSchemaView | 表單設定維護（`IM_FORM_OPTION` 的選項） |
| 403／錯誤頁 | 無權限與錯誤提示 |

畫面上的按鈕是否顯示，依後端在申請單 DTO 內回傳的 9 個 `canX` 權限旗標決定，前端不自行判斷權限。

---

## 第二區塊：技術規格（工程師適用）

### 技術架構

#### 技術選型
以公司兩個開發範本為底：後端用 dev_template_jdk25、前端用 web_template_3.5。兩個範本各自的開發規範在 `infra_manager_java/README.md` 與 `infra_manager_web/README.md`（三條鐵律、標準分層、properties 分工、建置與執行、檢查清單），本檔不重複，只記本系統在範本之上的選擇。

| 層 | 技術 |
|---|---|
| 後端 API | `infra_manager_java/`：Java 25 LTS（Temurin）+ Spring Boot 4.1.1，port 3202 |
| 前端殼 jar | `infra_manager_web/infra_manager_web/`：Spring Boot 4.1 / Java 25，port 3201，context path `/infra_manager_web`；服務前端靜態檔並轉發 API |
| 前端 | `infra_manager_web/infra_manager_web_frontend/`：Vue 3.5 + TypeScript + Vite 8 + vue-router（hash 模式）+ axios；Node 24；不使用 UI 元件庫，沿用範本 `src/assets/main.css` 的色票與基礎字級 |
| 建置工具 | Maven，一律透過各目錄的 Maven Wrapper（`mvnw`）執行；前端用 npm |
| 資料存取 | 範本 `com.mpx.common.db` 的 `DbClient`（`query`／`update`，`:name` 具名參數）；**無 JPA、無 Flyway** |
| 資料庫 | Oracle 19c；建表與改表一律由開發方提供 SQL 檔（放 `db/oracle/`），由使用者以 `rd_user` 手動執行 |
| JDBC 驅動 | ojdbc17 + orai18n（中文字元集轉換用），隨範本 pom 引入。範本 pom 另附帶 `mssql-jdbc`，本系統不使用、不移除（見「給範本維護者的註記」第 3 點） |
| DB 連線資訊 | 執行期由範本向公司「DB 連線資訊 API」依別名取得（jdbcUrl／帳密不在程式、設定檔或環境變數），並建 Hikari 連線池 |
| 測試 | 後端 JUnit 5（單元測試由 surefire 跑、`*IT` 整合測試由 failsafe 跑）；前端 Vitest（jsdom）；端對端 Playwright |
| 測試 DB | 直接連使用者提供的那台公司 Oracle 19c 測試環境（不用 H2、不用 Testcontainers）；連線資訊同樣由連線資訊 API 提供，不進 repo |
| 部署 | Docker 容器，主機作業系統 Rocky Linux 9.7 |

舊 Node 系統仍在 port 3200 執行，供對照。

#### 資料庫帳號
只列帳號名稱與用途；密碼與連線字串不寫進 repo 內任何檔案。

| 帳號 | 用途 |
|---|---|
| `rd_user` | 具建表權限，用來執行 DDL；表建在此帳號的 schema 下 |
| `ap_user` | 應用程式連線用；對 V1 的 31 張表與交易測試表 `IM_TX_TEST` 只有 SELECT／INSERT／UPDATE／DELETE 權限，由 `rd_user` 授權；應用程式以 schema 前綴（`rd_user.表名`）存取（授權 SQL 為 `db/oracle/grant_ap_user.sql`，不建同義詞）。正式 DAO 的 SQL 怎麼帶 schema 前綴待定，見 BACKLOG.md 第 77 項 |

連線用哪個帳號，由 `config/database.properties` 的 `db.connect.itflow` 所設的別名在連線資訊 API 端決定。

#### Oracle 19c 連動規則（全系統適用）
- 空字串 `''` 在 Oracle 等於 NULL → 規則為「DB 存 NULL、API 回 `""`」，在資料存取層（DAO／model）統一處理
- JSON 欄位用 `CLOB` + `CHECK (col IS JSON)`（19c 沒有原生 JSON 型別），欄名後綴 `_JSON`
- 字元集 AL32UTF8、字串長度模式 STANDARD：一個中文字佔 3 位元組，`VARCHAR2` 上限 4000 位元組（約 1333 個中文字），所以使用者自由輸入的長文欄位用 `CLOB`（簽核意見 `MEMO`、`IMPACT_DESC`、`RESUB_MEMO`、`EXCPT_DESC`、`FOLLOW_UP_DESC`、`VER_REASON`、事件 `MEMO` 等），系統產生的錯誤訊息／query string 限 `VARCHAR2(1000 CHAR)`，寫入前由程式端按字元（code point）截斷。由 API 驗證長度，超過回 400：本次由 `VARCHAR2` 改成 `CLOB` 的 8 欄每欄上限 2000 字（舊資料實測最長 1665 字，為簽核意見，放得下），原本就是 `CLOB` 的 4 欄（`WORK_DETAIL`、`RISK_DESC`、`ROLL_BACK_PLAN`、`EXEC_MEMO`）每欄上限 20000 字（舊資料最長為作業內容 10749 字，另有 21 張單的作業內容超過 2000 字）。字數以 code point 計、換行先統一成 LF 再計算（不直接用 `@Size`，它以 UTF-16 單位計，emoji 會算 2 字），由 `util.TextLength.check` 檢核並回傳正規化後的字串（呼叫端以回傳值寫入 DB）；請求本文上限見「後端分層」的「請求本文上限」。CLOB 欄位不得用在 `DISTINCT`／`ORDER BY`／`GROUP BY`／`=` 比較（Oracle 不允許）
- 布林用 `NUMBER(1)` + `CHECK (col IN (0, 1))`（23ai 以前沒有 boolean），欄名前綴 `IS_`
- Oracle 字串比對分大小寫：`IM_USER.LOGIN_ID` 一律存小寫，由 CHECK 約束強制
- 時間欄位一律 `DATE`，存台灣當地時間（不帶時區）；畫面一律以台灣時區顯示（`SYS_PARAM` 的 `TIME_ZONE` = `Asia/Taipei`）
- 可能含中文的字串欄位用字元語意 `VARCHAR2(n CHAR)`；代碼、鍵值類用位元組語意

#### Repo 結構
單一 repo（`D:\ai\Infra_Manager_Java`）：
```
infra_manager_java/                        後端 API（dev_template_jdk25，port 3202）
infra_manager_web/infra_manager_web/       前端殼 jar（web_template_3.5，port 3201）；src/frontend/ 為前端 build 產物（不進 git，只留 .gitkeep）
infra_manager_web/infra_manager_web_frontend/  Vue 3 SPA 原始碼
db/oracle/   DDL 與授權 SQL，皆由 rd_user 手動執行（見「資料結構」）
db/tools/    gen_table_doc.js（從 V1 DDL 重產給 DBA 審的 xlsx）
docs/db/     Table_List_Schema.xlsx（給 DBA 審的 Table List／Table Schema，由 db/tools 產生）
docs/plan/   規劃文件（歷史文件，不再更新）
start-new.bat  本機一鍵建置並啟動兩個 jar
```

#### 後端分層
- 範本的 `com.mpx.common` 與 `com.mpx.Application` 不修改；業務程式只放 `com.mpx.infra_manager_java` 底下
- 分層依範本：controller（`@RestController`，路徑 `/api/<資源>`）→ service（業務邏輯，交易邊界）→ dao（`@Repository`，注入 `DbClient`，DB 別名以 `@Value("${db.connect.itflow}")` 讀取）→ model（POJO，SQL 欄位別名大寫底線對應 camelCase 屬性）。另有 `config`（Spring 設定）、`web`（filter 與例外處理）、`util`（共用工具）
- 業務功能範圍：`identity`、`approval`（共用簽核引擎）、`changerequest`、`ai`、`mail`、`inventory`、`audit`
- **交易管理**：範本 `DbClient.update` 為單句 autocommit、不提供 TransactionManager；本系統一張申請單要同時寫多張表，因此在 `config.AppTransactionConfig` 自建 `LazyAliasTransactionManager`（繼承 `DataSourceTransactionManager`）。它疊在範本已建好的 Hikari 連線池上（不另建池），第一次交易時才依 `db.connect.itflow` 別名取池並快取 DataSource，維持範本「連線資訊 API 位址未設仍可啟動」的行為；別名空白時啟動即失敗。`@Transactional` 範圍內的 `DbClient` 呼叫共用同一條連線、一起 commit／rollback
- **請求本文上限**（`config.RequestLimitConfig`，常數寫在程式內）：JSON 等非 multipart 本文 1 MB（`web.BodyLimitFilter`，掛在 `/api/*`；`Content-Length` 已超過就不讀本文、未知長度則邊讀邊計數）、multipart 單檔 50 MB、整個 multipart 請求 500 MB，超過一律回 413。multipart 延後解析（`spring.servlet.multipart.resolve-lazily=true`），只有真的取 `MultipartFile` 參數的端點才會落暫存檔
- **錯誤處理**：`web.ApiExceptionHandler` 統一把例外轉成 `{"message": …}` 回應（格式見「API 規格」），回應一律不帶內部細節；log 只記例外類別名、ORA 錯誤碼、method／path 與未預期例外的堆疊前 10 個 frame，不記例外訊息
- **log 紀律**：log 不得含密碼、jdbcUrl、帳號、SQL 參數值。log 落點依範本（`/home/tomcat/log/infra_manager_java/`，Windows 對應啟動時工作目錄所在磁碟機）
- **簽核引擎**：`ApprovalChainBuilder` 建立簽核鏈——送審時開一筆 `IM_APPR`、依 `IM_FLOW_STEP` 展開 `IM_APPR_STEP`，角色池關卡的候選人在送審當下由 `IM_USER_ROLE_MAP` 展開、固化寫入 `IM_APPR_CAND_MAP`；`DecisionPolicy` 處理同意／退件判定；補件時關閉舊 `IM_APPR`、開新一筆，並把前一版表單全文寫入 `IM_APP_VER`
- **Transaction**：一次簽核一個 transaction——以 `IM_APP.ROW_VER_NO` 樂觀鎖鎖定申請單（衝突回 409）→ 更新 `IM_APPR_STEP` → 更新申請單狀態 → 插入 `IM_MAIL_OUTBOX` 與 `IM_APP_EVENT` → commit；寄信由 outbox worker 在 commit 後處理
- **認證**：server-side session（HttpOnly cookie）+ CSRF token；多機部署時接 Spring Session JDBC；不用 JWT。登入方式（DB 帳密或 AD/LDAP）待定，見 BACKLOG.md 第 16 項；殼 jar 轉發時不帶瀏覽器 cookie 與 header，session 放在哪一層、怎麼傳到後端待定，見 BACKLOG.md 第 78 項
- **授權**：`IM_USER_ROLE_MAP` 轉成 `ROLE_*` authority 管 URL 層；與資料相關的判斷用 `@PreAuthorize("@crAuthz.canDecide(#id)")`；9 個 `canX` 旗標由後端算進 DTO
- **密碼相容**：舊格式 `scrypt$saltHex$hashHex`（N=16384、r=8、p=1、keylen=64），以自寫 encoder（BouncyCastle `SCrypt.generate`）掛在 `DelegatingPasswordEncoder`，登入成功時升級成新格式
- **AI 審查**（是否保留待定，見 BACKLOG.md 第 17 項；金鑰存放待定，見 BACKLOG.md 第 18 項）：Anthropic Java SDK，使用 OutputConfig（structured output）／tool use／thinking；Bedrock mantle 端點拒絕 `output_config.format`，走 bedrock 通道一律落到 tool 模式；aws 通道需自訂 baseUrl + `anthropic-workspace-id` header（未驗證，S11 先做概念驗證）；報告是否過期以「key 排序 JSON + SHA-256」的申請單快照 hash 判斷；retry 用 SDK 內建 + 退避 1s／3s；遇 refusal 用 fallbackModel 重送並記 `fell_back_from`；追問的輸入為快照、報告、最近 20 則對話、新問題，max_tokens 16000；審查為非同步（回 202，前端輪詢）；提示詞與 REVIEW_SCHEMA 放 `resources`
- **存取紀錄**：Servlet Filter 寫 `IM_ACCESS_LOG`（有上限佇列 + 批次寫入，定期清除；保留天數待定，見 BACKLOG.md 第 70 項）；loopback 改記 LAN IP；`SYS_PARAM` 的 `EXCLUDE_IP` 所列 IP 不記錄
- **信件**：JavaMailSender + `IM_MAIL_OUTBOX`（`PENDING`／`SENT`／`FAILED`、`TRY_CNT` 重試次數）；所有信件內容（舊系統五種樣板 + 三種 inline HTML）改用 Thymeleaf 或 Mustache 樣板
- **附件**：檔案本體存檔案系統，DB（`IM_ATTACH.FILE_PATH`）只存相對於附件根目錄的路徑；下載一律經 controller 檢查權限，不提供公開 static 路徑；檔名 UTF-8；上傳限制（`SYS_PARAM` 的 `UPLOAD_MAX_FILES`、`UPLOAD_MAX_MB`）每次請求讀取；殼 jar 的 `ApiForwarder` 只轉 JSON，上傳與下載的轉發方式待定，見 BACKLOG.md 第 79 項
- **機櫃盤點快取**：Caffeine + DB 快取列（`IM_RACK_CACHE`，依快取鍵值分列，如 `SITES`、`RACKS_<站點代碼>`）；`@Scheduled` 背景刷新（stale-while-revalidate）；單一執行中旗標避免重複刷新；外部機櫃系統（Impact）斷線時回舊快取
- **我的待辦數**：一條 SQL，靠 `IM_APPR_CAND_MAP` 的 `USER_ID` 索引

#### 前端架構
- 兩包（web_template_3.5）：
  - **殼 jar** `infra_manager_web/infra_manager_web/`：port 3201、context path `/infra_manager_web`；服務前端靜態檔；controller 只透過範本的 `ApiForwarder` 把 `/api/v1/**` 轉給後端 `/api/**`（後端位址由殼 jar `config/host.properties` 的 `backend.api.domain.path` 指定），不解析 payload、不加 CrossOrigin。不連 DB、沒有 SQL
  - **前端** `infra_manager_web/infra_manager_web_frontend/`：Vue 3.5 + TypeScript；vue-router **hash 模式**；axios 實例 `src/api/http.ts`（baseURL `/infra_manager_web/api/v1`、timeout 120 秒，與殼 jar read timeout 一致）；API 呼叫集中在 `src/api/`、請求與回應型別放 `src/types/`、畫面放 `src/views/`、測試放 `tests/`；Vite `base` 為 `/infra_manager_web/`，build 輸出到殼 jar 的 `src/frontend`，殼 jar 打包時一併放進 jar
- 頁面只呼叫自己殼 jar 的 `/infra_manager_web/api/v1/...`，不直接打後端 3202。入口網址 `http://localhost:3201/infra_manager_web/#/`
- 不使用 UI 元件庫，沿用範本 `main.css` 的色票（`--teal`、`--line` 等）與基礎字級（`body` 19px）；版面規範依範本 README
- 呼叫失敗時一律提示錯誤（toast），不得把失敗顯示成「查無資料」
- 互動元件：
  - DateTimePicker：固定台灣時區
  - 附件上傳：拖放、貼上截圖（自寫 paste handler）、上傳前預檢
  - **機櫃 U 位視覺化選擇器**：自寫元件，是前端最大工作項
  - 設備清單：可編輯表格（動態增刪列）
  - 簽核流程關卡排序：拖曳排序（SortableJS 或 vuedraggable，引入前審查）
  - 統計圖表：做法待 S14 前確認舊系統是否純 CSS 圖，見 BACKLOG.md 第 38 項
- 正式環境的前端部署：前端 build 進殼 jar 的 static，與殼 jar 一起以單一 jar 部署（前端範本的既定架構），不另設 nginx 反向代理容器

#### 給範本維護者的註記
本系統在兩個範本之上有以下超出範本範圍的做法或對範本的需求，供範本維護者評估：
1. **自建 TransactionManager**：後端範本 README 寫明 `DbClient` 不支援交易（沒有 `@Transactional`／TransactionManager）。本系統在業務 package 自建 `LazyAliasTransactionManager`（見「後端分層」的「交易管理」），疊在範本連線池上，`com.mpx.common` 未修改
2. **`ApiForwarder` 把後端 4xx 轉成 500**：殼 jar 範本的 `ApiForwarder` 遇後端任何錯誤一律回 500 `{"message":"後端服務呼叫失敗"}`，後端的 400（如字數超過的 `field`／`max`／`actual`）到不了前端。已決定向範本維護者提變更單，讓 4xx 原樣轉發；變更單通過前，前端在表單送出前自行檢核字數（規則同後端：code point、換行先轉 LF）。另殼 jar 本身沒有請求本文上限、會把整份 JSON 讀進記憶體再轉發，相關待辦見 BACKLOG.md 第 80 項
3. **範本 pom 附帶 `mssql-jdbc`**：本系統只連 Oracle，用不到 SQL Server 驅動；但它是範本 pom 的一部分、範本 `com.mpx.common.db` 的測試可能依賴，本系統不自行移除，請範本維護者評估改為可選依賴。

#### 測試策略
- **單元測試**：`./mvnw clean package`（surefire）執行，不連真 DB 或真 API；需要 DB 的類別一律 mock `DbClient`（交易管理器的測試 mock `DbConnectionManager`）。S1 骨架共 78 項（範本 `com.mpx.common.db` 42 項、業務程式 36 項）；範本的 42 項不可修改、不可刪除
- **整合測試**：檔名 `*IT`，只在 `./mvnw verify`（failsafe）執行，直接連公司測試 Oracle（連線資訊 API 提供）。`host.properties` 的連線資訊 API 位址為空時略過（`HealthIT`、`TransactionRollbackIT`）；`TransactionRollbackIT` 另在交易測試表 `IM_TX_TEST` 尚未建立時略過，寫入的資料以隨機鍵區分、結束一律刪除
- 不用 H2、不用 Testcontainers
- **前端**：`npm run type-check`（vue-tsc）、`npm test`（Vitest，jsdom）

#### 資料遷移（從舊 Node 系統）
- 工具：Java 匯入器（Spring Boot `import` profile + CommandLineRunner，資料存取走 `DbClient`，可重跑、可測試）。遷移工具最終選擇待定，見 BACKLOG.md 第 29 項；三代 AI 報告（v1／v2／v3）的轉換方式待定，見 BACKLOG.md 第 24 項
- 資料量：applications 93 張 HIST（紙本歷史單）+ 263 張 IM（其中 162 張有 AI 報告）、已刪除 12 張、使用者約 22 人、範本 3 份
- 轉換規則：
  - 時間：一律存成 `DATE` 台灣當地時間；舊資料的假 UTC（存台灣時間卻標 `Z`）由匯入程式修正
  - `''` → NULL，依「Oracle 19c 連動規則」
  - 人員：舊系統的字串帳號依「帳號工號對照表」轉成工號（`IM_USER.USER_ID`），所有人員欄位都存工號；比對帳號一律不分大小寫。`LOGIN_ID` 匯入時轉小寫、保留原有空白（舊資料如 `Alan Kuo`、`gary` 大小寫混用）。對照表待使用者提供，見 BACKLOG.md 第 60 項。alex 即陳儀仁，陳儀仁的紙本單直接掛 alex 的工號，不另建代用帳號
  - 紙本歷史單：申請人（`IM_APP.APPLY_USER_ID`）依對照表掛工號，對不到者掛停用的代用帳號 `HIST_PAPER`（`HIST_PAPER` 只用於申請人）；簽核紀錄沿用舊資料——簽核人（`IM_APPR_STEP.USER_ID`）取 `decidedBy`（如 `gary`）依對照表轉成工號，簽核時間（`IM_APPR_STEP.DECIDE_DATE`）取 `decidedAt`（舊系統補核慣例為紙本日 +1 天）；簽核流程掛停用的 `imported` 流程（單一 `POST_HOC` 事後補核關卡）
  - 簽核候選人 `candidateIds` 轉成工號寫入 `IM_APPR_CAND_MAP`（不存姓名、email 快照；名稱只存主檔）
  - 檢核表執行人：舊資料 `checklist.executor` 是自由文字（含廠商、多人）。空字串與「(紙本匯入)」存 NULL；能唯一對應到一位使用者者（不分大小寫比對登入帳號，或比對中文姓名）填 `IM_APP_CHECK_LIST.USER_ID`、`EXEC_USER_DESC` 留 NULL；其餘原文存 `EXEC_USER_DESC`
  - 機櫃位置：舊資料的區域、機櫃、U 位自由文字存 `IM_APP.AREA_NAME`／`RACK_NAME`／`U_RANGE`；新單只寫 `SITE_ID`／`RACK_ID`（與 `U_START_NO`／`U_END_NO`），名稱由外部機櫃系統帶出
  - 勾選類（設備類別、申請原因、影響範圍、檢核項）以名稱對應 `IM_FORM_OPTION`，對不到者先建 `STATUS` 0 的選項再掛
  - 匯入時 `CREATE_DATE`／`CREATE_BY` 寫入原始值；代理鍵若指定原值匯入，匯入完成後須重設該表 identity 起點（步驟見 `SETUP.md`）
  - AI 報告重算 `SNAP_HASH`：對 `aiReview.appSnapshot` 與目前申請單都用「key 排序 JSON + SHA-256」重算（舊 `computeAppHash` 是 `sha1(JSON.stringify(...))`，依賴 JS key 插入順序，Java 算不出同值）；舊值保留在 `LEGACY_SHA1`。`IM_AI_REVIEW`、`IM_TMPL` 主鍵沿用舊 id
  - 已刪除目錄匯成軟刪除（`IM_APP.STATUS` 0 + `DELETE_*` 欄位）；舊系統因「當天檔案數 + 1」取號，有 4 張已刪除單與現存單同號（`IM20260505-002`、`IM20260505-003`、`IM20260506-002`、`IM20260918-003`，皆為不同的單），主鍵 `APP_ID` 不允許重複，這 4 張已刪除單改號匯入：原單號後加後綴 `-D`（如 `IM20260505-002-D`），不從序號表取號；它們所有子表與關聯資料（含附件、AI 審查、事件紀錄、版次、簽核實例）的單號隨之改號。舊系統附件放在 `public/uploads/<單號>/`，撞號的已刪除單與現存單共用同一資料夾，附件歸屬以各單 JSON 的 `attachments[].storedName` 判定、不以資料夾判定；匯入時把這 4 張已刪除單中有附件的 2 張（`IM20260505-003`、`IM20260918-003`）的實體檔，複製到各自的新資料夾 `IM20260505-003-D`（1 個檔）與 `IM20260918-003-D`（4 個檔），資料夾名一律等於單號；`IM20260505-002`、`IM20260506-002` 無附件。細節見 BACKLOG.md 第 75 項
  - 申請單編號：舊單沿用原編號匯入（上述 4 張已刪除單除外）；依各前綴各日最大序號回填 `IM_APP_SEQ.LAST_NO`，避免切換當日新單、以及日後補匯同日紙本單撞號；先改號、後回填，回填時排除帶 `-D` 後綴的單號
  - 舊簽核人 `Eric` 與角色代碼 `dept_manager` 不在帳號檔，對應方式待定，見 BACKLOG.md 第 60 項
  - 自由文字欄（如檢核表執行人含 `&`）一律以 JDBC 參數綁定寫入，不產生 INSERT 腳本經 SQL*Plus 執行
  - 舊版次快照沒有表單內容；`IM_APP_VER.FORM_JSON` 為 NOT NULL，舊版次匯入時填什麼待定，見 BACKLOG.md 第 69 項
  - `workflows.json` 關卡 key 與名稱錯位（如 key `dept_manager` 名稱卻是「機房管理員」），以實際角色為準——預載的 `IM_FLOW_STEP` 已依 `ROLE_ID` 對好，`STEP_CODE` 沿用舊 key
  - 附件以 `public/uploads` 為準（`Docs/` 與 `public/uploads/HIST-*` 約 93 份重複檔不重複匯入）
- 對帳：
  - 筆數依狀態分組一致（IM 263、HIST 93、deleted 12）
  - 「DB 匯出回舊 JSON」工具逐檔 diff（已知轉換列入白名單）
  - 附件數／大小／sha256 一致
  - 人工抽 5 張：IM20260826-005、一張 HIST，以及從非終結狀態 25 張中抽（rejected 18、in_review 3、in_execution 1、pending_review 3）

#### 部署與維運
- 部署：Docker 容器，主機作業系統 Rocky Linux 9.7；容器的啟動與管理方式（docker compose 或 systemd）待定，見 BACKLOG.md 第 22 項。開發期在本機以 jar + bat 啟動（`start-new.bat`：後端 3202、殼 jar 3201），本機開發不使用 Docker
- 設定分三處：不變的放各 jar 的 `*.properties`（真檔不進 git，進 git 的是同名 `.properties.example`，鍵值見「環境設定」）；secret（session secret、機櫃 API 金鑰、SMTP 認證、AI 金鑰）放環境變數（AI 金鑰存放處待定，見 BACKLOG.md 第 18 項）；DB 連線資訊不放本系統任何地方，由連線資訊 API 依別名提供；畫面上可調的放 `SYS_PARAM`
- 切換：新系統以 3201 port 上 UAT，正式切換後改用 3200，舊系統保留唯讀；切換策略待定，見 BACKLOG.md 第 27 項

### 資料結構

DDL 全部放在 `db/oracle/`，由 `rd_user` 手動執行（不使用 Flyway）：

| 檔案 | 內容 |
|---|---|
| `V1__init_schema.sql` | 業務表 31 張與預載資料 |
| `V2__tx_test_table.sql` | 交易回滾整合測試專用表 `IM_TX_TEST`（只供 `TransactionRollbackIT` 使用，平時應為空；與業務表脫鉤） |
| `V2a__tx_test_alter.sql` | 給已用舊版 V2 建過 `IM_TX_TEST` 的環境補共同欄位 `UPDATE_DATE`／`UPDATE_BY`；全新環境不需要 |
| `grant_ap_user.sql` | 把上述全部表的 SELECT／INSERT／UPDATE／DELETE 授權給 `ap_user`；可重複執行，新增表時補一行 |

業務表的定義來源是 `V1__init_schema.sql`（Oracle 19c，31 張表）；完整欄位、型別、約束與欄位說明以該檔為準，本節列每張表的用途、鍵與關鍵欄位。PK 主鍵、UK 唯一鍵、IX 索引。

#### 命名與欄位慣例
依公司《DB規範_V1_20261002_JLA》16 條，加上寫在 DDL 檔頭的本系統自訂慣例：
- 業務表前綴 `IM_`；`SYS_PARAM`（規範指定的系統參數表）不加前綴，其欄名以 `PARAM_` 為實體前綴；純鍵值的多對多對照表以 `_MAP` 結尾，並在表說明註明退場條件
- 主鍵：主檔用業務鍵（`IM_USER`、`IM_ROLE`、`IM_FLOW`、`IM_APP`、`IM_TMPL`、`IM_AI_REVIEW`、`IM_RACK_CACHE`）；`_MAP` 對照表（5 張）與 `SYS_PARAM` 保留複合主鍵；其餘 18 張表一律以「表名_ID」identity 代理鍵為主鍵（`NUMBER(19) GENERATED BY DEFAULT ON NULL AS IDENTITY`，匯入時可指定原值）；有業務鍵者設唯一鍵（`UK_`）或唯一索引（`IND_`），`IM_ACCESS_LOG`、`IM_APP_EVENT`、`IM_MAIL_OUTBOX` 為流水紀錄、無業務鍵
- 人員欄位一律存工號，型別 `VARCHAR2(30)`，參照 `IM_USER.USER_ID`；同表只參照一次時欄名即 `USER_ID`，參照多次時加角色前綴（`APPLY_USER_ID`、`DELETE_USER_ID`、`OWNER_USER_ID`、`LAST_USE_USER_ID`）。登入帳號另存在 `IM_USER.LOGIN_ID`。常設例外只有 `IM_APP_CHECK_LIST.EXEC_USER_DESC`（執行人原文，廠商與多人沒有工號），為常設欄位、不設退場條件；另 `IM_ACCESS_LOG.LOGIN_ID` 為帶退場條件的帳號快照（確認 `USER_ID` 足以追查後移除）
- 表間參照為邏輯參照，DDL 不建 FOREIGN KEY 約束
- 名稱類只存主檔（`IM_USER`／`IM_ROLE`／`IM_FLOW_STEP`／`IM_FORM_OPTION`），其他表只存代碼或鍵值；例外（規範 12）見各欄說明，例如 `IM_APP.SUP_NAME`（廠商名稱）、`IM_APP_CHECK_LIST.EXEC_USER_DESC`（執行人原文）、`IM_USER.DEPT_NAME` 與 `IM_APP.APPLY_DEPT_NAME`（無部門主檔）、`IM_APP.AREA_NAME` 與 `IM_APP.RACK_NAME`（舊資料自由文字，有退場條件）。廠商部分：舊資料的廠商只有文字（368 張單中 263 張有廠商名稱，如「晉泰科技」），施工廠商是否在公司廠商主檔 `CMN_SUP` 內待 DBA 確認（BACKLOG.md 第 76 項）
- 欄名後綴：代碼 `_CODE`；整數編號（版次、U 位、序號）`_NO`；數量 `_QTY` 並在欄名帶單位（`DUR_MS_QTY`、`FILE_BYTE_QTY`、`EST_HOUR_QTY`）；計次（筆數、次數、人數）`_CNT`；JSON `_JSON`；布林前綴 `IS_`
- 每張表都有共同欄位 `STATUS NUMBER(1)`（1 啟用／0 停用）與 `CREATE_DATE`（default SYSDATE）、`CREATE_BY`、`UPDATE_DATE`、`UPDATE_BY`（存工號）。`IM_APP` 的 STATUS 0 = 已刪除；`IM_USER` 的 STATUS 0 = 停用。預載資料與排程產生的資料 `CREATE_BY` 填 `SYSTEM`；`IM_ACCESS_LOG` 未登入請求的 `CREATE_BY` 填 `ANONYMOUS`（兩者皆對不到 `IM_USER`）
- 約束與索引命名：`PK_表名`、`UK_表名_欄名`、`CK_表名_欄名`（總長 ≤ 30，過長取簡稱）、`IND_表名_NN`

#### 身分
- `IM_USER` 使用者主檔：PK `USER_ID`（工號）；`LOGIN_ID` UK、CHECK 強制小寫；`USER_NAME`、`EMAIL`、`JOB_TITLE`、`DEPT_NAME`、`TEL`；`PWD_HASH`（NULL 表示尚未設定）、`IS_DFLT_PWD`、`PWD_CHANGE_DATE`。**使用者只能停用（STATUS 0）、不能刪除**。預載一個停用的代用帳號 `HIST_PAPER`（登入帳號 `hist_paper`、無密碼，不可登入），只供對不到申請人的紙本歷史單掛申請人
- `IM_ROLE` 角色主檔：PK `ROLE_ID`；`ROLE_NAME`、`SORT_NO`。預載 6 筆：admin 系統管理員、it_manager 資訊主管、dept_manager Infra 主管、idc_admin 機房管理員、governance 資訊治理、infra Infra 同仁
- `IM_USER_ROLE_MAP`：PK (`USER_ID`, `ROLE_ID`)；一人可多角色
- `IM_LOGIN_TOKEN` 登入權杖：PK `LOGIN_TOKEN_ID`；`TOKEN_HASH` UK（SHA-256，不存明文）；`USER_ID`、`LAST_USE_DATE`、`EXPIRY_DATE`、`USER_AGENT`；STATUS 0 = 已登出或撤銷。IX (`USER_ID`, `STATUS`)。remember-me 做法待定，見 BACKLOG.md 第 28 項

#### 簽核流程定義與簽核實例（共用簽核引擎，給未來模組重用）
- `IM_FLOW` 簽核流程主檔：PK `FLOW_ID`；`FLOW_NAME`、`FLOW_DESC`；`DOC_TYPE`（CR／ACCOUNT／INSPECTION）；`IS_DFLT`（同一 `DOC_TYPE` 只能有一個預設流程，且預設流程必須啟用）。預載 4 筆：`full`（P3／P4 預設，5 關）、`p2_high`（Infra 主管單關，允許代簽）、`p1_emergency`（資訊主管事後補核）、`imported`（93 張紙本歷史單專用，停用、新單不可選）
- `IM_FLOW_STEP` 流程關卡定義：PK `FLOW_STEP_ID`；`FLOW_ID`、`SEQ_NO`、`STEP_CODE`、`STEP_NAME`；`APPR_TYPE`（USER 時填 `USER_ID`／ROLE 時填 `ROLE_ID`，由 CHECK 保證二擇一）；`IS_NOTIFY_ONLY`、`IS_ALLOW_DELEG`；`STEP_MODE_CODE`（SEQUENTIAL 送審後依序簽核／POST_HOC 事後補核）。同一流程內啟用中的關卡 `SEQ_NO` 不得重複；修改流程時須在同一交易內先把舊列 STATUS 改 0、再新增新列（順序顛倒會違反唯一索引），不得改寫已被簽核實例參照的列
- `IM_APPR` 簽核實例（一次送審一筆）：PK `APPR_ID`；`DOC_TYPE`、`DOC_ID`（CR 時為 `IM_APP.APP_ID`）、`DOC_VER_NO`、`FLOW_ID`；`APPR_STATUS_CODE`（PENDING／APPROVED／REJECTED／RECALLED／CANCELLED）；`START_DATE`、`CLOSE_DATE`。同一文件同一版次只能有一筆狀態不是 RECALLED／CANCELLED 的實例（撤回後在同版次重送會再開一筆）
- `IM_APPR_STEP` 簽核實例關卡：PK `APPR_STEP_ID`；UK (`APPR_ID`, `SEQ_NO`)；`FLOW_STEP_ID`（關卡名稱、是否僅通知等屬性從 `IM_FLOW_STEP` 取，不重複存放）；`STEP_STATUS_CODE`（WAITING／PENDING／APPROVED／REJECTED／SKIPPED／CANCELLED）；狀態為 APPROVED／REJECTED 時 `USER_ID`（實際簽核人，代簽時為代簽人）與 `DECIDE_DATE` 必填；`MEMO`（CLOB）
- `IM_APPR_CAND_MAP` 關卡候選人：PK (`APPR_STEP_ID`, `USER_ID`)；送審當下固定候選人；IX (`USER_ID`) 供「我的待辦」查詢。退場條件：改成簽核時即時查角色池則移除本表
- `IM_APPR_STEP_ITEM` 逐項裁決：PK `APPR_STEP_ITEM_ID`；UK (`APPR_STEP_ID`, `ITEM_REF`)；`DECIDE_CODE`（APPROVED／REJECTED）；`MEMO`（CLOB）；`DECIDE_DATE`

#### 申請單
- `IM_APP_SEQ` 申請單編號序號：PK `APP_SEQ_ID`；UK (`SEQ_PREFIX`, `SEQ_DATE`)；`SEQ_PREFIX`（IM／HIST）；`LAST_NO`。每日每前綴獨立計數，以 `SELECT … FOR UPDATE` 取號，刪掉的號碼不再使用
- `IM_APP` 申請單主檔：PK `APP_ID`（申請單編號，`VARCHAR2(20)`）；`APP_TITLE`；`PRIO_CODE`（P1～P4）；`FLOW_ID`；`APPLY_USER_ID`；`SOURCE_CODE`（ONLINE／IMPORTED）；`CURR_VER_NO`；`ROW_VER_NO`（樂觀鎖）
  - **申請單狀態 `APP_STATUS_CODE` 共七種**：`DRAFT` 草稿、`IN_REVIEW` 審核中、`APPROVED` 核准、`IN_EXECUTION` 執行中、`PENDING_REVIEW` 待治理審查、`EXECUTED` 結案、`REJECTED` 退件
  - 刪除：軟刪除，`STATUS` 0 = 已刪除，此時 `DELETE_DATE`、`DELETE_USER_ID`、`DELETE_MODE_CODE`（ADMIN 管理員刪除／APPLICANT_PRE_REVIEW 申請人於送審前自行刪除）必填；`DELETE_REASON`
  - 基本資料：`APPLY_DATE`；`APPLY_DEPT_NAME`、`APPLY_TEL`、`APPLY_EMAIL`（填單當下的申請人資料快照）；`IS_SELF_EXEC`、`IS_SUP_EXEC`；`WORK_MODE_CODE`（ONSITE／REMOTE）、`REMOTE_METHOD`；`SUP_NAME`、`SUP_CNTCT`、`SUP_TEL`、`SUP_HEAD_CNT`
  - 作業內容：`WORK_SUBJ`、`IMPACT_DESC`（CLOB）、`WORK_DETAIL`／`RISK_DESC`／`ROLL_BACK_PLAN`（CLOB）、`OTHER_REASON`、`SCHED_START_DATE`／`SCHED_END_DATE`、`EST_HOUR_QTY`、`RESUB_MEMO`（CLOB，最近一次補件說明）
  - 位置：`LOC_SOURCE_CODE`（IMPACT／MANUAL）；**新單只寫 `SITE_ID`／`RACK_ID`** 與 `U_START_NO`／`U_END_NO`，區域與機櫃名稱由外部機櫃系統（Impact）帶出；`AREA_NAME`／`RACK_NAME`／`U_RANGE` 只承接舊資料的自由文字（退場條件：舊資料全數對應代碼並經 DBA 確認後移除）；`OMIT_REASON`
- `IM_APP_CATG_MAP`、`IM_APP_REASON_MAP`、`IM_APP_SCOPE_MAP`：PK (`APP_ID`, `FORM_OPTION_ID`)；設備類別子項、申請原因、影響範圍的多選勾選
- `IM_APP_CATG_OTHER`：PK `APP_CATG_OTHER_ID`；UK (`APP_ID`, `FORM_OPTION_ID`)；`OTHER_TEXT`（各類別的「其他」補充文字）
- `IM_APP_EQUIP` 異動設備：PK `APP_EQUIP_ID`；UK (`APP_ID`, `SEQ_NO`)；`EQUIP_NAME`、`ASSET_NO`（IX）、`MODEL_NO`、`SERIAL_NO`（IX）、`PURP_DESC`、`MGMT_IP`
- `IM_APP_PLAN_STEP` 作業計畫步驟：PK `APP_PLAN_STEP_ID`；UK (`APP_ID`, `SEQ_NO`)；`STEP_TEXT`
- `IM_APP_CHECK_LIST` 執行檢核表：PK `APP_CHECK_LIST_ID`；UK (`APP_ID`, `APP_VER_NO`, `SEQ_NO`)；`FORM_OPTION_ID`（檢核項，群組 CHECK_LIST 共 11 項）；`IS_DONE`、`DONE_DATE`；`USER_ID`（執行人工號）；`EXEC_USER_DESC`（執行人自由文字，`VARCHAR2(200 CHAR)`，供廠商、多人等非系統使用者；新舊單皆可填）。`USER_ID` 與 `EXEC_USER_DESC` 互斥、至多填一欄（CHECK 約束 `CK_IM_APP_CHECK_LIST_EXEC_USER` 保證）
- `IM_APP_EXEC` 執行結果：PK `APP_EXEC_ID`；UK (`APP_ID`, `APP_VER_NO`)；`ACTUAL_START_DATE`／`ACTUAL_END_DATE`；`RESULT_CODE`（DONE／DONE_ADJ／PARTIAL／NOT_DONE／CANCEL）；`IS_EXCPT`／`EXCPT_DESC`（CLOB）；`IS_FOLLOW_UP`／`FOLLOW_UP_DESC`（CLOB）；`EXEC_MEMO`（CLOB）；`USER_ID`（結案人）、`CLOSE_DATE`
- `IM_APP_VER` 歷史版次快照：PK `APP_VER_ID`；UK (`APP_ID`, `APP_VER_NO`)；`CLOSE_STATUS_CODE`（REJECTED／EXEC_REJECTED／GOV_RETURNED／RECALLED）；`VER_REASON`（CLOB）；`SNAP_DATE`；`FORM_JSON`（該版表單全文，NOT NULL）。目前版次不在本表
- `IM_APP_EVENT` 狀態事件：PK `APP_EVENT_ID`；IX (`APP_ID`, `EVENT_DATE`)；`APP_VER_NO`；`EVENT_CODE`（SUBMIT／RECALL／EXEC_REJECT／GOV_PASS／GOV_RETURN／RESUBMIT／DELETE／RESTORE）；`USER_ID`；`EVENT_DATE`；`MEMO`（CLOB）

#### 附件（各模組共用）
- `IM_ATTACH`：PK `ATTACH_ID`；`OWNER_TYPE`（APP／STEP／EVENT／AI）＋ `OWNER_ID`（IX，以文字存所屬物件鍵值）；`ORIG_FILE_NAME`、`STORE_FILE_NAME`；`FILE_PATH`（相對於附件根目錄的路徑，UK；CHECK 擋 `..`、斜線開頭與磁碟代號開頭，只是縱深防禦，應用層仍須 normalize 後確認位於根目錄下）；`FILE_BYTE_QTY`、`MIME_TYPE`、`SHA256_HASH`。檔案本體存檔案系統，本表只存路徑；下載經後端檢查權限

#### AI 審查（去留待定，見 BACKLOG.md 第 17 項；表先建以免匯入遺失）
- `IM_AI_REVIEW`：PK `AI_REVIEW_ID`（沿用舊檔 id）；IX (`APP_ID`, `CREATE_DATE`)；`MODEL_NAME`、`EFFORT_CODE`、`REVIEW_MODE_CODE`、`FALL_BACK_FROM`；`APP_VER_NO`、`APP_STATUS_CODE`；`APP_SNAP_JSON`、`SNAP_HASH`（key 排序 JSON + SHA-256）、`LEGACY_SHA1`；`RESULT_JSON`、`RESULT_SCHEMA_CODE`（v1／v2／v3）；`INPUT_TOKEN_CNT`、`OUTPUT_TOKEN_CNT`、`CACHE_READ_CNT`、`CACHE_WRITE_CNT`、`DUR_MS_QTY`、`STOP_REASON`；`SEND_DATE`、`SEND_TO_JSON`；`REVIEW_STATUS_CODE`（PENDING／DONE／FAILED）、`ERROR_TEXT`
- `IM_AI_REVIEW_MSG` 追問對話：PK `AI_REVIEW_MSG_ID`；UK (`AI_REVIEW_ID`, `SEQ_NO`)；`MSG_ROLE_CODE`（USER／ASSISTANT）；`MSG_TEXT`（CLOB）；`ASK_APP_VER_NO`；`MODEL_NAME`、`FALL_BACK_FROM`；用量欄位同 `IM_AI_REVIEW`

#### 範本、表單選項、系統參數
- `IM_TMPL` 範本：PK `TMPL_ID`（沿用舊 id）；`TMPL_NAME`；`FORM_JSON`；`OWNER_USER_ID`（IX）；`USE_CNT`、`LAST_USE_DATE`、`LAST_USE_USER_ID`
- `IM_FORM_OPTION` 表單選項主檔：PK `FORM_OPTION_ID`；UK (`GROUP_CODE`, `OPTION_CODE`)；`GROUP_CODE` 八群組——PRIO 優先等級（附說明、時限、流程說明、範例與對應 `FLOW_ID`）、CATG 設備類別、CATG_ITEM 類別子項（`UP_FORM_OPTION_ID` 指向所屬 CATG，只有本群組可填）、REASON 申請原因、SCOPE 影響範圍、CHECK_LIST 執行檢核項、EXEC_RESULT 執行結果、SIGN_ROLE 列印表單簽名欄角色；`OPTION_NAME`、`COLOR_CODE`、`SORT_NO`。預載自舊系統 form-schema.json，之後由 admin 在後台維護；停用（`STATUS` 0）的選項不在表單顯示，既有單據仍可帶出名稱
- `SYS_PARAM` 系統參數：PK (`PARAM_NAME`, `PARAM_VALUE`)；`PARAM_DESC`、`MEMO` 必填；`NAME`／`VALUE`／`DESC` 三欄依規範 11（避免保留字或易混淆的單字欄名，`NAME` 在列，`DESC` 為 Oracle 保留字）加 `PARAM_` 前綴，前綴與表名 `SYS_PARAM` 一致，說明欄用字典縮寫 `DESC`（規範 3）；`VALUE` 是 Oracle 關鍵字、與保留字 `VALUES` 易混淆，且為求 `SYS_PARAM` 的欄名一致一併加前綴；`MEMO` 不是保留字，沿用範例與字典原名。與規範範例工作表的 `NAME`／`VALUE`／`DESCR` 不同，此說明已寫進表說明隨 xlsx 送 DBA（BACKLOG.md 第 76 項）。多值參數一值一列。參數：`SITE_NAME`、`SITE_SHORT_NAME`、`TIME_ZONE`（Asia/Taipei）、`UPLOAD_MAX_MB`（預載 50）、`UPLOAD_MAX_FILES`（預載 30）、`FLOW_POLICY`（full_only／by_priority，預載 full_only）、`EXCLUDE_IP`（預設無）、`ADMIN_EMAIL`（部署時填入，不預載）

#### 郵件、存取紀錄、外部快取
- `IM_MAIL_OUTBOX` 郵件寄件匣：PK `MAIL_OUTBOX_ID`；IX (`MAIL_STATUS_CODE`, `CREATE_DATE`)；`TO_JSON`／`CC_JSON`／`BCC_JSON`；`MAIL_SUBJ`；`HTML_BODY`（CLOB）；`MAIL_STATUS_CODE`（PENDING／SENT／FAILED）；`TRY_CNT`；`ERROR_TEXT`；`SMTP_MSG_ID`；`SEND_DATE`；`META_JSON`
- `IM_ACCESS_LOG` HTTP 存取紀錄：PK `ACCESS_LOG_ID`；IX (`CREATE_DATE`)、(`USER_ID`, `CREATE_DATE`)；`USER_ID`、`LOGIN_ID`、`IP_ADDR`、`HTTP_METHOD`、`REQ_PATH`、`QUERY_TEXT`、`ACTION_CODE`、`HTTP_STATUS_CODE`、`DUR_MS_QTY`、`USER_AGENT`
- `IM_RACK_CACHE` 外部機櫃系統回應快取：PK `RACK_CACHE_ID`（快取鍵值，由程式定義）；`RESP_JSON`；`FETCH_DATE`

#### 測試專用表（V2）
- `IM_TX_TEST` 交易回滾測試：PK `TX_TEST_ID`（identity）；`TEST_KEY`（單次測試的隨機 UUID，IX）、`MEMO`；共同欄位同業務表。只供 `TransactionRollbackIT` 寫入並自行清理，平時應為空；退場條件：改用其他方式驗證交易時刪除

#### 版次表達
每一輪送審一筆 `IM_APPR`（帶 `DOC_VER_NO`），關卡在 `IM_APPR_STEP`（待辦與統計要用）；檢核表與執行結果依 `APP_VER_NO` 分版；補件時前一版表單全文凍結到 `IM_APP_VER.FORM_JSON`（只做整份比對），目前版次的內容在 `IM_APP` 與其子表。

#### 申請單編號
沿用舊系統格式：線上申請 `IM{yyyymmdd}-{NNN}`、紙本匯入 `HIST-{yyyymmdd}-{NNN}`；新單產號一律走 `IM_APP_SEQ`（前綴限 IM／HIST）。

### API 規格

全部 REST。後端（3202）的路徑前綴是 `/api`、沒有 context path；瀏覽器一律經殼 jar（3201）呼叫 `/infra_manager_web/api/v1/...`，由殼 jar 轉發到後端同名的 `/api/...`（例：`/infra_manager_web/api/v1/health` → `/api/health`），版本號只在殼 jar 這一層。下表列的是後端路徑。寫入需 session + CSRF token（session 如何穿過殼 jar 待定，見 BACKLOG.md 第 78 項）。「舊系統權限（對照用）」欄是 Node 版現況，**不是新系統的權限規則**；新系統的權限待 BACKLOG.md 第 26 項（未登入可看範圍）與第 30 項（舊漏洞處理）裁示後改寫本表。請求參數與回應格式各階段實作時補上。

| 新 REST API | 功能 | 舊系統權限（對照用） | Vue 頁面 |
|---|---|---|---|
| GET /api/health | 健康檢查 | — | — |
| GET /api/dashboard | 首頁統計、我的待辦 | 公開 | HomeView |
| POST /api/auth/login | 登入、remember-me、預設密碼導向改密碼 | 公開 | LoginView |
| POST /api/auth/password | 改密碼（≥6 字、不得等於帳號、改完撤銷 token） | 登入 | ChangePasswordView |
| POST /api/auth/logout | 登出 | 登入 | — |
| GET /api/apps?status&priority&source&mine&q&from&to | 列表（預設 90 天、待我簽核置頂、AI 摘要與費用） | 公開 | AppListView |
| POST /api/apps（multipart） | 建草稿、附件、選範本、套 workflowPolicy | 登入 | AppFormView |
| GET /api/apps/{id} | 檢視，含 9 個 canX 權限旗標 | 公開 | AppViewView |
| PUT /api/apps/{id} | 草稿編輯 | 申請人或 admin | AppFormView |
| POST /api/apps/{id}/submit | 送審（AI 啟用時須有 hash 相符報告） | 申請人或 admin | AppViewView |
| POST /api/apps/{id}/recall | 撤回成草稿（無人簽時） | 申請人 | AppViewView |
| POST /api/apps/{id}/decisions | 同意／退件（退件必填意見），可附檔 | 當前關卡候選人 | AppViewView |
| POST /api/apps/{id}/resubmit | 退件補件 → 新版次直接進審核 | 申請人 | AppFormView |
| PUT /api/apps/{id}/execution | 執行檢核表與實際紀錄；有填結果進 pending_review | idc_admin 或申請人 | ExecuteView |
| POST /api/apps/{id}/execution/reject | 執行端退回 → rejected | idc_admin 或申請人 | ExecuteView |
| POST /api/apps/{id}/governance-review | 治理審查：pass → executed、return → rejected | governance | ReviewView |
| DELETE /api/apps/{id} | 軟刪除，需 confirmId | admin 或申請人（條件） | AppViewView |
| POST /api/apps/{id}/ai-reviews | AI 審查（非同步 202；hash 沒變略過，force 強制） | 申請人（draft）或 admin | AppViewView |
| POST /api/ai-reviews/{rid}/messages | AI 報告追問 | 登入 | AppViewView |
| POST /api/ai-reviews/{rid}/send | 寄出 AI 報告 | admin | AppViewView |
| /api/templates（CRUD） | 範本列表、JSON、增刪改 | 列表公開；增刪改任何登入者 | TemplateListView、TemplateEditView |
| GET /api/stats | 日／週／月分桶統計 | 公開 | StatsView |
| GET /api/access-logs | 存取紀錄（最後 500 筆） | admin | AccessLogView |
| GET /api/rack-data、GET /api/rack-data/status、POST /api/rack-data/refresh | 機櫃盤點資料、快取狀態、強制刷新（是否需登入待定，見 `BACKLOG.md` 第 26 項；refresh 限 admin） | 前兩者公開；refresh admin | 機櫃選擇器 |
| /api/admin/users | 使用者新增、修改、停用（不提供刪除） | admin | AdminUsersView |
| /api/admin/workflows | 簽核流程 CRUD | admin | AdminWorkflowsView |
| /api/admin/mail | 外寄紀錄、失敗紀錄、信件內容、測試信 | admin | AdminMailView、MailDetailView、MailTestView |
| PUT /api/admin/settings | 系統設定 | admin | AdminSettingsView |
| GET /api/form-schema | 表單設定讀取（後台維護用的寫入 API 待定，見 BACKLOG.md 第 74 項） | admin | AdminFormSchemaView |

#### GET /api/health
- 請求：無參數
- 回應：HTTP 一律 200，`{"status": "UP", "db": "UP" | "DOWN", "time": "yyyy-MM-dd HH:mm:ss"}`。`status` 固定 `UP`（程式活著）；`db` 為對 `db.connect.itflow` 別名執行 `SELECT 1 FROM DUAL` 的結果；`time` 為台灣時間。DB 失敗不回錯誤碼，讓前端分辨「後端連不上」（請求本身失敗）與「後端活著但 DB 連不上」（200 且 `db` 為 `DOWN`）
- 結果快取 5 秒；快取過期時同時只有一條執行緒去查 DB，其他請求回舊值（第一次查詢、還沒有舊值時才等它查完）
- 首頁顯示：`status` 取到 → 後端服務「正常」；`db` 為 `UP` → 資料庫「正常」，否則「無法連線」；請求失敗 → 後端服務「無法連線」、資料庫「未知」並出 toast

#### 錯誤回應格式
後端所有錯誤回應都是 JSON，至少含 `message`，一律不帶內部細節（例外訊息、SQL、主機）：

| 狀態碼 | 情況 | 回應 |
|---|---|---|
| 400 | 文字欄位超過字數上限 | `{"message", "field", "max", "actual"}`；`field` 為 camelCase 的 JSON 欄名，`message` 如「「影響說明」超過 2000 字（目前 N 字）」 |
| 400 | JSON 格式錯誤、參數型別錯誤 | `{"message": "請求格式錯誤"}` |
| 400／404／405／406／415 | Spring MVC 自身的錯誤（缺必要參數、路徑不存在、方法不允許、媒體型別不支援等） | 照原狀態碼回 `{"message": "請求無法處理"}` |
| 413 | 請求本文超過上限（JSON 1 MB、單檔 50 MB、整個請求 500 MB） | `{"message": "請求內容過大"}` |
| 500 | DB 連線、SQL、交易例外 | `{"message": "資料庫存取失敗"}` |
| 500 | 其他未預期例外 | `{"message": "系統發生錯誤"}` |

經殼 jar 轉發時，後端的 4xx 目前會被殼 jar 轉成 500 `{"message":"後端服務呼叫失敗"}`，見「給範本維護者的註記」。

### 環境設定

各 jar 的設定檔是**真檔不進 git**（被 `.gitignore` 排除），進 git 的是同名 `.properties.example`；新增鍵時同步寫進 `.example`（只寫鍵與說明，不寫真實位址或帳密）。實際怎麼建立與填值見 `SETUP.md`。DB 的 jdbcUrl 與帳密不在本系統任何檔案或環境變數中，由連線資訊 API 依別名提供。

後端 `infra_manager_java/src/main/resources/`：

| 檔案 | 鍵 | 用途 |
|---|---|---|
| `application.properties` | `spring.application.name` | 專案名（`infra_manager_java`） |
| | `server.port` | 3202 |
| | `spring.servlet.multipart.resolve-lazily` | `true`：multipart 延後到端點取 `MultipartFile` 時才解析 |
| `config/database.properties` | `db.connect.itflow` | 本系統資料庫在連線資訊 API 的別名（必填；空白時啟動失敗） |
| `config/host.properties` | `rt-api.domain`、`db.connect.api.port`、`db.connect.api.path` | 連線資訊 API 的協定＋主機、port、路徑 |
| | `db.connect.api.domain.path` | 由前三個以 `${}` 組合，範本只讀這一個；四個鍵都要存在（值可空），全空時仍可啟動，第一次查 DB 才失敗 |

殼 jar `infra_manager_web/infra_manager_web/src/main/resources/`：

| 檔案 | 鍵 | 用途 |
|---|---|---|
| `application.properties` | `spring.application.name` | 專案名（`infra_manager_web`） |
| | `server.port` | 3201 |
| | `server.servlet.context-path` | `/infra_manager_web`（前端 `base` 與 axios `baseURL` 都以此為前綴） |
| `config/host.properties` | `backend.api.domain`、`backend.api.port`、`backend.api.path` | 後端 API 的協定＋主機、port、根路徑（本系統為 `/api`） |
| | `backend.api.domain.path` | 由前三個以 `${}` 組合，`ApiForwarder` 只讀這一個；四個鍵都要存在（值可空） |

secret 一律放環境變數，不寫進 repo 內任何檔案：

| 環境變數 | 用途 |
|---|---|
| `IM_SESSION_SECRET` | session 簽章用 secret |
| `IM_SMTP_*` | SMTP 連線與認證（細項 S8 定） |
| `IM_RACK_API_KEY` | Impact 機櫃盤點 API 金鑰 |
| AI 金鑰 | 存放方式待定，見 BACKLOG.md 第 18 項 |

本系統分批施工中，進度見 `BACKLOG.md`。
