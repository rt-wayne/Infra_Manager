# 第 6 項 S6 新增、編輯草稿（施工計畫）

> 依據：2026-10-06 `architect` 開工前分析，使用者裁示「全部依建議」（①A ②B ③A ④B ⑤A ⑥A ⑦A ⑧A ⑨A）。
> 本檔是施工計畫與交接欄；結案後整檔刪除，歷史進 `CHANGELOG.md`。

## 裁示結果

| 編號 | 題目 | 裁示 | 內容 |
|------|------|------|------|
| ① | 附件怎麼經過殼 jar（原第 79 項） | A | 轉發器請求與回應全面串流（`transferTo`／`exchange` 回呼內同步寫 `HttpServletResponse`，不用 `StreamingResponseBody`）；殼 jar `spring.servlet.multipart.enabled=false`（真檔與 `.example` 同步）；回應 header 白名單加 `Content-Disposition`、`Content-Length`、`X-Content-Type-Options`、`Cache-Control`（順帶完成第 83 項 ②）。否決 B（瀏覽器直連 3202：違反範本、cookie 帶不過去、3202 要對外開）、C1／C2／C3 |
| ② | 上傳 API 形狀 | B | `POST /api/apps`（JSON）建草稿回 `appId`，附件逐檔 `POST /api/apps/{id}/attachments`（一個 part）。否決 A（一次送：30×50 MB 超過 500 MB、撞 part 數上限、一檔失敗整單失敗） |
| ③ | 殼 jar 本文上限（原第 80 項上限部分） | A | 轉發器內擋，非 multipart 1 MB、multipart 51 MB，程式常數；有 `Content-Length` 先判斷、否則邊轉邊計數；超過回 413 `{"message":"請求內容過大"}` 不打後端。可另評估調大 `server.tomcat.max-swallow-size` |
| ④ | 前端字數預檢 | B | 不擋送出，以後端 400 `{message, field, max, actual}` 為準，前端依 `field` 標出並捲到該欄；CLOB 欄不用 HTML `maxlength`（UTF-16 計數與後端 code point 不一致）；字數計數器（C）留細節調整期 |
| ⑤ | 誰能編輯草稿 | A | 只有申請人（同 `AppPermissionService.canEditDraft`）；PRD `PUT /api/apps/{id}` 權限改「申請人」；漏洞第 45 項後半（admin 能開編輯頁但存不了）於回合二完成時結案 |
| ⑥ | 樂觀鎖 | A | S6 就做：detail 回 `rowVerNo`，PUT 帶回；`UPDATE ... SET ROW_VER_NO = ROW_VER_NO + 1 WHERE APP_ID=:id AND ROW_VER_NO=:v AND STATUS=1 AND APP_STATUS_CODE='DRAFT'`，0 列回 409「資料已被他人修改，請重新載入」；子表同交易刪除重建；上傳端點 `FOR UPDATE` 鎖主檔 |
| ⑦ | 沒有機櫃選擇器時的設備位置 | A | 只做「不適用＋原因」與「留空」，S13 前送審不得要求位置必填；PRD 267 行不變 |
| ⑧ | 上傳檔案類型 | A | 後端副檔名白名單＝舊 accept 清單（jpg／jpeg／png／gif／bmp／webp、pdf、doc、docx、xls、xlsx、ppt、pptx、txt、csv、zip）＋`.msg`；MIME 由副檔名對照表決定；下載端不在白名單回 `application/octet-stream`（順帶完成第 95 項 N6） |
| ⑨ | S4 未驗收時的施工順序（B9） | A | 先做回合二（不碰殼 jar 轉發器），S4 驗收後再做回合一；回合二只在檢視 API 多加欄位，S4 驗收改以當時的新版為準 |

## 未回答的待確認（我採用的假設，使用者可隨時推翻）

| # | 問題 | 目前假設 | 影響回合 |
|---|------|---------|---------|
| 1 | 3202 是否只開給殼 jar | 不確定，照 ③A 在殼 jar 也擋 | 一 |
| 2 | 會不會從 VPN／外點上傳大檔 | 上傳呼叫單次 timeout 600000 ms，其餘維持 120000 | 一、四 |
| 3 | 存草稿哪些欄位必填 | 只要求 `APP_TITLE`（DB NOT NULL），其餘到 S7 送審才檢核 | 二 b |
| 4 | 正式殼 jar 的 heap／容器記憶體 | B7 實測用 `-Xmx128m` | 一 |
| 5 | 副檔名白名單有無其他類型 | 照 ⑧A，不加 `.vsdx`／`.7z` | 三 |
| 6 | 申請人姓名可否手改 | 鎖定為登入者（`APPLY_USER_ID` 由 session 決定） | 二 b、四 |
| 7 | 當天超過 999 張 | 自然變 4 位數（`IM20261006-1000`），不報錯 | 二 b |
| 8 | 回合一要不要等 S4 驗收 | 已由 ⑨A 決定：要等 | — |
| 9 | 第 73 項附件根目錄正式路徑 | 開發期用本機 `im.attach.root`；S15 前定 | 三 |

## 回合切分（依 ⑨A，順序為 二 a → 二 b → 一 → 三 → 四）

### 回合二 a：唯讀支援（選項 API、系統參數、檢視 API 補欄位）——已完成（commit `ec19ce5`）
- B1：一般登入者可讀的表單選項 API（含 `formOptionId`，只回 `STATUS=1`）；PRD 的 `GET /api/form-schema`（admin、後台用）不受影響
- B3：SysParam 讀取元件（`FLOW_POLICY`、`UPLOAD_MAX_MB`、`UPLOAD_MAX_FILES`），讀不到退回 PRD 預設值
- B2：`AppDetail` 補 `rowVerNo`，`AppDetail.Option` 補 `formOptionId`
- 完成判定：`./mvnw -q clean package` 全綠

### 回合二 b：建草稿與編輯草稿（JSON）
- `POST /api/apps` 建草稿：編號計數器（同一交易：UPDATE `LAST_NO+1` → 0 列則 INSERT 1、遇 `DuplicateKeyException` 重做 UPDATE → SELECT；日期用 `TaiwanTime`）、套 `FLOW_POLICY`（full_only → `full`；by_priority → 該優先等級選項的 `FLOW_ID`）、`APPLY_DATE` 由伺服器設、申請人鎖定登入者
- `PUT /api/apps/{id}`：樂觀鎖（⑥A）、只有申請人（⑤A）、非 DRAFT 409、子表同交易刪除重建
- B8：VARCHAR2 長度檢查（CHAR 語意用 code point，byte 語意的 `APPLY_EMAIL`／`SITE_ID`／`RACK_ID` 用 UTF-8 byte），錯誤格式同 PRD；CLOB 照 `TextLength`
- 整合測試 `AppSeqIT`（`mvnw verify`）：2099 年假日期、兩執行緒同時取號得 1 與 2、軟刪除後再建號碼往上加、結束清掉假資料
- 單元測試：409 版本衝突、非申請人 403、非 DRAFT 409、超長欄位 400 且 `field` 正確
- 建單交易內不放落檔等慢動作（UPDATE 會鎖住當天計數列）

### 回合一：殼 jar 串流透傳＋上限＋下載修正（S4 驗收後才做，建議 `/effort xhigh`）
- ①A、③A；後端 N5（`Files.size`，與 DB 值不同寫 warn）、N6（下載 MIME 白名單）；前端啟用下載按鈕（`AppViewView.vue` 附件區）
- 第一步：在 `ApiProxyControllerTest` 先加「multipart 請求轉到後端本文不得為空」並確認現在會失敗
- 測試：multipart 透傳本文不為空、1 MB／51 MB 邊界 413、`Content-Disposition` 透傳；B6 用慢速假後端量 timeout 涵蓋範圍
- 手動（B7）：殼 jar `-Xmx128m`，經 3201 傳下載 50 MB×3，sha256 一致

### 回合三：附件上傳端點（建議 `/effort xhigh`）
- `POST /api/apps/{id}/attachments` 一次一檔：副檔名白名單（⑧A）、檔數與大小依 SysParam、`FOR UPDATE` 鎖主檔並確認 DRAFT、只有申請人
- 落檔 `<root>/<APP_ID>/<uuid>.<ext>`：先寫暫存、邊寫邊算 SHA-256，再搬移；DB 寫入失敗刪檔；原始檔名清路徑與控制字元、截 255 字
- B5：CSRF 改只讀 header 的 resolver，測試「無 CSRF header 的 multipart 不產生暫存檔」
- 測試：白名單外 400、超過檔數 400、非 DRAFT 409；手動經 3201 上傳後下載 sha256 一致

### 回合四：前端 AppFormView（新增與編輯）
- 表單欄位、設備表格、計畫步驟、日期時間；位置「不適用＋原因」（⑦A）
- 附件：選檔（拖放、貼上超出規模時降到細節調整期）、預檢檔數／大小／副檔名、逐檔上傳顯示進度
- 400 依 `field` 標欄位、409 提示重新載入
- 完成判定：建置與型別檢查通過、無 `any`、1024 寬不橫向捲動；手動新增含 3 個附件的草稿再編輯成功、兩分頁先後存檔第二個看到 409

## 風險
- 「本文還沒傳完就回 413」在瀏覽器可能顯示成連線錯誤，所以前端預檢必做
- 殼 jar 到 3202 是 http 明文，開放附件後份量變重（第 80 項）
- 正式機若有全域 JVM 代理設定，串流大檔可能被截斷或緩衝，部署前確認 JVM 參數
- 第 73 項附件根目錄未定，正式部署前一定要定

## 交接
- 目前回合：二 b，因規模拆兩半（2026-10-07）：**二 b-1**＝編號計數器＋`POST /api/apps` 建草稿（主檔＋6 張子表 `CATG_MAP`／`CATG_OTHER`／`REASON_MAP`／`SCOPE_MAP`／`EQUIP`／`PLAN_STEP` 寫入、B8 長度檢查、套 `FLOW_POLICY`）＋`AppSeqIT`；**二 b-2**＝`PUT /api/apps/{id}`（樂觀鎖、只有申請人、非 DRAFT 409、子表刪除重建，共用 b-1 的寫入與檢查元件）。下一步：二 b-1
- 二 b-1 開工前要讀：`DbClient` 寫入 API（`update`／批次）、`util/TextLength`、`web/ApiExceptionHandler`（400 `{message, field, max, actual}` 已有？）、`AuthUser`、`TaiwanTime`、`AppController`、`AppDao`（欄位對應）、V1 的 `IM_FLOW`／`IM_FORM_OPTION.FLOW_ID`（by_priority 取流程）。表欄位已查：V1 第 397～711 行
- 二 a 已完成：`GET /api/form-options`（`FormOptionController`／`FormOptionService`／`FormOptionDao`）、`SysParamService`（`uploadMaxMb`／`uploadMaxFiles`／`flowPolicy`，二 b 建草稿套流程時直接用 `flowPolicy()`）、檢視 API 補 `rowVerNo`／`formOptionId`、前端型別同步；後端測試 209、前端 35
- 二 b 已改動：（無）
- 卡住／待確認：上方「未回答的待確認」第 1～7、9 題（目前照假設施工）
