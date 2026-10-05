# 待辦清單（BACKLOG）

> 給 Claude Code 讀的工作檔。維護規則：
> 1. 只放**未結案**項目。項目完成或拍板不做 → 立刻從對應分區移除／移入「已拍板不做」，
>    並在 `CHANGELOG.md` 記一行。歷史不留在本檔
> 2. 狀態用**分區**表達，不寫在標題的自由文字裡（避免「（已拍板 → ✅ 已完成）」這種
>    只有人看得懂、grep 必然失準的寫法）
> 3. 摘要一行講完。**只有累積了實質討論記錄的項目**才另開 `backlog/<ID>-<短名>.md`，
>    一句話就講完的項目不要建檔
> 4. **動工前必須先讀該項的詳情檔（若有）**，不得只憑摘要行開工
> 5. ID 只增不重用（移除的項目其編號不再指派給別人），方便對話中直接以編號指涉
>
> 規劃依據：`docs/plan/2026-10-02-rewrite-architecture.md`（歷史文件，內容有變以本檔與 `PRD.md` 為準）。
> ID 配置：1～15 = 施工階段 S1～S15；16～31 = 待裁示 ②～⑰；32～40 = architect 待確認；41～54 = 舊系統已知漏洞；55 起依序新增。

## 進行中

| ID | 摘要 | 詳情 |
|----|------|------|

### 交接狀態（每次停下回報時更新；無進行中項目時三欄留空）
- 下一步：
- 已改動：
- 卡住／待確認：

## 已拍板待實作

每階段 2～3 回合、可單獨驗收、做完 commit + push。S1～S15（第 1～15 項）依 ID 順序施工；第 59、68 項在 S1 之前做。S1 尚未開工。

| ID | 摘要 | 優先 | 詳情 |
|----|------|------|------|
| 59 | 依 DB 規範範例格式，從 V1 DDL 產出 Table List 與 Table Schema 給 DBA 審；一併列問 `SYS_PARAM` 命名：(1) NAME／VALUE／DESCR／MEMO 沿用範例欄名，與規範 11（單字欄名須加實體前綴）衝突，以範例或條文為準；(2) 範例用 `PARAM`／`DESCR`，字典是 `PARM`／`DESC`，本檔沿用範例寫法，以哪個為準 | 下一步 | — |
| 68 | 另寫一支授權 SQL，由 `rd_user` 執行，把 31 張表的 SELECT／INSERT／UPDATE／DELETE 授權給 `ap_user`；應用程式以 schema 前綴存取（是否加同義詞留待 DBA 審）。與第 59 項一起送 DBA 審 | 與第 59 項一起 | — |
| 1 | S1 骨架：Spring Boot 4.1 + Vue/Vite 單一 repo（Maven，以 mvnw 執行）、`db/oracle/V1__init_schema.sql` 接上 Flyway 成為第一支 migration、`/api/health`、Vue 首頁殼、`start-new.bat`（3201）、SETUP.md 補啟動方式與 px-secret-resolver 的 Azure Artifacts 設定；**開工前先裁示：測試用 Oracle 怎麼連（本機無 Docker，預設方案為單元測試 H2 Oracle 相容模式 + 開發期連公司 19c 測試 schema，DBA 支援與測試 schema 待確認）**。完成條件：瀏覽器開 3201 看到新首頁；`mvnw test` 通過 | 1 | — |
| 2 | S2 帳號與登入：`IM_USER`／`IM_ROLE`／`IM_USER_ROLE_MAP` 表已在 V1；users.json 匯入器（需第 60 項帳號工號對照表）；session + CSRF；登入／登出／改密碼。**開工前先裁示第 16 項（登入方式）**；若選 A 才做 scrypt 相容 encoder。完成條件：舊帳號能登入；預設密碼導向改密碼頁；匯入 22 人筆數一致 | 2 | — |
| 3 | S3 申請單匯入：申請單與簽核表已在 V1；申請單匯入器（時間轉換、撞號處理、舊版次 `FORM_JSON` 填法依第 69 項）；對帳報告。完成條件：依狀態分組筆數與來源一致；報告進 repo。開工前先量測第 34、35、36 項，並先裁示第 60 項（Eric、dept_manager 對應方式）、第 69 項（舊版次 `FORM_JSON` 填法）、第 73 項（附件根目錄）與第 75 項（已刪除單改號規則） | 3 | — |
| 4 | S4 唯讀列表與檢視：GET 列表（6 篩選、待我簽核置頂）、GET 檢視（關卡、附件下載權限）。完成條件：抽 5 張新舊畫面一致 | 4 | — |
| 5 | S5 表單設定與範本：`IM_FORM_OPTION`（V1 已建）；範本 CRUD（修改／刪除權限**開工前先裁示第 30 項**，涉及漏洞第 43 項）。完成條件：3 份範本可見；權限規則依第 30 項裁示結果驗證 | 5 | — |
| 6 | S6 新增、編輯草稿：AppForm（不含機櫃選擇器）、附件上傳、編號計數器。完成條件：建草稿成功；同日刪單再建不撞號（有測試） | 6 | — |
| 7 | S7 簽核引擎：送審、同意、退件、撤回；樂觀鎖；待辦數。完成條件：full 5 關走完；兩人同時簽其中一人 409 | 7 | — |
| 8 | S8 信件 outbox：`IM_MAIL_OUTBOX`、worker、樣板、admin 信件頁、測試信；定 `IM_SMTP_*` 細項。完成條件：三種信寄到測試信箱；SMTP 中斷 failed 可重寄 | 8 | — |
| 9 | S9 補件、版次、刪除：resubmit 寫 `IM_APP_VER` 快照；軟刪除。完成條件：補件後 v2，v1 簽核紀錄查得到 | 9 | — |
| 10 | S10 執行與治理審查：Execute、execute-reject、Review。完成條件：APPROVED→IN_EXECUTION→PENDING_REVIEW→EXECUTED；GOV_RETURN→REJECTED | 10 | — |
| 11 | S11 AI 概念驗證與審查（**開工前先裁示第 17 項 AI 去留與第 18 項金鑰存放**；選 B 則本階段改為「舊報告唯讀顯示」）：先驗證 SDK 在正式通道的 structured output／tool 退回／自訂 header（第 40 項）；再做非同步審查 + hash 把關。完成條件：同單審兩次第二次略過；refusal 有單元測試；**概念驗證失敗就停下回報** | 11 | — |
| 12 | S12 AI 對話、寄出、費用：messages、send、aiPricing、v1～v3 顯示。完成條件：三種舊格式正常顯示 | 12 | — |
| 13 | S13 機櫃選擇器：盤點快取 + U 位視覺化元件（可能超過 3 回合，必要時拆兩段）。完成條件：選範圍帶入設備列；Impact 斷線顯示舊快取 | 13 | — |
| 14 | S14 後台與統計：users、workflows、settings、form-schema、stats、`IM_ACCESS_LOG`（保留天數與清理排程依第 70 項）。完成條件：統計與舊系統同區間一致。開工前先確認第 38 項 | 14 | — |
| 15 | S15 服務化與切換演練：正式環境以 Docker 部署在 Rocky Linux 9.7（容器啟動與管理方式**開工前先裁示第 22 項**）、正式匯入演練、切換 runbook 進 SETUP.md（切換策略**開工前先裁示第 27 項**）。完成條件：演練機完整跑一次切換與回退 | 15 | — |

## 僅記錄未拍板

| ID | 摘要 | 提出日 | 詳情 |
|----|------|--------|------|
| 16 | ② 登入方式：A DB 帳密沿用舊密碼／B AD/LDAP。architect 建議 A 先上線，B 之後加 | 2026-10-02 | — |
| 17 | ③ AI 審查去留：A 保留移植／B 首版拿掉、舊報告唯讀。architect 建議 A（162 份在用） | 2026-10-02 | — |
| 18 | ④ AI 金鑰存放（正式環境為 Docker＋Rocky Linux 9.7）：候選 A 環境變數／B Docker secret 檔案掛載／C px-secret-resolver（若支援非 DB 憑證）／D AWS Secrets Manager。原 architect 建議的 DPAPI＋WinSW 方案因正式環境非 Windows 已失效，需重新分析；遷移時解出舊 `ai.key.enc` 另見第 33 項 | 2026-10-02 | — |
| 19 | ⑤ UI 元件庫：A Element Plus（後台元件齊、繁中、外觀制式）／B Naive UI（TS 原生、主題好調、社群小）；另比較過 PrimeVue（元件最多、曾大改版）、Vuetify（Material、樣式重）。architect 建議 A | 2026-10-02 | — |
| 21 | ⑦ 前端部署（正式環境為 Docker＋Rocky Linux 9.7）：A 打包進 jar／B 獨立 nginx 容器反向代理。原 IIS 選項因非 Windows 已失效；architect 當初（比較對象為 IIS）建議 A | 2026-10-02 | — |
| 22 | ⑧ 正式環境容器的啟動與管理方式：A docker compose／B systemd；開發期維持 jar + bat。原 WinSW 選項因非 Windows 已失效，需重新分析。S15 開工前裁示 | 2026-10-02 | — |
| 24 | ⑩ 三代 AI 報告：A 原樣存、Java 讀時正規化／B 原樣存 + Node 預轉顯示欄／C 全轉 v3。architect 建議 B | 2026-10-02 | — |
| 26 | ⑫ 未登入可看：A 全部須登入／B 維持公開。architect 建議 A | 2026-10-02 | — |
| 27 | ⑬ 切換策略：A 凍結一次切換（3201 UAT → 凍結舊系統 → 最後一次匯入 → 對帳 → 改 3200 → 舊系統唯讀）／B 並行寫入。architect 建議 A | 2026-10-02 | — |
| 28 | ⑭ remember-me：A 不搬 token、全部重登／B 移植舊 token。architect 建議 A | 2026-10-02 | — |
| 29 | ⑮ 遷移工具：A Java 匯入器／B Node 匯出 + Java 匯入。architect 建議 A（AI 部分借 Node） | 2026-10-02 | — |
| 30 | ⑯ 既有漏洞：A 一律修正／B 完全照搬。architect 建議 A，逐項列 CHANGELOG 並公告。清單見下方「舊系統已知漏洞」第 41～54 項 | 2026-10-02 | — |
| 32 | 待確認：正式環境 AI provider 是 `aws`（舊系統設定檔、SYSTEM_README:153）；兩台正式機是否一致？ | 2026-10-02 | — |
| 33 | 待確認：舊系統 `ai.key.enc` 用哪個 Windows 帳號、哪種範圍（CurrentUser／LocalMachine）加密？（遷移時要解出舊金鑰，與第 18 項新存放方式無關） | 2026-10-02 | — |
| 34 | 待確認：schedule.start/end、execution.actualStart/End 是不是不帶 Z 的牆上時間？（S3 前量測） | 2026-10-02 | — |
| 35 | 待確認：aiReviews.appSnapshot 欄位範圍是否與 computeAppHash 輸入一致？（S3 前量測） | 2026-10-02 | — |
| 36 | 待確認：附件總容量？（S3 前量測） | 2026-10-02 | — |
| 37 | 待確認：AD 帳號 ID 能否與現有 login_id 一一對應？（第 16 項登入方式選 B 時才需要） | 2026-10-02 | — |
| 38 | 待確認：舊系統統計頁是否純 CSS 圖？（S14 前確認） | 2026-10-02 | — |
| 39 | 待確認：Spring Boot 4.1 的 patch 版本，S1 動工當天到 spring.io 確認 | 2026-10-02 | — |
| 40 | 待確認：Java SDK 是否支援自訂 baseUrl + `anthropic-workspace-id` header（S11 驗證） | 2026-10-02 | — |
| 55 | 舊系統 P1「口頭報備、事後補單」（form-schema.json:9）系統沒有強制，新系統要不要強制未定 | 2026-10-02 | — |
| 56 | 舊系統 SMTP TLS 憑證驗證關閉（rejectUnauthorized false），移植時是否維持未定（S8 前決定） | 2026-10-02 | — |
| 57 | 帳號權限管理與機房巡檢兩個新模組：規格在舊專案 `D:\ai\Infra_Manager\機房巡檢\`，待既有功能切換上線後再排程（不是不做） | 2026-10-02 | — |
| 60 | 22 個舊帳號對工號的對照表，待使用者提供（alex 即陳儀仁）；S2 匯入與 S3 匯入都依賴它。比對規則：不分大小寫比對登入帳號；舊帳號匯入時轉小寫、保留原有空白（如 `Alan Kuo`）。範圍另含不在 22 人帳號檔的舊簽核人：`Eric`（111 筆核准、56 張單）與角色代碼 `dept_manager`（1 筆，`IM20260422-001`）；APPROVED 必有簽核人（`CK_IM_APPR_STEP_DECIDE`），S3 前須定對應方式（補工號或另立代用帳號） | 2026-10-05 | — |
| 61 | 請 DBA 把 29 個縮寫補進字典：APP application、APPR approval、ATTACH attachment、TMPL template、EQUIP equipment、EXEC execution、PRIO priority、VER version、MSG message、CAND candidate、SNAP snapshot、SCHED schedule、LOC location、PWD password、DFLT default、SUBJ subject、EST estimate、EXCPT exception、DUR duration、ORIG original、DELEG delegate、GOV governance、MGMT management、PURP purpose、CNTCT contact、RESUB resubmit、CURR current、REQ request、RESP response。`SYS_PARAM` 的 `PARAM`／`DESCR` 與字典 `PARM`／`DESC` 不一致的問題見第 59 項 | 2026-10-05 | — |
| 62 | 2000 CHAR 欄位是否改 CLOB：先查 `NLS_CHARACTERSET` 與 `MAX_STRING_SIZE`（查法見 SETUP.md「查資料庫字元集」），查完再決定。STANDARD + AL32UTF8 時 `VARCHAR2(2000 CHAR)` 約 1333 個中文字就超過 4000 bytes；V1 共 11 個 2000 CHAR 欄位 | 2026-10-05 | — |
| 63 | 風險：外部機櫃系統（Impact）的可用性。新單位置只存 `SITE_ID`／`RACK_ID`，區域與機櫃名稱由它帶出 | 2026-10-05 | — |
| 64 | 請 DBA 確認公司是否已有共用員工主檔；若有，`IM_USER` 會成為重複資料 | 2026-10-05 | — |
| 65 | 請 DBA 確認：DB 規範 Table List 範例的 `CMN_` 前綴與規範第 2 條「主檔不加前綴」矛盾，以哪個為準 | 2026-10-05 | — |
| 66 | 搬遷到 Azure DevOps：等使用者提供 repo 網址並說「搬」才動 | 2026-10-05 | — |
| 67 | px-secret-resolver 與環境變數 `IM_DB_URL`／`IM_DB_USER`／`IM_DB_PASSWORD` 的分工（哪些由套件解析、哪些仍走環境變數）未定，S1 前決定 | 2026-10-05 | — |
| 69 | 舊系統版次沒有表單內容快照，但 `IM_APP_VER.FORM_JSON` 為 NOT NULL，舊版次匯入時要填什麼未定（S3 前決定） | 2026-10-05 | — |
| 70 | `IM_ACCESS_LOG` 保留天數與清理排程未定（S14 前決定）。完成條件含：同步改寫 `IM_ACCESS_LOG` 表說明的「見 BACKLOG」（該句進 DB 資料字典；V1 已執行時需另開 migration） | 2026-10-05 | — |
| 72 | `IM_LOGIN_TOKEN` 表說明寫「取代舊系統記憶體 session」，與 PRD 的 server-side session + Spring Session JDBC 設計不一致；此表是 remember-me token 還是 session 本體未定。已裁示先保留 `IM_LOGIN_TOKEN` 表，與第 16 項（登入方式）、第 28 項（remember-me）一起決定 | 2026-10-05 | — |
| 73 | 附件根目錄的位置與設定方式未定（`IM_ATTACH.FILE_PATH` 存相對路徑；正式環境 Docker 容器要掛哪個 volume、由哪個設定鍵或環境變數指定根目錄）；S3 匯入附件並核對 sha256 時就要用到，S3 前決定 | 2026-10-05 | — |
| 75 | 4 張已刪除單與現存單同號（`IM20260505-002`、`IM20260505-003`、`IM20260506-002`、`IM20260918-003`，皆為不同的單），`APP_ID` 為主鍵不可重複，已刪除單改號匯入的規則未定：A 取同日下一個號碼（格式統一，但舊信件與紙本上的號碼對不到）／B 原號加後綴如 `IM20260505-002-D`（對得上舊信件，但格式不一致、回填 `IM_APP_SEQ` 時要排除）。其所有子表與關聯資料（含附件、AI 審查、事件紀錄、版次、簽核實例）的單號隨之改號。附件歸屬以各單 JSON 的 `attachments[].storedName` 判定、不以 `public/uploads/<單號>/` 資料夾判定（撞號的兩張單共用同一資料夾），改號後實體檔是否搬移一併決定。回填 `IM_APP_SEQ` 的順序：改號不從序號表取號則改號後回填，從序號表取號則先回填再改號。S3 前決定 | 2026-10-05 | — |
| 74 | 表單選項已定為後台可維護（`IM_FORM_OPTION`），但 PRD API 規格只有唯讀的 `GET /api/form-schema`；寫入 API 的端點、權限，以及可否刪除或只能停用選項未定，S14 前決定 | 2026-10-05 | — |

### 舊系統已知漏洞（依第 30 項 ⑯ 決定後處理）

| ID | 摘要 | 提出日 | 詳情 |
|----|------|--------|------|
| 41 | 編號撞號覆蓋既有單（apps.js:88-93，「當天檔案數 + 1」） | 2026-10-02 | — |
| 42 | 多處不用登入；`/uploads` 公開 static（server.js:69）；`/api/rack-data` 回設備管理 IP 與序號 | 2026-10-02 | — |
| 43 | 範本任何登入者可改刪別人的（routes/templates.js） | 2026-10-02 | — |
| 44 | POST `/:id/execute` 沒檢查角色 | 2026-10-02 | — |
| 45 | 補件跳過 AI 閘門；admin 能開編輯頁但送出被擋 | 2026-10-02 | — |
| 46 | notifyOnly、allowDelegate 沒實作；流程存檔丟掉 allowDelegate | 2026-10-02 | — |
| 47 | 簽核候選人建單時固定，之後異動不反映 | 2026-10-02 | — |
| 48 | 版次 history 沒有表單內容快照 | 2026-10-02 | — |
| 49 | 假 UTC（存台灣時間標 Z），信件再 +8h（完成信顯示 +16h；rememberToken 效期實際 30 天又 8 小時） | 2026-10-02 | — |
| 50 | login redirect 參數沒驗證（open redirect） | 2026-10-02 | — |
| 51 | 預設密碼 = 帳號小寫 | 2026-10-02 | — |
| 52 | accessLogger 高併發遺失；settings 非原子寫入 | 2026-10-02 | — |
| 53 | 首頁「我的待辦」含 draft，與 server.js 算法不一致 | 2026-10-02 | — |
| 54 | workflows.json 關卡 key 與名稱錯位（key `dept_manager` 名稱卻是「機房管理員」）；遷移以 name/role 為準 | 2026-10-02 | — |

## 已拍板不做

| ID | 摘要 | 決定日 | 理由（一句） |
|----|------|--------|------------|
