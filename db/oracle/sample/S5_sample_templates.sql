-- ============================================================
-- AI版本  : Claude Opus 5.5 (claude-opus-5-5)
-- 修改日期: 2026-10-07
-- 變更說明: 新增：S5 範本管理／套用範本的驗收種子資料。3 筆範本，建立者都是 T0001（wayne，admin），
--           涵蓋：完整內容（含設備、作業步驟、供應商）、只有少數欄位（標題空白）、已套用過（有套用次數與最後套用人）。
--           FORM_JSON 格式同 TemplateForm；選項不帶 formOptionId（各環境 ID 不同），套用後勾選項由驗收者自己補。
--           只在測試 DB 執行；鍵值一律以 S5SEED- 開頭，清除指令見檔尾。不含任何帳號密碼
-- 執行方式：以 AP 帳號連測試 DB、ALTER SESSION SET CURRENT_SCHEMA 後執行本檔
-- ============================================================

INSERT INTO IM_TMPL (TMPL_ID, TMPL_NAME, FORM_JSON, OWNER_USER_ID, CREATE_BY) VALUES (
    'S5SEED-001', 'S5 種子：核心交換器韌體升級',
    '{"title":"核心交換器韌體升級","prioCode":"P3","selfExec":true,"supplierExec":true,"workModeCode":"ONSITE",'
    || '"supplier":{"name":"某網通廠商","contact":"陳工程師","tel":"02-1234-5678","headCount":2},'
    || '"workSubject":"核心交換器 SW-CORE-01 韌體由 9.3 升級至 9.5",'
    || '"impactDesc":"升級期間核心網路中斷約 10 分鐘\n影響全公司內網","workDetail":"1. 備份設定\n2. 上傳韌體\n3. 重開機驗證",'
    || '"riskDesc":"韌體不相容導致開機失敗","rollbackPlan":"以備份韌體回復並還原設定檔",'
    || '"categoryItemIds":[],"categoryOthers":[],"reasonIds":[],"scopeIds":[],'
    || '"equipments":[{"name":"SW-CORE-01","assetNo":"A-0001","modelNo":"C9500","serialNo":"SN0001","purpose":"核心交換","mgmtIp":"10.0.0.1"}],'
    || '"planSteps":["備份設定檔","上傳新韌體","重開機","驗證各 VLAN 連線"],"schedule":{"estHours":2.5}}',
    'T0001', 'S5SEED');

INSERT INTO IM_TMPL (TMPL_ID, TMPL_NAME, FORM_JSON, OWNER_USER_ID, CREATE_BY) VALUES (
    'S5SEED-002', 'S5 種子：例行巡檢（只填少數欄位）',
    '{"title":null,"prioCode":"P4","selfExec":true,"supplierExec":false,"workModeCode":"ONSITE",'
    || '"workSubject":"機房例行巡檢","categoryItemIds":[],"categoryOthers":[],"reasonIds":[],"scopeIds":[],'
    || '"equipments":[],"planSteps":[],"schedule":{"estHours":1}}',
    'T0001', 'S5SEED');

INSERT INTO IM_TMPL (TMPL_ID, TMPL_NAME, FORM_JSON, OWNER_USER_ID, USE_CNT, LAST_USE_DATE, LAST_USE_USER_ID, CREATE_BY) VALUES (
    'S5SEED-003', 'S5 種子：防火牆遠端政策調整（已套用過）',
    '{"title":"防火牆政策調整","prioCode":"P2","selfExec":false,"supplierExec":true,"workModeCode":"REMOTE",'
    || '"remoteMethod":"VPN","supplier":{"name":"資安廠商","headCount":1},'
    || '"workSubject":"新增對外服務 NAT 與存取規則","impactDesc":"不影響既有連線",'
    || '"categoryItemIds":[],"categoryOthers":[],"reasonIds":[],"scopeIds":[],"equipments":[],'
    || '"planSteps":["匯出現行政策","套用新規則","測試連線"],"schedule":{"estHours":1.5}}',
    'T0001', 7, SYSDATE - 3, 'T0001', 'S5SEED');

COMMIT;

-- ============================================================
-- 清除本檔資料（驗收完成後執行）
-- ============================================================
-- DELETE FROM IM_TMPL WHERE TMPL_ID LIKE 'S5SEED-%';
-- COMMIT;
