# SETUP：建置、設定、啟動與 DB 維護

> 本檔只放照著做的步驟。系統規格見 `PRD.md`，待辦見 `BACKLOG.md`。
> 本檔不記載任何 DB 帳密、主機位址或 IP；實際值向系統管理員取得。

## 1. 環境需求

| 項目 | 版本 | 用途 |
|---|---|---|
| JDK | 25 | 後端與殼 jar（`JAVA_HOME` 指向 JDK 25） |
| Maven | 不需安裝 | 兩個 jar 都附 Maven Wrapper（`mvnw`／`mvnw.cmd`，固定 Maven 3.9.16） |
| Node.js | 24 以上 | 前端（`im_acct_insp_web\im_acct_insp_web_frontend\.nvmrc`） |
| Oracle | 19c | schema RD_USER，須已有本模組 21 張表與初始資料（建表與初始資料 SQL 在 `db\`，用法見第 6 節） |
| DB 連線資訊 API | — | 後端執行期向它取得 DB 連線資訊，須能連到 |

## 2. 設定檔

實際設定檔不進 git。首次建置時，把每個 `.example` 複製成去掉 `.example` 的檔名，再填值（各 key 用途見 `PRD.md`「環境設定」，範本內註解有填法說明）。例（cmd，在 `im_acct_insp\` 下）：

```
copy src\main\resources\config\host.properties.example src\main\resources\config\host.properties
```

| 範本 | 複製成 | 必填重點 |
|---|---|---|
| `im_acct_insp\src\main\resources\application.properties.example` | `application.properties` | `server.port` |
| `im_acct_insp\src\main\resources\config\host.properties.example` | `config\host.properties` | DB 連線資訊 API 的三個 key；四個 key 都要保留（值可空） |
| `im_acct_insp\src\main\resources\config\database.properties.example` | `config\database.properties` | 取消 `db.connect.itflow` 的註解並填入 DB 別名 |
| `im_acct_insp\src\main\resources\config\environment.properties.example` | `config\environment.properties` | `im.acct.insp.attach.dir` 填附件根目錄絕對路徑 |
| `im_acct_insp_web\im_acct_insp_web\src\main\resources\application.properties.example` | `application.properties` | `server.port`；`spring.servlet.multipart.enabled` 保持關閉 |
| `im_acct_insp_web\im_acct_insp_web\src\main\resources\config\host.properties.example` | `config\host.properties` | 後端 API 的三個 key；四個 key 都要保留 |

- 附件根目錄在 Windows 要用 `/` 或 `\\`（例 `D:/home/im_acct_insp/attach`）；寫成單一 `\` 會被當跳脫字元，變成相對路徑而啟動失敗。目錄須讓執行帳號可讀寫。
- 測試環境若要用 baseDate 手動跑排程，把 `im.acct.insp.job.allow-base-date` 設為 true；正式環境保持 false。

## 3. 後端建置與啟動

在 `im_acct_insp\` 下：

```
.\mvnw.cmd clean package
java -jar target\im_acct_insp-0.0.1-SNAPSHOT.jar
```

- Git Bash／Linux 改用 `./mvnw clean package`、`java -jar target/im_acct_insp-0.0.1-SNAPSHOT.jar`。
- `clean package` 會一併跑單元測試（含 42 個 common.db 測試），須全過。
- port 取自 `server.port`，也可加 `--server.port=xxxx` 覆蓋。
- log：`/home/tomcat/log/im_acct_insp/im_acct_insp.yyyy-MM-dd.log`（Windows 為啟動時工作目錄所在磁碟機，例 `D:\home\tomcat\log\im_acct_insp\`），保留 30 天。

## 4. 前端與殼 jar 建置與啟動

先啟動後端，再於 `im_acct_insp_web\` 下：

```
cd im_acct_insp_web_frontend
npm install
npm test
npm run build
cd ..\im_acct_insp_web
.\mvnw.cmd clean package
java -jar target\im_acct_insp_web-0.0.1-SNAPSHOT.jar
```

- `npm test` 跑 Vitest（jsdom），測試檔在 `tests\`。
- `npm run build` 先做型別檢查（`vue-tsc --build`）再輸出到 `im_acct_insp_web\src\frontend`，會清空該目錄（含 `.gitkeep`）；要還原 `.gitkeep` 用 `git checkout`。該目錄只有 `.gitkeep` 時殼 jar 也能建置。型別檢查也可單獨跑 `npm run type-check`。
- 開啟 `http://localhost:<殼 jar 的 server.port>/im_acct_insp_web/`。
- 開發時可另開 `npm run dev`（Vite dev server），`/im_acct_insp_web/api` 會 proxy 到本機殼 jar，殼 jar 須先啟動。
- 殼 jar log：`/home/tomcat/log/im_acct_insp_web/im_acct_insp_web.yyyy-MM-dd.log`（Windows 依啟動磁碟機），保留 30 天。

## 5. 停止

兩個 jar 都是前景程序，在各自的主控台按 Ctrl+C 停止。

## 6. DB 維護（系統沒有維護畫面）

角色指派、執行人對照、使用者、巡檢項目、資產分類、帳號類別開關都直接改 RD_USER 的表。改完下一個請求即生效，不用重新部署；系統不留管理軌跡，改動前自行確認影響。

| 要做的事 | 改哪張表、哪些欄 |
|---|---|
| 新增使用者 | IM_ACCT_USER 新增一列：USER_ID（工號）、USER_EN_NAME 必填；PWD_HASH 依下方「產生密碼雜湊」填入；CREATE_BY 必填 |
| 停用使用者 | IM_ACCT_USER.STATUS 改 0（若正被系統管理員扮演，會自動視為切回） |
| 解鎖 | IM_ACCT_USER.LOCK_DATE 清空、LOGIN_FAIL_CNT 改 0 |
| 重設密碼 | 依下方產生新雜湊，寫入 IM_ACCT_USER.PWD_HASH（PWD_UPDATE_DATE 記設定時間） |
| 指派／移除角色 | IM_ACCT_ROLE_USER 新增一列（ROLE_ID、USER_ID），或把 STATUS 改 0；不得為 GENERAL。移除前自行確認沒有在途申請卡在只有該人能處理的關卡（系統不檢查） |
| 指派／移除執行人 | IM_EXEC_MAP 新增一列（CATG_ID、USER_ID），或把 STATUS 改 0 |
| 開放／關閉某帳號類別的新增 | IM_ACCT_CATG.IS_ENABLED 改 1／0；使用者要重新整理頁面或重新登入才看到新的下拉選項 |
| 停用巡檢項目 | IM_INSP_ITEM.STATUS 改 0（不要刪列，舊巡檢單以快照顯示） |

- 建正式環境 DB 時，只帶初始資料，排除測試 DB 才有的帳號與單據（見 `BACKLOG.md` 第 10 項）。

### 執行前共通注意

- **字元集**：用 SQL*Plus 前先設 `NLS_LANG=.AL32UTF8`；SQL Developer 開檔時選 UTF-8。否則寫入的中文（表 COMMENT、中文姓名、部門）會無聲變成亂碼，不會報錯。
- **一次只執行一個操作**：下面每個操作各自一個區塊，只複製你要做的那一塊，**不要把整節貼進去一起執行**。
- **關 autocommit**：每句 INSERT／UPDATE 都應**影響 1 筆**，確認筆數無誤再 COMMIT，異常就 ROLLBACK。
- 替換 `【】` 後的實際值不要存回本檔、不要 commit 進版控。

### 建新環境：執行 `db\` 兩個 SQL 檔

依序執行：

1. `db\im_acct_insp_create_table.sql`：在空 schema 建 21 張表、索引、5 個序列與欄位 COMMENT（需 CREATE TABLE、CREATE SEQUENCE 權限與表空間配額）。
2. `db\im_acct_insp_init_data.sql`：代碼表 116 筆初始資料（檔首 `WHENEVER SQLERROR EXIT ROLLBACK`，遇錯即停並回滾）。

建完補第一位管理者：打開 `im_acct_insp_init_data.sql` 檔尾的「初始管理者範本」，把最下面兩句 INSERT 與 COMMIT 複製到 SQL 工具，**只去掉這幾行行首的「-- 」**（沒去掉的話整段都是註解，執行不會報錯、但什麼都沒寫入），把【工號】【密碼雜湊】等占位符換成實際值後執行，最後要 COMMIT。要讓這位管理者能處理申請單的執行回報，還要依下方「指派執行人」為**每個帳號類別各加一列**（關卡 6 認執行人對照表 IM_EXEC_MAP、不認角色）。

### 各操作的可執行 SQL

**查合法代碼與確認工號**（指派角色、執行人前先查；代碼區分大小寫。系統沒有外鍵，INSERT 時代碼或工號打錯仍會成功、影響 1 筆，但不會生效也不會報錯）

```sql
SELECT ROLE_ID, ROLE_NAME FROM IM_ACCT_ROLE WHERE STATUS = 1 ORDER BY SORT_SEQ;
SELECT CATG_ID, CATG_NAME FROM IM_ACCT_CATG WHERE STATUS = 1 ORDER BY SORT_SEQ;
SELECT USER_ID, USER_EN_NAME, USER_NAME, STATUS FROM IM_ACCT_USER WHERE USER_ID = '【工號】';
```

**1. 新增使用者**（工號＝登入帳號；雜湊用下方 PwdHashTool 產生）

```sql
INSERT INTO IM_ACCT_USER (USER_ID, USER_EN_NAME, USER_NAME, DEPT_NAME, EMAIL,
                          PWD_HASH, PWD_UPDATE_DATE, STATUS, CREATE_DATE, CREATE_BY)
VALUES ('【工號】', '【英文名】', '【中文姓名】', '【部門】', '【Email】',
        '【密碼雜湊】', SYSDATE, 1, SYSDATE, '【操作人工號】');
```

**2. 停用／重新啟用使用者**（停用後從下一個請求起就無法使用系統；若正被系統管理員扮演，會自動視為切回。重新啟用曾停用的人＝同句把 STATUS 改 1，不能再 INSERT，會主鍵衝突）

```sql
UPDATE IM_ACCT_USER
   SET STATUS = 0, UPDATE_DATE = SYSDATE, UPDATE_BY = '【操作人工號】'
 WHERE USER_ID = '【工號】';
```

**3. 解鎖**（登入連錯 5 次被鎖）

```sql
UPDATE IM_ACCT_USER
   SET LOCK_DATE = NULL, LOGIN_FAIL_CNT = 0,
       UPDATE_DATE = SYSDATE, UPDATE_BY = '【操作人工號】'
 WHERE USER_ID = '【工號】';
```

**4. 重設密碼**（重設**不會**登出已登入的人）

```sql
UPDATE IM_ACCT_USER
   SET PWD_HASH = '【密碼雜湊】', PWD_UPDATE_DATE = SYSDATE,
       UPDATE_DATE = SYSDATE, UPDATE_BY = '【操作人工號】'
 WHERE USER_ID = '【工號】';
```

- 因密碼外洩而重設時，要讓對方手上的登入一起失效：先用第 2 段停用 → 用本段重設 → **停用後至少等 30 分鐘**再用第 2 段改回 STATUS＝1。停用只是暫時擋下舊登入，30 分鐘無存取後舊登入才會因閒置逾時永久失效；提早重新啟用，舊登入會恢復有效。

**5a. 指派角色**（第一次指派；不得為 GENERAL——一般人員權限是程式自動給的）

```sql
INSERT INTO IM_ACCT_ROLE_USER (ROLE_ID, USER_ID, STATUS, CREATE_DATE, CREATE_BY)
VALUES ('【角色代碼】', '【工號】', 1, SYSDATE, '【操作人工號】');
```

**5b. 移除角色**（重新指派曾移除的人＝同句把 STATUS 改回 1，不能再 INSERT，會主鍵衝突。移除前自行確認沒有在途申請卡在只有該人能處理的關卡，系統不檢查）

```sql
UPDATE IM_ACCT_ROLE_USER
   SET STATUS = 0, UPDATE_DATE = SYSDATE, UPDATE_BY = '【操作人工號】'
 WHERE ROLE_ID = '【角色代碼】' AND USER_ID = '【工號】';
```

**6a. 指派執行人**（第一次指派；一列對一個帳號類別）

```sql
INSERT INTO IM_EXEC_MAP (CATG_ID, USER_ID, STATUS, CREATE_DATE, CREATE_BY)
VALUES ('【帳號類別代碼】', '【工號】', 1, SYSDATE, '【操作人工號】');
```

**6b. 移除執行人**（重新指派＝同句改回 1）

```sql
UPDATE IM_EXEC_MAP
   SET STATUS = 0, UPDATE_DATE = SYSDATE, UPDATE_BY = '【操作人工號】'
 WHERE CATG_ID = '【帳號類別代碼】' AND USER_ID = '【工號】';
```

**7. 開放／關閉某帳號類別的新增申請**（1＝開放、0＝關閉；使用者要重新整理頁面或重新登入才看到新的下拉選項）

```sql
UPDATE IM_ACCT_CATG
   SET IS_ENABLED = 【1 或 0】, UPDATE_DATE = SYSDATE, UPDATE_BY = '【操作人工號】'
 WHERE CATG_ID = '【帳號類別代碼】';
```

**8. 停用巡檢項目**（不刪列，舊巡檢單靠快照顯示）

```sql
UPDATE IM_INSP_ITEM
   SET STATUS = 0, UPDATE_DATE = SYSDATE, UPDATE_BY = '【操作人工號】'
 WHERE INSP_ITEM_ID = '【巡檢項目代碼】';
```

### 產生密碼雜湊（PwdHashTool）

在 `im_acct_insp\` 下（`JAVA_HOME` 已指向 JDK 25）：

```
.\mvnw.cmd -q compile
"%JAVA_HOME%\bin\java" -cp target\classes com.mpx.im_acct_insp.tools.PwdHashTool
```

- 上面第二行是 cmd 寫法。直接在主控台執行時輸入密碼不回顯，按 Enter 後印出 `PBKDF2$...` 字串，整串貼進 PWD_HASH。
- 讀一行密碼，只去掉行尾換行，其他字元（含空白）原樣保留。
- 不接受命令列參數（帶參數會直接結束，exit code 2）；不寫檔、不記 log。
- 也可用管線餵入一行密碼，但密碼會留在 shell 歷史；cmd 的 `echo xxx|` 會把 `|` 前的空白一起送進去，要用管線請改用 Git Bash 或 PowerShell。
- 同一密碼每次產生的字串都不同，都能驗證通過。

## 7. 手動觸發排程

系統沒有觸發排程的畫面，以 curl 呼叫 API。需 SYS_JOB_RUN 權限（初始資料中只有 SYS_ADMIN 角色有）。以下以直連後端（`server.port` 8989）為例；經殼 jar 時把 `http://localhost:8989` 換成殼 jar 的位址並在路徑前加 `/im_acct_insp_web`。指令為 Git Bash 寫法，`【】` 換成實際值。

**1. 登入取 token**

每個指令都加了 `-w`，回應內容下一行會印出 HTTP 狀態碼（例如 `200`、`401`）。

```bash
curl -s -w "\n%{http_code}\n" -X POST http://localhost:8989/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"userId":"【工號】","password":"【密碼】"}'
```

- 成功回 200，JSON 的 `token` 欄位就是下一步要帶的 token（另有 `me` 欄位為登入者資料）。
- 工號不存在、密碼錯、使用者停用都回 401 `{"message":"帳號或密碼錯誤"}`；被鎖定回 401「此帳號已鎖定，請聯絡系統管理員」（解鎖見第 6 節第 3 段）。
- 密碼含 `'`、`"`、`\` 時這行指令會壞掉（回 400 或 shell 報錯，看起來像帳密錯），請找開發人員協助。

**2. 觸發排程**

```bash
# J01：到期帳號轉逾期、寄逾期信與到期前 7 天提醒（漏跑會補寄、不重寄）
curl -s -w "\n%{http_code}\n" -X POST http://localhost:8989/api/v1/jobs/J01/run \
  -H "Authorization: Bearer 【token】"

# J02：每週逾期帳號彙總信，同一週只寄一次
curl -s -w "\n%{http_code}\n" -X POST http://localhost:8989/api/v1/jobs/J02/run \
  -H "Authorization: Bearer 【token】"
```

- 成功回 200，JSON 為 `runId`（執行紀錄序號）、`resultCode`（`SUCCESS`／`PARTIAL`／`FAIL`）、`affectedCnt`（處理筆數）。J02 回 `SUCCESS` 且 0 筆＝本週已寄過，不是失敗。
- 錯誤：
  - 401「登入已逾時，請重新登入」：token 貼錯、少了 `Bearer `、或閒置超過 30 分鐘，重新登入取 token。
  - 403「您沒有執行此操作的權限」：登入者沒有 SYS_JOB_RUN。
  - 404：代碼不是 J01／J02。
  - 409 E16「排程執行中，請稍後再試」：同一排程正在執行。
  - 400 E17「此環境不允許指定基準日」：見下方 baseDate。

**指定基準日（僅測試環境）**：`im.acct.insp.job.allow-base-date=true` 的環境可在網址加 `?baseDate=yyyy-MM-dd`，以該日代替今天執行；正式環境為 false，帶了一律回 400 E17。日期格式不對回 400。

```bash
curl -s -w "\n%{http_code}\n" -X POST "http://localhost:8989/api/v1/jobs/J01/run?baseDate=【yyyy-MM-dd】" \
  -H "Authorization: Bearer 【token】"
```

- **J01 帶未來日期會把到期日早於該日的帳號全部轉成逾期**、寫入帳號歷史並寄通知，系統沒有復原功能；只在確定要模擬那一天時使用。

**3. 用完登出**（token 會跟著指令留在 shell 歷史，最長 8 小時內有效；登出後立即失效）

```bash
curl -s -w "\n%{http_code}\n" -X POST http://localhost:8989/api/v1/auth/logout \
  -H "Authorization: Bearer 【token】"
```

- 成功回 200、沒有內容。密碼與 token 都會留在 shell 歷史，正式環境操作後自行清除。
