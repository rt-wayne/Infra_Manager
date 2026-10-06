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

每階段 2～3 回合、可單獨驗收、做完 commit + push。S2～S15（第 2～15 項）依 ID 順序施工。

| ID | 摘要 | 優先 | 詳情 |
|----|------|------|------|
| 2 | S2 帳號與登入：`IM_USER`／`IM_ROLE`／`IM_USER_ROLE_MAP` 表已在 V1；users.json 匯入器（需第 60 項帳號工號對照表）；session + CSRF；登入／登出／改密碼。**開工前先裁示第 16 項（登入方式）**；若選 A 才做 scrypt 相容 encoder。完成條件：舊帳號能登入；預設密碼導向改密碼頁；匯入 22 人筆數一致 | 2 | — |
| 3 | S3 申請單匯入：申請單與簽核表已在 V1；申請單匯入器（時間轉換、撞號處理、舊版次 `FORM_JSON` 填法依第 69 項）；對帳報告。完成條件：依狀態分組筆數與來源一致；報告進 repo。開工前先量測第 34、35、36 項，並先裁示第 60 項（Eric、dept_manager 對應方式）、第 69 項（舊版次 `FORM_JSON` 填法）、第 73 項（附件根目錄）與第 75 項（已刪除單改號規則） | 3 | — |
| 4 | S4 唯讀列表與檢視：GET 列表（6 篩選、待我簽核置頂）、GET 檢視（關卡、附件下載權限）。完成條件：抽 5 張新舊畫面一致 | 4 | — |
| 5 | S5 表單設定與範本：`IM_FORM_OPTION`（V1 已建）；範本 CRUD（修改／刪除權限**開工前先裁示第 30 項**，涉及漏洞第 43 項）。完成條件：3 份範本可見；權限規則依第 30 項裁示結果驗證 | 5 | — |
| 6 | S6 新增、編輯草稿：AppForm（不含機櫃選擇器）、附件上傳、編號計數器。前端送出前自行檢核字數（裁示 ①C，因殼 jar 把 4xx 轉成 500；規則同後端 `TextLength`：code point、換行先轉 LF、2000／20000 字）。完成條件：建草稿成功；同日刪單再建不撞號（有測試） | 6 | — |
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
| 40 | 待確認：Java SDK 是否支援自訂 baseUrl + `anthropic-workspace-id` header（S11 驗證） | 2026-10-02 | — |
| 55 | 舊系統 P1「口頭報備、事後補單」（form-schema.json:9）系統沒有強制，新系統要不要強制未定 | 2026-10-02 | — |
| 56 | 舊系統 SMTP TLS 憑證驗證關閉（rejectUnauthorized false），移植時是否維持未定（S8 前決定） | 2026-10-02 | — |
| 57 | 帳號權限管理與機房巡檢兩個新模組：規格在舊專案 `D:\ai\Infra_Manager\機房巡檢\`，待既有功能切換上線後再排程（不是不做） | 2026-10-02 | — |
| 60 | 22 個舊帳號對工號的對照表，待使用者提供（alex 即陳儀仁）；S2 匯入與 S3 匯入都依賴它。比對規則：不分大小寫比對登入帳號；舊帳號匯入時轉小寫、保留原有空白（如 `Alan Kuo`）。範圍另含不在 22 人帳號檔的舊簽核人：`Eric`（111 筆核准、56 張單）與角色代碼 `dept_manager`（1 筆，`IM20260422-001`）；APPROVED 必有簽核人（`CK_IM_APPR_STEP_DECIDE`），S3 前須定對應方式（補工號或另立代用帳號）。**2026-10-06 使用者裁示：對照表之前，S2 開發與測試先用 `wayne` 一個帳號配假資料（假工號、假姓名）即可，不等真實對照表**；真實對照表到了再做匯入 | 2026-10-05 | — |
| 61 | 請 DBA 把 29 個縮寫補進字典：APP application、APPR approval、ATTACH attachment、TMPL template、EQUIP equipment、EXEC execution、PRIO priority、VER version、MSG message、CAND candidate、SNAP snapshot、SCHED schedule、LOC location、PWD password、DFLT default、SUBJ subject、EST estimate、EXCPT exception、DUR duration、ORIG original、DELEG delegate、GOV governance、MGMT management、PURP purpose、CNTCT contact、RESUB resubmit、CURR current、REQ request、RESP response。使用者已裁示這 29 個縮寫維持現狀、隨第 76 項一併送 DBA | 2026-10-05 | — |
| 63 | 風險：外部機櫃系統（Impact）的可用性。新單位置只存 `SITE_ID`／`RACK_ID`，區域與機櫃名稱由它帶出 | 2026-10-05 | — |
| 64 | 請 DBA 確認公司是否已有共用員工主檔；若有，`IM_USER` 會成為重複資料 | 2026-10-05 | — |
| 65 | 請 DBA 確認：DB 規範 Table List 範例的 `CMN_` 前綴與規範第 2 條「主檔不加前綴」矛盾，以哪個為準 | 2026-10-05 | — |
| 66 | 搬遷到 Azure DevOps：等使用者提供 repo 網址並說「搬」才動 | 2026-10-05 | — |
| 69 | 舊系統版次沒有表單內容快照，但 `IM_APP_VER.FORM_JSON` 為 NOT NULL，舊版次匯入時要填什麼未定（S3 前決定） | 2026-10-05 | — |
| 70 | `IM_ACCESS_LOG` 保留天數與清理排程未定（S14 前決定）。完成條件含：同步改寫 `IM_ACCESS_LOG` 表說明的「見 BACKLOG」（該句進 DB 資料字典；V1 已執行時需另開 migration） | 2026-10-05 | — |
| 72 | `IM_LOGIN_TOKEN` 表說明寫「取代舊系統記憶體 session」，與 PRD 的 server-side session + Spring Session JDBC 設計不一致；此表是 remember-me token 還是 session 本體未定。已裁示先保留 `IM_LOGIN_TOKEN` 表，與第 16 項（登入方式）、第 28 項（remember-me）一起決定 | 2026-10-05 | — |
| 73 | 附件根目錄的位置與設定方式未定（`IM_ATTACH.FILE_PATH` 存相對路徑；正式環境 Docker 容器要掛哪個 volume、由哪個設定鍵或環境變數指定根目錄）；S3 匯入附件並核對 sha256 時就要用到，S3 前決定 | 2026-10-05 | — |
| 75 | 4 張已刪除單與現存單同號（`IM20260505-002`、`IM20260505-003`、`IM20260506-002`、`IM20260918-003`，皆為不同的單），`APP_ID` 為主鍵不可重複，使用者已裁示改號規則為 B：原號加後綴 `-D`（如 `IM20260505-002-D`），不從序號表取號，先改號、後回填 `IM_APP_SEQ`，回填時排除帶 `-D` 後綴的單號。其所有子表與關聯資料（含附件、AI 審查、事件紀錄、版次、簽核實例）的單號隨之改號。附件歸屬以各單 JSON 的 `attachments[].storedName` 判定、不以 `public/uploads/<單號>/` 資料夾判定（撞號的兩張單共用同一資料夾），實體檔使用者已裁示為 A：匯入時把已刪除單的附件複製（不是搬移）到新的 `-D` 資料夾，資料夾名一律等於單號、無例外。共 5 個檔：已刪除的 `IM20260505-003` 的 `1777960881217_20251210_012806829_iOS.jpg` → `IM20260505-003-D`；已刪除的 `IM20260918-003` 的 `1789717904288_PA升級作業計畫.docx`、`1789718818701_paste-2026-09-18T08-06-17-1.png`、`…08-06-18-1.png`、`1789718818702_paste-2026-09-18T08-06-25-1.png` → `IM20260918-003-D`（來源皆在舊 `public/uploads/<單號>/`，與現存同號單共用資料夾）；`IM20260505-002`、`IM20260506-002` 無附件。待做：S3 匯入腳本實作此複製，並讓附件路徑欄指向新資料夾 | 2026-10-05 | — |
| 76 | 等 DBA 回覆：(1) 審 `docs/db/Table_List_Schema.xlsx`（31 張表、417 個欄位，使用者裁示照現狀送出；`SYS_PARAM` 已依規範條文把欄名改為 `PARAM_NAME`／`PARAM_VALUE`／`PARAM_DESC`（`MEMO` 不是保留字，沿用範例原名），與規範範例工作表的 `NAME`／`VALUE`／`DESCR` 不同；此說明已寫進 `SYS_PARAM` 的表說明，隨 xlsx 送出）；(1b) 順帶問 DBA 一題：`IM_APP.SUP_NAME` 存廠商文字（舊資料 368 張單中 263 張有廠商名稱、沒有代碼，同一廠商有不同寫法，如「晉泰科技」47 次、「晉泰」40 次），機房施工廠商是否在公司廠商主檔 `CMN_SUP`（PX 廠編）內？若在，是否要改存 `SUP_ID`；(2) 第 61 項的 29 個縮寫（使用者裁示維持現狀）一併送。稽核性質的表（`IM_ACCESS_LOG`、`IM_APP_EVENT`、`IM_APP_VER`）使用者已裁示維持四種權限都給（清理排程見第 70 項、改號匯入見第 75 項需要 UPDATE／DELETE），不送 DBA。回覆若要改欄名，改動範圍是 V1 DDL、PRD，並須重產 xlsx（`node db/tools/gen_table_doc.js`）；改表名則授權檔也要改。`ap_user` 存取方式已裁示維持以 schema 前綴存取、不建同義詞，不送 DBA。S1 不受阻擋，但 DDL 在正式環境執行前須有回覆 | 2026-10-05 | — |
| 74 | 表單選項已定為後台可維護（`IM_FORM_OPTION`），但 PRD API 規格只有唯讀的 `GET /api/form-schema`；寫入 API 的端點、權限，以及可否刪除或只能停用選項未定，S14 前決定 | 2026-10-05 | — |
| 77 | S2 正式 DAO 的 SQL 要怎麼帶 schema 前綴（`rd_user.表名`，已裁示不建同義詞）：識別字不能用 `:name` 綁定，必然是字串串接，與後端 README §8「SQL 沒有字串拼接」字面衝突。候選：A 設定檔 key（如 `db.schema.itflow`）啟動時白名單驗證後注入成常數／B 寫死常數／C 從 `ALL_TABLES` 查（TransactionRollbackIT 目前做法，2026-10-06 複審裁示 ①A 加白名單 `[A-Z][A-Z0-9_$#]{0,127}`）。定案時一併在 README §8 補一句「schema 前綴例外」。S2 開工前決定 | 2026-10-06 | — |
| 78 | 殼 jar 的 `ApiForwarder` 轉發時不帶瀏覽器 cookie 與 header（範本設計），後端 3202 看不到 session；S2 的登入／session／CSRF 要怎麼設計（session 放殼 jar 由它轉工號給後端、或殼 jar 透傳 cookie、或 token header）未定，S2 開工前決定。2026-10-06 architect 已分析（A 範本改透傳 cookie／B session 放殼 jar／C 權杖放 JSON／D 本專案攔截器透傳 cookie，建議 D＋同時送 A 變更單），**方向（A／B／C／D）待使用者裁示**；已裁示：登入過期辨識採「失敗時再查 `/api/auth/me`」（②A）、cookie 名加 `IM_` 前綴（`IM_SESSION`、`IM_XSRF`、header `X-IM-XSRF`，④A）、session 閒置逾時 8 小時；使用者已確認本系統有自己的登入頁、公司沒有統一入口或 SSO；3201 是否 https、3202 是否只開給殼 jar：使用者不知道，先以保守值（http、可被直連）實作並在 PRD 標待確認；技術棧規範 xlsx 有無認證規定待主對話自查 | 2026-10-06 | — |
| 79 | 殼 jar 的 `ApiForwarder` 只轉 JSON（`get`／`post` 固定 JSON 本文與回應），附件上傳（multipart）與下載（二進位串流）無法經它轉發；S4 下載／S6 上傳前要決定：殼 jar 加 multipart／串流轉發、或瀏覽器直連 3202、或其他。與第 80 項一起看 | 2026-10-06 | — |
| 80 | 殼 jar 本身沒有請求本文上限，且把整份 JSON 讀進記憶體再轉發；⑤A 的 1 MB／50 MB／500 MB 只保護後端 3202。要確認正式環境 3202 是否只允許殼 jar 來源（防火牆或 Docker 網路），並決定殼 jar 要不要也加上限（會動到範本 `ApiForwarder`，須與範本維護者協調）。另兩件要給範本維護者的事：`ApiForwarder` 把後端 4xx 一律轉成 500「後端服務呼叫失敗」（裁示 ①A：提變更單讓它原樣轉發 4xx）、業務層自建 TransactionManager 超出範本 README「不支援交易」、範本 pom 附帶的 `mssql-jdbc` 本系統用不到（裁示 ⑦A：不自行移除）。三件都已寫進 PRD「給範本維護者的註記」，剩下的是實際送出變更單 | 2026-10-06 | — |
| 82 | 殼 jar `ApiForwarder` 只有 `get`／`post`，PRD 規劃的 PUT／DELETE 端點（`PUT /api/apps/{id}`、`PUT /api/apps/{id}/execution`、`DELETE /api/apps/{id}`、`PUT /api/admin/settings`，以及範本、使用者、流程三組 CRUD）轉不到後端。S4 前裁示：全改 POST（PRD API 規格表改寫）或向範本維護者送變更單加 put／delete（可與第 78 項的變更單一起送）。2026-10-06 裁示 ③A：先登記、S4 前再定 | 2026-10-06 | — |

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
| 19 | ⑤ UI 元件庫（原候選 Element Plus／Naive UI／PrimeVue／Vuetify）：不使用元件庫，沿用前端範本 `main.css`（S1 裁示 ④B） | 2026-10-06 | 照公司前端範本 web_template_3.5 的 `main.css` 與版面規範 |
