# 第 7 項 S7 簽核引擎（施工計畫）

> 依據：2026-10-07 `architect` 開工前分析，使用者裁示 ①B；②～⑧ 依衝刺規則照建議施工（②A ③A ④A ⑤A ⑥A ⑦A ⑧A）。
> 本檔是施工計畫與交接欄；結案後整檔刪除，歷史進 `CHANGELOG.md`。

## 裁示結果

| 編號 | 題目 | 裁示 | 內容 |
|------|------|------|------|
| ① | 申請人能否簽自己的單 | **B（使用者裁示）** | 展開候選人時排除申請人（`APPLY_USER_ID`，不是送審者）；排除後某關 0 人就擋送審，400「第 N 關（關卡名）沒有可簽核的人，請聯絡管理員」。admin 代送審時一樣排除申請人 |
| ② | 送審時的必填清單 | A | 照舊表單 `new.ejs` 的 `*` 欄位：申請單位、聯絡電話、Email、作業主題；至少一個執行人員；處理方式 REMOTE 時連線方式必填；勾委外廠商時執行廠商／廠商聯絡人／廠商電話必填。**設備位置不檢核**（S6 ⑦A：S13 前不得要求位置必填）。一次列出全部缺漏：400「送審前請先補齊：申請單位、聯絡電話」 |
| ③ | S7 要不要寫信件 outbox | A | 不寫；S8 補到 5 個時點（送審、進下一關、末關同意、退件、撤回） |
| ④ | 簽核時附檔 | A | `POST /decisions` 只收 JSON 意見；附檔登記下一階段 |
| ⑤ | 首頁統計與導覽列待辦徽章 | A | 不做；列表 `mineCount` 就是待辦數 |
| ⑥ | 只通知關卡（notifyOnly） | A | 展開時直接設 SKIPPED、不展開候選人 |
| ⑦ | 送審時是否重算 FLOW_ID | A | 送審時依當下 `FLOW_POLICY` 重算並寫回 `IM_APP.FLOW_ID` |
| ⑧ | 候選人固化時機 | A | 送審時固化寫入 `IM_APPR_CAND_MAP`（PRD 設計；S4 的 `mineExpr` 已照此） |

## 採用的假設（使用者可隨時推翻）

- admin 可送審（PRD「申請人或 admin」）；撤回只限申請人（PRD）
- 撤回時已有關卡簽過回 409（舊系統 400）
- 意見與撤回原因上限 2000 字（`TextLength.LIMIT_SHORT`）
- 同意時意見空白自動填「同意」（舊 `routes/apps.js:985-1026`）
- 退件時未輪到的關卡設 SKIPPED，撤回時設 CANCELLED（DDL 欄位註解）
- AI 閘門等第 17 項拍板，S7 的 `canSubmit` 不擋
- 第 100 項 N1（服務層 403 訊息被換成通用文字）依衝刺規則不當場修
- 事件表只寫 SUBMIT／RECALL（DDL 事件碼沒有同意／退件），同意與退件只記在 `IM_APPR_STEP`
- 程式一律用 `ROLE_ID` 判斷角色，不得用 `STEP_CODE`（第 54 項 key 錯位）

## 設計要點

- 三個端點都在 `AppFlowController`（`/api/apps/{id}/submit`、`/recall`、`/decisions`），body 帶 `rowVerNo`，成功回 200 `{appId, rowVerNo(新)}`
- 交易第一句是條件式 UPDATE `IM_APP`（版本＋狀態＋申請人條件）並 `ROW_VER_NO + 1`，同時當整張單的列鎖；0 列照 S6 分流 404／403／409
- 權限在鎖內重判，不信任前端 `permissions`；403 連同版本加一一起 rollback
- 送審：UPDATE 主檔 → 鎖內讀單做必填檢核 → 重算 FLOW_ID → INSERT `IM_APPR` 並依唯一索引查回 APPR_ID → `INSERT…SELECT` 展開關卡（序號最小且非只通知 PENDING、只通知 SKIPPED、其餘 WAITING）→ `INSERT…SELECT` 展開候選人（ROLE 型三表皆啟用、USER 型取啟用中指定人、排除申請人）→ 檢查沒有 0 候選人關卡 → 事件 SUBMIT
- 撤回：UPDATE 主檔回 DRAFT → 查無已簽關卡（有則 409）→ 未結束關卡 CANCELLED、`IM_APPR` RECALLED → 事件 RECALL（原因放 MEMO）
- 同意／退件（R2）：UPDATE 主檔版本加一 → 找序號最小 PENDING 關卡、比對候選人（無候選人列退回流程定義指定人，同 `AppPermissionService.isCurrentApprover`）→ 條件式 UPDATE 關卡（WHERE PENDING，0 列 409 第二道防線）→ 同意：下一關 PENDING 或結案 APPROVED；退件：意見必填、剩餘 WAITING → SKIPPED、結案 REJECTED

## 回合切分

### R1：後端送審＋撤回
- 新增 `dao/changerequest/ApprovalWriteDao`、`service/changerequest/AppFlowService`（submit、recall）、`AppSubmitValidator`、`controller/changerequest/AppFlowController`、`model/changerequest/FlowActionRequest`
- 修改 `AppWriteDao`（`transition` 條件式 UPDATE、`updateFlowId`）、`AppPermissionService`（`canSubmit`）
- 測試：`AppSubmitValidatorTest`、`AppFlowServiceTest`、`AppPermissionServiceTest` 更新、`AppFlowControllerTest`；IT `AppFlowSubmitIT`（送審展開 5 關、每關有候選人且不含申請人、事件 SUBMIT；撤回後 DRAFT／RECALLED／CANCELLED；必填失敗無殘留 `IM_APPR`；撤回後可再送審）
- 完成判定：`./mvnw -q clean package` 全綠；IT 在 R2 一起用 `verify` 跑

### R2：後端簽核＋兩個完成條件
- 新增 `service/approval/DecisionPolicy`（純函式）、`AppFlowService.decide`、`POST /decisions`、`model/changerequest/DecisionRequest`
- `ApprovalWriteDao` 加 decideStep／activateNext／skipWaiting
- IT `AppFlowDecisionIT`：完成條件一（`S7IT-` 單號，S4U002 idc_admin → S4U003 dept_manager → S4U004 governance → S4U005 it_manager → S4U004 依序同意，斷言三層皆 APPROVED、`countMine` 增減正確）；完成條件二（一關兩位候選人，兩執行緒同一 `rowVerNo` 以 CountDownLatch 同時出發，恰一成功一 `ApiConflictException`，關卡 USER_ID 是成功者；測試類不加 `@Transactional`、加 `@Timeout`）
- 需要使用者提供測試 DB 密碼跑 `./mvnw verify`

### R3：前端
- `api/apps.ts` 加 submitApp／recallApp／decideApp；`types/app.ts` 加型別；`AppViewView.vue` `act()` 改接真動作：送審先確認、撤回選填原因＋確認、簽核顯示意見欄＋同意／退件（退件先確認且意見必填）；視需要 `components/DecisionPanel.vue`
- 409 一律 toast 後端訊息並重新載入；1024 寬無橫向捲動
- 手動驗收：送審後待我簽核數加一、候選人同意一次、撤回一張沒人簽過的單、同帳號兩分頁同時簽後送出者見 409

### 收尾回合
- `code-reviewer` → 當場修阻擋項 → 非阻擋登記 → 補 PRD／CHANGELOG → 刪本檔

## 風險（施工時留意）
- R-a 附件上傳 `FOR UPDATE` 無等待上限（第 100 項 N8）：大檔上傳中按送審會等到上傳結束
- R-b IT 依賴 S4 種子帳號 S4U001～S4U005 仍在測試 DB
- R-c 完成條件只在 `verify` 驗得到，階段結案前必跑一次
- R-d 送審後某關候選人全部停用會卡死，無改派功能（舊系統亦然）

## 交接欄
- 目前回合：R2
- 已完成：R1（2026-10-07）——`ApprovalWriteDao`（insertAppr／findPendingAppr／insertSteps／insertCandidates／countOpenSteps／
  findOpenStepsWithoutCandidate／countDecidedSteps／closeOpenSteps／closeAppr／insertEvent）、`AppFlowService.submit`／`recall`、
  `AppSubmitValidator`、`AppFlowController`（`POST /{id}/submit`、`/{id}/recall`）、`AppWriteDao.transition`／`updateFlowId`、
  `canSubmit` 開啟。單元測試 `AppSubmitValidatorTest`／`AppFlowServiceTest`／`AppPermissionServiceTest` 全綠；
  IT `AppFlowSubmitIT` 已寫、尚未對真實 DB 跑（R2 一起 verify）。`AppFlowControllerTest` 未寫（R1 預算用完，R2 補或收尾一起）
- R1 自行決定（衝刺規則，使用者可推翻）：admin 代送審時 403 訊息為「只有申請人或管理員可以送審」；撤回原因全空白存 null；
  `transition` 的申請人條件用 boolean 參數轉 0／1 綁定（避免 Oracle null 綁定型別問題）；送審時 `IM_APP.CURR_VER_NO` 為 null 視為 1
- 下一步：R2 施工（`DecisionPolicy`、`AppFlowService.decide`、`POST /decisions`、`ApprovalWriteDao` 加 decideStep／activateNext／skipWaiting、
  `AppFlowDecisionIT`），完成後向使用者要測試 DB 密碼跑 `./mvnw verify`
