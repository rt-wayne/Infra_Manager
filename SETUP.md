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
| `im_acct_insp\src\main\resources\application.properties.example` | `application.properties` | `server.port`；要實際寄信填 `spring.mail.host=mail.pxmart.com.tw` |
| `im_acct_insp\src\main\resources\config\host.properties.example` | `config\host.properties` | DB 連線資訊 API 的三個 key；四個 key 都要保留（值可空） |
| `im_acct_insp\src\main\resources\config\database.properties.example` | `config\database.properties` | 取消 `db.connect.itflow` 的註解並填入 DB 別名 |
| `im_acct_insp\src\main\resources\config\environment.properties.example` | `config\environment.properties` | `im.acct.insp.attach.dir` 填附件根目錄絕對路徑；寄信的寄件人與寄送名單見下方 |
| `im_acct_insp_web\im_acct_insp_web\src\main\resources\application.properties.example` | `application.properties` | `server.port`；`spring.servlet.multipart.enabled` 保持關閉 |
| `im_acct_insp_web\im_acct_insp_web\src\main\resources\config\host.properties.example` | `config\host.properties` | 後端 API 的三個 key；四個 key 都要保留 |

- 附件根目錄在 Windows 要用 `/` 或 `\\`（例 `D:/home/im_acct_insp/attach`）；寫成單一 `\` 會被當跳脫字元，變成相對路徑而啟動失敗。目錄須讓執行帳號可讀寫。
- 測試環境若要用 baseDate 手動跑排程，把 `im.acct.insp.job.allow-base-date` 設為 true；正式環境保持 false。
- 寄信：
  - `application.properties` 的 `spring.mail.host` 與 `environment.properties` 的 `im.acct.insp.mail.from` 都有值才會實際寄出，否則只寫 log。
  - 誰收得到信由 `im.acct.insp.mail.allow-list`（逗號分隔工號，半形或全形逗號皆可）決定，留空＝誰都不寄。測試環境填 `2410305`，因為測試 DB 有同事的真實 Email。
  - 正式環境才設 `im.acct.insp.mail.send-to-all=true`，寄給所有收件人。
  - `im.acct.insp.mail.site-url` 填使用者在瀏覽器開的前端網址（含 context），信末才會有「前往系統」連結，例 `http://主機:8787/im_acct_insp_web/`。直接從瀏覽器網址列複製也可以（`#` 之後、結尾的 `index.html` 會自動去掉）。留空＝不放連結；不是 `http://` 或 `https://` 開頭、沒有主機名稱，或網址含空白、雙引號等不合法字元，也視同留空，啟動 log 會有 WARN。
  - 改完要重啟後端。啟動 log 會有一行「寄信：SMTP 實際寄出；寄送名單 N 人」或「寄信：SMTP 未設定…只寫 log」，可據此確認。若出現 WARN「SMTP 已啟用但寄送名單是空的…」，代表名單空又沒開 `send-to-all`，所有通知都不會寄出。

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
- 用殼 jar 開的畫面空白、樣式或圖片沒出來時，按 F12 看 Console：出現 `Content Security Policy`（Chrome／Edge）或 `Content-Security-Policy`（Firefox）字樣的錯誤，代表前端用了外站資源、行內程式、行內樣式或 data: 圖片，被殼 jar 的安全標頭擋下。`npm run dev` 開的畫面不經過殼 jar、不帶這個標頭，所以這類問題開發時看不到，只在殼 jar 開的畫面出現。放寬步驟：
  1. 改 `im_acct_insp_web\src\main\java\com\mpx\im_acct_insp_web\config\SecurityHeadersConfig.java` 的 `CSP`，加上錯誤訊息點名的項目。來源只填協定加主機，不帶路徑（帶路徑就只對得到那一個檔）。例如外站字型通常要先載外站的 CSS，`style-src` 與 `font-src` 都要加：`style-src 'self' https://字型主機; font-src 'self' https://字型主機`。
  2. 同步改 `src\test\java\com\mpx\im_acct_insp_web\config\SecurityHeadersConfigTest.java` 裡寫死的 CSP 預期值，否則 `.\mvnw.cmd clean package` 會測試失敗。
  3. 同步改 `PRD.md`「安全標頭」那段的 CSP 值。
  4. 重建殼 jar，重新啟動。

## 5. 停止

兩個 jar 都是前景程序，在各自的主控台按 Ctrl+C 停止。

## 6. DB 維護（系統沒有維護畫面）

角色指派、執行人對照、使用者、巡檢項目、資產分類、帳號類別開關都直接改 RD_USER 的表。改完下一個請求即生效，不用重新部署；系統不留管理軌跡，改動前自行確認影響。

### 執行前共通注意

- **字元集**：DB 字元集須為 AL32UTF8（Big5 系字元集存不了罕用的人名用字，會無聲變成問號）。SQL*Plus 只用來執行下方 `db\` 兩個檔案，在 cmd 視窗執行，執行前在同一個視窗先下 `set NLS_LANG=.AL32UTF8`。下方各段含中文值的語句（例如第 1 段的中文姓名、部門）一律用 SQL Developer 執行，不要貼進 cmd 的 SQL*Plus——cmd 預設是 Big5 代碼頁，中文會無聲變成亂碼、不會報錯。
- **一次只執行一個操作**：下面每個操作各自一個區塊，只複製你要做的那一塊，**不要把整節貼進去一起執行**。
- **關 autocommit**：每句 INSERT／UPDATE 都應**影響 1 筆**（例外在該段註明），確認筆數無誤再 COMMIT，異常就 ROLLBACK。核對完立即 COMMIT 或 ROLLBACK——交易沒結束前，被改到的那個人登入會卡住。
- 替換 `【】` 後的實際值不要存回本檔、不要 commit 進版控。
- **SQL 歷史**：SQL Developer 預設會保留執行過的語句（「SQL 歷史記錄」，F8），第 1、4 段與建新環境的初始管理者 INSERT 含密碼雜湊；正式環境操作後，在 SQL 歷史記錄視窗清除歷史，或刪 `%APPDATA%\SQL Developer\SqlHistory\` 下對應的檔案。

### 建新環境：執行 `db\` 兩個 SQL 檔

正式環境一律用 `db\` 兩個檔案在空 schema 建，**不要從測試 DB 匯出或複製**（測試 DB 有真實員工的測試帳號與測試單據）。依序執行：

1. `db\im_acct_insp_create_table.sql`：在空 schema 建 21 張表、索引、5 個序列與欄位 COMMENT（需 CREATE TABLE、CREATE SEQUENCE 權限與表空間配額）。本檔沒有遇錯即停，且 DDL 每句自動提交：中途失敗時，先 DROP 已建立的表與序列（或請 DBA 清空 schema）再從頭重跑，否則已存在的表會報 ORA-00955。
2. `db\im_acct_insp_init_data.sql`：代碼表 116 筆初始資料（檔首 `WHENEVER SQLERROR EXIT ROLLBACK`，遇錯即停並回滾）。

建完補第一位管理者，**用 SQL Developer 另開一個連線**執行（姓名含中文；也不要接在跑 init_data 檔的 SQL*Plus 連線裡——該檔的遇錯即停設定整個連線都有效，之後任何一句打錯都會直接結束 SQL*Plus 並回滾）：

1. 打開 `im_acct_insp_init_data.sql` 檔尾的「初始管理者範本」，把最下面兩句 INSERT 複製到 SQL 工具，**只去掉這幾行行首的「-- 」**（沒去掉的話整段都是註解，執行不會報錯、但什麼都沒寫入），把【工號】【密碼雜湊】等占位符換成實際值。
2. 要讓這位管理者能處理申請單的執行回報，再依下方第 6a 段為**每個帳號類別各加一列**（關卡 6 認執行人對照表 IM_EXEC_MAP、不認角色；類別代碼用下方查代碼的第二句查）。
3. 執行以上全部 INSERT，確認每句都影響 1 筆後，執行 `COMMIT;`。

### 各操作的可執行 SQL

**查合法代碼與確認工號**（指派角色、執行人前先查；代碼區分大小寫。系統沒有外鍵，INSERT 時代碼或工號打錯仍會成功、影響 1 筆，但不會生效也不會報錯）

```sql
SELECT ROLE_ID, ROLE_NAME FROM IM_ACCT_ROLE WHERE STATUS = 1 ORDER BY SORT_SEQ;
SELECT CATG_ID, CATG_NAME FROM IM_ACCT_CATG WHERE STATUS = 1 ORDER BY SORT_SEQ;
SELECT USER_ID, USER_EN_NAME, USER_NAME, STATUS FROM IM_ACCT_USER WHERE USER_ID = '【工號】';
SELECT INSP_ITEM_ID, SECT_NAME, ITEM_NAME, STATUS FROM IM_INSP_ITEM ORDER BY SORT_SEQ;
SELECT ASSET_CLASS_ID, ASSET_CLASS_NAME, IS_SELECTABLE FROM IM_ASSET_CLASS WHERE STATUS = 1 ORDER BY SORT_SEQ;
```

**1. 新增使用者**（工號＝登入帳號；英文名必填；雜湊用下方 PwdHashTool 產生；分機電話選填：使用者在申請表單按「同申請人」、且表單上沒填申請人分機與 Email 時，會帶入「使用人聯絡方式」並蓋掉原本填的內容（門禁卡類別不帶入）；不填就把 `'【分機電話】'` 換成 `NULL`）

```sql
INSERT INTO IM_ACCT_USER (USER_ID, USER_EN_NAME, USER_NAME, DEPT_NAME, EMAIL, TEL_NO,
                          PWD_HASH, PWD_UPDATE_DATE, STATUS, CREATE_DATE, CREATE_BY)
VALUES ('【工號】', '【英文名】', '【中文姓名】', '【部門】', '【Email】', '【分機電話】',
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

**4. 重設密碼**（重設**不會**登出已登入的人，也**不會**解鎖——被鎖定的人另做第 3 段）

```sql
UPDATE IM_ACCT_USER
   SET PWD_HASH = '【密碼雜湊】', PWD_UPDATE_DATE = SYSDATE,
       UPDATE_DATE = SYSDATE, UPDATE_BY = '【操作人工號】'
 WHERE USER_ID = '【工號】';
```

- 因密碼外洩而重設時，要讓對方手上的登入一起失效：在**同一個交易**裡接著執行下面這句，把該人所有還沒登出的登入標成已登出，再一起 COMMIT。COMMIT 後舊登入立即永久失效（與系統登出相同），不必停用使用者。這句影響的筆數＝該人所有沒按登出的登入紀錄數，可以是 0 或多筆，不是 1 筆；早已逾時但沒按登出的舊紀錄也算在內，所以筆數可能遠多於實際在線數，屬正常。這句執行後、交易沒結束前，該人正在用的頁面也可能沒反應，核對完立即 COMMIT。
- COMMIT 後**再執行一次同一句並 COMMIT**：核對筆數期間若有人用舊密碼登入，那次登入會等到第一次 COMMIT 後才完成並取得新 token，第二次執行就是把它補標掉（通常影響 0 筆）。

```sql
UPDATE IM_ACCT_LOGIN_TOKEN
   SET LOGOUT_DATE = SYSDATE, UPDATE_DATE = SYSDATE, UPDATE_BY = '【操作人工號】'
 WHERE USER_ID = '【工號】' AND LOGOUT_DATE IS NULL;
```

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

**8. 停用／重新啟用巡檢項目**（不刪列，舊巡檢單靠快照顯示；新巡檢單只列 STATUS＝1 的項目。重新啟用＝同句改回 1）

```sql
UPDATE IM_INSP_ITEM
   SET STATUS = 0, UPDATE_DATE = SYSDATE, UPDATE_BY = '【操作人工號】'
 WHERE INSP_ITEM_ID = '【巡檢項目代碼】';
```

**9. 資產分類可否選用**（1＝可選、0＝不可選。0 時申請表單下拉不列出該分類，送出時也會回 400 E06「此資產分類碼不可選用」；已用該分類的舊帳號與舊單照常顯示，但這些帳號申請修改、重新啟用（含這兩類申請退件重送）時要改選其他分類，否則送出同樣回 E06；停用與刪除申請不檢查資產分類。使用者要重新整理頁面或重新登入才看到新的下拉選項）

```sql
UPDATE IM_ASSET_CLASS
   SET IS_SELECTABLE = 【1 或 0】, UPDATE_DATE = SYSDATE, UPDATE_BY = '【操作人工號】'
 WHERE ASSET_CLASS_ID = '【資產分類代碼】';
```

**10. 讓排程信重寄**（J01、J02 只要通知紀錄裡有任何一筆就不再寄，連寄送失敗、收件人沒有 Email、不在寄送名單而略過的也算；修好 SMTP 或改了寄送名單後要重測，先刪掉那一輪的紀錄，再依第 7 節觸發排程。紀錄刪了就查不回來，先用第一句確認要刪的筆數）

```sql
-- 事件代碼：J02 每週彙總＝WEEKLY_SUMMARY（參照鍵＝該週週一，例 20261005）；
--           J01 逾期信＝EXPIRED、到期前 7 天信＝EXPIRY_7D（參照鍵＝帳號單號_到期日，中間無空格，例 AR20261001-001_20261006）
SELECT RCPT_USER_ID, SEND_DATE, RESULT_CODE, ERR_MSG
  FROM IM_ACCT_NOTIFY_LOG
 WHERE EVENT_CODE = '【事件代碼】' AND REF_ID = '【參照鍵】';

DELETE FROM IM_ACCT_NOTIFY_LOG
 WHERE EVENT_CODE = '【事件代碼】' AND REF_ID = '【參照鍵】';
-- 影響筆數與上一句相同再 COMMIT
```

- J01 刪了紀錄後，帳號要仍符合條件才會重寄：逾期信要帳號仍是已逾期；7 天提醒要仍是已核准、且到期日落在基準日到基準日＋7 天之間（可用第 7 節的 baseDate 調整基準日，但先看該處的粗體警告：帶未來日期會把帳號轉成逾期，且無法復原）。不符合時觸發排程也不會寄，不代表 SMTP 有問題。

**未列出的操作**（例如改使用者的 Email、部門、分機電話）：UPDATE 一律一併設 `UPDATE_DATE = SYSDATE`、`UPDATE_BY = '【操作人工號】'`，WHERE 用該表主鍵，確認影響 1 筆再 COMMIT。新增代碼（帳號類別、巡檢項目、資產分類）可能牽動程式邏輯，先找開發人員，不要直接 INSERT。

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

密碼與 token 都只放在 shell 變數裡，不直接寫進指令，所以不會留在 shell 歷史；密碼經管線送給 curl，也不會出現在程序的命令列（token 會，但登出後即失效）。全部做完在第 3 步清掉變數。下面三步要在**同一個 Git Bash 視窗**依序執行。每個 curl 都加了 `-w`，回應內容的最後一行是 HTTP 狀態碼（例如 `200`、`401`）。

**1. 登入取 token**（分兩次執行：先單獨執行第一行並輸入密碼，再貼下面那段——整塊一起貼的話，有些終端機會把下一行指令當成密碼讀走）

```bash
IFS= read -rs -p "密碼: " PW; echo
```

輸入時不回顯，按 Enter 結束；密碼前後的空白會原樣保留。接著貼：

```bash
PWJ=${PW//\\/\\\\}; PWJ=${PWJ//\"/\\\"}
RESP=$(printf '{"userId":"%s","password":"%s"}' '【工號】' "$PWJ" \
  | curl -s -w "\n%{http_code}" -X POST http://localhost:8989/api/v1/auth/login \
      -H "Content-Type: application/json" --data-binary @-)
unset PW PWJ
TOKEN=$(echo "$RESP" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
echo "$RESP" | tail -n 1
```

- 第一行把密碼裡的 `\` 與 `"` 先跳脫，含這兩個字元的密碼也能登入。
- 最後印出 `200` 就是成功，token 已存進變數 `TOKEN`，下一步直接用；要確認有沒有存到，用 `echo ${#TOKEN}` 看長度（應為 43），不要把 token 本身印出來。不是 200 時用 `echo "$RESP"` 看錯誤訊息。
- 印出 `000`＝連不上後端（沒啟動或位址錯），這時 `$RESP` 也沒有內容。
- 工號不存在、密碼錯、使用者停用都回 401 `{"message":"帳號或密碼錯誤"}`；其中只有「密碼錯」每次算一次輸錯，連錯 5 次會被鎖定（工號不存在或已停用不計次）。被鎖定回 401「此帳號已鎖定，請聯絡系統管理員」（解鎖見第 6 節第 3 段）。
- 回 400「請求內容格式錯誤」＝密碼含 Tab 之類的控制字元，組出的 JSON 格式不合法、後端拒收，請找開發人員協助。

**2. 觸發排程**

```bash
# J01：到期帳號轉逾期、寄逾期信與到期前 7 天提醒（漏跑會補寄、不重寄）
curl -s -w "\n%{http_code}\n" -X POST http://localhost:8989/api/v1/jobs/J01/run \
  -H "Authorization: Bearer $TOKEN"

# J02：每週逾期帳號彙總信，同一週只寄一次（本週寄送失敗、收件人沒有 Email 或不在寄送名單也算已寄，同週不會重寄）
curl -s -w "\n%{http_code}\n" -X POST http://localhost:8989/api/v1/jobs/J02/run \
  -H "Authorization: Bearer $TOKEN"
```

- 成功回 200，JSON 為 `runId`（執行紀錄序號）、`resultCode`（`SUCCESS`／`PARTIAL`／`FAIL`）、`affectedCnt`。
- `affectedCnt` 的意思：J01＝轉逾期的帳號數，加上本次有寫逾期信或 7 天提醒通知紀錄的帳號數（每個帳號算 1，不論收件人幾位；寄失敗、收件人都沒 Email 或不在寄送名單而略過的也算；同一帳號可能被算兩次）；J02＝彙總信列出的逾期帳號數，不是寄出封數。兩者回 `SUCCESS` 都不代表信已寄達。
- J02 回 `SUCCESS` 且 0 筆有三種可能：本週已有彙總紀錄（不再寄，含本週寄送失敗、收件人沒有 Email 或不在寄送名單的情況）；目前沒有逾期帳號（照常寄給每位收件人一封「0 筆」彙總）；或沒有逾期帳號、也沒有任何收件人（什麼都不寄、不留紀錄，下次執行會再試）。要確認寄送結果請查（週一日期＝基準日所在週的週一，例 `20261005`；`RESULT_CODE` 為寄送結果）：

```sql
SELECT RCPT_USER_ID, RCPT_EMAIL, SEND_DATE, RESULT_CODE, ERR_MSG
  FROM IM_ACCT_NOTIFY_LOG
 WHERE EVENT_CODE = 'WEEKLY_SUMMARY' AND REF_ID = '【該週週一 yyyymmdd】';
```

- 同一週要重寄（例如修好 SMTP 後重測）：先依第 6 節第 10 段刪掉該週紀錄。

- 錯誤：
  - 401「登入已逾時，請重新登入」＝token 已失效：變數 `TOKEN` 是空的（登入沒成功）或少了 `Bearer `、閒置超過 480 分鐘、登入已滿 8 小時、已登出，或登入者已被停用。重新登入取 token；重新登入回「帳號或密碼錯誤」而密碼確定沒錯時，查該工號的 STATUS（第 6 節查代碼的第三句）。
  - 403「您沒有執行此操作的權限」：登入者沒有 SYS_JOB_RUN（沒有這個權限時，代碼打錯也是回 403）。
  - 404：代碼不是大寫的 `J01`／`J02`（`j01` 也是 404）。
  - 409 E16「排程執行中，請稍後再試」：同一排程正在執行。
  - 400 E17「此環境不允許指定基準日」：見下方 baseDate。

**指定基準日（僅測試環境）**：`im.acct.insp.job.allow-base-date=true` 的環境可在網址加 `?baseDate=yyyy-MM-dd`，以該日代替今天執行；正式環境為 false，帶了一律回 400 E17。日期格式不對回 400。

```bash
curl -s -w "\n%{http_code}\n" -X POST "http://localhost:8989/api/v1/jobs/J01/run?baseDate=【yyyy-MM-dd】" \
  -H "Authorization: Bearer $TOKEN"
```

- **J01 帶未來日期會把到期日早於該日的帳號全部轉成逾期**、寫入帳號歷史並寄通知，系統沒有復原功能；只在確定要模擬那一天時使用。

**3. 用完登出並清掉變數**（不登出的話 token 最長 8 小時內有效；登出後立即失效）

```bash
curl -s -w "\n%{http_code}\n" -X POST http://localhost:8989/api/v1/auth/logout \
  -H "Authorization: Bearer $TOKEN"
unset TOKEN RESP
```

- 成功回 200、沒有內容。
