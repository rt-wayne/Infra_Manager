// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-06
// 變更說明: 新增：Vitest 設定（規格 D-42）；沿用 vite.config.ts，測試環境 jsdom，測試檔放 tests/
//           jdk25 階段 6（規格 v1.3 D-42、D-50）：範本不含測試檔，加 passWithNoTests 讓沒有測試檔時 npm test 仍成功結束
// ============================================================
import { defineConfig, mergeConfig } from 'vitest/config'
import viteConfig from './vite.config.ts'

export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: 'jsdom',
      include: ['tests/**/*.spec.ts'],
      passWithNoTests: true
    }
  })
)
