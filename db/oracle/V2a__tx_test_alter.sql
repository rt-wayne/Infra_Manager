-- =====================================================================
-- Infra Manager：IM_TX_TEST 補共同欄位 UPDATE_DATE／UPDATE_BY（2026-10-06 複審裁示 ⑥A）
-- 適用：已用「補欄前」的 V2__tx_test_table.sql 建過表的環境。全新環境直接跑新版 V2 即可，不需本檔。
-- 執行方式：由 rd_user（表的擁有者）登入後手動執行；本專案不使用 Flyway。
-- 本檔不含任何帳號密碼或連線資訊。
-- =====================================================================

ALTER TABLE IM_TX_TEST ADD (
    UPDATE_DATE         DATE,
    UPDATE_BY           VARCHAR2(30)
);

COMMENT ON COLUMN IM_TX_TEST.UPDATE_DATE IS '最後更新時間';
COMMENT ON COLUMN IM_TX_TEST.UPDATE_BY   IS '最後更新人員工號';
