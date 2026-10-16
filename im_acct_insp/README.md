# im_acct_insp 專案說明（給接手開發的 AI 助理）

本專案由 template_jdk25_vue_3_5/backend 範本產生於 2026-10-07

這是一個 Spring Boot 4.1.1／Java 25／Maven 專案，由公司開發範本產生，已內建 DB 連線元件 `com.mpx.common.db`：
只要在 DAO 注入 `DbClient`，給「DB 別名＋SQL＋參數＋model」就能查詢，DataSource、連線池、RowMapper 都不用寫。
連線資訊（jdbcUrl／帳密）不在程式或設定檔裡，執行期由 common.db 向「DB 連線資訊 API」取得。
JSON 使用 Jackson 3（套件 `tools.jackson`，例 `tools.jackson.databind.ObjectMapper`；`@JsonProperty` 等註解仍在 `com.fasterxml.jackson.annotation`）。

**請先讀完本檔再動手。** 三條鐵律：

1. `com.mpx.common` 底下（含 `src/test/java/com/mpx/common`）任何檔案都**不可修改**；`com.mpx.Application` 也不要改。
2. 業務程式一律放在 `com.mpx.im_acct_insp` 底下（子套件自訂，例 `controller`、`service`、`dao`、`model`）。
3. SQL 一律用 `:name` 具名參數綁定，**不可**用字串拼接組 SQL。

若專案根目錄有 `CLAUDE.md`，請一併遵守其中的規則。

前後端建議用不同專案名（例 `xxx_api`／`xxx_web`），避免 log 目錄撞在一起。

## 1. 專案結構

| 套件／資料夾 | 用途 | 可否修改 |
|---|---|---|
| `com.mpx.Application` | 啟動類別；掃描根 `com.mpx`；排除 `DataSourceAutoConfiguration`；載入 `config/database.properties` | 不可 |
| `com.mpx.common.db` | 共用 DB 連線元件（`DbClient`、`DbConnectionManager`、`DbConnectApiClient` 等 6 個類別） | 不可 |
| `com.mpx.im_acct_insp` | 業務程式（帳號權限管理／機房巡檢，spec v2.0；概要見第 10 節） | 自由新增 |
| `src/main/resources/application.properties` | 專案名、port | 可改值 |
| `src/main/resources/config/*.properties` | 連線資訊 API、DB 別名、自訂設定（見第 4 節） | 可改值 |
| `src/main/resources/logback-spring.xml` | log 設定（見第 6 節） | 一般不需改 |
| `src/test/java/com/mpx/common/db` | common.db 的 42 個單元測試 | 不可 |
| `src/test/java/com/mpx/im_acct_insp` | 業務程式的測試 | 自由新增 |

## 2. 新增一支 API 的標準寫法

分層：Controller（收 HTTP、回 JSON）→ Service（業務邏輯）→ DAO（SQL，注入 `DbClient`）→ model（POJO）。

- Controller：`@RestController`，路徑以 `/api/<資源>` 開頭，直接回傳物件或 `List`，由 Spring 轉成 JSON。
- Service：`@Service`，以建構子注入 DAO。
- DAO：`@Repository`，以建構子注入 `DbClient`；DB 別名用 `@Value("${<key>}")` 從 `config/database.properties` 讀，不要寫死在程式。
- `DbClient` 只有兩個方法：
  - `<T> List<T> query(String dbName, String sql, Map<String,Object> params, Class<T> modelClass)`：查無資料回空 List（不會回 null）。
  - `int update(String dbName, String sql, Map<String,Object> params)`：INSERT／UPDATE／DELETE，回影響筆數。
  - `params` 可傳 null；`dbName` 前後空白會被去掉、區分大小寫，null 或空白丟 `DbConnectException`。

`config/database.properties` 先加一行別名（key 自訂，value 是連線資訊 API 認得的名稱）：

```
nova.db.alias=<alias>
```

最小範例（四個檔，可直接編譯）：

```java
// src/main/java/com/mpx/im_acct_insp/model/Item.java
package com.mpx.im_acct_insp.model;

import java.math.BigDecimal;

public class Item {
	private String itemNo;
	private String itemName;
	private BigDecimal price;

	public String getItemNo() { return itemNo; }
	public void setItemNo(String itemNo) { this.itemNo = itemNo; }
	public String getItemName() { return itemName; }
	public void setItemName(String itemName) { this.itemName = itemName; }
	public BigDecimal getPrice() { return price; }
	public void setPrice(BigDecimal price) { this.price = price; }
}
```

```java
// src/main/java/com/mpx/im_acct_insp/dao/ItemDao.java
package com.mpx.im_acct_insp.dao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.mpx.common.db.DbClient;
import com.mpx.im_acct_insp.model.Item;

@Repository
public class ItemDao {

	private final DbClient dbClient;

	@Value("${nova.db.alias}")
	private String novaDb;

	public ItemDao(DbClient dbClient) {
		this.dbClient = dbClient;
	}

	public List<Item> findByItemNo(String itemNo) {
		String sql = "SELECT ITEM_NO, ITEM_NAME, PRICE FROM ITEM WHERE ITEM_NO = :itemNo";
		Map<String, Object> params = new HashMap<>();
		params.put("itemNo", itemNo);
		return dbClient.query(novaDb, sql, params, Item.class);
	}
}
```

```java
// src/main/java/com/mpx/im_acct_insp/service/ItemService.java
package com.mpx.im_acct_insp.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.mpx.im_acct_insp.dao.ItemDao;
import com.mpx.im_acct_insp.model.Item;

@Service
public class ItemService {

	private final ItemDao itemDao;

	public ItemService(ItemDao itemDao) {
		this.itemDao = itemDao;
	}

	public List<Item> findByItemNo(String itemNo) {
		return itemDao.findByItemNo(itemNo);
	}
}
```

```java
// src/main/java/com/mpx/im_acct_insp/controller/ItemController.java
package com.mpx.im_acct_insp.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mpx.im_acct_insp.model.Item;
import com.mpx.im_acct_insp.service.ItemService;

@RestController
@RequestMapping("/api/items")
public class ItemController {

	private final ItemService itemService;

	public ItemController(ItemService itemService) {
		this.itemService = itemService;
	}

	@GetMapping("/{itemNo}")
	public List<Item> findByItemNo(@PathVariable String itemNo) {
		return itemService.findByItemNo(itemNo);
	}
}
```

## 3. SQL 與 model 規則

- 值一律用 `:參數名` 佔位、放進 `params`（key 與參數名相同，區分大小寫）；IN 條件直接傳 `List`：`WHERE ITEM_NO IN (:itemNos)`。
- 表名、欄位名這種不能綁定的部分，只能從程式內固定的白名單挑選，不可取自外部輸入。
- 查詢結果以 Spring `BeanPropertyRowMapper` 對應：SQL 欄位別名用**大寫底線**（`ITEM_NO`）、**不加雙引號**，對應 model 的 camelCase 屬性 `itemNo`。MSSQL 同樣用大寫底線別名，不加方括號或雙引號。
- 欄位對不上**不會報錯**，該屬性就是 null；結果怪怪的先檢查別名拼字。
- NUMBER 帶小數用 `BigDecimal`；primitive 型別（`int`、`long`…）遇 DB 的 NULL 會丟例外，改用包裝型別（`Integer`、`Long`…）。
- model 需有**無參數建構子**與每個屬性的 **setter**。

## 4. properties 分工

| 檔案 | 內容 | 誰讀 |
|---|---|---|
| `application.properties` | `spring.application.name`（專案名 `infra_manager`）、`server.port`、`server.servlet.context-path`（固定 `/infra_manager`） | Spring |
| `config/host.properties` | 連線資訊 API 四個 key：`rt-api.domain`、`db.connect.api.port`、`db.connect.api.path`，以及由前三個以 `${}` 組成的 `db.connect.api.domain.path` | common.db（只讀 `db.connect.api.domain.path`） |
| `config/database.properties` | DB 別名（key 自訂） | `Application` 載入，業務程式以 `@Value` 讀 |
| `config/environment.properties` | 各專案自訂設定（選用，可能不存在） | 業務程式自行以 `@PropertySource("classpath:config/environment.properties")` 載入 |

- 以上 `*.properties` 都是**真檔，不進 git**（已被 `.gitignore` 排除），repo 內也沒有範本，向專案負責人取得。
- 新增任何 key 時，**同步寫進根目錄 `PRD.md`「環境設定」表**（只寫 key 與用途，不寫真實位址或帳密），並通知專案負責人更新他手上的設定檔。
- 本機沒有真檔時的取得與放置步驟見根目錄 `SETUP.md`。
- `host.properties` 四個 key 都要存在（值可空），不存在時啟動失敗；API 位址為空時仍可啟動，第一次呼叫 `DbClient` 才丟例外。

## 5. 限制與假設

- 支援 **Oracle** 與 **MSSQL**（由連線資訊 API 回的 `dbType` 決定）；SQL 方言依目標 DB 自行撰寫（例：分頁、日期函式、`TOP`／`FETCH FIRST`），common.db 不做方言轉換。
- `update` 本身**不開交易**：呼叫端沒開交易時每句 autocommit；在 `TransactionTemplate` 內呼叫則加入該交易，**僅限 `itflow.db.alias` 別名**（`LazyDataSourceTransactionManager` 只綁該別名的連線池，見該類別檔頭）；其他別名的寫入仍每句 autocommit、不隨交易回滾。沒有 `@Transactional`。
- 連線池建好後不會重新取連線資訊；API 端改了帳密或位址，**需重啟程式**。
- 連線資訊 API 走 http、不帶驗證 header。
- 取得連線階段失敗（別名空白、API 位址未設、API 失敗、回應缺欄位、dbType 非 ORACLE／MSSQL、建池失敗）丟 `com.mpx.common.db.DbConnectException`，
  訊息格式 `取得 DB 連線失敗 alias=<alias>: <原因>`，原始例外掛在 cause；失敗不快取，下次呼叫會重試。
- SQL 執行期錯誤（語法錯、ORA- 權限錯等）原樣丟 Spring `DataAccessException`，不包裝。
- log 不得印出密碼、完整 jdbcUrl、帳號、SQL 參數值。

## 6. log

- 落點：`/home/tomcat/log/infra_manager/infra_manager.yyyy-MM-dd.log`，按日切檔保留 30 天；Windows 上對應到**啟動時工作目錄所在磁碟機**（例 `D:\home\tomcat\log\infra_manager\`）。
- 業務程式的 log 與 common.db 的 log（logger `devTemplate.db`）寫進**同一個檔**，console 也會輸出。
- 業務 logger 建議用類別名：`private static final Logger log = LoggerFactory.getLogger(ItemService.class);`（slf4j）。
- `devTemplate.db` 這個 logger 名稱不要改。

## 7. 建置與執行

建置與啟動步驟見根目錄 `SETUP.md`。

- 既有 42 個 common.db 單元測試不可修改、不可刪除；`./mvnw clean package` 必須全過。
- 業務程式的測試放 `src/test/java/com/mpx/im_acct_insp/`。單元測試不要依賴真 DB 或真 API。

## 8. 給 AI 的檢查清單

每次改動前後確認：

- [ ] 已讀完本檔。
- [ ] 改動的檔案都不在 `com.mpx.common` 底下，也沒改 `com.mpx.Application`。
- [ ] 業務程式都在 `com.mpx.im_acct_insp` 底下。
- [ ] SQL 全部用 `:name` 具名參數，沒有字串拼接。
- [ ] 新增的 properties key 已同步寫進 `PRD.md`「環境設定」表，並已通知專案負責人。
- [ ] 沒有把 `*.properties` 真檔、帳密或真實位址加進 git。
- [ ] `./mvnw clean package` 通過，42 個 common.db 測試全過。

## 9. 密碼雜湊工具（spec v2.0 §9.2）

`IM_ACCT_USER.PWD_HASH` 由工具類別 `com.mpx.im_acct_insp.tools.PwdHashTool` 產生（雜湊本體為 `com.mpx.im_acct_insp.common.PasswordHasher`：
PBKDF2WithHmacSHA256、salt 16 bytes、迭代 310000，輸出 `PBKDF2$310000$base64(salt)$base64(hash)`）。

執行步驟見根目錄 `SETUP.md`。

## 10. 業務程式概要（spec v2.0，2026-10-07）

| 主題 | 位置 | 說明 |
|---|---|---|
| 取身分 | `common.IdentityProvider`（介面）、`common.IdentityArgumentResolver`、實作 `service.TokenIdentityProvider` | Controller 方法宣告 `Identity` 參數即取得目前身分（讀 `Authorization: Bearer`）；除 `POST /auth/login` 外每支 API 都要宣告（`SqlConstantScanTest` 檢查）。日後換共用登入只換 `IdentityProvider` 實作 |
| 權限 | `service.PermissionService` | `can(身分, 權限碼)` 與資料範圍判斷集中處；關卡權限取自 `IM_ACCT_APPR_STAGE.PERM_ID`；程式不判斷角色名稱 |
| 交易 | `common.AppConfig#transactionTemplate`、`common.LazyDataSourceTransactionManager` | `TransactionTemplate` 與 `DbClient` 共用 common.db 的連線池（DataSource 延遲到第一次開交易才取）；不改 `com.mpx.common` |
| 時間 | `common.AppConfig#clock`（Asia/Taipei） | 業務時間一律由 Clock 產生後以參數綁定，SQL 不用 `SYSDATE` |
| 附件 | `service.AttachmentService` | 根目錄 `infra.manager.attach.dir`（`config/environment.properties`，key 見根目錄 `PRD.md`「環境設定」）；必須是絕對路徑，**Windows 請用 `/` 或 `\\`**（properties 的單一 `\` 是跳脫字元，`D:\home\attach` 會變成相對路徑 `D:homeattach`），例 `D:/home/infra_manager/attach`；未設定或不是絕對路徑時啟動即失敗並記 ERROR（變更單 #3） |
| 排程 | `service.JobService`（`@Scheduled`） | J01、J02 的 cron、開關、`allow-base-date` 見 `config/environment.properties`（key 見根目錄 `PRD.md`「環境設定」）；手動 `POST /api/v1/jobs/{code}/run` |
| 寄信 | `service.MailSender`（介面）、`service.SmtpMailSender`、`service.LogMailSender`、`common.MailConfig`、`service.MailAllowList`、`service.MailHtml` | 本文為 HTML（`MailHtml` 組版面、值一律跳脫，信末連結＝`infra.manager.mail.site-url`＋前端頁面，留空不放）；`spring.mail.host`（`application.properties`）與 `infra.manager.mail.from` 都有值時 `MailConfig` 選 SMTP 實際寄出，否則只寫 log；只寄給寄送名單 `infra.manager.mail.allow-list` 內的工號（留空誰都不寄，`send-to-all=true` 忽略名單），名單外記 SKIP「不在寄送名單」 |
| 通知收件人 | `service.NotificationService`、`dao.PermDao#findUsersInRoles` | 角色收件人固定為程式常數（`AcctConst.MAIL_ROLE_EXPIRY`＝GOV：到期前 7 天、轉已逾期、每週彙總；`AcctConst.MAIL_ROLE_JOB_FAIL`＝SYS_ADMIN：排程失敗），**不放設定檔**（變更單 #2）；成員於寄信當下查 `IM_ACCT_ROLE_USER`（STATUS＝1）＋`IM_ACCT_USER`（STATUS＝1），無 Email 記 SKIP |

- 新增的設定 key 全在 `config/environment.properties`（選用檔，缺 key 用程式預設；只有附件根目錄無預設，未設定時啟動失敗）；例外是 SMTP 連線用 Spring 固定 key `spring.mail.*`，放 `application.properties`。
- `spring.servlet.multipart.max-file-size`／`max-request-size` 未設定時程式預設 10MB／11MB（`AppConfig`）。
