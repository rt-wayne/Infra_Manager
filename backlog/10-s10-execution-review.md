# 第 10 項 S10 執行與治理審查 施工計畫

> 2026-10-07 開工。開工分析由主對話整理（舊系統行為由 `Explore` 盤點 `D:\ai\Infra_Manager` 的 `routes/apps.js:1133-1342`、`views/apps/execute.ejs`、`review.ejs`、`view.ejs`）；
> 16 項決策中 ⑨（治理審查人與申請人／執行人是否互斥）、⑪（執行人選系統使用者要不要開使用者查詢 API）屬資安取捨，
> 2026-10-07 使用者裁示「全部依建議」：⑨ 不擋（維持舊行為，結案時登記職責分離為日後選項）、⑪ 不開查詢 API。其餘依衝刺規則照建議施工。
> 完成條件：APPROVED → IN_EXECUTION → PENDING_REVIEW → EXECUTED；GOV_RETURN → REJECTED。
> S10 結案時跑一次 `code-reviewer`、整合測試與經 3201 手動驗證，補 PRD（執行、治理審查、ExecuteView、API 表三列、檢視頁）與 CHANGELOG，然後刪本檔。

## 舊系統行為摘要（要保留的與要修的）

- 保留：結果（`RESULT_CODE`）有值才進待治理審核、沒值就停在執行中可反覆暫存；執行端退回意見必填、退回不清既有執行資料；治理退回意見必填；
  申請人本人也可執行與執行端退回（舊 `apps.js:564-566`、`1210-1212`）；補件後新版次的檢核表與執行紀錄從空白開始（新系統依 `APP_VER_NO` 分版，天然成立）
- 修：`POST /execute` 不檢查角色（漏洞第 44 項）；`POST /execute` 接受已結案 `executed`（可把結案單重開）；勾「未完成」卻留著完成時間；
  沒有任何版本檢查（後送出的人整份覆寫）；結果值不檢查是否在清單內；審查頁「通過」前端強制填意見、後端不強制（前後不一致）

## 已拍板的 16 項決策

| # | 決策 | 要點 |
|---|------|------|
| ① | 執行儲存端點 | `PUT /api/apps/{id}/execution`，本文 `{rowVerNo, checklist:[{seqNo, done, doneAt, userId, executorDesc}], actualStart, actualEnd, resultCode, exception, exceptionDesc, followUp, followUpDesc, memo}`；成功 200 `{appId, rowVerNo, statusCode}`。**`resultCode` 有值 → `PENDING_REVIEW`；空 → `IN_EXECUTION`（暫存，可反覆存）**——沿用舊語意與 PRD，不另加 submit 旗標 |
| ② | 執行權限與狀態 | 只限 `idc_admin` 角色或申請人（修第 44 項）；狀態只收 `APPROVED`、`IN_EXECUTION`（不收 `EXECUTED`，修舊系統可重開結案單）。鎖外先做便宜預檢（非 idc_admin 時用 `findLockState` 比對申請人，不是就 403、不取鎖——避開第 105 項 ② 的問題）；再 `lockForUpdate(appId, rowVerNo)` 取鎖，鎖內重讀狀態與權限：0 列分流 404／409 `MSG_STALE`，狀態不對 409「申請單不在待執行或執行中，無法填寫執行紀錄，請重新載入頁面」，無權 403「只有機房管理員或申請人可以填寫執行紀錄」 |
| ③ | 送治理審查時的必填 | `resultCode` 有值時：必須是啟用中的 `EXEC_RESULT` 選項；實際開始、實際結束必填且結束不早於開始；「有異常」時異常說明必填、「需後續追蹤」時說明必填。**不要求 11 項全勾**（結果可能是部分完成／取消，全勾不合理；舊程式也沒有這條，`SYSTEM_README.md:128` 已過時）。錯誤一次列出全部缺漏（同送審 `AppSubmitValidator` 的 400 格式） |
| ④ | 暫存時的檢核 | 只檢格式：日期 `yyyy-MM-dd HH:mm`（也收 `T` 分隔，同草稿）、兩個時間都有時結束不早於開始（DDL `CK_IM_APP_EXEC_ACTUAL_DATE` 也會擋，先擋成 400）、文字長度（說明與備註上限 2000、執行人描述 200）。`resultCode` 非空但不在清單 → 400 |
| ⑤ | 檢核表列的建立 | 第一次儲存執行紀錄時，依啟用中的 `CHECK_LIST` 選項（`SORT_NO` 順序）為目前版次建 11 列（DDL 註解「進入執行時展開」）；之後每次儲存逐列 UPDATE。本文的 `seqNo` 必須是該版已存在（或剛展開）的序號，未知序號 400；本文沒帶到的列維持原值 |
| ⑥ | 檢核項欄位規則 | `done=false` → `DONE_DATE` 清成 null（修舊系統未完成卻留時間）；`done=true` 且沒填時間 → 補伺服器現在時間（同舊系統）。執行人 `userId` 與 `executorDesc` 至多擇一（兩個都給 400，對應 DDL `CK_IM_APP_CHECK_LIST_EXEC_USER`）；`userId` 必須是 `IM_USER` 中啟用的使用者，否則 400 |
| ⑦ | 執行結果列 | `IM_APP_EXEC` 依 (`APP_ID`, `APP_VER_NO`) 有就 UPDATE、沒有就 INSERT。`USER_ID`（結案人）與 `CLOSE_DATE` 只在 `resultCode` 有值、轉 `PENDING_REVIEW` 那次寫入；暫存時兩欄維持 null，暫存人看 `UPDATE_BY` |
| ⑧ | 執行端退回 | `POST /api/apps/{id}/execution/reject`，本文 `{rowVerNo, memo}`，意見必填（去頭尾空白後不可空）、上限 2000；權限與可呼叫狀態同 ②；`→ REJECTED`；事件 `EXEC_REJECT`（意見進 `MEMO`）；不清除已暫存的檢核表與執行紀錄（同舊系統，留在該版次）；成功 200 `{appId, rowVerNo}` |
| ⑨ | 治理審查 | `POST /api/apps/{id}/governance-review`，本文 `{rowVerNo, decision: PASS\|RETURN, memo}`；只限 `governance` 角色（角色判斷不需查 DB，鎖外先擋 403「只有資訊治理人員可以審核執行結果」）；鎖用 `transition(PENDING_REVIEW → EXECUTED／REJECTED, applicantOnly=false)`，0 列分流 404／409「申請單不是待治理審核狀態，無法審核，請重新載入頁面」／409 `MSG_STALE`。PASS → `EXECUTED`、事件 `GOV_PASS`；RETURN → `REJECTED`、事件 `GOV_RETURN`；**退回意見必填、通過意見選填**（以舊後端為準，與簽核「同意不填記為同意」一致），上限 2000。審查人是申請人本人或該版執行人時**不擋**（同舊系統；2026-10-07 使用者裁示） |
| ⑩ | 事件 | 執行儲存不寫事件（DDL 事件碼沒有 EXECUTE，紀錄在 `IM_APP_EXEC` 的結案人與結案時間）；退回與審查寫 `EXEC_REJECT`／`GOV_PASS`／`GOV_RETURN`。補件時 `closeOf` 已能依這兩種事件推算 `EXEC_REJECTED`／`GOV_RETURNED`（S9 已寫好，S10 補 IT 驗證） |
| ⑪ | 執行人選系統使用者 | 選人需要一支使用者查詢 API（任何已登入者可查到全公司使用者姓名與工號），**不開**（2026-10-07 使用者裁示）：後端照 ⑥ 收 `userId`，前端只提供「填入我自己」按鈕（帶登入者工號）與自由文字 |
| ⑫ | 樂觀鎖 | 三支端點都必帶 `rowVerNo`，不符 409 `MSG_STALE`；成功 `ROW_VER_NO + 1`。兩人同時執行：後到者 409（修舊系統後送出整份覆寫） |
| ⑬ | 檢視 API | `permissions.canExecute` 維持（S4 已寫：APPROVED／IN_EXECUTION、尚無結果、idc_admin 或申請人）；`canReview` 維持。`checklist` 在目前版次還沒展開時回空陣列，執行頁改用 `GET /api/form-options` 的 `CHECK_LIST` 選項畫 11 列。治理審查結果不另開欄位，從 `events`（GOV_PASS／GOV_RETURN 的人、時間、意見）呈現 |
| ⑭ | 附件 | 執行、執行端退回、治理審查都**不開附件上傳**（舊系統可附；`IM_ATTACH.OWNER_TYPE` 沒有執行類型，要掛 APP 或 EVENT 需另行設計）——結案時登記 BACKLOG，與第 101 項簽核附檔一起做 |
| ⑮ | 前端 | 新路由 `/apps/:id/execute`（`ExecuteView`）：五、檢核表（11 列；第 5～8 項名稱後接申請單計畫步驟一～四的文字，同舊系統）＋六、實際執行紀錄＋「暫存」與「完成並送治理審查」兩顆鈕（後者要選結果、帶 confirm）＋下方「退回給申請人」區（意見必填、帶 confirm）。進頁檢查 `permissions.canExecute`。治理審查**不另開頁**，做成檢視頁面板（同簽核面板：通過／退回、意見，退回帶 confirm）。檢視頁加「前往填寫執行紀錄」入口。409／403／404 一律重載（沿用 S9 R3 的 isStale） |
| ⑯ | 信件 | 送治理審查通知、退回通知、結案通知都等 S8 信件 outbox，S10 不寄 |

## 回合切分

| 回合 | 範圍 | 狀態 |
|------|------|------|
| R1 後端執行 | ①～⑦、⑫。新增 `ExecutionRequest`（含檢核項 record）、`ExecutionValidator`（暫存與送審兩級檢核，純函式好測）、`AppExecutionService`；改 `AppWriteDao` 或新增 `ExecWriteDao`（展開檢核表、逐列 UPDATE、執行結果 upsert）、`AppFlowController`（或新 `AppExecutionController`）。測試：validator 各分支、service 權限／狀態／分流、controller 401／CSRF 403／200；IT：APPROVED → 暫存 IN_EXECUTION → 送審 PENDING_REVIEW、併發兩人執行一 200 一 409、非 idc_admin 非申請人 403 | 完成（2026-10-07；採新 `ExecWriteDao`＋新 `AppExecutionController`，執行人啟用檢查放 `UserDao.isActive`；IT 已寫、階段結束跑） |
| R2 後端退回＋治理審查 | ⑧～⑩。新增 `GovernanceReviewRequest`；`AppExecutionService` 加 reject、review。測試：單元各分支；IT：完成條件全程（APPROVED → IN_EXECUTION → PENDING_REVIEW → EXECUTED）、GOV_RETURN → REJECTED 後補件 `IM_APP_VER.CLOSE_STATUS_CODE = GOV_RETURNED`、EXEC_REJECT → 補件 `EXEC_REJECTED` 且 v2 檢核表為空 | 未開始 |
| R3 前端 | ⑪、⑬、⑮。新增 `ExecuteView.vue`、路由與 `router/names.ts`；改 `AppViewView.vue`（執行入口、治理審查面板、執行確認列從事件取值）、`api/apps.ts`、`types/app.ts`。測試：vitest 執行頁暫存／送審／退回／409 重載、檢視頁審查面板。**注意（R1 實作帶出）**：後端對 `done=true` 且沒帶 `doneAt` 的檢核項填伺服器現在時間，所以前端重存時必須把已存的 `doneAt` 原樣帶回，否則每次暫存都會把完成時間刷成當下；沒勾異常／後續追蹤時後端把對應說明存 null，前端取消勾選前可提示 | 未開始 |

## 測試要鎖的點

- 完成條件 IT：APPROVED → IN_EXECUTION → PENDING_REVIEW → EXECUTED；每步 ROW_VER_NO + 1；`IM_APP_EXEC.USER_ID`／`CLOSE_DATE` 只在送審那次寫入；事件 GOV_PASS
- GOV_RETURN → REJECTED → 補件 → v2：`IM_APP_VER` v1 `CLOSE_STATUS_CODE = GOV_RETURNED`、`VER_REASON` 是退回意見；v2 檢核表與執行結果查不到列
- 已在 PENDING_REVIEW 或 EXECUTED 的單再打 execution → 409（修舊系統重開結案單）
- 檢核項 `done=false` 帶時間 → 存 null；`userId` 與 `executorDesc` 同時給 → 400；未知 `seqNo` → 400；停用或不存在的 `userId` → 400
- 送審必填一次列全：缺結果以外的每一項各自出現在錯誤清單

## 範圍外（結案時登記 BACKLOG）

- 執行、退回、審查附件（⑭），與第 101 項簽核附檔一起設計 `OWNER_TYPE`
- 「待我執行」「待我審核」待辦清單與首頁計數（舊系統也沒有，只靠信件）
- 職責分離（⑨ 已裁示不擋，登記為日後選項：治理審查人不得是申請人或該版執行人）
- 已送治理審查後執行人無法自行修正（同舊系統，只能等治理退回整單重跑）
