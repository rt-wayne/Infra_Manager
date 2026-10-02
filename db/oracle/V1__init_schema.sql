-- =====================================================================
-- Infra Manager（Java 重寫版）Oracle 19c 初始 schema
-- 檔名沿用 Flyway 慣例（V1__init_schema.sql），S1 骨架建好後整檔搬到
-- backend/src/main/resources/db/migration/ 由 Flyway 執行；在那之前可用
-- SQL*Plus / SQL Developer 直接在測試 schema 執行。
--
-- 規則（見 docs/plan/2026-10-02-rewrite-architecture.md 第 0 節）：
--   * 布林一律 NUMBER(1) + CHECK IN (0,1)
--   * JSON 一律 CLOB + CHECK (col IS JSON)
--   * Oracle 的 '' 等於 NULL，應用層統一「DB 存 NULL、API 回 ""」
--   * 時間一律 TIMESTAMP WITH TIME ZONE（舊資料的假 UTC 由匯入程式修正）
--   * 代理鍵用 GENERATED ALWAYS AS IDENTITY（19c 支援，不另建 SEQUENCE）
--   * 名稱全部 ≤ 30 字元，相容 19c 的舊式 30 字元限制設定
--
-- 本檔只建結構與固定參考資料（角色、簽核流程定義、站台設定預設值），
-- 不含任何帳號密碼或連線資訊。使用者、申請單等業務資料由 S3 的匯入程式灌入。
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. 身分
-- ---------------------------------------------------------------------

CREATE TABLE app_user (
    id                  NUMBER(19)      GENERATED ALWAYS AS IDENTITY,
    login_id            VARCHAR2(64)    NOT NULL,          -- 一律小寫
    name                VARCHAR2(100)   NOT NULL,
    email               VARCHAR2(255),
    title               VARCHAR2(100),
    department          VARCHAR2(100),
    phone               VARCHAR2(50),
    active              NUMBER(1)       DEFAULT 1 NOT NULL,
    password_hash       VARCHAR2(255),                     -- 格式依 BACKLOG 第 16 項裁示（可能為 scrypt$… 或 bcrypt）
    password_is_default NUMBER(1)       DEFAULT 0 NOT NULL,
    password_changed_at TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_app_user          PRIMARY KEY (id),
    CONSTRAINT uq_app_user_login    UNIQUE (login_id),
    CONSTRAINT ck_app_user_login_lc CHECK (login_id = LOWER(login_id)),
    CONSTRAINT ck_app_user_active   CHECK (active IN (0, 1)),
    CONSTRAINT ck_app_user_pwd_def  CHECK (password_is_default IN (0, 1))
);

CREATE TABLE role (
    code        VARCHAR2(32)    NOT NULL,
    name        VARCHAR2(100)   NOT NULL,
    sort_no     NUMBER(5)       DEFAULT 0 NOT NULL,
    CONSTRAINT pk_role PRIMARY KEY (code)
);

CREATE TABLE user_role (
    user_id     NUMBER(19)      NOT NULL,
    role_code   VARCHAR2(32)    NOT NULL,
    CONSTRAINT pk_user_role      PRIMARY KEY (user_id, role_code),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id)   REFERENCES app_user (id),
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_code) REFERENCES role (code)
);

CREATE INDEX ix_user_role_role ON user_role (role_code);

-- remember-me：若 BACKLOG 第 29 項選 Spring 內建 remember-me，本表改為 persistent_logins
CREATE TABLE remember_token (
    id              NUMBER(19)      GENERATED ALWAYS AS IDENTITY,
    user_id         NUMBER(19)      NOT NULL,
    token_hash      VARCHAR2(128)   NOT NULL,              -- SHA-256 hex，不存原始 token
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    last_used_at    TIMESTAMP WITH TIME ZONE,
    expires_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    user_agent      VARCHAR2(512),
    CONSTRAINT pk_remember_token      PRIMARY KEY (id),
    CONSTRAINT uq_remember_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_remember_token_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);

CREATE INDEX ix_remember_token_user ON remember_token (user_id);


-- ---------------------------------------------------------------------
-- 2. 共用簽核引擎（CR 先用；ACCOUNT、INSPECTION 兩模組之後重用）
-- ---------------------------------------------------------------------

CREATE TABLE workflow_def (
    id              VARCHAR2(32)    NOT NULL,              -- 沿用 full / p2_high / p1_emergency
    name            VARCHAR2(100)   NOT NULL,
    description     VARCHAR2(500),
    doc_type        VARCHAR2(20)    NOT NULL,              -- CR / ACCOUNT / INSPECTION
    is_default      NUMBER(1)       DEFAULT 0 NOT NULL,
    active          NUMBER(1)       DEFAULT 1 NOT NULL,
    CONSTRAINT pk_workflow_def         PRIMARY KEY (id),
    CONSTRAINT ck_workflow_def_type    CHECK (doc_type IN ('CR', 'ACCOUNT', 'INSPECTION')),
    CONSTRAINT ck_workflow_def_default CHECK (is_default IN (0, 1)),
    CONSTRAINT ck_workflow_def_active  CHECK (active IN (0, 1))
);

CREATE TABLE workflow_step_def (
    id                  NUMBER(19)      GENERATED ALWAYS AS IDENTITY,
    workflow_id         VARCHAR2(32)    NOT NULL,
    seq                 NUMBER(3)       NOT NULL,          -- 從 1 起算
    step_key            VARCHAR2(50)    NOT NULL,          -- dept_manager / governance_review / …
    name                VARCHAR2(100)   NOT NULL,
    approver_type       VARCHAR2(10)    NOT NULL,          -- USER / ROLE
    approver_user_id    NUMBER(19),
    approver_role       VARCHAR2(32),
    notify_only         NUMBER(1)       DEFAULT 0 NOT NULL,
    allow_delegate      NUMBER(1)       DEFAULT 0 NOT NULL,
    step_mode           VARCHAR2(20)    DEFAULT 'SEQUENTIAL' NOT NULL,  -- SEQUENTIAL；預留 POST_HOC（MODE 是 Oracle 保留字，故加前綴）
    CONSTRAINT pk_wf_step_def        PRIMARY KEY (id),
    CONSTRAINT uq_wf_step_def_seq    UNIQUE (workflow_id, seq),
    CONSTRAINT fk_wf_step_def_wf     FOREIGN KEY (workflow_id)      REFERENCES workflow_def (id),
    CONSTRAINT fk_wf_step_def_user   FOREIGN KEY (approver_user_id) REFERENCES app_user (id),
    CONSTRAINT fk_wf_step_def_role   FOREIGN KEY (approver_role)    REFERENCES role (code),
    CONSTRAINT ck_wf_step_def_type   CHECK (approver_type IN ('USER', 'ROLE')),
    CONSTRAINT ck_wf_step_def_target CHECK (
        (approver_type = 'USER' AND approver_user_id IS NOT NULL AND approver_role IS NULL) OR
        (approver_type = 'ROLE' AND approver_role IS NOT NULL AND approver_user_id IS NULL)
    ),
    CONSTRAINT ck_wf_step_def_notify CHECK (notify_only IN (0, 1)),
    CONSTRAINT ck_wf_step_def_deleg  CHECK (allow_delegate IN (0, 1)),
    CONSTRAINT ck_wf_step_def_mode   CHECK (step_mode IN ('SEQUENTIAL', 'POST_HOC'))
);

-- 每份文件、每個版次一條簽核實例
CREATE TABLE approval_instance (
    id              NUMBER(19)      GENERATED ALWAYS AS IDENTITY,
    doc_type        VARCHAR2(20)    NOT NULL,
    doc_id          VARCHAR2(40)    NOT NULL,              -- CR 時 = change_request.id
    doc_version     NUMBER(5)       NOT NULL,
    workflow_id     VARCHAR2(32)    NOT NULL,
    status          VARCHAR2(20)    NOT NULL,              -- PENDING / APPROVED / REJECTED / RECALLED / CANCELLED
    started_at      TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    closed_at       TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_approval_instance     PRIMARY KEY (id),
    CONSTRAINT uq_approval_instance_doc UNIQUE (doc_type, doc_id, doc_version),
    CONSTRAINT fk_approval_instance_wf  FOREIGN KEY (workflow_id) REFERENCES workflow_def (id),
    CONSTRAINT ck_approval_instance_typ CHECK (doc_type IN ('CR', 'ACCOUNT', 'INSPECTION')),
    CONSTRAINT ck_approval_instance_st  CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'RECALLED', 'CANCELLED'))
);

CREATE INDEX ix_approval_instance_status ON approval_instance (status);

CREATE TABLE approval_step (
    id              NUMBER(19)      GENERATED ALWAYS AS IDENTITY,
    instance_id     NUMBER(19)      NOT NULL,
    seq             NUMBER(3)       NOT NULL,
    step_key        VARCHAR2(50)    NOT NULL,
    name            VARCHAR2(100)   NOT NULL,              -- 建立當下的步驟名稱快照
    notify_only     NUMBER(1)       DEFAULT 0 NOT NULL,
    status          VARCHAR2(20)    NOT NULL,              -- WAITING / PENDING / APPROVED / REJECTED / SKIPPED / CANCELLED
    decided_at      TIMESTAMP WITH TIME ZONE,
    decided_by      NUMBER(19),
    decided_by_name VARCHAR2(100),                         -- 快照：人員改名後歷史不變
    comment_text    VARCHAR2(2000),
    CONSTRAINT pk_approval_step       PRIMARY KEY (id),
    CONSTRAINT uq_approval_step_seq   UNIQUE (instance_id, seq),
    CONSTRAINT fk_approval_step_inst  FOREIGN KEY (instance_id) REFERENCES approval_instance (id),
    CONSTRAINT fk_approval_step_user  FOREIGN KEY (decided_by)  REFERENCES app_user (id),
    CONSTRAINT ck_approval_step_notif CHECK (notify_only IN (0, 1)),
    CONSTRAINT ck_approval_step_stat  CHECK (status IN ('WAITING', 'PENDING', 'APPROVED', 'REJECTED', 'SKIPPED', 'CANCELLED'))
);

CREATE INDEX ix_approval_step_inst ON approval_step (instance_id);

-- 候選簽核人（角色池展開後的名單，任一人可簽）
CREATE TABLE approval_step_candidate (
    step_id         NUMBER(19)      NOT NULL,
    user_id         NUMBER(19)      NOT NULL,
    name_snapshot   VARCHAR2(100),
    email_snapshot  VARCHAR2(255),
    CONSTRAINT pk_approval_step_cand      PRIMARY KEY (step_id, user_id),
    CONSTRAINT fk_approval_step_cand_step FOREIGN KEY (step_id) REFERENCES approval_step (id),
    CONSTRAINT fk_approval_step_cand_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);

-- 「我的待辦」：由 user_id 反查 step → instance（status = PENDING）
CREATE INDEX ix_approval_step_cand_user ON approval_step_candidate (user_id);

-- 預留逐項簽核（本次只建表不使用）
CREATE TABLE approval_step_item (
    id              NUMBER(19)      GENERATED ALWAYS AS IDENTITY,
    step_id         NUMBER(19)      NOT NULL,
    item_ref        VARCHAR2(100)   NOT NULL,
    decision        VARCHAR2(20),
    comment_text    VARCHAR2(2000),
    decided_at      TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_approval_step_item      PRIMARY KEY (id),
    CONSTRAINT fk_approval_step_item_step FOREIGN KEY (step_id) REFERENCES approval_step (id)
);

CREATE INDEX ix_approval_step_item_step ON approval_step_item (step_id);


-- ---------------------------------------------------------------------
-- 3. 申請單（Change Request）
-- ---------------------------------------------------------------------

-- 編號產生器：取代舊系統「當天檔案數 + 1」
CREATE TABLE cr_number_seq (
    prefix      VARCHAR2(10)    NOT NULL,                  -- IM / HIST
    ymd         CHAR(8)         NOT NULL,                  -- yyyymmdd
    last_no     NUMBER(5)       DEFAULT 0 NOT NULL,
    CONSTRAINT pk_cr_number_seq PRIMARY KEY (prefix, ymd)
);

CREATE TABLE change_request (
    id                      VARCHAR2(20)    NOT NULL,      -- IM20260826-005 / HIST-20251217-001
    title                   VARCHAR2(200)   NOT NULL,
    priority                VARCHAR2(2)     NOT NULL,      -- P1 ~ P4
    workflow_id             VARCHAR2(32)    NOT NULL,
    workflow_name_snapshot  VARCHAR2(100),
    applicant_id            NUMBER(19)      NOT NULL,
    status                  VARCHAR2(20)    NOT NULL,      -- DRAFT / PENDING / APPROVED / REJECTED / RETURNED / EXECUTED / CLOSED
    source                  VARCHAR2(10)    DEFAULT 'online' NOT NULL,  -- online / imported
    current_version         NUMBER(5)       DEFAULT 1 NOT NULL,
    row_version             NUMBER(10)      DEFAULT 0 NOT NULL,         -- JPA @Version 樂觀鎖
    created_at              TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    -- 軟刪除（取代 applications-deleted/ 目錄）
    deleted_at              TIMESTAMP WITH TIME ZONE,
    deleted_by              NUMBER(19),
    delete_reason           VARCHAR2(500),
    delete_via_role         VARCHAR2(32),
    -- basic 攤平
    apply_date              DATE,
    department              VARCHAR2(100),
    applicant_name_snapshot VARCHAR2(100),
    phone                   VARCHAR2(50),
    email                   VARCHAR2(255),
    executor_self           NUMBER(1)       DEFAULT 0 NOT NULL,
    executor_vendor         NUMBER(1)       DEFAULT 0 NOT NULL,
    work_mode               VARCHAR2(10),                  -- onsite / remote
    remote_method           VARCHAR2(200),
    vendor                  VARCHAR2(200),
    vendor_contact          VARCHAR2(100),
    vendor_phone            VARCHAR2(50),
    vendor_headcount        NUMBER(4),
    -- work 攤平
    subject                 VARCHAR2(200),
    impact_desc             VARCHAR2(2000),
    work_detail             CLOB,
    risk_assessment         CLOB,
    rollback_plan           CLOB,
    reason_other            VARCHAR2(500),
    schedule_start          TIMESTAMP WITH TIME ZONE,
    schedule_end            TIMESTAMP WITH TIME ZONE,
    estimated_hours         NUMBER(6, 2),
    -- location 攤平
    loc_source              VARCHAR2(10),                  -- impact（機櫃選擇器）/ manual
    area                    VARCHAR2(50),
    rack                    VARCHAR2(50),
    u_position              VARCHAR2(50),                  -- 顯示用字串，如 U19-U23
    site_id                 VARCHAR2(50),
    rack_id                 VARCHAR2(50),
    u_start                 NUMBER(3),
    u_end                   NUMBER(3),
    omit_reason             VARCHAR2(500),
    -- 補件說明（最新一次；歷次在 cr_event）
    resubmit_note           VARCHAR2(2000),
    CONSTRAINT pk_change_request        PRIMARY KEY (id),
    CONSTRAINT fk_change_request_wf     FOREIGN KEY (workflow_id)  REFERENCES workflow_def (id),
    CONSTRAINT fk_change_request_appl   FOREIGN KEY (applicant_id) REFERENCES app_user (id),
    CONSTRAINT fk_change_request_delby  FOREIGN KEY (deleted_by)   REFERENCES app_user (id),
    CONSTRAINT ck_change_request_prio   CHECK (priority IN ('P1', 'P2', 'P3', 'P4')),
    CONSTRAINT ck_change_request_status CHECK (status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'RETURNED', 'EXECUTED', 'CLOSED')),
    CONSTRAINT ck_change_request_source CHECK (source IN ('online', 'imported')),
    CONSTRAINT ck_change_request_exself CHECK (executor_self IN (0, 1)),
    CONSTRAINT ck_change_request_exvend CHECK (executor_vendor IN (0, 1)),
    CONSTRAINT ck_change_request_wmode  CHECK (work_mode IS NULL OR work_mode IN ('onsite', 'remote')),
    CONSTRAINT ck_change_request_urange CHECK (u_start IS NULL OR u_end IS NULL OR u_start <= u_end)
);

CREATE INDEX ix_change_request_applicant ON change_request (applicant_id);
CREATE INDEX ix_change_request_status    ON change_request (status);
CREATE INDEX ix_change_request_created   ON change_request (created_at);
CREATE INDEX ix_change_request_deleted   ON change_request (deleted_at);
CREATE INDEX ix_change_request_schedule  ON change_request (schedule_start);

-- 異動類別（多選）
CREATE TABLE cr_category (
    cr_id           VARCHAR2(20)    NOT NULL,
    category_key    VARCHAR2(30)    NOT NULL,              -- network / server_storage / power / hvac / security / facility / software
    option_text     VARCHAR2(100)   NOT NULL,
    CONSTRAINT pk_cr_category    PRIMARY KEY (cr_id, category_key, option_text),
    CONSTRAINT fk_cr_category_cr FOREIGN KEY (cr_id) REFERENCES change_request (id)
);

CREATE TABLE cr_category_other (
    cr_id           VARCHAR2(20)    NOT NULL,
    category_key    VARCHAR2(30)    NOT NULL,
    other_text      VARCHAR2(500)   NOT NULL,
    CONSTRAINT pk_cr_category_other    PRIMARY KEY (cr_id, category_key),
    CONSTRAINT fk_cr_category_other_cr FOREIGN KEY (cr_id) REFERENCES change_request (id)
);

CREATE TABLE cr_reason (
    cr_id           VARCHAR2(20)    NOT NULL,
    reason_value    VARCHAR2(100)   NOT NULL,
    CONSTRAINT pk_cr_reason    PRIMARY KEY (cr_id, reason_value),
    CONSTRAINT fk_cr_reason_cr FOREIGN KEY (cr_id) REFERENCES change_request (id)
);

CREATE TABLE cr_impact_scope (
    cr_id           VARCHAR2(20)    NOT NULL,
    scope_value     VARCHAR2(100)   NOT NULL,
    CONSTRAINT pk_cr_impact_scope    PRIMARY KEY (cr_id, scope_value),
    CONSTRAINT fk_cr_impact_scope_cr FOREIGN KEY (cr_id) REFERENCES change_request (id)
);

CREATE TABLE cr_equipment (
    id              NUMBER(19)      GENERATED ALWAYS AS IDENTITY,
    cr_id           VARCHAR2(20)    NOT NULL,
    seq             NUMBER(3)       NOT NULL,
    name            VARCHAR2(200),
    asset_no        VARCHAR2(100),
    model           VARCHAR2(200),
    serial_no       VARCHAR2(100),
    purpose         VARCHAR2(500),
    mgmt_ip         VARCHAR2(45),                          -- 容納 IPv6
    CONSTRAINT pk_cr_equipment     PRIMARY KEY (id),
    CONSTRAINT uq_cr_equipment_seq UNIQUE (cr_id, seq),
    CONSTRAINT fk_cr_equipment_cr  FOREIGN KEY (cr_id) REFERENCES change_request (id)
);

CREATE INDEX ix_cr_equipment_asset  ON cr_equipment (asset_no);
CREATE INDEX ix_cr_equipment_serial ON cr_equipment (serial_no);

-- 作業步驟（固定 4 筆，空白列不存）
CREATE TABLE cr_plan_step (
    cr_id           VARCHAR2(20)    NOT NULL,
    seq             NUMBER(3)       NOT NULL,
    step_text       VARCHAR2(1000)  NOT NULL,
    CONSTRAINT pk_cr_plan_step    PRIMARY KEY (cr_id, seq),
    CONSTRAINT fk_cr_plan_step_cr FOREIGN KEY (cr_id) REFERENCES change_request (id)
);

-- 執行檢核表（每版 11 筆）
CREATE TABLE cr_checklist_item (
    cr_id           VARCHAR2(20)    NOT NULL,
    version         NUMBER(5)       NOT NULL,
    seq             NUMBER(3)       NOT NULL,
    item            VARCHAR2(200)   NOT NULL,
    done            NUMBER(1)       DEFAULT 0 NOT NULL,
    completed_at    TIMESTAMP WITH TIME ZONE,
    executor        VARCHAR2(100),
    CONSTRAINT pk_cr_checklist_item      PRIMARY KEY (cr_id, version, seq),
    CONSTRAINT fk_cr_checklist_item_cr   FOREIGN KEY (cr_id) REFERENCES change_request (id),
    CONSTRAINT ck_cr_checklist_item_done CHECK (done IN (0, 1))
);

-- 執行回報（每版一筆）
CREATE TABLE cr_execution (
    cr_id           VARCHAR2(20)    NOT NULL,
    version         NUMBER(5)       NOT NULL,
    actual_start    TIMESTAMP WITH TIME ZONE,
    actual_end      TIMESTAMP WITH TIME ZONE,
    result          VARCHAR2(20),                          -- success / partial / failed（沿用舊值）
    exceptions_has  NUMBER(1)       DEFAULT 0 NOT NULL,
    exceptions_desc VARCHAR2(2000),
    followups_has   NUMBER(1)       DEFAULT 0 NOT NULL,
    followups_desc  VARCHAR2(2000),
    notes           CLOB,
    closed_by       NUMBER(19),
    closed_at       TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_cr_execution        PRIMARY KEY (cr_id, version),
    CONSTRAINT fk_cr_execution_cr     FOREIGN KEY (cr_id)     REFERENCES change_request (id),
    CONSTRAINT fk_cr_execution_closer FOREIGN KEY (closed_by) REFERENCES app_user (id),
    CONSTRAINT ck_cr_execution_exc    CHECK (exceptions_has IN (0, 1)),
    CONSTRAINT ck_cr_execution_fup    CHECK (followups_has IN (0, 1))
);

-- 版次（退件／補件時封存當版表單）
CREATE TABLE cr_version (
    cr_id           VARCHAR2(20)    NOT NULL,
    version         NUMBER(5)       NOT NULL,
    status_at_close VARCHAR2(20),                          -- 封存時的狀態（rejected / returned …）
    reason          VARCHAR2(2000),
    snapshot_at     TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    form_snapshot   CLOB,                                  -- 整份表單 JSON；舊資料匯入時無快照，為 NULL
    CONSTRAINT pk_cr_version      PRIMARY KEY (cr_id, version),
    CONSTRAINT fk_cr_version_cr   FOREIGN KEY (cr_id) REFERENCES change_request (id),
    CONSTRAINT ck_cr_version_json CHECK (form_snapshot IS JSON)
);

-- 事件流水（統一 governanceReview / reviewHistory / executionReject / resubmitNote / 刪除）
CREATE TABLE cr_event (
    id              NUMBER(19)      GENERATED ALWAYS AS IDENTITY,
    cr_id           VARCHAR2(20)    NOT NULL,
    version         NUMBER(5)       NOT NULL,
    event_type      VARCHAR2(20)    NOT NULL,
    by_user         NUMBER(19),
    by_user_name    VARCHAR2(100),                         -- 快照
    at_time         TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    comment_text    VARCHAR2(2000),
    CONSTRAINT pk_cr_event      PRIMARY KEY (id),
    CONSTRAINT fk_cr_event_cr   FOREIGN KEY (cr_id)   REFERENCES change_request (id),
    CONSTRAINT fk_cr_event_user FOREIGN KEY (by_user) REFERENCES app_user (id),
    CONSTRAINT ck_cr_event_type CHECK (event_type IN (
        'SUBMIT', 'RECALL', 'EXEC_REJECT', 'GOV_PASS', 'GOV_RETURN', 'RESUBMIT', 'DELETE', 'RESTORE'
    ))
);

CREATE INDEX ix_cr_event_cr ON cr_event (cr_id, at_time);


-- ---------------------------------------------------------------------
-- 4. 附件（三模組共用；檔案本體存放位置依 BACKLOG 第 21 項裁示）
-- ---------------------------------------------------------------------

CREATE TABLE attachment (
    id              NUMBER(19)      GENERATED ALWAYS AS IDENTITY,
    owner_type      VARCHAR2(10)    NOT NULL,              -- CR / STEP / AI
    owner_id        VARCHAR2(40)    NOT NULL,              -- CR → change_request.id；STEP → approval_step.id；AI → ai_review.id
    original_name   VARCHAR2(255)   NOT NULL,
    stored_name     VARCHAR2(255)   NOT NULL,
    storage_key     VARCHAR2(500)   NOT NULL,              -- 磁碟相對路徑或物件儲存 key
    size_bytes      NUMBER(12)      NOT NULL,
    mime            VARCHAR2(100),
    sha256          VARCHAR2(64),
    uploaded_at     TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    uploaded_by     NUMBER(19),
    CONSTRAINT pk_attachment      PRIMARY KEY (id),
    CONSTRAINT uq_attachment_key  UNIQUE (storage_key),
    CONSTRAINT fk_attachment_user FOREIGN KEY (uploaded_by) REFERENCES app_user (id),
    CONSTRAINT ck_attachment_type CHECK (owner_type IN ('CR', 'STEP', 'AI'))
);

CREATE INDEX ix_attachment_owner ON attachment (owner_type, owner_id);


-- ---------------------------------------------------------------------
-- 5. AI 審查（去留依 BACKLOG 第 17 項；選「不做」則本節只放舊報告唯讀顯示）
-- ---------------------------------------------------------------------

CREATE TABLE ai_review (
    id                  VARCHAR2(40)    NOT NULL,          -- 沿用 ar_xxxxxxxx_xxxxxx
    cr_id               VARCHAR2(20)    NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    created_by          NUMBER(19),
    created_by_name     VARCHAR2(100),
    model               VARCHAR2(100),
    effort              VARCHAR2(20),
    review_mode         VARCHAR2(20),                      -- 舊欄位 mode（保留字，加前綴）
    fell_back_from      VARCHAR2(100),
    app_version         NUMBER(5),
    app_status          VARCHAR2(20),
    app_snapshot        CLOB,                              -- 送審當下的申請單 JSON
    snapshot_hash       VARCHAR2(64),                      -- 新算法：key 排序 JSON 的 SHA-256
    legacy_sha1         VARCHAR2(40),                      -- 舊系統 computeAppHash，匯入保留供對照
    result              CLOB,                              -- AI 報告 JSON（三代格式見 result_schema）
    result_schema       VARCHAR2(5),                       -- v1 / v2 / v3
    input_tokens        NUMBER(10),
    output_tokens       NUMBER(10),
    cache_read_tokens   NUMBER(10),
    cache_write_tokens  NUMBER(10),
    duration_ms         NUMBER(10),
    stop_reason         VARCHAR2(50),
    sent_at             TIMESTAMP WITH TIME ZONE,
    sent_to             CLOB,                              -- 寄送對象清單 JSON
    status              VARCHAR2(10)    DEFAULT 'DONE' NOT NULL,  -- PENDING / DONE / FAILED
    error_text          VARCHAR2(2000),
    CONSTRAINT pk_ai_review          PRIMARY KEY (id),
    CONSTRAINT fk_ai_review_cr       FOREIGN KEY (cr_id)      REFERENCES change_request (id),
    CONSTRAINT fk_ai_review_user     FOREIGN KEY (created_by) REFERENCES app_user (id),
    CONSTRAINT ck_ai_review_snap     CHECK (app_snapshot IS JSON),
    CONSTRAINT ck_ai_review_result   CHECK (result IS JSON),
    CONSTRAINT ck_ai_review_sent_to  CHECK (sent_to IS JSON),
    CONSTRAINT ck_ai_review_schema   CHECK (result_schema IS NULL OR result_schema IN ('v1', 'v2', 'v3')),
    CONSTRAINT ck_ai_review_status   CHECK (status IN ('PENDING', 'DONE', 'FAILED'))
);

CREATE INDEX ix_ai_review_cr ON ai_review (cr_id, created_at);

-- 追問對話
CREATE TABLE ai_review_message (
    id                  NUMBER(19)      GENERATED ALWAYS AS IDENTITY,
    review_id           VARCHAR2(40)    NOT NULL,
    seq                 NUMBER(5)       NOT NULL,
    msg_role            VARCHAR2(10)    NOT NULL,          -- user / assistant（避開 ROLE 關鍵字）
    content             CLOB            NOT NULL,
    by_user             NUMBER(19),
    app_version_at_ask  NUMBER(5),
    model               VARCHAR2(100),
    fell_back_from      VARCHAR2(100),
    input_tokens        NUMBER(10),
    output_tokens       NUMBER(10),
    cache_read_tokens   NUMBER(10),
    cache_write_tokens  NUMBER(10),
    duration_ms         NUMBER(10),
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_ai_review_msg        PRIMARY KEY (id),
    CONSTRAINT uq_ai_review_msg_seq    UNIQUE (review_id, seq),
    CONSTRAINT fk_ai_review_msg_review FOREIGN KEY (review_id) REFERENCES ai_review (id),
    CONSTRAINT fk_ai_review_msg_user   FOREIGN KEY (by_user)   REFERENCES app_user (id),
    CONSTRAINT ck_ai_review_msg_role   CHECK (msg_role IN ('user', 'assistant'))
);


-- ---------------------------------------------------------------------
-- 6. 範本、表單選項、站台設定
-- ---------------------------------------------------------------------

CREATE TABLE template (
    id              VARCHAR2(60)    NOT NULL,              -- 沿用 tpl_xxx
    name            VARCHAR2(100)   NOT NULL,
    form            CLOB            NOT NULL,              -- 部分表單 JSON，直接套進新增頁
    owner_id        NUMBER(19),
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    created_by      NUMBER(19),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_by      NUMBER(19),
    usage_count     NUMBER(10)      DEFAULT 0 NOT NULL,
    last_used_at    TIMESTAMP WITH TIME ZONE,
    last_used_by    NUMBER(19),
    CONSTRAINT pk_template         PRIMARY KEY (id),
    CONSTRAINT fk_template_owner   FOREIGN KEY (owner_id)     REFERENCES app_user (id),
    CONSTRAINT fk_template_creator FOREIGN KEY (created_by)   REFERENCES app_user (id),
    CONSTRAINT fk_template_updater FOREIGN KEY (updated_by)   REFERENCES app_user (id),
    CONSTRAINT fk_template_lastby  FOREIGN KEY (last_used_by) REFERENCES app_user (id),
    CONSTRAINT ck_template_form    CHECK (form IS JSON)
);

-- 表單選項（類別、原因、影響範圍、優先級說明等）。
-- BACKLOG 第 31 項若選「留 JSON 設定檔」則本表不用、下個 migration 移除。
CREATE TABLE form_option (
    group_key       VARCHAR2(30)    NOT NULL,              -- category_network / reason / impact_scope / priority …
    option_key      VARCHAR2(50)    NOT NULL,
    name            VARCHAR2(100)   NOT NULL,
    color           VARCHAR2(20),
    definition      VARCHAR2(500),
    workflow_id     VARCHAR2(32),                          -- 優先級 → 預設流程
    sort_no         NUMBER(5)       DEFAULT 0 NOT NULL,
    active          NUMBER(1)       DEFAULT 1 NOT NULL,
    CONSTRAINT pk_form_option        PRIMARY KEY (group_key, option_key),
    CONSTRAINT fk_form_option_wf     FOREIGN KEY (workflow_id) REFERENCES workflow_def (id),
    CONSTRAINT ck_form_option_active CHECK (active IN (0, 1))
);

CREATE TABLE site_setting (
    setting_key     VARCHAR2(50)    NOT NULL,
    setting_value   VARCHAR2(2000),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_by      NUMBER(19),
    CONSTRAINT pk_site_setting      PRIMARY KEY (setting_key),
    CONSTRAINT fk_site_setting_user FOREIGN KEY (updated_by) REFERENCES app_user (id)
);


-- ---------------------------------------------------------------------
-- 7. 信件佇列、存取紀錄、機櫃快取
-- ---------------------------------------------------------------------

CREATE TABLE mail_outbox (
    id              NUMBER(19)      GENERATED ALWAYS AS IDENTITY,
    to_json         CLOB            NOT NULL,
    cc_json         CLOB,
    bcc_json        CLOB,
    subject         VARCHAR2(500)   NOT NULL,
    html            CLOB,
    status          VARCHAR2(10)    DEFAULT 'PENDING' NOT NULL,  -- PENDING / SENT / FAILED
    attempts        NUMBER(3)       DEFAULT 0 NOT NULL,
    error_text      VARCHAR2(2000),
    smtp_message_id VARCHAR2(255),
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    sent_at         TIMESTAMP WITH TIME ZONE,
    meta            CLOB,                                  -- 關聯文件、事件類型等 JSON
    CONSTRAINT pk_mail_outbox        PRIMARY KEY (id),
    CONSTRAINT ck_mail_outbox_to     CHECK (to_json IS JSON),
    CONSTRAINT ck_mail_outbox_cc     CHECK (cc_json IS JSON),
    CONSTRAINT ck_mail_outbox_bcc    CHECK (bcc_json IS JSON),
    CONSTRAINT ck_mail_outbox_meta   CHECK (meta IS JSON),
    CONSTRAINT ck_mail_outbox_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);

-- 背景 worker 撈待寄信件
CREATE INDEX ix_mail_outbox_status ON mail_outbox (status, created_at);

CREATE TABLE access_log (
    id              NUMBER(19)      GENERATED ALWAYS AS IDENTITY,
    at_time         TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    user_id         NUMBER(19),
    login_id        VARCHAR2(64),                          -- 快照，使用者刪除後仍可查
    ip              VARCHAR2(45),
    method          VARCHAR2(10),
    path            VARCHAR2(500),
    status_code     NUMBER(3),
    duration_ms     NUMBER(10),
    user_agent      VARCHAR2(512),
    CONSTRAINT pk_access_log PRIMARY KEY (id)
);

-- 定期清除與查詢都以時間為主，不對 user_id 建 FK（避免刪使用者受阻）
CREATE INDEX ix_access_log_time ON access_log (at_time);
CREATE INDEX ix_access_log_user ON access_log (user_id, at_time);

-- Impact 機櫃盤點 API 的快取（一列）
CREATE TABLE rack_inventory_cache (
    cache_key       VARCHAR2(30)    NOT NULL,              -- 固定 'default'
    payload         CLOB            NOT NULL,
    fetched_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_rack_inventory_cache PRIMARY KEY (cache_key),
    CONSTRAINT ck_rack_inventory_json  CHECK (payload IS JSON)
);


-- =====================================================================
-- 8. 固定參考資料
-- =====================================================================

INSERT INTO role (code, name, sort_no) VALUES ('admin',        '系統管理員',   1);
INSERT INTO role (code, name, sort_no) VALUES ('it_manager',   '資訊主管',     2);
INSERT INTO role (code, name, sort_no) VALUES ('dept_manager', 'Infra 主管',   3);
INSERT INTO role (code, name, sort_no) VALUES ('idc_admin',    '機房管理員',   4);
INSERT INTO role (code, name, sort_no) VALUES ('governance',   '資訊治理',     5);
INSERT INTO role (code, name, sort_no) VALUES ('infra',        'Infra 同仁',   6);

INSERT INTO workflow_def (id, name, description, doc_type, is_default, active) VALUES
    ('full', '完整簽核流程 (P3/P4 預設)',
     'Infra 主管 → 資訊治理(審查) → 機房管理員 → 資訊主管 → 資訊治理(複驗)', 'CR', 1, 1);
INSERT INTO workflow_def (id, name, description, doc_type, is_default, active) VALUES
    ('p2_high', 'P2 簡化簽核 (主管簽核可電子簽核)',
     '由Infra 主管即可簽核放行。', 'CR', 0, 1);
INSERT INTO workflow_def (id, name, description, doc_type, is_default, active) VALUES
    ('p1_emergency', 'P1 口頭報備 / 事後補單',
     '先執行後補單，資訊主管補核准。', 'CR', 0, 1);

-- 流程步驟：舊 workflows.json 的 step key 與顯示名稱對不上（dept_manager 步驟名叫「機房管理員」、
-- 審核人卻是 idc_admin 角色），此處照舊資料原樣搬，不修正；是否整理見 BACKLOG 第 34 項。
-- it_manager 步驟在舊系統指定使用者 gary；使用者尚未匯入，此處先以 ROLE=it_manager 代替，
-- S3 匯入使用者後若要維持「指定人」再 UPDATE 成 USER。
INSERT INTO workflow_step_def (workflow_id, seq, step_key, name, approver_type, approver_role, notify_only, allow_delegate) VALUES
    ('full', 1, 'dept_manager',      '機房管理員',     'ROLE', 'idc_admin',    0, 0);
INSERT INTO workflow_step_def (workflow_id, seq, step_key, name, approver_type, approver_role, notify_only, allow_delegate) VALUES
    ('full', 2, 'governance_review', 'Infra 主管',     'ROLE', 'dept_manager', 0, 0);
INSERT INTO workflow_step_def (workflow_id, seq, step_key, name, approver_type, approver_role, notify_only, allow_delegate) VALUES
    ('full', 3, 'idc_admin',         '資訊治理 (審查)', 'ROLE', 'governance',   0, 0);
INSERT INTO workflow_step_def (workflow_id, seq, step_key, name, approver_type, approver_role, notify_only, allow_delegate) VALUES
    ('full', 4, 'it_manager',        '資訊主管',       'ROLE', 'it_manager',   0, 0);
INSERT INTO workflow_step_def (workflow_id, seq, step_key, name, approver_type, approver_role, notify_only, allow_delegate) VALUES
    ('full', 5, 'governance_final',  '資訊治理 (複驗)', 'ROLE', 'governance',   0, 0);

INSERT INTO workflow_step_def (workflow_id, seq, step_key, name, approver_type, approver_role, notify_only, allow_delegate) VALUES
    ('p2_high', 1, 'dept_manager', 'Infra 主管', 'ROLE', 'dept_manager', 0, 1);

INSERT INTO workflow_step_def (workflow_id, seq, step_key, name, approver_type, approver_role, notify_only, allow_delegate, step_mode) VALUES
    ('p1_emergency', 1, 'it_manager', '資訊主管事後補核', 'ROLE', 'it_manager', 0, 0, 'POST_HOC');

-- 站台設定預設值（舊 config/site.json；sessionSecret 不進 DB，改環境變數 IM_SESSION_SECRET）
INSERT INTO site_setting (setting_key, setting_value) VALUES ('siteName',       '機房設備異動申請系統');
INSERT INTO site_setting (setting_key, setting_value) VALUES ('siteShort',      'Infra Manager');
INSERT INTO site_setting (setting_key, setting_value) VALUES ('timezone',       'Asia/Taipei');
INSERT INTO site_setting (setting_key, setting_value) VALUES ('uploadMaxMB',    '50');
INSERT INTO site_setting (setting_key, setting_value) VALUES ('uploadMaxFiles', '30');
INSERT INTO site_setting (setting_key, setting_value) VALUES ('workflowPolicy', 'full_only');
INSERT INTO site_setting (setting_key, setting_value) VALUES ('excludeIps',     '[]');

COMMIT;
