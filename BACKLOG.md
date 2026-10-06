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
| 4 | S4 唯讀列表與檢視：GET 列表（6 篩選、待我簽核置頂）、GET 檢視（關卡、附件下載權限）、後端附件下載端點；前端列表頁與檢視頁。2026-10-06 裁示：列表／檢視須登入（①A）、手寫種子 SQL 當驗收資料（②A）、附件只做後端端點、殼 jar 透傳留 S6（③B）、列表不做 AI 欄位（④A）。完成條件：`db/oracle/sample/S4_sample_apps.sql` 種子資料在列表頁與檢視頁全欄位正確顯示（原「抽 5 張新舊畫面一致」移到第 3 項 S3 完成後驗收） | — |
| 6 | S6 新增、編輯草稿：AppForm（不含機櫃選擇器）、附件上傳、編號計數器、殼 jar 串流透傳與本文上限。2026-10-06 開工裁示「全部依建議」（①A 串流透傳、②B 逐檔上傳、③A 殼 jar 擋 1 MB／51 MB、④B 前端不預檢字數、⑤A 只有申請人能編輯、⑥A 樂觀鎖、⑦A 位置只做「不適用」、⑧A 副檔名白名單、⑨A 先做回合二）；回合順序 二 a → 二 b → 一（S4 驗收後）→ 三 → 四。完成條件：建草稿成功；同日刪單再建不撞號（有測試）；經 3201 上傳下載 50 MB 檔 sha256 一致 | [backlog/6-s6-draft-form.md](backlog/6-s6-draft-form.md) |

### 交接狀態（每次停下回報時更新；無進行中項目時三欄留空）
- 下一步：**S4 結案驗收（裁示 ④A）**：等使用者 (1) 在測試 DB 重跑種子 SQL（已匯入過的先跑檔尾清除指令；904／905／906 的簽核關卡已改）(2) 提供 test-runner 用的測試帳號 → 派 `test-runner` 用真實登入看列表頁與 901～908 檢視頁全欄位，量 1024 與 1007 寬無水平捲軸、console 無錯誤（只讀，不得寫 DB）。通過即 S4 結案、移出本區。S4 驗收時以當下 `main` 為準（S6 回合二會在檢視 API 多加 `rowVerNo`／`formOptionId`，畫面不變）
- S6（第 6 項）：施工計畫與交接在 [backlog/6-s6-draft-form.md](backlog/6-s6-draft-form.md)；S4 驗收前只做回合二（不碰殼 jar 轉發器）
- 已改動（回合三，commit `bc17054` 快照＋`4f11e3b` 審查修正）：前端 `views/AppViewView.vue`、路由 `/apps/:id`（`router/names.ts` 具名路由）、`types/app.ts`（`AppDetail` 等）、`api/apps.ts` `getApp`、`utils/format.ts`、`StatusPill.vue`、列表頁單號改連結、測試 `AppViewView.spec.ts`（前端測試 25 → 35）；後端 `AppDao.findOptions` 類別「其他」補充改獨立一段取回＋`AppDaoTest`（後端測試 197 → 198）；種子 904／905／906 改 5 關全核准。階段末 code review 已跑：阻擋 1 項（B1）已修、複驗時點已裁示（②A：核准前第 5 關）、非阻擋登記第 95 項；test-runner 已用假資料（未登入後端、攔截 API）驗過 1024 寬兩頁無水平捲軸
- 已改動（回合二，commit `e94fc06`）：殼 jar `config/SecurityHeadersFilter`＋測試（殼 jar 測試 29 → 31）；前端 `types/app.ts`、`api/apps.ts`、`components/StatusPill.vue`／`AppPager.vue`、`views/AppListView.vue`、路由 `/apps`、`HomeView` 功能卡片、`api/http.ts`（`isUnauthorized`、略過 `/auth/login`）、`composables/useAuth.ts`（401 處理去重）、測試 `AppListView.spec.ts`／`unauthorized.spec.ts`（前端測試 15 → 25）
- 已改動（回合一，commit `b5e7d74`）：後端 `controller/dao/model/service` 的 `changerequest` 子套件、`util/TaiwanTime`、`web/ApiNotFoundException`／`ApiBadRequestException`、`ApiExceptionHandler`、`application.properties.example`（新鍵 `im.attach.root`，**使用者的真實 `application.properties` 要自己補這個鍵**，不補時下載端點回 500）、種子 SQL、6 個測試類別（單元測試 161 → 197）
- 要記得的事：種子 SQL 用 SQL*Plus 以 `ap_user` 跑 `@db/oracle/sample/S4_sample_apps.sql`，開頭會問「要看到待我簽核的使用者工號」（填 wayne 的工號）；它只建附件索引列、沒有實體檔，對它們打下載端點回 404「附件檔案不存在」是預期；檔尾有註解掉的清理 DELETE 區塊。`AppDaoIT` 只由 `mvnw verify` 執行（連真實測試 DB）。IDE 的 Java 擴充套件會跟 `mvnw clean package` 搶寫 `target/classes`，偶發 `NoClassDefFoundError`，重跑一次即可。殼 jar 若要含最新前端，`npm run build` 後還要重打殼 jar；打包前要先停掉正在跑的 3201／3202（jar 被鎖住會 clean 失敗；`start-new.bat` 會自動停自己的 jar）；`npm run build` 會清掉殼 jar `src/frontend/` 內的 `.gitkeep`，commit 前 `git restore` 它。測試 DB 的 wayne 密碼已被使用者在瀏覽器改過（不再是預設值），要重設回預設就重跑 sample 匯入
- 卡住／待確認：S4 結案驗收等使用者提供測試帳號與重跑種子 SQL（見「下一步」）；第 93 項（登出失敗時是否仍導回登入頁）等使用者回 ①A／①B；第 94 項（正式主機是否還有其他 web 服務）等使用者確認

## 下一階段（細節調整）

> 2026-10-06 使用者裁示：先把原有功能的基本改寫做出來，細節調整全部記在這一區、基本功能完成後再做。
> 衝刺期每個階段結束才跑一次 `code-reviewer`，review 的非阻擋項一律登記到此區、不當場修。

| ID | 摘要 | 提出日 | 詳情 |
|----|------|--------|------|
| 90 | 帳號匯入器 code review 延後的 12 項（2026-10-06，裁示全部延後）：② 工號格式檢核（任何欄位含 `"` 整檔失敗＋字元集正則，需使用者補工號格式規則）；③ web 模式帶 `im.import.users` 時啟動即失敗並提示正確指令（約 15 行＋測試）；④ `@Transactional` 回滾測試（mock `PlatformTransactionManager`，驗 commit 一次、第二筆失敗 rollback）＋對測試 schema 實跑一次含故意失敗、確認 DB 無殘留；⑤ `UserImportRunnerTest` 每次 run 前 `exitCode.set(-1)`；⑥ 用 `ApplicationContextRunner`／`WebApplicationContextRunner` 釘住 runner 的兩個條件註解（web＋參數無 bean、非 web＋參數有 bean、非 web 無參數無 bean）；⑦ 非 UTF-8（cp950）檔案 catch `CharacterCodingException` 改訊息「不是 UTF-8 編碼，請用 Excel 的『CSV UTF-8』另存」；⑧ JSON 解析錯誤只印例外類別與 `e.getLocation()` 行列，不印 `getOriginalMessage()`（可能帶欄位值）；⑨ SETUP 第 4 步加註：重跑時 users.json 只留缺漏的帳號（否則全員密碼再被重設）；⑩ SETUP 匯入流程改為「停服務 → 匯入 → 啟服務 → 通知」（已登入 session 不受停用影響，與第 89 項同根）；⑪ `ImportFileReader.stripBom` 與兩個測試檔的 BOM 字元改 `"﻿"` 跳脫（共三處）；⑫ 對照表有、users.json 沒有的帳號印 warn；寫入 0 筆時 warn 並結束碼改 1；⑬ 移除 `ImportSummary.written()` 與無效的 `row.setIsDfltPwd(1)`＋其測試斷言（SQL 寫死 `IS_DFLT_PWD=1`）。另：runner 用 `System.exit`，日後若有 `@SpringBootTest` 以 none 模式又帶 `im.import.users` 會殺掉測試 JVM，寫測試時避開 | 2026-10-06 | — |
| 92 | S2 回合三前端 code review 非阻擋 6 項（2026-10-06，衝刺規則登記不修；⑥⑦ 已於 S4 回合二做完）：③ `safeRedirect` 加固——拒絕 `\x00-\x1f`、`\x7f` 與空白字元（`/\t/evil.example` 目前通過檢查，瀏覽器會剝掉 tab 變 `//evil.example`；現在只走 hash 路由所以不可利用，日後若改用 `window.location` 導頁就是 open redirect），更穩的做法是改用 `router.resolve(target)` 要求 `matched.length > 0` 並回 `fullPath`；測試補 `/\evil`、`/%2F%2Fevil`、`javascript:alert(1)`、`/\t/evil`、`/login/`、`/LOGIN`；④ 守衛與 `safeRedirect` 用字串比對 `/login`，`/login/`、`/LOGIN` 不會被導回首頁——路由加 `name`、守衛比 `to.name`；⑤ 沒有「找不到頁面」路由，打錯網址畫面空白——加 `/:pathMatch(.*)*`；⑨ `readCookie` 的 `decodeURIComponent` 遇壞格式丟 URIError，之後每個請求都失敗——包 try/catch 回 null；⑩ `HomeView` 與 `ChangePasswordView` 的 `doLogout` 重複——收進 `useAuth`；⑪ 測試缺口：改密碼頁、http 攔截器（是否真帶 `X-IM-XSRF`）、`errorMessage` 都沒測（401 處理器已在 S4 回合二補測）——至少補「攔截器從 cookie 讀值帶 header」一條。基本功能完成後做 | 2026-10-06 | — |
| 95 | S4 階段末 code review 非阻擋 5 項＋新舊檢視頁差異 4 項（2026-10-06，衝刺規則登記不修；B1／N3／N4／N8／N9 已於 S4 回合三修完）：**N1** 沒有候選人列的關卡（含草稿預覽的每一關）簽核人欄顯示「—」，舊系統這裡顯示指定簽核人——後端要帶 `assigneeName`；**N2** 作業類別沒分組，看不出子項屬於哪個類別——後端 `findOptions` 已 JOIN 上層選項，只差把上層名稱（`upName`）帶出來；**N5** 附件下載的 `Content-Length` 用 DB 記的大小，與實體檔不同時下載會被截斷或卡住——改用 `Files.size(file)`，與 DB 值不同時寫 warn；**N6** 附件 MIME 直接照 DB 值回（`AppController.java:71-76`、`AttachmentService.java:66`），目前有 `Content-Disposition: attachment`＋`nosniff` 擋著不可利用，S6 開放下載前改白名單；**N7** 檢視 API 的 JSON 形狀沒有 controller 層測試，前後端型別一致只靠人工比對，`uRange` 這類「單字母＋大寫」欄名最容易出事；**G4** 事件分級：舊頁列出四級卡片（定義、處理時效、審核流程、範例）並標出選中的那級（舊 `view.ejs:66-81`），新頁只顯示等級名稱；**G5**「在機房盤點查看」連結：舊頁用盤點系統網址加站點／機櫃組連結（舊 `view.ejs:128-154`），新頁沒有、後端也沒提供網址——補時網址在後端組，前端只接受 http(s) 開頭；**G6** 關卡與事件的附件：舊頁掛在簽核欄該關的「意見／附件」格（舊 `view.ejs:273-277`），新頁集中在附件區、只標「簽核關卡」「狀態事件」，看不出是哪一關哪一次，簽核欄表頭也少了「／附件」；**G7** 頁尾 6 條注意事項（舊 `view.ejs:369-379`）沒有。另 G8（「不適用」原因代碼轉中文）併入第 3 項 | 2026-10-06 | — |
| 91 | 真實帳號匯入（22 人）：等第 60 項對照表到位後，照 `SETUP.md`「匯入使用者」匯入並核對筆數；S2 完成條件中的「真對照表到位後匯入 22 人筆數一致」移到此項驗收 | 2026-10-06 | — |

## 已拍板待實作

每階段 2～3 回合、可單獨驗收、做完 commit + push。S2～S15（第 2～15 項）依 ID 順序施工。**衝刺期 S3（歷史申請單匯入）延後到基本功能之後，施工順序改為 S4 → S6 → S7 → S9 → S10 → S5 → S8 → 其餘（S2 已於 2026-10-06 完成）。**

| ID | 摘要 | 優先 | 詳情 |
|----|------|------|------|
| 3 | S3 申請單匯入：申請單與簽核表已在 V1；申請單匯入器（時間轉換、撞號處理、舊版次 `FORM_JSON` 填法依第 69 項）；對帳報告。完成條件：依狀態分組筆數與來源一致；報告進 repo。開工前先量測第 34、35、36 項，並先裁示第 60 項（Eric、dept_manager 對應方式）、第 69 項（舊版次 `FORM_JSON` 填法）、第 73 項（附件根目錄）與第 75 項（已刪除單改號規則）。**S4 的「抽 5 張新舊畫面一致」驗收（2026-10-06 裁示 ②A）併入本項，匯入完成後對列表頁與檢視頁執行**。匯入時設備位置「不適用」原因要把舊代碼轉中文（`not_idc`、`rack_not_ready`，對照舊 `view.ejs:127`；S4 code review G8），否則檢視頁顯示英文代碼。匯入後要把 `IM_APP_SEQ.LAST_NO` 回填到各日期的最大號（S6 編號計數器只增不減，不回填則當天新單撞舊單；S6 開工分析範圍外發現） | 3 | — |
| 5 | S5 表單設定與範本：`IM_FORM_OPTION`（V1 已建）；範本 CRUD（修改／刪除權限**開工前先裁示第 30 項**，涉及漏洞第 43 項）。完成條件：3 份範本可見；權限規則依第 30 項裁示結果驗證 | 5 | — |
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
| 17 | ③ AI 審查去留：A 保留移植／B 首版拿掉、舊報告唯讀。architect 建議 A（162 份在用） | 2026-10-02 | — |
| 18 | ④ AI 金鑰存放（正式環境為 Docker＋Rocky Linux 9.7）：候選 A 環境變數／B Docker secret 檔案掛載／C px-secret-resolver（若支援非 DB 憑證）／D AWS Secrets Manager。原 architect 建議的 DPAPI＋WinSW 方案因正式環境非 Windows 已失效，需重新分析；遷移時解出舊 `ai.key.enc` 另見第 33 項 | 2026-10-02 | — |
| 22 | ⑧ 正式環境容器的啟動與管理方式：A docker compose／B systemd；開發期維持 jar + bat。原 WinSW 選項因非 Windows 已失效，需重新分析。S15 開工前裁示 | 2026-10-02 | — |
| 24 | ⑩ 三代 AI 報告：A 原樣存、Java 讀時正規化／B 原樣存 + Node 預轉顯示欄／C 全轉 v3。architect 建議 B | 2026-10-02 | — |
| 27 | ⑬ 切換策略：A 凍結一次切換（3201 UAT → 凍結舊系統 → 最後一次匯入 → 對帳 → 改 3200 → 舊系統唯讀）／B 並行寫入。architect 建議 A | 2026-10-02 | — |
| 29 | ⑮ 遷移工具：A Java 匯入器／B Node 匯出 + Java 匯入。architect 建議 A（AI 部分借 Node） | 2026-10-02 | — |
| 30 | ⑯ 既有漏洞：A 一律修正／B 完全照搬。architect 建議 A，逐項列 CHANGELOG 並公告。清單見下方「舊系統已知漏洞」第 41～54 項 | 2026-10-02 | — |
| 32 | 待確認：正式環境 AI provider 是 `aws`（舊系統設定檔、SYSTEM_README:153）；兩台正式機是否一致？ | 2026-10-02 | — |
| 33 | 待確認：舊系統 `ai.key.enc` 用哪個 Windows 帳號、哪種範圍（CurrentUser／LocalMachine）加密？（遷移時要解出舊金鑰，與第 18 項新存放方式無關） | 2026-10-02 | — |
| 34 | 待確認：schedule.start/end、execution.actualStart/End 是不是不帶 Z 的牆上時間？（S3 前量測） | 2026-10-02 | — |
| 35 | 待確認：aiReviews.appSnapshot 欄位範圍是否與 computeAppHash 輸入一致？（S3 前量測） | 2026-10-02 | — |
| 36 | 待確認：附件總容量？（S3 前量測） | 2026-10-02 | — |
| 37 | 待確認：AD 帳號 ID 能否與現有 login_id 一一對應？（第 84 項改 AD/LDAP 登入時才需要） | 2026-10-02 | — |
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
| 73 | 附件根目錄的位置與設定方式未定（`IM_ATTACH.FILE_PATH` 存相對路徑；正式環境 Docker 容器要掛哪個 volume、由哪個設定鍵或環境變數指定根目錄）；S3 匯入附件並核對 sha256 時就要用到，S3 前決定 | 2026-10-05 | — |
| 75 | 4 張已刪除單與現存單同號（`IM20260505-002`、`IM20260505-003`、`IM20260506-002`、`IM20260918-003`，皆為不同的單），`APP_ID` 為主鍵不可重複，使用者已裁示改號規則為 B：原號加後綴 `-D`（如 `IM20260505-002-D`），不從序號表取號，先改號、後回填 `IM_APP_SEQ`，回填時排除帶 `-D` 後綴的單號。其所有子表與關聯資料（含附件、AI 審查、事件紀錄、版次、簽核實例）的單號隨之改號。附件歸屬以各單 JSON 的 `attachments[].storedName` 判定、不以 `public/uploads/<單號>/` 資料夾判定（撞號的兩張單共用同一資料夾），實體檔使用者已裁示為 A：匯入時把已刪除單的附件複製（不是搬移）到新的 `-D` 資料夾，資料夾名一律等於單號、無例外。共 5 個檔：已刪除的 `IM20260505-003` 的 `1777960881217_20251210_012806829_iOS.jpg` → `IM20260505-003-D`；已刪除的 `IM20260918-003` 的 `1789717904288_PA升級作業計畫.docx`、`1789718818701_paste-2026-09-18T08-06-17-1.png`、`…08-06-18-1.png`、`1789718818702_paste-2026-09-18T08-06-25-1.png` → `IM20260918-003-D`（來源皆在舊 `public/uploads/<單號>/`，與現存同號單共用資料夾）；`IM20260505-002`、`IM20260506-002` 無附件。待做：S3 匯入腳本實作此複製，並讓附件路徑欄指向新資料夾 | 2026-10-05 | — |
| 76 | 等 DBA 回覆：(1) 審 `docs/db/Table_List_Schema.xlsx`（31 張表、417 個欄位，使用者裁示照現狀送出；`SYS_PARAM` 已依規範條文把欄名改為 `PARAM_NAME`／`PARAM_VALUE`／`PARAM_DESC`（`MEMO` 不是保留字，沿用範例原名），與規範範例工作表的 `NAME`／`VALUE`／`DESCR` 不同；此說明已寫進 `SYS_PARAM` 的表說明，隨 xlsx 送出）；(1b) 順帶問 DBA 一題：`IM_APP.SUP_NAME` 存廠商文字（舊資料 368 張單中 263 張有廠商名稱、沒有代碼，同一廠商有不同寫法，如「晉泰科技」47 次、「晉泰」40 次），機房施工廠商是否在公司廠商主檔 `CMN_SUP`（PX 廠編）內？若在，是否要改存 `SUP_ID`；(2) 第 61 項的 29 個縮寫（使用者裁示維持現狀）一併送。稽核性質的表（`IM_ACCESS_LOG`、`IM_APP_EVENT`、`IM_APP_VER`）使用者已裁示維持四種權限都給（清理排程見第 70 項、改號匯入見第 75 項需要 UPDATE／DELETE），不送 DBA。回覆若要改欄名，改動範圍是 V1 DDL、PRD，並須重產 xlsx（`node db/tools/gen_table_doc.js`）；改表名則授權檔也要改。`ap_user` 存取方式已裁示維持以 schema 前綴存取、不建同義詞，不送 DBA。S1 不受阻擋，但 DDL 在正式環境執行前須有回覆 | 2026-10-05 | — |
| 74 | 表單選項已定為後台可維護（`IM_FORM_OPTION`），但 PRD API 規格只有唯讀的 `GET /api/form-schema`；寫入 API 的端點、權限，以及可否刪除或只能停用選項未定，S14 前決定。同時把 PRD `GET /api/form-schema` 的權限欄拆成「讀：登入者／寫：admin」（S6 開工分析範圍外發現；S6 另做一支登入者可讀的表單選項 API 供 AppForm 用） | 2026-10-05 | — |
| 80 | 殼 jar 本文上限已於 2026-10-06 S6 開工裁示 ③A 定案（轉發器內擋 1 MB／51 MB，於第 6 項回合一實作），本項只剩以下部署議題。正式環境 3202 是否只允許殼 jar 來源（防火牆或 Docker 網路）：使用者 2026-10-06 答不知道，後端一律自行驗 session、不信任殼 jar 轉來的身分，PRD 標待確認。同一個部署議題：殼 jar 到 3202 目前是 http 明文（`host.properties.example` 的範例），`IM_SESSION` 與 `X-IM-XSRF` 在內網明碼傳輸；若 3202 不是只開給殼 jar、或兩者不在同一台／同一個 Docker 網路，要改 https 或其他保護，S15 部署前定。另一個同時定的部署議題（2026-10-06 第二輪 code review B3）：3201 的 https 若由前面的反向代理終結，殼 jar `getRemoteAddr()` 拿到的是代理 IP，轉給後端的 `X-Forwarded-For` 與存取紀錄都會變成代理 IP；要記真實來源得設 `server.forward-headers-strategy` 並限定只信任該代理的 IP，不得無條件信任。第三個（2026-10-06 第三輪 code review ⑤）：殼 jar 的 JDK HttpClient 沒指定代理、用 JVM 預設 `ProxySelector`，正式後端是內網主機名或 IP、不在預設不代理清單（`localhost|127.*|[::1]`）內，維運若用 `-Dhttp.proxyHost` 或 `JAVA_TOOL_OPTIONS` 啟動殼 jar，打後端的請求（含 `IM_SESSION`、`X-IM-XSRF`）會經公司代理；候選：`BackendClientConfig` 加 `.proxy(HttpClient.Builder.NO_PROXY)` 固定直連、或部署文件規定啟動參數要把後端主機放進 `http.nonProxyHosts`。給範本維護者的事剩兩件：業務層自建 TransactionManager 超出範本 README「不支援交易」、範本 pom 附帶的 `mssql-jdbc` 本系統用不到（裁示 ⑦A：不自行移除）；原「`ApiForwarder` 4xx 轉 500 的變更單」因自建轉發器不再需要。兩件都在 PRD「給範本維護者的註記」，剩下的是實際送出 | 2026-10-06 | — |
| 83 | 殼 jar 資安回應標頭剩兩件沒做（`security.md` A05；nosniff、`X-Frame-Options: DENY`、`Referrer-Policy` 已於 S4 回合二由 `SecurityHeadersFilter` 加上，裁示 ①A）：① **CSP**：S4 回合二量過——build 後的 `index.html` 只有外部 `<script type="module" src>` 與 `<link rel="stylesheet">`、沒有 inline script／`style=""` 屬性；Vue 的 `:style` 綁定（列表頁優先等級底色）走 CSSOM，不受 `style-src` 限制。所以 `default-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'` 這類嚴格政策應可直接用，要在 S4 檢視頁做完後實測一次（DevTools console 無 CSP 違規）再上；② **`Cache-Control` 透傳**：轉發器只回 `Content-Type` 與 `IM_` cookie，會丟掉後端的 `Cache-Control: no-store`（含個資的 API 回應可能被瀏覽器快取）——轉發器多透傳這一個 header。範本原本就這樣，不是轉發器改版造成。**② 併入第 6 項回合一（S6 裁示 ①A 的回應 header 白名單含 `Cache-Control`）**，① 基本功能完成後做 | 2026-10-06 | — |
| 84 | AD／LDAP 登入（原第 16 項選項 B）：DB 帳密版（S2）上線後再評估要不要加；要做時先確認第 37 項（AD 帳號能否對應 `login_id`），並決定 AD 帳號與 `IM_USER` 的對應與停用同步方式 | 2026-10-06 | — |
| 85 | remember-me 與 `IM_LOGIN_TOKEN`：S2 不做 remember-me（裁示 ③A；原第 28 項定為不移植舊 token），表先保留不使用。表說明「取代舊系統記憶體 session，支援多機與重啟不掉線」與現行 Tomcat session 設計不符，要改成「remember-me 權杖（尚未啟用）」——V1 已在測試 DB 執行，須另寫 `COMMENT ON TABLE` 的 SQL 檔、同步改 V1 檔文字並重產 xlsx（`node db/tools/gen_table_doc.js`）；原定 S2 回合二一併做，2026-10-06 使用者裁示延後到「下一階段（細節調整）」。日後要做 remember-me 時再定效期、撤銷規則與第 87 項的 `USER_AGENT` | 2026-10-06 | — |
| 86 | 多機部署的 session 共享：目前 Tomcat 記憶體 session（裁示 ④A），後端重啟即全員登出、不能多台；正式環境若超過一個後端實例或要重啟不掉線，接 Spring Session JDBC（4.1.1 在 Boot BOM 內）並建 session 表（DDL 由開發方提供、使用者以 `rd_user` 執行）。S15 部署前決定 | 2026-10-06 | — |
| 87 | 瀏覽器 `User-Agent` 不轉發、不記錄（裁示 ⑤A）：殼 jar 轉發器只轉 `IM_` cookie、`X-IM-XSRF`、`Content-Type`／`Accept`，後端只看得到殼 jar 的 Java 用戶端；`IM_LOGIN_TOKEN.USER_AGENT` 與 `IM_ACCESS_LOG` 因此沒有瀏覽器資訊。日後要記得把 `User-Agent` 加進轉發清單；與第 85 項、S14 存取紀錄一起看 | 2026-10-06 | — |
| 88 | 登入暴力嘗試防護：`POST /api/auth/login` 沒有次數限制或帳號鎖定（舊系統也沒有），bcrypt 比對耗時只是自然減速；**`POST /api/auth/password` 的「舊密碼錯誤」同樣沒有次數限制**（S2 回合二 code review 第 1 項，裁示 A 登記）——拿到別人 session 的人可用它猜出對方真正的密碼，把「session 被盜」升級成「密碼被盜」。候選：**按帳號計數、login 與 password 兩個端點共用同一個計數器**（否則攻擊者改走沒鎖的那一支），失敗 N 次後暫時鎖定（需記錄失敗次數）、或殼 jar 層限流。`security.md` A07。S15 部署前決定 | 2026-10-06 | — |
| 89 | 停用帳號或移除角色後，已登入的 session 不會立即失效（S2 回合一 code review 第 11 項，裁示 ③A 登記）：權限在登入當下固定存進 session，之後不再查 `IM_USER.STATUS` 與角色表；閒置逾時 8 小時且無絕對逾時，持續操作的人可無限期保持登入。**改密碼也只重建本次請求的 session，同帳號在其他瀏覽器／裝置的 session 不會失效**（S2 回合二 code review 第 2 項，裁示 A 併入本項）：被盜的 session 不會因受害者改密碼而失效（OWASP 建議改密碼後讓其他 session 失效）；另一個瀏覽器若是用預設密碼登入的，會卡在「請先修改預設密碼」、再改一次又得到「舊密碼錯誤」，只能重新登入。候選：每個請求重查 `STATUS` **並一併比對 `PWD_CHANGE_DATE`**（同一次 DB 重查解決停用與改密碼兩件事；多一次 DB 查詢）、維護 session 清單供管理員踢人（接 Spring Session 後較容易，見第 86 項）、加絕對逾時。**帳號匯入器把帳號或角色設 `STATUS=0` 時同樣不影響已登入者**（S2 回合二匯入器 code review 第 ⑩ 項，作業面對策在第 90 項 ⑩）。S14 使用者管理（停用功能）開工時一起做 | 2026-10-06 | — |
| 93 | 登出請求失敗時前端仍導回登入頁（S2 回合三 code review 第 8 項，待裁示 ①）：網路斷、殼 jar 502 或 CSRF 403 時，前端把狀態設成未登入並導回登入頁、toast「登出失敗」約 3 秒，但後端 `IM_SESSION` 仍有效；共用電腦上下一個人重新整理就進前一個人的帳號。選項 A 維持現狀、toast 改為「登出未完成，請關閉瀏覽器」（操作一致，但 toast 可能沒看到）；選項 B 失敗時留在原頁顯示錯誤讓使用者重試（不會誤以為已登出，但後端掛掉時離不開首頁，要改 PRD）。code-reviewer 建議 A。不影響基本功能，裁示後再動 | 2026-10-06 | — |
| 94 | 瀏覽器 cookie 依主機名隔離、不依 port（S2 回合三 code review 範圍外發現）：若 3201 所在主機的其他 port 還跑著別的服務（例如舊系統 3200），瀏覽器會把 `IM_SESSION`／`IM_XSRF` 一併送給那些服務，那些服務也能寫入同名 cookie 蓋掉本站的（cookie tossing）。第 80 項（殼 jar 本文上限與 3202 來源限制）沒涵蓋這點。需使用者確認正式主機上是否還有其他 web 服務；本機開發時舊系統 3200 與新系統 3201 同時跑就符合此情境（舊系統不會寫 `IM_` cookie，實際無害）。S15 部署前決定 | 2026-10-06 | — |

### 舊系統已知漏洞（依第 30 項 ⑯ 決定後處理）

| ID | 摘要 | 提出日 | 詳情 |
|----|------|--------|------|
| 41 | 編號撞號覆蓋既有單（apps.js:88-93，「當天檔案數 + 1」） | 2026-10-02 | — |
| 42 | 多處不用登入；`/uploads` 公開 static（server.js:69）；`/api/rack-data` 回設備管理 IP 與序號 | 2026-10-02 | — |
| 43 | 範本任何登入者可改刪別人的（routes/templates.js） | 2026-10-02 | — |
| 44 | POST `/:id/execute` 沒檢查角色 | 2026-10-02 | — |
| 45 | 補件跳過 AI 閘門；admin 能開編輯頁但送出被擋（後半：S6 裁示 ⑤A 草稿只有申請人能編輯，第 6 項回合二 b 完成時結案） | 2026-10-02 | — |
| 46 | notifyOnly、allowDelegate 沒實作；流程存檔丟掉 allowDelegate | 2026-10-02 | — |
| 47 | 簽核候選人建單時固定，之後異動不反映 | 2026-10-02 | — |
| 48 | 版次 history 沒有表單內容快照 | 2026-10-02 | — |
| 49 | 假 UTC（存台灣時間標 Z），信件再 +8h（完成信顯示 +16h；rememberToken 效期實際 30 天又 8 小時） | 2026-10-02 | — |
| 50 | login redirect 參數沒驗證（open redirect） | 2026-10-02 | — |
| 51 | 預設密碼 = 帳號小寫（S2 裁示沿用此預設值，但匯入時全員重設並以 `PWD_OK` 權限閘門強制首次改密碼；是否改成隨機初始密碼於第 30 項裁示時再看） | 2026-10-02 | — |
| 52 | accessLogger 高併發遺失；settings 非原子寫入 | 2026-10-02 | — |
| 53 | 首頁「我的待辦」含 draft，與 server.js 算法不一致 | 2026-10-02 | — |
| 54 | workflows.json 關卡 key 與名稱錯位（key `dept_manager` 名稱卻是「機房管理員」）；遷移以 name/role 為準 | 2026-10-02 | — |

## 已拍板不做

| ID | 摘要 | 決定日 | 理由（一句） |
|----|------|--------|------------|
| 19 | ⑤ UI 元件庫（原候選 Element Plus／Naive UI／PrimeVue／Vuetify）：不使用元件庫，沿用前端範本 `main.css`（S1 裁示 ④B） | 2026-10-06 | 照公司前端範本 web_template_3.5 的 `main.css` 與版面規範 |
| 28 | ⑭ remember-me：不移植舊 token，S2 也不做 remember-me 功能（裁示 ③A；日後要做見第 85 項） | 2026-10-06 | 全員密碼重設後舊 token 無意義；功能本身延後 |
