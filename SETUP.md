# 環境設定與操作手冊（SETUP）

> 目前程式尚未建立（S1 骨架施工後補齊安裝與啟動步驟）。本檔先定好工具版本與環境變數名稱。

## 安全規則

**連線資訊與金鑰不得寫進任何 repo 內的檔案**（包含 `application.yml`、程式碼、bat 檔、測試設定、文件）。一律用環境變數提供；`.env` 已列入 `.gitignore`。

## 需要的工具

| 工具 | 版本 | 備註 |
|---|---|---|
| JDK | 21 以上（LTS） | 確切版本 S1 動工當天確認 |
| Node.js | LTS | 前端 Vue 3 + Vite 用 |
| 建置工具 | Maven 或 Gradle | S1 決定 |
| Oracle | 19c | 開發期連公司 19c 測試 schema；本機不需安裝 Oracle、不使用 Docker |

## 環境變數

環境變數的**設定方式**（放哪裡、怎麼載入）S1 開工時連同 `BACKLOG.md` 第 18 項（AI 金鑰存放）一起決定後補上步驟；在那之前只先定好名稱。實際值向 DBA 或系統負責人取得，不要貼進任何檔案或 commit。

| 環境變數 | 用途 |
|---|---|
| `IM_DB_URL` | Oracle JDBC 連線字串，格式 `jdbc:oracle:thin:@//host:1521/service` |
| `IM_DB_USER` | Oracle 帳號 |
| `IM_DB_PASSWORD` | Oracle 密碼 |
| `IM_SESSION_SECRET` | session 簽章用 secret |
| `IM_SMTP_*` | SMTP 連線與認證，細項 S8 定 |
| `IM_RACK_API_KEY` | Impact 機櫃盤點 API 金鑰 |
| AI 金鑰 | 存放方式待定（見 `BACKLOG.md` 第 18 項），名稱屆時補上 |

## 啟動與停止

S1 補。

## 部署

S15 補（含 WinSW 服務化與切換 runbook）。
