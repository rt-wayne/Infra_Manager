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

## 進行中

| ID | 摘要 | 詳情 |
|----|------|------|
| 46 | 緊急存取（SA §5.11、第二階段）：送出即生效、24 小時補核准、申請人回報、主管補核准／退回、異常結案、J03 逾期通知、首頁兩卡、鈴鐺類別 3、審查異常旗標閉環；使用者裁示 17 項見詳情檔 | `backlog/46-緊急存取.md` |

### 交接狀態（每次停下回報時更新；無進行中項目時三欄留空）
- 下一步：**S5 結案**（2026-10-13；回合 1 commit `8427781`、回合 2 commit `ec2335b`、瀏覽器實測 36 項全過，見詳情檔第 6 節 S5 列）。下一步 **S6 異常結案**（結案發現 NO_CHANGE／CHANGED、EMG_CLOSE_MODIFY 歷史、旗標；詳情檔第 4 節 S6 列），動工前先列需求理解與檔案清單等使用者確認。S5 審查另記：`TodoView` 也有「彈窗卸載後 emit 被丟掉」同款問題，不在第 46 項範圍，尚未登記成待辦（要不要開新項由使用者決定）。兩件待使用者自行做的事：(1) 以 RD_USER 再跑一次 `db\im_acct_insp_phase2_emg.sql`，讓 S4b-2 的 COMMENT 段生效（可重跑、只改說明）(2) 收信確認「【緊急存取】EA20261013-001 已退回」與「【緊急存取】EA20261013-002 已送出，待您補核准」兩封有到（允許名單只有 2410305）。測試 DB 現況：EA20261013-001 已退回（版本 2）、EA20261013-002 已補核准（版本 2，回報「沒有異動」）、EA20261013-003 已補核准（沒有異動）、EA20261013-004 已退回（異動 TEST，尚未結案，可給 S6 實測用）、EA20261013-005 已補核准（異動 TEST、t18_host_c／AR20261007-004、Brian／AR20261007-023）；AR20261012-004（Wayne 名下主機帳號 TEST）與 -005 的另兩個帳號帶審查異常旗標（來源 EMG，清除待 S10）；兩個服務（後端 8989 PID 20412 是 `8427781`、殼 jar 8787 PID 26216 是 `ec2335b` 的前端）。重新打包前要先停服務（執行中的 JVM 鎖住 jar，repackage 會失敗）；重啟用 PowerShell `Start-Process`（`cmd start` 會卡住）；實跑在 auto mode 下會被權限分類器擋
- 已改動（未 commit）：無
- 卡住／待確認：S4-⑦（偏離 SA）已裁示維持，SA 附錄 D 已加一列，告知資訊治理組由使用者自行處理；EMERGENCY→EMG 補進 DB 規範縮寫字典由使用者自行處理（裁示 ㉘）；規範 xlsx 不進 repo（裁示 2026-10-13，commit 一律用明確路徑 `git add`）

## 已拍板待實作

| ID | 摘要 | 優先 | 詳情 |
|----|------|------|------|

## 僅記錄未拍板

| ID | 摘要 | 提出日 | 詳情 |
|----|------|--------|------|
| 8 | `/me` 回 401 以外的錯誤時，需權限的頁面導向無權限頁而非顯示 U09「查詢失敗，請稍後再試」，要整頁重新整理才恢復（`useSession.ts:59-64`） | 2026-10-10 | — |
| 11 | IM_ACCT_ADMIN_LOG.CONTENT_JSON 宣告 VARCHAR2(2000 CHAR)，受單欄 4000 bytes 限制實際約只容 1333 個中文字；目前只存切換對象顯示名不會超過，日後寫入更多內容前須處理 | 2026-10-10 | — |
| 12 | DAO 以參數顯式綁定 NULL 會蓋過 DB DEFAULT（IS_PRIV、EXPIRY_TYPE_CODE、IS_UNIQUE_CONF 等必填欄）；目前 Service 都先填值所以沒出事，日後新路徑漏填會直接 ORA-01400 | 2026-10-10 | — |
| 13 | 顯示名兩種算法不一致：`AcctReqDao.nameExpr`（第 42 行，用於第 56-58、181 行與 `AttachDao.java:43`）不 trim、無工號退回；快照用的 `Texts.displayName`（`Texts.java:35-42`）有 trim 與英文名 → 中文名 → 工號退回 | 2026-10-10 | — |
| 14 | 第二階段功能：定期審查、統計頁、帳號與巡檢匯出、資產分類與巡檢項目維護（緊急存取已拆出為第 46 項）；目前只有權限碼與預留欄位，無程式 | 2026-10-10 | — |
| 16 | 原始規格書缺件：子 README 與 `.example` 註解引用 `spec_im_acct_insp_v2.0.md`、變更單 #1～#4，但 repo 與使用者手上都沒有；PRD 不引用 | 2026-10-10 | — |
| 17 | `im_acct_insp_web\README.md` 第 2 節「兩包結構」表仍是範本初始描述：說 Controller 目錄只有 `package-info.java`、`src\types\` 與 `src\views\` 只有 `.gitkeep`、router 的 routes 是空陣列，實際都已有程式（4 個型別檔、10 個畫面、14 個 path） | 2026-10-10 | — |
| 18 | 登入查詢後才被改成停用（STATUS＝0）的帳號，該次登入仍取得 token 與回應中的權限清單；下一次呼叫 API 即因 `TokenIdentityProvider` 檢查 STATUS 而回 A03，攻擊面接近零 | 2026-10-10 | — |
| 19 | 人工鎖定（DB 直接寫 LOCK_DATE）只擋之後的登入，不擋已發出的 token；`TokenIdentityProvider` 每次請求只查 STATUS 不查 LOCK_DATE。要不要讓鎖定也立即作廢既有 token 屬設計決策 | 2026-10-10 | — |
| 20 | 門禁卡新增複驗退回後再次回報、卡號沒變時不應更新主表帳號名稱（`RequestService.java:465` 的 `else if`），此分支沒有測試；只有換卡號會更新的測試（`RequestServiceTest.java:231`） | 2026-10-10 | — |
| 21 | `im_acct_insp_交接文件.md`（初始交付文件）第 692 行寫「空檔視同未上傳」，未含「副檔名不符仍回 E14」的例外，與現行程式不一致；先決定這份文件是否持續隨程式維護 | 2026-10-10 | — |
| 22 | J02（每週逾期彙總信）以「本週有無任何通知紀錄」判斷是否已寄，寄送失敗（FAIL）也寫紀錄，所以本週信全部寄失敗時同週不會再重寄（`JobService.java:299`、`NotificationService.java:296,303`）；J01 同一套判斷。SETUP 已註明後果，要不要改程式未拍板 | 2026-10-10 | — |
| 23 | SETUP 第 7 節的 J01／J02 觸發 curl 尚未實跑（使用者裁示不在第 6 項實跑；登入與登出已於 2026-10-11 實跑），日後有安全的測試時機時補跑並核對回應。可用測試 DB 的測試帳號 2410305（密碼同工號；2026-10-11 已加 DC_ADMIN、INFRA_MGR、GOV、GOV_MGR、IT_HEAD、SYS_ADMIN、INSP_STAFF、ACCT_REGISTRY 八個角色與全部 8 個帳號類別的執行人，Email 為使用者本人信箱；使用者裁示這些權限不移除） | 2026-10-10 | — |
| 24 | token 標登出（系統登出或 SETUP 第 6 節第 4 段外洩處理）COMMIT 前已通過 token 檢查的進行中請求都會照常完成（每個請求各一次）；其中正卡在更新最後存取時間的那類，程式本可依影響 0 筆擋下卻沒擋（`TokenIdentityProvider.java:57-59`）。日後補縫時不能只檢查更新回傳值，那只擋得住後面那類 | 2026-10-11 | — |
| 29 | 切換身分再切回後在首頁按登出，會多送一個不帶 token 的 `GET /home/stats` 並回 401（第 28 項瀏覽器實測發現，3 次登出穩定重現於此路徑）；推測 `resetSession` 把 `viewKey` 歸 0 讓首頁重建。畫面無差別，只多一行 console error；401 處理會寫 `loginNotice`，之後開登入頁會不會冒出「登入已逾時」未驗證。同類：第 28 項第二、三輪實測各見一次登出瞬間 `GET /todos` 回 401（第三輪是在待辦頁登出，重跑 3 次未重現）；推測 `AppLayout.vue` 換頁時的 `watch(route.fullPath) → refreshBadges` 在「token 已清、版面未卸載」瞬間觸發，未證實 | 2026-10-11 | — |
| 34 | 正式環境走 HTTPS：使用者確認（2026-10-11）正式環境能用 HTTPS（公司憑證或反向代理）；目前 `SETUP.md` 範例網址為 `http://`，密碼與 token 明文傳輸。做法（殼 jar 自己掛憑證或前面放反向代理）、憑證來源未定；完成後補 HSTS 標頭（第 30 項不含） | 2026-10-11 | — |
| 35 | `im_acct_insp_web_frontend\vite.config.ts` 第 22 行 proxy 目標寫死 `http://localhost:8080`（與殼 jar `application.properties.example` 的 port 相同），但本機實際的 `application.properties`（不進版控）是 8787，第 6 行註解要求兩者一致；照本機設定跑 `npm run dev` 時 API 會連不上。第 30 項審查發現 | 2026-10-11 | — |
| 36 | 測試 DB 的附件 14～18、20 在 DB 有紀錄但實體檔不存在，後端下載回 500「附件檔案不存在」，真實附件下載無法實測（第 30 項瀏覽器實測改以模擬 PDF 驗證下載）。要補檔、刪紀錄或維持現狀未定 | 2026-10-11 | — |
| 37 | 帳號歷史彈窗在視窗高 900px 時，底部「關閉」鈕被畫面底緣切掉一半（按得到、關得掉）；第 30 項瀏覽器補測發現，與安全標頭無關 | 2026-10-11 | — |
| 41 | `PRD.md` 表單欄位表「使用人聯絡方式」列寫「灰底範例『#1614 或 Email』」，但畫面（`RequestFormView.vue` 的 `#holderCntct`）是一般 placeholder「請填寫使用人的分機、電話或 Email（外部廠商請務必填寫）」、無灰底；第 40 項審查時發現的既有落差，要改 PRD 照現況或改畫面照 PRD 未定 | 2026-10-11 | — |
| 44 | `SETUP.md` 第 71 行寫 `npm run build` 會清掉 `im_acct_insp_web\src\frontend\.gitkeep`、要用 `git checkout` 還原，且「該目錄只有 `.gitkeep` 時殼 jar 也能建置」；但使用者已確認刪除該 `.gitkeep`（第 43 項動工前快照 `83a5f62` 一併 commit），現在 git 還原不回來，新 clone 沒有該目錄時殼 jar 能否建置未驗證。`im_acct_insp_web\README.md` 第 32 行「平時只有 `.gitkeep`」同樣過時。第 43 項 test-runner 發現。補充（第 46 項 S3 審查，2026-10-13）：`im_acct_insp_web\im_acct_insp_web\.gitignore` 第 12 行 `!src/frontend/.gitkeep` 例外仍在但檔已不在 git；單純補回沒用，每次 `npm run build`（vite 清空輸出目錄）都會再刪，要一併改建置設定或改用其他方式保留目錄 | 2026-10-12 | — |
| 45 | 處理中的按鈕沒有停用外觀：`src/assets/app.css` 第 84 行 `.btn-link` 無 `:disabled` 樣式（只有 `.btn:disabled` 半透明，第 79 行），附件檔名鈕與下載鈕在處理中停用時外觀、游標都不變，下載大檔時看不出在處理。改法一行 `.btn-link:disabled { opacity: .55; cursor: default; }`，但屬全站共用樣式，要目視確認其他畫面的停用連結鈕。第 43 項 code-reviewer 發現，使用者裁示（2026-10-12）留到下一批 | 2026-10-12 | — |
| 31 | 一個分頁收到 401 清 token 時，其他分頁被連帶整頁重新整理後落在空白登入頁，看不到「登入已逾時」原因（`loginNotice` 只在記憶體，`useSession.ts`）；可在重新整理前把訊息暫存 sessionStorage、登入頁讀出即刪。使用者裁示（2026-10-11）先只記錄：8 小時到期一天只遇一次 | 2026-10-11 | — |
| 47 | IM_ACCT_JOB_RUN 沒有清理機制；J03（每 15 分鐘）上線後每天約 96 列、一年約 3.5 萬列。第 46 項 architect 發現 | 2026-10-12 | — |
| 48 | `db\im_acct_insp_create_table.sql` 與 `SETUP.md` 都沒有記載 public synonym 與 ap_user 授權怎麼建（只有 `database.properties.example:20` 一句「有 Public Synonym」），新環境照 SETUP 建好後殼 jar 可能 ORA-00942 讀不到表。第 46 項 architect 發現；S1 會把緊急存取新表的 synonym／GRANT 寫進遞增腳本，但第一階段 21 表的建法仍待補 | 2026-10-12 | — |
| 49 | 第一階段 21 張表對照 `DB規範_V1_20261002_JLA.xlsx` 規則 3（單字超過 6 碼要用字典縮寫）有 7 個字未縮寫：AFFECTED_CNT、CONTENT_JSON、IS_ENABLED、IS_SELECTABLE、SUGGEST_*、IS_REVIEW_TRIGGER、VERSION_NO；「IM_」前綴也不在規範的業務簡碼表。已上線欄位改名要動程式、PRD 與既有資料。第 46 項 DB 規範對照時發現，使用者裁示（2026-10-12）只登記 | 2026-10-12 | — |
| 50 | `SqlConstantScanTest` 的黏字檢查目前只抓 `END` 接下一個字（`\bEND[A-Z]`）；同類拼接錯誤還有 `)AS`、`'X'AS`、`:nowAND`、`NULLAS`、漏逗號、連兩個逗號等沒抓。可擴充方向：關鍵字前必須有空白、`,\s*,`、`,\s*(FROM\|WHERE)\b`，要先確認不誤報既有 SQL。另：掃描清單比對只認 `@Repository`，DAO 日後改標 `@Component`、用 `@Bean` 建立或寫成介面／抽象類別時會漏掃而測試照過（目前 15 支都有標），可改依「含 static final String 的類別」或類別名 `*Dao` 比對。第 46 項 ENDAS 修正審查時提出，使用者裁示（2026-10-13）只登記 | 2026-10-13 | — |
| 51 | Service 單元測試把 DAO 全 mock，SQL 文法錯誤要連 DB 才會發現（第 46 項 ENDAS 即此）。不連 DB 的做法有四種：(a) 在片段接縫處斷言前後有空白 (b) 擴充第 50 項的規則 (c) 把各 SQL 常數存快照逐字比對 (d) 引入 JSqlParser 解析（新套件要先審查）；連 DB 的話可在測試環境以 `WHERE 1=0` 包住執行，或呼叫 `DBMS_SQL.PARSE` 只解析不執行。第 46 項 ENDAS 修正審查時提出，使用者裁示（2026-10-13）只登記 | 2026-10-13 | — |
| 52 | `NotificationService` 把寄信流程的 RuntimeException 一律 catch 後只寫 log：寄信前讀單失敗（例如第 46 項 ENDAS 那種 SQL 錯）時，送出仍成功、主管收不到通知，系統沒有任何可見警示（畫面、排程失敗通知都沒有）。「寄信失敗不影響送出結果」是既定規則，要補的是可見性。第 46 項 ENDAS 修正審查時提出，使用者裁示（2026-10-13）只登記 | 2026-10-13 | — |
| 53 | 前端「字數按碼點、去頭尾空白」的寫法 `[...s.trim()].length` 重複 5 次，分布在 `EmergencyDetailModal.vue`、`EmergencyReportModal.vue`（兩處）、`EmgAcctPicker.vue`、`EmergencyFormView.vue`；可抽成 `utils` 共用函式。使用者看不到差別，這是內部品質問題；日後改字數規則要改 5 處。第 46 項 S5 回合 2 審查時提出，使用者裁示（2026-10-13）只登記 | 2026-10-13 | — |

## 已拍板不做

| ID | 摘要 | 決定日 | 理由（一句） |
|----|------|--------|------------|
| 15 | 帳號展延功能 | 交付時已定（SA／交接文件） | 延長以重新提新增申請，或以「修改」申請調整到期日即可 |
