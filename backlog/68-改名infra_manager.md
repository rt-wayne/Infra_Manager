# 第 68 項：專案改名 im_acct_insp → infra_manager

> 狀態：已拍板待實作（使用者 2026-10-15 裁示，分兩批施工）。
> 盤點結果以 2026-10-15 的程式為準（HEAD a44f069），每批動工時重新 grep 一次。

## 待確認

1a. Infra_Manager_Java 的殼 jar 已叫 `infra_manager_web`（context-path `/infra_manager_web`），與本專案改名後的殼 jar 同名。該版未部署，正式與 UAT 不衝突；同一台開發機兩個都跑時，log 會寫進同一個 `/home/tomcat/log/infra_manager_web/`。
2. 本專案 UAT 的附件根目錄現在設在哪、要不要隨改名搬到新目錄（DB 的 `IM_ACCT_ATTACH.FILE_PATH` 存相對路徑，搬檔＋改設定即可，不用動資料）。
3. 批 2 的 Java 套件改名做法（批 2 動工前裁示）。

## 一、背景與裁示（2026-10-15）

- 盤點：`im_acct_insp`（不分大小寫）出現在 221 個檔、991 行、1063 處（行數為 `git grep -ic` 加總、處數為 `git grep -io` 逐次計數；盤點時 HEAD 為 a44f069）。系統尚未上線正式環境，只有本機與 UAT 兩個環境要同步。
- 使用者裁示：
  - ①A 全改，分兩批：先對外可見、再內部搬家（理由：停在一半會永久留下兩個名字）
  - ②A 設定鍵前綴 `im.acct.insp.*` 改為 `infra.manager.*`
  - ③A DB 表前綴 `IM_ACCT_*`／`IM_INSP_*`／`IM_EMG_*` 不動：IM 就是專案名縮寫
  - ④A 歷史記錄不動，PRD 加一句「舊名 im_acct_insp」
  - GitHub repo 改名由使用者在 GitHub 網頁操作，Claude 改完本機 remote 與 PRD 第 4 行「遠端倉庫：」（原定 Claude 用 `gh repo rename`，因本機沒有 `gh`，2026-10-16 改為網頁操作）
  - 其他電腦沒有 clone 過 Infra_Manager_Java（使用者 2026-10-16 確認），改名後只需改本機那份的 remote（已改）
  - 最外層資料夾「機房巡檢與帳號權限管理系統」不改
  - UAT 端（Apache 轉發規則、啟動指令、log 目錄權限、UAT 設定檔）由專案負責人做；Claude 碰不到 UAT 主機，只把步驟寫進 SETUP
  - 殼 jar 與前端名稱比照現行「加 _web 後綴」規則：infra_manager_web、infra_manager_web_frontend
- 瀏覽器分頁標題（`index.html` 的 `<title>`，目前是代號 `im_acct_insp_web`）改為「Infra Manager」（使用者 2026-10-15 裁示 ①B）；信件頁尾「本信件由「機房與帳號權限管理系統」自動發送」不改（使用者 2026-10-15 裁示）
- GitHub repo 名稱衝突已解決（2026-10-16）：Infra_Manager_Java 的 repo 已改名為 `rt-wayne/Infra_Manager_Java` 並封存，本機 origin 已更新（詳見第 69 項詳情檔）。
- UAT 撞名疑慮已排除（2026-10-15）：原 Infra_Manager 在不同主機，跑在 port 3200 的根路徑、沒有網址前綴；Infra_Manager_Java 沒有部署；舊系統是 Node.js，批 2 的 Java 套件 `com.mpx.infra_manager` 不會撞名（Java 改寫版是 `com.mpx.infra_manager_java`）
- 與既有系統的關係（使用者 2026-10-15 說明）：Infra_Manager_Java 是原有 Infra_Manager 改寫到一半的版本；未來方向是本專案完成後把原 Infra_Manager 整併進來（BACKLOG 第 69 項），Infra_Manager_Java 應已無用。程式註解裡「比照 Infra_Manager_Java」的字樣是來源記錄，不改。

## 二、新舊名稱對照表

| 項目 | 舊 | 新 |
|---|---|---|
| 後端 context-path（API 根） | `/im_acct_insp`（`/im_acct_insp/api/v1`） | `/infra_manager`（`/infra_manager/api/v1`） |
| 殼 jar context-path | `/im_acct_insp_web` | `/infra_manager_web` |
| 後端 jar | `im_acct_insp-0.0.1-SNAPSHOT.jar` | `infra_manager-0.0.1-SNAPSHOT.jar` |
| 殼 jar | `im_acct_insp_web-0.0.1-SNAPSHOT.jar` | `infra_manager_web-0.0.1-SNAPSHOT.jar` |
| 後端 log | `/home/tomcat/log/im_acct_insp/im_acct_insp.yyyy-MM-dd.log` | `/home/tomcat/log/infra_manager/infra_manager.yyyy-MM-dd.log` |
| 殼 jar log | `/home/tomcat/log/im_acct_insp_web/im_acct_insp_web.yyyy-MM-dd.log` | `/home/tomcat/log/infra_manager_web/infra_manager_web.yyyy-MM-dd.log` |
| 設定鍵前綴 | `im.acct.insp.*`（含殼 jar `host.properties` 的 `im.acct.insp.path`、`im.acct.insp.domain.path`） | `infra.manager.*`（`infra.manager.path`、`infra.manager.domain.path`） |
| 瀏覽器儲存 key | `im_acct_insp_web.token`、`im_acct_insp_web.identityChanged` | `infra_manager_web.token`、`infra_manager_web.identityChanged` |
| spring.application.name | `im_acct_insp`、`im_acct_insp_web` | `infra_manager`、`infra_manager_web` |
| npm 套件名 | `im_acct_insp_web_frontend` | `infra_manager_web_frontend` |
| Java 套件 | `com.mpx.im_acct_insp`、`com.mpx.im_acct_insp_web` | `com.mpx.infra_manager`、`com.mpx.infra_manager_web` |
| 目錄 | `im_acct_insp\`、`im_acct_insp_web\`、`im_acct_insp_web\im_acct_insp_web\`、`im_acct_insp_web\im_acct_insp_web_frontend\` | `infra_manager\`、`infra_manager_web\`、`infra_manager_web\infra_manager_web\`、`infra_manager_web\infra_manager_web_frontend\` |
| DB 腳本檔名 | `db\im_acct_insp_create_table.sql`、`_init_data.sql`、`_phase2_emg.sql` | `db\infra_manager_*.sql` |
| GitHub repo | `rt-wayne/im_acct_insp` | `rt-wayne/infra_manager` |

## 三、批 1：對外可見＋設定鍵（UAT 負責人只需配合一次部署）

### 要改的地方

- **後端**
  - 本機不進版控的 `application.properties`（spring.application.name、context-path）
  - `config\environment.properties`（鍵名改前綴、`mail.site-url` 值改 `/infra_manager_web/`、附件目錄範例）
  - `pom.xml`（artifactId、name、description）
  - `logback-spring.xml`（LOG_DIR、檔名）
  - 讀設定鍵的 7 支程式：AppConfig、MailConfig、AttachmentService、JobService、MailAllowList、MailHtml、NotificationService
  - 3 支測試：MailConfigContextTest、AttachmentServiceTest、JobAndNotifyTest
  - AttachmentService 錯誤訊息裡的範例路徑 `D:/home/im_acct_insp/attach` → `D:/home/infra_manager/attach`（測試同步）
- **殼 jar**
  - 本機不進版控的 `application.properties`（spring.application.name、context-path）
  - `config\host.properties`（鍵名、`infra.manager.path=/infra_manager/api/v1`）
  - `pom.xml`、`logback-spring.xml`
  - `BackendForwarder.java` 與 `package-info.java` 的設定鍵引用；相關測試
- **前端**
  - `vite.config.ts`（base、dev proxy 路徑；outDir 的目錄路徑留到批 2）
  - `src\api\http.ts`（baseURL）
  - `index.html`（favicon 路徑；title 改「Infra Manager」）
  - `src\api\tokenStore.ts`（兩個 key）
  - `package.json`／`package-lock.json` 的 name
  - `tests\session.spec.ts`
- **GitHub**：✅ 已於 2026-10-16 提前單獨完成（repo 已是 `rt-wayne/infra_manager`，本機 origin 與 PRD 第 4 行已更新；UAT 建置機不從 GitHub 抓程式，不受影響）。以下為當時做法記錄：repo 改名為 `infra_manager`。本機沒有 GitHub CLI（`gh`），改名改由使用者在 GitHub 網頁 Settings 操作（repo 未封存，Repository name 欄位直接可改）；完成後 Claude 執行 `git remote set-url origin https://github.com/rt-wayne/infra_manager.git` 並 fetch 驗證，PRD 第 4 行同步。以 `curl https://api.github.com/repositories/<repo 編號>` 查名稱（本 repo 編號 1412912882，2026-10-16 查 `api.github.com/repos/rt-wayne/im_acct_insp` 取得）；以名稱查 API 改名後短時間會回 Not Found，是快取
- **文件**
  - PRD：網址、jar、log、設定鍵、repo 網址；加一句舊名 im_acct_insp
  - SETUP：第 2、3、4、7、8 節，並新增給負責人的「改名部署步驟」
  - 兩份子 README 的相關行
  - BACKLOG 中描述現況的項目（例如第 56、63、65 項提到的網址或名稱）

### 給 UAT 負責人的步驟（寫進 SETUP）

1. Apache 新增 `ProxyPass /infra_manager http://localhost:<後端 port>/infra_manager`（舊規則待切換完成後移除）
2. 啟動指令改新 jar 名
3. 建立兩個新 log 目錄並給 tomcat 帳號寫入權限
4. UAT 後端 `environment.properties`、殼 jar `host.properties` 的鍵名與值改新名（共用 config 目錄若有 `im.acct.insp.*` 也要改）
5. `mail.site-url` 改新網址
6. 用 SETUP 第 8 節的 curl 驗證

### 使用者感受得到的影響

- 網址改變、舊書籤失效
- 換版當下所有人被登出一次

### 注意事項：設定鍵改名的風險

某環境設定檔沒同步改時，有預設值的鍵會默默退回預設——例如寄件人留空＝只寫 log、不寄信，不會報錯；所以 jar 與設定檔要在同一次部署一起更新。

## 四、批 2：內部搬家（使用者看不到差別）

- `git mv` 四個目錄（約 263 個進版控檔）；Windows 上要先關掉 VS Code 的 Java 語言服務與執行中的 jar，否則可能鎖檔
- Java 套件改名：後端 132 個檔（main 114、test 18）、殼 jar 15 個檔，改 `package`／`import` 與全名引用。做法待使用者批 2 動工前裁示（逐檔 Edit 約需數百次工具呼叫、要拆多個回合）。第 69 項（整併原 Infra_Manager）路線採 C（以本專案為底、從 Infra_Manager_Java 搬業務層），建議批 2 在整併開工前做完，搬進來的程式直接放新套件
- `vite.config.ts` 的 outDir 路徑、`PwdHashTool.java` 註解裡的執行指令類別全名
- DB 腳本 3 個檔改名，連同 SQL 註解、PRD、SETUP、README、BACKLOG（如第 48 項）裡的引用
- 文件中的目錄路徑：PRD、SETUP、兩份子 README、BACKLOG 中描述現況的項目（如第 17、44 項）
- 兩份子 README 標題（`# im_acct_insp 專案說明`、`# im_acct_insp_web 專案說明`）
- PRD 第 3 行的過渡說明句（「網址、jar、log、設定鍵已用新名；目錄、Java 套件與 DB 腳本檔名仍為舊名⋯」）連同「本功能分批施工中」指標句整句刪除（2026-10-16 裁示 ②A：過渡期保留、批 2 結案時刪）

## 五、不動清單

- DB 表前綴 IM_（③A）
- CHANGELOG（只增不改）
- 交付當時的快照（含檔名本身）：`im_acct_insp_交接文件.md`、`帳號權限管理與機房巡檢_SA文件_v0.5.*`、`im_acct_insp_Table關係圖_v0.2.html`、系統說明 pptx
- 程式「變更說明」註解裡引用的 `spec_im_acct_insp*.md` 檔名
- `.gitignore` 的 `im_acct_insp.zip`（原始交付壓縮檔）
- 最外層資料夾
- 角色名「Infra 主管」
- 註解中的 Infra_Manager_Java 字樣

## 六、每批結束

- 平行派 code-reviewer 與 test-runner
- 本機重啟實測：直連後端 `/infra_manager/api/v1/auth/login` 回 401、舊前綴回 404、經殼 jar `/infra_manager_web/api/v1/auth/login` 回 401

## 交接狀態

- 目前階段：批 1 施工中（2026-10-16 開工；回合 1 做完後端，回合 2 殼 jar＋前端，回合 3 文件＋commit；進度細節見 BACKLOG.md 交接狀態）
- 本階段起點：`4fc1f79`
