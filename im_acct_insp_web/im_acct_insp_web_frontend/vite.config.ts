// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-05
// 變更說明: 新增：前端範本 Vite 設定（規格 v4），比照 store_web_barcode_frontend
//           base 與殼 jar 的 context-path 相同；build 直接輸出到殼 jar 的 src/frontend（mvn package 時打進 static）
//           dev 時 /<專案名>/api 轉給本機殼 jar（port 同 application.properties 的 server.port）
//           jdk25 階段 3（規格 D-41）：改 TypeScript（vite.config.ts）、Vite 8；移除 '@' alias（範本未使用，可省掉 node 型別）
// AI版本  : 使用者手改
// 修改日期: 2026-10-14
// 變更說明: dev proxy 目標 port 由 8080 改為 8787，與殼 jar 的 server.port 一致（BACKLOG 第 35 項）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 1：base 與 dev proxy 路徑改 /infra_manager_web（隨殼 jar context-path 改名）；
//           outDir 的目錄名留到批 2 搬目錄時再改
// ============================================================
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  base: '/infra_manager_web/',
  build: {
    outDir: '../im_acct_insp_web/src/frontend',
    emptyOutDir: true
  },
  server: {
    proxy: {
      '/infra_manager_web/api': {
        target: 'http://localhost:8787',
        changeOrigin: true
      }
    }
  }
})
