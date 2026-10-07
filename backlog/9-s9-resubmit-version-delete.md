# 第 9 項 S9 補件、版次、刪除 施工計畫

> 2026-10-07 開工。開工分析由 `architect` 產出；15 項決策中第 ⑤ 項由使用者裁示 **⑤A**，其餘依衝刺規則照建議施工。
> 完成條件：補件後 v2，v1 簽核紀錄查得到。S9 結案時跑一次 `code-reviewer`，補 PRD（第 32、153、161 行與版次表達）與 CHANGELOG，然後刪本檔。

## 已拍板的 15 項決策

| # | 決策 | 要點 |
|---|------|------|
| ① | 補件交易順序 | 第一句 `AppWriteDao.transition(REJECTED → IN_REVIEW, applicantOnly=true)` 兼列鎖；0 列用 `findLockState` 分流 404／403「只有申請人可以補件」／409「申請單不是退件狀態，無法補件，請重新載入頁面」／409 `MSG_STALE`。admin 也不能代為補件 |
| ② | 補件本文 | 一支 `POST /api/apps/{id}/resubmit`，平坦結構 `{...AppDraftRequest 欄位, rowVerNo, resubMemo}`；一次完成改內容、快照、送審。resubMemo 上限 2000 字 |
| ③ | FORM_JSON 快照 | 專用 record `AppVersionSnapshot`，帶 `snapshotSchema:1`，重用 AppDetail 巢狀 record。放：title、prio（代碼＋名稱）、Applicant、selfExec／supplierExec／workMode／remoteMethod／Supplier、五段文字、categories、reasons、otherReason、scopes、equipments、planSteps、Schedule、Location、flowId／flowName、resubmitMemo（舊版那份）、附件索引（attachId、檔名、大小、上傳時間；只列 OWNER_TYPE=APP、STATUS=1、CREATE_DATE ≤ 舊版結束時間）。不放：statusCode、permissions、approval、events、versions、rowVerNo、checklist、execution。鎖內呼叫 `appQueryService.detail()` 再投影；序列化用注入的 Jackson 3 `JsonMapper`；寫入用 `SqlParameterValue(Types.CLOB, …)` |
| ④ | CLOSE_STATUS_CODE／VER_REASON | 依目前版次事件推算：有 EXEC_REJECT → EXEC_REJECTED；有 GOV_RETURN → GOV_RETURNED；其他 → REJECTED。VER_REASON 放退件意見（REJECTED 那一關的 MEMO，或該事件 MEMO）；補件說明存 RESUB_MEMO 與 RESUBMIT 事件 MEMO |
| ⑤ | REJECTED 上傳附件 | **使用者裁示 ⑤A**：`AttachmentUploadService` 上傳條件改「DRAFT，或 REJECTED 且是申請人」；只開上傳不開刪除。前端順序「先上傳、再補件」 |
| ⑥ | 舊 IM_APPR 與歷史簽核 | 舊實例維持 REJECTED 不動；AppDetail 新增 `approvalHistory`（目前那筆以外的所有 IM_APPR 與各自關卡，帶 verNo），`ApprovalDao.findHistory(appId, excludeApprId)` 一次 JOIN 後在 Java 分組；順便修第 101 項 ⑤ |
| ⑦ | FLOW_ID／RESUB_MEMO／必填 | 補件用 `AppDraftService.flowFor` 依新優先等級重算；RESUB_MEMO 選填（空白存 null）；必填檢核沿用 `AppSubmitValidator` |
| ⑧ | 刪除權限 | 鎖內用 `AppPermissionService` 重算：admin 任何狀態（DELETE_MODE=ADMIN）；申請人在沒人簽過且狀態不是 APPROVED／IN_EXECUTION／PENDING_REVIEW／EXECUTED 時（APPLICANT_PRE_REVIEW）。申請人不能刪 REJECTED（同舊系統） |
| ⑨ | confirmId | `DELETE /api/apps/{id}` 帶 JSON 本文 `{rowVerNo, confirmId, reason}`；不符回 400「確認編號不符，已取消刪除」。經 3201 殼 jar 實測一次，失敗再退回 `POST /delete` |
| ⑩ | DELETE_REASON | 必填，trim 後不可空，上限 500 |
| ⑪ | 刪除連帶 | 進行中 IM_APPR 與未結關卡改 CANCELLED（重用 `closeOpenSteps`／`closeAppr`）；附件實體檔保留；`IM_APP_SEQ` 不回收；事件 DELETE。順序：先取鎖（只加 ROW_VER_NO 的 `lockForUpdate`）→ 鎖內重判權限 → UPDATE STATUS=0 與 DELETE_*（`CK_IM_APP_DELETE` 要求三欄必填）→ 關簽核 → 事件 |
| ⑫ | 刪除樂觀鎖 | 必帶 rowVerNo，不符回 409 |
| ⑬ | RESTORE | S9 不做，登記 BACKLOG |
| ⑭ | 前端補件入口 | 新路由 `/apps/:id/resubmit`，AppFormView 第三種模式（mode：new／edit／resubmit）；標題「補件並重送」、上方顯示退件資訊、補件說明欄、按鈕「補件並送審」帶 confirm；`load()` 檢查 `permissions.canResubmit`；`save()` 先跑 uploadQueue 再 `resubmitApp`，409 沿用 S7 自動重載 |
| ⑮ | 前端刪除與版次 | 檢視頁對話框（輸入單號＋原因，單號一致才能按確認）；版次區每一版列出該版簽核紀錄（來自 approvalHistory）；刪除成功導回 `/apps` 並 toast；409／403／404 重載 |

## 補件交易步驟（`AppFlowService.resubmit`，@Transactional）

1. 鎖外：`AppDraftValidator.validate`；缺 rowVerNo 回 400 `MSG_NO_VERSION`；單號 `requireVersion` regex
2. `transition(appId, rowVerNo, "REJECTED", "IN_REVIEW", by, applicantOnly=true)`；0 列分流
3. 鎖內讀舊內容組快照（主檔已是 IN_REVIEW，快照不放 statusCode）
4. 判 CLOSE_STATUS_CODE 與 VER_REASON
5. INSERT `IM_APP_VER`（APP_VER_NO＝舊版次）
6. `AppWriteDao.updateForResubmit`：更新內容欄、`CURR_VER_NO+1`、RESUB_MEMO、FLOW_ID；不再加 ROW_VER_NO、不帶 DRAFT 條件（`updateApp` WHERE 寫死 DRAFT 不能重用）
7. 子表 `deleteChildren`／`insertChildren`（6 張）
8. 重讀主檔跑 `AppSubmitValidator.validate`，缺欄位 400 整筆 rollback
9. submit 的建 IM_APPR／關卡／候選人抽成 private `startApproval(appId, verNo, flowId, applyUserId, by)` 共用（S11 AI 閘門掛這裡，涵蓋第 45 項）
10. `insertEvent(appId, 新版次, "RESUBMIT", by, 補件說明)`
11. 回傳 `rowVerNo + 1`

## 回合切分

| 回合 | 範圍 | 狀態 |
|------|------|------|
| R1 後端補件 | ①～⑤、⑦。新增 `ResubmitRequest`、`AppVersionSnapshot`、`AppVerDao`；改 `AppFlowService`（resubmit、startApproval、closeStatus）、`AppWriteDao`（updateForResubmit）、`AppFlowController`、`AttachmentUploadService`。測試：service 補件分支、controller 補件、完成條件 IT、400 不留殘 IT、併發 IT | 完成（2026-10-07）。與計畫的差異：`ResubmitRequest` 採巢狀 `{rowVerNo, resubMemo, form}` 而非攤平（Jackson 3 record 的 `@JsonUnwrapped` 反序列化不完整）；快照由 `AppRow`＋DAO 直接組、不經 `appQueryService.detail()`（避免權限欄混入）；注入 Spring 的 `ObjectMapper` bean；快照附件只含舊版實例結束前上傳的檔 |
| R2 後端刪除＋歷次簽核 | ⑥、⑧～⑫。新增 `DeleteRequest`（delete 可拆 `AppDeleteService`）；改 `AppWriteDao`（lockForUpdate／deleteApp）、`AppPermissionService`（開放 deleteMode）、`AppFlowController`（@DeleteMapping）、`ApprovalDao`（findHistory）、`AppDetail`、`AppQueryService`。測試：刪除各分支單元＋IT、刪除 × 簽核併發、controller DELETE 帶本文；手動經 3201 打一次 DELETE | 完成（2026-10-07）。與計畫的差異：⑨ 的「經 3201 實測」改成殼 jar 端到端測試 `ApiProxyStreamingTest.deleteWithJsonBody_reachesBackendIntact`（真 Tomcat＋真 JDK 用戶端，驗方法、Content-Type、Content-Length、本文 sha256 原樣到後端），因為這個 session 沒有測試帳號密碼、無法登入打真的 DELETE；R3 前端走刪除對話框時會經 3201 實際打一次，屆時若失敗再退回 `POST /delete`。刪除 × 簽核併發結果不對稱：刪除先贏時簽核回 404（單已不在），簽核先贏時刪除回 409，IT 斷言「恰一成功、另一 409 或 404、結果與勝方一致」。`approvalHistory` 的歷次關卡不帶候選人（`candidateNames` 一律空陣列，避免多查一次） |
| R3 前端 | ⑭、⑮。改 `router/index.ts`、`AppFormView.vue`、`AppViewView.vue`、`api/apps.ts`、`types/app.ts`。手動走退件 → 補件 → v2 簽核、刪除對話框 | 未開始 |

## 測試要鎖的點

- 完成條件 IT：v1 送審 → 退件 → 補件 → v2 IN_REVIEW；v1 IM_APPR 仍 REJECTED 查得到；IM_APP_VER 正好一筆 APP_VER_NO=1；`JSON_VALUE(FORM_JSON,'$.title')` 是退件當時標題（補件時故意改標題）
- 補件 400 不留殘：無 IM_APP_VER、無新 IM_APPR、CURR_VER_NO／ROW_VER_NO 不變
- 補件 × 補件併發：同 rowVerNo 兩次，一次 200 一次 409，只一個版次
- 補件權限：非申請人 403、admin 403、非 REJECTED 409
- CLOSE_STATUS_CODE 三分支單元測試
- 刪除 IT：待辦數歸零、IM_APPR 與未結關卡 CANCELLED、DELETE_* 有值、事件 DELETE、檢視 API 404
- 刪除 × 簽核併發：一邊成功另一邊 409
- 刪除驗證：confirmId 不符 400、原因空白 400、申請人刪 REJECTED 403、申請人刪已有人簽的 IN_REVIEW 403、admin 刪 APPROVED 200
- 上傳：REJECTED 申請人 200、非申請人 403、v1 快照附件索引不含 REJECTED 期間上傳的檔
- Controller：resubmit／delete 各測 401、CSRF 403、200 回新 rowVerNo、例外對應；DELETE 帶本文能解析

## 範圍外（結案時登記 BACKLOG）

- DDL 第 515 行 DELETE_MODE_CODE 註解「送審前」與實際規則不符（COMMENT ON 由使用者執行）
- RESTORE 端點、舊版表單內容檢視（`GET /api/apps/{id}/versions/{verNo}`）
- 申請人無法刪或放棄自己被退件的單（同舊系統）
- 第 100 項 N8 鎖等待延伸到 REJECTED 上傳；第 104 項 N1／N2 重載邏輯套用到新動作（R3 順手套）
- 第 69 項匯入要對齊 `snapshotSchema:1` 與「VER_REASON＝退件意見」
- PRD 第 153 行「補件時關閉舊 IM_APPR」已不符實際（decide 退件時已關），結案時改寫
