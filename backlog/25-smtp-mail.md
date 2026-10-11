# 第 25 項：通知信改為實際寄出（SMTP）

> 提出日：2026-10-11。寄法參考 `D:\ai\Infra_Manager_Java` 已實作的寄信功能；測試期間只寄給 2410305，其他人一律不寄。

## 使用者裁示（2026-10-11）

| 項 | 裁示 |
|----|------|
| 寄送架構 | 只新增 SMTP 版寄信實作、取代只寫 log 的版本；同步寄送，失敗記 FAIL、不重試；不做發件匣表與背景排程（參考專案的 outbox／worker 不採用） |
| 寄送名單 | 用「寄送名單」（工號清單）限制收件人，不採「改寄到單一信箱」；不在名單的人不寄 |
| 名單外怎麼記 | 通知紀錄記 SKIP，ERR_MSG 寫「不在寄送名單」；IM_ACCT_NOTIFY_LOG.RESULT_CODE 的 SKIP 說明同步改（`db/` DDL 檔與測試 DB 的 COMMENT） |
| 名單留空 | 誰都不寄；要寄給所有人須另外設「寄給所有人＝true」 |
| 2410305 的 Email | 使用者已自行填入測試 DB |
| SMTP | 同參考專案：`mail.pxmart.com.tw`、port 25、不加密、無帳號認證 |
| 寄件人 | 沿用 `Infra-Manager@pxmart.com.tw` |
| 測試帳號角色 | 2410305 加 DC_ADMIN、INFRA_MGR、GOV、GOV_MGR、IT_HEAD、SYS_ADMIN 六個角色，並在 IM_EXEC_MAP 加 8 個帳號類別的執行人對照，讓每種信都能測到；測完要移除只需 DELETE |
| 測試信觸發 | 用 2410305 的角色實際觸發（排程或送一張測試單），驗證時再依當下狀況決定 |

## 施工計畫

單一功能單位，一回合做完：

1. pom 加 `spring-boot-starter-mail`（版本由 Spring Boot BOM 管理）
2. 新增寄信設定類別與 SMTP 版實作；SMTP 未設定時退回只寫 log 的版本
3. `NotificationService.sendAll` 加寄送名單過濾（名單外記 SKIP、ERR_MSG「不在寄送名單」）
4. 設定範本：`application.properties.example` 加 `spring.mail.*`，`environment.properties.example` 加名單、寄給所有人、寄件人
5. 單元測試
6. 文件：PRD、SETUP 設定說明、`im_acct_insp\README.md`、DDL 檔 SKIP 的 COMMENT
7. 測試 DB：SKIP COMMENT 同步、2410305 加角色與執行人對照；實際寄一封給 2410305 驗證
