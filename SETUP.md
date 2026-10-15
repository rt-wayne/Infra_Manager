# SETUP：建置、設定、啟動與 DB 維護

> 本檔只放照著做的步驟。系統規格見 `PRD.md`，待辦見 `BACKLOG.md`。
> 本檔不記載任何 DB 帳密、主機位址或 IP；實際值向系統管理員取得。

## 1. 環境需求

| 項目 | 版本 | 用途 |
|---|---|---|
| JDK | 25 | 後端與殼 jar（`JAVA_HOME` 指向 JDK 25） |
| Maven | 不需安裝 | 兩個 jar 都附 Maven Wrapper（`mvnw`／`mvnw.cmd`，固定 Maven 3.9.16） |
| Node.js | 24 以上 | 前端（`im_acct_insp_web\im_acct_insp_web_frontend\.nvmrc`） |
| Oracle | 19c | schema RD_USER，須已有本模組 23 張表與初始資料（建表與初始資料 SQL 在 `db\`，用法見第 6 節；只有第一階段 21 張表的環境先跑第 6 節的升級腳本） |
| DB 連線資訊 API | — | 後端執行期向它取得 DB 連線資訊，須能連到 |

## 2. 設定檔

實際設定檔不進 git，repo 內也沒有範本。首次建置時向專案負責人取得下列 6 個檔，放到對應位置後，依各環境改值（各 key 用途見 `PRD.md`「環境設定」）。

| 目錄 | 檔案 | 必填重點 |
|---|---|---|
| `im_acct_insp\src\main\resources\` | `application.properties` | `server.port`；`server.servlet.context-path=/im_acct_insp`（少了這行，殼 jar 轉送會回 404）；要實際寄信填 `spring.mail.host=mail.pxmart.com.tw` |
| `im_acct_insp\src\main\resources\` | `config\host.properties` | DB 連線資訊 API 的三個 key；四個 key 都要保留（值可空） |
| `im_acct_insp\src\main\resources\` | `config\database.properties` | 取消 `itflow.db.alias` 的註解並填入 DB 別名 |
| `im_acct_insp\src\main\resources\` | `config\environment.properties` | `im.acct.insp.attach.dir` 填附件根目錄絕對路徑；寄信的寄件人與寄送名單見下方 |
| `im_acct_insp_web\im_acct_insp_web\src\main\resources\` | `application.properties` | `server.port`；`spring.servlet.multipart.enabled` 保持關閉 |
| `im_acct_insp_web\im_acct_insp_web\src\main\resources\` | `config\host.properties` | 後端 API 的三個 key；四個 key 都要保留。`backend.api.domain` 只寫協定＋主機、不帶 port；`im.acct.insp.path` 含後端前綴，填 `/im_acct_insp/api/v1` |

**既有環境升級（2026-10-14 起的版本）**：DB 別名的 key 由 `db.connect.itflow` 改名為 `itflow.db.alias`。已有 `config\database.properties` 的環境（各開發機、UAT／正式的建置機），重新建置前把該行 key 改名、值不變（例 `db.connect.itflow=itflow_uat` → `itflow.db.alias=itflow_uat`）。沒改的話建置會成功、但後端啟動失敗，log 出現 `Could not resolve placeholder 'itflow.db.alias'`。

- 附件根目錄在 Windows 要用 `/` 或 `\\`（例 `D:/home/im_acct_insp/attach`）；寫成單一 `\` 會被當跳脫字元，變成相對路徑而啟動失敗。目錄須讓執行帳號可讀寫。
- 測試環境若要用 baseDate 手動跑排程，把 `im.acct.insp.job.allow-base-date` 設為 true；正式環境保持 false。J03（緊急存取逾期通知）預設每 15 分鐘跑一次，不想在本機跑就把 `im.acct.insp.job.j03.cron` 設為 `-`。
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
- port 取自 `server.port`，也可加 `--server.port=xxxx` 覆蓋。API 根為 `http://主機:port/im_acct_insp/api/v1`（前綴來自 `server.servlet.context-path`）。
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
- 開發時可另開 `npm run dev`（Vite dev server），`/im_acct_insp_web/api` 會 proxy 到本機殼 jar，殼 jar 須先啟動。proxy 目標寫在 `im_acct_insp_web_frontend\vite.config.ts` 的 `server.proxy`（目前 `http://localhost:8787`），殼 jar 改 `server.port` 時要一起改，否則 API 會連不上。
- 殼 jar log：`/home/tomcat/log/im_acct_insp_web/im_acct_insp_web.yyyy-MM-dd.log`（Windows 依啟動磁碟機），保留 30 天。
- 用殼 jar 開的畫面空白、樣式或圖片沒出來時，按 F12 看 Console：出現 `Content Security Policy`（Chrome／Edge）或 `Content-Security-Policy`（Firefox）字樣的錯誤，代表前端用了外站資源、行內程式、`<style>` 區塊或 data: 圖片，被殼 jar 的安全標頭擋下（HTML 的 style 屬性已放行）。`npm run dev` 開的畫面不經過殼 jar、不帶這個標頭，所以這類問題開發時看不到，只在殼 jar 開的畫面出現。放寬步驟：
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

1. `db\im_acct_insp_create_table.sql`：在空 schema 建 23 張表、索引、5 個序列與欄位 COMMENT（需 CREATE TABLE、CREATE SEQUENCE 權限與表空間配額）。本檔沒有遇錯即停，且 DDL 每句自動提交：中途失敗時，先 DROP 已建立的表與序列（或請 DBA 清空 schema）再從頭重跑，否則已存在的表會報 ORA-00955。
2. `db\im_acct_insp_init_data.sql`：代碼表 116 筆初始資料（檔首 `WHENEVER SQLERROR EXIT ROLLBACK`，遇錯即停並回滾）。

**既有環境升級到第二階段緊急存取**（已照上面建好 21 張表的測試、正式環境）：以 RD_USER 執行 `db\im_acct_insp_phase2_emg.sql`（SQL Developer 用「執行指令碼」F5；SQL*Plus 同樣先 `set NLS_LANG=.AL32UTF8`）。本檔可重跑：表與索引已存在就印「已存在，略過」，COMMENT 重覆執行無害；本檔也會建兩張新表的 PUBLIC synonym 並授權給殼 jar 連線帳號 AP_USER（`IM_EMG_ACCESS` 授 SELECT／INSERT／UPDATE、`IM_EMG_AFFECT_ACCT` 授 SELECT／INSERT；比照第一階段的 PUBLIC synonym 建法，RD_USER 自己有 CREATE PUBLIC SYNONYM 權限）。檔尾五句查詢會列出兩張表、三個 IND_ 索引（主鍵索引已排除）、兩表欄數（30、11）及無 COMMENT 欄位數（期望 0）、兩個 PUBLIC synonym、AP_USER 的 5 筆授權供核對，期望值寫在該段註解。檔頭設了遇錯即停、檔尾已解除，中途失敗直接從頭重跑。全新環境用建表檔建完表後**仍須跑本檔**：建表檔沒有 synonym 與授權，本檔重跑時表與索引會印「已存在，略過」，只補上這兩張表的 synonym 與 GRANT；第一階段 21 張表的 synonym 與授權建法仍未記載（見 `BACKLOG.md` 第 48 項）。

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

**查每個人實際生效的角色**（只算啟用中的人員指派與角色；GENERAL 不存這張表、所有啟用中使用者自動擁有，所以查不到是正常的）

```sql
SELECT U.USER_ID, U.USER_EN_NAME, U.USER_NAME, U.STATUS,
       LISTAGG(R.ROLE_ID, ',') WITHIN GROUP (ORDER BY R.SORT_SEQ) AS ROLES
  FROM IM_ACCT_USER U
  LEFT JOIN IM_ACCT_ROLE_USER RU ON RU.USER_ID = U.USER_ID AND RU.STATUS = 1
  LEFT JOIN IM_ACCT_ROLE R ON R.ROLE_ID = RU.ROLE_ID AND R.STATUS = 1
 GROUP BY U.USER_ID, U.USER_EN_NAME, U.USER_NAME, U.STATUS
 ORDER BY U.USER_ID;
```

**查某個權限碼給了哪些角色**（例：ACC_LIST_FULL、ACC_APPLY；再用上一句找有該角色的人）

```sql
SELECT RP.ROLE_ID, P.PERM_NAME
  FROM IM_ACCT_ROLE_PERM RP
  JOIN IM_ACCT_ROLE R ON R.ROLE_ID = RP.ROLE_ID AND R.STATUS = 1
  JOIN IM_ACCT_PERM P ON P.PERM_ID = RP.PERM_ID AND P.STATUS = 1
 WHERE RP.PERM_ID = '【權限碼】' AND RP.STATUS = 1;
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

**10. 讓排程信重寄**（J01、J02、J03 只要通知紀錄裡有任何一筆就不再寄，連寄送失敗、收件人沒有 Email、不在寄送名單而略過的也算；修好 SMTP 或改了寄送名單後要重測，先刪掉那一輪的紀錄，再依第 7 節觸發排程。紀錄刪了就查不回來，先用第一句確認要刪的筆數）

```sql
-- 事件代碼：J02 每週彙總＝WEEKLY_SUMMARY（參照鍵＝該週週一，例 20261005）；
--           J01 逾期信＝EXPIRED、到期前 7 天信＝EXPIRY_7D（參照鍵＝帳號單號_到期日，中間無空格，例 AR20261001-001_20261006）；
--           J03 緊急存取逾期信＝EMG_OVERDUE（參照鍵＝緊急存取單號，例 EA20261013-001）
SELECT RCPT_USER_ID, SEND_DATE, RESULT_CODE, ERR_MSG
  FROM IM_ACCT_NOTIFY_LOG
 WHERE EVENT_CODE = '【事件代碼】' AND REF_ID = '【參照鍵】';

DELETE FROM IM_ACCT_NOTIFY_LOG
 WHERE EVENT_CODE = '【事件代碼】' AND REF_ID = '【參照鍵】';
-- 影響筆數與上一句相同再 COMMIT
```

- J01 刪了紀錄後，帳號要仍符合條件才會重寄：逾期信要帳號仍是已逾期；7 天提醒要仍是已核准、且到期日落在基準日到基準日＋7 天之間（可用第 7 節的 baseDate 調整基準日，但先看該處的粗體警告：帶未來日期會把帳號轉成逾期，且無法復原）。不符合時觸發排程也不會寄，不代表 SMTP 有問題。
- J03 刪了紀錄後，該單要仍是「逾期未補核准」（主管還沒補核准或退回）才會重寄；已處理的單刪了也不會再寄。J03 預設每 15 分鐘自動跑，刪完紀錄後不手動觸發也會在下一輪補寄。

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

系統沒有觸發排程的畫面，以 curl 呼叫 API。需 SYS_JOB_RUN 權限（初始資料中只有 SYS_ADMIN 角色有）。以下以直連後端（`server.port` 8989、context-path `/im_acct_insp`）為例；經殼 jar 時把 `http://localhost:8989/im_acct_insp` 換成殼 jar 的位址加 `/im_acct_insp_web`（例 `http://localhost:8787/im_acct_insp_web/api/v1/...`）。指令為 Git Bash 寫法，`【】` 換成實際值。

密碼與 token 都只放在 shell 變數裡，不直接寫進指令，所以不會留在 shell 歷史；密碼經管線送給 curl，也不會出現在程序的命令列（token 會，但登出後即失效）。全部做完在第 3 步清掉變數。下面三步要在**同一個 Git Bash 視窗**依序執行。每個 curl 都加了 `-w`，回應內容的最後一行是 HTTP 狀態碼（例如 `200`、`401`）。

**1. 登入取 token**（分兩次執行：先單獨執行第一行並輸入密碼，再貼下面那段——整塊一起貼的話，有些終端機會把下一行指令當成密碼讀走）

```bash
IFS= read -rs -p "密碼: " PW; echo
```

輸入時不回顯，按 Enter 結束；密碼前後的空白會原樣保留。接著貼：

```bash
PWJ=${PW//\\/\\\\}; PWJ=${PWJ//\"/\\\"}
RESP=$(printf '{"userId":"%s","password":"%s"}' '【工號】' "$PWJ" \
  | curl -s -w "\n%{http_code}" -X POST http://localhost:8989/im_acct_insp/api/v1/auth/login \
      -H "Content-Type: application/json" --data-binary @-)
unset PW PWJ
TOKEN=$(echo "$RESP" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
echo "$RESP" | tail -n 1
```

- 第一行把密碼裡的 `\` 與 `"` 先跳脫，含這兩個字元的密碼也能登入。
- 最後印出 `200` 就是成功，token 已存進變數 `TOKEN`，下一步直接用；要確認有沒有存到，用 `echo ${#TOKEN}` 看長度（應為 43），不要把 token 本身印出來。不是 200 時用 `echo "$RESP"` 看錯誤訊息。
- 印出 `000`＝連不上後端（沒啟動或位址錯），這時 `$RESP` 也沒有內容。
- 印出 `404`＝網址前綴和後端的 `server.servlet.context-path` 對不上（網址少了 `/im_acct_insp`，或後端沒設 context-path）。
- 工號不存在、密碼錯、使用者停用都回 401 `{"message":"帳號或密碼錯誤"}`；其中只有「密碼錯」每次算一次輸錯，連錯 5 次會被鎖定（工號不存在或已停用不計次）。被鎖定回 401「此帳號已鎖定，請聯絡系統管理員」（解鎖見第 6 節第 3 段）。
- 回 400「請求內容格式錯誤」＝密碼含 Tab 之類的控制字元，組出的 JSON 格式不合法、後端拒收，請找開發人員協助。

**2. 觸發排程**

```bash
# J01：到期帳號轉逾期、寄逾期信與到期前 7 天提醒（漏跑會補寄、不重寄）
curl -s -w "\n%{http_code}\n" -X POST http://localhost:8989/im_acct_insp/api/v1/jobs/J01/run \
  -H "Authorization: Bearer $TOKEN"

# J02：每週逾期帳號彙總信，同一週只寄一次（本週寄送失敗、收件人沒有 Email 或不在寄送名單也算已寄，同週不會重寄）
curl -s -w "\n%{http_code}\n" -X POST http://localhost:8989/im_acct_insp/api/v1/jobs/J02/run \
  -H "Authorization: Bearer $TOKEN"

# J03：緊急存取逾期未補核准通知，每張單只寄一次（寄送失敗、收件人沒有 Email 或不在寄送名單也算已寄，不會重寄）
curl -s -w "\n%{http_code}\n" -X POST http://localhost:8989/im_acct_insp/api/v1/jobs/J03/run \
  -H "Authorization: Bearer $TOKEN"
```

- 成功回 200，JSON 為 `runId`（執行紀錄序號）、`resultCode`（`SUCCESS`／`PARTIAL`／`FAIL`）、`affectedCnt`。
- `affectedCnt` 的意思：J01＝轉逾期的帳號數，加上本次有寫逾期信或 7 天提醒通知紀錄的帳號數（每個帳號算 1，不論收件人幾位；寄失敗、收件人都沒 Email 或不在寄送名單而略過的也算；同一帳號可能被算兩次）；J02＝彙總信列出的逾期帳號數，不是寄出封數；J03＝本次有寫逾期通知紀錄的緊急存取單數（每張算 1，不論收件人幾位；收件人沒 Email 或不在寄送名單而略過、寄失敗的也算；寄前發現主管已處理的單不算）。三者回 `SUCCESS` 都不代表信已寄達。
- J03 回 `PARTIAL` 時看 `IM_ACCT_JOB_RUN.ERR_MSG`：「{單號}:未寫入通知紀錄」＝沒有任何人持有 EMG_DECIDE 權限、通知紀錄寫不進 DB、或重讀單據／查收件人／寄信途中出錯，該單下一輪會再試（沒人有權限時每 15 分鐘都會 PARTIAL，但排程失敗信一天只寄一封）；「{單號}:全部收件人寄送失敗」＝紀錄已寫、沒人收到信，不會再寄，要補寄依第 6 節第 10 段刪紀錄。「{單號}:例外類別」是程式內部防線，一般不會出現；看到就把後端 log 一起交給開發人員。
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
  - 404：代碼不是大寫的 `J01`／`J02`／`J03`（`j01` 也是 404）。
  - 409 E16「排程執行中，請稍後再試」：同一排程正在執行。
  - 400 E17「此環境不允許指定基準日」：見下方 baseDate。
  - 500「系統發生錯誤，請稍後再試或聯絡系統管理員」：多半是連不上 DB，連開始紀錄都寫不進去，所以排程沒有執行、也沒有 `runId`。後端 log 會有「開始紀錄寫入失敗」，系統也會試著寄 JOB_FAIL 信給系統管理員（DB 全斷時寄不出）。DB 恢復後重新觸發即可。
- 後端在排程跑到一半時被關掉或當掉，那筆執行紀錄會停在 `RUNNING`。下次同一排程開跑（排程或手動都算）會先把它改成 `FAIL`、錯誤摘要「上次執行未正常結束（下次開跑時補記）」，並寄一封 JOB_FAIL 信；這筆舊紀錄不用手動處理。
- 回 200（`resultCode` 可能是 `SUCCESS`）但那筆紀錄停在 `RUNNING`＝結束紀錄寫入失敗。系統管理員會收到主旨「【排程失敗】{排程代碼} 結束紀錄寫入失敗」的信，實際結果以那封信與 API 回應為準；下次開跑補記成 `FAIL` 時不會再寄。例外：DB 整個斷線時可能收不到這封信，改在下次開跑時收到「上次執行未正常結束」；信寄出了但通知紀錄沒寫進去時，下次開跑會再收到一封。
- J03 的排程失敗信同一種失敗一天只寄一封，所以同一天第二次 PARTIAL 不會再收到信，要看結果請查 `IM_ACCT_JOB_RUN`（每次執行都有紀錄）。「同一種」＝信裡的「結果」加上例外類別：PARTIAL 不分原因算一種；FAIL 與開始紀錄寫入失敗合併計算，同一個例外類別算一種（DB 間歇性斷線一整天只有一封；DB 完全斷線時查不到收件人，一封都寄不出）；結束紀錄寫入失敗不論結果是 SUCCESS、PARTIAL 或 FAIL，都依結束紀錄那個例外另算一種，所以當天已收過 PARTIAL 信，再遇到 PARTIAL 加結束紀錄寫入失敗仍會收到一封。沒有寄到任何人（SMTP 全掛、或沒人有 SYS_ADMIN）不算已寄：下一輪若仍是同一種失敗會再寄，已恢復正常的那次不會補寄。後端重啟後當天可能多收一封。J01、J02 每次失敗都寄。
- 上一段「下次開跑補記成 `FAIL` 時不會再寄」對 J03 有一個例外：J03 被「一天一封」擋下、沒寄出結束紀錄寫入失敗那封的那幾輪，紀錄一樣停在 `RUNNING`，下一輪開跑補記時仍會收到「上次執行未正常結束」的信（這封以執行序號去重、不受一天一封限制）。這不是重複故障，是同一件事的補記。

**指定基準日（僅測試環境）**：`im.acct.insp.job.allow-base-date=true` 的環境可在網址加 `?baseDate=`，以該日（J01、J02）或該時間點（J03）代替現在執行；正式環境為 false，帶了一律回 400 E17。格式不對回 400。

```bash
# J01、J02：日期
curl -s -w "\n%{http_code}\n" -X POST "http://localhost:8989/im_acct_insp/api/v1/jobs/J01/run?baseDate=【yyyy-MM-dd】" \
  -H "Authorization: Bearer $TOKEN"

# J03：時間點，格式 yyyy-MM-ddTHH:mm:ss（秒可省；T 也可換成空白，但網址裡要寫成 %20）；只給日期回 400
curl -s -w "\n%{http_code}\n" -X POST "http://localhost:8989/im_acct_insp/api/v1/jobs/J03/run?baseDate=【yyyy-MM-dd】T【HH:mm:ss】" \
  -H "Authorization: Bearer $TOKEN"
```

- **J01 帶未來日期會把到期日早於該日的帳號全部轉成逾期**、寫入帳號歷史並寄通知，系統沒有復原功能；只在確定要模擬那一天時使用。
- **J03 帶未來時間點會替目前還沒逾期、但在那個時間點已過期限的單寄逾期信並寫 EMG_OVERDUE 紀錄，之後它真的逾期時不會再寄**；單本身不會被改動。要恢復可依第 6 節第 10 段刪掉該單的 EMG_OVERDUE 紀錄。

**3. 用完登出並清掉變數**（不登出的話 token 最長 8 小時內有效；登出後立即失效）

```bash
curl -s -w "\n%{http_code}\n" -X POST http://localhost:8989/im_acct_insp/api/v1/auth/logout \
  -H "Authorization: Bearer $TOKEN"
unset TOKEN RESP
```

- 成功回 200、沒有內容。

## 8. UAT 部署（經 Apache 轉發）

UAT 上兩個 jar 由 tomcat 帳號以 `java -jar -Dserver.port=<port> <jar> --spring.config.name=application,database,host,environment,... --spring.config.additional-location=<共用 config 目錄>` 啟動，port 由啟動參數指定（會蓋過 `application.properties` 的 `server.port`）。殼 jar 不直連後端，而是經 Apache 轉發：

```
殼 jar → http://<Apache 主機>:<Apache port>/im_acct_insp/api/v1/...
       → Apache：ProxyPass /im_acct_insp http://localhost:<後端 port>/im_acct_insp（前綴原樣保留）
       → 後端 /im_acct_insp/api/v1/...（context-path /im_acct_insp）
```

- **後端必須有 context-path `/im_acct_insp`**：Apache 轉過去時不拿掉前綴，後端沒有前綴就一律回 404。前綴寫在後端 `application.properties` 的 `server.servlet.context-path=/im_acct_insp`，隨 jar 打包，啟動指令不用加任何參數。該檔不進 git，打包前先確認建置機那份有這一行。
- **不要把 context-path 寫進共用 config 目錄的設定檔**：該目錄所有 jar（含其他系統）都會讀，寫進去它們會一起多一段前綴而壞掉。反過來，共用目錄任何一個檔（不限 `application.properties`）若有這個 key，會蓋掉 jar 內的那一行。部署前兩項檢查：`grep -rn "context-path" <共用 config 目錄>` 應無輸出；`unzip -p <後端 jar> BOOT-INF/classes/application.properties | grep context-path` 應印出 `server.servlet.context-path=/im_acct_insp`。
- 殼 jar 的 `config\host.properties`：`backend.api.domain` 直接填 Apache 的協定＋主機實際值（不帶 port），**不要沿用 `${itflow.domain}`**：共用 config 目錄的檔案優先於 jar 內設定，`itflow.domain` 是共用 key，值由其他系統決定，可能不是 Apache。`backend.api.port` 填 `:<Apache port>`；留空會連到該協定的預設 port（http 為 80、https 為 443），不是 Apache 這個 port。`im.acct.insp.path` 填 `/im_acct_insp/api/v1`。
- 實際 port 查法：`ps -ef | grep -v grep | grep java | grep -o -- '-Dserver.port=[0-9]*'` 只取出各 jar 的 port（完整的 `ps` 輸出含同台所有系統的啟動參數，可能夾著密碼，不要整段貼到工單或對話）；`ss -ltnp` 看哪些 port 在聽；Apache 規則用 `grep -ri proxypass /etc/httpd/` 查。
- 改完 `host.properties` 要重啟殼 jar；改後端 `application.properties` 要重新打包、部署後端 jar 再重啟。

**驗證**（在 UAT 主機上，`<>` 換成實際值）：

```bash
# 直連後端（有前綴）
curl -s -o /dev/null -w '%{http_code}\n' -X POST -H 'Content-Type: application/json' -d '{}' http://localhost:<後端 port>/im_acct_insp/api/v1/auth/login
# 經 Apache（殼 jar 實際打的網址）
curl -s -o /dev/null -w '%{http_code}\n' -X POST -H 'Content-Type: application/json' -d '{}' http://<Apache 主機>:<Apache port>/im_acct_insp/api/v1/auth/login
```

| 結果 | 代表什麼 |
|---|---|
| 401 或 400 | 正常：已打到後端登入 API，空帳密被拒 |
| 直連 404 | 後端沒有 context-path（見上方第一點）、共用 config 目錄有 `server.servlet.context-path` 蓋掉 jar 內設定（用上方 grep 查），或 port 指到同台別的系統的 jar（用 `ss -ltnp` 對照） |
| 直連通、經 Apache 404 | Apache 規則的前綴不對 |
| 經 Apache 503 或 502 | Apache 連不到後端：後端沒在跑，或 ProxyPass 的目標 port 錯 |
| 000 | 連不上（只會出現在直連，或 Apache 本身連不上時）：程序沒在跑、port 錯或防火牆擋住 |

殼 jar log 出現含 `POST /auth/login backend status 404` 的 WARN 行，就是上面其中一種對不上；`503`、`502` 同理對照上表。
