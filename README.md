# infra_manager 帳號權限管理／機房巡檢系統

資訊處用的 Web 系統：管理系統帳號、憑證與機房門禁卡的申請、7 關簽核、生效與到期追蹤，並提供機房巡檢紀錄與異常處理。
由後端 API（`infra_manager\`）、殼 jar 與 Vue 前端（`infra_manager_web\`）組成，資料存在 Oracle。

- 要跑起來：看 `SETUP.md`
- 系統規格（現況）：看 `PRD.md`
- 待辦：看 `BACKLOG.md`
- 變更歷史：看 `CHANGELOG.md`

根目錄的 `im_acct_insp_交接文件.md`、`帳號權限管理與機房巡檢_SA文件_v0.5.*`、`im_acct_insp_Table關係圖_v0.2.html`、`機房與帳號權限管理系統_系統說明_4.pptx` 是原始交付文件，僅供追溯；與 `PRD.md` 不一致時以 `PRD.md` 為準。
