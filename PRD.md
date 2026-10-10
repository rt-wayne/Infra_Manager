# im_acct_insp 帳號權限管理／機房巡檢系統

> 系統現況以本檔為準；原始交付文件（`im_acct_insp_交接文件.md`、`帳號權限管理與機房巡檢_SA文件_v0.5.md`、`im_acct_insp_Table關係圖_v0.2.html`）僅供追溯。
> 遠端倉庫：https://github.com/rt-wayne/im_acct_insp.git
> 待辦見 `BACKLOG.md`；版本與變更歷史見 `CHANGELOG.md`；建置、設定、啟動與 DB 維護步驟見 `SETUP.md`。

## 專案概述

資訊處用來管理「被管理的系統帳號、憑證、機房門禁卡」的 Web 系統，另含機房巡檢紀錄。帳號部分涵蓋申請、7 關簽核、生效、到期提醒與歷史；巡檢部分涵蓋填寫、查詢、異常標記處理。使用者是資訊處同仁：申請人、各關簽核主管、關卡 6 執行人、資訊治理、巡檢人員與系統管理員。

本 repo 只含兩個模組：**帳號權限管理**與**機房巡檢**。同一個 DB schema 裡另有既有的「機房異動申請」模組，不在本 repo、不在本文件範圍。

## 背景與動機

帳號、憑證、門禁卡的開通與異動過去缺少一致的申請簽核與到期追蹤，稽核時難以回答「這個帳號是誰申請、誰核准、何時生效、何時到期」。本系統把每一次異動做成走固定 7 關的申請單，關卡 6 執行完畢才寫進帳號清冊，並以排程追蹤到期，所有動作留下只增不改的歷史；巡檢則把紙本巡檢表數位化，異常項目可追蹤到處理完成。

本案由前任開發者完成第一階段並交付，目前由接手者維護。

### 刻意偏離 SA v0.5 的決定

| # | SA 規則 | 現況 |
|---|---|---|
| 1 | 登入與使用者管理與其他模組共用，本模組不開發 | 自建 IM_ACCT_USER、IM_ACCT_LOGIN_TOKEN；取身分集中在單一介面（`IdentityProvider`），日後換共用登入只換實作 |
| 2 | 毫秒時間戳 | 一律 DATE（到秒）；同秒事件以流水號排序 |
| 3 | 角色、執行人對照由畫面維護、每次變更寫軌跡、原因必填 | 純 DB 維護，不留軌跡；管理軌跡只記切換身分／切回 |
| 4 | 移除角色最後一位成員時，若有在途項目卡在該角色則拒絕 | 不檢查；改 DB 的人自行確認 |
| 5 | J01 單筆：狀態、歷史、通知紀錄同一交易 | 狀態＋歷史同一交易；寄信與通知紀錄在交易提交後，漏寄由下次執行補上 |
| 6 | §3.2 代理操作一律顯示「Hank（以 Gary 身分）」格式 | 帳號歷史沿用 SA 格式；**巡檢**的填寫人與處理人改顯示「{代理身分}（實際操作人：{實際操作人}）」（見「切換身分」） |
| 7 | §4.3 權限清單 | 另加 ACC_EXPIRY_ALL（到期提醒看全部帳號）、SYS_JOB_RUN（手動觸發排程）兩個權限碼 |

### 系統不提供的功能

| 項目 | 現況 |
|---|---|
| 維護畫面 | 角色指派、執行人對照、使用者（含重設密碼、解鎖）、巡檢項目、資產分類、帳號類別開關：一律 DB 直接維護，無 API、無畫面、不寫管理軌跡（步驟見 `SETUP.md`） |
| 帳號匯入、既有帳號轉入 | 無（IM_ACCT.SOURCE_CODE＝MIGRATE 為預留值，程式不寫入） |
| 其他 | 改密碼、AD 登入、代理人、人員離職處理、系統管理軌跡查詢畫面（以 DB 查）、實際寄出 Email |
| 展延 | 無；要延長就重新提新增申請，或以「修改」調整已核准帳號的到期日（見 `BACKLOG.md` 已拍板不做） |

---

## 第一區塊：功能說明（所有人適用）

### 名詞

| 名詞 | 定義 |
|---|---|
| 使用者 | 可登入本系統的人員（IM_ACCT_USER）；登入帳號＝工號 |
| 帳號 | 帳號主表 IM_ACCT 的一筆，代表一個被管理的系統帳號、憑證或門禁卡；**不是**本系統的登入帳號 |
| 帳號單號 | ACCT_ID＝建立該帳號的新增申請單號 |
| 申請單 | 對某帳號的一次異動（新增、修改、停用、重新啟用、刪除），走 7 關 |
| 在途 | 申請單狀態為 REVIEW（審核中）、EXEC（待執行）、VERIFY（待複驗） |
| 生效 | 關卡 6 執行人**第一次**回報執行完畢；帳號主表此時更新 |
| 交付 | 關卡 7 複驗通過後把帳號交給使用者；在系統外進行，系統不寄交付通知 |
| 執行人 | 關卡 6 在實際系統上建立／異動帳號的人，依帳號類別由 IM_EXEC_MAP 決定，不是角色 |
| 實際操作人 | 真正按下按鈕的登入者；切換身分時是切換前的本人 |
| 代理身分 | 系統管理員切換身分後扮演的使用者；切換期間權限與資料範圍完全等同此人 |
| 有效身分 | 切換中＝代理身分，否則＝實際操作人；權限、資料範圍、業務欄位一律用有效身分 |
| 顯示名 | 「英文名 (中文名)」；中文名空白只顯示英文名；英文名空白時顯示中文名，兩者皆空顯示工號（`Texts.displayName`）。各表 *_NAME 快照欄存此格式 |

### 功能清單

#### 1. 登入、登出

- 以工號＋密碼登入。可登入條件：使用者 STATUS＝1 且有密碼雜湊。
- 判斷順序：① 工號不存在／停用／無雜湊 → A01 ② 已鎖定（LOCK_DATE 非空）→ A02，即使密碼正確 ③ 密碼錯 → 失敗次數＋1，達 5 次同一句寫入鎖定，該次仍回 A01 ④ 成功 → 失敗次數歸 0、記最後登入時間、發新 token。
- 已知差異，修正見 BACKLOG 第 1 項：登入成功的那句 UPDATE 不帶「未鎖定」條件，若同一瞬間另一個請求剛好寫入鎖定，這次登入仍會成功取得 token（LOCK_DATE 保留，下次登入回 A02）。
- 工號不存在、停用、無雜湊時仍做一次雜湊計算，回應時間與密碼錯相同，統一回 A01。
- token 有效條件：未登出、登入後 8 小時內、閒置不超過 30 分鐘、使用者仍啟用；任一不符 → A03，前端清 token 並導回登入頁。
- 同一人可同時多處登入。登出後 token 立即作廢。不提供改密碼，首次登入不強制改。
- 解鎖、重設密碼以 DB 維護（`SETUP.md`）。

#### 2. 權限與資料範圍

- 程式只判斷**權限碼**，不判斷角色名或人名。使用者的權限＝其有效角色展開的權限碼，再加上 GENERAL 角色的權限（所有啟用中使用者自動擁有，不存角色人員資料列）。
- 每次請求依當下 DB 名單重算，不跨請求快取：改 DB 的角色、執行人對照，下一個請求就生效；已完成的簽核不受影響。
- 沒有權限 → 403 P01；有權限但狀態或版本不符 → 409。
- 前端只依 `/me` 的 permissions[] 決定導覽與按鈕；後端一律再驗。

**角色（9 個）**

| 代碼＝中文 | 職責 |
|---|---|
| SYS_ADMIN＝系統管理員 | 切換身分、手動觸發排程；排程失敗通知收件人 |
| GENERAL＝一般人員 | 送出申請、查自己的申請；所有啟用中使用者自動擁有 |
| DC_ADMIN＝機房管理員 | 關卡 2 |
| INFRA_MGR＝Infra 主管 | 關卡 3；鈴鐺看全部 |
| GOV＝資訊治理 | 關卡 4、關卡 7；到期信與每週彙總收件人 |
| GOV_MGR＝資訊治理主管 | 鈴鐺看全部（系統管理軌跡以 DB 查） |
| IT_HEAD＝資訊主管 | 關卡 5；鈴鐺看全部 |
| ACCT_REGISTRY＝帳號清冊檢視 | 完整帳號列表 |
| INSP_STAFF＝巡檢人員 | 巡檢查詢、填寫、標記處理 |

**權限碼（28 個；階段 1 為目前有功能者）**

| 權限碼＝中文 | 模組 | 階段 |
|---|---|---|
| ACC_APPLY＝送出帳號申請、退件後重新送出 | ACCT | 1 |
| ACC_SELF_SERVICE＝進入帳號申請單列表 | ACCT | 1 |
| ACC_LIST_FULL＝完整帳號列表 | ACCT | 1 |
| ACC_EXPIRY_ALL＝到期提醒看全部帳號 | ACCT | 1 |
| APPR_G2～APPR_G5＝關卡 2～5 核准／退件（4 個碼） | ACCT | 1 |
| VERIFY_G7＝關卡 7 複驗通過／退回 | ACCT | 1 |
| INSP_VIEW＝巡檢紀錄查詢與明細 | INSP | 1 |
| INSP_CREATE＝填寫巡檢 | INSP | 1 |
| INSP_RESOLVE＝標記異常已處理 | INSP | 1 |
| SYS_ROLE＝角色指派、SYS_EXECUTOR＝執行人對照維護、SYS_AUDIT_VIEW＝查詢系統管理軌跡 | SYS | 1（只有碼，無對應功能） |
| SYS_IMPERSONATE＝切換身分 | SYS | 1 |
| SYS_JOB_RUN＝手動觸發排程 | SYS | 1 |
| ACC_EXPORT＝帳號權限清單匯出 | ACCT | 2 |
| REVIEW_LIST、REVIEW_SIGN_INFRA、REVIEW_SIGN_GOV＝定期審查清單／Infra 半簽核／治理半簽核 | REVIEW | 2 |
| EMG_APPLY、EMG_VIEW_ALL、EMG_DECIDE＝緊急存取申請／看全部／補核准退回結案 | EMG | 2 |
| STATS_VIEW＝統計頁 | STATS | 2 |
| INSP_EXPORT＝巡檢紀錄匯出 | INSP | 2 |
| MAINT_ASSET_CODE＝資產分類說明維護 | ACCT | 3 |
| MAINT_INSP_ITEM＝巡檢項目維護 | INSP | 3 |

階段 2、3 的權限碼只存在於資料，程式沒有對應功能。MAINT_ASSET_CODE、MAINT_INSP_ITEM 不授予任何角色。

**角色 × 權限（35 列）**

| 角色 | 權限碼 |
|---|---|
| GENERAL | ACC_APPLY、ACC_SELF_SERVICE、EMG_APPLY、STATS_VIEW |
| DC_ADMIN | APPR_G2 |
| INFRA_MGR | APPR_G3、ACC_EXPIRY_ALL、REVIEW_LIST、REVIEW_SIGN_INFRA、EMG_VIEW_ALL、EMG_DECIDE |
| GOV | APPR_G4、VERIFY_G7 |
| GOV_MGR | SYS_AUDIT_VIEW、ACC_EXPIRY_ALL、REVIEW_LIST、REVIEW_SIGN_GOV、EMG_VIEW_ALL、EMG_DECIDE |
| IT_HEAD | APPR_G5、ACC_EXPIRY_ALL、REVIEW_LIST、EMG_VIEW_ALL、EMG_DECIDE |
| ACCT_REGISTRY | ACC_LIST_FULL、ACC_EXPORT |
| INSP_STAFF | INSP_VIEW、INSP_CREATE、INSP_RESOLVE、INSP_EXPORT |
| SYS_ADMIN | SYS_ROLE、SYS_EXECUTOR、SYS_IMPERSONATE、SYS_AUDIT_VIEW、SYS_JOB_RUN |

**資料範圍**（「我」＝有效身分；寫在後端查詢條件，直接呼叫 API 查他人資料一樣被拒）

| 畫面／API | 進入條件 | 看得到什麼 |
|---|---|---|
| 我的申請單 | ACC_SELF_SERVICE | 我送出的申請單 |
| 我曾申請新增的帳號 | ACC_SELF_SERVICE | 原新增申請人是我、狀態不是已退件或已刪除 |
| 對既有帳號提出異動（查詢） | ACC_SELF_SERVICE | 帳號名稱**完整精確比對**（去前後空白、區分大小寫），狀態為已核准／已逾期／已停用；不提供模糊查詢或瀏覽 |
| 異動表單帶入 | ACC_APPLY | 須同時帶帳號名稱且精確相符，或我「可看該帳號」；狀態同上；否則 404 |
| 完整帳號列表 | ACC_LIST_FULL | 全部（預設不含已刪除） |
| 帳號明細、歷史 | 登入 | 「可看帳號」者 |
| 申請單明細、附件下載 | 登入 | 「可看申請單」者 |
| 待辦 | 登入 | 我此刻能處理的 |
| 鈴鐺 | 登入 | 逐筆與我相關者 |
| 首頁卡片 | 登入 | 全部，只回聚合數字 |
| 代碼、資產分類說明 | 登入 | 全部 |
| 切換身分名單 | 實際操作人有 SYS_IMPERSONATE | 啟用中、不含自己 |
| 巡檢 | INSP_VIEW | 全部 |

- **可看申請單**（任一）：我是申請人；我有 ACC_LIST_FULL；申請單在途且我此刻能處理目前關卡；我曾在這張單上操作過（歷史的實際操作人或代理身分是我）。
- **可看帳號**（任一）：我有 ACC_LIST_FULL；我是原新增申請人；我曾對該帳號送過申請；我可看該帳號的任一張申請單（依上一條第 3、4 款）；該帳號在到期提醒範圍內，且我有 ACC_EXPIRY_ALL 或是該類別執行人。

**執行人對照（關卡 6）**：IM_EXEC_MAP 中類別＝申請單類別、工號＝有效身分、STATUS＝1；同類別多人時任一人可執行。同一人可兼簽核人與執行人（系統不阻擋，只在歷史記 IS_SELF_ACTION）。

#### 3. 切換身分

- 誰能用：**實際操作人**有 SYS_IMPERSONATE。對象須為其他啟用中使用者；選自己 → 400「不可切換為自己」；對象不存在或停用 → 404 N01；未帶對象 → 400 E02。
- 切換期間權限、資料範圍、業務欄位完全等同代理身分；頁首顯示 U01「目前以 {代理身分顯示名} 身分操作」與「切回」。切換或切回後重新載入 `/me`，目前頁面無權限時導向首頁。
- 切換中要改扮另一人：前端**不提供**直接改切，切換中頁首不顯示「切換身分」，須先切回（`AppLayout.vue:48`）。後端 API 允許切換中直接改切別人（以實際操作人判斷權限）。
- 未切換時呼叫切回 → 409 E20。代理身分被停用（STATUS 變 0）時自動視為切回（不寫軌跡）。登出即結束切換。
- 軌跡：切換寫 IMPERSONATE、切回寫 IMPERSONATE_END（RESULT_CODE＝SUCCESS）；無權限者嘗試切換 → 403 並寫一筆 DENY。
- 操作人顯示（三種位置格式不同）：
  - **帳號歷史**：「{實際操作人}（以 {代理身分} 身分）」，例「Hank (何嘉浤)（以 Gary (許永成) 身分）」；排程事件顯示「系統排程」（`labels.ts` histActorText）。
  - **巡檢填寫人、異常處理人**：「{代理身分}（實際操作人：{實際操作人}）」，例「Kevin (李凱鈞)（實際操作人：Hank (何嘉浤)）」（`labels.ts` personText）。
  - **通知本文、待辦**：申請人只顯示有效身分（申請單 APPLY_BY 本來就記有效身分），不帶實際操作人。

| 欄位種類 | 記誰 | 欄位 |
|---|---|---|
| 業務欄位 | 有效身分 | IM_ACCT_REQ 的 APPLY_BY、EXEC_BY、VERIFY_BY、REJECT_BY；IM_ACCT.APPLY_BY |
| 稽核欄位 | 實際操作人 | IM_ACCT_HIST.ACT_BY、IM_INSP.INSP_BY、IM_INSP_RESULT.FIX_BY、各表 CREATE_BY／UPDATE_BY |
| 代理欄位 | 代理身分（切換中才有值） | IM_ACCT_HIST.ACT_AS_*、IM_INSP.ACT_AS_*、IM_INSP_RESULT.FIX_ACT_AS_* |
| 系統排程 | — | ACT_BY 空、ACT_BY_NAME＝「系統排程」、CREATE_BY／UPDATE_BY＝SYSTEM |

#### 4. 帳號類別與資產分類

**帳號類別（8 種）**：共用同一套 7 關流程，差異只在表單欄位、執行人、提示訊息；只有門禁卡有實質不同的規則（見第 10 節）。

| 代碼＝中文 | 開放新增（初始資料） | 建議到期日提示 U04 | 初始執行人 |
|---|---|---|---|
| HOST＝主機帳號 | 1 | 0 | Alex |
| DB＝資料庫帳號 | 1 | 0 | Terry、Michael |
| APP＝應用系統帳號 | 0 | 0 | Alex |
| SVC＝服務帳號 | 0 | 1 | Alex、Alan |
| NET＝網路設備帳號 | 0 | 0 | Alex |
| VENDOR＝廠商/委外帳號 | 0 | 1 | Sage |
| CERT＝憑證/金鑰 | 0 | 1 | Alex、Alan |
| DOOR＝機房門禁卡 | 1 | 0 | Alex、Alan |

- 開放開關（IS_ENABLED）只管「新增」：新增表單類別下拉只列 IS_ENABLED＝1 且 STATUS＝1；既有帳號照常可異動；帳號列表 8 個類別頁籤始終顯示。測試 DB 現值與初始資料不同，見 BACKLOG 第 9 項。
- 改 DB 的 IS_ENABLED 不需重新部署：後端每個請求都讀 DB，立即生效；前端的代碼表（`/codes`）在同一個登入期間有快取（`useSession.ts:108-127`），使用者要重新整理頁面或重新登入才看到新的下拉選項。在此之前若送出已關閉的類別，後端回 400 E05。
- 退件重送新增申請時，若該類別已被關閉，前端下拉仍列出原類別（只要是目前選的值就列），送出後後端回 400 E05。
- 「特權」是疊加在任一類別上的勾選欄位（IS_PRIV），不是第 9 類。

**資產分類碼（15 碼）**

| 代碼 | 名稱 | 可選 | 審查觸發 |
|---|---|---|---|
| H01 | 伺服器－與營運直接相關 | 1 | 1 |
| H02 | 伺服器－與營運間接相關 | 1 | 0 |
| H03 | 伺服器－與 IT 管理性作業相關 | 1 | 0 |
| H04 | 測試設備 | 1 | 0 |
| H05 | IT 基礎設備－與營運直接相關 | 1 | 1 |
| H06 | IT 基礎設備－與營運間接相關 | 1 | 0 |
| H07 | 個人電腦（含 NB） | 0 | 0 |
| H08 | 儲存設備 | 1 | 0 |
| H09 | 支援設備 | 1 | 0 |
| H10 | 輸出設備 | 0 | 0 |
| S01 | 與營運直接相關軟體 | 1 | 1 |
| S02 | 與營運間接相關軟體 | 1 | 0 |
| S03 | 與 IT 管理性作業相關軟體 | 1 | 0 |
| S04 | IT 基礎相關軟體 | 1 | 0 |
| S05 | 測試軟體／辦公室套裝軟體 | 1 | 0 |

- H＝硬體、S＝軟體。「未分類」不建資料列，以空值表示；表單選項＝13 個可選碼＋未分類。送出時須存在且可選，否則 400 E06。
- 程式以「審查觸發」欄判斷，不寫死代碼。資產分類說明頁與表單旁「查看分類說明」彈窗列出全部 15 碼與存取控制要求摘要（CTRL_DESCR）。

#### 5. 帳號申請（5 種類型）

| 類型 | 目標帳號狀態前提 | 表單行為 |
|---|---|---|
| ADD＝新增 | 無；不可指定帳號（帶了 → 400「新增申請不可指定帳號」） | 完整填寫 |
| MODIFY＝修改 | 已核准 | 帳號類別、帳號名稱、所屬系統鎖定；其餘可改（含到期日） |
| DISABLE＝停用 | 已核准、已逾期 | 只填申請單位與原因（標示「停用原因」）；其他內容複製主表當下內容 |
| ENABLE＝重新啟用 | 已停用 | 以主表當下內容帶入，可一併修正 |
| DELETE＝刪除 | 已核准、已停用、已逾期 | 同停用，標示「刪除原因」；顯示 U03 |

- 帳號不存在 → 404 N01；前提不符 → 409 E09；同一帳號同時只能一筆在途 → 409 E07。已刪除為終態，之後任何申請 409 E09。
- 帳號列表與查詢結果只在「符合前提且無在途」時給可做的申請類型（allowedReqTypes）；在途帳號不顯示異動按鈕。
- 停用、刪除申請的「申請原因」欄存的就是停用原因／刪除原因。

**表單欄位**

| 欄位 | 標準表單 | 門禁卡表單 |
|---|---|---|
| 申請單號、申請日期、申請人 | 系統產生／系統時間／有效身分，唯讀 | 同 |
| 申請單位 | 必填；新開申請一律空白，只有退件重送保留原值 | 同 |
| 申請人分機／Email | 灰底範例，只作「同申請人」帶入來源，不送後端 | 同 |
| 申請類型 | 必填 | 同 |
| 帳號類別 | 必填；新增只列開放類別 | DOOR |
| 帳號名稱 | 新增必填；異動鎖定 | 新增（含重送）不顯示；異動以唯讀顯示現有卡號 |
| 所屬系統/設備名稱 | 新增必填；異動鎖定 | 隱藏（後端強制「機房門禁管制系統」） |
| 門禁權限範圍 | — | 必填：TEST＝僅測試區（第一道門）、FULL＝機房完整權限（兩道門）；修改時可重選 |
| 資產分類碼 | 選填 | 鎖定未分類 |
| 是否特權帳號 | 勾選 | 同 |
| 實際使用人/保管人 | 必填；旁有「同申請人」 | 同 |
| 所屬單位 | 選填 | 同 |
| 使用人聯絡方式 | 選填；灰底範例「#1614 或 Email」 | 隱藏 |
| 使用人 Email | — | 選填 |
| 權限範圍/角色 | 選填：RO＝唯讀、OP＝操作、ADMIN＝管理員（完全控制） | 隱藏（後端強制 DOOR） |
| 存取對象 | 選填 | 隱藏 |
| 申請原因 | 必填（停用／刪除時標示停用原因／刪除原因） | 同 |
| 附件（公文） | 選填 | 同 |
| 有效期限類型 | 必填：PERM＝永久有效、DATE＝指定到期日 | 同 |
| 到期日 | DATE 時必填；不可早於今天 | 同 |
| 帳號唯一性聲明 | 必勾 | 同 |
| 簽核路徑預覽 | 顯示 7 關名稱 | 同 |

| 類型 | 必填 | 可改的內容欄位 |
|---|---|---|
| 新增（標準） | 申請單位、帳號類別、帳號名稱、所屬系統、使用人、申請原因、有效期限類型 | 全部 |
| 新增（門禁卡） | 申請單位、門禁權限範圍、使用人、申請原因、有效期限類型 | 門禁卡可見欄位 |
| 修改、重新啟用 | 申請單位、使用人、申請原因、有效期限類型（門禁卡另需門禁權限範圍） | 除帳號類別、帳號名稱、所屬系統外的內容欄位（這三欄後端忽略，以主表值存入） |
| 停用、刪除 | 申請單位、申請原因 | 只有申請原因；其他內容複製主表，不做到期日檢查 |

「同申請人」帶入（前端）：使用人＝登入者顯示名；聯絡方式＝表單分機，空則表單 Email，再空則使用者資料的電話；門禁卡使用人 Email＝表單 Email，空則使用者資料 Email；所屬單位空白時帶申請單位，有值不覆蓋。分機不做格式驗證。

送出驗證順序與錯誤代碼見「API 規格 → 送出驗證順序」。

#### 6. 7 關簽核

```
關卡 1 申請人送出（ACC_APPLY）
 → 關卡 2 機房管理員（APPR_G2）
 → 關卡 3 Infra 主管（APPR_G3）
 → 關卡 4 資訊治理（APPR_G4）
 → 關卡 5 資訊主管（APPR_G5）
 → 關卡 6 執行人（IM_EXEC_MAP 該類別）：回報執行完畢＋佐證，帳號此時生效
 → 關卡 7 資訊治理複驗（VERIFY_G7）：通過 → 已核准（可交付）；退回＋原因 → 回關卡 6
關卡 2～5 退件＋原因 → 已退件；僅原申請人可重新送出 → 回關卡 2
```

| 關卡 | 處理者 | 動作 | 必填 | 結果 | 通知 |
|---|---|---|---|---|---|
| 1 | ACC_APPLY | 送出 | 表單 | 審核中／關卡 2，版本 0 | SUBMIT |
| 1 | 原申請人 | 重新送出 | 表單＋versionNo | 審核中／關卡 2，重送次數＋1，送出時間更新；申請日期不變；類型與帳號不可變 | RESUB |
| 2～4 | 該關權限 | 核准 | versionNo | 下一關 | NEXT_STAGE |
| 5 | APPR_G5 | 核准 | versionNo | 待執行／關卡 6 | TO_EXEC |
| 2～5 | 該關權限 | 退件 | versionNo、原因（E10） | 已退件，記退件關卡、退件人、時間、原因，退件次數＋1 | REJECT |
| 6 | 該類別執行人 | 回報執行完畢 | versionNo、佐證（E12）、門禁卡新增的卡號（E13）；執行說明選填 | 待複驗／關卡 7；第一次回報記生效時間 | EXEC_DONE |
| 7 | VERIFY_G7 | 複驗通過 | versionNo | 已核准，記複驗人、時間、結案時間 | 不寄 |
| 7 | VERIFY_G7 | 複驗退回 | versionNo、原因（E10） | 待執行／關卡 6，複驗退回次數＋1 | VERIFY_REJECT |

- 關卡 2～5、7 只審核不改內容，只有退件、退回要填原因。每次狀態轉移版本號＋1。
- 核准／退件判斷順序：① 沒有任何關卡 2～5 權限 → 403 ② 未帶 versionNo → 400 E02 ③ 狀態或版本不符 → 409 E08 ④ 沒有目前關卡的權限 → 403 ⑤ 退件原因 E10。回報執行、複驗同理：先驗權限，再做帶條件的 UPDATE。
- **樂觀鎖**：狀態轉移 UPDATE 帶「單號＋版本號＋預期狀態＋預期關卡」條件；影響 0 筆 → 409 E08「此申請已由他人處理，請重新整理」，先送出者成功。前端收到 409 → toast 訊息並重新載入。
- **同帳號一筆在途**：程式先查；併發競態由唯一函式索引 IND_IM_ACCT_REQ_04 擋下，ORA-00001 經 Spring 轉成 DuplicateKeyException 後回 409 E07。已知差異，修正見 BACKLOG 第 3 項：送出與重送交易內任何唯一鍵衝突（例如其他表的主鍵重複）目前都會被回成 E07。
- **自行核准／同人兼任**（U12，只標示不阻擋）：對核准、退件、回報、複驗通過、複驗退回判斷；有效身分＝申請人 →「自行核准」；同單其他關已由同一有效身分處理 →「同人兼任」。
- **退件重送**：只有原申請人看得到、能做（否則 403）；沿用同一單號；附新公文時舊公文失效、新公文一筆，不附則保留舊公文。已知差異，修正見 BACKLOG 第 4 項：新增申請重送時前端仍可改帳號類別，後端以表單值覆寫。

**申請單狀態**：REVIEW＝審核中（關卡 2～5，畫面顯示 U10「審核中：待 {關卡名}」）、EXEC＝待執行（6）、VERIFY＝待複驗（7）、APPROVED＝已核准（無關卡）、REJECTED＝已退件（無關卡，可重送）。

**帳號狀態**（與申請單狀態是不同集合）

| 狀態 | 何時進入 | 計入帳號總數 | 可發起的申請 |
|---|---|---|---|
| REVIEW＝審核中 | 新增申請送出、重送 | 是 | 無（在途） |
| APPROVED＝已核准 | 新增於關卡 6 生效；重新啟用生效；已逾期帳號的修改生效且新期限有效 | 是 | 修改、停用、刪除 |
| EXPIRED＝已逾期 | J01 發現到期日已過 | 是 | 停用、刪除 |
| DISABLED＝已停用 | 停用生效 | 是 | 重新啟用、刪除 |
| REJECTED＝已退件 | 新增申請被退件 | 是 | 原申請人重送 |
| DELETED＝已刪除 | 刪除生效（軟刪除，終態） | 否 | 無；列表預設不顯示 |

- 「待複驗」標記（U11）：帳號有一筆待複驗申請單即顯示，計算值不存欄位。
- 非新增類型的送出、退件、重送，以及關卡 7 的通過與退回，都不動帳號主表。

#### 7. 關卡 6 生效與主表同步

第一次回報（申請單生效時間原本為空）時，在同一交易依類型更新帳號主表：

| 類型 | 寫入主表 | 帳號狀態 |
|---|---|---|
| 新增 | 申請單全部內容欄位；門禁卡的帳號名稱＝卡號 | 已核准 |
| 修改 | 使用人、所屬單位、聯絡方式、使用人 Email、權限範圍、存取對象、資產分類、特權、有效期限類型、到期日、門禁範圍、申請原因 | 不變；但當下是已逾期且新期限為永久或到期日 ≥ 今天 → 已核准 |
| 重新啟用 | 同修改 | 已核准 |
| 停用 | 申請原因 | 已停用 |
| 刪除 | 申請原因 | 已刪除 |

- 門禁卡完成時清空主表的使用人聯絡方式與存取對象。主表的申請原因只存最近一次，歷次見歷史。
- 已知差異，修正見 BACKLOG 第 2 項：門禁卡新增回報時，卡號只寫入申請單 CARD_NO 與主表 ACCT_NAME，**申請單本身的帳號名稱欄仍為空**，所以之後給複驗人的通知與待辦標題仍顯示「（門禁卡，待發卡）」。
- **複驗退回後再次回報**：只更新申請單的執行人、時間、說明、卡號與佐證（舊佐證失效、新佐證一筆）；不重寫主表、不重寫審查日期、不改生效時間。唯一例外：門禁卡新增若卡號改變，主表帳號名稱同步為新卡號。
- **審查日期**：審查條件（任一）＝資產分類「審查觸發」為 1、類別為門禁卡、特權帳號；由單一共用函式判斷。只在第一次回報時：新增→新內容符合就寫；修改、重新啟用→新內容符合，且主表舊內容不符合或上次審查日為空才寫；停用、刪除不寫。寫入值：上次審查日＝生效日期，下次應審查日＝上次審查日＋180 天。寫入後不再變動（系統沒有審查流程）。

#### 8. 到期管理（排程 J01、J02）

**到期提醒範圍**（首頁卡片、鈴鐺、帳號列表橫幅與到期篩選共用）：已核准且有效期限為指定日、到期日 ≤ 今天＋30；或狀態為已逾期。剩餘天數＝到期日－今天（已過為負），依到期日由近到遠排序。

**共通**

| 項目 | 規則 |
|---|---|
| 觸發 | 排程（時區 Asia/Taipei，時間可由設定調整，可整體關閉）；或手動 `POST /jobs/{code}/run`（SYS_JOB_RUN），邏輯相同 |
| 指定基準日 | 手動可帶 baseDate 當作「今天」，僅在設定允許的環境可用，否則 400 E17 |
| 不重入 | 同一排程同時只跑一個，再觸發 → 409 E16（鎖只在單一程序內） |
| 紀錄 | 每次一筆 IM_ACCT_JOB_RUN：開始寫 RUNNING，結束寫結果、筆數、錯誤摘要 |
| 逐筆交易 | 每筆帳號一個交易；單筆失敗記錄後繼續，整體 PARTIAL；整個作業無法進行為 FAIL |
| 失敗通知 | PARTIAL、FAIL 寄 JOB_FAIL 給系統管理員 |
| 可重複執行 | 同一基準日跑兩次結果相同：不重複轉換、不重寄 |

- 已知限制，見 BACKLOG 第 7 項：若寫「開始」或「結束」紀錄這一步本身失敗，該筆會停在 RUNNING（或完全沒有紀錄），也不會寄失敗通知。

**J01 到期處理**（預設每日 00:10）

1. 轉逾期：已核准、指定日、到期日 < 今天的每筆，交易內帶條件改為已逾期，影響 1 筆才寫歷史 EXPIRE（含到期日）。在途申請不受影響、照常完成。
2. 轉逾期信：已逾期、且通知紀錄沒有（EXPIRED、該帳號參照鍵）任何一筆者，寄 EXPIRED。
3. 到期前 7 天信：已核准、指定日、今天 ≤ 到期日 ≤ 今天＋7，且通知紀錄沒有（EXPIRY_7D、參照鍵）任何一筆者，寄 EXPIRY_7D。

- 參照鍵＝`ACCT_ID_到期日yyyymmdd`，修改到期日即視為新的一輪。該鍵已有任何紀錄（成功、失敗、略過）就不再寄；失敗不自動重寄。漏跑幾天也會補轉、補寄。
- 處理筆數＝轉逾期筆數＋「本次有寫入通知紀錄」的帳號數（第 2、3 步各自計；收件人無 Email 而記 SKIP 也算寫入）（`JobService.java:199-210`）。

**J02 每週彙總**（預設每日 08:00 觸發，每週只寄一次）

- 週鍵＝基準日所在週的週一 yyyymmdd。通知紀錄已有（WEEKLY_SUMMARY、週鍵）任何一筆 → 本次不寄，結果成功、筆數 0。
- 否則寄一封給每位資訊治理（GOV）成員：列出所有已逾期帳號（單號、類別、帳號名稱、使用人、到期日、逾期天數，依到期日排序）；0 筆仍寄，內文 U13。不含已停用或已刪除帳號。週一漏跑則該週第一次執行補寄。
- 無任何收件人時記 WARN log，結果照常成功。筆數＝列出的帳號數。

#### 9. 通知

- 寄信介面（收件 Email、主旨、本文）目前的實作**只寫 log**：逐筆記收件人工號與主旨，不記 Email、不記本文，視為成功；不實際寄出。
- 業務動作的通知在交易提交後寄出，失敗不影響業務結果與 API 回應。
- 每位收件人寫一筆通知紀錄；收件人無 Email → SKIP；例外 → FAIL。收件人在寄送當下計算，只含啟用中使用者，同一封信同一人去重。
- 角色型收件人為程式常數：到期前 7 天、轉逾期、每週彙總 → GOV；排程失敗 → SYS_ADMIN。

| 事件 | 收件人 | 主旨 |
|---|---|---|
| SUBMIT＝申請送出 | 有 APPR_G2 的人 | 【帳號申請】{單號} 待您審核（機房管理員） |
| RESUB＝重新送出 | 有 APPR_G2 的人 | 【帳號申請】{單號} 已重新送出，待您審核 |
| NEXT_STAGE＝關卡 2～4 核准 | 有下一關權限的人 | 【帳號申請】{單號} 待您審核（{關卡名}） |
| TO_EXEC＝關卡 5 核准 | 該類別執行人 | 【帳號申請】{單號} 待您執行 |
| REJECT＝退件 | 申請人 | 【帳號申請】{單號} 已退件 |
| EXEC_DONE＝回報執行完畢 | 有 VERIFY_G7 的人 | 【帳號申請】{單號} 待您複驗 |
| VERIFY_REJECT＝複驗退回 | 該次執行人 | 【帳號申請】{單號} 複驗退回，請重新處理 |
| EXPIRY_7D＝到期前 7 天 | 原新增申請人（空則略過）、該類別執行人、GOV 成員 | 【帳號到期】{帳號名稱} 將於 {到期日} 到期 |
| EXPIRED＝轉為已逾期 | 同上 | 【帳號到期】{帳號名稱} 已逾期 |
| WEEKLY_SUMMARY＝每週彙總 | GOV 成員 | 【帳號到期】本週已逾期未處理帳號彙總（{n} 筆） |
| JOB_FAIL＝排程失敗 | SYS_ADMIN 成員 | 【排程失敗】{排程代碼} 執行{部分}失敗 |

- 複驗通過不寄信。申請單類本文含：單號、申請類型、帳號類別、帳號名稱、申請人（有效身分）、目前關卡；退件、退回含原因；到期信含到期日。門禁卡申請單的帳號名稱為空時顯示「（門禁卡，待發卡）」。

#### 10. 附件與機房門禁卡

**附件**

| 項目 | 規則 |
|---|---|
| 種類 | 公文：送出、重送時選填，每次最多 1 檔；執行佐證：回報執行完畢時必填（E12），每次 1 檔 |
| 格式 | 副檔名（不分大小寫）限 .pdf .doc .docx .jpg .png，且檔頭相符（pdf `%PDF`、doc OLE2、docx zip、jpg `FF D8 FF`、png `89 50 4E 47`）；否則 400 E14 |
| 大小 | 單檔 ≤ 10 MB，否則 413 E15；請求總長上限 11 MB；空檔視同未上傳 |
| 存放 | 附件根目錄（設定值，須為絕對路徑，啟動時檢查，不符則啟動失敗）下的 `REQ_ID/uuid.副檔名`；DB 存相對路徑、原檔名、大小、依副檔名決定的 MIME |
| 寫入順序 | 先寫檔，再在交易內寫 DB；交易回復時盡力刪除剛寫的檔（失敗記 log） |
| 取代 | 重送附新公文、再次回報附新佐證時，同種類舊列失效（檔案保留）；明細只列有效附件 |
| 下載 | 須「可看申請單」；已失效的附件可看該單者仍可下載；前端以 blob 取得後存檔 |
| 前端讀檔失敗 | 公文讀取失敗 → U07，使用者確認後不附公文送出；佐證讀取失敗 → U08 並擋下 |

**機房門禁卡（DOOR）**

- 隱藏欄位：帳號名稱（新增時）、所屬系統、權限範圍、存取對象、使用人聯絡方式。
- 後端強制值：所屬系統＝「機房門禁管制系統」、權限範圍＝DOOR、資產分類＝空、送出時帳號名稱＝空；前端送來的值忽略。
- 專屬欄位：門禁權限範圍（必填，修改時可重選）、使用人 Email。
- 卡號：新增申請在關卡 6 必填（E13）；其他類型不需卡號，佐證為門禁系統操作截圖。
- 必定符合審查條件；完成時清空主表聯絡方式與存取對象。

#### 11. 首頁、鈴鐺、待辦

**首頁六張卡片**（所有登入者，只回數字）：帳號總數（不含已刪除）、待審核（帳號狀態審核中）、30 天內到期（到期提醒範圍，含已逾期；有 ACC_LIST_FULL 點擊進帳號列表到期篩選，否則進無權限頁）、特權帳號（不含已刪除）、本月巡檢次數、本月發現異常項目數（本月巡檢中異常且未處理的項目數）。

**到期鈴鐺**：到期提醒範圍內，逐筆判斷與我相關才列：我有 ACC_EXPIRY_ALL；或我是原新增申請人；或我是該類別執行人。沒有相關項目時不顯示徽章；仍可點開，面板顯示「目前沒有與您相關的到期帳號」。面板標題「30 天內到期或已逾期」；有 ACC_LIST_FULL 且有項目時顯示「查看全部到期清單」。點單筆：有 ACC_LIST_FULL → 帳號列表到期篩選；否則開唯讀帳號明細彈窗（不顯示異動按鈕）。

**待辦簽核**：只列我此刻能處理的申請單，送出時間由舊到新；每次依當下名單計算。標題格式「{申請類型} {帳號類別}：{帳號名稱}」。

| 狀態／關卡 | 出現在誰的待辦 | 可做 |
|---|---|---|
| 審核中／2～5 | 有該關 APPR_Gn | 查看明細、核准、退件（填原因） |
| 待執行／6 | 該類別執行人 | 回報執行（彈窗：佐證必選、門禁卡新增時卡號必填、執行說明選填） |
| 待複驗／7 | 有 VERIFY_G7 | 複驗通過、複驗退回（填原因） |
| 已退件 | 不出現（原申請人在「我的申請單」重送） | — |

複驗退回過的單，在回報與複驗彈窗顯示 U02。

#### 12. 機房巡檢

**巡檢項目（14 項）**

| 代碼 | 大類 | 項目 |
|---|---|---|
| A01～A10 | 甲、機房環境設施 | 機房溫度、機房溼度、UPS 電力狀態、UPS 電池狀態、精密空調運作、消防偵煙系統、滅火系統、門禁系統、監視系統、漏水偵測（依代碼順序） |
| B01 | 乙、資訊設備狀態（只需觀察，不需判斷原因） | 交換器面板燈是否全部正常（無紅燈閃爍、無告警音） |
| B02 | 同上 | 路由器/防火牆面板是否有紅色告警燈或告警音 |
| B03 | 同上 | 伺服器面板燈是否正常（電源/風扇/溫度指示燈無紅、黃燈） |
| B04 | 同上 | 儲存陣列面板是否有紅色/黃色告警燈 |

項目 STATUS＝1 才在填寫時列出；停用不刪除。

**填寫**（INSP_CREATE）：14 項分甲乙兩大類，每項選正常或異常（無預設，全部選完才能送出）；異常展開說明欄且必填（E10「請填寫異常說明」）；異常數即時統計。結果須剛好涵蓋所有啟用中項目，否則 400 E18。巡檢時間預設現在、可調整，不可晚於現在。按「巡檢人員簽章並結案」即送出，簽章時間＝送出時間；取號 IN。每項存送出當下的項目與大類名稱快照，畫面以快照為準。送出後不可修改。

**整體狀態**（即時計算，不存欄位）：沒有異常 → NORMAL＝正常結案；有未處理的異常 → EXCPT＝有異常；異常全部已處理 → FIXED＝異常已排除。列表另顯示異常項目數與未處理數。

**標記處理**（INSP_RESOLVE）：只接受異常且未處理的項目，否則 409 E19；處理方式與結果必填（E10「請填寫處理方式與結果」）。記處理時間、實際處理人、切換中的代理身分。巡檢人員本人也可標記。

**查詢**（INSP_VIEW，看全部）：關鍵字比對巡檢單號、填寫人、代理身分名；狀態篩選全部／正常結案／有異常／異常已排除；日期區間全部、近 7／30（預設）／90 天、自訂（依巡檢時間，含頭尾）。明細列各項結果與處理資訊，未處理的異常項目有「標記已處理」。

#### 13. 單號與歷史

- 單號格式：前綴 2 碼＋yyyymmdd＋「-」＋當日 3 碼流水號，例 `AR20261015-001`。AR＝帳號申請單（新增申請的單號同時是帳號單號）、IN＝機房巡檢。流水號依前綴分開、每日從 001 起，單日上限 999。取號在送出交易內，交易回復時一併回復。
- 歷史（IM_ACCT_HIST）：每個動作一筆，與狀態更新同一交易，只增不改，程式沒有更新或刪除路徑。依發生時間、流水號排序；畫面顯示快照姓名。結果文字至少含：送出／重送的申請原因、核准的關卡名、退件與退回原因、執行說明與門禁卡號、轉逾期的到期日。

### 使用者流程

**申請帳號（新增）**
1. 申請人登入 → 左側「新增申請」（需 ACC_APPLY）→ 選類別、填表、勾唯一性聲明、可附公文 → 送出，取得單號。
2. 關卡 2～5 簽核人在「待辦簽核」看到並核准或退件；退件時申請人在「帳號申請單列表 → 我的申請單」看到原因，可修改後「重新送出」。
3. 關卡 5 核准後，該類別執行人在待辦看到，於實際系統建立帳號後「回報執行」並上傳佐證（門禁卡填卡號）→ 帳號生效。
4. 資訊治理在待辦複驗：通過即結案，帳號在系統外交付；退回則回到執行人重新處理。

**異動既有帳號**
1. 申請人在「帳號申請單列表 → 對既有帳號提出異動」輸入完整帳號名稱查詢（或在「我曾申請新增的帳號」、帳號列表點操作按鈕）。
2. 選修改／停用／重新啟用／刪除 → 帶入現有內容的表單 → 送出，之後同新增的第 2～4 步。

**到期**
1. J01 每日把過期帳號轉為已逾期、寄到期前 7 天與轉逾期信；J02 每週一寄已逾期彙總給資訊治理。
2. 相關人在首頁卡片與鈴鐺看到到期帳號，要延長就以修改申請調整到期日，不用就提停用或刪除。

**機房巡檢**
1. 巡檢人員「新增巡檢」→ 逐項選正常／異常，異常填說明 → 簽章並結案。
2. 在「巡檢紀錄」查到有異常的單 → 開明細 → 對異常項目「標記已處理」並填處理方式，全部處理完狀態轉為異常已排除。

**系統管理員代為操作**
1. 頁首「切換身分」→ 選人 → 以該人身分操作（頁首顯示 U01）→ 「切回」。要改扮另一人須先切回。

### 畫面說明

Vue 單頁應用，經瀏覽器操作。左側導覽、頁首、主內容三區；清單每頁 20 筆；彈窗點遮罩關閉等同取消。

**頁首**：頁名、到期鈴鐺、登入者（實際操作人）顯示名、登出。實際操作人有 SYS_IMPERSONATE 且未切換時顯示「切換身分」；切換中顯示 U01 與「切回」。

**左側導覽**（依 `/me` 權限顯示，`AppLayout.vue`、`perms.ts`）

| 項目 | 顯示條件 |
|---|---|
| 首頁 | 一律 |
| 待辦簽核（含數量） | 一律 |
| 帳號列表 | 一律；有 ACC_LIST_FULL 連到帳號列表，否則連到帳號申請單列表 |
| 帳號申請單列表 | 有 ACC_SELF_SERVICE |
| 新增申請 | 有 ACC_APPLY |
| 資產分類說明 | 一律 |
| 機房巡檢 → 巡檢紀錄 | 有 INSP_VIEW（整個區塊） |
| 機房巡檢 → 新增巡檢 | 有 INSP_VIEW 且 INSP_CREATE |

**路由**（hash 路由；沒有 token → 登入頁，登入後回原路由；權限不符 → 無權限頁；未知路徑 → 首頁）

| 路徑 | 畫面 | 進入條件 | 重點 |
|---|---|---|---|
| `/login` | 登入 | 無 | 工號、密碼；錯誤顯示後端訊息；已登入導向首頁 |
| `/` | 首頁 | 登入 | 6 卡片 |
| `/todo` | 待辦簽核 | 登入 | 核准／退件／回報／複驗彈窗 |
| `/accounts` | 帳號列表 | ACC_LIST_FULL | 橫幅 U14（點擊套用到期篩選）；關鍵字（帳號單號、帳號名稱、使用人）、類別頁籤（含數量，8 類都顯示）、狀態（含數量，預設全部不含已刪除）、資產分類可同時套用；列顯示特權標籤與 U11；操作按鈕依可做的申請類型 |
| `/requests` | 帳號申請單列表 | ACC_SELF_SERVICE | 三區塊：① 對既有帳號提出異動（完整名稱輸入＋U05；查無 U06；在途不顯示操作）② 我曾申請新增的帳號（在途只有查看、歷史）③ 我的申請單（狀態篩選；已退件顯示原因與「重新送出」） |
| `/requests/new` | 帳號權限申請 | ACC_APPLY | 新增；異動（帶 reqType、acctId、acctName）；重送（帶 resubmit＝單號）；刪除顯示 U03 |
| `/assets` | 資產分類說明 | 登入 | 15 碼表格 |
| `/inspections` | 巡檢紀錄 | INSP_VIEW | 查詢；明細彈窗可標記處理（INSP_RESOLVE） |
| `/inspections/new` | 新增巡檢 | INSP_CREATE | 14 項填寫 |
| `/no-access` | 無權限 | 無 | 回首頁 |

**彈窗**：申請單明細（附件、事件、可用動作、U02）、帳號明細（含唯讀模式）、帳號歷史（操作人顯示、U12）、巡檢明細與標記處理、切換身分選人、資產分類說明。

**查詢失敗**：任何查詢失敗顯示 U09「查詢失敗，請稍後再試」，不得顯示成查無資料。已知限制，見 BACKLOG 第 8 項：`/me` 回 401 以外的錯誤時，需要權限的頁面會導向無權限頁而不是顯示 U09，只有整頁重新整理才恢復。

**畫面固定文字**

| 代號 | 文案 | 位置 |
|---|---|---|
| U01 | 目前以 {代理身分顯示名} 身分操作 | 切換中頁首 |
| U02 | 此案件先前已被複驗退回過，上次退回原因：{原因} | 回報與複驗彈窗、申請單明細 |
| U03 | 刪除後此帳號將無法再啟用或異動，僅保留歷史紀錄供稽核查詢。 | 刪除申請表單 |
| U04 | 建議指定明確到期日 | 建議到期日類別的有效期限旁 |
| U05 | 請輸入完整帳號名稱（不提供模糊查詢） | 對既有帳號提出異動 |
| U06 | 查無此帳號，請確認輸入的是完整帳號名稱 | 同上查無時 |
| U07 | 公文附件讀取失敗，是否不附公文直接送出？ | 送出前讀公文失敗 |
| U08 | 佐證附件讀取失敗，請重新選擇檔案 | 回報前讀佐證失敗 |
| U09 | 查詢失敗，請稍後再試 | 任何查詢失敗 |
| U10 | 審核中：待 {關卡名} | 申請單狀態 |
| U11 | 待複驗 | 帳號列表標記 |
| U12 | 自行核准／同人兼任 | 歷史標籤 |
| U13 | 目前沒有已逾期未處理的帳號 | J02 彙總 0 筆 |
| U14 | 有 {n} 個帳號 30 天內到期或已逾期 | 帳號列表橫幅 |
| — | 所選檔案沒有內容，視同未上傳 | 前端選到空檔時（`utils/attach.ts`） |
| — | 目前沒有與您相關的到期帳號 | 鈴鐺面板無項目時 |

---

## 第二區塊：技術規格（工程師適用）

### 技術架構

| 程式 | 路徑 | 技術 | 負責 | 不做 |
|---|---|---|---|---|
| 後端 API | `im_acct_insp\` | Spring Boot 4.1.1、Java 25、Maven Wrapper（Maven 3.9.16）、Jackson 3（`tools.jackson`）；API 根 `/api/v1` | 登入與 token、權限、資料範圍、業務規則、SQL、交易、附件存檔、排程、通知紀錄 | 畫面 |
| 殼 jar | `im_acct_insp_web\im_acct_insp_web\` | Spring Boot；context `/im_acct_insp_web` | 提供前端靜態頁；把 `/im_acct_insp_web/api/v1/...` 原樣轉送到後端（Authorization、查詢字串、JSON、multipart 原始位元組、二進位下載；後端狀態碼與本文原樣回） | 不連 DB、不驗 token、不解析 payload、不開 CORS；本文超過 11 MB 直接回 413 E15 |
| 前端 | `im_acct_insp_web\im_acct_insp_web_frontend\` | Vue 3.5＋TypeScript、Vite 8、Vitest、axios；Node 24 以上 | 畫面、送出前驗證、依權限碼顯示導覽與按鈕 | 最終權限判斷 |
| DB | — | Oracle 19c，schema RD_USER | 本模組 21 張表 | — |

```
瀏覽器（token 存 sessionStorage，每個請求帶 Authorization: Bearer）
  → 殼 jar /im_acct_insp_web/api/v1/...（RestTemplate 原樣轉送；連線逾時 5 秒、讀取逾時 120 秒）
  → 後端 /api/v1/...（驗 token → 權限 → 業務 → SQL）
  → Oracle RD_USER
```

- 後端連不上時殼 jar 回 500，前端顯示 U09。前端收到 401 → 清 token、導向登入頁並顯示後端訊息。
- **DB 存取**：一律經範本共用元件 `com.mpx.common.db.DbClient`（內部用 `NamedParameterJdbcTemplate`，具名參數 SQL）；連線資訊於執行期向「DB 連線資訊 API」取得，DB 別名寫在設定檔。`com.mpx.common.*` 與 `com.mpx.Application` 為範本共用程式，不屬本模組業務。
- **交易**：業務層以 TransactionTemplate 包多句寫入；交易管理器 `LazyDataSourceTransactionManager` 第一次開交易才取 DataSource。登入失敗計數、最後存取時間等單句更新不開交易。
- **時間**：業務時間由應用程式 Clock（Asia/Taipei）產生並以參數綁定寫入，SQL 不用 SYSDATE；比較日期時去掉時分秒。
- **密碼**：PBKDF2WithHmacSHA256、salt 16 bytes、迭代 310,000、輸出 32 bytes，存成 `PBKDF2$310000$base64(salt)$base64(hash)`，常數時間比對。產生雜湊用 `tools\PwdHashTool.java`（`SETUP.md`）。
- **token**：32 bytes 亂數，base64url 回前端；DB 只存 SHA-256（16 進位小寫 64 碼）。驗證通過即更新最後存取時間（節流為每分鐘最多寫一次）。30 分鐘、8 小時、5 次與雜湊參數皆為程式常數。log 不記密碼、token、雜湊值。
- **前端狀態**：`/me` 與代碼表由 `composables/useSession.ts` 共用；代碼表快取到登出、401 或重新登入時清除（`resetSession`）。
- **log**：後端寫 `/home/tomcat/log/im_acct_insp/`、殼 jar 寫 `/home/tomcat/log/im_acct_insp_web/`（Windows 為啟動所在磁碟的同路徑），保留 30 天。
- **DB 共用硬規則**：RD_USER 與既有「機房異動申請」模組共用；既有模組的表（IM_USER、IM_ROLE、IM_USER_ROLE_MAP、IM_APPR*、IM_ATTACH 等）本模組不可建立、刪除、修改或在程式引用；重疊概念一律另建 IM_ACCT_ 前綴的新表，人員一律參照 IM_ACCT_USER。

**後端主要程式**（`im_acct_insp\src\main\java\com\mpx\im_acct_insp\`）

| 層 | 檔案與職責 |
|---|---|
| common | `AppConfig`（Clock、TransactionTemplate、啟用排程、載入業務設定）、`Identity`／`IdentityProvider`／`IdentityArgumentResolver`（取身分單一介面，Controller 宣告 Identity 參數即注入）、`TokenCodec`、`PasswordHasher`、`AcctConst`（代碼、時效、通知收件角色）、`AcctMsg`（錯誤文案）、`ApiException`、`GlobalExceptionHandler`（例外轉 `{message}`；multipart 超限轉 413 E15；其他 500 不外洩）、`DocSeqService`（取號）、`Texts`（顯示名、trim、LIKE 跳脫、欄寬檢查） |
| controller | `AuthController`、`HomeController`、`AccountController`、`RequestController`、`AttachmentController`、`InspectionController`、`JobController`；只轉呼叫 service，不寫權限邏輯 |
| service | `AuthService`（登入、鎖定、登出、`/me`、切換身分）、`TokenIdentityProvider`（驗 token、代理被停用自動切回）、`PermissionService`（can、執行人、此刻能處理哪關、可看申請單／帳號、到期相關性）、`RequestService`（送出驗證、7 關狀態機、樂觀鎖、在途、主表同步、歷史、交易）、`ReviewRule`（審查條件）、`AccountService`、`AttachmentService`、`NotificationService`、`MailSender`／`LogMailSender`（只寫 log 的寄信實作）、`JobService`、`HomeService`、`InspectionService`、`CodeService` |
| dao | `UserDao`、`PermDao`、`LoginTokenDao`、`AdminLogDao`、`CodeDao`、`AcctDao`、`AcctReqDao`（帶條件狀態轉移 UPDATE）、`AcctHistDao`、`AttachDao`、`NotifyLogDao`、`JobRunDao`、`HomeDao`、`InspDao`（整體狀態 CASE SQL）、`DocSeqDao`、`DaoSupport` |
| tools | `PwdHashTool`：從主控台讀密碼（不回顯、不收命令列參數）輸出 PWD_HASH 字串 |

**前端主要程式**（`im_acct_insp_web_frontend\src\`）：`api\http.ts`（axios，baseURL `/im_acct_insp_web/api/v1`，401 清 token）、`api\tokenStore.ts`（sessionStorage）、`router\index.ts`（路由與 meta.need 權限碼）、`composables\useSession.ts`、`layouts\AppLayout.vue`（頁首、導覽）、`views\*View.vue`（各畫面）、`components\*Modal.vue`（各彈窗）、`utils\requestForm.ts`（表單模型與送出前驗證）、`utils\labels.ts`（代碼中文、狀態文字、操作人顯示）、`utils\attach.ts`（前端附件檢查）、`utils\perms.ts`、`utils\codes.ts`。

**殼 jar**：`BackendForwarder` 共用轉送（去掉長度類 header；log 不記 Authorization 與本文）；7 個 Controller 路徑與後端一一對應，只呼叫 BackendForwarder。

**各模組交易內容**

| 動作 | 交易內（依序） | 提交後 |
|---|---|---|
| 送出申請 | 取號 → INSERT 申請單 → 新增類型 INSERT 主表（REVIEW、SOURCE_CODE＝APPLY） → INSERT 公文 → INSERT 歷史 SUBMIT | 寄 SUBMIT |
| 重新送出 | 帶條件 UPDATE 申請單 → 新增類型 UPDATE 主表（覆寫、改回 REVIEW） → 舊公文失效＋新公文 → 歷史 RESUB | 寄 RESUB |
| 核准 | 帶條件 UPDATE 申請單 → 歷史 APPROVE | 寄 NEXT_STAGE／TO_EXEC |
| 退件 | 帶條件 UPDATE 申請單 → 新增類型主表改 REJECTED → 歷史 REJECT | 寄 REJECT |
| 回報執行完畢 | 帶條件 UPDATE 申請單 → 舊佐證失效＋新佐證 → 更新主表 → 歷史 EXEC_DONE → 寫審查日期 | 寄 EXEC_DONE |
| 複驗通過／退回 | 帶條件 UPDATE 申請單 → 歷史 VERIFY_OK／VERIFY_REJECT | 退回寄 VERIFY_REJECT |
| 巡檢送出 | 取號 → INSERT 巡檢單 → INSERT 全部結果 | — |
| 標記處理 | 帶條件 UPDATE 結果（異常且未處理） | — |
| 切換身分／切回 | UPDATE token 列 → INSERT 管理軌跡 | — |
| J01 單筆轉逾期 | 帶條件 UPDATE 主表 → 歷史 EXPIRE | 由下一步驟寄 EXPIRED |

**取號**（IM_DOC_SEQ）：樂觀 CAS——讀 CURR_SEQ → `UPDATE … SET CURR_SEQ＝原值＋1 WHERE DOC_TYPE_ID、SEQ_DATE、CURR_SEQ＝原值`，影響 0 筆重試，最多 10 次；當日第一筆先 INSERT，主鍵衝突改走 UPDATE。

### 資料結構

21 張表、42 個索引（PK 21＋一般 21）、5 個 SEQUENCE；不建實體 FK，參照由程式維護。以下已於 2026-10-10 對照測試 DB（Oracle 19c、RD_USER）逐表核對：表、PK、欄位、序列、IND_IM_ACCT_REQ_04 運算式皆相符。repo 內沒有建表與初始資料 SQL，已知差異，修正見 BACKLOG 第 6 項。

**慣例**

- 中文欄 `VARCHAR2(n CHAR)`（欄寬以字數計）、代碼／英數欄 `VARCHAR2(n)`（以 UTF-8 byte 計）、旗標 `NUMBER(1)`（1＝是）、日期時間 `DATE`（到秒）、流水號 `NUMBER(12)`。人員欄一律存工號 `VARCHAR2(30)`。
- `VARCHAR2(n CHAR)` 仍受 Oracle 單欄 4000 bytes 上限約束：`VARCHAR2(2000 CHAR)` 實際約只容 1333 個中文字（見 BACKLOG 第 11 項）。
- PK 名 `PK_表名`；索引名 `IND_表名_NN`。
- 每張表最後固定**共用五欄**（下列各表不重列）：STATUS NUMBER(1) 必填預設 1（1＝啟用、0＝停用；業務狀態另用 *_STATE_CODE）、CREATE_DATE DATE 必填預設 SYSDATE、CREATE_BY VARCHAR2(30) 必填（排程與初始資料為 SYSTEM）、UPDATE_DATE DATE、UPDATE_BY VARCHAR2(30)。
- 有 DB DEFAULT 的必填欄（IS_PRIV、EXPIRY_TYPE_CODE、IS_UNIQUE_CONF 等）目前由 Service 先填值；DAO 若以參數顯式綁定 NULL 會蓋過 DEFAULT（見 BACKLOG 第 12 項）。

下表「必」＝NOT NULL，PK 欄以（PK）標示。

**IM_ACCT_USER 使用者**（DB 維護；STATUS 0＝不可登入，若正被扮演則自動視為切回）

| 欄 | 型別 | 必 | 說明 |
|---|---|---|---|
| USER_ID（PK） | VARCHAR2(30) | ✓ | 工號＝登入帳號，數字字串最多 7 碼 |
| USER_EN_NAME | VARCHAR2(50) | ✓ | 英文顯示名 |
| USER_NAME | VARCHAR2(30 CHAR) | | 中文姓名 |
| DEPT_NAME | VARCHAR2(50 CHAR) | | 部門 |
| EMAIL | VARCHAR2(100) | | 空＝不收信（通知記 SKIP） |
| TEL_NO | VARCHAR2(30) | | 分機／電話 |
| PWD_HASH | VARCHAR2(200) | | 密碼雜湊；空＝不可登入 |
| PWD_UPDATE_DATE | DATE | | 密碼最後設定時間 |
| LOGIN_FAIL_CNT | NUMBER(2) | ✓ | 連續失敗次數，預設 0 |
| LOCK_DATE | DATE | | 非空＝鎖定中 |
| LAST_LOGIN_DATE | DATE | | 最後成功登入 |

**IM_ACCT_ROLE 角色**：ROLE_ID（PK）VARCHAR2(20)；ROLE_NAME VARCHAR2(30 CHAR) 必；ROLE_DESCR VARCHAR2(200 CHAR)；IS_SYSTEM NUMBER(1) 必（1＝不可刪除：GENERAL、SYS_ADMIN；預設 0）；SORT_SEQ NUMBER(3) 必（預設 0）。STATUS＝0 不計入權限。

**IM_ACCT_PERM 權限碼**：PERM_ID（PK）VARCHAR2(30)；PERM_NAME VARCHAR2(50 CHAR) 必；MODULE_CODE VARCHAR2(10) 必（ACCT、INSP、SYS、REVIEW、EMG、STATS）；PHASE_CODE VARCHAR2(1) 必（1、2、3）；SORT_SEQ NUMBER(3) 必。STATUS＝0 不計入。

**IM_ACCT_ROLE_PERM 角色權限對照**：ROLE_ID（PK）VARCHAR2(20)、PERM_ID（PK）VARCHAR2(30)；含 GENERAL 的列。STATUS＝0 即不再授予。索引 IND_IM_ACCT_ROLE_PERM_01（PERM_ID）。

**IM_ACCT_ROLE_USER 角色人員**：ROLE_ID（PK）VARCHAR2(20)（不得為 GENERAL）、USER_ID（PK）VARCHAR2(30)。STATUS＝0 即不再具此角色（下一個請求生效）。索引 IND_IM_ACCT_ROLE_USER_01（USER_ID）。

**IM_EXEC_MAP 執行人對照**：CATG_ID（PK）VARCHAR2(10)、USER_ID（PK）VARCHAR2(30)。STATUS＝0 即失效。索引 IND_IM_EXEC_MAP_01（USER_ID）。

**IM_ACCT_LOGIN_TOKEN 登入 token**（每次登入一列；過期列不自動清除；CREATE_BY＝登入者）

| 欄 | 型別 | 必 | 說明 |
|---|---|---|---|
| TOKEN_HASH（PK） | VARCHAR2(64) | ✓ | token 的 SHA-256 |
| USER_ID | VARCHAR2(30) | ✓ | 實際使用者 |
| ACT_AS_USER_ID | VARCHAR2(30) | | 代理身分；空＝未切換 |
| EXPIRE_DATE | DATE | ✓ | 登入＋8 小時 |
| LAST_ACCESS_DATE | DATE | ✓ | 最後使用時間 |
| LOGOUT_DATE | DATE | | 非空＝已作廢 |

索引 IND_IM_ACCT_LOGIN_TOKEN_01（USER_ID）。

**IM_ACCT_ADMIN_LOG 系統管理軌跡**（只增不改；SEQ_IM_ACCT_ADMIN_LOG；STATUS 固定 1）

| 欄 | 型別 | 必 | 說明 |
|---|---|---|---|
| LOG_ID（PK） | NUMBER(12) | ✓ | 流水號 |
| ACT_DATE | DATE | ✓ | 發生時間 |
| ACT_BY | VARCHAR2(30) | ✓ | 實際操作人 |
| ACT_BY_NAME | VARCHAR2(100 CHAR) | ✓ | 顯示名快照 |
| ACTION_CODE | VARCHAR2(30) | ✓ | IMPERSONATE、IMPERSONATE_END；ROLE_GRANT、ROLE_REVOKE、EXEC_GRANT、EXEC_REVOKE 為預留值，程式不寫入 |
| TARGET_USER_ID | VARCHAR2(30) | | 對象使用者 |
| CONTENT_JSON | VARCHAR2(2000 CHAR) | | 切換時記 `{"targetUserName":"…"}` |
| REASON | VARCHAR2(500 CHAR) | | 原因（切換身分可空） |
| RESULT_CODE | VARCHAR2(10) | ✓ | SUCCESS、DENY |

索引 IND_IM_ACCT_ADMIN_LOG_01（ACT_DATE）。

**IM_ACCT_APPR_STAGE 簽核關卡**：STAGE_ID（PK）NUMBER(2)（1＝申請人送出、2＝機房管理員、3＝Infra 主管、4＝資訊治理、5＝資訊主管、6＝執行人、7＝資訊治理複驗）；STAGE_NAME VARCHAR2(30 CHAR) 必；STAGE_TYPE_CODE VARCHAR2(10) 必（SUBMIT、APPR、EXEC、VERIFY）；PERM_ID VARCHAR2(30)（2～5 為 APPR_G2～G5、7 為 VERIFY_G7；1、6 為空——1 看 ACC_APPLY、6 看 IM_EXEC_MAP）。

**IM_DOC_SEQ 單號流水號**（無初始資料；STATUS 固定 1）：DOC_TYPE_ID（PK）VARCHAR2(2)（AR、IN；EA 為預留值）；SEQ_DATE（PK）VARCHAR2(8)（yyyymmdd）；CURR_SEQ NUMBER(3) 必（預設 0）。

**IM_ACCT_CATG 帳號類別**：CATG_ID（PK）VARCHAR2(10)；CATG_NAME VARCHAR2(30 CHAR) 必；CATG_DESCR VARCHAR2(100 CHAR)（表單旁範例文字）；IS_ENABLED NUMBER(1) 必（開放新增，預設 0）；IS_SUGGEST_EXPIRY NUMBER(1) 必（預設 0）；SORT_SEQ NUMBER(3) 必。新增時另需 STATUS＝1。

**IM_ASSET_CLASS 資產分類碼**：ASSET_CLASS_ID（PK）VARCHAR2(5)；ASSET_TYPE_CODE VARCHAR2(1) 必（H、S）；ASSET_CLASS_NAME VARCHAR2(50 CHAR) 必；IS_REVIEW_TRIGGER NUMBER(1) 必（預設 0）；IS_SELECTABLE NUMBER(1) 必（預設 1）；CTRL_DESCR VARCHAR2(200 CHAR)；SORT_SEQ NUMBER(3) 必。

**IM_INSP_ITEM 巡檢項目**（STATUS 即是否啟用）：INSP_ITEM_ID（PK）VARCHAR2(10)；SECT_CODE VARCHAR2(1) 必（A、B）；SECT_NAME VARCHAR2(50 CHAR) 必；ITEM_NAME VARCHAR2(100 CHAR) 必；ITEM_DESCR VARCHAR2(200 CHAR)；SORT_SEQ NUMBER(3) 必。

**IM_ACCT 帳號主表**（存目前生效的內容；新增送出時建立，之後只在關卡 6 依申請內容更新；刪除為狀態 DELETED）

| 欄 | 型別 | 必 | 說明 |
|---|---|---|---|
| ACCT_ID（PK） | VARCHAR2(20) | ✓ | 帳號單號＝新增申請 REQ_ID |
| CATG_ID | VARCHAR2(10) | ✓ | 帳號類別 |
| ACCT_NAME | VARCHAR2(100 CHAR) | | 帳號名稱；門禁卡為卡號（關卡 6 後才有值） |
| SYS_NAME | VARCHAR2(100 CHAR) | | 所屬系統；門禁卡固定「機房門禁管制系統」 |
| DOOR_SCOPE_CODE | VARCHAR2(10) | | TEST、FULL（僅門禁卡） |
| ASSET_CLASS_ID | VARCHAR2(5) | | 空＝未分類 |
| IS_PRIV | NUMBER(1) | ✓ | 特權，預設 0 |
| HOLDER_NAME | VARCHAR2(50 CHAR) | | 實際使用人／保管人 |
| HOLDER_DEPT | VARCHAR2(50 CHAR) | | 使用人所屬單位 |
| HOLDER_CNTCT | VARCHAR2(100 CHAR) | | 聯絡方式（門禁卡不用） |
| HOLDER_EMAIL | VARCHAR2(100) | | 使用人 Email（僅門禁卡） |
| ACCESS_LEVEL_CODE | VARCHAR2(10) | | RO、OP、ADMIN、DOOR（門禁卡固定） |
| ACCESS_TARGET | VARCHAR2(200 CHAR) | | 存取對象（門禁卡不用） |
| APPLY_REASON | VARCHAR2(500 CHAR) | | 最近一次生效申請的原因 |
| EXPIRY_TYPE_CODE | VARCHAR2(10) | ✓ | PERM、DATE，預設 'PERM' |
| EXPIRY_DATE | DATE | | 到期日（時分秒為 0） |
| ACCT_STATE_CODE | VARCHAR2(10) | ✓ | REVIEW、APPROVED、EXPIRED、DISABLED、REJECTED、DELETED |
| SOURCE_CODE | VARCHAR2(10) | ✓ | APPLY；MIGRATE 為預留值；預設 'APPLY' |
| APPLY_BY | VARCHAR2(30) | | 原新增申請人（之後異動不覆蓋） |
| LAST_REVIEW_DATE | DATE | | 上次審查日 |
| NEXT_REVIEW_DATE | DATE | | 下次應審查日 |
| IS_REVIEW_FLAG | NUMBER(1) | ✓ | 審查異常標記，預設 0（預留，程式不寫入） |
| REVIEW_FLAG_REASON | VARCHAR2(500 CHAR) | | 預留 |
| REVIEW_FLAG_SOURCE_CODE | VARCHAR2(10) | | 預留（REVIEW、EMG） |
| REVIEW_FLAG_DATE | DATE | | 預留 |
| REVIEW_FLAG_BY | VARCHAR2(30) | | 預留 |

索引：IND_IM_ACCT_01（ACCT_STATE_CODE, CATG_ID）、_02（ACCT_NAME）、_03（EXPIRY_DATE）、_04（NEXT_REVIEW_DATE）、_05（APPLY_BY）。

**IM_ACCT_REQ 帳號申請單**（在途期間不動主表；停用、刪除申請由主表複製當下內容）

| 欄 | 型別 | 必 | 說明 |
|---|---|---|---|
| REQ_ID（PK） | VARCHAR2(20) | ✓ | 申請單號 AR |
| ACCT_ID | VARCHAR2(20) | ✓ | 目標帳號；新增＝本單號 |
| REQ_TYPE_CODE | VARCHAR2(10) | ✓ | ADD、MODIFY、DISABLE、ENABLE、DELETE |
| REQ_STATE_CODE | VARCHAR2(10) | ✓ | REVIEW、EXEC、VERIFY、APPROVED、REJECTED |
| CURR_STAGE_ID | NUMBER(2) | | REVIEW＝2～5、EXEC＝6、VERIFY＝7；其他為空 |
| APPLY_BY | VARCHAR2(30) | ✓ | 申請人（有效身分） |
| APPLY_DEPT | VARCHAR2(50 CHAR) | ✓ | 申請單位 |
| APPLY_DATE | DATE | ✓ | 第一次送出時間；重送不變 |
| CATG_ID | VARCHAR2(10) | ✓ | 申請內容－帳號類別 |
| ACCT_NAME | VARCHAR2(100 CHAR) | | 申請內容－帳號名稱；門禁卡新增目前一直為空（BACKLOG 第 2 項） |
| SYS_NAME | VARCHAR2(100 CHAR) | | 申請內容－所屬系統 |
| DOOR_SCOPE_CODE | VARCHAR2(10) | | 申請內容－門禁範圍 |
| ASSET_CLASS_ID | VARCHAR2(5) | | 申請內容－資產分類 |
| IS_PRIV | NUMBER(1) | ✓ | 申請內容－特權，預設 0 |
| HOLDER_NAME | VARCHAR2(50 CHAR) | | 申請內容－使用人 |
| HOLDER_DEPT | VARCHAR2(50 CHAR) | | 申請內容－所屬單位 |
| HOLDER_CNTCT | VARCHAR2(100 CHAR) | | 申請內容－聯絡方式 |
| HOLDER_EMAIL | VARCHAR2(100) | | 申請內容－使用人 Email |
| ACCESS_LEVEL_CODE | VARCHAR2(10) | | 申請內容－權限範圍 |
| ACCESS_TARGET | VARCHAR2(200 CHAR) | | 申請內容－存取對象 |
| APPLY_REASON | VARCHAR2(500 CHAR) | | 申請內容－申請原因（停用／刪除申請時為停用／刪除原因） |
| EXPIRY_TYPE_CODE | VARCHAR2(10) | ✓ | 申請內容－有效期限類型，預設 'PERM' |
| EXPIRY_DATE | DATE | | 申請內容－到期日 |
| IS_UNIQUE_CONF | NUMBER(1) | ✓ | 帳號唯一性聲明，預設 0 |
| SUBMIT_DATE | DATE | ✓ | 最近一次送出時間 |
| RESUB_CNT | NUMBER(3) | ✓ | 重送次數，預設 0 |
| REJECT_CNT | NUMBER(3) | ✓ | 被退件次數，預設 0 |
| REJECT_STAGE_ID | NUMBER(2) | | 最近一次退件關卡 |
| REJECT_BY | VARCHAR2(30) | | 最近一次退件人 |
| REJECT_DATE | DATE | | 最近一次退件時間 |
| REJECT_REASON | VARCHAR2(500 CHAR) | | 最近一次退件原因 |
| EXEC_BY | VARCHAR2(30) | | 最近一次回報的執行人 |
| EXEC_DATE | DATE | | 最近一次回報時間 |
| EXEC_DESCR | VARCHAR2(500 CHAR) | | 執行說明 |
| CARD_NO | VARCHAR2(50) | | 門禁卡號 |
| EFFECT_DATE | DATE | | 生效時間＝第一次回報；之後不覆寫 |
| VERIFY_BY | VARCHAR2(30) | | 複驗通過人 |
| VERIFY_DATE | DATE | | 複驗通過時間 |
| VERIFY_REJECT_CNT | NUMBER(3) | ✓ | 複驗退回次數，預設 0 |
| VERIFY_REJECT_REASON | VARCHAR2(500 CHAR) | | 最近一次複驗退回原因 |
| DONE_DATE | DATE | | 結案時間 |
| VERSION_NO | NUMBER(9) | ✓ | 樂觀鎖版本，預設 0 |

索引：IND_IM_ACCT_REQ_01（ACCT_ID）、_02（REQ_STATE_CODE, CURR_STAGE_ID）、_03（APPLY_BY, SUBMIT_DATE）、_04（UNIQUE 函式索引，同帳號一筆在途）：

```sql
CREATE UNIQUE INDEX IND_IM_ACCT_REQ_04 ON RD_USER.IM_ACCT_REQ
  (CASE WHEN REQ_STATE_CODE IN ('REVIEW','EXEC','VERIFY') THEN ACCT_ID END);
```

**IM_ACCT_ATTACH 申請單附件**（SEQ_IM_ACCT_ATTACH；STATUS 0＝已被新檔取代，仍可下載）：ATTACH_ID（PK）NUMBER(12)；REQ_ID VARCHAR2(20) 必；ATTACH_TYPE_CODE VARCHAR2(10) 必（DOC、PROOF）；FILE_NAME VARCHAR2(255 CHAR) 必（原檔名）；FILE_PATH VARCHAR2(500) 必（`REQ_ID/uuid.副檔名`）；FILE_SIZE NUMBER(12) 必（≤ 10,485,760）；MIME_TYPE VARCHAR2(100) 必。索引 IND_IM_ACCT_ATTACH_01（REQ_ID）。

**IM_ACCT_HIST 帳號歷史事件**（只增不改；SEQ_IM_ACCT_HIST；STATUS 固定 1；CREATE_BY＝實際操作人或 SYSTEM）

| 欄 | 型別 | 必 | 說明 |
|---|---|---|---|
| HIST_ID（PK） | NUMBER(12) | ✓ | 流水號（同秒排序） |
| ACCT_ID | VARCHAR2(20) | ✓ | 帳號 |
| REQ_ID | VARCHAR2(20) | | 來源申請單（SOURCE_CODE＝REQ） |
| EMG_ID | VARCHAR2(20) | | 預留，目前為空 |
| REVIEW_ID | NUMBER(12) | | 預留，目前為空 |
| SOURCE_CODE | VARCHAR2(10) | ✓ | REQ、SYS（EMG、REVIEW 為預留值） |
| ACTION_CODE | VARCHAR2(20) | ✓ | SUBMIT、RESUB、APPROVE、REJECT、EXEC_DONE、VERIFY_OK、VERIFY_REJECT、EXPIRE（REVIEW_DONE、REVIEW_REJECT、FLAG_CLEAR、EMG_MODIFY、EMG_CLOSE_MODIFY 為預留值） |
| STAGE_ID | NUMBER(2) | | 發生關卡；系統事件為空 |
| ACT_BY | VARCHAR2(30) | | 實際操作人；排程為空 |
| ACT_BY_NAME | VARCHAR2(100 CHAR) | ✓ | 顯示名快照；排程為「系統排程」 |
| ACT_AS_ID | VARCHAR2(30) | | 代理身分 |
| ACT_AS_NAME | VARCHAR2(100 CHAR) | | 代理身分顯示名快照 |
| ACT_DATE | DATE | ✓ | 發生時間 |
| APPLY_DEPT | VARCHAR2(50 CHAR) | | 申請單位快照 |
| RESULT_DESCR | VARCHAR2(1000 CHAR) | | 結果文字 |
| IS_SELF_ACTION | NUMBER(1) | ✓ | 自行核准／同人兼任，預設 0 |

索引：IND_IM_ACCT_HIST_01（ACCT_ID, ACT_DATE）、_02（REQ_ID）。

**IM_INSP 巡檢單**：INSP_ID（PK）VARCHAR2(20)（IN）；INSP_DATE DATE 必；INSP_BY VARCHAR2(30) 必（實際填寫人）；INSP_BY_NAME VARCHAR2(100 CHAR) 必；ACT_AS_ID VARCHAR2(30)；ACT_AS_NAME VARCHAR2(100 CHAR)；SIGN_DATE DATE 必。索引 IND_IM_INSP_01（INSP_DATE）。

**IM_INSP_RESULT 巡檢結果明細**（每單每個啟用中項目一筆；STATUS 固定 1）

| 欄 | 型別 | 必 | 說明 |
|---|---|---|---|
| INSP_ID（PK） | VARCHAR2(20) | ✓ | 巡檢單 |
| INSP_ITEM_ID（PK） | VARCHAR2(10) | ✓ | 項目 |
| ITEM_NAME_SNAP | VARCHAR2(100 CHAR) | ✓ | 項目名稱快照 |
| SECT_NAME_SNAP | VARCHAR2(50 CHAR) | ✓ | 大類名稱快照 |
| RESULT_CODE | VARCHAR2(10) | ✓ | NORMAL、EXCPT |
| EXCPT_DESCR | VARCHAR2(500 CHAR) | | 異常說明 |
| IS_FIXED | NUMBER(1) | ✓ | 已處理；NORMAL 固定 0；預設 0 |
| FIX_DESCR | VARCHAR2(500 CHAR) | | 處理方式與結果 |
| FIX_BY | VARCHAR2(30) | | 實際處理人 |
| FIX_BY_NAME | VARCHAR2(100 CHAR) | | 顯示名快照 |
| FIX_ACT_AS_ID | VARCHAR2(30) | | 處理時代理身分 |
| FIX_ACT_AS_NAME | VARCHAR2(100 CHAR) | | 代理身分顯示名快照 |
| FIX_DATE | DATE | | 標記處理時間 |

索引 IND_IM_INSP_RESULT_01（RESULT_CODE, IS_FIXED）。整體狀態 SQL：

```sql
CASE
  WHEN SUM(CASE WHEN r.RESULT_CODE = 'EXCPT' THEN 1 ELSE 0 END) = 0 THEN 'NORMAL'
  WHEN SUM(CASE WHEN r.RESULT_CODE = 'EXCPT' AND r.IS_FIXED = 0 THEN 1 ELSE 0 END) > 0 THEN 'EXCPT'
  ELSE 'FIXED'
END
```

**IM_ACCT_NOTIFY_LOG 通知紀錄**（每收件人每封一筆；排程通知防重寄依據；SEQ_IM_ACCT_NOTIFY_LOG；STATUS 固定 1）

| 欄 | 型別 | 必 | 說明 |
|---|---|---|---|
| NOTIFY_ID（PK） | NUMBER(12) | ✓ | 流水號 |
| EVENT_CODE | VARCHAR2(20) | ✓ | 11 種事件（見「通知」） |
| REF_TYPE_CODE | VARCHAR2(10) | ✓ | REQ、ACCT、WEEK、JOB |
| REF_ID | VARCHAR2(40) | ✓ | REQ＝單號；ACCT＝`ACCT_ID_到期日yyyymmdd`；WEEK＝週一 yyyymmdd；JOB＝RUN_ID |
| RCPT_USER_ID | VARCHAR2(30) | ✓ | 收件人 |
| RCPT_EMAIL | VARCHAR2(100) | | 寄出當下的 Email；SKIP 時為空 |
| SEND_DATE | DATE | ✓ | 嘗試寄送時間 |
| RESULT_CODE | VARCHAR2(10) | ✓ | SUCCESS、FAIL、SKIP |
| ERR_MSG | VARCHAR2(500 CHAR) | | 失敗原因（不含帳密） |

索引 IND_IM_ACCT_NOTIFY_LOG_01（EVENT_CODE, REF_ID, RCPT_USER_ID）。

**IM_ACCT_JOB_RUN 排程執行紀錄**（SEQ_IM_ACCT_JOB_RUN；STATUS 固定 1）：RUN_ID（PK）NUMBER(12)；JOB_CODE VARCHAR2(10) 必（J01＝到期處理、J02＝每週彙總）；RUN_BY VARCHAR2(30)（手動觸發者；排程為空）；BASE_DATE DATE 必（採用的今天）；START_DATE DATE 必；END_DATE DATE；RESULT_CODE VARCHAR2(10) 必（RUNNING、SUCCESS、PARTIAL、FAIL）；AFFECTED_CNT NUMBER(6) 必（預設 0）；ERR_MSG VARCHAR2(1000 CHAR)（失敗筆的帳號與例外類別）。索引 IND_IM_ACCT_JOB_RUN_01（JOB_CODE, START_DATE）。

**SEQUENCE**：SEQ_IM_ACCT_ATTACH、SEQ_IM_ACCT_ADMIN_LOG、SEQ_IM_ACCT_NOTIFY_LOG、SEQ_IM_ACCT_JOB_RUN（NOCACHE）；SEQ_IM_ACCT_HIST（CACHE 20）；皆 START WITH 1。

**邏輯參照**（重點；其餘同名欄即參照同名主鍵）：IM_ACCT.ACCT_ID 與建立它的 IM_ACCT_REQ.REQ_ID 值相同；IM_ACCT_REQ.ACCT_ID → IM_ACCT；IM_ACCT_HIST.REQ_ID 僅 SOURCE_CODE＝REQ 時有值；IM_ACCT_NOTIFY_LOG.REF_ID 依 REF_TYPE_CODE 指向不同對象（組合鍵）；所有人員欄 → IM_ACCT_USER.USER_ID（排程與初始資料為 SYSTEM）；IM_DOC_SEQ 不與其他表關聯。

**顯示名的兩種算法**：快照欄（*_NAME）由 `Texts.displayName` 產生（含 trim、英文名空白退回中文名再退回工號）；申請單明細的 applyByName／execByName／verifyByName 與附件上傳人則由 SQL 即時 JOIN 計算（`AcctReqDao.nameExpr`：中文名為空只顯示英文名，否則「英文名 (中文名)」，不 trim、無工號退回）。見 BACKLOG 第 13 項。

**初始資料**（CREATE_BY＝SYSTEM、STATUS＝1；測試 DB 筆數已核對相符）

| 表 | 筆數 | 內容 |
|---|---|---|
| IM_ACCT_ROLE | 9 | 見「角色」，SORT_SEQ 1～9 依表列順序（SYS_ADMIN、GENERAL、DC_ADMIN、INFRA_MGR、GOV、GOV_MGR、IT_HEAD、ACCT_REGISTRY、INSP_STAFF） |
| IM_ACCT_PERM | 28 | 見「權限碼」 |
| IM_ACCT_ROLE_PERM | 35 | 見「角色 × 權限」 |
| IM_ACCT_APPR_STAGE | 7 | 見上方表定義 |
| IM_ACCT_CATG | 8 | 見下 |
| IM_ASSET_CLASS | 15 | 見「資產分類碼」與下方 CTRL_DESCR，SORT_SEQ 1～15 依代碼順序 |
| IM_INSP_ITEM | 14 | 見「巡檢項目」，SORT_SEQ 1～14，ITEM_DESCR 為空 |
| IM_EXEC_MAP | 12 | 見「帳號類別」初始執行人 |
| IM_ACCT_USER | 12 | 假使用者，見下 |
| IM_ACCT_ROLE_USER | 20 | 見下 |
| 其他業務與紀錄表 | 0 | IM_ACCT、IM_ACCT_REQ、IM_ACCT_ATTACH、IM_ACCT_HIST、IM_INSP、IM_INSP_RESULT、IM_ACCT_NOTIFY_LOG、IM_ACCT_JOB_RUN、IM_ACCT_LOGIN_TOKEN、IM_ACCT_ADMIN_LOG、IM_DOC_SEQ |

假使用者（部門與聯絡資料為假值；12 位共用同一個初始密碼，只存雜湊，本文件不記載；未鎖定）：

| 工號 | 顯示名 | 角色（GENERAL 不列） | 執行人類別 |
|---|---|---|---|
| 0000001 | Kevin (李凱鈞) | GOV、INSP_STAFF | — |
| 0000002 | Molly (林宜瑾) | GOV、GOV_MGR、ACCT_REGISTRY、INSP_STAFF | — |
| 0000003 | Cathy (周佩欣) | GOV | — |
| 0000004 | Terry (邱健唐) | INFRA_MGR、ACCT_REGISTRY、INSP_STAFF | DB |
| 0000005 | Wensen (林文松) | INFRA_MGR、INSP_STAFF | — |
| 0000006 | Alex (陳儀仁) | DC_ADMIN、INSP_STAFF | HOST、APP、SVC、NET、CERT、DOOR |
| 0000007 | Alan (王祖樑) | DC_ADMIN、INSP_STAFF | SVC、CERT、DOOR |
| 0000008 | Michael (許漢煒) | 僅 GENERAL | DB |
| 0000009 | Sage (秦一中) | 僅 GENERAL | VENDOR |
| 0000010 | Gary (許永成) | IT_HEAD、ACCT_REGISTRY、INSP_STAFF | — |
| 0000011 | Hank (何嘉浤) | SYS_ADMIN | — |
| 0000012 | Tester (一般測試員) | 僅 GENERAL | — |

Michael、Sage 沒有任何清單權限，待辦只看到被指派執行的單；Terry 同時是關卡 3 核准人與 DB 類執行人。

帳號類別初始資料：

| CATG_ID | CATG_NAME | CATG_DESCR | IS_ENABLED | IS_SUGGEST_EXPIRY | SORT_SEQ |
|---|---|---|---|---|---|
| HOST | 主機帳號 | 例如：POS 後台主機、財務 Oracle 主機 | 1 | 0 | 1 |
| DB | 資料庫帳號 | 例如：Oracle、MySQL、MSSQL | 1 | 0 | 2 |
| APP | 應用系統帳號 | 例如：VG 電子價標、ERP、POS 後台管理 | 0 | 0 | 3 |
| SVC | 服務帳號 | 例如：備份服務、監控 Agent | 0 | 1 | 4 |
| NET | 網路設備帳號 | 例如：防火牆、核心交換器 | 0 | 0 | 5 |
| VENDOR | 廠商/委外帳號 | 例如：委外維護廠商臨時帳號 | 0 | 1 | 6 |
| CERT | 憑證/金鑰 | 例如：SSL 憑證、API Key | 0 | 1 | 7 |
| DOOR | 機房門禁卡 | 卡鐘，例如：測試區、機房實體門禁 | 1 | 0 | 8 |

資產分類 CTRL_DESCR：

| 代碼 | CTRL_DESCR |
|---|---|
| H01、H02、H03 | 存取控制：只有獲得授權的人員，可以進入管制區域。 |
| H04 | 得考量有適當之實體保護措施，限制授權人員方可進入管制區域。 |
| H05 | 診斷埠／組態埠之使用宜採帳號密碼辨識、限定來源位置、通訊加密等保護措施。 |
| H06 | 得考量適當的實體保護措施，必要時宜使用帳號密碼辨識使用者身分。 |
| H07 | 得考量適當的實體保護措施，並確保僅有經授權之人員可使用。 |
| H08、H10 | 得考量適當的實體保護措施。 |
| H09 | 得視資產特性採取適當之實體保護措施，例如僅經授權之人員可調整監視器角度。 |
| S01 | 存取權限賦予應與角色職責相符，且應至少每半年清查帳號與權限一次，交由權責單位覆核。 |
| S02 | 將可存取的帳號及權限減到最少或最小，賦予應與角色職責相符。 |
| S03、S04 | 須維持各系統之帳號權限清單，並於人員調離職時及時更新清單。 |
| S05 | 管理測試與套裝軟體的使用授權。 |

### API 規格

後端根路徑 `/api/v1`；經殼 jar 為 `/im_acct_insp_web/api/v1`，路徑一一對應。

**通則**

- JSON 請求與回應，欄位 camelCase，與 DB 欄名對應（ACCT_NAME → acctName）。日期 `yyyy-MM-dd`、時間 `yyyy-MM-dd HH:mm:ss`。
- 錯誤格式 `{ "message": "…" }`，前端原樣顯示。
- multipart：part `form` 為 JSON（Content-Type application/json，前端以 Blob 送），檔案 part 名稱見各 API。
- 除 `POST /auth/login` 外都需 `Authorization: Bearer <token>`，無效 → 401 A03。
- 附件超過 10 MB（後端 multipart 上限）或請求超過 11 MB（殼 jar 上限）時，在權限、E01 等所有業務檢查之前就回 413 E15。

| 狀態碼 | 意義 |
|---|---|
| 400 | 驗證失敗（E01～E06、E10～E14、E17、E18、缺 versionNo 或必填 E02、欄位內容不正確、日期格式錯、不可切換為自己、新增申請不可指定帳號、巡檢時間不可晚於現在） |
| 401 | A01、A02（登入）、A03（其他 API） |
| 403 | P01：無權限碼、不在資料範圍、非原申請人重送、非該類別執行人 |
| 404 | N01：單號／帳號／附件／巡檢單不存在、lookup 名稱不符、排程代碼不是 J01／J02、切換對象不存在或停用 |
| 409 | E07 在途、E08 版本或狀態不符、E09 帳號狀態不符前提、E16 排程執行中、E19 巡檢項目已處理或非異常、E20 未切換身分 |
| 413 | E15 |
| 500 | 其他（不外洩例外內容） |

**身分**

| 方法 | 路徑 | 權限 | 請求 | 回應 | 主要錯誤 |
|---|---|---|---|---|---|
| POST | `/auth/login` | 無 | userId、password | token、me | 401 A01、A02 |
| POST | `/auth/logout` | 登入 | — | 200 空本文 | 401 |
| GET | `/me` | 登入 | — | userId、userName（有效身分）、realUserId、realUserName、impersonating、canImpersonate（實際操作人有 SYS_IMPERSONATE 且未切換）、permissions[]、roleIds[]、execCatgIds[]、telNo、email | 401 |
| GET | `/users` | 實際操作人 SYS_IMPERSONATE | — | [userId、userName、roleIds[]]（啟用中、不含自己） | 403 |
| POST | `/auth/impersonate` | 實際操作人 SYS_IMPERSONATE | targetUserId | me | 403（寫 DENY）、400 E02／不可切換為自己、404 |
| POST | `/auth/impersonate/end` | 登入（切換中） | — | me | 409 E20 |

**共用**

| 方法 | 路徑 | 權限 | 回應 |
|---|---|---|---|
| GET | `/codes` | 登入 | categories[catgId、catgName、catgDescr、enabled、suggestExpiry、sortSeq]、assetClasses[assetClassId、assetTypeCode、assetClassName、reviewTrigger、selectable、ctrlDescr、sortSeq]、stages[stageId、stageName、stageTypeCode、permId] |
| GET | `/home/stats` | 登入 | acctTotal、acctReviewing、acctExpiring、acctPriv、inspThisMonth、inspExcptThisMonth |
| GET | `/home/expiring` | 登入 | [acctId、acctName、catgId、catgName、acctStateCode、expiryDate、daysLeft]（不含 applyBy） |
| GET | `/todos` | 登入 | [source（ACCT_REQ）、reqId、title、reqTypeCode、catgId、catgName、acctName、isPriv、applyBy、applyByName、stageId、stageName、submitDate、actionKind（APPROVE、EXEC、VERIFY）、versionNo、verifyRejectCnt、verifyRejectReason] |

**帳號**

| 方法 | 路徑 | 權限 | 請求 | 回應 | 主要錯誤 |
|---|---|---|---|---|---|
| GET | `/accounts` | ACC_LIST_FULL | query：keyword、catgId、stateCode、assetClassId、expiring（1＝只看到期提醒範圍） | items[IM_ACCT 全欄、catgName、assetClassName、inFlight、inFlightReqId、pendingVerify、allowedReqTypes[]]、stateCounts、catgCounts、expiringCount | 403 |
| GET | `/accounts/mine` | ACC_SELF_SERVICE | — | 同 items | 403 |
| GET | `/accounts/lookup` | ACC_SELF_SERVICE | query：acctName（必填） | [acctId、catgId、catgName、acctName、sysName、holderName、acctStateCode、expiryTypeCode、expiryDate、inFlight、allowedReqTypes[]] | 400 E02、403 |
| GET | `/accounts/lookup/{id}` | ACC_APPLY＋範圍 | query：acctName | IM_ACCT 內容欄位 | 403、404 |
| GET | `/accounts/{id}` | 可看帳號 | — | IM_ACCT 全欄、catgName、assetClassName、inFlight、pendingVerify、allowedReqTypes | 403、404 |
| GET | `/accounts/{id}/history` | 可看帳號 | — | [histId、actDate、sourceCode、actionCode、stageId、stageName、reqId、actBy、actByName、actAsId、actAsName、applyDept、resultDescr、isSelfAction、selfActionLabel] | 403、404 |

- 列表預設不含 DELETED（指定 stateCode 時依指定）。stateCounts 套用 stateCode 以外的條件；catgCounts 套用 catgId 以外的條件；expiringCount 不受篩選影響。

**申請單**

| 方法 | 路徑 | 權限 | 請求 | 回應 | 主要錯誤 |
|---|---|---|---|---|---|
| POST | `/requests` | ACC_APPLY | multipart：form＝reqTypeCode、acctId、applyDept、catgId、acctName、sysName、doorScopeCode、assetClassId、isPriv、holderName、holderDept、holderCntct、holderEmail、accessLevelCode、accessTarget、applyReason、expiryTypeCode、expiryDate、isUniqueConf；`docFile`（選填） | reqId | 400 E01～E06、E11、E14；403；404；409 E07、E09；413 E15 |
| GET | `/requests` | ACC_SELF_SERVICE | query：stateCode | [reqId、acctId、reqTypeCode、reqStateCode、currStageId、stageName、catgId、catgName、acctName、sysName、applyDate、submitDate、rejectReason、canResubmit] | 403 |
| GET | `/requests/{id}` | 可看申請單 | — | IM_ACCT_REQ 全欄（含 versionNo）、stageName、applyByName、execByName、verifyByName、attachments[attachId、attachTypeCode、fileName、fileSize、createDate、createByName]、history、actions[]（APPROVE、REJECT、EXEC_DONE、VERIFY_OK、VERIFY_REJECT、RESUBMIT） | 403、404 |
| POST | `/requests/{id}/approve` | 該關權限 | versionNo | reqId、reqStateCode、currStageId、versionNo | 403、400 E02、404、409 E08 |
| POST | `/requests/{id}/reject` | 該關權限 | versionNo、reason | 同上 | 同上＋400 E10 |
| POST | `/requests/{id}/resubmit` | 原申請人＋ACC_APPLY | multipart：form＝送出內容欄位＋versionNo（reqTypeCode、acctId 忽略）；`docFile`（選填，有則取代舊公文） | 同上 | 403、400 E01～E06、E11、E14；409 E07、E08、E09；413 |
| POST | `/requests/{id}/exec-done` | 該類別執行人 | multipart：form＝versionNo、cardNo、execDescr；`proofFile`（必填） | 同上 | 403、400 E02、E11、E12、E13、E14；409 E08；413 |
| POST | `/requests/{id}/verify-ok` | VERIFY_G7 | versionNo | 同上 | 403、400 E02、409 E08 |
| POST | `/requests/{id}/verify-reject` | VERIFY_G7 | versionNo、reason | 同上 | 同上＋400 E10 |
| GET | `/attachments/{id}` | 可看申請單 | — | 原始位元組；Content-Type＝MIME；Content-Disposition attachment（RFC 5987 編碼原檔名） | 403、404 |

**送出驗證順序**（遇第一個錯誤即回）

| 順序 | 前端（`utils/requestForm.ts`） | 後端送出 `POST /requests`（`RequestService.submit`） |
|---|---|---|
| 0 | — | 附件超限 413 E15（見通則）；無 ACC_APPLY → 403 |
| 1 | E01 唯一性聲明 | E01 |
| 2 | E02 必填（一次列出缺的欄位，以頓號分隔） | 申請類型必填與值域；新增帶帳號 → 400；非新增缺帳號 → E02「帳號」；**非新增帳號不存在 → 404 N01** |
| 3 | E03 指定日缺到期日（停用、刪除不做） | E02 必填 |
| 4 | E04 到期日早於今天（新增、修改、重新啟用） | （停用、刪除：內容複製主表，跳過 E03～E11）E03 |
| 5 | E05 新增時類別未開放 | E04 |
| 6 | E06 資產分類不可選 | E05（僅新增） |
| 7 | E11 欄寬：`n CHAR` 欄以字數、`VARCHAR2(n)` 欄以 UTF-8 byte 計 | E06 → 代碼欄值域（欄位內容不正確）→ E11 |
| 8 | 附件：**副檔名 E14 → 大小 E15** → 空檔提示 | 附件：**大小 413 E15 → 副檔名 E14** → 檔名欄寬 → 檔頭 E14 |
| 9 | — | 前提狀態 409 E09 → 在途 409 E07 → 交易（唯一索引衝突 → E07） |

- 已知差異，修正見 BACKLOG 第 5 項：前後端在「帳號不存在 404 的位置」與「附件副檔名／大小的先後」兩處順序不同。
- E11 與 E03 同時成立時回 E03。代碼欄值不在值域 → 400「欄位內容不正確：{欄位}」；日期格式錯 → 400「日期格式錯誤（yyyy-MM-dd）」。
- 重新送出（`RequestService.resubmit`）順序：非原申請人或無 ACC_APPLY → 403 → 缺 versionNo → E02 → 狀態非已退件或版本不符 → 409 E08 → E01 → 非新增帳號不存在 → 404 → 內容檢查（同送出第 3～7 步；新增的帳號類別取自表單）→ 附件 → E09 → E07 → 交易。E08 先於 E01 為已知差異，修正見 BACKLOG 第 5 項。
- 回報執行完畢（`RequestService.execDone`）順序：非該類別執行人 → 403 → 缺 versionNo → E02 → 狀態／關卡／版本不符 → 409 E08 → 附件檢查（E15、E14）→ **缺佐證 E12 → 門禁卡新增缺卡號 E13** → 卡號超過 50 bytes、執行說明超過 500 字 → E11 → 交易。前端彈窗先檢查卡號再檢查佐證；後端「E02／E08／附件格式先於 E12」與「E12 先於 E13」皆為已知差異，修正見 BACKLOG 第 5 項。

**巡檢**

| 方法 | 路徑 | 權限 | 請求 | 回應 | 主要錯誤 |
|---|---|---|---|---|---|
| GET | `/inspection-items` | INSP_VIEW 或 INSP_CREATE | — | [inspItemId、sectCode、sectName、itemName、itemDescr、sortSeq]（啟用中、依排序） | 403 |
| POST | `/inspections` | INSP_CREATE | inspDate（時間）、results[inspItemId、resultCode、excptDescr] | inspId | 403、400 E18、E10、E11、巡檢時間晚於現在 |
| GET | `/inspections` | INSP_VIEW | query：keyword、stateCode、dateFrom、dateTo | [inspId、inspDate、inspByName、actAsName、excptCnt、unfixedCnt、inspStateCode] | 403 |
| GET | `/inspections/{id}` | INSP_VIEW | — | 上列欄位＋signDate、results[inspItemId、sectNameSnap、itemNameSnap、resultCode、excptDescr、isFixed、fixDescr、fixByName、fixActAsName、fixDate] | 403、404 |
| POST | `/inspections/{id}/items/{itemId}/fix` | INSP_RESOLVE | fixDescr | inspId、inspStateCode | 403、404、400 E10、409 E19 |

keyword 以 LIKE 比對 INSP_ID、INSP_BY_NAME、ACT_AS_NAME（跳脫 `\ % _`，`ESCAPE '\'`）。

**排程**

| 方法 | 路徑 | 權限 | 請求 | 回應 | 主要錯誤 |
|---|---|---|---|---|---|
| POST | `/jobs/{code}/run` | SYS_JOB_RUN | code＝J01／J02；query：baseDate（選填） | runId、resultCode、affectedCnt | 403、404（其他 code）、400 E17、409 E16 |

**錯誤訊息**（後端 message，前端原樣顯示；文案來源 `common/AcctMsg.java`）

| 代號 | 狀態 | 文案 |
|---|---|---|
| E01 | 400 | 請勾選帳號唯一性聲明 |
| E02 | 400 | 請填寫必填欄位：{欄位名，以頓號分隔} |
| E03 | 400 | 請填寫到期日 |
| E04 | 400 | 到期日不可早於今天 |
| E05 | 400 | 此帳號類別尚未開放申請 |
| E06 | 400 | 此資產分類碼不可選用 |
| E07 | 409 | 此帳號目前已有一筆申請正在處理中 |
| E08 | 409 | 此申請已由他人處理，請重新整理 |
| E09 | 409 | 此帳號目前的狀態不可提出此類申請 |
| E10 | 400 | 請填寫{原因／異常說明／處理方式與結果}（退件與複驗退回＝原因、巡檢異常＝異常說明、標記處理＝處理方式與結果） |
| E11 | 400 | 欄位長度超過上限：{欄位名} |
| E12 | 400 | 請上傳執行佐證附件 |
| E13 | 400 | 請填寫門禁卡號 |
| E14 | 400 | 附件格式僅限 .pdf .doc .docx .jpg .png |
| E15 | 413 | 附件大小不可超過 10 MB |
| E16 | 409 | 排程執行中，請稍後再試 |
| E17 | 400 | 此環境不允許指定基準日 |
| E18 | 400 | 巡檢項目不完整，請重新整理後再填寫 |
| E19 | 409 | 此項目已處理或不是異常項目 |
| E20 | 409 | 目前未切換身分 |
| A01 | 401 | 帳號或密碼錯誤 |
| A02 | 401 | 此帳號已鎖定，請聯絡系統管理員 |
| A03 | 401 | 登入已逾時，請重新登入 |
| P01 | 403 | 您沒有執行此操作的權限 |
| N01 | 404 | 查無資料 |

無代號的 400 文案：欄位內容不正確：{欄位}／日期格式錯誤（yyyy-MM-dd）／時間格式錯誤（yyyy-MM-dd HH:mm:ss）／新增申請不可指定帳號／不可切換為自己／巡檢時間不可晚於現在。

### 環境設定

實際設定檔不進 git，repo 內只有 `.example` 範本；以下只列 key 與用途，怎麼建立與填值見 `SETUP.md`。

**後端 `im_acct_insp\src\main\resources\`**

| 檔案 | key | 用途 |
|---|---|---|
| `application.properties` | `spring.application.name` | Spring 專案名（不影響 log 落點） |
| | `server.port` | 後端監聽 port |
| | `spring.servlet.multipart.max-file-size`、`spring.servlet.multipart.max-request-size` | 附件單檔與請求上限（未設定時程式預設 10MB／11MB） |
| | `server.tomcat.max-swallow-size` | 讓超大附件能正常回 413，而不是被 Tomcat 直接斷線 |
| `config\host.properties` | `rt-api.domain`、`db.connect.api.port`、`db.connect.api.path` | DB 連線資訊 API 的協定＋主機、port、路徑 |
| | `db.connect.api.domain.path` | 由上面三個 key 組合，程式只讀這個；四個 key 都要保留（值可空） |
| `config\database.properties` | `db.connect.itflow` | 本模組 21 張表所在 DB 的別名（連線資訊 API 認得的名稱）；所有 DAO 讀這個 key |
| `config\environment.properties` | `im.acct.insp.attach.dir` | 附件根目錄絕對路徑（必填）；未設定或非絕對路徑時後端啟動失敗 |
| | `im.acct.insp.job.enabled` | 是否啟用排程（關閉時手動 API 仍可用） |
| | `im.acct.insp.job.j01.cron`、`im.acct.insp.job.j02.cron` | J01、J02 觸發時間（Spring 六欄 cron，Asia/Taipei） |
| | `im.acct.insp.job.allow-base-date` | 是否允許手動執行時指定 baseDate（測試環境用） |

`environment.properties` 不存在時後端仍可啟動、缺 key 用程式預設值（附件根目錄除外）。

**殼 jar `im_acct_insp_web\im_acct_insp_web\src\main\resources\`**

| 檔案 | key | 用途 |
|---|---|---|
| `application.properties` | `spring.application.name`、`server.port`、`server.servlet.context-path` | 專案名、監聽 port、context（`/im_acct_insp_web`） |
| | `spring.servlet.multipart.enabled` | 須為關閉，multipart 本文才會原樣轉送給後端 |
| | `server.tomcat.max-swallow-size` | 同後端 |
| `config\host.properties` | `backend.api.domain`、`backend.api.port`、`im.acct.insp.path` | 後端 API 的協定＋主機、port、根路徑（含後端 context，以 / 開頭、結尾不加 /） |
| | `im.acct.insp.domain.path` | 由上面三個 key 組合，程式只讀這個；四個 key 都要保留 |

**前端**：無環境變數；API baseURL 固定為 `/im_acct_insp_web/api/v1`（與殼 jar 同源）。
