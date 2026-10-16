// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-06
// 變更說明: 新增：殼 jar 業務程式佔位套件（規格 v1.3 D-50，取代原範例 Controller）；用產生器開新專案時本套件改名為 com.mpx.<專案名>
//           jdk25 階段 7（規格 v1.4 D-51）：說明改為 Controller 自行注入 RestTemplate、以 host.properties 組合 key 取後端位址
//           jdk25 階段 7 第 1 輪修正（2026-10-06，TC-F04）：說明改為本文以 byte[] 接、原樣回傳並去掉 Content-Length／Transfer-Encoding
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 1：說明中的組合 key 改為 infra.manager.domain.path（專案改名 infra_manager）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp_web 改名為 com.mpx.infra_manager_web（專案改名 infra_manager），只改 package／import／全名引用
// ============================================================

/**
 * 呼叫後端的 Controller 放這裡（子套件自訂，例 {@code controller}），寫法見專案 README 第 3 節。
 * <p>
 * Controller 映射 {@code /api/v1/...}；注入 {@code RestTemplate}（{@code com.mpx.common.web.RestTemplateConfig} 提供），
 * 以 {@code @PropertySource("classpath:config/host.properties")}＋{@code @Value("${infra.manager.domain.path}")} 取得後端位址
 * （本專案組合 key 由使用者改名，變更單 #2；2026-10-16 隨專案改名為 infra.manager.domain.path），
 * 以 {@code byte[]} 接本文呼叫後端（{@code exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), byte[].class)}／{@code getForEntity(url, byte[].class)}），
 * 狀態碼與本文原樣回傳，後端 header 照抄但去掉 {@code Content-Length}、{@code Transfer-Encoding}、{@code Connection}（由殼 jar 重算，照抄會卡到逾時）；
 * 不解析 payload、不開 CrossOrigin。
 * 共用元件在 {@code com.mpx.common.web} 底下、啟動類別 {@code com.mpx.Application}，兩者複製後都不要動。
 */
package com.mpx.infra_manager_web;
