# infra_manager_web 專案說明（給接手開發的 AI 助理）

本專案由 web_template_3.5 範本產生於 2026-10-05

這是一個前端 Web 專案，由公司開發範本產生，分成兩包：Spring Boot 4.1／Java 25 殼 jar（`infra_manager_web\`）與 Vue 3.5＋TypeScript 前端（`infra_manager_web_frontend\`）。
殼 jar 服務前端 build 出來的頁面，並把頁面的 API 呼叫原樣轉發給後端 API；本專案**不連 DB、沒有 SQL**。

## 1. 開場與鐵律

**請先讀完本檔再動手。**

1. `com.mpx.common.web` 底下（含其測試）任何檔案都**不可修改**；`com.mpx.Application` 也不要改。
2. 頁面只呼叫**自己殼 jar** 的 `/infra_manager_web/api/v1/...`，不可直接打後端 API 或其他網站。
3. 轉發層（Controller、`ApiForwarder`）**不解析 payload**、**不開 CrossOrigin**；業務邏輯放後端 API。
4. 這兩包都沒有 SQL、沒有 DB 設定；要查資料就在後端 API 加端點，再由這裡轉發。

> **本專案的例外（2026-10-06 使用者裁示 ①B）**：`com.mpx.common.web`（`ApiForwarder` 等四個類別與測試）已自本專案刪除，改用 `com.mpx.infra_manager_web` 自建的轉發器（透傳 `IM_` 開頭 cookie 與 `X-IM-XSRF`、全部 HTTP method、4xx 原樣回）。本檔其餘提到 `ApiForwarder` 之處為範本原文，以 `PRD.md`「前端架構」與「給範本維護者的註記」第 2 點為準；鐵律第 2、3、4 條與其餘規範照舊。

若專案根目錄有 `CLAUDE.md`，請一併遵守其中的規則。

前後端建議用不同專案名（例 `xxx_api`／`xxx_web`），避免 log 目錄撞在一起。

## 2. 兩包結構

| 位置 | 用途 | 可否修改 |
|---|---|---|
| `infra_manager_web\src\main\java\com\mpx\Application.java` | 殼 jar 啟動類別（掃描根 `com.mpx`） | 不可 |
| `infra_manager_web\src\main\java\com\mpx\common\web\` | `RestTemplateConfig`（connect 5 秒、read 120 秒）、`ApiForwarder`（轉發，只回狀態碼與本文）、`ForwardException`、`ForwardExceptionHandler`（只接 `ForwardException`，回 500 固定訊息） | 不可 |
| `infra_manager_web\src\main\java\com\mpx\infra_manager_web\controller\` | 轉發用 Controller（範例 `ExampleController`） | 自由新增 |
| `infra_manager_web\src\main\resources\` | `application.properties`、`config\host.properties`、`logback-spring.xml` | 可改值 |
| `infra_manager_web\src\frontend\` | 前端 build 產物（由 `npm run build` 寫入，不進 git） | 不要手改 |
| `infra_manager_web_frontend\src\types\` | API 請求／回應型別（範例 `example.ts`） | 新增功能時新增 |
| `infra_manager_web_frontend\src\api\` | `http.ts`（axios 實例）、各功能的 API 函式（範例 `example.ts`） | 新增功能時新增 |
| `infra_manager_web_frontend\src\views\` | 畫面（範例 `ExampleView.vue`） | 自由新增 |
| `infra_manager_web_frontend\src\router\index.ts` | 路由（hash 模式） | 新增畫面時加一筆 |
| `infra_manager_web_frontend\src\composables\useToast.ts` | toast 通知 | 可沿用 |
| `infra_manager_web_frontend\src\assets\main.css` | 色票與基礎字級 | 謹慎修改 |
| `infra_manager_web_frontend\tests\` | Vitest 測試（範例 `ExampleView.spec.ts`） | 自由新增 |

## 3. 新增一支功能的標準寫法

流程：殼 jar Controller（轉發）→ `types/` 型別 → `api/*.ts` → 畫面 `views/*.vue`。下面以「商品查詢」為例，都可直接編譯／build。

殼 jar：`infra_manager_web\src\main\java\com\mpx\infra_manager_web\controller\ItemController.java`

```java
package com.mpx.infra_manager_web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mpx.common.web.ApiForwarder;

@RestController
@RequestMapping("/api/v1/item")
public class ItemController {

	private final ApiForwarder apiForwarder;

	public ItemController(ApiForwarder apiForwarder) {
		this.apiForwarder = apiForwarder;
	}

	/** 轉發至後端 <backend.api.domain.path>/item/search；不解析 payload */
	@PostMapping("/search")
	public ResponseEntity<Object> search(@RequestBody Object body) {
		return apiForwarder.post("/item/search", body);
	}
}
```

型別：`infra_manager_web_frontend\src\types\item.ts`

```ts
export interface ItemSearchRequest {
  itemNo: string
}

export interface Item {
  itemNo: string
  itemName: string
}
```

前端 API：`infra_manager_web_frontend\src\api\item.ts`

```ts
import http from './http'
import type { Item, ItemSearchRequest } from '../types/item'

export function searchItems(payload: ItemSearchRequest): Promise<Item[]> {
  return http.post<Item[]>('/item/search', payload).then(r => r.data)
}
```

畫面：`infra_manager_web_frontend\src\views\ItemView.vue`

```vue
<template>
  <main class="page">
    <form @submit.prevent="doSearch">
      <label for="itemNo">商品編號</label>
      <input id="itemNo" v-model="itemNo" />
      <button type="submit" :disabled="loading">搜尋</button>
    </form>
    <p v-if="searched && rows.length === 0">查無資料</p>
    <ul v-else>
      <li v-for="r in rows" :key="r.itemNo">{{ r.itemNo }} {{ r.itemName }}</li>
    </ul>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { searchItems } from '../api/item'
import { useToast } from '../composables/useToast'
import type { Item } from '../types/item'

const { toast } = useToast()
const itemNo = ref('')
const rows = ref<Item[]>([])
const loading = ref(false)
const searched = ref(false)

async function doSearch(): Promise<void> {
  if (!itemNo.value.trim()) {
    toast('請輸入商品編號', 'amber')
    return
  }
  loading.value = true
  try {
    const data = await searchItems({ itemNo: itemNo.value.trim() })
    if (!Array.isArray(data)) {
      throw new Error('unexpected response')
    }
    rows.value = data
    searched.value = true
  } catch {
    toast('查詢失敗，請稍後再試', 'amber')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.page { padding: 20px; }
</style>
```

路由：在 `src\router\index.ts` 的 `routes` 加 `{ path: '/item', component: () => import('../views/ItemView.vue') }`，以 `#/item` 開啟。
toast 的顯示區塊請照 `ExampleView.vue` 的 `<div id="toasts">` 放進畫面。

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
| `infra_manager_web\src\main\resources\config\host.properties` | 後端 API 位址：`backend.api.domain`、`backend.api.port`、`backend.api.path`，以及由前三個以 `${}` 組成的 `backend.api.domain.path`（`ApiForwarder` 只讀這一個） |

- 以上都是**真檔，不進 git**；進 git 的是同名 `.properties.example`。新增 key 時同步寫進 `.example`（不寫真實位址）。
- 本機沒有真檔時從 `.example` 複製，例：`copy src\main\resources\config\host.properties.example src\main\resources\config\host.properties`。

## 7. 建置與執行

需要 **Node 24**（`infra_manager_web_frontend\.nvmrc`，`package.json` 的 `engines.node` 為 `>=24`）與 **JDK 25**。

```
cd infra_manager_web_frontend
npm install
npm test                           # Vitest（jsdom），測試檔在 tests\
npm run build                      # 先型別檢查，再輸出到 ..\infra_manager_web\src\frontend
cd ..\infra_manager_web
./mvnw clean package               # Windows：.\mvnw.cmd clean package
java -jar target\infra_manager_web-0.0.1-SNAPSHOT.jar
```

- 開啟 `http://localhost:<server.port>/infra_manager_web/`。
- 殼 jar 帶 Maven Wrapper（`mvnw`、`.\mvnw.cmd`，釘 Maven 3.9.16），不需本機安裝 Maven。
- 開發時另開 `npm run dev`（Vite dev server），`/infra_manager_web/api` 會 proxy 到本機殼 jar（port 同 `server.port`），殼 jar 要先啟動。
- `npm run build` 會清空 `infra_manager_web\src\frontend`（含 `.gitkeep`），可用 `git checkout` 還原 `.gitkeep`；只有 `.gitkeep` 時殼 jar 也能建置。

## 8. Vite 版本說明

本範本用 **Vite 8**（打包器為 Rolldown，取代 Vite 7 的 Rollup＋esbuild 組合）。對使用者有影響的部分：

- `vite.config.ts` 的一般設定（`base`、`build.outDir`、`server.proxy`、plugins）寫法與 Vite 7 相同，本範本未用到需改寫的項目。
- 需要自訂打包細節時，請查 Vite 8 文件的 `build.rolldownOptions`（Vite 7 為 `build.rollupOptions`），不要照抄舊文章的 Rollup／esbuild 專屬設定。
- Node 需 20.19 以上或 22.12 以上；本範本要求 Node 24。

## 9. 限制

- 前端 axios timeout（120 秒，`http.ts`）與殼 jar 的 read timeout（120 秒）一致，改一邊就要改另一邊。
- 後端錯誤或連不上時，殼 jar 回 500 與固定訊息 `{"message":"後端服務呼叫失敗"}`（不帶後端回應內容、不轉發後端 header）；前端一律提示「查詢失敗，請稍後再試」，**不得把失敗顯示成「查無資料」**。
- log 落點 `/home/tomcat/log/infra_manager_web/infra_manager_web.yyyy-MM-dd.log`（Windows 依啟動磁碟機），不記 payload。
- 不內建登入、選店或與外框頁面的整合；需要時由專案自行加。
- 真檔（`*.properties`）、`node_modules`、`coverage`、build 產物都不進 git。

## 10. 給 AI 的檢查清單

- [ ] 已讀完本檔。
- [ ] 沒有修改 `com.mpx.common.web` 與 `com.mpx.Application`。
- [ ] 新 Controller 放在 `com.mpx.infra_manager_web` 底下，只用 `ApiForwarder` 轉發，不解析 payload、沒有 CrossOrigin。
- [ ] API 型別放在 `src\types\`，前端只透過 `api/http.ts` 打 `/infra_manager_web/api/v1`。
- [ ] 沒有 `any`（或已註明原因）；`npm run type-check` 通過。
- [ ] 查詢失敗走 toast，不顯示成「查無資料」；清單每頁 20 筆；1024 寬無橫向捲軸。
- [ ] 新增的 properties key 已同步寫進 `.example`；沒有把真檔或真實位址加進 git。
- [ ] `npm test`、`npm run build` 與 `./mvnw clean package` 都通過。
