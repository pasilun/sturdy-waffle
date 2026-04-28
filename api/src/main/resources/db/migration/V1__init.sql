-- Chart of accounts (seeded separately; FK target for postings)
CREATE TABLE accounts (
    code        VARCHAR(10)  PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    type        VARCHAR(20)  NOT NULL,
    normal_side VARCHAR(6)   NOT NULL
);

-- One row per uploaded PDF
CREATE TABLE invoices (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_name  VARCHAR(200),
    invoice_number VARCHAR(100),
    invoice_date   DATE,
    currency       VARCHAR(3)   NOT NULL DEFAULT 'SEK',
    net            NUMERIC(18,2),
    vat            NUMERIC(18,2),
    gross          NUMERIC(18,2),
    pdf_path       TEXT         NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Raw LLM extraction output + metadata
CREATE TABLE extractions (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id     UUID         NOT NULL REFERENCES invoices(id),
    raw_json       TEXT         NOT NULL,
    model          VARCHAR(100) NOT NULL,
    prompt_version VARCHAR(50)  NOT NULL,
    latency_ms     BIGINT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- A proposed journal entry (linked to one extraction)
CREATE TABLE suggestions (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id     UUID         NOT NULL REFERENCES invoices(id),
    extraction_id  UUID         NOT NULL REFERENCES extractions(id),
    model          VARCHAR(100) NOT NULL,
    prompt_version VARCHAR(50)  NOT NULL,
    latency_ms     BIGINT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Individual debit/credit lines in a suggestion
-- debit XOR credit: exactly one must be non-null per row
CREATE TABLE postings (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    suggestion_id UUID         NOT NULL REFERENCES suggestions(id),
    line_index    INT          NOT NULL,
    account_code  VARCHAR(10)  NOT NULL REFERENCES accounts(code),
    debit         NUMERIC(18,2),
    credit        NUMERIC(18,2),
    description   TEXT,
    reasoning     TEXT,
    confidence    NUMERIC(4,3),
    CONSTRAINT posting_debit_xor_credit
        CHECK ((debit IS NOT NULL AND credit IS NULL)
            OR (debit IS NULL AND credit IS NOT NULL))
);

-- Accountant approve/decline decision (one per suggestion)
CREATE TABLE decisions (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    suggestion_id UUID        NOT NULL UNIQUE REFERENCES suggestions(id),
    status        VARCHAR(20) NOT NULL,  -- APPROVED | DECLINED
    decided_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    note          TEXT
);

-- Append-only event log (table exists in v1; audit UI is a §13 extension)
CREATE TABLE audit_events (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    entity       VARCHAR(50)  NOT NULL,
    entity_id    UUID         NOT NULL,
    event        VARCHAR(100) NOT NULL,
    payload_json TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
