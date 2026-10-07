# Infra Manager Java 改寫專案指引

機房設備異動申請系統的 Spring Boot REST + Vue 3 SPA 改寫版（Oracle 19c）。
舊 Node 系統在 `D:\ai\Infra_Manager`（唯讀參考，port 3200）。
規格看 `PRD.md`、待辦看 `BACKLOG.md`、操作步驟看 `SETUP.md`。公司範本規範以 `infra_manager_java/README.md` §8 與 `infra_manager_web/README.md` §10 為準。

## 基本功能衝刺期（2026-10-06 使用者裁示 ②A，基本功能完成後刪除本段、恢復全域規則）

目標：先把舊系統的日常流程（登入 → 看單 → 建單 → 簽核 → 補件退件 → 執行結案）做出來，細節調整全部延後。

- **code review 的時機改為「每個施工階段（S 編號）結束才跑一次 `code-reviewer`」**，回合內只跑單元測試（`./mvnw -q clean package` 全綠即可 commit；3201／3202 在跑時改用 `./mvnw -q package -Dspring-boot.repackage.skip=true`，不加 clean），不每回合委派 `code-reviewer` 與 `test-runner`
- review 回報的**阻擋項當場修**；**非阻擋項一律登記到 `BACKLOG.md`「下一階段（細節調整）」分區、不當場修**，也不逐項請使用者裁示
- **待確認項預設照建議做**（2026-10-07 裁示 ①A）：開工分析、驗收、review 列出的待確認項，Claude 直接採自己的建議施工，不停下等「全部依建議」；報告照常列出採了哪個選項與理由，使用者不同意再事後改。**例外，仍須先停下問**：刪除資料或檔案、資安相關取捨、動設定檔（含 `.env`／`*.properties`）、與使用者先前裁示矛盾
- **三檔同步改為階段結束才做**（2026-10-07 裁示 ②A）：回合內的 commit 只更新 `BACKLOG.md`（交接欄、登記項）；`PRD.md` 與 `CHANGELOG.md` 在每個 S 階段結束時一次補齊。階段做到一半時 PRD 落後實際程式屬預期
- **測試與驗證的時機**（2026-10-07 使用者裁示「全部依建議」：①A ②A ③B ④B）：
  - 每回合照做：單元測試（同時是編譯檢查；前端回合跑型別檢查與 build），以及 commit＋push（每回合可單獨還原、有遠端備份）
  - 延到 S 階段結束一次做：**跑整合測試**（`./mvnw verify`，連測試 DB）與**手動驗證**（經 3201 從畫面走完整流程）。新的整合測試**仍在該回合寫好**，只是不在回合內跑
  - 施工計畫回合列裡寫的「手動經 3201 驗證」一律併入階段結束那次，不逐回合做
- 施工順序：S2 回合三 → S4 → S6 → S7 → S9 → S10 → S5 → S8 → 其餘；S3（歷史申請單匯入）與帳號真實匯入（第 91 項）延後到基本功能之後
- 其他全域規則（Edit／Write 改檔、commit 後 push、繁體中文、不碰 `com.mpx.common`）**照常**（三檔同步依上方 ②A 改為階段結束），不因衝刺放寬

## 開發慣例
- 後端：Java 25（`export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot"` 後再 `./mvnw`）、Spring Boot 4.1、Jackson 3（`tools.jackson.*`）；SQL 只用 `:name` 參數，表名經 `DbSchema.table()` 加前綴
- 前端：無 `any`、型別進 `src/types/`、API 一律經 `api/http.ts`、失敗 toast 不寫「查無資料」、每頁 20 列、1024 寬不得橫向捲動、不用 UI 元件庫
- 真實 `*.properties`、`db/import/` 真實檔不進版控；log 不得含密碼、jdbcUrl、帳號、SQL 參數、cookie（「帳號」指登入帳號 `LOGIN_ID`；工號 `USER_ID` 可記，供稽核追查操作人——2026-10-07 使用者裁示）

## 測試 DB 分工（2026-10-07 使用者交代）
- **DDL（建表、改表、授權）由使用者執行**：Claude 只產出 SQL 檔與步驟，不自己對 DB 跑 DDL
- **測試需要的資料由 Claude 自己建立與清除**（種子 SQL、驗收用假資料），不再請使用者代跑；只限測試 DB（`d-itflow`，SID `FLOW`；JDBC 一律用 IP `192.168.119.161:1521`，主機名解析到的位址連線會被拒——2026-10-07 實測），以 `ap_user` 連線、`ALTER SESSION SET CURRENT_SCHEMA = RD_USER`。刪除條件必須限定在種子資料自己的鍵值範圍
- 連線方式：本機 sqlplus 是 11.2 版，連 19c 會 ORA-28040，改用 JDBC（`~/.m2` 的 ojdbc17＋orai18n）執行
- **DB 密碼不寫進任何檔案**（含本檔、repo、auto memory）；新 session 沒有密碼時向使用者索取
