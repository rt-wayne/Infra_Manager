# infra_manager_web 專案說明（給接手開發的 AI 助理）

本專案由 template_jdk25_vue_3_5/frontend 範本產生於 2026-10-07

這是一個前端 Web 專案，由公司開發範本產生，分成兩包：Spring Boot 4.1／Java 25 殼 jar（`infra_manager_web\`）與 Vue 3.5＋TypeScript 前端（`infra_manager_web_frontend\`）。
殼 jar 服務前端 build 出來的頁面；頁面的 API 呼叫由殼 jar 裡各功能自己寫的 Controller 以 `RestTemplate` 呼叫後端 API，並把回應原樣回傳。本專案**不連 DB、沒有 SQL**。

## 1. 開場與鐵律

**請先讀完本檔再動手。**

1. `com.mpx.common.web` 底下（目前只有 `RestTemplateConfig`，含其測試）任何檔案都**不可修改**；`com.mpx.Application` 也不要改。
2. 頁面只呼叫**自己殼 jar** 的 `/infra_manager_web/api/v1/...`，不可直接打後端 API 或其他網站。
3. 殼 jar 的 Controller **只做呼叫後端與回傳**：**不解析 payload**、**不開 CrossOrigin**；業務邏輯放後端 API。
4. 後端 URL **只從 `config\host.properties` 的組合 key `infra.manager.domain.path` 取得**，不得寫死在程式；換環境只改 host.properties（第 6 節）。
5. 這兩包都沒有 SQL、沒有 DB 設定；要查資料就在後端 API 加端點，再由這裡的 Controller 呼叫。
6. **範本不含任何範例程式，照第 3 節片段新增檔案即可，不需刪檔**；佔位檔（`package-info.java`、`.gitkeep`）留著不影響建置。

若專案根目錄有 `CLAUDE.md`，請一併遵守其中的規則。

前後端建議用不同專案名（例 `xxx_api`／`xxx_web`），避免 log 目錄撞在一起。
剛產出時首頁是空白的（`router/index.ts` 沒有任何 route），屬正常；照第 3 節加上第一個畫面後才有內容。

## 2. 兩包結構

| 位置 | 用途 | 可否修改 |
|---|---|---|
| `infra_manager_web\src\main\java\com\mpx\Application.java` | 殼 jar 啟動類別（掃描根 `com.mpx`） | 不可 |
| `infra_manager_web\src\main\java\com\mpx\common\web\` | `RestTemplateConfig`：殼 jar 唯一共用元件，提供 `RestTemplate` bean（connect 5 秒、read 120 秒），Controller 注入使用 | 不可 |
| `infra_manager_web\src\main\java\com\mpx\infra_manager_web\` | 呼叫後端的 Controller 放這裡（子套件自訂，例 `controller`）；目前只有佔位用的 `package-info.java` | 自由新增 |
| `infra_manager_web\src\main\resources\` | `application.properties`、`config\host.properties`、`logback-spring.xml` | 可改值 |
| `infra_manager_web\src\frontend\` | 前端 build 產物（由 `npm run build` 寫入，不進 git）；平時只有 `.gitkeep` | 不要手改 |
| `infra_manager_web_frontend\src\types\` | API 請求／回應型別；目前只有佔位用的 `.gitkeep` | 新增功能時新增 |
| `infra_manager_web_frontend\src\api\` | `http.ts`（axios 實例，baseURL `/infra_manager_web/api/v1`）；各功能的 API 函式新增在這裡 | 新增功能時新增 |
| `infra_manager_web_frontend\src\views\` | 畫面；目前只有佔位用的 `.gitkeep` | 自由新增 |
| `infra_manager_web_frontend\src\router\index.ts` | 路由（hash 模式），`routes` 目前是空陣列 | 新增畫面時加一筆 |
| `infra_manager_web_frontend\src\composables\useToast.ts` | toast 通知（查詢失敗提示用） | 可沿用 |
| `infra_manager_web_frontend\src\assets\main.css` | 色票與基礎字級 | 謹慎修改 |
| `infra_manager_web_frontend\tests\` | Vitest 測試；目前有 8 個 `*.spec.ts` 與共用的 `helpers.ts` | 自由新增 |

佔位檔只是讓空資料夾能進 git，新增檔案後留著即可，不必刪。

## 3. 新增一支功能的標準寫法

**範本不含任何範例程式，照本節片段新增檔案即可，不需刪檔。**

流程：殼 jar Controller → `types/*.ts` 型別 → `api/*.ts` 函式 → 畫面 `views/*.vue` → `router/index.ts` 註冊 → `tests/*.spec.ts` 測試。
下面以「商品查詢」為例，每段都是完整檔案（route 為改一行），照抄後即可編譯、build、測試通過；換成自己的功能時改名稱與欄位即可。

### 3.1 殼 jar Controller

新增 `infra_manager_web\src\main\java\com\mpx\infra_manager_web\controller\ItemController.java`：

```java
package com.mpx.infra_manager_web.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

/**
 * 頁面打 /infra_manager_web/api/v1/item/...，由這裡呼叫後端 API 並把回應原樣回傳。
 * 不解析 payload：請求與回應本文都以 byte[] 接，原樣轉送，不重新序列化；後端欄位變動時本檔不需改。
 * 不開 CrossOrigin：頁面與殼 jar 同源，無跨域需求。
 */
@PropertySource("classpath:config/host.properties")
@RestController
@RequestMapping("/api/v1/item")
public class ItemController {

	private static final Logger log = LoggerFactory.getLogger(ItemController.class);

	/** com.mpx.common.web.RestTemplateConfig 提供（connect 5 秒、read 120 秒） */
	private final RestTemplate restTemplate;

	/** 後端 API 根位址：host.properties 的組合 key（domain＋port＋path），不得寫死在程式 */
	@Value("${infra.manager.domain.path}")
	private String backendApi;

	public ItemController(RestTemplate restTemplate) {
		this.restTemplate = restTemplate;
	}

	/** 查詢：呼叫後端 <infra.manager.domain.path>/item/search */
	@PostMapping("/search")
	public ResponseEntity<byte[]> search(@RequestBody byte[] body) {
		String path = "/item/search";
		log.info("POST {}", path);
		try {
			// 請求本文原樣送出；byte[] 預設會被當成 octet-stream，所以明確標 JSON
			HttpHeaders reqHeaders = new HttpHeaders();
			reqHeaders.setContentType(MediaType.APPLICATION_JSON);
			return passThrough(restTemplate.exchange(backendApi + path, HttpMethod.POST,
					new HttpEntity<>(body, reqHeaders), byte[].class));
		} catch (RuntimeException e) {
			// 後端回 4xx／5xx 或連不上時 RestTemplate 會丟例外：只記方法、路徑與例外類別（不記 payload），
			// 原樣往上拋，由 Spring 預設處理成 500；前端 catch 後統一 toast
			log.error("POST {} failed: {}", path, e.getClass().getName());
			throw e;
		}
	}

	/** 清單：呼叫後端 <infra.manager.domain.path>/item/list */
	@GetMapping("/list")
	public ResponseEntity<byte[]> list() {
		String path = "/item/list";
		log.info("GET {}", path);
		try {
			return passThrough(restTemplate.getForEntity(backendApi + path, byte[].class));
		} catch (RuntimeException e) {
			log.error("GET {} failed: {}", path, e.getClass().getName());
			throw e;
		}
	}

	/**
	 * 後端回應原樣回頁面：狀態碼、本文 byte 照抄；header 照抄但去掉長度／連線類，由殼 jar 依實際本文重算。
	 * 照抄 Content-Length 會在長度對不上時讓呼叫端一直等到逾時，不可省略這一步。
	 */
	private static ResponseEntity<byte[]> passThrough(ResponseEntity<byte[]> resp) {
		HttpHeaders h = HttpHeaders.copyOf(resp.getHeaders());
		h.remove(HttpHeaders.CONTENT_LENGTH);
		h.remove(HttpHeaders.TRANSFER_ENCODING);
		h.remove(HttpHeaders.CONNECTION);
		return ResponseEntity.status(resp.getStatusCode()).headers(h).body(resp.getBody());
	}
}
```

- 後端路徑（`/item/search`、`/item/list`）依實際後端 API 填，以 `/` 開頭，接在組合 key 後面（組合規則見第 6 節）。
- 本文一律以 `byte[]` 接（`@RequestBody byte[]`、`exchange(..., byte[].class)`／`getForEntity(..., byte[].class)`），不要改成 `Object.class` 或 DTO：那樣會先解析再重新序列化，本文會變（例：`10.50` 變 `10.5`、美化 JSON 被壓縮）。
- 後端成功（2xx）時，狀態碼、本文、header（除 `Content-Length`、`Transfer-Encoding`、`Connection`）原樣回頁面，`Set-Cookie` 等自訂 header 也會帶回；後端非 2xx 或連不上時 RestTemplate 丟例外 → 500。
- 後端錯誤或連不上時 Controller 記 log 後 rethrow，殼 jar 回 Spring 預設的 500；不要另外 catch 後改回 200 或回假資料。
- Controller 自己的 log 只記方法、路徑與例外類別，不記 payload；但 rethrow 後 Spring 預設的錯誤 log 仍會含完整後端 URL 與堆疊，後端回 4xx／5xx 時還含後端回應內容。

### 3.2 型別

新增 `infra_manager_web_frontend\src\types\item.ts`：

```ts
/** POST /item/search 的請求本文 */
export interface ItemSearchRequest {
  itemNo: string
}

/** /item/search、/item/list 回傳陣列中的一列；欄位以後端 API 實際回傳為準 */
export interface Item {
  itemNo: string
  itemName: string
}
```

### 3.3 前端 API

新增 `infra_manager_web_frontend\src\api\item.ts`：

```ts
import http from './http'
import type { Item, ItemSearchRequest } from '../types/item'

/** 查詢：POST /infra_manager_web/api/v1/item/search；失敗時 Promise reject，由畫面提示 */
export function searchItems(payload: ItemSearchRequest): Promise<Item[]> {
  return http.post<Item[]>('/item/search', payload).then(r => r.data)
}

/** 清單：GET /infra_manager_web/api/v1/item/list */
export function listItems(): Promise<Item[]> {
  return http.get<Item[]>('/item/list').then(r => r.data)
}
```

### 3.4 畫面

新增 `infra_manager_web_frontend\src\views\ItemView.vue`（搜尋列＋表格＋每頁 20 筆分頁＋空狀態＋toast）：

```vue
<template>
  <main class="page">
    <form class="search-bar" @submit.prevent="doSearch">
      <div class="fld">
        <label for="itemNo">商品編號</label>
        <input id="itemNo" v-model="itemNo" type="text" autocomplete="off" />
      </div>
      <button class="go" type="submit" :disabled="loading">{{ loading ? '查詢中…' : '搜尋' }}</button>
    </form>

    <template v-if="searched">
      <div v-if="rows.length > 0">
        <p class="res-head">共 {{ rows.length }} 筆，第 {{ page }} / {{ totalPages }} 頁</p>
        <table class="tbl">
          <thead>
            <tr><th>商品編號</th><th>商品名稱</th></tr>
          </thead>
          <tbody>
            <tr v-for="(r, i) in pageRows" :key="i">
              <td>{{ r.itemNo }}</td>
              <td>{{ r.itemName }}</td>
            </tr>
          </tbody>
        </table>
        <div v-if="totalPages > 1" class="pager">
          <button type="button" :disabled="page <= 1" @click="page--">上一頁</button>
          <span>{{ page }} / {{ totalPages }}</span>
          <button type="button" :disabled="page >= totalPages" @click="page++">下一頁</button>
        </div>
      </div>
      <div v-else class="empty">查無資料，請換個條件再試一次</div>
    </template>
    <p v-else class="hint">輸入商品編號後按「搜尋」</p>

    <div class="toasts">
      <div v-for="t in toasts" :key="t.id" class="toast" :class="t.cls">{{ t.msg }}</div>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { searchItems } from '../api/item'
import { useToast } from '../composables/useToast'
import type { Item } from '../types/item'

const PAGE_SIZE = 20

const { toasts, toast } = useToast()
const itemNo = ref('')
const rows = ref<Item[]>([])
const page = ref(1)
const loading = ref(false)
const searched = ref(false)

const totalPages = computed(() => Math.max(1, Math.ceil(rows.value.length / PAGE_SIZE)))
const pageRows = computed(() => rows.value.slice((page.value - 1) * PAGE_SIZE, page.value * PAGE_SIZE))

async function doSearch(): Promise<void> {
  const no = itemNo.value.trim()
  // 查詢前擋空輸入：不打 API，只提示
  if (!no) {
    toast('請輸入商品編號', 'amber')
    return
  }
  loading.value = true
  try {
    const data = await searchItems({ itemNo: no })
    if (!Array.isArray(data)) {
      throw new Error('unexpected response')
    }
    rows.value = data
    page.value = 1
    searched.value = true
  } catch {
    // 失敗只出 toast、保留原畫面；不得顯示成「查無資料」
    toast('查詢失敗，請稍後再試', 'amber')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.page { width: 100%; padding: 20px; }
.search-bar { display: flex; gap: 12px; align-items: flex-end; flex-wrap: wrap; background: #fff;
  border: 1px solid var(--line); border-radius: 10px; padding: 12px 16px; }
.fld { display: flex; flex-direction: column; gap: 4px; }
.fld input { border: 1px solid var(--line); border-radius: 7px; padding: 10px 14px; min-height: 48px;
  width: 340px; max-width: 100%; }
.go { background: var(--teal); color: #fff; border: none; border-radius: 7px; padding: 12px 24px;
  font-size: 19px; min-width: 140px; cursor: pointer; }
.go:disabled { opacity: .55; cursor: default; }
.hint, .empty { color: var(--gray); margin-top: 16px; }
.empty { text-align: center; padding: 48px 0; }
.res-head { font-weight: 700; margin: 16px 0 8px; }
.tbl { width: 100%; border-collapse: collapse; background: #fff; border: 1px solid var(--line); }
.tbl th { background: #eef2f7; padding: 12px 14px; text-align: left; }
.tbl td { padding: 12px 14px; border-top: 1px solid var(--line); overflow-wrap: anywhere; }
.pager { display: flex; justify-content: center; align-items: center; gap: 14px; margin-top: 16px; }
.pager button { min-width: 150px; font-size: 19px; }
.toasts { position: fixed; top: 18px; right: 18px; display: flex; flex-direction: column; gap: 10px; z-index: 50; }
.toast { background: var(--navy); color: #fff; padding: 16px 22px; border-radius: 9px; font-size: 19px; max-width: 460px; }
.toast.amber { background: #92400e; }
.toast.red { background: var(--red); }
</style>
```

- toast 顯示區（`<div class="toasts">`）每個畫面都要放；`useToast` 的 `cls` 可用 `teal`／`amber`／`red`。
- 查詢失敗只出 toast，畫面維持原狀；「查無資料」只在後端成功回傳空陣列時顯示。

### 3.5 註冊 route

把 `infra_manager_web_frontend\src\router\index.ts` 的 `routes` 由空陣列改成下面這一行（檔案其餘不動）：

```ts
const routes: RouteRecordRaw[] = [{ path: '/', component: () => import('../views/ItemView.vue') }]
```

- `path: '/'` 是首頁；第二個以後的畫面用其他路徑（例 `'/item'`），以 `#/item` 開啟。

### 3.6 Vitest 測試

新增 `infra_manager_web_frontend\tests\ItemView.spec.ts`（以 `vi.mock` 替換 API，不打真後端）：

```ts
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ItemView from '../src/views/ItemView.vue'
import { searchItems } from '../src/api/item'

vi.mock('../src/api/item', () => ({
  searchItems: vi.fn(),
  listItems: vi.fn()
}))

const searchMock = vi.mocked(searchItems)

describe('ItemView', () => {
  beforeEach(() => {
    // toast 清單是模組層共用狀態：只假 setTimeout，每案結束把 3 秒自動移除跑完，避免跨案殘留
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    searchMock.mockReset()
  })

  afterEach(() => {
    vi.runAllTimers()
    vi.useRealTimers()
  })

  it('空輸入時不送查詢，只提示', async () => {
    const wrapper = mount(ItemView)

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(searchMock).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('請輸入商品編號')
    wrapper.unmount()
  })

  it('查詢失敗時顯示 toast，不顯示成查無資料', async () => {
    searchMock.mockRejectedValue(new Error('network'))
    const wrapper = mount(ItemView)

    await wrapper.find('#itemNo').setValue('A001')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(searchMock).toHaveBeenCalledWith({ itemNo: 'A001' })
    expect(wrapper.text()).toContain('查詢失敗，請稍後再試')
    expect(wrapper.find('.empty').exists()).toBe(false)
    wrapper.unmount()
  })
})
```

## 4. TypeScript 規則

- 所有 `.vue` 用 `<script setup lang="ts">`；其他原始碼都是 `.ts`。`tsconfig` 開 strict。
- **不得用 `any`**；真的必要時在同一行註明原因（例 `// any：第三方套件無型別`）。後端欄位不確定時用 `unknown` 再自行判斷。
- API 的請求與回傳型別一律定義在 `src\types\`，`api/*.ts` 與畫面都 import 同一份型別。
- 型別錯誤會讓 `npm run build` 失敗（build 先跑 `vue-tsc --build` 才跑 `vite build`）；也可單獨執行 `npm run type-check`。

## 5. 版面規範

- 使用者多為門市人員、螢幕小：內文 18～20px（`body` 19px）、輸入框 20px、標籤 17px、按鈕最小高 48px。
- 1024 寬視窗**不可出現橫向捲軸**；表格欄寬要能在 1024 下放得下。
- 清單每頁 **20 筆**分頁。
- 頁面寬度由各專案決定（範本不設 `max-width`）；`main.css` 的色票（`--teal`、`--line` 等）請沿用。

## 6. properties 分工

| 檔案 | 內容 |
|---|---|
| `infra_manager_web\src\main\resources\application.properties` | `spring.application.name`、`server.port`、`server.servlet.context-path`（＝`/infra_manager_web`，前端 `base`、`http.ts` 的 `baseURL` 都以此為前綴） |
| `infra_manager_web\src\main\resources\config\host.properties` | 後端 API 位址（下表四個 key）；Controller 以 `@PropertySource("classpath:config/host.properties")` 載入 |

### host.properties 的四個 key

| key | 要填什麼 | 例 |
|---|---|---|
| `backend.api.domain` | 協定＋主機 | `http://<api-host>` |
| `backend.api.port` | 冒號＋port；走協定預設 port 時留空 | `:<port>` |
| `infra.manager.path` | 後端 API 的根路徑（含後端的 context-path），以 `/` 開頭、結尾不加 `/` | `/<backend-context-path>/api/v1` |
| `infra.manager.domain.path` | 組合 key，值固定為 `${backend.api.domain}${backend.api.port}${infra.manager.path}`，**不要改** | 組合結果 `http://<api-host>:<port>/<backend-context-path>/api/v1` |

- 組合規則：三段**直接串接**，不會自動補 `:` 或 `/`，所以 port 要含冒號、path 要以 `/` 開頭；Controller 再接上以 `/` 開頭的功能路徑（例 `/item/search`）。
- **程式只讀組合 key** `infra.manager.domain.path`（`@Value("${infra.manager.domain.path}")`），不要在程式裡讀前三個 key 自己拼，也不要把位址寫死。
- 四個 key 名稱固定、都要保留（值可空）：被 `${}` 引用的 key 不存在時殼 jar 會啟動失敗；值留空時能啟動，呼叫 API 時才失敗（回 500）。
- 本機、UAT、正式環境**只改這個檔的值、不改程式**；檔案打包在 jar 內，改完要重新建置。

### 真檔不進 git

- 以上都是**真檔，不進 git**，repo 內也沒有範本，向專案負責人取得。新增 key 時同步寫進根目錄 `PRD.md`「環境設定」表（不寫真實位址），並通知專案負責人。
- 本機沒有真檔時的取得與放置步驟見根目錄 `SETUP.md`。

### cookie 與 header

`RestTemplate` 是殼 jar 另開的一條連線，**瀏覽器送來的 Cookie／Authorization 等 header 不會自動帶給後端**；照第 3.1 節回傳時後端回的 header（含 `Set-Cookie`，長度類除外）會回到瀏覽器，但其 Path／Domain 是後端設的，不一定適用殼 jar 的網址。
是否轉遞 Cookie／Authorization、是否回傳或改寫 `Set-Cookie`，由開發者依後端的認證方式自行決定、需要時自行處理；範本不處理，也不附實作。

## 7. 建置與執行

建置與啟動步驟見根目錄 `SETUP.md`。

## 8. Vite 版本說明

本範本用 **Vite 8**（打包器為 Rolldown，取代 Vite 7 的 Rollup＋esbuild 組合）。對使用者有影響的部分：

- `vite.config.ts` 的一般設定（`base`、`build.outDir`、`server.proxy`、plugins）寫法與 Vite 7 相同，本範本未用到需改寫的項目。
- 需要自訂打包細節時，請查 Vite 8 文件的 `build.rolldownOptions`（Vite 7 為 `build.rollupOptions`），不要照抄舊文章的 Rollup／esbuild 專屬設定。
- Node 需 20.19 以上或 22.12 以上；本範本要求 Node 24。

## 9. 限制

- 前端 axios timeout（120 秒，`http.ts`）與殼 jar 的 read timeout（120 秒，`RestTemplateConfig`）一致，改一邊就要改另一邊。
- 後端錯誤或連不上時，Controller 記 log 後 rethrow，殼 jar 回 Spring 預設的 500；前端一律提示「查詢失敗，請稍後再試」，**不得把失敗顯示成「查無資料」**。
- 殼 jar 不會自動把瀏覽器的 cookie／header 帶給後端（第 6 節「cookie 與 header」）。
- log 落點 `/home/tomcat/log/infra_manager_web/infra_manager_web.yyyy-MM-dd.log`（Windows 依啟動磁碟機）。Controller 自己的 log 只記方法、路徑與例外類別，不記 payload；rethrow 後 Spring 預設的錯誤 log 仍含完整後端 URL 與堆疊，後端回 4xx／5xx 時含後端回應內容。
- 不內建登入、選店或與外框頁面的整合；需要時由專案自行加。
- 真檔（`*.properties`）、`node_modules`、`coverage`、build 產物都不進 git。

## 10. 給 AI 的檢查清單

- [ ] 已讀完本檔。
- [ ] 新功能都是新增檔案（照第 3 節片段），沒有刪除範本既有檔與佔位檔。
- [ ] 沒有修改 `com.mpx.common.web`（`RestTemplateConfig`）與 `com.mpx.Application`。
- [ ] 新 Controller 放在 `com.mpx.infra_manager_web` 底下，注入 `RestTemplate` 呼叫後端、本文以 `byte[]` 接並原樣回傳、回傳前去掉 `Content-Length`／`Transfer-Encoding`／`Connection`；不解析 payload、沒有 CrossOrigin。
- [ ] 後端位址只用 `@Value("${infra.manager.domain.path}")` 取得，程式裡沒有寫死的主機、port 或路徑前綴。
- [ ] 失敗時記 log（路徑、例外類別，不含 payload）後 rethrow，沒有吞掉例外。
- [ ] 若後端需要登入資訊，已自行決定 Cookie／Authorization 的處理方式（範本不處理）。
- [ ] API 型別放在 `src\types\`，前端只透過 `api/http.ts` 打 `/infra_manager_web/api/v1`。
- [ ] 沒有 `any`（或已註明原因）；`npm run type-check` 通過。
- [ ] 查詢失敗走 toast，不顯示成「查無資料」；清單每頁 20 筆；1024 寬無橫向捲軸。
- [ ] 新增的 properties key 已同步寫進 `PRD.md`「環境設定」表並通知專案負責人；沒有把真檔或真實位址加進 git。
- [ ] `npm test`、`npm run build` 與 `./mvnw clean package` 都通過。
