# SETUP：建置、設定、啟動與 DB 維護

> 本檔只放照著做的步驟。系統規格見 `PRD.md`，待辦見 `BACKLOG.md`。
> 本檔不記載任何 DB 帳密、主機位址或 IP；實際值向系統管理員取得。

## 1. 環境需求

| 項目 | 版本 | 用途 |
|---|---|---|
| JDK | 25 | 後端與殼 jar（`JAVA_HOME` 指向 JDK 25） |
| Maven | 不需安裝 | 兩個 jar 都附 Maven Wrapper（`mvnw`／`mvnw.cmd`，固定 Maven 3.9.16） |
| Node.js | 24 以上 | 前端（`im_acct_insp_web\im_acct_insp_web_frontend\.nvmrc`） |
| Oracle | 19c | schema RD_USER，須已有本模組 21 張表與初始資料（目前 repo 內沒有建表 SQL，見 `BACKLOG.md` 第 6 項） |
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

- 修改時一併填 UPDATE_DATE、UPDATE_BY。
- 建正式環境 DB 時，只帶初始資料，排除測試 DB 才有的帳號與單據（見 `BACKLOG.md` 第 10 項）。

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

有 SYS_JOB_RUN 權限的使用者（初始資料中為 SYS_ADMIN）登入後，以其 token 呼叫：

```
POST /api/v1/jobs/J01/run
POST /api/v1/jobs/J02/run?baseDate=yyyy-MM-dd
```

- 經殼 jar 時路徑前加 `/im_acct_insp_web`；請求須帶 `Authorization: Bearer <token>`。
- baseDate 只在 `im.acct.insp.job.allow-base-date=true` 的環境可用。
- 系統沒有觸發排程的畫面。
