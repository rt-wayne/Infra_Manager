# 環境設定與操作手冊（SETUP）

> 本機第一次跑起來的順序：需要的工具 → 設定 JAVA_HOME → 建立設定檔 → 資料庫初始化（DDL、交易測試表、授權）→ 建置與測試 → 啟動。

## 安全規則

**連線資訊、密碼與金鑰不得寫進任何進版控的檔案**（包含 `*.properties.example`、程式碼、bat 檔、SQL script、測試、文件），也不得寫進指令列參數。`*.properties` 真檔只留在本機（已被 `.gitignore` 排除），**絕不 commit**；commit 前先跑 `git status` 確認清單裡沒有任何不是 `.example` 結尾的 `*.properties`。資料庫密碼一律在執行時手動輸入；`.env` 已列入 `.gitignore`。

## 需要的工具

| 工具 | 版本 | 備註 |
|---|---|---|
| JDK | 25 LTS（Eclipse Temurin） | 本機安裝在 `C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\`；PATH／JAVA_HOME 若仍指向舊版，見下一節 |
| Maven | 各目錄附的 Maven Wrapper（`mvnw`／`mvnw.cmd`） | 不需另裝 Maven |
| Node.js／npm | Node 24／npm 11 | 前端 Vue 3.5 + Vite 8 用（`infra_manager_web_frontend/.nvmrc` 為 24） |
| SQL*Plus | 11.2 | 執行 DDL 用 |
| 公司 DB 連線資訊 API | — | 本機要連得到；後端執行期由它依別名取得 DB 連線資訊（位址向系統負責人取得） |
| Oracle | 19c | 開發期連公司 19c 測試環境；本機不需安裝 Oracle |
| Docker | — | 只在部署環境（Rocky Linux 9.7）使用；本機開發不需安裝 |

## 設定 JAVA_HOME

建置與啟動都要用 JDK 25。先確認：

```
java -version
```

若顯示的不是 25，在**要執行建置或啟動的那個視窗**先設定（只對該視窗有效）：

- cmd：
  ```
  set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot
  set PATH=%JAVA_HOME%\bin;%PATH%
  ```
- Git Bash：
  ```
  export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot"
  export PATH="$JAVA_HOME/bin:$PATH"
  ```

`start-new.bat` 只看 JAVA_HOME，沒設或指到的不是 JDK 會直接報錯停止。

## 建立設定檔（properties）

每個 `.properties.example` 都要在同一個資料夾複製成去掉 `.example` 的真檔再填值。真檔不進 git；日後新增鍵時，要同步寫進對應的 `.example`（只寫鍵與說明，不寫真實值）。在 repo 根目錄的 cmd 執行：

```
copy infra_manager_java\src\main\resources\application.properties.example infra_manager_java\src\main\resources\application.properties
copy infra_manager_java\src\main\resources\config\database.properties.example infra_manager_java\src\main\resources\config\database.properties
copy infra_manager_java\src\main\resources\config\host.properties.example infra_manager_java\src\main\resources\config\host.properties
copy infra_manager_web\infra_manager_web\src\main\resources\application.properties.example infra_manager_web\infra_manager_web\src\main\resources\application.properties
copy infra_manager_web\infra_manager_web\src\main\resources\config\host.properties.example infra_manager_web\infra_manager_web\src\main\resources\config\host.properties
```

接著填值（各鍵用途見 `PRD.md`「環境設定」）：

| 真檔 | 要填的值 |
|---|---|
| 後端 `application.properties` | 不用改（port 3202 已寫好） |
| 後端 `config/database.properties` | `db.connect.itflow=` 後面填本系統資料庫在連線資訊 API 的別名（向系統負責人取得）；`db.schema.itflow=` 後面填 `IM_` 表所在的 schema（表的擁有者，例 `rd_user`；連線帳號是 `ap_user` 時仍填 `rd_user`）。兩個都沒填會啟動失敗 |
| 後端 `config/host.properties` | `rt-api.domain`（協定＋主機）、`db.connect.api.port`（冒號＋port，走預設 port 留空）、`db.connect.api.path`（API 路徑）；`db.connect.api.domain.path` 那行不要動 |
| 殼 jar `application.properties` | 不用改（port 3201、context path `/infra_manager_web` 已寫好） |
| 殼 jar `config/host.properties` | `backend.api.domain=http://localhost`、`backend.api.port=:3202`、`backend.api.path=/api`；`backend.api.domain.path` 那行不要動 |

兩個 `host.properties` 各自的四個鍵都要保留（值可空），刪掉任一個啟動就會失敗。

## 資料庫帳號

只列帳號與用途；密碼向 DBA 取得，執行時手動輸入，不寫進任何檔案或指令。

| 帳號 | 用途 |
|---|---|
| `rd_user` | 執行 DDL（建表、改表） |
| `ap_user` | 應用程式連線用 |

## 資料庫初始化（執行 DDL）

DDL 檔：`db/oracle/V1__init_schema.sql`（建立 31 張表與預載資料，結尾自帶 `COMMIT`）。

1. 確認目標 schema 是空的。DDL 只有 `CREATE`，在已有表的 schema 重跑會報錯
2. 開 cmd，設定用戶端字元集（中文註解與預載資料才不會變亂碼），並確認 log 資料夾存在（第 5 步要用，SQL*Plus 不會自動建資料夾）：
   ```
   set NLS_LANG=TRADITIONAL CHINESE_TAIWAN.AL32UTF8
   if not exist C:\temp mkdir C:\temp
   ```
3. 切到 repo 根目錄，以 `rd_user` 登入（`<連線識別>` 換成 DBA 給的 TNS 名稱或 `host:port/service`；不要在指令裡帶密碼，等提示出現再手動輸入）：
   ```
   sqlplus rd_user@<連線識別>
   ```
4. 關閉替代變數。DDL 目前已不含 `&`，此行是保險，防範日後註解誤加 `&` 時 SQL*Plus 停下來要求輸入變數值：
   ```
   SET DEFINE OFF
   ```
5. 把輸出存成 log 檔（SQL*Plus 遇錯會繼續往下跑，輸出有數百行，不能靠肉眼找錯誤）。log 路徑放在 repo 以外，避免被誤 commit：
   ```
   SPOOL C:\temp\v1_init.log
   ```
   若出現 `SP2-0606`，代表 log 檔建不出來，**先不要執行第 6 步**，回頭確認第 2 步的資料夾
6. 執行 DDL、關閉記錄並離開 SQL*Plus（`EXIT` 會確保 log 檔寫完並關閉；DDL 結尾已自帶 `COMMIT`）。以下三行請逐行貼上，每行貼上後確認已按 Enter 送出：
   ```
   @db\oracle\V1__init_schema.sql
   SPOOL OFF
   EXIT
   ```
7. 回到 cmd 後，在同一個視窗檢查 log，不得出現任何含 `ORA-` 或 `SP2-`（SQL*Plus 本身的錯誤）的行；有輸出代表有錯：
   ```
   findstr /C:"ORA-" /C:"SP2-" C:\temp\v1_init.log
   ```
   DDL 的每個 `CREATE` 會自動 commit，且 SQL*Plus 不會因錯誤而停下，出錯時會留下有缺漏的 schema，而第 1 步禁止在已有表的 schema 重跑：出錯時不要自行補跑，請 DBA 清空該 schema 後從第 1 步重來

## 建立交易測試表（V2）

V1 執行成功後才做。`IM_TX_TEST` 只供整合測試 `TransactionRollbackIT` 使用；沒建這張表時該測試會略過。`ap_user` 須已由 DBA 建立（檔尾的授權行才不會報 `ORA-01917`）。

- **全新環境**（從沒建過 `IM_TX_TEST`）：跑 `V2__tx_test_table.sql`
- **已用舊版 V2 建過表的環境**：改跑 `V2a__tx_test_alter.sql`（補 `UPDATE_DATE`／`UPDATE_BY` 兩欄），**不要重跑 V2**（表已存在會報錯）

步驟同上一節：第 2 步（`NLS_LANG`、log 資料夾）、切到 repo 根目錄、第 3 步以 `rd_user` 登入、第 4 步 `SET DEFINE OFF`，然後依序貼上（全新環境；舊環境把兩處 `V2__tx_test_table` 換成 `V2a__tx_test_alter`），每行貼上後確認已按 Enter 送出：

```
SPOOL C:\temp\v2_tx_test.log
@db\oracle\V2__tx_test_table.sql
SPOOL OFF
EXIT
```

回到 cmd 後檢查 log，有輸出代表有錯：

```
findstr /C:"ORA-" /C:"SP2-" C:\temp\v2_tx_test.log
```

出錯時同上一節：不要自行補跑，請 DBA 清空該 schema 後從 V1 重來。

## 授權 ap_user 存取（執行授權 SQL）

DDL 執行成功（上兩節的 log 檢查都無輸出）後才做，且 `ap_user` 須已由 DBA 建立（否則每一行都會報 `ORA-01917`）。授權檔：`db/oracle/grant_ap_user.sql`，把 V1 的 31 張表與 `IM_TX_TEST` 的 SELECT／INSERT／UPDATE／DELETE 授權給 `ap_user`。本專案不使用 Flyway，新增表時要在授權檔補一行並重跑。

1. 開 cmd，切到 repo 根目錄，設定字元集並確認 log 資料夾存在（同上一節第 2、3 步的 `cd`、`NLS_LANG`、`mkdir`；`@db\oracle\...` 是相對路徑，不在根目錄會報 `SP2-0310`）
2. 以 `rd_user` 登入（表的擁有者才能授權；密碼提示出現再手動輸入）：
   ```
   sqlplus rd_user@<連線識別>
   ```
3. 依序貼上以下四行，每行貼上後確認已按 Enter 送出：
   ```
   SPOOL C:\temp\grant_ap_user.log
   @db\oracle\grant_ap_user.sql
   SPOOL OFF
   EXIT
   ```
4. 回到 cmd 後，在同一個視窗檢查 log，有輸出代表有錯（授權可重複執行，修正原因後整檔重跑即可，不需清空 schema）：
   ```
   findstr /C:"ORA-" /C:"SP2-" C:\temp\grant_ap_user.log
   ```

應用程式連線時以 `rd_user` 的 schema 名稱當前綴存取；不建同義詞。

## 匯入使用者（舊系統 users.json）

把舊 Node 系統的帳號匯進 `IM_USER`／`IM_USER_ROLE_MAP`。用的是同一個後端 jar，以非 web 模式啟動（不開 3202、不影響正在跑的服務），跑完自動結束。**每次匯入都會把檔內全員的密碼重設為「帳號小寫」（預設密碼），已改過密碼的人也會被重設**，匯入前先通知使用者。

1. 先完成「建置與測試」第 1 步（`target/` 內要有 `infra_manager_java-*.jar`），且真實的 `config/host.properties`、`config/database.properties` 已就位（匯入要連公司 Oracle；`db.schema.itflow` 也要設）
2. 準備兩個檔，放在 `db/import/`（此資料夾除 `*.sample.*` 外全部在 `.gitignore`，不管檔名怎麼取都不會進版控）：
   - `users.real.json`：直接複製舊系統的 `data/users.json`。只讀 `id`、`name`、`email`、`title`、`department`、`phone`、`roles`、`active`，其他欄位（含舊 scrypt 雜湊、remember token）忽略
   - `user_mapping.real.csv`：帳號工號對照，UTF-8，第一列表頭固定 `login_id,user_id`，之後每列「舊帳號,工號」（逗號分隔、不要加引號；帳號不分大小寫，工號最長 30 bytes）。範例見 `user_mapping.sample.csv`
   對照表還沒到位時，用 repo 內的 `users.sample.json` ＋ `user_mapping.sample.csv`（只有測試帳號 `wayne`，假工號 `T0001`）
3. 在 cmd 或 Git Bash、repo 根目錄執行（檔案路徑換成實際檔名；cmd 把 `$(ls ...)` 換成實際 jar 檔名）：
   ```
   cd infra_manager_java
   java -jar $(ls target/infra_manager_java-*.jar) --spring.main.web-application-type=none --im.import.users=../db/import/users.real.json --im.import.mapping=../db/import/user_mapping.real.csv
   ```
4. 看最後幾行 log：
   - `匯入完成 新增=… 更新=… 角色新增/啟用=… 角色停用=… 略過=…` 代表成功（結束碼 0），整批已 commit
   - `對照表沒有、未匯入的帳號：…` 列出對照表缺的帳號，補進 csv 後整檔重跑即可（可重複執行，既有帳號走更新）
   - `匯入失敗，共 N 個問題，一筆都未寫入` 後逐條列出原因（未知角色、欄位過長、帳號已屬於別的工號⋯⋯），一筆都不會寫入（結束碼 1）；修正資料後重跑
5. 匯入規則：帳號去頭尾空白、轉小寫（中間空白保留，如 `alan kuo`）存 `LOGIN_ID`；工號由對照表決定；`''` 存 NULL；`active:false` 存 `STATUS=0`；角色以 `IM_ROLE` 主檔為準，不在主檔的角色整批失敗；既有工號走更新，檔內不再出現的角色設 `STATUS=0`；`CREATE_BY`／`UPDATE_BY` 為 `SYSTEM`

## 匯入後重設 identity

匯入舊資料時若對 identity 欄位指定原值，匯入完成後要把該欄的序號推到現有最大值之後，否則下一筆新資料會撞號。此指令需要表的 ALTER 權限，**須以 `rd_user`（表的擁有者）執行**，`ap_user` 會失敗。對每一個指定過原值的 identity 欄執行（`表`、`欄` 換成實際表名與欄名）：

```
ALTER TABLE 表 MODIFY 欄 GENERATED BY DEFAULT ON NULL AS IDENTITY (START WITH LIMIT VALUE);
```

## 查資料庫字元集

已查過：AL32UTF8、STANDARD（單一 `VARCHAR2` 上限 4000 位元組），V1 長文欄位因此改用 `CLOB`；換環境時可用下列指令重查。以任一帳號登入 SQL*Plus 後執行：

1. 資料庫字元集：
   ```
   SELECT VALUE FROM NLS_DATABASE_PARAMETERS WHERE PARAMETER='NLS_CHARACTERSET';
   ```
2. 字串長度上限模式：
   ```
   SELECT VALUE FROM V$PARAMETER WHERE NAME='max_string_size';
   ```
3. 若第 2 步因沒有 `V$PARAMETER` 權限而失敗，改執行：
   ```
   SELECT LENGTHB(RPAD('x', 4001, 'x')) FROM DUAL;
   ```
   結果 `4000` 代表 STANDARD，`4001` 代表 EXTENDED

## 環境變數

本機目前只需要 `JAVA_HOME`（見「設定 JAVA_HOME」）。DB 連線資訊不走環境變數，由連線資訊 API 提供。其他 secret 的變數名稱與用途見 `PRD.md`「環境設定」，本檔不重複一份；實際值向系統負責人取得，不要貼進任何檔案或 commit。

## 建置與測試

以下指令以 Git Bash 為例（cmd 把 `./mvnw` 換成 `.\mvnw.cmd`），每段都從 repo 根目錄開始；先完成「設定 JAVA_HOME」。

1. 後端 `infra_manager_java/`：
   ```
   cd infra_manager_java
   ./mvnw clean verify
   ```
   `verify` 會跑單元測試加整合測試（`*IT`，連公司測試 Oracle），**需要真實的 `config/host.properties` 與 `config/database.properties`**。沒有真檔時 `HealthIT` 會**失敗**（Spring 啟動不了），不是略過；只想跑單元測試改用：
   ```
   ./mvnw clean package
   ```
2. 前端 `infra_manager_web/infra_manager_web_frontend/`：
   ```
   cd infra_manager_web/infra_manager_web_frontend
   npm install
   npm run type-check
   npm test
   npm run build
   ```
   `npm install` 只在第一次、或 `package.json` 有變動時才需要。`npm run build` 會先型別檢查，再輸出到殼 jar 的 `src/frontend/`。
   改畫面時可用熱更新開發：殼 jar 與後端照「啟動與停止」跑起來後，另開視窗執行 `npm run dev`，開 Vite 印出的網址（預設 `http://localhost:5173/infra_manager_web/`）；`/infra_manager_web/api` 的請求會由 `vite.config.ts` 的 proxy 轉給 3201 的殼 jar，改檔立即生效、不必 build。
3. **build 後補回 `.gitkeep`**：`npm run build` 會清空 `infra_manager_web/infra_manager_web/src/frontend/`，連進版控的空檔 `.gitkeep` 一起刪掉。commit 前在 repo 根目錄補回：
   ```
   git restore infra_manager_web/infra_manager_web/src/frontend/.gitkeep
   ```
   補回後 `git status` 不應再出現這個檔的刪除。`start-new.bat`（未加 `--no-build`）也會跑 build，同樣要補。
4. 殼 jar `infra_manager_web/infra_manager_web/`（要先做完第 2 步，jar 內才會有前端靜態檔）：
   ```
   cd infra_manager_web/infra_manager_web
   ./mvnw clean package
   ```

jar 執行中時 Windows 會鎖住 `target/` 內的檔，`clean package` 前先停掉正在跑的程式（見「啟動與停止」）。

## 啟動與停止

1. 完成「設定 JAVA_HOME」（`start-new.bat` 只認 JAVA_HOME）
2. 在同一個 cmd 視窗、repo 根目錄執行：
   ```
   start-new.bat
   ```
   它會：建置前端（`node_modules` 不存在時先 `npm install`）→ 打包後端與殼 jar（略過測試）→ 停掉 3201／3202 上**本專案的** jar → 各開一個視窗啟動後端（3202）與殼 jar（3201）。佔用 port 的若不是本專案的 jar，不會動它，只印 `[WARN]`，並在 port 仍被佔用時以 `[ERROR]` 停止，照畫面提示的 `tasklist`／`taskkill` 指令自行處理。
   已建置過、只想重啟時：
   ```
   start-new.bat --no-build
   ```
3. 瀏覽器開 `http://localhost:3201/infra_manager_web/#/`，首頁「系統狀態」的**後端服務**與**資料庫**都應顯示「正常」
4. 停止：關掉標題為 `infra_manager_java (API 3202)` 與 `infra_manager_web (WEB 3201)` 的兩個視窗
5. log 在啟動目錄所在磁碟機的 `\home\tomcat\log\infra_manager_java\` 與 `\home\tomcat\log\infra_manager_web\`（例：`D:\home\tomcat\log\...`）

舊 Node 系統照舊以其專案的 `start.bat` 啟動，在 port 3200，可同時開著對照。

## 疑難排解

- **`./mvnw verify` 的 IT 出現 `DbConnectException ... API 回應 HTTP 404`**：通常是公司連線資訊 API 的應用程式沒在跑，不是本專案程式的問題；先確認 API 恢復再重跑
- **`clean package` 刪不掉 `target/` 內的 jar**：jar 還在執行、被 Windows 鎖住，先依「啟動與停止」第 4 步停掉
- **首頁後端服務「無法連線」**：確認 3202 的視窗還在、殼 jar `config/host.properties` 填的是 `http://localhost`、`:3202`、`/api`
- **首頁資料庫「無法連線」**：後端活著但查不到 DB；確認後端 `config/database.properties` 的別名、`config/host.properties` 的連線資訊 API 位址，以及 API 是否可達

## 部署

S15 補（Docker 部署至 Rocky Linux 9.7，含切換 runbook）。
